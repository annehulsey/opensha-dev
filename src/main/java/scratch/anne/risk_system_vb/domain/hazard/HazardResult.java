package scratch.anne.risk_system_vb.domain.hazard;

public final class HazardResult {

    private final double[] probabilities;
    //TODO incorporate input flags for rates, probabilities, duration

    public HazardResult(double[] probabilities) {
        this.probabilities = probabilities;
    }

    public double[] getProbabilities() {
        return probabilities;
    }
}
