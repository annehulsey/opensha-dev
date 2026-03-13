package scratch.anne.risk_system_va.portfolio;

import java.nio.file.*;
import java.util.*;

public class PortfolioReaderExample {

    public static void main(String[] args) {

        try {

            // ---------- Step 1: Read portfolio CSV ----------
            Path filePath = Paths.get(
                "C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\portfolio.csv"
            );

            Portfolio portfolio = PortfolioReader.readCSV(filePath);

            // ---------- Step 2: Print portfolio summary ----------
            System.out.println("Portfolio loaded:");
            System.out.println(portfolio);
            System.out.println("Number of assets: " + portfolio.size());

            // ---------- Step 3: Print first few assets ----------
            System.out.println("\nAssets:");
            for (Asset asset : portfolio.getAssets()) {

                System.out.printf(
                    "%s | lat=%.3f lon=%.3f | vs30=%.0f | value=%.0f | vuln=%s%n",
                    asset.getAssetID(),
                    asset.getLat(),
                    asset.getLon(),
                    asset.getVs30(),
                    asset.getValue(),
                    asset.getVulnModel()
                );
            }

            // ---------- Step 4: Extract vulnerability models ----------
            List<String> vulnNames = portfolio.getVulnerabilityNames();

            System.out.println("\nVulnerability models referenced:");
            for (String name : vulnNames) {
                System.out.println(" - " + name);
            }

            // These names are what you pass into ELossVulnerabilityPreparer
            // Example:
            //
            // ELossVulnerabilityLibrary elossLib =
            //     ELossVulnerabilityPreparer.prepare(
            //         library,
            //         vulnNames,
            //         false
            //     );

        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
