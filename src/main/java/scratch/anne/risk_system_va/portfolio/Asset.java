package scratch.anne.risk_system_va.portfolio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable container for asset data read from a portfolio CSV.
 * <p>
 * Holds standard asset properties (ID, location, value, vulnerability)
 * as well as any number of additional fields for dynamic grouping.
 * </p>
 * <p>
 * Example extra fields: CensusTract, OccupancyGroup, StructuralType, CodeLevel, etc.
 * These are stored as an unmodifiable map to preserve immutability.
 * </p>
 */
public class Asset {

    /** Unique asset identifier */
    private final String assetID;

    /** Latitude of the asset */
    private final double lat;

    /** Longitude of the asset */
    private final double lon;

    /** Vs30 value at the asset location (m/s) */
    private final double vs30;

    /** Monetary value of the asset */
    private final double value;

    /** Name of the associated vulnerability model */
    private final String vulnModel;

    /** Map of additional columns from the CSV for dynamic grouping */
    private final Map<String,String> extraGroupFields;

    /**
     * Constructs a new Asset instance with extra group fields.
     *
     * @param assetID         Unique identifier for the asset
     * @param lat             Latitude
     * @param lon             Longitude
     * @param vs30            Vs30 value
     * @param value           Asset monetary value
     * @param vulnModel       Vulnerability model name
     * @param extraGroupFields Map of additional columns; can be null
     */
    public Asset(String assetID,
                 double lat,
                 double lon,
                 double vs30,
                 double value,
                 String vulnModel,
                 Map<String,String> extraGroupFields) {
        this.assetID = assetID;
        this.lat = lat;
        this.lon = lon;
        this.vs30 = vs30;
        this.value = value;
        this.vulnModel = vulnModel;

        // store an unmodifiable copy to preserve immutability
        this.extraGroupFields = extraGroupFields != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(extraGroupFields))
                : Collections.emptyMap();
    }

    /**
     * Constructs a new Asset instance without extra group fields.
     * <p>
     * Equivalent to calling the full constructor with extraGroupFields = null.
     * </p>
     *
     * @param assetID   Unique identifier for the asset
     * @param lat       Latitude
     * @param lon       Longitude
     * @param vs30      Vs30 value
     * @param value     Asset monetary value
     * @param vulnModel Vulnerability model name
     */
    public Asset(String assetID,
                 double lat,
                 double lon,
                 double vs30,
                 double value,
                 String vulnModel) {
        this(assetID, lat, lon, vs30, value, vulnModel, null);
    }

    /** @return the unique asset ID */
    public String getAssetID() { return assetID; }

    /** @return latitude */
    public double getLat() { return lat; }

    /** @return longitude */
    public double getLon() { return lon; }

    /** @return Vs30 value */
    public double getVs30() { return vs30; }

    /** @return monetary value of the asset */
    public double getValue() { return value; }

    /** @return vulnerability model name */
    public String getVulnModel() { return vulnModel; }

    /**
     * @return an unmodifiable map of extra CSV columns for dynamic grouping
     */
    public Map<String,String> getExtraGroupFields() { return extraGroupFields; }

    @Override
    public String toString() {
        return String.format(
            "Asset[id=%s, lat=%.5f, lon=%.5f, vs30=%.1f, value=%.2f, vuln=%s, extraFields=%s]",
            assetID, lat, lon, vs30, value, vulnModel, extraGroupFields
        );
    }
}