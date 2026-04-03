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
import java.util.stream.Collectors;

/**
 * Immutable ExpectedLossPortfolio wrapper over an ImIndexedPortfolio of VulnerabilityAssets.
 *
 * <p>This class projects a full indexed portfolio into calculation-ready
 * ExpectedLossAsset instances while preserving all structural metadata,
 * indices, grouping maps, and ordering.</p>
 *
 * <p>All underlying maps and lists from the base portfolio are reused; nothing is rebuilt
 * except for projecting values into ExpectedLossAsset.</p>
 */
public final class ExpectedLossPortfolio implements Iterable<ExpectedLossAsset> {

    /** Base Indexed Portfolio (wraps original Portfolio) */
//    private final ImIndexedPortfolio<? extends VulnerabilityAsset> imIndexedPortfolio;

    /** 1-to-1 mapping of VulnerabilityAsset → ExpectedLossAsset */
    private final Map<VulnerabilityAsset, ExpectedLossAsset> elossMap;

    /** Cached ordered asset list */
    private final List<ExpectedLossAsset> assets;

    /** Asset ID lookup */
    private final Map<String, ExpectedLossAsset> assetsByID;

    /** Site-keyed lookup */
    private final Map<SiteKey, List<ExpectedLossAsset>> assetsBySite;

    /** IM-keyed lookup */
    private final Map<ImKey, List<ExpectedLossAsset>> assetsByImKey;
    
    /** Site IM-keyed lookup */
    private final Map<SiteKey,Map<ImKey,List<ExpectedLossAsset>>> assetsBySiteAndImKey;

    /** Additional fields lookup */
    private final Map<String, Map<String, List<ExpectedLossAsset>>> assetsByAdditionalField;
    
    /** indicator for whether results are calculated */
    private boolean expectedLossComputed;

    // ---------------------------------------------------------------------
    // CONSTRUCTOR
    // ---------------------------------------------------------------------

    public ExpectedLossPortfolio(ImIndexedPortfolio<? extends VulnerabilityAsset> indexedPortfolio) {
//        this.imIndexedPortfolio = Objects.requireNonNull(indexedPortfolio);
        
        expectedLossComputed = false;

        // ---------- 1-to-1 mapping ----------
        Map<VulnerabilityAsset, ExpectedLossAsset> map = new LinkedHashMap<>();
        List<ExpectedLossAsset> orderedList = new ArrayList<>();
        for (VulnerabilityAsset a : indexedPortfolio.getAssets()) {
            ExpectedLossAsset e = new ExpectedLossAsset(a);
            map.put(a, e);
            orderedList.add(e);
        }
        this.elossMap = Collections.unmodifiableMap(map);
        this.assets = Collections.unmodifiableList(orderedList);

        // ---------- asset ID ----------
        Map<String, ExpectedLossAsset> idMap = new LinkedHashMap<>();
        for (ExpectedLossAsset e : assets) {
            idMap.put(e.getAssetID(), e);
        }
        this.assetsByID = Collections.unmodifiableMap(idMap);

        // ---------- SiteKey mapping ----------
        Map<SiteKey, List<ExpectedLossAsset>> siteMap = new LinkedHashMap<>();
        for (SiteKey s : indexedPortfolio.getSiteKeys()) {
            List<ExpectedLossAsset> wrapped = indexedPortfolio.getAssetsBySite(s)
                    .stream()
                    .map(elossMap::get)
                    .collect(Collectors.toList());
            siteMap.put(s, Collections.unmodifiableList(wrapped));
        }
        this.assetsBySite = Collections.unmodifiableMap(siteMap);

        // ---------- IMKey mapping ----------
        Map<ImKey, List<ExpectedLossAsset>> imMap = new LinkedHashMap<>();
        for (ImKey k : indexedPortfolio.getImKeys()) {
            List<ExpectedLossAsset> wrapped = indexedPortfolio.getAssetsByImKey(k)
                    .stream()
                    .map(elossMap::get)
                    .collect(Collectors.toList());
            imMap.put(k, Collections.unmodifiableList(wrapped));
        }
        this.assetsByImKey = Collections.unmodifiableMap(imMap);
        
        // ---------- Site and ImKey mapping -------
        Map<SiteKey, Map<ImKey, List<ExpectedLossAsset>>> siteImMap =
                new LinkedHashMap<>();
        for (SiteKey site : indexedPortfolio.getSiteKeys()) {
            Map<ImKey, List<ExpectedLossAsset>> innerMap =
                    new LinkedHashMap<>();
            for (ImKey imKey : indexedPortfolio.getImKeys()) {
                List<ExpectedLossAsset> wrapped =
                        indexedPortfolio
                                .getAssetsBySiteAndImKey(site, imKey)
                                .stream()
                                .map(elossMap::get)
                                .collect(Collectors.toList());
                if (!wrapped.isEmpty()) {
                    innerMap.put(
                            imKey,
                            Collections.unmodifiableList(wrapped)
                    );
                }
            }
            siteImMap.put(
                    site,
                    Collections.unmodifiableMap(innerMap)
            );
        }
        this.assetsBySiteAndImKey =
                Collections.unmodifiableMap(siteImMap);   

        // ---------- Additional field mapping ----------
        Map<String, Map<String, List<ExpectedLossAsset>>> extraMap = new LinkedHashMap<>();
        for (String field : indexedPortfolio.getAdditionalFieldNames()) {
            Map<String, List<ExpectedLossAsset>> valueMap = new LinkedHashMap<>();
            for (String val : indexedPortfolio.getAdditionalFieldValues(field)) {
                List<ExpectedLossAsset> wrapped = indexedPortfolio.getAssetsByAdditionalField(field, val)
                        .stream()
                        .map(elossMap::get)
                        .collect(Collectors.toList());
                valueMap.put(val, Collections.unmodifiableList(wrapped));
            }
            extraMap.put(field, Collections.unmodifiableMap(valueMap));
        }
        this.assetsByAdditionalField = Collections.unmodifiableMap(extraMap);
    }
    
    /**
     * Check if the portfolio has been updated with expected loss values.
     */
    public boolean isExpectedLossComputed() {
        return expectedLossComputed;
    }

    /**
     * Package-private setter, only the calculator should flip this.
     */
    public void setExpectedLossComputed(boolean computed) {
        this.expectedLossComputed = computed;
    }

    // ---------------------------------------------------------------------
    // BASIC ASSET ACCESS
    // ---------------------------------------------------------------------
    
    public List<ExpectedLossAsset> getAssetsBySiteAndImKey(
            SiteKey site,
            ImKey imKey) {

        return assetsBySiteAndImKey
                .getOrDefault(site, Collections.emptyMap())
                .getOrDefault(imKey, Collections.emptyList());
    }
    
    public Set<ImKey> getImKeysBySite(SiteKey site) {
        return assetsBySiteAndImKey
                .getOrDefault(site, Collections.emptyMap())
                .keySet();
    }

    public List<ExpectedLossAsset> getAssets() { return assets; }

    public ExpectedLossAsset getAssetByID(String id) { return assetsByID.get(id); }

    public Set<SiteKey> getSiteKeys() { return assetsBySite.keySet(); }

    public List<ExpectedLossAsset> getAssetsBySite(SiteKey s) {
        return assetsBySite.getOrDefault(s, List.of());
    }

    public Set<ImKey> getImKeys() { return assetsByImKey.keySet(); }

    public List<ExpectedLossAsset> getAssetsByImKey(ImKey k) {
        return assetsByImKey.getOrDefault(k, List.of());
    }

    public Set<String> getAdditionalFieldNames() { return assetsByAdditionalField.keySet(); }

    public Map<String, List<ExpectedLossAsset>> getAssetsByAdditionalField(String field) {
        return assetsByAdditionalField.getOrDefault(field, Map.of());
    }

    public int size() { return assets.size(); }

    @Override
    public Iterator<ExpectedLossAsset> iterator() { return assets.iterator(); }

    /** Internal lookup from original asset */
    ExpectedLossAsset get(VulnerabilityAsset a) { return elossMap.get(a); }
    
 // ---------------------------------------------------------------------
 // AGGREGATED EXPECTED LOSS GETTERS
 // ---------------------------------------------------------------------

	 /** @return total expected loss of all assets */
	 public double getTotalExpectedLoss() {
	     return assets.stream()
	             .mapToDouble(ExpectedLossAsset::getExpectedLoss)
	             .sum();
	 }
	
	 /** @return total asset value of all assets */
	 public double getTotalAssetValue() {
	     return assets.stream()
	             .mapToDouble(ExpectedLossAsset::getValue)
	             .sum();
	 }
	
	 /** @return expected loss aggregated by SiteKey */
	 public Map<SiteKey, Double> getExpectedLossBySite() {
	     Map<SiteKey, Double> result = new LinkedHashMap<>();
	     for (SiteKey s : getSiteKeys()) {
	         double sum = getAssetsBySite(s).stream()
	                 .mapToDouble(ExpectedLossAsset::getExpectedLoss)
	                 .sum();
	         result.put(s, sum);
	     }
	     return result;
	 }
	
	 /** @return expected loss aggregated by IMKey */
	 public Map<ImKey, Double> getExpectedLossByImKey() {
	     Map<ImKey, Double> result = new LinkedHashMap<>();
	     for (ImKey k : getImKeys()) {
	         double sum = getAssetsByImKey(k).stream()
	                 .mapToDouble(ExpectedLossAsset::getExpectedLoss)
	                 .sum();
	         result.put(k, sum);
	     }
	     return result;
	 }
	
	 /** @return expected loss aggregated by vulnerability model */
	 public Map<String, Double> getExpectedLossByVulnerability() {
	     Map<String, Double> result = new LinkedHashMap<>();
	     for (ExpectedLossAsset a : assets) {
	         result.merge(a.getModelName(), a.getExpectedLoss(), Double::sum);
	     }
	     return result;
	 }
	
	 /** @return expected loss aggregated by a dynamic additional field */
	 public Map<String, Double> getExpectedLossByAdditionalField(String field) {
	     Map<String, Double> result = new LinkedHashMap<>();
	     Map<String, List<ExpectedLossAsset>> groups = getAssetsByAdditionalField(field);
	     for (Map.Entry<String, List<ExpectedLossAsset>> e : groups.entrySet()) {
	         double sum = e.getValue().stream()
	                 .mapToDouble(ExpectedLossAsset::getExpectedLoss)
	                 .sum();
	         result.put(e.getKey(), sum);
	     }
	     return result;
	 }
	
	 /** @return total asset value aggregated by a dynamic additional field */
	 public Map<String, Double> getTotalValueByAdditionalField(String field) {
	     Map<String, Double> result = new LinkedHashMap<>();
	     Map<String, List<ExpectedLossAsset>> groups = getAssetsByAdditionalField(field);
	     for (Map.Entry<String, List<ExpectedLossAsset>> e : groups.entrySet()) {
	         double sum = e.getValue().stream()
	                 .mapToDouble(ExpectedLossAsset::getValue)
	                 .sum();
	         result.put(e.getKey(), sum);
	     }
	     return result;
	 }
	 
    /** Prints a summary of portfolio losses including per-asset detail. */
    public void printSummary() {
        System.out.println("----- ELossPortfolio Summary -----");
        System.out.printf("Total assets: %d%n", assets.size());
        System.out.printf("Total expected loss: %.2e%n", getTotalExpectedLoss());
    }
	
	 /** @return summary statistics (min, max, avg, stddev) for a group of assets */
	 public static Map<String, Double> summarizeExpectedLoss(List<ExpectedLossAsset> assets) {
	     Map<String, Double> stats = new LinkedHashMap<>();
	     int n = assets.size();
	     if (n == 0) return stats;
	
	     double total = assets.stream().mapToDouble(ExpectedLossAsset::getExpectedLoss).sum();
	     double avg = total / n;
	     double min = assets.stream().mapToDouble(ExpectedLossAsset::getExpectedLoss).min().orElse(Double.NaN);
	     double max = assets.stream().mapToDouble(ExpectedLossAsset::getExpectedLoss).max().orElse(Double.NaN);
	     double variance = assets.stream().mapToDouble(a -> Math.pow(a.getExpectedLoss() - avg, 2)).sum() / n;
	     double stdDev = Math.sqrt(variance);
	
	     stats.put("total", total);
	     stats.put("avg", avg);
	     stats.put("min", min);
	     stats.put("max", max);
	     stats.put("stdDev", stdDev);
	     stats.put("n", (double)n);
	
	     return stats;
	 }
	 
    // ---------------------------------------------------------------------
    // CSV EXPORT
    // ---------------------------------------------------------------------

    public void writeCSV(Path file) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file))) {
            // Header
            pw.print("AssetID,Latitude,Longitude,Vs30,Value,Vulnerability");
            for (String field : getAdditionalFieldNames()) pw.print("," + field);
            pw.println(",ExpectedLoss");

            for (ExpectedLossAsset a : assets) {
                pw.printf("\"%s\",%.6f,%.6f,%.0f,%.2f,\"%s\"",
                        a.getAssetID(), a.getLatitude(), a.getLongitude(),
                        a.getVs30(), a.getValue(), a.getModelName());
                Map<String,String> extras = a.getAdditionalFields();
                for (String f : getAdditionalFieldNames()) pw.printf(",\"%s\"", extras.getOrDefault(f,""));
                pw.printf(",%.6e%n", a.getExpectedLoss());
            }
        }
    }

    public void writeAggregatedCSV(Path file) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file))) {
            pw.println("GroupField,GroupValue,NumAssets,TotalValue,TotalExpectedLoss," +
                    "AvgExpectedLoss,MinExpectedLoss,MaxExpectedLoss,StdDevExpectedLoss");
            for (String field : getAdditionalFieldNames()) {
                Map<String,List<ExpectedLossAsset>> groups = getAssetsByAdditionalField(field);
                for (Map.Entry<String, List<ExpectedLossAsset>> e : groups.entrySet()) {
                    Map<String, Double> stats = summarizeExpectedLoss(e.getValue());
                    double totalValue = e.getValue().stream().mapToDouble(ExpectedLossAsset::getValue).sum();

                    pw.printf("\"%s\",\"%s\",%d,%.2f,%.6e,%.6f,%.6f,%.6f,%.6f%n",
                            field,
                            e.getKey(),
                            stats.get("n").intValue(),
                            totalValue,
                            stats.get("total"),
                            stats.get("avg"),
                            stats.get("min"),
                            stats.get("max"),
                            stats.get("stdDev"));
                }
            }
        }
    }
}