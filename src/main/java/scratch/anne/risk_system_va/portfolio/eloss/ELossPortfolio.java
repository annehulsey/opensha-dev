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
 *   <li>By dynamic extra group fields (any string column)</li>
 * </ul>
 *
 * <p>All returned collections are unmodifiable.
 */
public class ELossPortfolio {

    private final List<ELossAsset> assets;
    private final Map<ELossAsset.SiteKey, List<ELossAsset>> assetsBySite;
    private final Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> assetsBySiteAndImKey;
    private final Map<String, ELossAsset> assetsByID;
    private final Map<String, Map<String, List<ELossAsset>>> assetsByExtraGroup;
    private final Set<String> extraFieldNames;

    // -----------------------
    // Constructors
    // -----------------------

    /**
     * Constructs an ELossPortfolio from a list of ELossAssets.
     * Extra fields are automatically inferred from the assets.
     *
     * @param inputAssets list of prepared ELossAssets
     */
    public ELossPortfolio(List<ELossAsset> inputAssets) {
        this(inputAssets, null);
    }

    /**
     * Internal constructor that optionally accepts a pre-defined set of extra field names.
     * If extraFieldNames is null, they are automatically inferred from the assets.
     *
     * @param inputAssets list of prepared ELossAssets
     * @param extraFieldNames optional set of extra field names (can be null)
     */
    private ELossPortfolio(List<ELossAsset> inputAssets, Set<String> extraFieldNames) {

        List<ELossAsset> assetList = new ArrayList<>(inputAssets);
        Map<ELossAsset.SiteKey, List<ELossAsset>> bySite = new HashMap<>();
        Map<ELossAsset.SiteKey, Map<ImKey, List<ELossAsset>>> bySiteIm = new HashMap<>();
        Map<String, ELossAsset> byID = new HashMap<>();
        Map<String, Map<String, List<ELossAsset>>> byExtraGroup = new HashMap<>();
        Set<String> extraFields = extraFieldNames != null
                ? new LinkedHashSet<>(extraFieldNames)
                : new LinkedHashSet<>();

        for (ELossAsset asset : assetList) {

            // ID index
            byID.put(asset.getAssetID(), asset);

            // Site index
            ELossAsset.SiteKey siteKey = asset.getSiteKey();
            bySite.computeIfAbsent(siteKey, k -> new ArrayList<>()).add(asset);

            // Site + IM index
            ImKey imKey = asset.getImKey();
            bySiteIm.computeIfAbsent(siteKey, k -> new HashMap<>())
                    .computeIfAbsent(imKey, k -> new ArrayList<>())
                    .add(asset);

            // Extra group fields
            for (var e : asset.getExtraGroupFields().entrySet()) {
                String field = e.getKey();
                String value = e.getValue();
                extraFields.add(field);
                byExtraGroup
                        .computeIfAbsent(field, k -> new HashMap<>())
                        .computeIfAbsent(value, k -> new ArrayList<>())
                        .add(asset);
            }
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

        Map<String, Map<String, List<ELossAsset>>> byExtraGroupFinal = new HashMap<>();
        for (var e : byExtraGroup.entrySet()) {
            Map<String, List<ELossAsset>> inner = new HashMap<>();
            for (var e2 : e.getValue().entrySet()) {
                inner.put(e2.getKey(), Collections.unmodifiableList(e2.getValue()));
            }
            byExtraGroupFinal.put(e.getKey(), Collections.unmodifiableMap(inner));
        }

        this.assets = Collections.unmodifiableList(assetList);
        this.assetsBySite = Collections.unmodifiableMap(bySiteFinal);
        this.assetsBySiteAndImKey = Collections.unmodifiableMap(bySiteImFinal);
        this.assetsByID = Collections.unmodifiableMap(byID);
        this.assetsByExtraGroup = Collections.unmodifiableMap(byExtraGroupFinal);
        this.extraFieldNames = Collections.unmodifiableSet(extraFields);
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

    /** @return asset by unique ID, or null if not found */
    public ELossAsset getAssetByID(String assetID) {
        return assetsByID.get(assetID);
    }

    /** @return names of extra group fields in this portfolio */
    public Set<String> getExtraFieldNames() {
        return extraFieldNames;
    }

    /** @return assets grouped by a dynamic extra field and value */
    public Map<String, List<ELossAsset>> getAssetsByExtraGroupField(String fieldName) {
        return assetsByExtraGroup.getOrDefault(fieldName, Collections.emptyMap());
    }

    /** @return all site keys */
    public Set<ELossAsset.SiteKey> getSiteKeys() {
        return assetsBySite.keySet();
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
        if (imMap == null) return Collections.emptyList();
        return imMap.getOrDefault(imKey, Collections.emptyList());
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

//        System.out.println("Loss by site:");
//        getExpectedLossBySite()
//                .forEach((k, v) -> System.out.printf("  %s -> %.2e%n", k, v));
//
//        System.out.println("Loss by vulnerability:");
//        getExpectedLossByVulnerability()
//                .forEach((k, v) -> System.out.printf("  %s -> %.2e%n", k, v));
//
//        System.out.println("Loss by individual asset:");
//        for (ELossAsset asset : assets) {
//            System.out.printf(
//                    "  AssetID=%s, Site=%s, Vulnerability=%s, Value=%.2e, ExpectedLoss=%.2e%n",
//                    asset.getAssetID(),
//                    asset.getSiteKey(),
//                    asset.getVulnerabilityName(),
//                    asset.getValue(),
//                    asset.getExpectedLoss()
//            );
//        }

        System.out.println("---------------------------------");
    }
    
    /**
     * Writes the ELossPortfolio to a CSV file.
     *
     * <p>The CSV will contain: AssetID, Latitude, Longitude, Value, Vulnerability,
     * optional extra fields (if present), and ExpectedLoss as the last column.
     *
     * @param outputCSV Path to the output CSV file
     * @throws java.io.IOException if writing fails
     */
    public void writeCSV(java.nio.file.Path outputCSV) throws java.io.IOException {

        try (java.io.PrintWriter pw = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(outputCSV))) {

            // --- Header ---
            pw.print("AssetID,Latitude,Longitude,Vs30,Value,Vulnerability");

            // Optional extra fields
            if (!extraFieldNames.isEmpty()) {
                for (String extraField : extraFieldNames) {
                    pw.print("," + extraField);
                }
            }

            pw.println(",ExpectedLoss"); // always last

            // --- Rows ---
            for (ELossAsset asset : assets) {

                pw.printf("\"%s\",%.6f,%.6f,%.0f,%.2f,\"%s\"",
                        asset.getAssetID(),
                        asset.getSiteKey().getLat(),
                        asset.getSiteKey().getLon(),
                        asset.getSiteKey().getVs30(),
                        asset.getValue(),
                        asset.getVulnerabilityName()
                );

                // Extra fields
                for (String field : getExtraFieldNames()) {
                    Map<String,String> extraMap = asset.getExtraGroupFields();
                    if (extraMap != null) {
                    	String value = extraMap.getOrDefault(field, "");
                        pw.printf(",\"%s\"", value);
                    }
                }

                // Expected loss
                pw.printf(",%.6e%n", asset.getExpectedLoss());
            }
        }
    }
    
    /**
     * Reads an ELossPortfolio from a CSV file.
     *
     * <p>This method reconstructs an ELossPortfolio from a previously exported CSV,
     * including all assets, grouping fields, and expected loss values.
     *
     * <p>Required columns (order does not matter):
     * <ul>
     *   <li>AssetID</li>
     *   <li>Latitude</li>
     *   <li>Longitude</li>
     *   <li>Vs30</li>
     *   <li>Value</li>
     *   <li>Vulnerability</li>
     *   <li>ExpectedLoss</li>
     * </ul>
     *
     * <p>Any additional columns are treated as dynamic grouping fields and stored
     * in each ELossAsset's extraGroupFields map.
     *
     * <p>The resulting ELossPortfolio will rebuild all grouping structures
     * (site, IM, and extra fields) automatically.
     *
     * @param file path to the CSV file
     * @return reconstructed ELossPortfolio
     * @throws java.io.IOException if file reading fails
     * @throws IllegalArgumentException if required columns are missing
     */
    public static ELossPortfolio readCSV(java.nio.file.Path file) throws java.io.IOException {

        List<String> lines = java.nio.file.Files.readAllLines(file);
        if (lines.isEmpty()) throw new java.io.IOException("Empty CSV: " + file);

        // --- Header ---
        String headerLine = lines.get(0);
        if (headerLine.startsWith("\uFEFF")) headerLine = headerLine.substring(1);

        String[] rawHeader = headerLine.split(",", -1);
        String[] header = new String[rawHeader.length];

        Map<String, Integer> colIndex = new LinkedHashMap<>();
        for (int i = 0; i < rawHeader.length; i++) {
            String clean = rawHeader[i].trim();
            // Strip surrounding quotes if present
            if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) {
                clean = clean.substring(1, clean.length() - 1);
            }
            header[i] = clean;
            colIndex.put(clean, i);
        }

        // --- Required columns ---
        List<String> required = Arrays.asList(
                "AssetID", "Latitude", "Longitude", "Vs30",
                "Value", "Vulnerability", "ExpectedLoss"
        );

        for (String col : required) {
            if (!colIndex.containsKey(col)) {
                throw new IllegalArgumentException("Missing required column: " + col);
            }
        }

        // --- Extra fields (preserve CSV order) ---
        List<String> extraFields = new ArrayList<>();
        for (String col : header) {
            if (!required.contains(col)) extraFields.add(col);
        }

        // --- Parse rows ---
        List<ELossAsset> assets = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] t = line.split(",", -1);

            // Strip quotes from string values
            String assetID = t[colIndex.get("AssetID")].replaceAll("^\"|\"$", "");
            double lat = Double.parseDouble(t[colIndex.get("Latitude")].replaceAll("^\"|\"$", ""));
            double lon = Double.parseDouble(t[colIndex.get("Longitude")].replaceAll("^\"|\"$", ""));
            double vs30 = Double.parseDouble(t[colIndex.get("Vs30")].replaceAll("^\"|\"$", ""));
            double value = Double.parseDouble(t[colIndex.get("Value")].replaceAll("^\"|\"$", ""));
            String vuln = t[colIndex.get("Vulnerability")].replaceAll("^\"|\"$", "");
            double expectedLoss = Double.parseDouble(t[colIndex.get("ExpectedLoss")].replaceAll("^\"|\"$", ""));

            Map<String, String> extras = new LinkedHashMap<>();
            for (String f : extraFields) {
                String v = t[colIndex.get(f)].replaceAll("^\"|\"$", "");
                extras.put(f, v);
            }

            ELossAsset asset = new ELossAsset(
                    assetID, lat, lon, vs30, value, vuln, extras, expectedLoss
            );
            assets.add(asset);
        }

        return new ELossPortfolio(assets);
    }
    
    /**
     * Writes aggregated expected loss statistics by extra grouping fields to a CSV file.
     * <p>
     * Each row corresponds to a single group value for a given extra field.
     * The output includes count, total value, total expected loss, average, min, max, and standard deviation.
     * * If no extra fields exist, a message is printed and an empty CSV is created with only a header.
     * <p>
     * Format:
     * <pre>
     * GroupField,GroupValue,NumAssets,TotalValue,TotalExpectedLoss,AvgExpectedLoss,MinExpectedLoss,MaxExpectedLoss,StdDevExpectedLoss
     * </pre>
     *
     * @param outputPath the path to the CSV file to write
     * @throws java.io.IOException if writing the file fails
     */
    public void writeAggregatedCSV(java.nio.file.Path outputPath) throws java.io.IOException {
        if (getExtraFieldNames().isEmpty()) {
            System.out.println("No extra groupings available in this portfolio. Aggregation CSV will contain only the header.");
        }

        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                java.nio.file.Files.newBufferedWriter(outputPath))) {

            // Header
            pw.println("GroupField,GroupValue,NumAssets,TotalValue,TotalExpectedLoss,"
                    + "AvgExpectedLoss,MinExpectedLoss,MaxExpectedLoss,StdDevExpectedLoss");

            for (String field : getExtraFieldNames()) {
                Map<String, List<ELossAsset>> grouped = getAssetsByExtraGroupField(field);

                for (var entry : grouped.entrySet()) {
                    String groupValue = entry.getKey();
                    List<ELossAsset> assets = entry.getValue();

                    int n = assets.size();
                    double totalValue = assets.stream().mapToDouble(ELossAsset::getValue).sum();
                    double totalEL = assets.stream().mapToDouble(ELossAsset::getExpectedLoss).sum();
                    double avgEL = totalEL / n;

                    double minEL = assets.stream().mapToDouble(ELossAsset::getExpectedLoss).min().orElse(Double.NaN);
                    double maxEL = assets.stream().mapToDouble(ELossAsset::getExpectedLoss).max().orElse(Double.NaN);

                    double variance = assets.stream()
                            .mapToDouble(a -> Math.pow(a.getExpectedLoss() - avgEL, 2))
                            .sum() / n;
                    double stdDev = Math.sqrt(variance);

                    pw.printf("\"%s\",\"%s\",%d,%.2f,%.6e,%.6f,%.6f,%.6f,%.6f%n",
                            field,
                            groupValue,
                            n,
                            totalValue,
                            totalEL,
                            avgEL,
                            minEL,
                            maxEL,
                            stdDev
                    );
                }
            }
        }
    }
}