package scratch.anne.risk_system_vb.examples;

import java.util.*;
import java.nio.file.*;

import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF;

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


public class PortfolioExpectedLossExample {

    public static void main(String[] args) throws Exception {
    	
        // --------------------------------------------------
        // 0. Input and output filenames
        // --------------------------------------------------
//    	String file_tag = "eLoss_p366_v0";
    	String file_tag = "test1";
    	
    	
    	Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\software architecture\\my_scratch\\conversion to Java project\\java_outputs\\_vb");
    	String outputFileName = file_tag + ".csv";
    	String aggregatedOutputFileName = file_tag + "_aggregated.csv";
    	Path outputCSV = baseFolder.resolve(outputFileName);
    	Path aggregatedOutputCSV = baseFolder.resolve(aggregatedOutputFileName);

        Path resourceFolder = Path.of("C:/Users/ahulsey/git/opensha-dev/src/main/resources/scratch/anne/risk_system_vb");
        Path vulnPortfolioCSV = resourceFolder.resolve("p366-portfolio_Porter-vuln-approximation_SHORT.csv");
        Path vulnLibraryJSON = resourceFolder.resolve("Porter_vulns_for_java.json");

        // ---------- Step 1: Load vulnerability portfolio ----------
        Portfolio<VulnerabilityAsset> basePortfolio =
                PortfolioReader.readCSV(vulnPortfolioCSV, VulnerabilityAsset.class);
        Set<String> assetModelNames = basePortfolio.getResponseModelNames();
        System.out.println("Base portfolio loaded: " + basePortfolio.size() + " assets");

        // ---------- Step 2: Load base vulnerability library ----------
        ResponseModelLibrary<VulnerabilityModel> vulnLib =
                VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);
        System.out.println("Basic vulnerability library loaded: " + vulnLib.size() + " items");

        // ---------- Step 3: Convert to ExpectedVuln library ----------
        ExpectedVulnLibrary expVulnLib =
                ExpectedVulnLibraryPreparer.prepare(vulnLib, assetModelNames);
        double logImStep = 0.075;
//        ExpectedVulnLibrary expVulnLib =
//                ExpectedVulnLibraryPreparer.prepare(vulnLib, assetModelNames,new ImValueTransformer.LogInterpTransformation(logImStep));
//        System.out.println("Expected vulnerability library created: " + expVulnLib.size() + " items");

        // ---------- Step 4: Wrap portfolio with IMKey mapping ----------
        ImIndexedPortfolio<VulnerabilityAsset> indexedPortfolio =
                ImIndexedPortfolio.of(basePortfolio, expVulnLib);

        // --------------------------------------------------
        // 5. Create ExpectedLossPortfolio (empty results)
        // --------------------------------------------------
        ExpectedLossPortfolio elossPortfolio =
                new ExpectedLossPortfolio(indexedPortfolio);
        
        
        // --------------------------------------------------
        // 6. Set up erf
        // --------------------------------------------------
        NSHM23_WUS_BranchAveragedERF erf = new NSHM23_WUS_BranchAveragedERF();
        erf.getTimeSpan().setDuration(1.0);
        erf.updateForecast();
        
        // --------------------------------------------------
        // 7. Create calculator
        // --------------------------------------------------
        ExpectedLossPortfolioCalculator calculator =
                new ExpectedLossPortfolioCalculator(
                        elossPortfolio,
                        expVulnLib,
                        AttenRelRef.ASK_2014,
                        erf
                );
//        ExpectedLossPortfolioCalculator calculator =
//                new ExpectedLossPortfolioCalculator(
//                        elossPortfolio,
//                        expVulnLib,
//                        AttenRelRef.ASK_2014,
//                        erf,
//                        RiskIntegral.IntegrationMethod.CLOSED_FORM
//                );
        
        // --------------------------------------------------
        // 8. Run calculation
        // --------------------------------------------------
        elossPortfolio = calculator.computeExpectedLoss();
        // Ensure portfolio has been marked as computed
        if (!elossPortfolio.isExpectedLossComputed()) {
            throw new IllegalStateException("Expected loss computation did not complete correctly!");
        }
        elossPortfolio.printSummary();

        // --------------------------------------------------
        // 9. Export
        // --------------------------------------------------
        elossPortfolio.writeCSV(outputCSV);
        elossPortfolio.writeAggregatedCSV(aggregatedOutputCSV);
    }
}
