package scratch.anne.risk_system_vb.portfolio.portfolio_wrappers;

import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.ExpectedLossAsset;
import scratch.anne.risk_system_vb.portfolio.assets.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Immutable Expected Loss portfolio wrapper.
 *
 * <p>This class projects an {@link ImIndexedPortfolio} of
 * {@link VulnerabilityAsset}s into calculation-ready
 * {@link ExpectedLossAsset}s.
 *
 * <h2>Design Philosophy</h2>
 *
 * <ul>
 *   <li>The underlying portfolio remains immutable.</li>
 *   <li>All metadata, indexing, and grouping are inherited —
 *       NEVER recomputed.</li>
 *   <li>Each asset receives a mutable calculation state
 *       (expected loss result).</li>
 *   <li>The portfolio structure itself is immutable and thread-safe.</li>
 * </ul>
 *
 * <p>The wrapper performs a <b>one-time projection</b>:
 *
 * <pre>
 * VulnerabilityAsset  →  ExpectedLossAsset
 * </pre>
 *
 * preserving all existing portfolio indices:
 *
 * <ul>
 *   <li>Asset ID</li>
 *   <li>SiteKey</li>
 *   <li>ImKey</li>
 *   <li>Dynamic additional grouping fields</li>
 * </ul>
 *
 * <h2>Threading Model</h2>
 *
 * <ul>
 *   <li>Portfolio structure is immutable.</li>
 *   <li>{@link ExpectedLossAsset} instances contain mutable
 *       calculation results.</li>
 *   <li>Assets can safely be processed in parallel provided
 *       each thread writes only to its assigned assets.</li>
 * </ul>
 */
public final class ExpectedLossPortfolio {

    /* ======================================================
                        BASE PORTFOLIO
       ====================================================== */

    private final ImIndexedPortfolio<? extends VulnerabilityAsset> base;

    /* ======================================================
                        ASSET PROJECTION
       ====================================================== */

    private final Map<VulnerabilityAsset, ExpectedLossAsset> assetMap;
    private final List<ExpectedLossAsset> assets;

    /* ======================================================
                        PROJECTED INDICES
       ====================================================== */

    private final Map<String, ExpectedLossAsset> assetsByID;
    private final Map<SiteKey, List<ExpectedLossAsset>> assetsBySite;
    private final Map<ImKey, List<ExpectedLossAsset>> assetsByImKey;

    private final Map<String,
            Map<String, List<ExpectedLossAsset>>> assetsByAdditionalField;

    /* ======================================================
                            CONSTRUCTOR
       ====================================================== */

    /**
     * Constructs an ExpectedLossPortfolio by projecting an
     * existing {@link ImIndexedPortfolio}.
     *
     * <p>No grouping or metadata is recomputed. All indices are
     * derived directly from the base portfolio.
     *
     * @param basePortfolio indexed immutable portfolio
     */
    public ExpectedLossPortfolio(
            ImIndexedPortfolio<? extends VulnerabilityAsset> basePortfolio) {

        this.base = Objects.requireNonNull(basePortfolio);

        /* ---------- wrap assets ---------- */

        Map<VulnerabilityAsset, ExpectedLossAsset> map = new HashMap<>();
        List<ExpectedLossAsset> list = new ArrayList<>();

        for (VulnerabilityAsset asset : base.getAssets()) {
            ExpectedLossAsset e = new ExpectedLossAsset(asset);
            map.put(asset, e);
            list.add(e);
        }

        this.assetMap = Collections.unmodifiableMap(map);
        this.assets = Collections.unmodifiableList(list);

        /* ---------- ID index ---------- */

        Map<String, ExpectedLossAsset> idMap = new HashMap<>();
        for (ExpectedLossAsset a : assets) {
            idMap.put(a.getAssetID(), a);
        }
        this.assetsByID = Collections.unmodifiableMap(idMap);

        /* ---------- SITE projection ---------- */

        Map<SiteKey, List<ExpectedLossAsset>> siteMap = new HashMap<>();

        for (SiteKey site : base.getSiteKeys()) {
            List<ExpectedLossAsset> wrapped =
                    base.getAssetsBySite(site)
                            .stream()
                            .map(assetMap::get)
                            .toList();

            siteMap.put(site, Collections.unmodifiableList(wrapped));
        }

        this.assetsBySite = Collections.unmodifiableMap(siteMap);

        /* ---------- IMKEY projection ---------- */

        Map<ImKey, List<ExpectedLossAsset>> imMap = new HashMap<>();

        for (ImKey key : base.getImKeys()) {

            List<ExpectedLossAsset> wrapped =
                    base.getAssetsByImKey(key)
                            .stream()
                            .map(assetMap::get)
                            .toList();

            imMap.put(key, Collections.unmodifiableList(wrapped));
        }

        this.assetsByImKey = Collections.unmodifiableMap(imMap);

        /* ---------- ADDITIONAL FIELD projection ---------- */

        Map<String,
                Map<String, List<ExpectedLossAsset>>> extra = new HashMap<>();

        for (String field : base.getAdditionalFieldNames()) {

            Map<String, List<ExpectedLossAsset>> valueMap =
                    new HashMap<>();

            for (String value :
                    base.getAdditionalFieldValues(field)) {

                List<ExpectedLossAsset> wrapped =
                        base.getAssetsByAdditionalField(field, value)
                                .stream()
                                .map(assetMap::get)
                                .toList();

                valueMap.put(value,
                        Collections.unmodifiableList(wrapped));
            }

            extra.put(field,
                    Collections.unmodifiableMap(valueMap));
        }

        this.assetsByAdditionalField =
                Collections.unmodifiableMap(extra);
    }

    /* ======================================================
                          BASIC ACCESS
       ====================================================== */

    /** @return all expected loss assets */
    public List<ExpectedLossAsset> getAssets() {
        return assets;
    }

    /** @return asset by unique ID */
    public ExpectedLossAsset getAssetByID(String id) {
        return assetsByID.get(id);
    }

    /** @return all SiteKeys present in portfolio */
    public Set<SiteKey> getSiteKeys() {
        return assetsBySite.keySet();
    }

    /** @return assets at a given site */
    public List<ExpectedLossAsset> getAssetsBySite(SiteKey key) {
        return assetsBySite.getOrDefault(key, List.of());
    }

    /** @return all IM keys */
    public Set<ImKey> getImKeys() {
        return assetsByImKey.keySet();
    }

    /** @return assets sharing an IM key */
    public List<ExpectedLossAsset> getAssetsByImKey(ImKey key) {
        return assetsByImKey.getOrDefault(key, List.of());
    }

    /** @return names of dynamic grouping fields */
    public Set<String> getAdditionalFieldNames() {
        return assetsByAdditionalField.keySet();
    }

    /** @return grouping map for a dynamic field */
    public Map<String, List<ExpectedLossAsset>>
    getAssetsByAdditionalField(String field) {

        return assetsByAdditionalField
                .getOrDefault(field, Map.of());
    }

    /* ======================================================
                          AGGREGATION
       ====================================================== */

    /** @return total expected loss */
    public double getTotalExpectedLoss() {
        double sum = 0.0;
        for (ExpectedLossAsset a : assets)
            sum += a.getExpectedLoss();
        return sum;
    }

    /** @return expected loss aggregated by site */
    public Map<SiteKey, Double> getExpectedLossBySite() {

        Map<SiteKey, Double> result = new LinkedHashMap<>();

        for (var e : assetsBySite.entrySet()) {

            double sum = 0.0;
            for (ExpectedLossAsset a : e.getValue())
                sum += a.getExpectedLoss();

            result.put(e.getKey(), sum);
        }

        return result;
    }

    /** @return expected loss aggregated by vulnerability model */
    public Map<String, Double> getExpectedLossByVulnerability() {

        Map<String, Double> result = new LinkedHashMap<>();

        for (ExpectedLossAsset a : assets) {
            result.merge(
                    a.getModelName(),
                    a.getExpectedLoss(),
                    Double::sum);
        }

        return result;
    }

    /** Aggregation over any dynamic grouping field. */
    public Map<String, Double>
    getExpectedLossByAdditionalField(String field) {

        Map<String, Double> result = new LinkedHashMap<>();

        var groups = assetsByAdditionalField.get(field);
        if (groups == null) return result;

        for (var e : groups.entrySet()) {

            double sum = 0.0;
            for (ExpectedLossAsset a : e.getValue())
                sum += a.getExpectedLoss();

            result.put(e.getKey(), sum);
        }

        return result;
    }

    /* ======================================================
                           CSV EXPORT
       ====================================================== */

    /**
     * Writes full portfolio data to CSV.
     *
     * <p>Includes all dynamic grouping fields automatically.
     */
    public void writeCSV(Path file) throws IOException {

        try (PrintWriter pw =
                     new PrintWriter(Files.newBufferedWriter(file))) {

            pw.print(
                    "AssetID,Latitude,Longitude,Vs30,Value,Vulnerability");

            for (String field : getAdditionalFieldNames())
                pw.print("," + field);

            pw.println(",ExpectedLoss");

            for (ExpectedLossAsset a : assets) {

                pw.printf("\"%s\",%.6f,%.6f,%.0f,%.2f,\"%s\"",
                        a.getAssetID(),
                        a.getLatitude(),
                        a.getLongitude(),
                        a.getVs30(),
                        a.getValue(),
                        a.getModelName());

                Map<String,String> extras =
                        a.getAdditionalFields();

                for (String field : getAdditionalFieldNames())
                    pw.printf(",\"%s\"",
                            extras.getOrDefault(field, ""));

                pw.printf(",%.6e%n", a.getExpectedLoss());
            }
        }
    }

    /* ======================================================
                    AGGREGATED CSV EXPORT
       ====================================================== */

    /**
     * Writes statistical summaries for every additional
     * grouping field.
     */
    public void writeAggregatedCSV(Path outputPath)
            throws IOException {

        try (PrintWriter pw =
                     new PrintWriter(Files.newBufferedWriter(outputPath))) {

            pw.println(
                "GroupField,GroupValue,NumAssets,TotalValue," +
                "TotalExpectedLoss,AvgExpectedLoss," +
                "MinExpectedLoss,MaxExpectedLoss,StdDevExpectedLoss");

            for (String field : getAdditionalFieldNames()) {

                var grouped = assetsByAdditionalField.get(field);

                for (var entry : grouped.entrySet()) {

                    List<ExpectedLossAsset> list = entry.getValue();

                    int n = list.size();

                    double totalValue =
                            list.stream()
                                .mapToDouble(ExpectedLossAsset::getValue)
                                .sum();

                    double totalEL =
                            list.stream()
                                .mapToDouble(ExpectedLossAsset::getExpectedLoss)
                                .sum();

                    double avg = totalEL / n;

                    double min =
                            list.stream()
                                .mapToDouble(ExpectedLossAsset::getExpectedLoss)
                                .min().orElse(Double.NaN);

                    double max =
                            list.stream()
                                .mapToDouble(ExpectedLossAsset::getExpectedLoss)
                                .max().orElse(Double.NaN);

                    double variance =
                            list.stream()
                                .mapToDouble(a ->
                                    Math.pow(a.getExpectedLoss() - avg, 2))
                                .sum() / n;

                    double stdDev = Math.sqrt(variance);

                    pw.printf("\"%s\",\"%s\",%d,%.2f,%.6e,%.6f,%.6f,%.6f,%.6f%n",
                            field,
                            entry.getKey(),
                            n,
                            totalValue,
                            totalEL,
                            avg,
                            min,
                            max,
                            stdDev);
                }
            }
        }
    }
}