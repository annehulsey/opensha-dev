package scratch.anne.risk_system_va.portfolio.eloss;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import scratch.anne.risk_system_va.portfolio.Asset;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerability;

/**
 * Represents an asset in an ELossPortfolio.
 * <p>
 * Each asset is associated with a site, an IMT/x-values group,
 * a vulnerability model name, and dynamic extra grouping fields.
 * This class is mutable only for storing the computed expected loss.
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

    /** Immutable map of extra CSV fields for dynamic grouping */
    private final Map<String,String> extraGroupFields;

    /** Mutable normalized expected loss (0–1) */
    private double normalizedExpectedLoss;

    /**
     * Constructs a new ELossAsset directly from an Asset and a prepared vulnerability.
     *
     * @param asset the original Asset
     * @param vuln  prepared ELossVulnerability
     */
    public ELossAsset(Asset asset, ELossVulnerability vuln) {
        this.siteKey = new SiteKey(asset.getLat(), asset.getLon(), asset.getVs30());
        this.imKey = new ImKey(vuln.getImtString(), vuln.getLogImValues());
        this.vulnerabilityName = asset.getVulnModel();
        this.value = asset.getValue();
        this.assetID = asset.getAssetID();
        this.extraGroupFields = Collections.unmodifiableMap(
                new LinkedHashMap<>(asset.getExtraGroupFields())
        );
        this.normalizedExpectedLoss = Double.NaN;
    }
    
    /**
     * Constructs an ELossAsset directly from CSV-derived values.
     *
     * <p>This constructor is used when reloading an ELossPortfolio from a CSV file
     * that already contains computed expected loss values. Unlike the standard
     * constructor, it does not require an ELossVulnerability object.
     *
     * <p>The provided expected loss is converted back into normalized expected loss
     * (nEL) using:
     * <pre>
     *     nEL = expectedLoss / value
     * </pre>
     *
     * <p>An ImKey is still required for internal consistency but is not meaningful
     * in this context, so a placeholder IMT ("UNKNOWN") and empty IM array are used.
     *
     * @param assetID unique asset identifier
     * @param lat latitude
     * @param lon longitude
     * @param vs30 site Vs30 value
     * @param value asset value
     * @param vulnerabilityName vulnerability model name
     * @param extraGroupFields additional grouping fields (may be empty but not null)
     * @param expectedLoss precomputed expected loss for this asset
     */
    public ELossAsset(String assetID,
                      double lat,
                      double lon,
                      double vs30,
                      double value,
                      String vulnerabilityName,
                      Map<String,String> extraGroupFields,
                      double expectedLoss) {

        this.siteKey = new SiteKey(lat, lon, vs30);

        // Placeholder IM key (not used in CSV rehydration context)
        this.imKey = new ImKey("UNKNOWN", new double[0]);

        this.vulnerabilityName = vulnerabilityName;
        this.value = value;
        this.assetID = assetID;

        this.extraGroupFields = Collections.unmodifiableMap(
                new LinkedHashMap<>(extraGroupFields)
        );

        this.normalizedExpectedLoss = (value == 0.0) ? 0.0 : expectedLoss / value;
    }

    // --- Getters and setters ---

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

    /**
     * Returns the computed expected loss (value * normalized expected loss).
     */
    public double getExpectedLoss() {
        return normalizedExpectedLoss * value;
    }

    public double getNormalizedExpectedLoss() {
        return normalizedExpectedLoss;
    }

    public void setNormalizedExpectedLoss(double normalizedExpectedLoss) {
        this.normalizedExpectedLoss = normalizedExpectedLoss;
    }

    public String getAssetID() {
        return assetID;
    }

    /**
     * @return an unmodifiable map of extra CSV fields for dynamic grouping
     */
    public Map<String,String> getExtraGroupFields() {
        return extraGroupFields;
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
            return String.format("SiteKey[lat=%.5f, lon=%.5f, vs30=%.1f]", latitude, longitude, vs30);
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
            return String.format("ImKey[imt=%s, xValues=%s]", imt, Arrays.toString(logImValues));
        }
    }
}