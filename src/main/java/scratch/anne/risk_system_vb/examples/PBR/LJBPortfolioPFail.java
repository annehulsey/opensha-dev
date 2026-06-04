package scratch.anne.risk_system_vb.examples.PBR;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_vb.domain.asset.fragility.FragilityAsset;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters.HazardMetric;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.PBRSurvivalPortfolioReporter;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio.ConvolutionMode;
import scratch.anne.risk_system_vb.domain.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.FragilityModel;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.SimpleImFragilityLibraryPreparer;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.SimpleImFragilityLibraryPreparer.PrepareSpec;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.io.readers.PortfolioReader;
import scratch.anne.risk_system_vb.io.readers.FragilityLibraryReader;
import scratch.anne.risk_system_vb.io.InputConfigUtil;
import scratch.anne.risk_system_vb.util.IO;
import scratch.anne.risk_system_vb.util.StringUtil;

public class LJBPortfolioPFail {

    public static void main(String[] args) throws Exception {
    	
    	Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\precarious-balanced-rock\\results\\");
    	Path inputFolder = Path.of("LJBport_all-LJB1frag");
    	
    	boolean writeHazard = true;

        Path runFolder = baseFolder.resolve(inputFolder);

        if (!Files.isDirectory(runFolder))
            throw new IllegalArgumentException("Run folder does not exist: " + runFolder);

        Path configCSV = runFolder.resolve("input.csv");

        if (!Files.exists(configCSV))
            throw new IllegalArgumentException("Missing input.csv in run folder: " + runFolder);

        Map<String, String> config = InputConfigUtil.loadConfig(configCSV);

        System.out.println("Running portfolio from folder:");
        System.out.println(runFolder);

        // ------------------- 2. Resolve input files (can be shared) -------------------
        Path portfolioCSV = Path.of(config.get("portfolio_csv"));
        Path fragLibraryJSON = Path.of(config.get("fragility_library_json"));

        // ----------- VERIFY INPUT FILES EXIST -----------
        IO.verifyFileExists(portfolioCSV, "Portfolio CSV");
        IO.verifyFileExists(fragLibraryJSON, "Fragility JSON");

        // ------------------- 3. Output paths inside run folder -------------------
        String fileTag = config.getOrDefault("file_tag", runFolder.getFileName().toString());
        Path outputCSV = runFolder.resolve(fileTag + ".csv");
        Path hazardJson = writeHazard
                ? runFolder.resolve(fileTag + "_hazard-list.json")
                : null;
        
        List<Double> probabilityTargets =
                InputConfigUtil.parseDoubleList(config.get("probability_targets"));
        if (probabilityTargets.isEmpty()) {
            throw new IllegalArgumentException(
                "Config missing 'probability_targets'");
        }
        
        
        // ----------- PREPARE HAZARD AND INTEGRATION PARAMETERS -----------
        HazardParameters hazardParameters = new HazardParameters(
                config.get("erf_class"),
                StringUtil.parseDoubleOrDefault(config.get("erf_duration"), 1.0),
                parseHazardMetric(config.get("hazard_metric")),
                config.get("gmm")
        );
        
        RiskConvolution.IntegrationMethod integrationMethod = parseIntegrationMethod(config.get("integration_method"));
        double logImStep = StringUtil.parseDoubleOrDefault(config.get("log_im_step"), Double.NaN);

        // ------------------- 4. Load portfolio -------------------
        Portfolio<FragilityAsset> basePortfolio =
                PortfolioReader.readCSV(portfolioCSV, FragilityAsset.class);

        Set<String> portfolioFragilities = basePortfolio.getResponseModelNames();

        // ------------------- 5. Load fragility library -------------------
        ResponseModelLibrary<FragilityModel> baseFragilityLib =
                FragilityLibraryReader.readLibrary(fragLibraryJSON);

        // ------------------- 6. Prepare SimpleFragility Library -------------------
        PrepareSpec spec = PrepareSpec.builder()
        		.filterNames(portfolioFragilities).build();
        SimpleImResponseLibrary fragilityLib = SimpleImFragilityLibraryPreparer.prepare(baseFragilityLib, spec);

        // ------------------- 7. Wrap portfolio with IMKey mapping -------------------
        RiskConvolutionPortfolio riskConvolutionPortfolio = new RiskConvolutionPortfolio(basePortfolio, fragilityLib);

        // ------------------- 10. Create calculator -------------------       
        PortfolioRiskConvolutionCalculator calculator =
                new PortfolioRiskConvolutionCalculator(
                        riskConvolutionPortfolio,
                        fragilityLib,
                        hazardParameters,
                        integrationMethod
                );

        // ------------------- 11. Compute Probability of Failure -------------------
        riskConvolutionPortfolio = calculator.computeRisk();
        riskConvolutionPortfolio.assertConvolutionExecutedAs(ConvolutionMode.FULL_HCURVE);
        riskConvolutionPortfolio.exportHazard(hazardJson);
        
	    // ------------------- 12. Write PBR outputs to run folder -------------------
	    PBRSurvivalPortfolioReporter PBRreporter = new PBRSurvivalPortfolioReporter(riskConvolutionPortfolio, probabilityTargets);
	    
	    PBRreporter.writeCSV(outputCSV);

        System.out.println("Outputs written to run folder:");
        System.out.println(outputCSV);
        System.out.println(hazardJson);
        
    }
    
    public static HazardMetric parseHazardMetric(String s) {

        if (s == null || s.isBlank())
            return HazardMetric.PROBABILITY_EXCEEDANCE;

        String key = s.trim().toUpperCase();

        switch (key) {

            // ---- probability aliases ----
            case "PROB":
            case "PROBABILITY":
            case "PROBABILITY_EXCEEDANCE":
                return HazardMetric.PROBABILITY_EXCEEDANCE;

            // ---- rate aliases ----
            case "RATE":
            case "RATE_EXCEEDANCE":
                return HazardMetric.RATE_EXCEEDANCE;

            default:
                // still allow exact enum names
                try {
                    return HazardMetric.valueOf(key);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Unknown hazard metric: " + s 
                    );
                }
        }
    }
    
    public static RiskConvolution.IntegrationMethod parseIntegrationMethod(String s) {
        if (s == null || s.isBlank()) {
            return RiskConvolution.IntegrationMethod.CLOSED_FORM;
        }

        return RiskConvolution.IntegrationMethod.valueOf(s.trim().toUpperCase());
    }

}