package scratch.anne.risk_system_vb.engine.portfolio_workflow;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.opensha.commons.geo.Location;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;

import scratch.anne.risk_system_vb.domain.asset.RiskConvolutionAsset;
import scratch.anne.risk_system_vb.domain.hazard.HazardCurve;
import scratch.anne.risk_system_vb.domain.hazard.HazardCurveCollection;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters.HazardMetric;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.StringUtil.ImtPeriod;
import scratch.anne.risk_system_vb.util.enums.IMT;

/**
 * Two-stage portfolio-level workflow:
 *
 * <pre>
 * Stage 1: Hazard field computation
 *   (SiteKey × ImKey → HazardResult)
 *
 * Stage 2: Risk convolution
 *   (Asset + Hazard field → ConvolutionResult)
 * </pre>
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>Hazard is computed once and cached at portfolio level</li>
 *   <li>Risk is a deterministic transform over stored hazard</li>
 *   <li>Parallelization is performed at SiteKey level</li>
 *   <li>IM consistency is guaranteed via ImKey (not raw arrays)</li>
 * </ul>
 */
public class PortfolioRiskConvolutionCalculator {

    private final RiskConvolutionPortfolio portfolio;
    private final SimpleImResponseLibrary responseLib;
    private final AttenRelRef gmmRef;
    private final AbstractERF erf;
    private final HazardMetric hazardMetric;
    private final RiskConvolution.IntegrationMethod integrationMethod;

    /** true once hazard field is fully computed */
    private boolean hazardComputed = false;
    HazardCurveCollection hazardCurves;
    
    private boolean riskComputed = false;

    /** full constructor */
    public PortfolioRiskConvolutionCalculator(
            RiskConvolutionPortfolio portfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf,
            HazardMetric hazardMetric,
            RiskConvolution.IntegrationMethod integrationMethod
    ) {
        this.portfolio = portfolio;
        this.responseLib = responseLib;
        this.gmmRef = gmmRef;
        this.erf = erf;
        this.hazardMetric = hazardMetric;
        this.integrationMethod = integrationMethod;
    }

    /** default integration method */
    public PortfolioRiskConvolutionCalculator(
            RiskConvolutionPortfolio portfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf,
            HazardMetric hazardMetric
    ) {
        this(portfolio, responseLib, gmmRef, erf, 
        		hazardMetric,
                RiskConvolution.IntegrationMethod.CLOSED_FORM);
    }
    
    /** default hazard metric */
    public PortfolioRiskConvolutionCalculator(
            RiskConvolutionPortfolio portfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf,
            RiskConvolution.IntegrationMethod integrationMethod
    ) {
        this(portfolio, responseLib, gmmRef, erf, 
        		HazardMetric.PROBABILITY_EXCEEDANCE,
                integrationMethod);
    }    
    
    /** default hazard metric and integration method */
    public PortfolioRiskConvolutionCalculator(
            RiskConvolutionPortfolio portfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf
    ) {
        this(portfolio, responseLib, gmmRef, erf, 
        		HazardMetric.PROBABILITY_EXCEEDANCE,
                RiskConvolution.IntegrationMethod.CLOSED_FORM);
    }

    // ============================================================
    // STAGE 1 — HAZARD FIELD COMPUTATION
    // ============================================================

    /**
     * Computes and stores hazard curves for all (SiteKey, ImKey) pairs.
     *
     * <p>This method is parallelized over SiteKey. Each thread:
     * <ul>
     *   <li>constructs site-specific GMM state</li>
     *   <li>loops over IM keys for that site</li>
     *   <li>computes hazard curves</li>
     *   <li>stores results in portfolio hazard cache</li>
     * </ul>
     *
     * <p>After execution, hazardCurves becomes immutable input for risk stage.</p>
     */
    public void computeHazardCurves() {
    	
    	beginHazardComputation();

        ArrayDeque<ScalarIMR> gmmDeque = new ArrayDeque<>();
        ArrayDeque<HazardCurveCalculator> calcDeque = new ArrayDeque<>();

        ScalarIMR baseGmm = gmmRef.get();

        List<SiteKey> siteKeys = new ArrayList<>(portfolio.getSiteKeys());

        int totalCurves = countTotalHazardCurves();
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
                HazardCurveCalculator calc = null;

                try {

                    synchronized (gmmDeque) {
                        gmm = gmmDeque.isEmpty() ? gmmRef.get() : gmmDeque.pop();
                    }

                    synchronized (calcDeque) {
                        calc = calcDeque.isEmpty()
                                ? new HazardCurveCalculator()
                                : calcDeque.pop();
                    }

                    gmm.setSite(site);

                    for (ImKey imKey : portfolio.getImKeysBySite(siteKey)) {

                        ImtPeriod imt = imKey.getImtPeriod();
                        gmm.setIntensityMeasure(imt.imt.name());

                        if (imt.imt == IMT.SA) {
//                            gmm.getParameter(PeriodParam.NAME)
//                                    .setValue(imt.period);
                        
                        	// work around for invalid period value
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
                       
                        
                        hazFunc = calc.getHazardCurve(hazFunc, site, gmm, erf);

                        switch (hazardMetric) {

                            case PROBABILITY_EXCEEDANCE:
                                // already correct
                                break;

                            case RATE_EXCEEDANCE:
                                // average rate over full ERF duration (not annualized)
                                hazFunc = calc.getAnnualizedRates(hazFunc, 1.0);
                                break;

                            default:
                                throw new IllegalArgumentException(
                                    "Hazard metric not supported for risk convolution class: " + hazardMetric
                                );
                        }
                        
                        double[] hazard = new double[hazFunc.size()];
                        for (int j = 0; j < hazard.length; j++)
                            hazard[j] = hazFunc.getY(j);

                        hazardCurves.put(
                                siteKey,
                                imKey,
                                new HazardCurve(hazard)
                        );

                        // ----- PROGRESS -----
                        int done = counter.incrementAndGet();

                        if (done % 100 == 0 || done == totalCurves) {

                            long now = System.nanoTime();

                            double elapsed = (now - startTime) / 1e9;
                            double rate = done / elapsed;
                            double etaSeconds = (totalCurves - done) / rate;

                            LocalTime currentTime = LocalTime.now();
                            LocalTime etaTime = currentTime.plusSeconds((long) etaSeconds);

                            DateTimeFormatter fmt =
                                    DateTimeFormatter.ofPattern("h:mm:ss a");

                            System.out.printf(
                                    "Hazard %d / %d (%.1f%%) | %.1f curves/s | Now %s | ETA %s (%.1f hr remaining)%n",
                                    done,
                                    totalCurves,
                                    100.0 * done / totalCurves,
                                    rate,
                                    currentTime.format(fmt),
                                    etaTime.format(fmt),
                                    etaSeconds / 60.0 / 60.0
                            );
                        }
                    }

                } finally {
                    if (gmm != null) synchronized (gmmDeque) { gmmDeque.push(gmm); }
                    if (calc != null) synchronized (calcDeque) { calcDeque.push(calc); }
                }
            }));
        }

        futures.forEach(CompletableFuture::join);

        hazardCurves.freeze();
        portfolio.setHazardCurves(hazardCurves);
        hazardComputed = true;
        
        portfolio.setHazardComputed(hazardComputed);
    }
    
    private int countTotalHazardCurves() {

        int total = 0;

        for (SiteKey siteKey : portfolio.getSiteKeys()) {
            total += portfolio.getImKeysBySite(siteKey).size();
        }

        return total;
    }

    // ============================================================
    // STAGE 2 — RISK CONVOLUTION
    // ============================================================

    /**
     * Computes risk convolution using precomputed hazard field.
     *
     * <p>Requires {@link #computeHazardField()} to be executed first.</p>
     *
     * <p>Parallelized over SiteKey. Each site consumes cached hazard only.</p>
     */
    public void computeRiskConvolution() {

        if (!hazardComputed) {
            throw new IllegalStateException(
                    "Hazard field must be computed before risk convolution.");
        }

        HazardCurveCollection hazards = portfolio.getHazardCurves();

        int totalAssets = portfolio.getAssets().size();
        AtomicInteger counter = new AtomicInteger();

        long startTime = System.nanoTime();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        hazards.forEachCurve((siteKey, imKey, hazard) -> {

            futures.add(CompletableFuture.runAsync(() -> {

                double[] hazardY = hazard.getHazard();

                for (RiskConvolutionAsset asset :
                        portfolio.getAssetsBySiteAndImKey(siteKey, imKey)) {

                    RiskConvolution riskCalc = new RiskConvolution(
                            responseLib.getByName(asset.getModelName()),
                            hazardY,
                            integrationMethod
                    );

                    asset.setRiskConvolutionResult(riskCalc.compute());

                int done = counter.incrementAndGet();
                
                if (done % 100 == 0 || done == totalAssets) {

                    long now = System.nanoTime();

                    double elapsed = (now - startTime) / 1e9;
                    double rate = done / elapsed;
                    double etaSeconds = (totalAssets - done) / rate;

                    LocalTime currentTime = LocalTime.now();
                    LocalTime etaTime = currentTime.plusSeconds((long) etaSeconds);

                    DateTimeFormatter fmt =
                            DateTimeFormatter.ofPattern("h:mm:ss a");

                    System.out.printf(
                            "Hazard %d / %d (%.1f%%) | %.1f assets/s | Now %s | ETA %s (%.1f hr remaining)%n",
                            done,
                            totalAssets,
                            100.0 * done / totalAssets,
                            rate,
                            currentTime.format(fmt),
                            etaTime.format(fmt),
                            etaSeconds / 60.0 / 60.0
                    );
                }

              }
            }));
        });

        futures.forEach(CompletableFuture::join);

        riskComputed = true;
        portfolio.setRiskConvolutionComputed(true);
    }


    private void beginHazardComputation() {

        hazardComputed = false;

        HazardParameters params =
                new HazardParameters(
                        erf.getName(),
                        erf.getTimeSpan().getDuration(),
                        hazardMetric,
                        gmmRef.name()
                );

        hazardCurves = new HazardCurveCollection(params);
    }

    /**
     * Convenience method for full pipeline execution.
     */
    public RiskConvolutionPortfolio computeRisk() {
        computeHazardCurves();
        computeRiskConvolution();
        return portfolio;
    }
}