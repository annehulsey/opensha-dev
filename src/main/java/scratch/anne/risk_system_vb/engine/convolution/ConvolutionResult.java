package scratch.anne.risk_system_vb.engine.convolution;

public class ConvolutionResult {

	public final double[] imls;
    public final double risk;
    public final double[] disaggContribution;
    public final double[] disaggCumulative;

    public ConvolutionResult(double[] imls, double[] disaggContribution, double risk) {
    	this.imls = imls;
        this.risk = risk;
        this.disaggContribution = disaggContribution.clone();
        this.disaggCumulative = buildCumulative(this.disaggContribution);
    }
    
    private static double[] buildCumulative(double[] contribution) {

        double[] cumulative = new double[contribution.length];

        double running = 0.0;

        for (double v : contribution) {
            running += v;
        }

        if (running == 0.0) {
            // avoid divide-by-zero; return flat zeros
            return cumulative;
        }

        double norm = 1.0 / running;

        double delta = 0.0;

        for (int i = 0; i < contribution.length; i++) {
            delta += contribution[i];
            cumulative[i] = delta * norm;
        }

        // enforce exact closure (numerical safety)
        cumulative[cumulative.length - 1] = 1.0;

        return cumulative;
    }
    
    public double getRisk() {
    	return risk;
    }
}
