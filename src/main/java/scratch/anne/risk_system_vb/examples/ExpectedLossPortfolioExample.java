package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_vb.io.PortfolioReader;
import scratch.anne.risk_system_vb.io.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.portfolio.Portfolio;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ImIndexedPortfolio;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ExpectedLossPortfolio;
import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibraryPreparer;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

public class ExpectedLossPortfolioExample {

    public static void main(String[] args) {
        try {
            Path resourceFolder = Path.of("C:/Users/ahulsey/git/opensha-dev/src/main/resources/scratch/anne/risk_system_vb");
            Path vulnPortfolioCSV = resourceFolder.resolve("p366-portfolio_Porter-vuln-approximation_SHORT.csv");
            Path vulnLibraryJSON = resourceFolder.resolve("Porter_vulns_for_java.json");

            // ---------- Step 1: Load vulnerability portfolio ----------
            Portfolio<VulnerabilityAsset> basePortfolio =
                    PortfolioReader.readCSV(vulnPortfolioCSV, VulnerabilityAsset.class);
            Set<String> assetModelNames = basePortfolio.getResponseModelNames();
            System.out.println("Base portfolio loaded: " + basePortfolio.size() + " assets");

            // ---------- Step 2: Load base vulnerability library ----------
            ResponseModelLibrary<VulnerabilityModel> vulnLib =
                    VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);
            System.out.println("Basic vulnerability library loaded: " + vulnLib.size() + " items");

            // ---------- Step 3: Convert to ExpectedVuln library ----------
            ExpectedVulnLibrary expVulnLib =
                    ExpectedVulnLibraryPreparer.prepare(vulnLib, assetModelNames);
            System.out.println("Expected vulnerability library created: " + expVulnLib.size() + " items");

            // ---------- Step 4: Wrap portfolio with IMKey mapping ----------
            ImIndexedPortfolio<VulnerabilityAsset> indexedPortfolio =
                    ImIndexedPortfolio.of(basePortfolio, expVulnLib);

            // ---------- Step 5: Project to Expected Loss Portfolio ----------
            ExpectedLossPortfolio expLossPortfolio = new ExpectedLossPortfolio(indexedPortfolio);

            System.out.println("ExpectedLossPortfolio created with " +
                    expLossPortfolio.getAssets().size() + " assets");

            // ---------- Step 6: Inspect key groupings ----------
            for (ImKey imKey : indexedPortfolio.getImKeys()) {
                var assetsByIm = expLossPortfolio.getAssetsByImKey(imKey);
                System.out.printf("ImKey %s has %d expected loss assets%n", imKey, assetsByIm.size());
            }

            for (SiteKey siteKey : indexedPortfolio.getSiteKeys()) {
                var assetsAtSite = expLossPortfolio.getAssetsBySite(siteKey);
                System.out.printf("Site %s has %d expected loss assets%n", siteKey, assetsAtSite.size());
            }

            // ---------- Step 7: Aggregate example ----------
            double totalEL = expLossPortfolio.getTotalExpectedLoss();
            System.out.printf("Total expected loss: %.6e%n", totalEL);

            Map<SiteKey, Double> elBySite = expLossPortfolio.getExpectedLossBySite();
            for (var entry : elBySite.entrySet()) {
                System.out.printf("Site %s total EL: %.6e%n", entry.getKey(), entry.getValue());
            }

            // ---------- Step 8: Optional CSV export ----------
            Path outputCSV = resourceFolder.resolve("expected_loss_portfolio.csv");
            expLossPortfolio.writeCSV(outputCSV);
            System.out.println("Expected loss portfolio written to CSV: " + outputCSV);

            Path aggregatedCSV = resourceFolder.resolve("expected_loss_summary.csv");
            expLossPortfolio.writeAggregatedCSV(aggregatedCSV);
            System.out.println("Aggregated summary written to CSV: " + aggregatedCSV);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}