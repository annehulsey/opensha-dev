package scratch.anne.risk_system_va.portfolio.eloss;

import scratch.anne.risk_system_va.portfolio.Portfolio;
import scratch.anne.risk_system_va.portfolio.PortfolioReader;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibrary;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibraryReader;
import scratch.anne.risk_system_va.portfolio.Asset;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerability;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerabilityLibrary;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerabilityPreparer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ELossPortfolioExample {

    public static void main(String[] args) {
    	
    	try {
    	
        // ---------- Step 1: Read portfolio CSV ----------
        Path portfolioPath = Paths.get(
            "C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\portfolio.csv"
        );
        Portfolio portfolio = PortfolioReader.readCSV(portfolioPath);
        List<String> vulnNames = portfolio.getVulnerabilityNames();
        // ---------- Step 2: Load a vulnerability library ----------
        Path vulnerabilityPath = Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\vulnerabilities.json");
        VulnerabilityLibrary library = VulnerabilityLibraryReader.readLibrary(
                vulnerabilityPath,
                "library source",
                "description",
                "DR → fraction, IM → g",
                "date, author, workflow v1.0"
            );
        
        // ---------- Step 3: Prepare for estimated loss calcs ----------
        ELossVulnerabilityLibrary elossVulnLib = ELossVulnerabilityPreparer.prepare(
                library,
                vulnNames,
                false  // no interpolation
        );

        // --- Prepare ELossPortfolio ---
        ELossPortfolio elossPortfolio = ELossPortfolioPreparer.prepare(portfolio, elossVulnLib);

        // --- Print basic info ---
        System.out.println("Total ELoss assets: " + elossPortfolio.getAssets().size());
        System.out.println("Site keys: " + elossPortfolio.getSiteKeys());

        // --- Print assets by site ---
        for (ELossAsset.SiteKey siteKey : elossPortfolio.getSiteKeys()) {
            List<ELossAsset> assetsAtSite = elossPortfolio.getAssetsBySite(siteKey);
            System.out.println("Assets at site " + siteKey + ":");
            for (ELossAsset asset : assetsAtSite) {
                System.out.println("  " + asset.getVulnerabilityName() + ", value=" + asset.getValue()
                        + ", IMT=" + asset.getImKey().getIMT());
            }

            // Assets by IM key
            Map<ELossAsset.ImKey, List<ELossAsset>> imGroups =
                    elossPortfolio.getAssetsBySiteAndImKey().get(siteKey);
            for (ELossAsset.ImKey imKey : imGroups.keySet()) {
                System.out.println("  IM group " + imKey + ": " + imGroups.get(imKey).size() + " asset(s)");
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
