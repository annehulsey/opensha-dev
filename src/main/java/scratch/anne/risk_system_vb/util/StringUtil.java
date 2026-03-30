package scratch.anne.risk_system_vb.util;

import scratch.anne.risk_system_vb.util.enums.IMT;

public class StringUtil {
	
    /**
     * Return string of the im type
     *
     * @param imtEnum imt type (PGA, PGV, SA)
     * @param period spectral period, if SA
     * @return im type as a string with spectral period (if applicable)
     */
	
	public static String imtToString(IMT imtEnum, Double period) {
	    if (imtEnum == null) {
	        throw new IllegalArgumentException("IMT cannot be null");
	    }
	    if (imtEnum.name().equalsIgnoreCase("SA") && period != null) {
	        return String.format("%s(%.2f)", imtEnum.name(), period);
	    } else {
	        return imtEnum.name(); // period is ignored
	    }
	}

}
