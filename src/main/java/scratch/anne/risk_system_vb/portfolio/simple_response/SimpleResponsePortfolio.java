//package scratch.anne.risk_system_vb.portfolio.simple_response;
//
//import scratch.anne.risk_system_vb.calc.convolution.SimpleImResponseLibrary;
//import scratch.anne.risk_system_vb.calc.convolution.SimpleImResponse;
//import scratch.anne.risk_system_vb.util.AssetKeys;
//
//import java.util.*;
//
///**
// * Prepared portfolio of SimpleImResponse objects used for calculations.
// * 
// * <p>This class is immutable. It indexes responses by IMKey and optionally by extra grouping fields.
// */
//public class SimpleResponsePortfolio {
//
//    private final List<SimpleImResponse> responses;
//    private final Map<AssetKeys.ImKey, List<SimpleImResponse>> responsesByImKey;
//    private final Map<String, SimpleImResponse> responsesByName; // optional
//
//    // -----------------------
//    // Constructors
//    // -----------------------
//
//    public SimpleResponsePortfolio(SimpleImResponseLibrary<? extends SimpleImResponse> library) {
//        this(library.getAllResponses(), null);
//    }
//
//    public SimpleResponsePortfolio(
//            Collection<? extends SimpleImResponse> responses,
//            Set<String> extraFields) {
//
//        List<SimpleImResponse> respList = new ArrayList<>(responses);
//        Map<AssetKeys.ImKey, List<SimpleImResponse>> byImKey = new HashMap<>();
//        Map<String, SimpleImResponse> byName = new LinkedHashMap<>();
//        Map<String, Map<String, List<SimpleImResponse>>> byExtraGroup = new HashMap<>();
//        Set<String> extraFieldSet = extraFields != null ? new LinkedHashSet<>(extraFields) : new LinkedHashSet<>();
//
//        for (SimpleImResponse r : respList) {
//
//            // IMKey index
//            AssetKeys.ImKey key = new AssetKeys.ImKey(r.getImtString(), r.getImValues());
//            byImKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
//        }
//
//        // wrap for immutability
//        Map<AssetKeys.ImKey, List<SimpleImResponse>> byImKeyFinal = new HashMap<>();
//        for (var e : byImKey.entrySet()) {
//            byImKeyFinal.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
//        }
//
//        Map<String, Map<String, List<SimpleImResponse>>> byExtraFinal = new HashMap<>();
//        for (var e : byExtraGroup.entrySet()) {
//            Map<String, List<SimpleImResponse>> inner = new HashMap<>();
//            for (var e2 : e.getValue().entrySet()) {
//                inner.put(e2.getKey(), Collections.unmodifiableList(e2.getValue()));
//            }
//            byExtraFinal.put(e.getKey(), Collections.unmodifiableMap(inner));
//        }
//
//        this.responses = Collections.unmodifiableList(respList);
//        this.responsesByImKey = Collections.unmodifiableMap(byImKeyFinal);
//        this.responsesByName = Collections.unmodifiableMap(byName);
//    }
//
//    // -----------------------
//    // Accessors
//    // -----------------------
//
//    public List<SimpleImResponse> getAll() {
//        return responses;
//    }
//
//    public SimpleImResponse getByName(String name) {
//        return responsesByName.get(name);
//    }
//
//    public List<SimpleImResponse> getByImKey(AssetKeys.ImKey key) {
//        return responsesByImKey.getOrDefault(key, Collections.emptyList());
//    }
//
//    public int size() {
//        return responses.size();
//    }
//}