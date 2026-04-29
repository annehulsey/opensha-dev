package scratch.anne.risk_system_vb.engine.convolution;

public class ConvolutionResult {

    public final double risk;
    public final double[] binContribution;

    public ConvolutionResult(double risk, double[] binContribution) {
        this.risk = risk;
        this.binContribution = binContribution;
    }
}
