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

            // ---------- Step 3: Prepare for EAL ----------
            List<ELossVulnerability> prepared = ELossVulnerabilityPreparer.prepare(
                    library,
                    portfolioNames,
                    false  // no interpolation
            );

            // ---------- Step 4: Print the prepared vulnerabilities ----------
            for (ELossVulnerability ev : prepared) {
            	 System.out.println(ev);
            }
            	
//                System.out.println("Prepared ELoss Vulnerability: " + ev.getName());
//                System.out.println("IM edges: " + Arrays.toString(ev.getImEdges()));
//                System.out.println("IM mid:   " + Arrays.toString(ev.getImMid()));
//                System.out.println("DR edges: " + Arrays.toString(ev.getDrEdges()));
//                System.out.println("DR mid:   " + Arrays.toString(ev.getDrMid()));
//                System.out.println();
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
