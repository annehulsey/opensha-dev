package scratch.anne.risk_system_va.portfolio.eloss;

import java.util.*;
import scratch.anne.risk_system_va.portfolio.eloss.ELossAsset.ImKey;

/**
 * Prepared portfolio of ELossAssets used for loss calculations.
 *
 * <p>This class is an immutable container for assets and their precomputed
 * grouping/indexing structures used during hazard and loss calculations.
 *
 * <p>All groupings are constructed in a single pass over the asset list for
 * efficiency, then wrapped to enforce deep immutability.
 *
 * <p>Available indices:
 * <ul>
 *   <li>All assets (flat list)</li>
 *   <li>By SiteKey</li>
 *   <li>By SiteKey + ImKey</li>
 *   <li>By Asset ID (unique)</li>
 * </ul>
 *
 * <p>All returned collections are unmodifiable.
 */
public class ELossPortfolio {

    private final List<ELossAsset> assets;
    private final Map<ELossAsset.SiteKey, List<ELossAsset>> assetsBySite;
    private final Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> assetsBySiteAndImKey;
    private final Map<String, ELossAsset> assetsByID;

    /**
     * Constructs an immutable ELossPortfolio and all index structures.
     *
     * <p>All indices are built in a single pass over the input asset list,
     * then wrapped to prevent mutation.
     *
     * @param inputAssets list of prepared ELossAssets
     */
    public ELossPortfolio(List<ELossAsset> inputAssets) {

        List<ELossAsset> assetList = new ArrayList<>(inputAssets);

        Map<ELossAsset.SiteKey, List<ELossAsset>> bySite = new HashMap<>();
        Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> bySiteIm = new HashMap<>();
        Map<String, ELossAsset> byID = new HashMap<>();

        for (ELossAsset asset : assetList) {

            // ID index
            byID.put(asset.getAssetID(), asset);

            // Site index
            ELossAsset.SiteKey siteKey = asset.getSiteKey();
            bySite.computeIfAbsent(siteKey, k -> new ArrayList<>()).add(asset);

            // Site + IM index
            ImKey imKey = asset.getImKey();
            bySiteIm
                .computeIfAbsent(siteKey, k -> new HashMap<>())
                .computeIfAbsent(imKey, k -> new ArrayList<>())
                .add(asset);
        }

        // Wrap lists/maps for immutability
        Map<ELossAsset.SiteKey, List<ELossAsset>> bySiteFinal = new HashMap<>();
        for (var e : bySite.entrySet()) {
            bySiteFinal.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }

        Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> bySiteImFinal = new HashMap<>();
        for (var e : bySiteIm.entrySet()) {
            Map<ImKey, List<ELossAsset>> inner = new HashMap<>();
            for (var e2 : e.getValue().entrySet()) {
                inner.put(e2.getKey(), Collections.unmodifiableList(e2.getValue()));
            }
            bySiteImFinal.put(e.getKey(), Collections.unmodifiableMap(inner));
        }

        this.assets = Collections.unmodifiableList(assetList);
        this.assetsBySite = Collections.unmodifiableMap(bySiteFinal);
        this.assetsBySiteAndImKey = Collections.unmodifiableMap(bySiteImFinal);
        this.assetsByID = Collections.unmodifiableMap(byID);
    }

    // -----------------------
    // Basic accessors
    // -----------------------

    /** @return all assets (unmodifiable list) */
    public List<ELossAsset> getAssets() {
        return assets;
    }

    /** @return assets grouped by SiteKey */
    public Map<ELossAsset.SiteKey, List<ELossAsset>> getAssetsBySite() {
        return assetsBySite;
    }

    /** @return assets grouped by SiteKey and ImKey */
    public Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> getAssetsBySiteAndImKey() {
        return assetsBySiteAndImKey;
    }

    /** @return assets at a given site */
    public List<ELossAsset> getAssetsBySite(ELossAsset.SiteKey siteKey) {
        return assetsBySite.getOrDefault(siteKey, Collections.emptyList());
    }

    /** @return assets for a given site and IM key */
    public List<ELossAsset> getAssetsBySiteAndImKey(
            ELossAsset.SiteKey siteKey,
            ImKey imKey) {

        Map<ImKey, List<ELossAsset>> imMap = assetsBySiteAndImKey.get(siteKey);
        if (imMap == null)
            return Collections.emptyList();

        return imMap.getOrDefault(imKey, Collections.emptyList());
    }

    /** @return all site keys */
    public Set<ELossAsset.SiteKey> getSiteKeys() {
        return assetsBySite.keySet();
    }

    /** @return asset by unique ID, or null if not found */
    public ELossAsset getAssetByID(String assetID) {
        return assetsByID.get(assetID);
    }

    // -----------------------
    // Aggregation / Summary
    // -----------------------

    /** @return total expected loss across all assets */
    public double getTotalExpectedLoss() {
        return assets.stream()
                .mapToDouble(ELossAsset::getExpectedLoss)
                .sum();
    }

    /** @return expected loss aggregated by site */
    public Map<ELossAsset.SiteKey, Double> getExpectedLossBySite() {
        Map<ELossAsset.SiteKey, Double> map = new LinkedHashMap<>();
        for (var e : assetsBySite.entrySet()) {
            double sum = e.getValue().stream()
                    .mapToDouble(ELossAsset::getExpectedLoss)
                    .sum();
            map.put(e.getKey(), sum);
        }
        return map;
    }

    /** @return expected loss aggregated by vulnerability name */
    public Map<String, Double> getExpectedLossByVulnerability() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (ELossAsset asset : assets) {
            map.merge(asset.getVulnerabilityName(),
                      asset.getExpectedLoss(),
                      Double::sum);
        }
        return map;
    }

    /** Prints a summary of portfolio losses including per-asset detail. */
    public void printSummary() {
        System.out.println("----- ELossPortfolio Summary -----");
        System.out.printf("Total assets: %d%n", assets.size());
        System.out.printf("Total expected loss: %.2e%n", getTotalExpectedLoss());

        System.out.println("Loss by site:");
        getExpectedLossBySite()
                .forEach((k, v) -> System.out.printf("  %s -> %.2e%n", k, v));

        System.out.println("Loss by vulnerability:");
        getExpectedLossByVulnerability()
                .forEach((k, v) -> System.out.printf("  %s -> %.2e%n", k, v));

        System.out.println("Loss by individual asset:");
        for (ELossAsset asset : assets) {
            System.out.printf(
                    "  AssetID=%s, Site=%s, Vulnerability=%s, Value=%.2e, ExpectedLoss=%.2e%n",
                    asset.getAssetID(),
                    asset.getSiteKey(),
                    asset.getVulnerabilityName(),
                    asset.getValue(),
                    asset.getExpectedLoss()
            );
        }

        System.out.println("---------------------------------");
    }
}