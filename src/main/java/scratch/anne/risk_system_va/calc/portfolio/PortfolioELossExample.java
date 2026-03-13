package scratch.anne.risk_system_va.calc.portfolio;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.opensha.commons.geo.Location;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

import scratch.anne.risk_system_va.calc.eloss.ELossCalculator.IntegrationMethod;
import scratch.anne.risk_system_va.portfolio.Asset;
import scratch.anne.risk_system_va.portfolio.Portfolio;
import scratch.anne.risk_system_va.portfolio.PortfolioReader;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerability;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerabilityLibrary;
import scratch.anne.risk_system_va.calc.eloss.ELossCalculator;
import scratch.anne.risk_system_va.hazard.RuptureContribution;
import scratch.anne.risk_system_va.vulnerabilities.IMT;

public class PortfolioELossExample {

    public static void main(String[] args) throws Exception {
    	
        // -------------------------
        // 0) Initialize ERF
        // -------------------------
        NSHM23_WUS_BranchAveragedERF erf = new NSHM23_WUS_BranchAveragedERF();
        erf.getTimeSpan().setDuration(1.0);
        erf.updateForecast();

        // -------------------------
        // 1) Define GMM reference and prepare deque
        // -------------------------
        AttenRelRef gmmRef = AttenRelRef.ASK_2014;
        ArrayDeque<ScalarIMR> gmmDeque = new ArrayDeque<>();
        ArrayDeque<HazardCurveCalculator> calcDeque = new ArrayDeque<>();

        // -------------------------
        // 2) Prepare portfolio
        // -------------------------
        Portfolio portfolio = PortfolioReader.readCSV(
            Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\portfolio.csv")
        );

        // -------------------------
        // 3) Group assets by site
        // -------------------------
        Map<Portfolio.SiteKey, List<Asset>> siteGroups = new HashMap<>();
        for (Asset asset : portfolio.getAssets()) {
            Portfolio.SiteKey siteKey = new Portfolio.SiteKey(
                asset.getLat(),
                asset.getLon(),
                asset.getVs30()
            );
            siteGroups.computeIfAbsent(siteKey, k -> new ArrayList<>()).add(asset);
        }

        // -------------------------
        // 4) Loop over sites (parallelizable)
        // -------------------------
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Portfolio.SiteKey siteKey : siteGroups.keySet()) {
            List<Asset> assetsAtSite = siteGroups.get(siteKey);

            futures.add(CompletableFuture.runAsync(() -> {
                // Thread-local GMM and hazard calculator
                ScalarIMR gmm;
                synchronized (gmmDeque) {
                    gmm = gmmDeque.isEmpty() ? gmmRef.get() : gmmDeque.pop();
                }

                HazardCurveCalculator calc;
                synchronized (calcDeque) {
                    calc = calcDeque.isEmpty() ? new HazardCurveCalculator() : calcDeque.pop();
                }

                // Set site parameters for GMM
                gmm.setSite(new Site(new Location(siteKey.getLat(), siteKey.getLon())));
                gmm.getParameter("Vs30").setValue(siteKey.getVs30());

                // -------------------------
                // 5) Group assets by IMT + x-values (from their vulnerability)
                // -------------------------
                Map<AssetGroupKey, List<Asset>> assetGroups = new HashMap<>();
                for (Asset asset : assetsAtSite) {
                    ELossVulnerability vuln = asset.getVuln(); // assumes asset references the prepared ELossVulnerability
                    AssetGroupKey key = new AssetGroupKey(vuln.getImt(), vuln.getImLevels());
                    assetGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(asset);
                }

                // -------------------------
                // 6) Compute hazard curves per unique IMT + x-values
                // -------------------------
                for (AssetGroupKey key : assetGroups.keySet()) {
                    DiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
                    boolean fullHazard = true; // replace with your logic

                    if (fullHazard) {
                        calc.getHazardCurve(hazFunc, new Site(siteKey.getLat(), siteKey.getLon()), gmm, erf);
                        for (Asset asset : assetGroups.get(key)) {
                            ELossCalculator calcEL = new ELossCalculator(asset.getVuln(), hazFunc, IntegrationMethod.RIEMANN);
                            double nEL = calcEL.compute();
                            System.out.printf("Asset: %s, nEL=%.6e%n", asset.getAssetID(), nEL);
                        }
                    } else {
                        List<RuptureContribution> rups = computeRuptureContributions(siteKey, gmm, erf, key);
                        for (Asset asset : assetGroups.get(key)) {
                            ELossCalculator calcEL = new ELossCalculator(asset.getVuln(), rups, IntegrationMethod.RIEMANN);
                            double nEL = calcEL.compute();
                            System.out.printf("Asset: %s, nEL=%.6e%n", asset.getAssetID(), nEL);
                        }
                    }
                }

                // Return GMM and calculator to deque
                synchronized (gmmDeque) { gmmDeque.push(gmm); }
                synchronized (calcDeque) { calcDeque.push(calc); }

            }));
        }

        // Wait for all threads to finish
        for (CompletableFuture<Void> f : futures) f.join();
    }

    // -------------------------
    // Helper class for grouping by IMT + x-values
    // -------------------------
    private static class AssetGroupKey {
        private final IMT imt;
        private final double[] xValues;
        private final int hash;

        public AssetGroupKey(IMT imt, double[] xValues) {
            this.imt = imt;
            this.xValues = xValues;
            this.hash = Arrays.hashCode(xValues) * 31 + imt.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AssetGroupKey)) return false;
            AssetGroupKey other = (AssetGroupKey) o;
            return imt == other.imt && Arrays.equals(xValues, other.xValues);
        }

        @Override
        public int hashCode() { return hash; }
    }
}