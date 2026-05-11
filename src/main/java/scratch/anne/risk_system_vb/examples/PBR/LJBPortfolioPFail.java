package scratch.anne.risk_system_vb.examples.PBR;

import java.nio.file.*;
import java.util.*;
import java.io.*;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.imr.AttenRelRef;

import scratch.anne.risk_system_vb.domain.asset.fragility.FragilityAsset;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.ExpectedLossPortfolioAggregator;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.PBRSurvivalPortfolioReporter;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.ProbFailurePortfolioReporter;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.FragilityModel;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.SimpleImFragilityLibraryPreparer;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.SimpleImFragilityLibraryPreparer.PrepareSpec;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.io.readers.PortfolioReader;
import scratch.anne.risk_system_vb.io.writers.HazardJsonWriter;
import scratch.anne.risk_system_vb.io.readers.FragilityLibraryReader;
import scratch.anne.risk_system_vb.io.InputConfigUtil;
import scratch.anne.risk_system_vb.util.IO;

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

        String erfClassName = config.get("erf_class");
        String gmmName = config.get("gmm");
        String integrationMethod = config.getOrDefault("integration_method", "CLOSED_FORM");
        
        List<Double> probabilityTargets =
                InputConfigUtil.parseDoubleList(config.get("probability_targets"));
        if (probabilityTargets.isEmpty()) {
            throw new IllegalArgumentException(
                "Config missing 'probability_targets'");
        }
        
        
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
                        fragilityLib,
                        gmm,
                        erf,
                        integrationMethodEnum
                );

        // ------------------- 11. Compute Probability of Failure -------------------
        riskConvolutionPortfolio = calculator.computeRiskConvolution();
        if (!riskConvolutionPortfolio.isRiskConvolutionComputed()) {
            throw new IllegalStateException("Risk convolution did not complete correctly.");
        }
        try (HazardJsonWriter writer =
		   new HazardJsonWriter(hazardJson)) {
        		writer.writePortfolio(riskConvolutionPortfolio);
		}
        
	    // ------------------- 12. Write PBR outputs to run folder -------------------
	    PBRSurvivalPortfolioReporter PBRreporter = new PBRSurvivalPortfolioReporter(riskConvolutionPortfolio, probabilityTargets);
	    
	    PBRreporter.writeCSV(outputCSV);

        System.out.println("Outputs written to run folder:");
        System.out.println(outputCSV);
        System.out.println(hazardJson);
        
    }

}