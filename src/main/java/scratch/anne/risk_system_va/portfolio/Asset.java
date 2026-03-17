package scratch.anne.risk_system_va.portfolio;

/**
 * Immutable asset data container.
 */
public class Asset {
    private final String assetID;
    private final double lat;
    private final double lon;
    private final double vs30;
    private final double value;
    private final String vulnModel; // vulnerability name

    public Asset(String assetID, double lat, double lon, double vs30, double value, String vulnModel) {
        this.assetID = assetID;
        this.lat = lat;
        this.lon = lon;
        this.vs30 = vs30;
        this.value = value;
        this.vulnModel = vulnModel;
    }

    public String getAssetID() { return assetID; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getVs30() { return vs30; }
    public double getValue() { return value; }
    public String getVulnModel() { return vulnModel; }

    @Override
    public String toString() {
        return String.format("Asset[id=%s, lat=%.5f, lon=%.5f, vs30=%.1f, value=%.2f, vuln=%s]",
                             assetID, lat, lon, vs30, value, vulnModel);
    }
}