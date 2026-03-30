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

}
