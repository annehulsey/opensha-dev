package scratch.anne.risk_system_vb.domain.hazard;

import scratch.anne.risk_system_vb.io.writers.HazardCurvesExporter;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class HazardCurveCollection {

    // ------------------------------------------------------------
    // METADATA (single source of truth)
    // ------------------------------------------------------------

    private final HazardParameters parameters;

    // ------------------------------------------------------------
    // DATA
    // ------------------------------------------------------------

    private final Map<SiteKey, Map<ImKey, HazardCurve>> data;

    private boolean frozen = false;

    // ------------------------------------------------------------
    // CONSTRUCTOR
    // ------------------------------------------------------------

    public HazardCurveCollection(HazardParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters);
        this.data = new ConcurrentHashMap<>();
    }

    // ------------------------------------------------------------
    // MUTATION PHASE
    // ------------------------------------------------------------

    public void put(SiteKey site, ImKey im, HazardCurve hazard) {
        if (frozen) {
            throw new IllegalStateException("HazardCurveCollection is frozen");
        }

        data.computeIfAbsent(site, s -> new ConcurrentHashMap<>())
        .put(im, hazard);
    }

    // ------------------------------------------------------------
    // FREEZE
    // ------------------------------------------------------------

    public void freeze() {
        if (frozen) return;

        for (Map.Entry<SiteKey, Map<ImKey, HazardCurve>> e : data.entrySet()) {
            e.setValue(Collections.unmodifiableMap(e.getValue()));
        }

        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    // ------------------------------------------------------------
    // ACCESSORS
    // ------------------------------------------------------------

    public HazardParameters getParameters() {
        return parameters;
    }

    public Map<SiteKey, Map<ImKey, HazardCurve>> asMap() {
        return Collections.unmodifiableMap(data);
    }

    public Set<SiteKey> getSiteKeys() {
        return data.keySet();
    }

    public Map<ImKey, HazardCurve> getSiteView(SiteKey site) {
        return data.getOrDefault(site, Map.of());
    }

    public HazardCurve get(SiteKey site, ImKey im) {
        return data.getOrDefault(site, Map.of()).get(im);
    }

    // ------------------------------------------------------------
    // ITERATION (important for export)
    // ------------------------------------------------------------

    public void forEachCurve(HazardConsumer consumer) {
        data.forEach((site, imMap) ->
            imMap.forEach((im, curve) ->
                consumer.accept(site, im, curve)
            )
        );
    }

    @FunctionalInterface
    public interface HazardConsumer {
        void accept(SiteKey site, ImKey im, HazardCurve curve);
    }

    // ------------------------------------------------------------
    // EXPORT HOOK (key addition)
    // ------------------------------------------------------------

    public void export(HazardCurvesExporter exporter, Path file) {
        exporter.export(this, file);
    }
}