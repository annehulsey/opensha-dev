package scratch.anne.risk_system_va;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple hazard curve reader.
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
     * @param path    Path to hazard JSON file
     * @param source  "roes" or "poes"
     * @param timespan Timespan key, e.g., "1-yr", "5-yr"
     * @throws IOException if reading file fails
     */
    public TestHazardCurveReader(Path path, String source, String timespan) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(path));

        // Read IM levels
        JsonNode imlsNode = root.get("imls");
        imls = new double[imlsNode.size()];
        for (int i = 0; i < imlsNode.size(); i++) {
            imls[i] = imlsNode.get(i).asDouble();
        }

        // Read hazard values
        JsonNode sourceNode = root.get(source);
        if (sourceNode == null) {
            throw new IllegalArgumentException("Source '" + source + "' not found in JSON");
        }

        JsonNode tsNode = sourceNode.get(timespan);
        if (tsNode == null) {
            throw new IllegalArgumentException("Timespan '" + timespan + "' not found in source '" + source + "'");
        }

        hazardValues = new double[tsNode.size()];
        for (int i = 0; i < tsNode.size(); i++) {
            hazardValues[i] = tsNode.get(i).asDouble();
        }

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