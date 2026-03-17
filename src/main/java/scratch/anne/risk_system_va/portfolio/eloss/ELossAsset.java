package scratch.anne.risk_system_va.portfolio.eloss;

import java.util.Arrays;
import java.util.Objects;

import scratch.anne.risk_system_va.portfolio.Asset;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerability;

/**
 * Represents an asset in an ELossPortfolio.
 * <p>
 * Each asset is associated with a site, an im group (IMT/x-values),
 * and a vulnerability model name. The asset is mutable for storing
 * estimated losses after hazard/loss calculations.
 * </p>
 */
public class ELossAsset {

    /** Immutable site key for grouping by location and site conditions */
    private final SiteKey siteKey;

    /** Immutable key for grouping by IMT/log-x-values */
    private final ImKey imKey;

    /** Name of the specific vulnerability model for aggregation by model */
    private final String vulnerabilityName;

    /** Asset value */
    private final double value;

    /** ID of the asset (copied from original Asset) */
    private final String assetID;

    /** Mutable field to store the computed normalized estimated loss */
    private double nEL;

    /**
     * Constructs a new ELossAsset directly from an Asset and a prepared vulnerability.
     *
     * @param asset the original Asset
     * @param imt the IMT associated with the vulnerability
     * @param logImValues the x-values associated with the vulnerability
     */
    public ELossAsset(Asset asset, ELossVulnerability vuln) {
        this.siteKey = new SiteKey(asset.getLat(), asset.getLon(), asset.getVs30());
        this.imKey = new ImKey(vuln.getImtString(), vuln.getLogImValues());
        this.vulnerabilityName = asset.getVulnModel();
        this.value = asset.getValue();
        this.assetID = asset.getAssetID();
        this.nEL = Double.NaN;
    }

    // --- Getters ---

    public SiteKey getSiteKey() {
        return siteKey;
    }

    public ImKey getImKey() {
        return imKey;
    }

    public String getVulnerabilityName() {
        return vulnerabilityName;
    }

    public double getValue() {
        return value;
    }

    public double getEstimatedLoss() {
        return nEL * value;
    }
    
    public double getnEL() {
        return nEL;
    }

    public void setnEL(double nEL) {
        this.nEL = nEL;
    }

    public String getAssetID() {
        return assetID;
    }

    // --- Inner classes ---

    /** Immutable site key for grouping by location */
    public static class SiteKey {
        private final double latitude;
        private final double longitude;
        private final double vs30;

        public SiteKey(double latitude, double longitude, double vs30) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.vs30 = vs30;
        }

        public double getLat() { return latitude; }
        public double getLon() { return longitude; }
        public double getVs30() { return vs30; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SiteKey)) return false;
            SiteKey siteKey = (SiteKey) o;
            return Double.compare(siteKey.latitude, latitude) == 0 &&
                   Double.compare(siteKey.longitude, longitude) == 0 &&
                   Double.compare(siteKey.vs30, vs30) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(latitude, longitude, vs30);
        }

        @Override
        public String toString() {
            return "SiteKey{" +
                    "lat=" + latitude +
                    ", lon=" + longitude +
                    ", vs30=" + vs30 +
                    '}';
        }
    }

    /** Immutable key for grouping by IMT/x-values */
    public static class ImKey {
        private final String imt;
        private final double[] logImValues;

        public ImKey(String imt, double[] logImValues) {
            this.imt = imt;
            this.logImValues = logImValues.clone();
        }

        public String getIMT() { return imt; }
        public double[] getLogImValues() { return logImValues.clone(); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ImKey)) return false;
            ImKey that = (ImKey) o;
            return imt.equals(that.imt) && Arrays.equals(logImValues, that.logImValues);
        }

        @Override
        public int hashCode() {
            int result = imt.hashCode();
            result = 31 * result + Arrays.hashCode(logImValues);
            return result;
        }

        @Override
        public String toString() {
            return "ImKey{" +
                    "imt='" + imt + '\'' +
                    ", xValues=" + Arrays.toString(logImValues) +
                    '}';
        }
    }
}