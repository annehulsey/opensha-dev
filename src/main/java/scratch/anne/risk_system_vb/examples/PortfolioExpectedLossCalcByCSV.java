package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;
import java.io.*;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.imr.AttenRelRef;

import scratch.anne.risk_system_vb.calc.convolution.RiskIntegral;
import scratch.anne.risk_system_vb.calc.portfolio_workflow.ExpectedLossPortfolioCalculator;
import scratch.anne.risk_system_vb.io.PortfolioReader;
import scratch.anne.risk_system_vb.io.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.portfolio.Portfolio;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ExpectedLossPortfolio;
import scratch.anne.risk_system_vb.portfolio.portfolio_wrappers.ImIndexedPortfolio;
import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibraryPreparer;
import scratch.anne.risk_system_vb.util.ImValueTransformer;
import scratch.anne.risk_system_vb.util.IO;

/**
 * Fully dynamic CSV-driven Expected Loss Portfolio runner with input verification.
 */
public class PortfolioExpectedLossCalcByCSV {

    public static void main(String[] args) throws Exception {
    	
    	Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\software architecture\\my_scratch\\conversion to Java project\\java_outputs\\_vb\\csv_inputs");
    	Path inputFolder = Path.of("p366\\PorterVulns_ASK14");

        Path runFolder = baseFolder.resolve(inputFolder);

        if (!Files.isDirectory(runFolder))
            throw new IllegalArgumentException("Run folder does not exist: " + runFolder);

        Path configCSV = runFolder.resolve("input.csv");

        if (!Files.exists(configCSV))
            throw new IllegalArgumentException("Missing input.csv in run folder: " + runFolder);

        Map<String, String> config = loadConfig(configCSV);

        System.out.println("Running portfolio from folder:");
        System.out.println(runFolder);

        // ------------------- 2. Resolve input files (can be shared) -------------------
        Path portfolioCSV = Path.of(config.get("portfolio_csv"));
        Path vulnLibraryJSON = Path.of(config.get("vuln_library_json"));

        // ----------- VERIFY INPUT FILES EXIST -----------
        IO.verifyFileExists(portfolioCSV, "Portfolio CSV");
        IO.verifyFileExists(vulnLibraryJSON, "Vulnerability JSON");

        // ------------------- 3. Output paths inside run folder -------------------
        String fileTag = config.getOrDefault("file_tag", runFolder.getFileName().toString());
        Path outputCSV = runFolder.resolve(fileTag + ".csv");
        Path aggregatedOutputCSV = runFolder.resolve(fileTag + "_aggregated.csv");

        String erfClassName = config.get("erf_class");
        String gmmName = config.get("gmm");
        String integrationMethod = config.getOrDefault("integration_method", "CLOSED_FORM");
        // Read the logImStep, defaulting to NaN if not provided
        String logImStepStr = config.get("log_im_step");
        double logImStep = (logImStepStr == null || logImStepStr.isBlank())
                ? Double.NaN
                : Double.parseDouble(logImStepStr);
        
        // ------------ VERIFY ERF and GMM EXIST -------------------
        try {
            Class<?> erfClass = Class.forName(erfClassName);
            if (!AbstractERF.class.isAssignableFrom(erfClass)) {
                throw new IllegalArgumentException("Specified ERF class does not extend AbstractERF: " + erfClassName);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("ERF class not found: " + erfClassName, e);
        }
        
        try {
            AttenRelRef.valueOf(gmmName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("GMM not recognized: " + gmmName, e);
        }

        // ------------------- 4. Load portfolio -------------------
        Portfolio<VulnerabilityAsset> basePortfolio =
                PortfolioReader.readCSV(portfolioCSV, VulnerabilityAsset.class);

        Set<String> assetModelNames = basePortfolio.getResponseModelNames();

        // ------------------- 5. Load vulnerability library -------------------
        ResponseModelLibrary<VulnerabilityModel> vulnLib =
                VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);

        // ------------------- 6. Prepare Expected Vulnerability Library -------------------
        ImValueTransformer transformer = Double.isNaN(logImStep)
                ? new ImValueTransformer.NoImTransformation()
                : new ImValueTransformer.LogInterpTransformation(logImStep);
        ExpectedVulnLibrary expVulnLib =
                ExpectedVulnLibraryPreparer.prepare(vulnLib, assetModelNames, transformer);

        // ------------------- 7. Wrap portfolio with IMKey mapping -------------------
        ImIndexedPortfolio<VulnerabilityAsset> indexedPortfolio =
                ImIndexedPortfolio.of(basePortfolio, expVulnLib);

        ExpectedLossPortfolio elossPortfolio = new ExpectedLossPortfolio(indexedPortfolio);

        // ------------------- 8. Dynamic ERF -------------------
        AbstractERF erf = (AbstractERF)
                Class.forName(erfClassName)
                        .getDeclaredConstructor()
                        .newInstance();

        erf.getTimeSpan().setDuration(1.0);
        erf.updateForecast();

        // ------------------- 9. Dynamic attenuation relation -------------------
        AttenRelRef gmm =
                AttenRelRef.valueOf(gmmName.toUpperCase());

        // ------------------- 10. Create calculator -------------------
        RiskIntegral.IntegrationMethod integrationMethodEnum =
                "RIEMANN".equalsIgnoreCase(integrationMethod)
                        ? RiskIntegral.IntegrationMethod.RIEMANN
                        : RiskIntegral.IntegrationMethod.CLOSED_FORM;
        
        ExpectedLossPortfolioCalculator calculator =
                new ExpectedLossPortfolioCalculator(
                        elossPortfolio,
                        expVulnLib,
                        gmm,
                        erf,
                        integrationMethodEnum
                );

        // ------------------- 11. Compute Expected Loss -------------------
        elossPortfolio = calculator.computeExpectedLoss();
        if (!elossPortfolio.isExpectedLossComputed()) {
            throw new IllegalStateException("Expected loss computation did not complete correctly!");
        }
        elossPortfolio.printSummary();

        // ------------------- 12. Write outputs to run folder -------------------
        elossPortfolio.writeCSV(outputCSV);
        elossPortfolio.writeAggregatedCSV(aggregatedOutputCSV);

        System.out.println("Outputs written to run folder:");
        System.out.println(outputCSV);
        System.out.println(aggregatedOutputCSV);
    }

    // ------------------- Helper: Load CSV config -------------------
    private static Map<String, String> loadConfig(Path csvPath) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(",", 2);
                if (tokens.length == 2) {
                    map.put(tokens[0].trim(), tokens[1].trim());
                }
            }
        }
        return map;
    }

//    // ------------------- Helper: Verify file exists -------------------
//    private static void verifyFileExists(Path path, String description) {
//        if (!Files.exists(path)) {
//            throw new IllegalArgumentException(description + " does not exist: " + path);
//        }
//    }
}