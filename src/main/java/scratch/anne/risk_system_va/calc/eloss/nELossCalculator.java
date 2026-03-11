package scratch.anne.risk_system_va.calc.eloss;

/**
 * Basic normalized expected loss (nEL) calculator for a single vulnerability and hazard curve.
 * <blockquote>
 * Loss is normalized by the asset value (0 to 1). <br>
 * Expected Loss depends on the hazard definition, typically Expected Annual Loss (EAL). <br>
 * Accepts either rate or probability of exceedance. NOTE: risk convolution is formally defined in rate-space. <br>
 * Uses Riemann or Closed-Form integration.
 * <blockquote>
 * 		Close-Form solution: <br>
 * 		Porter, K.A.,C.R. Scawthorn, and J.L. Beck, 2006. Cost-effectiveness of stronger
 *			woodframe buildings. <br>
 *          Earthquake Spectra 22 (1), February 2006, 239-266, Equation #2 <br>
 *		    http://www.sparisk.com/pubs/Porter-2006-woodframe.pdf
 *			</blockquote>
 * </blockquote>
 */
public class nELossCalculator {

    public enum IntegrationMethod { RIEMANN, CLOSED_FORM }

    private final ELossVulnerability vuln;  // prepared vulnerability (includes imEdges, drEdges, drMid, etc.)
    private final double[] hazardValue;   // either rate or probability of exceedance, at vuln's imEdges
    private final IntegrationMethod method;

    /**
     * Constructor.
     *
     * @param vuln        Vulnerability prepared for nEL calculations (includes imEdges, drEdges, drMid, etc.)
     * @param hazardValue Hazard value (rate or probability of exceedance at vuln's imEdges)
     * @param method      Integration method (default: RIEMANN)
     */
    public nELossCalculator(ELossVulnerability vuln,
                         double[] hazardValue,
                         IntegrationMethod method) {
        if (vuln.getImEdges().length != hazardValue.length)
            throw new IllegalArgumentException("vulnIM and hazardValue arrays must have the same length");

        this.vuln = vuln;
        this.hazardValue = hazardValue.clone();
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Compute normalized expected loss.
     *
     * @return nEL (unitless, normalized by vulnerability; multiply by asset value externally)
     */
    public double compute() {
        switch (method) {
            case RIEMANN:
                return computeRiemann();
            case CLOSED_FORM:
                return computeClosedForm();
            default:
                throw new IllegalStateException("Unsupported integration method: " + method);
        }
    }

    // ---------------------- Core Riemann Integration ----------------------
    private double computeRiemann() {
        double nEL = 0.0;
        double[] drMid = vuln.getDrMid();

        // Compute delta hazardValue for each bin
        double[] deltaHazard = new double[hazardValue.length];
        for (int i = 0; i < hazardValue.length - 1; i++) {
            deltaHazard[i] = hazardValue[i] - hazardValue[i + 1];
        }
        deltaHazard[hazardValue.length - 1] = hazardValue[hazardValue.length - 1]; // tail bin
        // adding the tail hazard as the left edge of the final bin is consistent with
        // the vulnerability's construction of the mid-bin DR values + final DR value

        // Riemann sum
        for (int i = 0; i < drMid.length; i++) {
            nEL += drMid[i] * deltaHazard[i];
        }

        return nEL;
    }

    // ---------------------- Porter Closed-Form Integration ----------------------
    private double computeClosedForm() {
        double[] iml = vuln.getImEdges();
        double[] df = vuln.getDrEdges();
        double[] mafe = hazardValue;  // MAFE = hazard values directly

        double nEL = 0.0;

        for (int i = 1; i < iml.length; i++) {
            double imlDelta = iml[i] - iml[i - 1];
            double dfDelta = df[i] - df[i - 1];

            double g = Math.log(mafe[i] / mafe[i - 1]) / imlDelta;

            double term1 = df[i - 1] * mafe[i - 1] * (1.0 - Math.exp(g * imlDelta));
            double term2 = (dfDelta / imlDelta) * mafe[i - 1] *
                           (Math.exp(g * imlDelta) * (imlDelta - 1.0 / g) + 1.0 / g);

            double deltaNEL = term1 - term2;
            if (!Double.isNaN(deltaNEL) && !Double.isInfinite(deltaNEL)) {
                nEL += deltaNEL;
            }
        }

        return nEL;
    }
}