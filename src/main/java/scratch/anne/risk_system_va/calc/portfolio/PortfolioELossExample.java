package scratch.anne.risk_system_va.calc.portfolio;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.opensha.commons.geo.Location;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

import scratch.anne.risk_system_va.calc.eloss.ELossCalculator;
import scratch.anne.risk_system_va.calc.eloss.ELossCalculator.IntegrationMethod;
import scratch.anne.risk_system_va.portfolio.eloss.ELossPortfolio;
import scratch.anne.risk_system_va.portfolio.eloss.ELossPortfolioPreparer;
import scratch.anne.risk_system_va.portfolio.eloss.ELossAsset;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerabilityLibrary;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibrary;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibraryReader;
import scratch.anne.risk_system_va.portfolio.Portfolio;
import scratch.anne.risk_system_va.portfolio.PortfolioReader;

public class PortfolioELossExample {

    public static void main(String[] args) throws Exception {

        // -------------------------
        // 1) Read portfolio and vulnerability library
        // -------------------------
        Portfolio portfolio = PortfolioReader.readCSV(
            Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\portfolio.csv")
        );
        List<String> vulnNames = portfolio.getVulnerabilityNames();

        VulnerabilityLibrary library = VulnerabilityLibraryReader.readLibrary(
            Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\vulnerabilities.json"),
            "library source",
            "description",
            "DR → fraction, IM → g",
            "date, author, workflow v1.0"
        );

        ELossVulnerabilityLibrary elossVulnLib = scratch.anne.risk_system_va.calc.eloss.ELossVulnerabilityPreparer.prepare(
                library, vulnNames, false
        );

        // -------------------------
        // 2) Prepare ELossPortfolio
        // -------------------------
        ELossPortfolio elossPortfolio = ELossPortfolioPreparer.prepare(portfolio, elossVulnLib);

        System.out.println("Total ELoss assets: " + elossPortfolio.getAssets().size());
        System.out.println("Site keys: " + elossPortfolio.getSiteKeys());

        // -------------------------
        // 3) Initialize ERF
        // -------------------------
        NSHM23_WUS_BranchAveragedERF erf = new NSHM23_WUS_BranchAveragedERF();
        erf.getTimeSpan().setDuration(1.0);
        erf.updateForecast();

        // -------------------------
        // 4) Define GMM reference and create deque for thread-local GMM and calculators
        // -------------------------
        AttenRelRef gmmRef = AttenRelRef.ASK_2014;
        ScalarIMR gmm0 = gmmRef.get();  // template for site parameters

        ArrayDeque<ScalarIMR> gmmDeque = new ArrayDeque<>();
        ArrayDeque<HazardCurveCalculator> calcDeque = new ArrayDeque<>();

        // Build sites from ELossPortfolio site keys
        List<Site> sites = new ArrayList<>();
        List<ELossAsset.SiteKey> siteKeys = new ArrayList<>(elossPortfolio.getSiteKeys());

        for (ELossAsset.SiteKey siteKey : siteKeys) {
            Site site = new Site(new Location(siteKey.getLat(), siteKey.getLon()));
            for (Parameter<?> param : gmm0.getSiteParams()) {
                site.addParameter((Parameter<?>) param.clone());
            }
            ((Parameter<Double>) site.getParameter("Vs30")).setValue(siteKey.getVs30());
            sites.add(site);
        }

        // -------------------------
        // 5) Parallel hazard + ELoss computation
        // -------------------------
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < sites.size(); i++) {
            final Site site = sites.get(i);
            final ELossAsset.SiteKey siteKey = siteKeys.get(i);

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

                    // Loop over ImKeys (x-values per ImKey)
                    Map<ELossAsset.ImKey, List<ELossAsset>> imGroups =
                            elossPortfolio.getAssetsBySiteAndImKey().get(siteKey);

                    for (ELossAsset.ImKey imKey : imGroups.keySet()) {
                        // Build hazard function directly from ImKey x-values
                        DiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
                        for (double x : imKey.getLogImValues()) {
                            hazFunc.set(x, 0d);
                        }
                        
                        hazFunc = calc.getHazardCurve(hazFunc, site, gmm, erf);
                        
                        double[] hazardY = new double[hazFunc.size()];
                        for (int j = 0; j < hazFunc.size(); j++) {
                            hazardY[j] = hazFunc.getY(j);
                        }

                        for (ELossAsset asset : imGroups.get(imKey)) {
                            ELossCalculator calcEL = new ELossCalculator(
                                    elossVulnLib.getByName(asset.getVulnerabilityName()),
                                    hazardY,
                                    IntegrationMethod.RIEMANN
                            );
                            double nEL = calcEL.compute();
                            asset.setnEL(nEL);
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

        // Wait for all threads to finish
        for (CompletableFuture<Void> f : futures) f.join();

        System.out.println("ELoss computation complete.");
        elossPortfolio.printSummary();
    }
}