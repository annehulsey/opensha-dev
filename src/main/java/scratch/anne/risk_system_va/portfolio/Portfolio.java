package scratch.anne.risk_system_va.portfolio;

import java.util.*;

/**
 * Immutable portfolio of assets plus metadata.
 * <p>
 * Holds a list of {@link Asset} objects and associated portfolio metadata.
 * Supports tracking the names of any extra CSV columns for dynamic grouping.
 * </p>
 */
public class Portfolio {

    /** List of assets in the portfolio (immutable after construction) */
    private final List<Asset> assets;

    /** Source of the portfolio (e.g., CSV file name) */
    private final String portfolioSource;

    /** Units used for asset attributes (e.g., USD, meters) */
    private final String attributeUnits;

    /** Source of the vulnerability model */
    private final String vulnerabilityModelSource;

    /** Coordinate reference system used for asset locations */
    private final String coordinateReference;

    /** Short description of the portfolio */
    private final String description;

    /** Creation metadata (timestamp, user, etc.) */
    private final String creationInfo;

    /** Names of extra columns read from the CSV for dynamic grouping */
    private final List<String> extraFieldNames;

    /**
     * Constructs a new Portfolio with specified extra field names.
     *
     * @param assets                   List of assets in the portfolio
     * @param portfolioSource          Portfolio source (e.g., CSV file)
     * @param attributeUnits           Units of asset attributes
     * @param vulnerabilityModelSource Vulnerability model source
     * @param coordinateReference      Coordinate reference system
     * @param description              Description of the portfolio
     * @param creationInfo             Creation metadata
     * @param extraFieldNames          Names of additional CSV columns for grouping; can be empty or null
     */
    public Portfolio(List<Asset> assets,
                     String portfolioSource,
                     String attributeUnits,
                     String vulnerabilityModelSource,
                     String coordinateReference,
                     String description,
                     String creationInfo,
                     List<String> extraFieldNames) {
        this.assets = new ArrayList<>(assets);
        this.portfolioSource = portfolioSource;
        this.attributeUnits = attributeUnits;
        this.vulnerabilityModelSource = vulnerabilityModelSource;
        this.coordinateReference = coordinateReference;
        this.description = description;
        this.creationInfo = creationInfo;
        this.extraFieldNames = extraFieldNames != null
                ? Collections.unmodifiableList(new ArrayList<>(extraFieldNames))
                : Collections.emptyList();
    }

    /**
     * Constructs a new Portfolio without specifying extra field names.
     * <p>
     * Equivalent to calling the full constructor with extraFieldNames = null.
     * </p>
     *
     * @param assets                   List of assets in the portfolio
     * @param portfolioSource          Portfolio source (e.g., CSV file)
     * @param attributeUnits           Units of asset attributes
     * @param vulnerabilityModelSource Vulnerability model source
     * @param coordinateReference      Coordinate reference system
     * @param description              Description of the portfolio
     * @param creationInfo             Creation metadata
     */
    public Portfolio(List<Asset> assets,
                     String portfolioSource,
                     String attributeUnits,
                     String vulnerabilityModelSource,
                     String coordinateReference,
                     String description,
                     String creationInfo) {
        this(assets, portfolioSource, attributeUnits, vulnerabilityModelSource,
             coordinateReference, description, creationInfo, null);
    }

    /** @return unmodifiable list of assets */
    public List<Asset> getAssets() { return assets; }

    /** @return number of assets in the portfolio */
    public int size() { return assets.size(); }

    /**
     * @return unmodifiable list of extra CSV field names used for dynamic grouping
     */
    public List<String> getExtraFieldNames() { return extraFieldNames; }

    /** Returns unique vulnerability names used in this portfolio */
    public List<String> getVulnerabilityNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Asset asset : assets) {
            names.add(asset.getVulnModel());
        }
        return new ArrayList<>(names);
    }

    @Override
    public String toString() {
        return String.format(
                "Portfolio[nAssets=%d, source=%s, description=%s, extraFields=%s]",
                assets.size(), portfolioSource, description, extraFieldNames
        );
    }

    // ---------------- Nested SiteKey class ----------------

    /**
     * Key for grouping assets by site (lat, lon, vs30).
     */
    public static class SiteKey {
        private final double lat;
        private final double lon;
        private final double vs30;
        private final int hash;

        public SiteKey(double lat, double lon, double vs30) {
            this.lat = lat;
            this.lon = lon;
            this.vs30 = vs30;
            this.hash = Objects.hash(lat, lon, vs30);
        }

        public double getLat() { return lat; }
        public double getLon() { return lon; }
        public double getVs30() { return vs30; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SiteKey)) return false;
            SiteKey other = (SiteKey) o;
            return Double.compare(lat, other.lat) == 0 &&
                   Double.compare(lon, other.lon) == 0 &&
                   Double.compare(vs30, other.vs30) == 0;
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public String toString() {
            return String.format("SiteKey[lat=%.5f, lon=%.5f, vs30=%.1f]", lat, lon, vs30);
        }
    }
}