package scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers;

import scratch.anne.risk_system_vb.domain.asset.AbstractAsset;
import scratch.anne.risk_system_vb.domain.asset.RiskConvolutionAsset;
import scratch.anne.risk_system_vb.domain.asset.fragility.FailureProbabilityAsset;
import scratch.anne.risk_system_vb.domain.asset.fragility.FragilityAsset;
import scratch.anne.risk_system_vb.domain.asset.vulnerability.ExpectedLossAsset;
import scratch.anne.risk_system_vb.domain.asset.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.domain.hazard.HazardResult;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.PortfolioGetters;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponse;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.Metadata;
import scratch.anne.risk_system_vb.util.PortfolioGroupingUtils;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.function.TriConsumer;

/**
 * Projection of a base Portfolio into a calculation-ready working set of RiskConvolutionAssets.
 *
 * <p>This class converts heterogeneous portfolio assets into a unified computational form and
 * builds deterministic lookup structures required for hazard/risk convolution workflows.</p>
 *
 * <p>The portfolio is immutable in structure after construction. All grouping maps are derived
 * from canonical per-asset key assignments.</p>
 */
public final class RiskConvolutionPortfolio implements PortfolioGetters<RiskConvolutionAsset> {

    // ---------------------------------------------------------------------
    // Core assets
    // ---------------------------------------------------------------------

    private final List<RiskConvolutionAsset> assets;
    private final Map<String, RiskConvolutionAsset> assetsById;

    // ---------------------------------------------------------------------
    // Canonical per-asset keys (single source of truth)
    // ---------------------------------------------------------------------

    private final Map<RiskConvolutionAsset, ImKey> imKeyByAsset;
    private final Map<RiskConvolutionAsset, SiteKey> siteKeyByAsset;

    // ---------------------------------------------------------------------
    // Derived indices
    // ---------------------------------------------------------------------

    private final Map<ImKey, List<RiskConvolutionAsset>> assetsByImKey;
    private final Map<SiteKey, List<RiskConvolutionAsset>> assetsBySite;
    private final Map<SiteKey, Map<ImKey, List<RiskConvolutionAsset>>> assetsBySiteAndImKey;
    private final Map<SiteKey, Set<ImKey>> imKeysBySite;

    private final Map<String, Map<String, List<RiskConvolutionAsset>>> assetsByAdditionalField;
    private final List<String> additionalFieldNames;

    private final Metadata metadata;

    private boolean riskConvolutionComputed;
    
	 // ---------------------------------------------------------------------
	 // Hazard results (computed state)
	 // ---------------------------------------------------------------------
	
	 private final Map<SiteKey, Map<ImKey, HazardResult>> hazardResults =
	         new LinkedHashMap<>();

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    public RiskConvolutionPortfolio(
            Portfolio<? extends AbstractAsset> base,
            SimpleImResponseLibrary responseLibrary
    ) {
        Objects.requireNonNull(base);
        Objects.requireNonNull(responseLibrary);

        this.riskConvolutionComputed = false;
        // TODO track hazard duration in risk convolution results

        // Build IM lookup: modelName → IMKey
        Map<String, ImKey> responseToIm = new HashMap<>();
        for (SimpleImResponse r : responseLibrary.getModels()) {
            responseToIm.put(r.getName(), r.getImKey());
        }

        List<RiskConvolutionAsset> projected = new ArrayList<>(base.size());

        Map<RiskConvolutionAsset, ImKey> tmpIm = new LinkedHashMap<>();
        Map<RiskConvolutionAsset, SiteKey> tmpSite = new LinkedHashMap<>();

        for (AbstractAsset a : base.getAssets()) {

            RiskConvolutionAsset riskAsset = convertToRiskAsset(a);

            ImKey imKey = responseToIm.get(a.getModelName());
            if (imKey == null) {
                throw new IllegalArgumentException(
                        "No IMKey for model: " + a.getModelName()
                );
            }

            SiteKey siteKey = new SiteKey(
                    a.getLatitude(),
                    a.getLongitude(),
                    a.getVs30()
            );

            riskAsset.setImKey(imKey);

            projected.add(riskAsset);
            tmpIm.put(riskAsset, imKey);
            tmpSite.put(riskAsset, siteKey);
        }

        this.assets = List.copyOf(projected);
        this.imKeyByAsset = Collections.unmodifiableMap(tmpIm);
        this.siteKeyByAsset = Collections.unmodifiableMap(tmpSite);

        // -----------------------------------------------------------------
        // Index: asset ID
        // -----------------------------------------------------------------
        Map<String, RiskConvolutionAsset> idMap = new LinkedHashMap<>();
        for (RiskConvolutionAsset a : assets) {
            idMap.put(a.getAssetID(), a);
        }
        this.assetsById = Collections.unmodifiableMap(idMap);

        // -----------------------------------------------------------------
        // Index: IM
        // -----------------------------------------------------------------
        Map<ImKey, List<RiskConvolutionAsset>> imMap = new HashMap<>();
        for (Map.Entry<RiskConvolutionAsset, ImKey> e : imKeyByAsset.entrySet()) {
            imMap.computeIfAbsent(e.getValue(), k -> new ArrayList<>())
                 .add(e.getKey());
        }
        this.assetsByImKey = imMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())
                ));

        // -----------------------------------------------------------------
        // Index: Site
        // -----------------------------------------------------------------
        Map<SiteKey, List<RiskConvolutionAsset>> siteMap = new HashMap<>();
        for (Map.Entry<RiskConvolutionAsset, SiteKey> e : siteKeyByAsset.entrySet()) {
            siteMap.computeIfAbsent(e.getValue(), k -> new ArrayList<>())
                   .add(e.getKey());
        }
        this.assetsBySite = siteMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())
                ));

        // -----------------------------------------------------------------
        // Index: Site → IM
        // -----------------------------------------------------------------
        Map<SiteKey, Map<ImKey, List<RiskConvolutionAsset>>> siteIm = new HashMap<>();

        for (RiskConvolutionAsset a : assets) {

            SiteKey site = siteKeyByAsset.get(a);
            ImKey im = imKeyByAsset.get(a);

            siteIm
                    .computeIfAbsent(site, k -> new HashMap<>())
                    .computeIfAbsent(im, k -> new ArrayList<>())
                    .add(a);
        }

        this.assetsBySiteAndImKey = siteIm.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        v -> List.copyOf(v.getValue())
                                ))
                ));

        // -----------------------------------------------------------------
        // Index: Site → IM keys
        // -----------------------------------------------------------------
        Map<SiteKey, Set<ImKey>> tmpKeysBySite = new HashMap<>();

        for (RiskConvolutionAsset a : assets) {
            SiteKey site = siteKeyByAsset.get(a);
            ImKey im = imKeyByAsset.get(a);

            tmpKeysBySite
                    .computeIfAbsent(site, k -> new LinkedHashSet<>())
                    .add(im);
        }

        this.imKeysBySite = tmpKeysBySite.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Set.copyOf(e.getValue())
                ));

        // -----------------------------------------------------------------
        // Additional fields
        // -----------------------------------------------------------------
        this.assetsByAdditionalField =
                PortfolioGroupingUtils.invertNestedFields(
                        assets,
                        RiskConvolutionAsset::getAdditionalFields
                );

        this.additionalFieldNames =
                List.copyOf(assetsByAdditionalField.keySet());

        this.metadata = base.getMetadata();
    }

    // ---------------------------------------------------------------------
    // Factory conversion
    // ---------------------------------------------------------------------

    private static RiskConvolutionAsset convertToRiskAsset(AbstractAsset asset) {

        if (asset instanceof VulnerabilityAsset v)
            return new ExpectedLossAsset(v);

        if (asset instanceof FragilityAsset f)
            return new FailureProbabilityAsset(f);

        throw new IllegalArgumentException(
                "Unsupported asset type: " + asset.getClass()
        );
    }

    // ---------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------

    public boolean isRiskConvolutionComputed() {
        return riskConvolutionComputed;
    }

    public void setRiskConvolutionComputed(boolean computed) {
        this.riskConvolutionComputed = computed;
    }

    // ---------------------------------------------------------------------
    // PortfolioGetters
    // ---------------------------------------------------------------------

    @Override
    public List<RiskConvolutionAsset> getAssets() {
        return assets;
    }

    @Override
    public Set<String> getAssetIDs() {
        return assetsById.keySet();
    }

    @Override
    public RiskConvolutionAsset getAssetByID(String id) {
        return assetsById.get(id);
    }

    @Override
    public int size() {
        return assets.size();
    }

    @Override
    public Map<SiteKey, List<RiskConvolutionAsset>> getSiteMap() {
        return assetsBySite;
    }

    @Override
    public Set<SiteKey> getSiteKeys() {
        return assetsBySite.keySet();
    }

    @Override
    public List<RiskConvolutionAsset> getAssetsBySite(SiteKey siteKey) {
        return assetsBySite.getOrDefault(siteKey, List.of());
    }

    public Set<ImKey> getImKeys() {
        return assetsByImKey.keySet();
    }

    public List<RiskConvolutionAsset> getAssetsByImKey(ImKey key) {
        return assetsByImKey.getOrDefault(key, List.of());
    }

    public List<RiskConvolutionAsset> getAssetsBySiteAndImKey(
            SiteKey site,
            ImKey imKey
    ) {
        return assetsBySiteAndImKey
                .getOrDefault(site, Map.of())
                .getOrDefault(imKey, List.of());
    }

    public Set<ImKey> getImKeysBySite(SiteKey siteKey) {
        return imKeysBySite.getOrDefault(siteKey, Set.of());
    }

    @Override
    public List<String> getAdditionalFieldNames() {
        return additionalFieldNames;
    }

    @Override
    public List<String> getAdditionalFieldValues(String field) {
        return List.copyOf(
                assetsByAdditionalField
                        .getOrDefault(field, Map.of())
                        .keySet()
        );
    }

    @Override
    public List<RiskConvolutionAsset> getAssetsByAdditionalField(
            String field,
            String value
    ) {
        return assetsByAdditionalField
                .getOrDefault(field, Map.of())
                .getOrDefault(value, List.of());
    }

    @Override
    public Map<String, List<RiskConvolutionAsset>> getAdditionalFieldMap(
            String field
    ) {
        return assetsByAdditionalField.getOrDefault(field, Map.of());
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public Set<String> getResponseModelNames() {
        return assets.stream()
                .map(RiskConvolutionAsset::getModelName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    
    // ----- hazard storage helpers ---------
    public void storeHazard(
            SiteKey siteKey,
            ImKey imKey,
            HazardResult result
    ) {
        hazardResults
            .computeIfAbsent(siteKey, s -> new LinkedHashMap<>())
            .put(imKey, result);
    }
    
    
    public Map<SiteKey, Map<ImKey, HazardResult>> getHazardResults() {
        return Collections.unmodifiableMap(hazardResults);
    }
    
    public void forEachHazard(
            TriConsumer<SiteKey, ImKey, HazardResult> consumer) {

        hazardResults.forEach((site, imMap) ->
            imMap.forEach((im, result) ->
                consumer.accept(site, im, result)
            )
        );
    }
}