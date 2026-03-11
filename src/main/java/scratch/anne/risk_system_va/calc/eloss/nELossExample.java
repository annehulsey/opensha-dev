package scratch.anne.risk_system_va.calc.eloss;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_va.hazard.TestHazardCurveReader;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibrary;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibraryReader;

public class nELossExample {

    public static void main(String[] args) throws Exception {
        // -------------------------------
        // 1) Read vulnerability library
        // -------------------------------
        Path vulnPath = Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\GEM_vulns_for_java.json");
        VulnerabilityLibrary library = VulnerabilityLibraryReader.readLibrary(
                vulnPath,
                "library source",
                "description",
                "DR → fraction, IM → g",
                "date, author, workflow v1.0"
            );

        // -------------------------------
        // 2) Define portfolio assets
        // -------------------------------
        List<String> assetNames = Arrays.asList("C1H-h-COM10-DF", "C1H-h-AGR1-DF");

        // -------------------------------
        // 3) Prepare vulnerabilities for EAL
        // -------------------------------
        boolean interpolate = false; // placeholder; can enable interpolation later
        List<ELossVulnerability> elVulns = ELossVulnerabilityPreparer.prepare(
                library,
                assetNames,
                interpolate
        );

        // -------------------------------
        // 4) Define hazard curve
        // -------------------------------
        Path hazardpath = Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\test_single_site_hcurves.json");
        TestHazardCurveReader reader = new TestHazardCurveReader(hazardpath);
        // retrieve arrays
        double[] hazard_imls = reader.getImls();
        double[] hazardValue = reader.getHazardValues();
        
        // -------------------------------
        // 5) Compute normalized expected loss
        // -------------------------------
        for (ELossVulnerability elV : elVulns) {
        	
        	// confirm that the test hazard values are for the same intensities
            double[] imEdges = elV.getImEdges();
            if (imEdges.length != hazard_imls.length) {
                throw new IllegalStateException("IM length mismatch between vulnerability and hazard curve");
            }
            // check element-wise equality
            for (int i = 0; i < imEdges.length; i++) {
                if (Double.compare(imEdges[i], hazard_imls[i]) != 0) {
                    throw new IllegalStateException(
                        "IM mismatch at index " + i + ": vuln=" + imEdges[i] + ", hazard=" + hazard_imls[i]
                    );
                }
            }
            
            // create new calculator
	        nELossCalculator calc = new nELossCalculator(
	                elV,
	                hazardValue,
	                nELossCalculator.IntegrationMethod.RIEMANN // default
	        );
	
	        double nEL = calc.compute();
	        System.out.printf("Vulnerability: %s, nEL = %.6e%n", elV.getName(), nEL);
	        }
    }
}
