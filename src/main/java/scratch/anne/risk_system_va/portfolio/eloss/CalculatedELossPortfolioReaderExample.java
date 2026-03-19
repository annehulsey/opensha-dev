package scratch.anne.risk_system_va.portfolio.eloss;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Example class demonstrating how to read an ELossPortfolio from a CSV file
 * and access its contents.
 *
 * <p>This example:
 * <ul>
 *   <li>Loads a CSV using ELossPortfolio.readCSV()</li>
 *   <li>Prints a summary of expected losses</li>
 *   <li>Demonstrates access to dynamic extra grouping fields</li>
 * </ul>
 */
public class CalculatedELossPortfolioReaderExample {

    public static void main(String[] args) {

        try {
            // -------------------------
            // 1) Input CSV path
            // -------------------------
        	String file_tag = "eLoss_p366_v0";
//        	String file_tag = "test1";
        	String baseFolder = "C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\software architecture\\my_scratch\\conversion to Java project\\java_outputs";
        	String inputFileName = file_tag + ".csv";
        	String outputFileName = file_tag + "_aggregated.csv";

        	Path inputCSV = Paths.get(baseFolder, inputFileName);
        	Path outputCSV = Paths.get(baseFolder, outputFileName);
        	
        	// -------------------------
            // 2) Read ELossPortfolio
            // -------------------------
            ELossPortfolio portfolio = ELossPortfolio.readCSV(inputCSV);

            System.out.println("CSV successfully loaded from: " + inputCSV);

            // -------------------------
            // 3) Print summary
            // -------------------------
            portfolio.printSummary();

            // -------------------------
            // 4) Inspect extra fields
            // -------------------------
            System.out.println("\nExtra field names:");
            for (String field : portfolio.getExtraFieldNames()) {
                System.out.println("  " + field);
            }

            // Example: group by first available extra field
            if (!portfolio.getExtraFieldNames().isEmpty()) {
            	for (String field : portfolio.getExtraFieldNames()) {

            	    System.out.println("\nAggregation by: " + field);

            	    Map<String, List<ELossAsset>> grouped =
            	            portfolio.getAssetsByExtraGroupField(field);

            	    for (var entry : grouped.entrySet()) {
            	        double total = entry.getValue().stream()
            	                .mapToDouble(ELossAsset::getExpectedLoss)
            	                .sum();

            	        System.out.printf("  %s -> %.6e%n", entry.getKey(), total);
            	    }
            	}
            }
            
            // -------------------------
            // 4) Print to CSV
            // -------------------------
            portfolio.writeAggregatedCSV(outputCSV);

        } catch (Exception e) {
            System.err.println("Error reading ELoss CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
