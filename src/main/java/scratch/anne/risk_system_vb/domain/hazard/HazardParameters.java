package scratch.anne.risk_system_vb.domain.hazard;

import java.util.Objects;

import org.opensha.sha.imr.AttenRelRef;

/**
 * Immutable metadata describing how a hazard field was generated.
 */
public final class HazardParameters {
	
	public enum HazardMetricType {
	    EXCEEDANCE,
	    OCCURRENCE
	}

	public enum HazardMetric {
	    RATE_EXCEEDANCE(HazardMetricType.EXCEEDANCE),
	    PROBABILITY_EXCEEDANCE(HazardMetricType.EXCEEDANCE),
	    RATE_OCCURENCE(HazardMetricType.OCCURRENCE),
	    PROBABILITY_OCCURENCE(HazardMetricType.OCCURRENCE);

	    private final HazardMetricType metricType;

	    HazardMetric(HazardMetricType metricType) {
	        this.metricType = metricType;
	    }

	    public HazardMetricType getHazardMetricType() {
	        return metricType;
	    }
	}

    private final String erfName;
    private final double erfDuration;

    private final HazardMetric hazardMetric;

    private final String gmmName;

    public HazardParameters(
            String erfName,
            double erfDuration,
            HazardMetric hazardMetric,
            String gmmName
    ) {
        this.erfName = Objects.requireNonNull(erfName);
        this.erfDuration = erfDuration;
        this.hazardMetric = Objects.requireNonNull(hazardMetric);
        this.gmmName = Objects.requireNonNull(gmmName);
        
        // -------------------------------
        // Validate ERF class exists
        // -------------------------------
        try {
            Class<?> erfClass = Class.forName(erfName);

            if (!org.opensha.sha.earthquake.AbstractERF.class.isAssignableFrom(erfClass)) {
                throw new IllegalArgumentException(
                        "ERF class must extend AbstractERF: " + erfName
                );
            }

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("ERF class not found: " + erfName, e);
        }

        // -------------------------------
        // Validate GMM exists
        // -------------------------------
        try {
            AttenRelRef.valueOf(gmmName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("GMM not recognized: " + gmmName, e);
        }
    }

    public String getErfName() {
        return erfName;
    }

    public double getErfDuration() {
        return erfDuration;
    }

    public HazardMetric getHazardMetric() {
        return hazardMetric;
    }
    
    public HazardMetricType getHazardMetricType() {
        return hazardMetric.getHazardMetricType();
    }

    public String getGmmName() {
        return gmmName;
    }

    @Override
    public String toString() {
        return "HazardParameters{" +
                "erf='" + erfName + '\'' +
                ", duration=" + erfDuration +
                ", hazard metric=" + hazardMetric +
                ", gmm='" + gmmName + '\'' +
                '}';
    }
}
