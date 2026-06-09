package scratch.anne.risk_system_vb.examples;

import java.nio.file.*;
import java.util.*;

import scratch.anne.risk_system_vb.domain.asset.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.ExpectedLossPortfolioAggregator;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.vulnerabilities.SimpleImVulnLibraryPreparer;
import scratch.anne.risk_system_vb.domain.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioPerRuptureRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.io.readers.ParseRiskRunParametersCSV;
import scratch.anne.risk_system_vb.io.readers.PortfolioReader;
import scratch.anne.risk_system_vb.io.readers.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.util.ImValueTransformer;
import scratch.anne.risk_system_vb.util.StringUtil;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio.ConvolutionMode;
import scratch.anne.risk_system_vb.util.IO;

/**
 * Fully dynamic CSV-driven Expected Loss Portfolio runner with input verification.
 */
public class PortfolioExpectedLossDualCalcByCSV {
	
	public static List<ConvolutionMode> convolutionModes = List.of(ConvolutionMode.FULL_HCURVE,ConvolutionMode.PER_RUPTURE);

    public static void main(String[] args) throws Exception {
    	
    	Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\BERM\\results\\EAL\\rate\\porter_vulns");
    	Path inputFolder = Path.of("PorterVulns_vs-360");
    	
    	boolean writeHazard = true;

        Path runFolder = baseFolder.resolve(inputFolder);

        if (!Files.isDirectory(runFolder))
            throw new IllegalArgumentException("Run folder does not exist: " + runFolder);

        Path configCSV = runFolder.resolve("input.csv");

        if (!Files.exists(configCSV))
            throw new IllegalArgumentException("Missing input.csv in run folder: " + runFolder);

        System.out.println("Running portfolio from folder:");
        System.out.println(runFolder);
        
        Map<String, String> config =
                ParseRiskRunParametersCSV.loadConfig(configCSV);

        Map<String, String> filterConfig =
                ParseRiskRunParametersCSV.extractSourceFilterConfig(config);

        // ------------------- 1. Resolve input files -------------------
        Path portfolioCSV = Path.of(config.get("portfolio_csv"));
        Path vulnLibraryJSON = Path.of(config.get("vuln_library_json"));

        IO.verifyFileExists(portfolioCSV, "Portfolio CSV");
        IO.verifyFileExists(vulnLibraryJSON, "Vulnerability JSON");

        // ------------------- 2. create output paths inside run folder -------------------
        String fileTag = config.getOrDefault("file_tag", runFolder.getFileName().toString());
        Path fullOutputCSV = runFolder.resolve(fileTag + "_all-assets.csv");
        Path aggregatedOutputCSV = runFolder.resolve(fileTag + "_aggregated.csv");
        Path perRuptureOutputCSV = runFolder.resolve(fileTag + "_per-rup.csv");
        Path hazardJson = writeHazard
                ? runFolder.resolve(fileTag + "_hazard-list.json")
                : null;

        
        // ------------------- 3. prepare hazard and integration parameters ----------------
        HazardParameters hazardParameters = new HazardParameters(
                config.get("erf_class"),
                StringUtil.parseDoubleOrDefault(
                        config.get("erf_duration"),
                        1.0
                ),
                ParseRiskRunParametersCSV.parseHazardMetric(config.get("hazard_metric")),
                config.get("gmm"),
                filterConfig
        );
        
        RiskConvolution.IntegrationMethod integrationMethod = ParseRiskRunParametersCSV.parseIntegrationMethod(config.get("integration_method"));
        double logImStep = StringUtil.parseDoubleOrDefault(config.get("log_im_step"), Double.NaN);
        

        // ------------------- 4. Load portfolio -------------------
        System.out.println("Loading portfolio...");
        Portfolio<VulnerabilityAsset> basePortfolio =
                PortfolioReader.readCSV(portfolioCSV, VulnerabilityAsset.class);

        Set<String> portfolioVulns = basePortfolio.getResponseModelNames();

        // ------------------- 5. Load vulnerability library -------------------
        System.out.println("Loading full vulnerability library...");
        ResponseModelLibrary<VulnerabilityModel> vulnLib =
                VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);

        // ------------------- 6. Prepare Expected Vulnerability Library -------------------
        System.out.println("Preparing vulnerability library...");
        ImValueTransformer transformer = Double.isNaN(logImStep)
                ? new ImValueTransformer.NoImTransformation()
                : new ImValueTransformer.LogInterpTransformation(logImStep);
        SimpleImResponseLibrary expVulnLib =
                SimpleImVulnLibraryPreparer.prepare(vulnLib, portfolioVulns, transformer);

        for (ConvolutionMode convolutionMode : convolutionModes) {
	        if (convolutionMode == ConvolutionMode.FULL_HCURVE) {
	        	
	        	RiskConvolutionPortfolio riskConvolutionPortfolio = new RiskConvolutionPortfolio(basePortfolio, expVulnLib);
	        	
	        	PortfolioRiskConvolutionCalculator calculator =
	                    new PortfolioRiskConvolutionCalculator(
	                            riskConvolutionPortfolio,
	                            expVulnLib,
	                            hazardParameters,
	                            integrationMethod
	                    );
	            
	            riskConvolutionPortfolio = calculator.computeRisk();
	            riskConvolutionPortfolio.assertConvolutionExecutedAs(ConvolutionMode.FULL_HCURVE);
	            riskConvolutionPortfolio.exportHazard(hazardJson);
	            ExpectedLossPortfolioAggregator aggregator = new ExpectedLossPortfolioAggregator(riskConvolutionPortfolio);
	            aggregator.writeCSV(fullOutputCSV);
	            aggregator.writeAggregatedCSV(aggregatedOutputCSV);
	            
	            System.out.println("Outputs written to run folder:");
	            System.out.println(fullOutputCSV);
	            System.out.println(aggregatedOutputCSV);
	            System.out.println(hazardJson);

	        }
	        else if (convolutionMode == ConvolutionMode.PER_RUPTURE) {
	        	
	        	RiskConvolutionPortfolio riskConvolutionPortfolio = new RiskConvolutionPortfolio(basePortfolio, expVulnLib);
	        	
	            PortfolioPerRuptureRiskConvolutionCalculator calculator =
	                    new PortfolioPerRuptureRiskConvolutionCalculator(
	                            riskConvolutionPortfolio,
	                            expVulnLib,
	                            hazardParameters,
	                            integrationMethod
	                    );
	            
	            riskConvolutionPortfolio = calculator.computeRisk();
	            riskConvolutionPortfolio.assertConvolutionExecutedAs(ConvolutionMode.PER_RUPTURE);
	            riskConvolutionPortfolio.exportRuptureResults(perRuptureOutputCSV);
	            
	            System.out.println("Outputs written to run folder:");
	            System.out.println(perRuptureOutputCSV);
        	
	        }
        }
    }
}