package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;
import java.io.*;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.imr.AttenRelRef;

import scratch.anne.risk_system_vb.domain.asset.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.ExpectedLossPortfolioAggregator;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.vulnerabilities.SimpleImVulnLibraryPreparer;
import scratch.anne.risk_system_vb.domain.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.io.readers.PortfolioReader;
import scratch.anne.risk_system_vb.io.readers.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.util.ImValueTransformer;
import scratch.anne.risk_system_vb.util.IO;

/**
 * Fully dynamic CSV-driven Expected Loss Portfolio runner with input verification.
 */
public class PortfolioExpectedLossCalcByCSV {

    public static void main(String[] args) throws Exception {
    	
    	Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\software architecture\\my_scratch\\conversion to Java project\\java_outputs\\_vb\\csv_inputs");
//    	Path inputFolder = Path.of("p366\\PorterVulns");
    	Path inputFolder = Path.of("tests\\short_portfolio");
    	
    	boolean writeHazard = true;

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
        Path hazardJson = writeHazard
                ? runFolder.resolve(fileTag + "_hazard.json")
                : null;

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
        SimpleImResponseLibrary expVulnLib =
                SimpleImVulnLibraryPreparer.prepare(vulnLib, assetModelNames, transformer);

        // ------------------- 7. Wrap portfolio with IMKey mapping -------------------
        RiskConvolutionPortfolio riskConvolutionPortfolio = new RiskConvolutionPortfolio(basePortfolio, expVulnLib);

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
        RiskConvolution.IntegrationMethod integrationMethodEnum =
                "RIEMANN".equalsIgnoreCase(integrationMethod)
                        ? RiskConvolution.IntegrationMethod.RIEMANN
                        : RiskConvolution.IntegrationMethod.CLOSED_FORM;
        
        PortfolioRiskConvolutionCalculator calculator =
                new PortfolioRiskConvolutionCalculator(
                        riskConvolutionPortfolio,
                        expVulnLib,
                        gmm,
                        erf,
                        integrationMethodEnum,
                        hazardJson
                );

        // ------------------- 11. Compute Expected Loss -------------------
        riskConvolutionPortfolio = calculator.computeRiskConvolution();
        if (!riskConvolutionPortfolio.isRiskConvolutionComputed()) {
            throw new IllegalStateException("Risk convolution did not complete correctly.");
        }
        ExpectedLossPortfolioAggregator aggregator = new ExpectedLossPortfolioAggregator(riskConvolutionPortfolio);
        aggregator.printSummary();

        // ------------------- 12. Write outputs to run folder -------------------
        aggregator.writeCSV(outputCSV);
        aggregator.writeAggregatedCSV(aggregatedOutputCSV);

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

}