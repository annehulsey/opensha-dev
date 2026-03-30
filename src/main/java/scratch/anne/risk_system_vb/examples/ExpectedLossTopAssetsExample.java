package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ExpectedLossPortfolio;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.ExpectedLossAsset;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ImIndexedPortfolio;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.io.PortfolioReader;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibraryPreparer;
import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.io.VulnerabilityLibraryReader;

public class ExpectedLossTopAssetsExample {

    public static void main(String[] args) {
        try {
            Path resourceFolder = Path.of("C:/Users/ahulsey/git/opensha-dev/src/main/resources/scratch/anne/risk_system_vb");
            Path vulnPortfolioCSV = resourceFolder.resolve("p366-portfolio_Porter-vuln-approximation_SHORT.csv");
            Path vulnLibraryJSON = resourceFolder.resolve("Porter_vulns_for_java.json");

            // Load base portfolio
            List<VulnerabilityAsset> basePortfolio = PortfolioReader.readCSV(vulnPortfolioCSV, VulnerabilityAsset.class).getAssets();
            Set<String> assetModelNames = new HashSet<>();
            for (VulnerabilityAsset a : basePortfolio) assetModelNames.addAll(a.getModelName());

            // Load vulnerability library
            ResponseModelLibrary<VulnerabilityModel> vulnLib = VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);

            // Convert to ExpectedVuln library
            ExpectedVulnLibrary expVulnLib = ExpectedVulnLibraryPreparer.prepare(vulnLib, assetModelNames);

            // Wrap portfolio
            ImIndexedPortfolio<VulnerabilityAsset> indexedPortfolio = ImIndexedPortfolio.of(basePortfolio, expVulnLib);

            // Project to ExpectedLossPortfolio
            ExpectedLossPortfolio elPortfolio = new ExpectedLossPortfolio(indexedPortfolio);

            // --- Find top 10 assets by expected loss ---
            List<ExpectedLossAsset> sortedAssets = new ArrayList<>(elPortfolio.getAssets());
            sortedAssets.sort(Comparator.comparingDouble(ExpectedLossAsset::getExpectedLoss).reversed());

            System.out.println("Top 10 assets by expected loss:");
            for (int i = 0; i < Math.min(10, sortedAssets.size()); i++) {
                ExpectedLossAsset a = sortedAssets.get(i);
                System.out.printf("%d: AssetID=%s, Site=(%.4f,%.4f), Vulnerability=%s, Value=%.2f, ExpectedLoss=%.6e%n",
                        i + 1,
                        a.getAssetID(),
                        a.getLatitude(),
                        a.getLongitude(),
                        a.getModelName(),
                        a.getValue(),
                        a.getExpectedLoss());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}