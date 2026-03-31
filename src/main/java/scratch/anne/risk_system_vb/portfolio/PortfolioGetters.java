package scratch.anne.risk_system_vb.portfolio;

import scratch.anne.risk_system_vb.util.Metadata;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scratch.anne.risk_system_vb.portfolio.assets.AbstractAsset;

public interface PortfolioGetters<T extends AbstractAsset> {
    List<T> getAssets();                              // all assets
    Set<String> getAssetIDs();                        // all asset IDs
    T getAssetByID(String assetID);                   // single asset by ID
    
    Map<SiteKey, List<T>> getSiteMap();				  // site map
    Set<SiteKey> getSiteKeys();                       // all unique site keys
    List<T> getAssetsBySite(SiteKey siteKey);         // lookup by site
    
    Set<String> getResponseModelNames();             // get the names of all the response models (vuln or fragility)
    
    List<String> getAdditionalFieldNames();           		// additional fields in CSV or portfolio
    Set<String> getAdditionalFieldValues(String field);    // values of the field
    List<T> getAssetsByAdditionalField(String field, String value); // lookup by field value
    Map<String, List<T>> getAdditionalFieldMap(String fieldName);
    
    Metadata getMetadata();                            // portfolio metadata
    int size();                                       // number of assets
	
}
