package scratch.anne.risk_system_vb.engine.portfolio_workflow;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.io.writers.HazardJsonWriter;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.StringUtil.ImtPeriod;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;

/**
 * Portfolio-level calculator that computes hazard curves per (SiteKey, ImKey)
 * and propagates them into risk integral calculations.
 *
 * <p>Includes an optional hazard trace writer that streams
 * hazard curves to CSV for debugging and reproducibility.</p>
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Hazard computation is parallelized by site</li>
 *   <li>Risk computation is performed immediately after hazard computation</li>
 *   <li>Hazard curves can optionally be recorded via a streaming writer</li>
 * </ul>
 *
 */
public class PortfolioRiskConvolutionCalculator {

    private final RiskConvolutionPortfolio riskConvolutionPortfolio;
    private final SimpleImResponseLibrary responseLib;
    private final AttenRelRef gmmRef;
    private final AbstractERF erf;
    private final RiskConvolution.IntegrationMethod integrationMethod;

    /** Optional hazard json writer (null if disabled). */
    private final HazardJsonWriter hazardWriter;

    /**
     * Full constructor with optional hazard JSON output.
     *
     * @param riskConvolutionPortfolio portfolio
     * @param responseLib simple response library
     * @param gmmRef GMPE reference
     * @param erf earthquake rupture forecast
     * @param integrationMethod integration method
     * @param hazardJsonPath optional path for hazard curve CSV (null disables writing)
     */
    public PortfolioRiskConvolutionCalculator(
    		RiskConvolutionPortfolio riskConvolutionPortfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf,
            RiskConvolution.IntegrationMethod integrationMethod,
            Path hazardJsonPath
    ) {
        this.riskConvolutionPortfolio = riskConvolutionPortfolio;
        this.responseLib = responseLib;
        this.gmmRef = gmmRef;
        this.erf = erf;
        this.integrationMethod = integrationMethod;

        this.hazardWriter = (hazardJsonPath != null)
                ? new HazardJsonWriter(hazardJsonPath)
                : null;
    }
    
    /**
     * Constructor specifying only integration method.
     * Hazard logging is disabled.
     */
    public PortfolioRiskConvolutionCalculator(
    		RiskConvolutionPortfolio riskConvolutionPortfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf,
            RiskConvolution.IntegrationMethod integrationMethod
    ) {
        this(riskConvolutionPortfolio, responseLib, gmmRef, erf,
                integrationMethod, null);
    }
    
    /**
     * Constructor enabling hazard CSV output with default integration method.
     */
    public PortfolioRiskConvolutionCalculator(
    		RiskConvolutionPortfolio riskConvolutionPortfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf,
            Path hazardJsonPath
    ) {
        this(riskConvolutionPortfolio, responseLib, gmmRef, erf,
                RiskConvolution.IntegrationMethod.CLOSED_FORM,
                hazardJsonPath);
    }

    /**
     * Convenience constructor (default integration and no hazard output).
     */
    public PortfolioRiskConvolutionCalculator(
    		RiskConvolutionPortfolio riskConvolutionPortfolio,
            SimpleImResponseLibrary responseLib,
            AttenRelRef gmmRef,
            AbstractERF erf
    ) {
        this(riskConvolutionPortfolio, responseLib, gmmRef, erf,
                RiskConvolution.IntegrationMethod.CLOSED_FORM, null);
    }

    /**
     * Main computation entry point.
     * 
     * Compute expected loss for all assets in the portfolio.
     * Uses parallel execution per site.
     *
     * <p>Hazard curves are computed per (SiteKey, ImKey),
     * optionally written to CSV, and immediately consumed
     * by the risk integrator.</p>
     *
     * @return portfolio with computed expected losses
     */
    public RiskConvolutionPortfolio computeRiskConvolution() {

    	// ---- Prepare hazard calculators and GMM deque ----
        ArrayDeque<ScalarIMR> gmmDeque = new ArrayDeque<>();
        ArrayDeque<HazardCurveCalculator> calcDeque = new ArrayDeque<>();

        ScalarIMR gmm0 = gmmRef.get();
        
        // Build sites from ELossPortfolio site keys
        List<SiteKey> siteKeys = new ArrayList<>(riskConvolutionPortfolio.getSiteKeys());
        List<Site> sites = new ArrayList<>();
        for (SiteKey siteKey : siteKeys) {
            Site site = new Site(new Location(siteKey.getLat(), siteKey.getLon()));
            for (Parameter<?> param : gmm0.getSiteParams()) {
                site.addParameter((Parameter<?>) param.clone());
            }
            @SuppressWarnings("unchecked")
            Parameter<Double> vs30Param =
                    (Parameter<Double>) site.getParameter("Vs30");
            vs30Param.setValue(siteKey.getVs30());
            sites.add(site);
        }

        AtomicInteger counter = new AtomicInteger(0);
        int total = sites.size();
        long startTime = System.nanoTime();

        // ---- Parallel computation per site ----
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < sites.size(); i++) {

            final Site site = sites.get(i);
            final SiteKey siteKey = siteKeys.get(i);

            futures.add(CompletableFuture.runAsync(() -> {

                ScalarIMR gmm = null;
                HazardCurveCalculator calc = null;

                try {
                	// thread-local GMM
                    synchronized (gmmDeque) {
                        gmm = gmmDeque.isEmpty() ? gmmRef.get() : gmmDeque.pop();
                    }
                    // thread-local hazard calculator
                    synchronized (calcDeque) {
                        calc = calcDeque.isEmpty()
                                ? new HazardCurveCalculator()
                                : calcDeque.pop();
                    }

                    gmm.setSite(site);

                    // ---- Get IMKey groups per site from lower-level portfolio ----
                    for (ImKey imKey : riskConvolutionPortfolio.getImKeysBySite(siteKey)) {

                        // get IM parameters for gmm
                    	ImtPeriod imtPeriod = imKey.getImtPeriod();
                    	gmm.setIntensityMeasure(imtPeriod.imt.name());
                    	gmm.getParameter(PeriodParam.NAME).setValue(imtPeriod.period);

                        // Build hazard curve im values
                        DiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
                        for (double x : imKey.getLogValues()) {
                            hazFunc.set(x, 0d);
                        }

                        hazFunc = calc.getHazardCurve(hazFunc, site, gmm, erf);

                        double[] hazardY = new double[hazFunc.size()];
                        for (int j = 0; j < hazFunc.size(); j++) {
                            hazardY[j] = hazFunc.getY(j);
                        }

                        
                        // ----------- OPTIONAL: hazard logging ----------------
                        if (hazardWriter != null) {
                            hazardWriter.write(siteKey, imKey, hazardY);
                        }

                        // ---- Compute normalized expected loss per asset ---
                        for (RiskConvolutionAsset asset :
                                riskConvolutionPortfolio.getAssetsBySiteAndImKey(siteKey, imKey)) {

                            RiskConvolution calcRisk = new RiskConvolution(
                                    responseLib.getByName(asset.getModelName()),
                                    hazardY,
                                    integrationMethod
                            );

                            asset.setRiskConvolutionResult(calcRisk.compute());
                        }
                    }

                    // ---- Progress update ----
                    int count = counter.incrementAndGet();
                    if (count % 100 == 0 || count == total) {
                        long now = System.nanoTime();
                        double elapsedSec = (now - startTime) / 1e9;
                        double rate = count / elapsedSec;
                        double remaining = (total - count) / rate;

                        System.out.printf(
                                "Processed %d / %d (%.1f%%) | %.1f sites/s | ETA %.1fs%n",
                                count, total,
                                100.0 * count / total,
                                rate,
                                remaining
                        );
                    }

                } catch (Exception e) {
                    System.err.println("Exception for site " + siteKey);
                    e.printStackTrace();
                } finally {
                    if (gmm != null) synchronized (gmmDeque) { gmmDeque.push(gmm); }
                    if (calc != null) synchronized (calcDeque) { calcDeque.push(calc); }
                }
            }));
        }

        // Wait for completion
        for (CompletableFuture<Void> f : futures) f.join();

        // flush hazard writer if enabled
        if (hazardWriter != null) {
            hazardWriter.close();
            System.out.println("Hazard written to run folder");
        }

        riskConvolutionPortfolio.setRiskConvolutionComputed(true);
        return riskConvolutionPortfolio;
    }
}
