package scratch.anne.risk_system_vb.calc.convolution;

import org.opensha.commons.data.function.DiscretizedFunc;
import scratch.anne.risk_system_vb.util.HazardFunctionUtil;

/**
 * Simple risk convolution integral, for a given hazard and a simple response given hazard function (e.g., a vulnerability or fragility).
 * <p>
 * The output value is: <br>
 * <ul>
 * <li> for vulnerability: Expected loss, normalized by the asset value (0 to 1)
 * <li> for fragility: Risk (rate or probability) of damage state exceedance (e.g., collapse or 
 * </ul>
 * Timespan depends on the input hazard, typically annual. <br>
 * Accepts (and outputs) either rate or probability of exceedance. NOTE: risk convolution is formally defined in rate-space. <br>
 * Uses Riemann or Closed-Form integration.
 * <blockquote>
 * 		Close-Form solution: <br>
 * 		Porter, K.A.,C.R. Scawthorn, and J.L. Beck, 2006. Cost-effectiveness of stronger
 *			woodframe buildings. <br>
 *          Earthquake Spectra 22 (1), February 2006, 239-266, Equation #2 <br>
 *		    http://www.sparisk.com/pubs/Porter-2006-woodframe.pdf
 * </blockquote>
 */
public class RiskIntegral {

    public enum IntegrationMethod { RIEMANN, CLOSED_FORM }

    private final SimpleImResponseInterface response;  // prepared response (includes imEdges, respEdges, respMid, etc.)
    private double[] hazardValues;           // either rate or probability of exceedance, at response's imEdges
                                            // can also be null, if hazard will be supplied as .compute(hazard)
    private final IntegrationMethod method;

 // ===================== Constructors =====================

    /**
     * Constructor using a pre-built SimpleImResponse and hazard array.
     * Integration method defaults to RIEMANN if not specified.
     *
     * @param response    SimpleImResponse (including im bin edges, response at im edges, mid-bin response
     * @param hazardValue Hazard value (rate or probability of exceedance at vuln's imEdges)
     * @param method      Integration method (default: RIEMANN)
     */
    public RiskIntegral(SimpleImResponseInterface response, double[] hazard, IntegrationMethod method) {
        if (hazard != null && response.getImValues().length != hazard.length) {
            throw new IllegalArgumentException("response IM and hazard array lengths must match");
        }
        this.response = response;
        this.hazardValues = (hazard != null) ? hazard.clone() : null;
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Convenience constructor using a pre-built SimpleImResponse and hazard array. <br>
     * Uses default RIEMANN integration method.
     *
     * @param response   SimpleImResponse
     * @param hazard     Hazard array
     */
    public RiskIntegral(SimpleImResponseInterface response, double[] hazard) {
        this(response, hazard, null);
    }

    /**
     * Constructor using a pre-built SimpleImResponse and hazard DiscretizedFunc.
     * Checks that the hazard x-values match the vulnerability's IM edges.
     * Default integration method is RIEMANN if not specified.
     *
     * @param response   SimpleImResponse
     * @param hazardFunc Hazard as DiscretizedFunc (x = IM, y = hazard value)
     * @param method     Integration method; if null, defaults to RIEMANN
     */
    public RiskIntegral(SimpleImResponseInterface response, DiscretizedFunc hazardFunc, IntegrationMethod method) {
        this.response = response;
        this.hazardValues = HazardFunctionUtil.convertHazFuncToArray(hazardFunc, response.getImValues());
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Convenience constructor using a pre-built SimpleImResponse  and hazard DiscretizedFunc.
     * Defaults to RIEMANN integration method.
     *
     * @param response   SimpleImResponse 
     * @param hazardFunc Hazard as DiscretizedFunc
     */
    public RiskIntegral(SimpleImResponseInterface response, DiscretizedFunc hazardFunc) {
        this(response, hazardFunc, null);
    }

    /**
     * Constructor that builds an internal SimpleImResponse  from raw IM and response arrays,
     * along with a hazard array. Integration method defaults to RIEMANN if not specified.
     *
     * @param im       Intensity measure array
     * @param fim      Response-as-a-function-of-im array
     * @param hazard   Hazard values corresponding to IMs
     * @param method   Integration method; if null, defaults to RIEMANN
     */
    public RiskIntegral(double[] im, double[] fim, double[] hazard, IntegrationMethod method) {
        if (im.length != fim.length || im.length != hazard.length) {
            throw new IllegalArgumentException("IM, F(IM), and hazard arrays must all be the same length");
        }
        this.response = new SimpleImResponseFunction(im, fim);
        this.hazardValues = hazard.clone();
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }

    /**
     * Convenience constructor that builds a vulnerability from IM/response arrays
     * and uses default RIEMANN integration method.
     *
     * @param im     Intensity measure array
     * @param fim      Response-as-a-function-of-im array
     * @param hazard Hazard array
     */
    public RiskIntegral(double[] im, double[] fim, double[] hazardValues) {
        this(im, fim, hazardValues, null);
    }

    /**
     * Constructor using only the SimpleImResponse.
     * Hazard must be supplied later via compute().
     * Integration method defaults to RIEMANN if not specified.
     *
     * @param response   SimpleImResponse
     * @param method     Integration method; if null, defaults to RIEMANN
     */
    public RiskIntegral(SimpleImResponseInterface response, IntegrationMethod method) {
        this.response = response;
        this.hazardValues = null;
        this.method = (method != null) ? method : IntegrationMethod.RIEMANN;
    }
    
    /**
     * Constructor using only the SimpleImResponse.
     * Hazard must be supplied later via compute().
     * Integration method defaults to RIEMANN.
     *
     * @param response SimpleImResponse
     */
    public RiskIntegral(SimpleImResponseInterface response) {
        this.response = response;
        this.hazardValues = null;
        this.method = null;
    }
    
    /**
     * Constructor using only IM and F(IM) arrays.
     * Builds SimpleImResponse internally; hazard must be supplied later via compute().
     * Integration method defaults to RIEMANN if not specified.
     *
     * @param im      Intensity measure array
     * @param fim     Response-as-a-function-of-im array
     * @param method  Integration method; if null, defaults to RIEMANN
     */
    public RiskIntegral(double[] im, double[] fim, IntegrationMethod method) {
        this(new SimpleImResponseFunction(im, fim), method);
    }

    /**
     * Constructor using only IM and F(IM) arrays.
     * Builds SimpleImResponse internally; hazard must be supplied later via compute().
     * Integration method defaults to RIEMANN.
     *
     * @param im      Intensity measure array
     * @param fim     Response-as-a-function-of-im array
     */
    public RiskIntegral(double[] im, double[] fim) {
        this.response = new SimpleImResponseFunction(im, fim);
        this.hazardValues = null;
        this.method = null;
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
        if (hazard.length != response.getImValues().length) {
            throw new IllegalArgumentException("Hazard array length does not match vulnerability IM length");
        }
        this.hazardValues = hazard.clone();
        return computeInternal(hazardValues);
    }

    /** Provide hazard as DiscretizedFunc (checks IMs) */
    public double compute(DiscretizedFunc hazardFunc) {
        this.hazardValues = HazardFunctionUtil.convertHazFuncToArray(hazardFunc, response.getImValues());
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
        double risk = 0.0;
        double[] respMid = response.getRespMid();
        
        int n = respMid.length;

        // interior bins
        for (int i = 0; i < respMid.length - 1; i++) {
            double deltaHazard = hazardValues[i] - hazardValues[i + 1];
            risk += respMid[i] * deltaHazard;
        }

        // tail bin
        risk += respMid[n - 1] * hazardValues[n - 1];

        return risk;
    }


    // ---------------------- Porter Closed-Form Integration ----------------------
    private double computeClosedForm(double[] hazardValues) {
        double[] iml = response.getImValues();
        double[] respEdge = response.getRespEdges();

        double risk = 0.0;

        for (int i = 1; i < iml.length; i++) {
            double imlDelta = iml[i] - iml[i - 1];
            double respDelta = respEdge[i] - respEdge[i - 1];

            double g = Math.log(hazardValues[i] / hazardValues[i - 1]) / imlDelta;

            double term1 = respEdge[i - 1] * hazardValues[i - 1] * (1.0 - Math.exp(g * imlDelta));
            double term2 = (respDelta / imlDelta) * hazardValues[i - 1] *
                           (Math.exp(g * imlDelta) * (imlDelta - 1.0 / g) + 1.0 / g);

            double deltaRisk = term1 - term2;
            if (!Double.isNaN(deltaRisk) && !Double.isInfinite(deltaRisk)) {
                risk += deltaRisk;
            }
        }

        return risk;
    }
    

}