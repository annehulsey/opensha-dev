package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_vb.io.PortfolioReader;
import scratch.anne.risk_system_vb.io.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.portfolio.Portfolio;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ImIndexedPortfolio;
import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.SimpleImVulnLibraryPreparer;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

public class ImIndexedPortfolioExample {

    public static void main(String[] args) {
        try {
        	
            Path resourceFolder = Path.of("C:/Users/ahulsey/git/opensha-dev/src/main/resources/scratch/anne/risk_system_vb");
            Path vulnPortfolioCSV = resourceFolder.resolve("p366-portfolio_Porter-vuln_SHORT.csv");
            Path vulnLibraryJSON = resourceFolder.resolve("Porter_vulns_for_java.json");
            
//            Path vulnPortfolioCSV = resourceFolder.resolve("portfolio.csv");
//            Path vulnLibraryJSON = resourceFolder.resolve("vulnerabilities.json");
            
            // ---------- Step 1: Create synthetic portfolio ----------
            Portfolio<VulnerabilityAsset> basePortfolio =
                    PortfolioReader.readCSV(vulnPortfolioCSV, VulnerabilityAsset.class);
            Set<String> assetModelNames = basePortfolio.getResponseModelNames();

            System.out.println("Base portfolio loaded: " + basePortfolio.size() + " assets");

            // ---------- Step 2: Load base Vulnerability library ----------
            ResponseModelLibrary<VulnerabilityModel> vulnLib =
                    VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);

            System.out.println("Basic vulnerability library loaded: " + vulnLib.size() + " items");

         // ---------- Step 3: Convert to ExpectedVuln library ----------
            SimpleImResponseLibrary expVulnLib =
                    SimpleImVulnLibraryPreparer.prepare(vulnLib, assetModelNames);

            System.out.println("Expected vulnerability library created: " + expVulnLib.size() + " items");

            // ---------- Step 4: Wrap portfolio with IMKey mapping ----------
            ImIndexedPortfolio<VulnerabilityAsset> indexedPortfolio =
                    ImIndexedPortfolio.of(basePortfolio, expVulnLib);
            
            // ----- Check Key groupings ----
            for (ImKey imKey : indexedPortfolio.getImKeys()) {
                var assetsByIm = indexedPortfolio.getAssetsByImKey(imKey);
                System.out.printf("ImKey %s has %d assets%n", imKey, assetsByIm.size());
            }

            for (SiteKey siteKey : indexedPortfolio.getSiteKeys()) {
                var assetsAtSite = indexedPortfolio.getAssetsBySite(siteKey);
                System.out.printf("Site %s has %d assets%n", siteKey, assetsAtSite.size());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}