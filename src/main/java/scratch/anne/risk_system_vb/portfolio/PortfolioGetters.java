package scratch.anne.risk_system_vb.portfolio;

import scratch.anne.risk_system_vb.util.Metadata;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scratch.anne.risk_system_vb.portfolio.assets.AbstractAsset;

public interface PortfolioGetters<T extends AbstractAsset> {

    // =========================================================
    // Core Asset Access
    // =========================================================

    /** All assets (read-only view). */
    List<T> getAssets();

    /** All asset IDs. */
    Set<String> getAssetIDs();

    /** Lookup single asset by ID. */
    T getAssetByID(String assetID);

    int size();


    // =========================================================
    // Site Index
    // =========================================================

    /** Map of SiteKey → assets (read-only index view). */
    Map<SiteKey, List<T>> getSiteMap();

    /** All unique site keys. */
    Set<SiteKey> getSiteKeys();

    /** Lookup assets at a site. */
    List<T> getAssetsBySite(SiteKey siteKey);


    // =========================================================
    // Response Models
    // =========================================================

    /** Names of response models used by assets. */
    Set<String> getResponseModelNames();


    // =========================================================
    // Additional Field Indexing
    // =========================================================

    /** Names of all additional portfolio fields. */
    List<String> getAdditionalFieldNames();

    /** Distinct values for a given field. */
    List<String> getAdditionalFieldValues(String field);

    /**
     * High-level lookup:
     * (field, value) → assets
     */
    List<T> getAssetsByAdditionalField(String field, String value);

    /**
     * Bulk index exposure:
     * field → (value → assets)
     *
     * Returned map should be read-only.
     */
    Map<String, List<T>> getAdditionalFieldMap(String field);


    // =========================================================
    // Metadata
    // =========================================================

    Metadata getMetadata();
}
