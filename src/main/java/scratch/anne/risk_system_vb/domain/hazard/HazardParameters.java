package scratch.anne.risk_system_vb.domain.hazard;

import java.util.Objects;

/**
 * Immutable metadata describing how a hazard field was generated.
 *
 * <p>This is intentionally separate from the hazard values themselves so that:
 * <ul>
 *   <li>metadata is not duplicated per Site/IM</li>
 *   <li>hazard values remain pure numeric results</li>
 *   <li>risk can reuse hazard under identical assumptions</li>
 * </ul>
 */
public final class HazardParameters {

    public enum HazardMetric {
        RATE_EXCEEDANCE,
        PROBABILITY_EXCEEDANCE,
        RATE_OCCURENCE,
        PROBABILITY_OCCURENCE
    }

    private final String erfName;
    private final double erfDurationYears;

    private final HazardMetric hazardMetric;

    private final String gmmName;

    public HazardParameters(
            String erfName,
            double erfDurationYears,
            HazardMetric hazardMetric,
            String gmmName
    ) {
        this.erfName = Objects.requireNonNull(erfName);
        this.erfDurationYears = erfDurationYears;
        this.hazardMetric = Objects.requireNonNull(hazardMetric);
        this.gmmName = Objects.requireNonNull(gmmName);
    }

    public String getErfName() {
        return erfName;
    }

    public double getErfDurationYears() {
        return erfDurationYears;
    }

    public HazardMetric getHazardMetric() {
        return hazardMetric;
    }

    public String getGmmName() {
        return gmmName;
    }

    @Override
    public String toString() {
        return "HazardParameters{" +
                "erf='" + erfName + '\'' +
                ", duration=" + erfDurationYears +
                ", hazard metric=" + hazardMetric +
                ", gmm='" + gmmName + '\'' +
                '}';
    }
}
