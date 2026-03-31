package scratch.anne.risk_system_vb.portfolio;

import java.util.*;
import java.util.stream.Collectors;

import scratch.anne.risk_system_vb.portfolio.assets.AbstractAsset;
import scratch.anne.risk_system_vb.util.Metadata;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

/**
 * Immutable canonical portfolio.
 *
 * Holds assets and intrinsic deterministic groupings derived
 * solely from those assets.
 *
 * Execution-specific structures belong in PortfolioView.
 */
public class Portfolio<T extends AbstractAsset> implements PortfolioGetters<T> {

    // ------------------------------------------------------------------------
    // Core data
    // ------------------------------------------------------------------------

    private final List<T> assets;
    private final Map<String, T> assetMap;

    /** Grouping by site (lat, lon, vs30) */
    private final Map<SiteKey, List<T>> siteMap;

    /** Grouping by additional field name → value → assets */
    private final Map<String, Map<String, List<T>>> additionalFieldGroups;

    private final List<String> additionalFieldNames;

    // ------------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------------

    private final Metadata metadata;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    public Portfolio(List<T> assets,
                     List<String> additionalFieldNames,
                     Metadata metadata) {

        if (assets == null || assets.isEmpty())
            throw new IllegalArgumentException("Assets must be non-null and non-empty");

        validateHomogeneous(assets);

        this.assets = Collections.unmodifiableList(new ArrayList<>(assets));
        
        this.assetMap = assets.stream()
                .collect(Collectors.toUnmodifiableMap(
                    T::getAssetID, // assumes AbstractAsset has getID()
                    a -> a
                ));

        this.metadata = (metadata == null)
                ? new Metadata.Builder().build()   // empty/default metadata
                : metadata;

        this.additionalFieldNames = additionalFieldNames != null
                ? Collections.unmodifiableList(new ArrayList<>(additionalFieldNames))
                : Collections.emptyList();

        // ---- intrinsic derived structures ----
        this.siteMap = Collections.unmodifiableMap(buildSiteMap(this.assets));
        this.additionalFieldGroups =
                Collections.unmodifiableMap(buildAdditionalFieldGroups(this.assets));
    }
    
    
    // ---- constructor without metadata ---- 
    public Portfolio(List<T> assets,
            List<String> additionalFieldNames) {
			this(assets, additionalFieldNames, null);
			}
    
    // ---- constructor without additional field names ----
    public Portfolio(List<T> assets,
            Metadata metadata) {
			this(assets, null, metadata);
			}
    
    // ---- constructor for assets only ---- 
    public Portfolio(List<T> assets) {
	        this(assets, null, null);
	    	}


    // ------------------------------------------------------------------------
    // Internal builders
    // ------------------------------------------------------------------------

    private Map<SiteKey, List<T>> buildSiteMap(List<T> assets) {

        Map<SiteKey, List<T>> map = new HashMap<>();

        for (T asset : assets) {

            SiteKey key = new SiteKey(
                    asset.getLatitude(),
                    asset.getLongitude(),
                    asset.getVs30()
            );

            map.computeIfAbsent(key, k -> new ArrayList<>()).add(asset);
        }

        map.replaceAll((k,v) -> Collections.unmodifiableList(v));
        return map;
    }

    private Map<String, Map<String, List<T>>> buildAdditionalFieldGroups(List<T> assets) {

        Map<String, Map<String, List<T>>> result = new HashMap<>();

        for (T asset : assets) {

            for (Map.Entry<String,String> entry :
                    asset.getAdditionalFields().entrySet()) {

                String field = entry.getKey();
                String value = entry.getValue();

                result
                    .computeIfAbsent(field, f -> new HashMap<>())
                    .computeIfAbsent(value, v -> new ArrayList<>())
                    .add(asset);
            }
        }

        // deep immutability
        result.replaceAll((field, valueMap) -> {
            valueMap.replaceAll((v,list) -> Collections.unmodifiableList(list));
            return Collections.unmodifiableMap(valueMap);
        });

        return result;
    }

    private void validateHomogeneous(List<T> assets) {
        Class<?> clazz = assets.get(0).getClass();
        for (T asset : assets) {
            if (!asset.getClass().equals(clazz)) {
                throw new IllegalArgumentException(
                        "Portfolio must contain homogeneous asset types");
            }
        }
    }


 // -------------------- Core getters --------------------
    @Override
    public List<T> getAssets() { return assets; }

    @Override
    public Set<String> getAssetIDs() { return assetMap.keySet(); }

    @Override
    public T getAssetByID(String assetID) {
        T asset = assetMap.get(assetID);
        if (asset == null) {
            throw new NoSuchElementException("No asset with ID: " + assetID);
        }
        return asset;
    }
    
    @Override
    public Set<String> getResponseModelNames() {
        return assets.stream()
                     .map(AbstractAsset::getModelName)
                     .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public int size() { return assets.size(); }

    @Override
    public Metadata getMetadata() { return metadata; }

    // -------------------- Site grouping --------------------
    @Override
    /** Returns the full site → assets map (immutable) */
    public Map<SiteKey, List<T>> getSiteMap() {
        return siteMap;
    }
    
    @Override
    public Set<SiteKey> getSiteKeys() { return siteMap.keySet(); }

    @Override
    public List<T> getAssetsBySite(SiteKey key) {
        return siteMap.getOrDefault(key, Collections.emptyList());
    }

    // -------------------- Additional fields --------------------
    @Override
    /** Returns all unique values for a given additional field */
    public Set<String> getAdditionalFieldValues(String field) {
        Map<String, List<T>> valueMap = additionalFieldGroups.get(field);
        return valueMap == null ? Collections.emptySet() : valueMap.keySet();
    }
    
    @Override
    public List<String> getAdditionalFieldNames() { return additionalFieldNames; }

    @Override
    public List<T> getAssetsByAdditionalField(String field, String value) {
        Map<String, List<T>> valueMap = additionalFieldGroups.get(field);
        if (valueMap == null) return Collections.emptyList();
        return valueMap.getOrDefault(value, Collections.emptyList());
    }
    
    @Override
    public Map<String, List<T>> getAdditionalFieldMap(String fieldName) {
        Map<String, List<T>> map = additionalFieldGroups.get(fieldName);
        return map != null ? Collections.unmodifiableMap(map) : Map.of();
    }

    @Override
    public String toString() {
        return String.format(
                "Portfolio[nAssets=%d, description=%s, additionalFields=%s]",
                assets.size(),
                metadata.get("description"),
                additionalFieldNames
        );
    }
}