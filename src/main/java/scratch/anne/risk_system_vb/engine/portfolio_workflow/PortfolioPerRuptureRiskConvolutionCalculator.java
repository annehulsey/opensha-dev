package scratch.anne.risk_system_vb.engine.portfolio_workflow;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.opensha.commons.geo.Location;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilterUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;

import scratch.anne.risk_system_vb.domain.asset.RiskConvolutionAsset;
import scratch.anne.risk_system_vb.domain.asset.RiskConvolutionAsset.RiskMetricType;
import scratch.anne.risk_system_vb.domain.asset.vulnerability.ExpectedLossAsset;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio.ConvolutionMode;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.engine.accumulators.RuptureKey;
import scratch.anne.risk_system_vb.engine.accumulators.RuptureResultsCollection;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.StringUtil.ImtPeriod;
import scratch.anne.risk_system_vb.util.enums.IMT;

/**
 * Portfolio workflow:
 * <ul>
 *   <li>Parallelization is performed at SiteKey level</li>
 *   <li>Loop over all siteKeys and imKeys to minimize hazard calculations</li>
 *   <li>Loop over each rupture to accumulate risk results
 *   <li>InnerLoop over all relevant assets for risk calcs</li>
 * </ul>

 */
public class PortfolioPerRuptureRiskConvolutionCalculator {

    private final RiskConvolutionPortfolio portfolio;
    private final SimpleImResponseLibrary responseLib;
    private final HazardParameters hazardParameters;
    private final RiskConvolution.IntegrationMethod integrationMethod;
    
    private final RiskMetricType riskMetricType;
    
    private final AbstractERF erf;
    private final AttenRelRef gmmRef;
    
    private final SourceFilterManager filterManager;
    private final List<SourceFilter> sourceFilters;

    RuptureResultsCollection ruptureResults;


    /** full constructor */
    public PortfolioPerRuptureRiskConvolutionCalculator(
            RiskConvolutionPortfolio portfolio,
            SimpleImResponseLibrary responseLib,
            HazardParameters hazardParameters,
            RiskConvolution.IntegrationMethod integrationMethod
    ) {
    	this.portfolio = portfolio;
    	this.riskMetricType = portfolio.getRiskMetricType();
        
        
    	this.responseLib = responseLib;
    	this.hazardParameters = hazardParameters;
    	this.integrationMethod = integrationMethod;
                
    	SurfaceCachingPolicy.force(CacheTypes.THREAD_LOCAL);
        try {
            this.erf = (AbstractERF)
                    Class.forName(hazardParameters.getErfName())
                            .getDeclaredConstructor()
                            .newInstance();
        } catch (ClassNotFoundException |
                 NoSuchMethodException |
                 IllegalAccessException |
                 InstantiationException |
                 InvocationTargetException e) {

            throw new IllegalArgumentException(
                    "Failed to instantiate ERF class: " + hazardParameters.getErfName(), e
            );
        }
        
        this.filterManager = hazardParameters.buildSourceManager();  
        this.sourceFilters = filterManager.getEnabledFilters();
        
        this.erf.getTimeSpan().setDuration(hazardParameters.getErfDuration());
        this.erf.updateForecast();
      
        this.gmmRef = AttenRelRef.valueOf(hazardParameters.getGmmName().toUpperCase());
    }

    /** default integration method */
    public PortfolioPerRuptureRiskConvolutionCalculator(
            RiskConvolutionPortfolio portfolio,
            SimpleImResponseLibrary responseLib,
            HazardParameters hazardParameters
    ) {
        this(portfolio, responseLib, hazardParameters,
                RiskConvolution.IntegrationMethod.CLOSED_FORM);
    }

    /** hazard and risk calculation loops */
    public RiskConvolutionPortfolio computeRisk() {
    	
    	ruptureResults = RuptureResultsCollection.fromERF(erf, hazardParameters);

        ArrayDeque<ScalarIMR> gmmDeque = new ArrayDeque<>();

        ScalarIMR baseGmm = gmmRef.get();

        List<SiteKey> siteKeys = new ArrayList<>(portfolio.getSiteKeys());

        int totalSiteImKeys = countTotalSiteImKeys();
        AtomicInteger counter = new AtomicInteger();

        long startTime = System.nanoTime();

        List<Site> sites = new ArrayList<>();

        for (SiteKey siteKey : siteKeys) {

            Site site = new Site(new Location(siteKey.getLat(), siteKey.getLon()));

            for (Parameter<?> p : baseGmm.getSiteParams())
                site.addParameter((Parameter<?>) p.clone());

            @SuppressWarnings("unchecked")
            Parameter<Double> gmmVs30 =
                    (Parameter<Double>) site.getParameter("Vs30");

            gmmVs30.setValue(siteKey.getVs30());

            sites.add(site);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < sites.size(); i++) {

            final Site site = sites.get(i);
            final SiteKey siteKey = siteKeys.get(i);

            futures.add(CompletableFuture.runAsync(() -> {

                ScalarIMR gmm = null;

                try {

                    synchronized (gmmDeque) {
                        gmm = gmmDeque.isEmpty() ? gmmRef.get() : gmmDeque.pop();
                    }

                    gmm.setSite(site);

                    for (ImKey imKey : portfolio.getImKeysBySite(siteKey)) {

                        ImtPeriod imt = imKey.getImtPeriod();
                        gmm.setIntensityMeasure(imt.imt.name());

                        if (imt.imt == IMT.SA) {
//                            gmm.getParameter(PeriodParam.NAME)
//                                    .setValue(imt.period);
                        
                        	// ----  work around for invalid period value ----
                        	// snap to nearest valid value
                        	//TODO find better solution for invalid gmm periods
                        	Parameter<Double> p =
                        	    (Parameter<Double>) gmm.getParameter(PeriodParam.NAME);

                        	var constraint =
                        	    (org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint)
                        	        p.getConstraint();

                        	double requested = imt.period;

                        	double allowed =
                        	    constraint.getAllowedDoubles().stream()
                        	        .min(Comparator.comparing(d -> Math.abs(d - requested)))
                        	        .orElseThrow();

                        	p.setValue(allowed);
                        }

                        DiscretizedFunc hazFunc =
                                new ArbitrarilyDiscretizedFunc();

                        for (double x : imKey.getLogValues())
                            hazFunc.set(x, 0d);
                        
                        
                        for (int sourceID = 0; sourceID < erf.getNumSources(); sourceID++) {
                            ProbEqkSource source = erf.getSource(sourceID);
                            if (SourceFilterUtils.canSkipSource(sourceFilters, source, site)) {
                                // source is outside filter limits
                                continue;
                            }          
                            
                            for (int ruptureID = 0; ruptureID < source.getNumRuptures(); ruptureID++) {
                                ProbEqkRupture rupture = source.getRupture(ruptureID);
	                            if (SourceFilterUtils.canSkipRupture(sourceFilters, rupture, site)) {
	                                // rupture is outside filter limits
	                                continue;
	                            }
	                            RuptureKey rupKey = new RuptureKey(sourceID, ruptureID);
	
	                            gmm.getExceedProbabilities(rupture, hazFunc);
			
		                        switch (hazardParameters.getHazardMetric()) {
		
		                            case PROBABILITY_EXCEEDANCE:
		                                // already correct
		                                break;
		
		                            case RATE_EXCEEDANCE:
		                                // also uses the conditional exceedance probability
		                            	// since the rate vs probability is addressed in the rupture likelihood 
		                                break;
		
		                            default:
		                                throw new IllegalArgumentException(
		                                    "Hazard metric not supported for risk convolution class: " + hazardParameters.getHazardMetric()
		                                );
		                        }
		                        
		                        double[] hazard = new double[hazFunc.size()];
		                        for (int j = 0; j < hazard.length; j++)
		                            hazard[j] = hazFunc.getY(j);
			                        
			                        for (RiskConvolutionAsset asset :
			                            portfolio.getAssetsBySiteAndImKey(siteKey, imKey)) {
	
			                        	// get asset value, defaults to 1 if not assessing expected loss
			                        	double assetValue = 1.0;
										if (riskMetricType == RiskMetricType.EXPECTED_LOSS) {
											ExpectedLossAsset vulnAsset = (ExpectedLossAsset) asset;
											assetValue = vulnAsset.getValue();
										}
			                        	
				                        RiskConvolution riskCalc = new RiskConvolution(
				                                responseLib.getByName(asset.getModelName()),
				                                hazard,
				                                integrationMethod
				                        );
				
				                        //TODO add rupture-by-rupture for failure probability, but not with accumulation over assets
				                        double assetLossForRupture = assetValue * riskCalc.compute().getRisk();
				                        
				                        ruptureResults.accumulateRuptureLoss(rupKey, assetLossForRupture);
				                        
			                        }
		                        }
                        }

                        // ----- PROGRESS -----
                        int done = counter.incrementAndGet();

                        if (done % 100 == 0 || done == totalSiteImKeys) {

                            long now = System.nanoTime();

                            double elapsed = (now - startTime) / 1e9;
                            double rate = done / elapsed;
                            double etaSeconds = (totalSiteImKeys - done) / rate;

                            LocalTime currentTime = LocalTime.now();
                            LocalTime etaTime = currentTime.plusSeconds((long) etaSeconds);

                            DateTimeFormatter fmt =
                                    DateTimeFormatter.ofPattern("h:mm:ss a");

                            System.out.printf(
                                    "Site/IM %d / %d (%.1f%%) | %.1f sites/s | Now %s | ETA %s (%.1f hr remaining)%n",
                                    done,
                                    totalSiteImKeys,
                                    100.0 * done / totalSiteImKeys,
                                    rate,
                                    currentTime.format(fmt),
                                    etaTime.format(fmt),
                                    etaSeconds / 60.0 / 60.0
                            );
                        }
                    }

                } finally {
                    if (gmm != null) synchronized (gmmDeque) { gmmDeque.push(gmm); }

                }
            }));
        }

        futures.forEach(CompletableFuture::join);

        ruptureResults.freeze();
        portfolio.setRuptureResults(ruptureResults);
        
        portfolio.setConvolutionMode(ConvolutionMode.PER_RUPTURE);
       
        
        double elapsedSeconds = (System.nanoTime() - startTime) / 1e9;

        if (elapsedSeconds >= 3600) {
            System.out.printf("Completed in %.1f hours%n",
                    elapsedSeconds / 3600.0);
        } else if (elapsedSeconds >= 60) {
            System.out.printf("Completed in %.1f minutes%n",
                    elapsedSeconds / 60.0);
        } else {
            System.out.printf("Completed in %.1f seconds%n",
                    elapsedSeconds);
        }
        
        
        
        return portfolio;
    }
    
    private int countTotalSiteImKeys() {

        int total = 0;

        for (SiteKey siteKey : portfolio.getSiteKeys()) {
            total += portfolio.getImKeysBySite(siteKey).size();
        }

        return total;
    }

}
