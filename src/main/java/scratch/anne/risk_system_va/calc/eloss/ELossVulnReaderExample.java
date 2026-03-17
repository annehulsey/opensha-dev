package scratch.anne.risk_system_va.calc.eloss;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibrary;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibraryReader;

public class ELossVulnReaderExample {

    public static void main(String[] args) {
        try {
            // ---------- Step 1: Load a vulnerability library ----------
            Path filePath = Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\vulnerabilities.json");
            VulnerabilityLibrary library = VulnerabilityLibraryReader.readLibrary(
                    filePath,
                    "library source",
                    "description",
                    "DR → fraction, IM → g",
                    "date, author, workflow v1.0"
                );

            System.out.println("Library loaded: " + library);

            // ---------- Step 2: Define the portfolio ----------
            List<String> portfolioNames = Arrays.asList("W1-lowrise", "W2-midrise");

            // ---------- Step 3: Prepare for estimated loss calcs ----------
            ELossVulnerabilityLibrary elossLib = ELossVulnerabilityPreparer.prepare(
                    library,
                    portfolioNames,
                    false  // no interpolation
            );

         // ---------- Step 4a: Print all vulnerabilities ----------
            System.out.println("\nAll prepared ELoss Vulnerabilities:");
            for (ELossVulnerability ev : elossLib.all()) {
            	System.out.println(ev);
            }

            // ---------- Step 4b: Example lookup by IMT + x-values key ----------
            // Pick first vulnerability as an example from the library
            ELossVulnerability example = elossLib.all().iterator().next();  // first element

            String imtStr = example.getImtString();
            double[] xValues = example.getImValues();

            List<ELossVulnerability> grouped = elossLib.getByKey(imtStr, xValues);
            System.out.println("\nVulnerabilities sharing the same IMT + x-values as " + example.getName() + ":");
            for (ELossVulnerability ev : grouped) {
                System.out.println(" - " + ev.getName());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}