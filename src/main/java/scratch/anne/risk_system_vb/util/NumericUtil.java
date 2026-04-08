package scratch.anne.risk_system_vb.util;

public class NumericUtil {
	
    /**
     * Compute log-transformed values of a response array.
     * Useful for building hazard curves or IM keys.
     *
     * @param values input array
     * @return new array of log(values)
     */
    public static double[] computeLogValues(double[] values) {
        double[] logValues = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            logValues[i] = Math.log(values[i]);
        }
        return logValues;
    }
    
    // Standard normal CDF (Φ)
    public static double standardNormalCDF(double x) {
        // Using Abramowitz & Stegun approximation
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(x));
        double d = 0.3989423 * Math.exp(-x * x / 2.0);
        double prob = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return (x >= 0.0) ? 1.0 - prob : prob;
    }    

}
