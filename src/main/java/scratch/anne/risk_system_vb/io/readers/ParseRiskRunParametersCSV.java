package scratch.anne.risk_system_vb.io.readers;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters.HazardMetric;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.util.StringUtil;

public final class ParseRiskRunParametersCSV {

    private ParseRiskRunParametersCSV() {}

    private static final Set<String> RESERVED_KEYS = Set.of(
            "portfolio_csv",
            "vuln_library_json",
            "fragility_library_json",
            "file_tag",
            "log_im_step",
            "integration_method",
            "erf_class",
            "erf_duration",
            "gmm",
            "hazard_metric",
            "probability_targets"
    );

    /**
     * Loads the entire CSV into a key/value map.
     */
    public static Map<String, String> loadConfig(Path csvPath)
            throws IOException {

        Map<String, String> map = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(csvPath)) {

            br.readLine(); // header

            String line;
            while ((line = br.readLine()) != null) {

                String[] tokens = line.split(",", 2);

                if (tokens.length == 2) {
                    map.put(
                            tokens[0].trim(),
                            tokens[1].trim()
                    );
                }
            }
        }

        return map;
    }

    /**
     * Extracts all non-reserved entries.
     *
     * Examples:
     * max_distance=200
     * min_mag=5.0
     */
    public static Map<String, String> extractSourceFilterConfig(
            Map<String, String> config) {

        Map<String, String> filterConfig = new HashMap<>();

        for (var e : config.entrySet()) {
            if (!RESERVED_KEYS.contains(e.getKey())) {
                filterConfig.put(e.getKey(), e.getValue());
            }
        }

        return filterConfig;
    }

    /**
     * Builds HazardParameters with defaults.
     */
    public static HazardParameters buildHazardParameters(
            Map<String, String> config) {

        String erfClass = config.get("erf_class");

        if (erfClass == null || erfClass.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required key: erf_class"
            );
        }

        String gmm = config.get("gmm");

        if (gmm == null || gmm.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required key: gmm"
            );
        }

        double erfDuration =
                StringUtil.parseDoubleOrDefault(
                        config.get("erf_duration"),
                        1.0
                );

        HazardMetric hazardMetric =
                parseHazardMetric(
                        config.get("hazard_metric")
                );

        Map<String, String> filterConfig =
                extractSourceFilterConfig(config);

        return new HazardParameters(
                erfClass,
                erfDuration,
                hazardMetric,
                gmm,
                filterConfig
        );
    }

    public static HazardMetric parseHazardMetric(String s) {

        if (s == null || s.isBlank()) {
            return HazardMetric.PROBABILITY_EXCEEDANCE;
        }

        String key = s.trim().toUpperCase();

        switch (key) {

            case "PROB":
            case "PROBABILITY":
            case "PROBABILITY_EXCEEDANCE":
                return HazardMetric.PROBABILITY_EXCEEDANCE;

            case "RATE":
            case "RATE_EXCEEDANCE":
                return HazardMetric.RATE_EXCEEDANCE;

            default:
                return HazardMetric.valueOf(key);
        }
    }
    
	  public static RiskConvolution.IntegrationMethod parseIntegrationMethod(String s) {
	  if (s == null || s.isBlank()) {
	      return RiskConvolution.IntegrationMethod.CLOSED_FORM;
	  }
	
	  return RiskConvolution.IntegrationMethod.valueOf(s.trim().toUpperCase());
	}
}
