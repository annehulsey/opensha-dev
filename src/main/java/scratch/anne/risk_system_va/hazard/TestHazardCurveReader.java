package scratch.anne.risk_system_va.hazard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Simple hazard curve reader using GSON.
 * Defaults to ROEs and 1-yr timespan.
 */
public class TestHazardCurveReader {

    private final double[] imls;
    private final double[] hazardValues;

    /**
     * Constructor: read hazard JSON file using defaults (ROE, 1-yr).
     *
     * @param path Path to hazard JSON file
     * @throws IOException if reading file fails
     */
    public TestHazardCurveReader(Path path) throws IOException {
        this(path, "roes", "1-yr");  // defaults
    }

    /**
     * Constructor: read hazard JSON file with optional source/timespan.
     *
     * @param path     Path to hazard JSON file
     * @param source   "roes" or "poes"
     * @param timespan Timespan key, e.g., "1-yr", "5-yr"
     * @throws IOException if reading file fails
     */
    public TestHazardCurveReader(Path path, String source, String timespan) throws IOException {
        String jsonContent = Files.readString(path);
        Gson gson = new Gson();

        // Parse JSON as nested Map
        Map<String, Object> root = gson.fromJson(jsonContent, new TypeToken<Map<String, Object>>() {}.getType());

        // Read IM levels
        List<Double> imlsList = gson.fromJson(gson.toJson(root.get("imls")), new TypeToken<List<Double>>() {}.getType());
        if (imlsList == null) throw new IllegalArgumentException("IM levels 'imls' not found in JSON");
        imls = imlsList.stream().mapToDouble(Double::doubleValue).toArray();

        // Read hazard values
        Map<String, Object> sourceMap = gson.fromJson(gson.toJson(root.get(source)), new TypeToken<Map<String, Object>>() {}.getType());
        if (sourceMap == null) throw new IllegalArgumentException("Source '" + source + "' not found in JSON");

        List<Double> hazardList = gson.fromJson(gson.toJson(sourceMap.get(timespan)), new TypeToken<List<Double>>() {}.getType());
        if (hazardList == null) throw new IllegalArgumentException("Timespan '" + timespan + "' not found in source '" + source + "'");

        hazardValues = hazardList.stream().mapToDouble(Double::doubleValue).toArray();

        // Verify sizes
        if (hazardValues.length != imls.length) {
            throw new IllegalStateException("IM levels and hazard values length mismatch");
        }
    }

    /** Get hazard IM levels */
    public double[] getImls() {
        return imls.clone();
    }

    /** Get hazard values (rate or probability) */
    public double[] getHazardValues() {
        return hazardValues.clone();
    }
}