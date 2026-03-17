package scratch.anne.risk_system_va.portfolio.eloss;

import java.util.*;
import java.util.stream.Collectors;

import scratch.anne.risk_system_va.portfolio.eloss.ELossAsset.ImKey;

/**
 * Prepared portfolio of ELossAssets used for loss calculations.
 *
 * This class acts as a container for assets and their precomputed
 * grouping structures used during hazard and loss calculations.
 *
 * Groupings are constructed externally by ELossPortfolioPreparer.
 */
public class ELossPortfolio {

    private final List<ELossAsset> assets;
    private final Map<ELossAsset.SiteKey, List<ELossAsset>> assetsBySite;
    private final Map<ELossAsset.SiteKey,
            Map<ImKey, List<ELossAsset>>> assetsBySiteAndImKey;

    public ELossPortfolio(
            List<ELossAsset> assets,
            Map<ELossAsset.SiteKey, List<ELossAsset>> assetsBySite,
            Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> assetsBySiteAndImKey) {

        this.assets = Collections.unmodifiableList(assets);
        this.assetsBySite = Collections.unmodifiableMap(assetsBySite);
        this.assetsBySiteAndImKey = Collections.unmodifiableMap(assetsBySiteAndImKey);
    }

    public List<ELossAsset> getAssets() {
        return assets;
    }

    public Map<ELossAsset.SiteKey, List<ELossAsset>> getAssetsBySite() {
        return assetsBySite;
    }

    public Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> getAssetsBySiteAndImKey() {
        return assetsBySiteAndImKey;
    }

    public List<ELossAsset> getAssetsBySite(ELossAsset.SiteKey siteKey) {
        return assetsBySite.getOrDefault(siteKey, Collections.emptyList());
    }

    public List<ELossAsset> getAssetsBySiteAndImKey(
            ELossAsset.SiteKey siteKey,
            ImKey imKey) {

        Map<ImKey, List<ELossAsset>> imMap = assetsBySiteAndImKey.get(siteKey);

        if (imMap == null)
            return Collections.emptyList();

        return imMap.getOrDefault(imKey, Collections.emptyList());
    }

    public Set<ELossAsset.SiteKey> getSiteKeys() {
        return assetsBySite.keySet();
    }

    // -----------------------
    // Aggregation / Summary
    // -----------------------

    /** Sum estimated loss across all assets in the portfolio */
    public double getTotalEstimatedLoss() {
        return assets.stream()
                .mapToDouble(ELossAsset::getEstimatedLoss)
                .sum();
    }

    /** Sum estimated loss by site */
    public Map<ELossAsset.SiteKey, Double> getEstimatedLossBySite() {
        Map<ELossAsset.SiteKey, Double> map = new LinkedHashMap<>();
        for (ELossAsset.SiteKey key : assetsBySite.keySet()) {
            double sum = assetsBySite.get(key).stream()
                    .mapToDouble(ELossAsset::getEstimatedLoss)
                    .sum();
            map.put(key, sum);
        }
        return map;
    }

    /** Sum estimated loss by vulnerability name across all assets */
    public Map<String, Double> getEstimatedLossByVulnerability() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (ELossAsset asset : assets) {
            map.merge(asset.getVulnerabilityName(), asset.getEstimatedLoss(), Double::sum);
        }
        return map;
    }

    /** Print a simple summary of the portfolio losses, including per-asset details */
    public void printSummary() {
        System.out.println("----- ELossPortfolio Summary -----");
        System.out.printf("Total assets: %d%n", assets.size());
        System.out.printf("Total estimated loss: %.2e%n", getTotalEstimatedLoss());

        System.out.println("Loss by site:");
        getEstimatedLossBySite().forEach((k, v) -> System.out.printf("  %s -> %.2e%n", k, v));

        System.out.println("Loss by vulnerability:");
        getEstimatedLossByVulnerability().forEach((k, v) -> System.out.printf("  %s -> %.2e%n", k, v));

        System.out.println("Loss by individual asset:");
        for (ELossAsset asset : assets) {
            System.out.printf("  AssetID=%s, Site=%s, Vulnerability=%s, Value=%.2e, EstimatedLoss=%.2e%n",
                    asset.getAssetID(),
                    asset.getSiteKey(),
                    asset.getVulnerabilityName(),
                    asset.getValue(),
                    asset.getEstimatedLoss());
        }

        System.out.println("---------------------------------");
    }
}