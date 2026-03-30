package scratch.anne.risk_system_vb.structural_response.vulnerabilities.expected;

import scratch.anne.risk_system_vb.structural_response.LibraryGetters;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.Metadata;
import scratch.anne.risk_system_vb.util.enums.IMT;

import java.util.*;

/**
 * Library of {@link ExpectedVulnerability} objects prepared for convolution.
 *
 * <p>Supports efficient lookup by:
 * <ul>
 *   <li>Vulnerability name</li>
 *   <li>IMKey (IMT string + IM values array)</li>
 * </ul>
 *
 * <p>This class assumes all vulnerabilities are already validated and
 * consistent (e.g., via a preparer). It does not modify input objects.
 *
 * <p>All internal maps are built once at construction and exposed as unmodifiable views.
 */
public class ExpectedVulnLibrary implements LibraryGetters<ExpectedVulnerability> {

    private final Map<String, ExpectedVulnerability> nameMap;
    private final Map<ImKey, List<ExpectedVulnerability>> imKeyMap;
    private final Metadata metadata;

    // -----------------------
    // Constructors
    // -----------------------

    public ExpectedVulnLibrary(
            List<ExpectedVulnerability> vulnerabilities,
            Metadata metadata) {

        this.metadata = metadata;

        Map<String, ExpectedVulnerability> nameMapTemp = new LinkedHashMap<>();
        Map<ImKey, List<ExpectedVulnerability>> imKeyMapTemp = new HashMap<>();
        initMaps(vulnerabilities, nameMapTemp, imKeyMapTemp);

        this.nameMap = Collections.unmodifiableMap(nameMapTemp);
        this.imKeyMap = Collections.unmodifiableMap(imKeyMapTemp);
    }

    public ExpectedVulnLibrary(List<ExpectedVulnerability> vulnerabilities) {
        this(vulnerabilities, null);
    }
    
    public static ExpectedVulnLibrary of(List<ExpectedVulnerability> vulnerabilities, Metadata metadata) {
        return new ExpectedVulnLibrary(vulnerabilities, metadata);
    }

    // -----------------------
    // Private helper
    // -----------------------

    private static void initMaps(
            List<ExpectedVulnerability> vulnerabilities,
            Map<String, ExpectedVulnerability> nameMapTemp,
            Map<ImKey, List<ExpectedVulnerability>> imKeyMapTemp) {

        for (ExpectedVulnerability v : vulnerabilities) {

            // ---- name map ----
            String name = v.getName();
            if (name == null || name.isEmpty())
                throw new IllegalArgumentException("Vulnerability must have a non-null name");
            if (nameMapTemp.containsKey(name))
                throw new IllegalArgumentException("Duplicate vulnerability name: " + name);
            nameMapTemp.put(name, v);

            // ---- imKey map ----
            ImKey key = v.getImKey(); // <--- use existing key
            imKeyMapTemp.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        // wrap inner lists
        for (var entry : imKeyMapTemp.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
    }

    // -----------------------
    // Accessors
    // -----------------------

    public ExpectedVulnerability getByName(String name) {
        return nameMap.get(name);
    }

    public List<ExpectedVulnerability> getByImKey(ImKey key) {
        return imKeyMap.getOrDefault(key, Collections.emptyList());
    }

    public Map<ImKey, List<ExpectedVulnerability>> getImKeyMap() {
        return imKeyMap;
    }

    public int size() {
        return nameMap.size();
    }

    @Override
    public List<ExpectedVulnerability> getModels() {
        return Collections.unmodifiableList(new ArrayList<>(nameMap.values()));
    }

    @Override
    public Set<String> getModelNames() {
        return nameMap.keySet();
    }

    @Override
    public ExpectedVulnerability getModelByName(String name) {
        return getByName(name);
    }

    @Override
    public Set<IMT> getIMTs() {
        Set<IMT> set = new LinkedHashSet<>();
        for (ExpectedVulnerability v : nameMap.values())
            set.add(v.getImt());
        return set;
    }

    @Override
    public List<ExpectedVulnerability> getByIMT(IMT imt) {
        List<ExpectedVulnerability> list = new ArrayList<>();
        for (ExpectedVulnerability v : nameMap.values()) {
            if (v.getImt() == imt) list.add(v);
        }
        return list;
    }

    @Override
    public Set<String> getIMTStrings() {
        Set<String> set = new LinkedHashSet<>();
        for (ExpectedVulnerability v : nameMap.values())
            set.add(v.getImtString());
        return set;
    }

    @Override
    public List<ExpectedVulnerability> getByIMTString(String imtString) {
        List<ExpectedVulnerability> list = new ArrayList<>();
        for (ExpectedVulnerability v : nameMap.values()) {
            if (v.getImtString().equals(imtString)) list.add(v);
        }
        return list;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }
}