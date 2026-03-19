package scratch.anne.risk_system_va.portfolio;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Reads a CSV into a Portfolio.
 * <p>
 * The first six columns are required but can be in any order:
 * assetID, lat, lon, vs30, value, vulnModel.
 * Any additional columns are stored in extraGroupFields and tracked in the Portfolio.
 * </p>
 */
public class PortfolioReader {

    /** Required columns (case-insensitive) */
    private static final List<String> REQUIRED_COLUMNS = 
            Arrays.asList("assetID", "lat", "lon", "vs30", "value", "vulnModel");

    /**
     * Reads a CSV file and returns a Portfolio.
     * 
     * @param file Path to CSV file
     * @return Portfolio containing all assets
     * @throws IOException if reading the file fails
     * @throws IllegalArgumentException if required columns are missing
     */
    public static Portfolio readCSV(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) throw new IOException("CSV file is empty: " + file);
        
        // remove BOM format if data is from excel
        String firstLine = lines.get(0);
        if (firstLine.startsWith("\uFEFF")) firstLine = firstLine.substring(1);

        // Parse header
        String[] header = firstLine.split(",", -1); // comma-separated
        Map<String,Integer> colIndex = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            colIndex.put(header[i].trim(), i);
        }

        // Verify all required columns are present
        for (String col : REQUIRED_COLUMNS) {
            if (!colIndex.containsKey(col)) {
                throw new IllegalArgumentException("Missing required column: " + col);
            }
        }

        // Identify extra fields
        List<String> extraFieldNames = new ArrayList<>();
        for (String col : header) {
            if (!REQUIRED_COLUMNS.contains(col)) extraFieldNames.add(col);
        }

        // Read assets
        List<Asset> assets = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) continue; // skip empty lines
            String[] t = lines.get(i).split(",", -1); // keep empty strings

            // Collect extra fields for this row
            Map<String,String> extraFields = new LinkedHashMap<>();
            for (String extra : extraFieldNames) {
                int idx = colIndex.get(extra);
                extraFields.put(extra, t[idx]);
            }

            Asset asset = new Asset(
                t[colIndex.get("assetID")],
                Double.parseDouble(t[colIndex.get("lat")]),
                Double.parseDouble(t[colIndex.get("lon")]),
                Double.parseDouble(t[colIndex.get("vs30")]),
                Double.parseDouble(t[colIndex.get("value")]),
                t[colIndex.get("vulnModel")],
                extraFields
            );
            assets.add(asset);
        }

        return new Portfolio(
                assets,
                file.toString(),
                "value: USD, vs30: m/s",
                "vulnerability JSON",
                "WGS84",
                "portfolio description",
                "creation info",
                extraFieldNames
        );
    }
}