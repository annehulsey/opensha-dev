package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_vb.io.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibraryPreparer;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnerability;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey.ImDomain;

public class ExpectedVulnLibraryReaderExample {

    public static void main(String[] args) {
        try {
            // ---------- Step 1: Load vulnerability model library ----------
            Path filePath = Paths.get(
                "C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_vb\\vulnerabilities.json"
            );

            ResponseModelLibrary<VulnerabilityModel> modelLibrary =
                    VulnerabilityLibraryReader.readLibrary(
                            filePath,
                            "library source",
                            "description",
                            "DR → fraction, IM → g",
                            "date, author, workflow v2.0"
                    );

            System.out.println("Model library loaded: " + modelLibrary);

            
            // ----- Step 2: Define vuln names included in portfolio-------
            List<String> portfolioVulnNames = Arrays.asList(
            	    "W1-lowrise",
            	    "W2-midrise"
            	);

            // ---------- Step 3: Prepare ExpectedVulnerabilities ----------
            ExpectedVulnLibrary expVulnLib =
                    ExpectedVulnLibraryPreparer.prepare(
                            modelLibrary,
                            new HashSet<>(portfolioVulnNames)
                    );

            System.out.println("\nExpectedVulnerabilityLibrary: " + expVulnLib);

            // ---------- Step 4a: Print all vulnerabilities ----------
            System.out.println("\nAll Expected Vulnerabilities:");
            for (ExpectedVulnerability ev : expVulnLib.getModels()) {
                System.out.println(ev);
            }

            // ---------- Step 4b: Example lookup by IMKey ----------
            ExpectedVulnerability example =
                    expVulnLib.getModels().iterator().next();

            String imtStr = example.getImtString();
            double[] imValues = example.getImValues();

            List<ExpectedVulnerability> grouped =
                    expVulnLib.getByImKey(new ImKey(imtStr, imValues, ImDomain.LOG_IM));

            System.out.println("\nVulnerabilities sharing IMKey with: " + example.getName());
            for (ExpectedVulnerability ev : grouped) {
                System.out.println(" - " + ev.getName());
            }

            // ---------- Step 4c: Example lookup by name ----------
            String testName = portfolioVulnNames.get(0);
            ExpectedVulnerability byName = expVulnLib.getByName(testName);

            System.out.println("\nLookup by name (" + testName + "):");
            System.out.println(byName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
