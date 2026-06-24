package scratch.anne.risk_system_vb.io.writers;

import scratch.anne.risk_system_vb.domain.hazard.HazardCurve;
import scratch.anne.risk_system_vb.domain.hazard.HazardCurveCollection;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class HazardCurvesExporter {

    private final Gson gson = new GsonBuilder().create();

    public void export(HazardCurveCollection collection, Path file) {

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {

            // ---------------- HEADER ----------------
            writer.write(gson.toJson(collection.getParameters()));
            writer.write("\n");

            // ---------------- DATA ----------------
            collection.forEachCurve((SiteKey site, ImKey im, HazardCurve curve) -> {

                HazardJson json = new HazardJson();

                json.lat = site.getLat();
                json.lon = site.getLon();
                json.vs30 = site.getVs30();

                json.imt = im.getImtString();
                json.imls = im.getLinearValues();
                json.hazard = curve.getHazard();

                try {
                    writer.write(gson.toJson(json));
                    writer.write("\n");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed exporting hazard", e);
        }
    }

 // Fields are accessed by Gson via reflection for JSON serialization
    @SuppressWarnings("unused")
    private static class HazardJson {
        double lat;
        double lon;
        double vs30;
        String imt;
        double[] imls;
        double[] hazard;
    }
}