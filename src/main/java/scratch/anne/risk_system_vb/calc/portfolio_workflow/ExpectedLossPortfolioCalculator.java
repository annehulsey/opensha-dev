package scratch.anne.risk_system_vb.calc.portfolio_workflow;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensha.commons.geo.Location;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

import scratch.anne.risk_system_vb.calc.convolution.RiskIntegral;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.ExpectedLossAsset;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ExpectedLossPortfolio;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibrary;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;


public class ExpectedLossPortfolioCalculator {

    private final ExpectedLossPortfolio elossPortfolio;
    private final ExpectedVulnLibrary vulnLib;
    private final AttenRelRef gmmRef;
    private final AbstractERF erf;
    private final RiskIntegral.IntegrationMethod integrationMethod;

    public ExpectedLossPortfolioCalculator(ExpectedLossPortfolio elossPortfolio,
		                                   ExpectedVulnLibrary elossVulnLib,
		                                   AttenRelRef gmmRef,
		                                   AbstractERF erf,
		                                   RiskIntegral.IntegrationMethod integrationMethod) {
        this.elossPortfolio = elossPortfolio;
        this.vulnLib = elossVulnLib;
        this.gmmRef = gmmRef;
        this.erf = erf;
        this.integrationMethod = integrationMethod;
    }
    
    /**
     * Convenience constructor.
     * Assumes Riemann integration.
     */
    public ExpectedLossPortfolioCalculator(
            ExpectedLossPortfolio elossPortfolio,
            ExpectedVulnLibrary elossVulnLib,
            AttenRelRef gmmRef,
            AbstractERF erf) {

        this(
                elossPortfolio,
                elossVulnLib,
                gmmRef,
                erf,
                RiskIntegral.IntegrationMethod.RIEMANN
        );
    }

    /**
     * Compute expected loss for all assets in the portfolio.
     * Uses parallel execution per site.
     */
    public ExpectedLossPortfolio computeExpectedLoss() {
        // ---- Prepare hazard calculators and GMM deque ----
        ArrayDeque<ScalarIMR> gmmDeque = new ArrayDeque<>();
        ArrayDeque<HazardCurveCalculator> calcDeque = new ArrayDeque<>();

        // Build sites from ELossPortfolio site keys
        ScalarIMR gmm0 = gmmRef.get();  // template for site parameters
        List<Site> sites = new ArrayList<>();
        List<SiteKey> siteKeys = new ArrayList<>(elossPortfolio.getSiteKeys());

        for (SiteKey siteKey : siteKeys) {
            Site site = new Site(new Location(siteKey.getLat(), siteKey.getLon()));
            for (Parameter<?> param : gmm0.getSiteParams()) {
                site.addParameter((Parameter<?>) param.clone());
            }
            @SuppressWarnings("unchecked")
            Parameter<Double> vs30Param = (Parameter<Double>) site.getParameter("Vs30");
            vs30Param.setValue(siteKey.getVs30());
            sites.add(site);
//            System.out.println("Site=" + site);
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
                        calc = calcDeque.isEmpty() ? new HazardCurveCalculator() : calcDeque.pop();
                    }

                    gmm.setSite(site);

                    // ---- Get IMKey groups per site from lower-level portfolio ----
                    for (ImKey imKey : elossPortfolio.getImKeysBySite(siteKey)) {
                    	
                        // Build hazard curve
                        DiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
                        for (double x : imKey.getLogValues()) {
                            hazFunc.set(x, 0d);
                        }
                        
                        hazFunc = calc.getHazardCurve(hazFunc, site, gmm, erf);
                        
                        double[] hazardY = new double[hazFunc.size()];
                        for (int j = 0; j < hazFunc.size(); j++) {
                            hazardY[j] = hazFunc.getY(j);
                        }
                        
                        // ---- Compute normalized expected loss per asset ----
                        for (ExpectedLossAsset asset : elossPortfolio.getAssetsBySiteAndImKey(siteKey, imKey)) {
                            RiskIntegral calcEL = new RiskIntegral(
                                    vulnLib.getByName(asset.getModelName()),
                                    hazardY,
                                    integrationMethod
                            );
                            double nEL = calcEL.compute();
                            asset.setNormalizedExpectedLoss(nEL);
                        }
                    }

                    // ---- Progress update ----
                    int count = counter.incrementAndGet();
                    if (count % 100 == 0 || count == total) {
                        long now = System.nanoTime();
                        double elapsedSec = (now - startTime) / 1e9;
                        double rate = count / elapsedSec;
                        double remaining = (total - count) / rate;

                        synchronized (System.out) {
                            System.out.printf(
                                    "Processed %d / %d (%.1f%%) | %.1f sites/s | elapsed %.1fs | ETA %.1fs | site: %s%n",
                                    count, total,
                                    100.0 * count / total,
                                    rate,
                                    elapsedSec,
                                    remaining,
                                    siteKey
                            );
                        }
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
        
        elossPortfolio.setExpectedLossComputed(true);
        
        return elossPortfolio;
    }
}