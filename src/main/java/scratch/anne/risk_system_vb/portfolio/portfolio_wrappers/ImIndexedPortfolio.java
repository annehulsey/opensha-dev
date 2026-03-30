package scratch.anne.risk_system_vb.portfolio.portfolio_wrappers;

import scratch.anne.risk_system_vb.portfolio.Portfolio;
import scratch.anne.risk_system_vb.portfolio.PortfolioGetters;
import scratch.anne.risk_system_vb.portfolio.assets.AbstractAsset;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnLibrary;
import scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected.ExpectedVulnerability;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.Metadata;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only view on a Portfolio that adds IMKey mapping for hazard calculations.
 *
 * <p>Wraps a base PortfolioLike and computes an IMKey per asset using a vulnerability or fragility library.
 * All other portfolio access (site groupings, additional fields) is delegated to the wrapped base.</p>
 */
public class ImIndexedPortfolio<T extends AbstractAsset> implements PortfolioGetters<T> {

    private final PortfolioGetters<T> base;
    private final Map<T, ImKey> imKeyMap;
    private final Map<ImKey, List<T>> assetsByImKey;

 // ---------------- Private Constructor --------------------
    private ImIndexedPortfolio(Portfolio<T> base, ExpectedVulnLibrary vulnLibrary) {
        this.base = base;

        // Build a mapping from vulnerability name → IMKey
        Map<String, ImKey> vulnNameToImKey = new HashMap<>();
        for (ExpectedVulnerability v : vulnLibrary.getModels()) {
            ImKey key = v.getImKey();
            String vulnName = v.getName();
            if (vulnName == null || vulnName.isEmpty()) {
                System.err.println("WARNING: Vulnerability has null/empty name, skipping: " + v);
                continue;
            }
            if (vulnNameToImKey.containsKey(vulnName)) {
                System.err.println("WARNING: Duplicate vulnerability name in library: " + vulnName);
            }
            vulnNameToImKey.put(vulnName, key);
        }

        Map<T, ImKey> tempMap = new HashMap<>();
        // Assign IMKeys to each asset based on matching vulnerability name
        for (T asset : base.getAssets()) {
            String assetModelName = asset.getModelName();
            ImKey imKey = vulnNameToImKey.get(assetModelName);
            if (imKey == null) {
                throw new IllegalArgumentException(
                        "No IMKey found for asset ID: " + asset.getAssetID() + " / vulnName: " + assetModelName);
            }
            tempMap.put(asset, imKey);
        }

        // Build the reverse map: IMKey → list of assets
        Map<ImKey, List<T>> imKeyGrouping = new HashMap<>();
        for (Map.Entry<T, ImKey> entry : tempMap.entrySet()) {
            imKeyGrouping.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // Make inner lists and outer map unmodifiable
        this.assetsByImKey = Collections.unmodifiableMap(
                imKeyGrouping.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> Collections.unmodifiableList(e.getValue())
                        ))
        );

        // Make imKeyMap unmodifiable
        this.imKeyMap = Collections.unmodifiableMap(tempMap);
    }

    // ---------------- STATIC FACTORY ----------------
    public static <T extends AbstractAsset>
    ImIndexedPortfolio<T> of(
            Portfolio<T> portfolio,
            ExpectedVulnLibrary vulnLibrary) {

        return new ImIndexedPortfolio<>(portfolio, vulnLibrary);
    }

    // ------------------------------------------------------------------------
    // Additional functionality
    // ------------------------------------------------------------------------

    /** Get the IMKey for a specific asset */
    public ImKey getIMKey(T asset) {
        return imKeyMap.get(asset);
    }

    /** All asset → IMKey mappings */
    public Map<T, ImKey> getIMKeyMap() {
        return imKeyMap;
    }

    /** Set of all unique IMKeys */
    public Set<ImKey> getImKeys() {
        return assetsByImKey.keySet();
    }

    /** Get all assets that share a given IMKey */
    public List<T> getAssetsByImKey(ImKey key) {
        return assetsByImKey.getOrDefault(key, Collections.emptyList());
    }
    
    // ------------------------------------------------------------------------
    // PortfolioGetters delegation
    // ------------------------------------------------------------------------
    @Override
    public List<T> getAssets() { return base.getAssets(); }

    @Override
    public Set<String> getAssetIDs() { return base.getAssetIDs(); }

    @Override
    public T getAssetByID(String assetID) { return base.getAssetByID(assetID) ;}
    
    @Override
    public Set<String> getResponseModelNames() {
        return base.getResponseModelNames();
    }

    @Override
    public int size() { return base.size(); }

    @Override
    public Metadata getMetadata() { return base.getMetadata(); }

    // -------------------- Site grouping --------------------
    @Override
    public Map<SiteKey, List<T>> getSiteMap() { return base.getSiteMap(); }
    
    @Override
    public Set<SiteKey> getSiteKeys() { return base.getSiteKeys(); }

    @Override
    public List<T> getAssetsBySite(SiteKey key) { return base.getAssetsBySite(key); }

    // -------------------- Additional fields --------------------
    @Override
    public List<String> getAdditionalFieldNames() { return base.getAdditionalFieldNames(); }
    
    @Override
    public Set<String> getAdditionalFieldValues(String field) { return base.getAdditionalFieldValues(field); }

    @Override
    public List<T> getAssetsByAdditionalField(String field, String value) { 
    	return base.getAssetsByAdditionalField(field, value) ;
    	}

    @Override
    public String toString() {
        return String.format("PortfolioView[nAssets=%d, imKeys=%d]",
                base.getAssets().size(), imKeyMap.size());
    }

}
