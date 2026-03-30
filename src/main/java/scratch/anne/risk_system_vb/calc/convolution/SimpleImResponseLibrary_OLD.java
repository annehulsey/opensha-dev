//package scratch.anne.risk_system_vb.calc.convolution;
//
//import java.util.*;
//
//import scratch.anne.risk_system_vb.structural_response.NamedResponseModel;
//import scratch.anne.risk_system_vb.structural_response.LibraryGetters;
//import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
//import scratch.anne.risk_system_vb.util.Metadata;
//import scratch.anne.risk_system_vb.util.enums.IMT;
//
///**
// * Computational library of {@link AbstractSimpleImResponse} objects.
// *
// * Provides:
// *  • lookup by name
// *  • lookup by IMT
// *  • lookup by IMT string
// *  • lookup by IMKey (IMT + numeric grid)
// *
// * @param <T> response type
// */
//public class SimpleImResponseLibrary<
//        T extends AbstractSimpleImResponse & NamedResponseModel>
//        implements LibraryGetters<T> {
//
//    // --------------------------------------------------
//    // Core storage
//    // --------------------------------------------------
//
//    private final List<T> models;
//    private final Map<String, T> nameMap;
//
//    private final Map<IMT, List<T>> imtMap;
//    private final Map<String, List<T>> imtStringMap;
//
//    /** grouping by IM grid */
//    private final Map<ImKey, List<T>> keyMap;
//
//    private final Metadata metadata;
//
//    // --------------------------------------------------
//    // Constructor (private — use factory)
//    // --------------------------------------------------
//
//    private SimpleImResponseLibrary(List<T> models, Metadata metadata) {
//
//        this.models = Collections.unmodifiableList(new ArrayList<>(models));
//        this.metadata = metadata;
//
//        Map<String, T> nMap = new LinkedHashMap<>();
//        Map<IMT, List<T>> iMap = new LinkedHashMap<>();
//        Map<String, List<T>> iStrMap = new LinkedHashMap<>();
//        Map<ImKey, List<T>> kMap = new LinkedHashMap<>();
//
//        for (T r : models) {
//
//            String name = r.getName();
//            if (name == null || name.isBlank())
//                throw new IllegalArgumentException("Response missing name");
//
//            if (nMap.containsKey(name))
//                throw new IllegalArgumentException("Duplicate response name: " + name);
//
//            nMap.put(name, r);
//
//            // ---- IMT grouping ----
//            iMap.computeIfAbsent(r.getImt(), k -> new ArrayList<>()).add(r);
//            iStrMap.computeIfAbsent(r.getImtString(), k -> new ArrayList<>()).add(r);
//
//            // ---- IM grid grouping ----
//            ImKey key = new ImKey(r.getImtString(), r.getImValues());
//            kMap.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
//        }
//
//        this.nameMap = Collections.unmodifiableMap(nMap);
//        this.imtMap = unmodifiableDeep(iMap);
//        this.imtStringMap = unmodifiableDeep(iStrMap);
//        this.keyMap = unmodifiableDeep(kMap);
//    }
//
//    // --------------------------------------------------
//    // Factory
//    // --------------------------------------------------
//
//    public static <T extends AbstractSimpleImResponse & NamedResponseModel>
//    SimpleImResponseLibrary<T> of(List<T> models, Metadata metadata) {
//        return new SimpleImResponseLibrary<T>(models, metadata);
//    }
//
//    // --------------------------------------------------
//    // LibraryGetters implementation
//    // --------------------------------------------------
//
//    @Override
//    public List<T> getModels() {
//        return models;
//    }
//
//    @Override
//    public Set<String> getModelNames() {
//        return nameMap.keySet();
//    }
//
//    @Override
//    public T getModelByName(String name) {
//        T r = nameMap.get(name);
//        if (r == null)
//            throw new NoSuchElementException("Model not found: " + name);
//        return r;
//    }
//
//    @Override
//    public Set<IMT> getIMTs() {
//        return imtMap.keySet();
//    }
//
//    @Override
//    public List<T> getByIMT(IMT imt) {
//        return imtMap.getOrDefault(imt, List.of());
//    }
//
//    @Override
//    public Set<String> getIMTStrings() {
//        return imtStringMap.keySet();
//    }
//
//    @Override
//    public List<T> getByIMTString(String imtString) {
//        return imtStringMap.getOrDefault(imtString, List.of());
//    }
//
//    @Override
//    public Metadata getMetadata() {
//        return metadata;
//    }
//
//    @Override
//    public int size() {
//        return models.size();
//    }
//
//    // --------------------------------------------------
//    // Convolution-specific lookup
//    // --------------------------------------------------
//
//    /** Lookup responses sharing identical IM grid */
//    public List<T> getByKey(String imtString, double[] imValues) {
//        return keyMap.getOrDefault(
//                new ImKey(imtString, imValues),
//                List.of());
//    }
//
//    public Map<ImKey, List<T>> getKeyMap() {
//        return keyMap;
//    }
//
//    // --------------------------------------------------
//    // Utilities
//    // --------------------------------------------------
//
//    private static <K,V> Map<K,List<V>> unmodifiableDeep(Map<K,List<V>> input) {
//        Map<K,List<V>> out = new LinkedHashMap<>();
//        input.forEach((k,v) -> out.put(k, List.copyOf(v)));
//        return Collections.unmodifiableMap(out);
//    }
//
//    @Override
//    public String toString() {
//        return "SimpleImResponseLibrary{" +
//                "nModels=" + size() +
//                ", metadata=" + metadata +
//                '}';
//    }
//}


