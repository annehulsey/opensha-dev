package scratch.anne.risk_system_va.portfolio;

import java.util.*;

public class Portfolio {

    private final List<Asset> assets;

    // metadata
    private final String portfolioSource;
    private final String attributeUnits;
    private final String vulnerabilityModelSource;
    private final String coordinateReference;
    private final String description;
    private final String creationInfo;

    public Portfolio(List<Asset> assets,
                     String portfolioSource,
                     String attributeUnits,
                     String vulnerabilityModelSource,
                     String coordinateReference,
                     String description,
                     String creationInfo) {

        this.assets = new ArrayList<>(assets);
        this.portfolioSource = portfolioSource;
        this.attributeUnits = attributeUnits;
        this.vulnerabilityModelSource = vulnerabilityModelSource;
        this.coordinateReference = coordinateReference;
        this.description = description;
        this.creationInfo = creationInfo;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public int size() {
        return assets.size();
    }

    /**
     * Return the set of vulnerability model names referenced by this portfolio.
     */
    public List<String> getVulnerabilityNames() {

        Set<String> names = new LinkedHashSet<>();

        for (Asset asset : assets) {
            names.add(asset.getVulnModel());
        }

        return new ArrayList<>(names);
    }

    @Override
    public String toString() {
        return "Portfolio{" +
                "nAssets=" + assets.size() +
                ", source='" + portfolioSource + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
    
 // ------------------- Nested SiteKey class -------------------
    /**
     * Represents a unique site: combination of location (lat/lon) and Vs30.
     * Can be used as a key for grouping assets that share the same site parameters.
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
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return String.format("SiteKey[lat=%.5f, lon=%.5f, vs30=%.1f]", lat, lon, vs30);
        }
    }
}