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

        // filter relevant vulnerabilities from the library
        List<VulnerabilityModel> relevantVulns = library.getVulnerabilityNames().stream()
                .filter(assetVulnNames::contains)
                .map(library::getVulnerability)
                .collect(Collectors.toList());

        // -------------------------------
        // 3) Prepare vulnerabilities for EAL
        // -------------------------------

        ELossVulnerabilityLibrary elossLib = null;
        List<ELossVulnerability> customELossVulns = null;

        boolean usePreparedVulns = false;  // true = use ELossVulnerabilityPreparer
        boolean useCustomVulns = false;   // true = build ELossVulnerability manually
        boolean skipELossVulns = true;   // true = skip ELossVulns entirely

        if (usePreparedVulns) {
            boolean interpolate = false;
            elossLib = ELossVulnerabilityPreparer.prepare(
                    library,
                    assetVulnNames,
                    interpolate
            );
        } else if (useCustomVulns) {
            customELossVulns = new ArrayList<>();
            for (VulnerabilityModel vm : relevantVulns) {
                double[] imLevels = vm.getImLevels();
                double[] meanDR = vm.getMeanDamageRatio();
                ELossVulnerability elv = new ELossVulnerability(imLevels, meanDR);
                customELossVulns.add(elv);
            }
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
        ELossCalculator.IntegrationMethod method = ELossCalculator.IntegrationMethod.RIEMANN;

        if (elossLib != null) {
            // Case 1: prepared ELossVulnerabilityLibrary
            for (ELossVulnerability elV : elossLib.all()) {
                validateIMMatch(elV.getImEdges(), hazardIMs);
                ELossCalculator calc = new ELossCalculator(elV, hazardValues, method);
                double nEL = calc.compute();
                System.out.printf("Vulnerability: %s, nEL = %.6e%n", elV.getName(), nEL);
            }

        } else if (customELossVulns != null) {
            // Case 2: manually built ELossVulnerabilities
            for (ELossVulnerability elV : customELossVulns) {
                validateIMMatch(elV.getImEdges(), hazardIMs);
                ELossCalculator calc = new ELossCalculator(elV, hazardValues, method);
                double nEL = calc.compute();
                System.out.printf("Vulnerability: %s, nEL = %.6e%n", elV.getName(), nEL);
            }

        } else if (skipELossVulns) {
            // Case 3: no ELossVulns, use raw vulnerability model
            for (VulnerabilityModel vm : relevantVulns) {
                ELossCalculator calc = new ELossCalculator(vm.getImLevels(), vm.getMeanDamageRatio(), hazardValues, method);
                double nEL = calc.compute();
                System.out.printf("Vulnerability: %s, nEL = %.6e%n", vm.getName(), nEL);
            }
        }
    }

    private static void validateIMMatch(double[] imEdges, double[] hazardIMs) {
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
    }
}