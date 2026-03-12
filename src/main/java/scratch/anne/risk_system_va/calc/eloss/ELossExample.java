package scratch.anne.risk_system_va.calc.eloss;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import scratch.anne.risk_system_va.hazard.TestHazardCurveReader;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibrary;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityLibraryReader;
import scratch.anne.risk_system_va.vulnerabilities.VulnerabilityModel;

public class ELossExample {

    public static void main(String[] args) throws Exception {
        // -------------------------------
        // 1) Read vulnerability library
        // -------------------------------
        Path vulnPath = Paths.get(
            "C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\GEM_vulns_for_java.json"
        );
        VulnerabilityLibrary library = VulnerabilityLibraryReader.readLibrary(
                vulnPath,
                "library source",
                "description",
                "DR → fraction, IM → g",
                "date, author, workflow v1.0"
        );

        // -------------------------------
        // 2) Define portfolio assets' vulnerabilities
        // -------------------------------
        List<String> assetVulnNames = Arrays.asList("C1H-h-COM10-DF", "C1H-h-AGR1-DF");

        // required if using the Vuln im and drs directly
        List<VulnerabilityModel> relevantVulns = library.getVulnerabilityNames().stream()
                .filter(assetVulnNames::contains)
                .map(library::getVulnerability)
                .collect(Collectors.toList());
        
        // -------------------------------
        // 3) Prepare vulnerabilities for EAL
        // -------------------------------
        
        List<ELossVulnerability> elVulns = new ArrayList<>();
        if (false) { // set to true for using prepared ELossVulns
	        if (true) { // set to true for using vulnerability preparer, false for im and dr values
	            // Use the vulnerability preparer
	            boolean interpolate = false; // placeholder; can enable interpolation later
	            elVulns = ELossVulnerabilityPreparer.prepare(
	                    library,
	                    assetVulnNames,
	                    interpolate
	            );
	        } else {
	            // Direct construction of ELossVulns from IM/DR arrays
	            for (VulnerabilityModel vm : relevantVulns) {
	                double[] imLevels = vm.getImLevels();
	                double[] meanDR = vm.getMeanDamageRatio();
		            ELossVulnerability exampleVuln = new ELossVulnerability(imLevels, meanDR);
		            elVulns.add(exampleVuln);
	            }
	        }
	        
        } else {
        	elVulns = null; // sets elVulns to null so that the calculation will go into a different loop
        }

        // -------------------------------
        // 4) Define hazard curve
        // -------------------------------
        Path hazardPath = Paths.get(
            "C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\test_single_site_hcurves.json"
        );
        TestHazardCurveReader reader = new TestHazardCurveReader(hazardPath);
        double[] hazardIMs = reader.getImls();
        double[] hazardValues = reader.getHazardValues();

        // -------------------------------
        // 5) Compute normalized expected loss
        // -------------------------------
        
        // Declare and assign the enum value
        ELossCalculator.IntegrationMethod method = ELossCalculator.IntegrationMethod.RIEMANN;
//        method = ELossCalculator.IntegrationMethod.CLOSED_FORM;

        // calculation loops
        if (elVulns != null) { // elVulns exists to loop over them for calculations
        
	        for (ELossVulnerability elV : elVulns) {
	
	            // Check that hazard IMs match vuln IMs
	            double[] imEdges = elV.getImEdges();
	            if (imEdges.length != hazardIMs.length) {
	                throw new IllegalStateException("IM length mismatch between vulnerability and hazard curve");
	            }
	            for (int i = 0; i < imEdges.length; i++) {
	                if (Double.compare(imEdges[i], hazardIMs[i]) != 0) {
	                    throw new IllegalStateException(
	                        "IM mismatch at index " + i + ": vuln=" + imEdges[i] + ", hazard=" + hazardIMs[i]
	                    );
	                }
	            }
	
	            // Create ELossCalculator
	            ELossCalculator calc = new ELossCalculator(elV, hazardValues, method);
	
	            double nEL = calc.compute();
	            System.out.printf("Vulnerability: %s, nEL = %.6e%n", elV.getName(), nEL);
	        }
        }
        else { // elVulns don't exist so loop over the original vulns and input im and dr directly
        	for (VulnerabilityModel vm : relevantVulns) {
                double[] imLevels = vm.getImLevels();
                double[] meanDR = vm.getMeanDamageRatio();
                // Create ELossCalculator
	            ELossCalculator calc = new ELossCalculator(imLevels, meanDR, hazardValues, method);
	            double nEL = calc.compute();
	            System.out.printf("Vulnerability: %s, nEL = %.6e%n", vm.getName(), nEL);
        	}
        	
        }
    }
}