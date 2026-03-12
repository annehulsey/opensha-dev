package scratch.anne.risk_system_va.calc.eloss;

import org.opensha.commons.data.function.DiscretizedFunc;

/**
 * Expected loss (EL) calculator for a single vulnerability and hazard curve.
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
public class ELossCalculator {

    public enum IntegrationMethod { RIEMANN, CLOSED_FORM }

    private final ELossVulnerability vuln;  // prepared vulnerability (includes imEdges, drEdges, drMid, etc.)
    private double[] hazardValues;          // either rate or probability of exceedance, at vuln's imEdges
                                            // can also be null, if hazard will be supplied as .compute(hazard)
    private final IntegrationMethod method;

 // ===================== Constructors =====================

    /**
     * Constructor using a pre-built vulnerability and hazard array.
     * Integration method defaults to RIEMANN if not specified.
     *
     * @param vuln        Vulnerability prepared for nEL calculations (includes imEdges, drEdges, drMid, etc.)
     * @param hazardValue Hazard value (rate or probability of exceedance at vuln's imEdges)
     * @param method      Integration method (default: RIEMANN)
     */
    public ELossCalculator(ELossVulnerability vuln, double[] hazard, IntegrationMethod method) {
        if (hazard != null && vuln.getImEdges().length != hazard.length) {
            throw new IllegalArgumentException("vuln IM and hazard array lengths must match");
        }
        this.vuln = vuln;
        this.hazardValues = (hazard != null) ? hazard.clone() : null;
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Convenience constructor using a pre-built vulnerability and hazard array.
     * Uses default RIEMANN integration method.
     *
     * @param vuln   Prepared ELossVulnerability
     * @param hazard Hazard array
     */
    public ELossCalculator(ELossVulnerability vuln, double[] hazard) {
        this(vuln, hazard, null);
    }

    /**
     * Constructor using a pre-built vulnerability and hazard DiscretizedFunc.
     * Checks that the hazard x-values match the vulnerability's IM edges.
     * Default integration method is RIEMANN if not specified.
     *
     * @param vuln       Prepared ELossVulnerability
     * @param hazardFunc Hazard as DiscretizedFunc (x = IM, y = hazard value)
     * @param method     Integration method; if null, defaults to RIEMANN
     */
    public ELossCalculator(ELossVulnerability vuln, DiscretizedFunc hazardFunc, IntegrationMethod method) {
        this.vuln = vuln;
        this.hazardValues = convertHazFuncToArray(hazardFunc, vuln.getImEdges());
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Convenience constructor using a pre-built vulnerability and hazard DiscretizedFunc.
     * Defaults to RIEMANN integration method.
     *
     * @param vuln       Prepared ELossVulnerability
     * @param hazardFunc Hazard as DiscretizedFunc
     */
    public ELossCalculator(ELossVulnerability vuln, DiscretizedFunc hazardFunc) {
        this(vuln, hazardFunc, null);
    }

    /**
     * Constructor that builds an internal ELossVulnerability from raw IM and DR arrays,
     * along with a hazard array. Integration method defaults to RIEMANN if not specified.
     *
     * @param im       Intensity measure array
     * @param dr       Damage ratio array
     * @param hazard   Hazard values corresponding to IMs
     * @param method   Integration method; if null, defaults to RIEMANN
     */
    public ELossCalculator(double[] im, double[] dr, double[] hazard, IntegrationMethod method) {
        if (im.length != dr.length || im.length != hazard.length) {
            throw new IllegalArgumentException("IM, DR, and hazard arrays must all be the same length");
        }
        this.vuln = new ELossVulnerability(im, dr);
        this.hazardValues = hazard.clone();
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Convenience constructor that builds a vulnerability from IM/DR arrays
     * and uses default RIEMANN integration method.
     *
     * @param im     Intensity measure array
     * @param dr     Damage ratio array
     * @param hazard Hazard array
     */
    public ELossCalculator(double[] im, double[] dr, double[] hazardValues) {
        this(im, dr, hazardValues, null);
    }

    /**
     * Constructor using only the vulnerability.
     * Hazard must be supplied later via compute().
     * Integration method defaults to RIEMANN if not specified.
     *
     * @param vuln   Prepared ELossVulnerability
     * @param method Integration method; if null, defaults to RIEMANN
     */
    public ELossCalculator(ELossVulnerability vuln, IntegrationMethod method) {
        this.vuln = vuln;
        this.hazardValues = null;
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

 // -------------------- Compute Overloads --------------------

    /** Use stored hazard array */
    public double compute() {
        if (hazardValues == null) {
            throw new IllegalStateException("Hazard array is not set. Provide hazard to compute.");
        }
        return computeInternal(hazardValues);
    }

    /** Provide hazard as double array */
    public double compute(double[] hazard) {
        if (hazard.length != vuln.getImEdges().length) {
            throw new IllegalArgumentException("Hazard array length does not match vulnerability IM length");
        }
        this.hazardValues = hazard.clone();
        return computeInternal(hazardValues);
    }

    /** Provide hazard as DiscretizedFunc (checks IMs) */
    public double compute(DiscretizedFunc hazardFunc) {
        this.hazardValues = convertHazFuncToArray(hazardFunc, vuln.getImEdges());
        return computeInternal(hazardValues);
    }
    
 // ---------------------- Internal shared compute ----------------------

    /**
     * Shared internal computation, chooses method based on this.method
     */
    private double computeInternal(double[] hazardValues) {
        switch (method) {
            case RIEMANN:
                return computeRiemann(hazardValues);
            case CLOSED_FORM:
                return computeClosedForm(hazardValues);
            default:
                throw new IllegalStateException("Unsupported integration method: " + method);
        }
    }
    // ---------------------- Core Riemann Integration ----------------------
    private double computeRiemann(double[] hazard) {
        double nEL = 0.0;
        double[] drMid = vuln.getDrMid();
        
        int n = drMid.length;

        // interior bins
        for (int i = 0; i < drMid.length - 1; i++) {
            double deltaHazard = hazardValues[i] - hazardValues[i + 1];
            nEL += drMid[i] * deltaHazard;
        }

        // tail bin
        nEL += drMid[n - 1] * hazardValues[n - 1];

        return nEL;
    }


    // ---------------------- Porter Closed-Form Integration ----------------------
    private double computeClosedForm(double[] hazardValues) {
        double[] iml = vuln.getImEdges();
        double[] df = vuln.getDrEdges();

        double nEL = 0.0;

        for (int i = 1; i < iml.length; i++) {
            double imlDelta = iml[i] - iml[i - 1];
            double dfDelta = df[i] - df[i - 1];

            double g = Math.log(hazardValues[i] / hazardValues[i - 1]) / imlDelta;

            double term1 = df[i - 1] * hazardValues[i - 1] * (1.0 - Math.exp(g * imlDelta));
            double term2 = (dfDelta / imlDelta) * hazardValues[i - 1] *
                           (Math.exp(g * imlDelta) * (imlDelta - 1.0 / g) + 1.0 / g);

            double deltaNEL = term1 - term2;
            if (!Double.isNaN(deltaNEL) && !Double.isInfinite(deltaNEL)) {
                nEL += deltaNEL;
            }
        }

        return nEL;
    }
    
 // -------------------- Helper --------------------

    /** Converts hazard function to array, ensuring IMs match */
    private static double[] convertHazFuncToArray(DiscretizedFunc hazardFunc, double[] vulnIMs) {
        if (hazardFunc.size() != vulnIMs.length) {
            throw new IllegalArgumentException("Hazard function size does not match vulnerability IM length");
        }

        double[] hazardValues = new double[vulnIMs.length];
        for (int i = 0; i < vulnIMs.length; i++) {
            if (hazardFunc.getX(i) != vulnIMs[i]) {
                throw new IllegalArgumentException(
                    String.format("IM values do not match at index %d: vuln IM=%f, hazard IM=%f",
                                  i, vulnIMs[i], hazardFunc.getX(i))
                );
            }
            hazardValues[i] = hazardFunc.getY(i);
        }
        return hazardValues;
    }
}