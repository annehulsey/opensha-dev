package scratch.anne.risk_system_vb.io.writers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Threaded NDJSON hazard curve writer using Gson.
 *
 * <p>Each line is a self-contained JSON object:
 *
 * <pre>
 * {"lat":39.7,"lon":-105.2,"vs30":760,"imt":"PGA",
 *  "imls":[...],
 *  "poe":[...]}
 * </pre>
  */
public class HazardJsonWriter implements AutoCloseable {

    // ============================================================
    // CONFIG
    // ============================================================

    private final BufferedWriter writer;
    private final Thread writerThread;

    private final BlockingQueue<Record> queue =
            new LinkedBlockingQueue<>();

    private volatile boolean running = true;

    private static final Record POISON =
            new Record(null, null, null);

    private final Gson gson =
            new GsonBuilder().create();

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public HazardJsonWriter(Path file) {

        try {
            this.writer = Files.newBufferedWriter(file);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to open hazard JSON file: " + file, e);
        }

        writerThread = new Thread(this::run, "HazardJsonWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public void write(SiteKey site, ImKey imKey, double[] hazardY) {

        if (!running)
            throw new IllegalStateException(
                    "HazardJsonWriter already closed");

        queue.add(new Record(site, imKey, hazardY));
    }
    
    public void writePortfolio(RiskConvolutionPortfolio portfolio) {

    	portfolio.forEachHazard((site, imKey, result) ->
        	write(site, imKey, result.getProbabilities()));

    }

    @Override
    public void close() {

        if (!running)
            return;

        running = false;

        try {
            queue.put(POISON);
            writerThread.join();
            writer.flush();
            writer.close();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed closing hazard JSON writer", e);
        }
    }

    // ============================================================
    // WORKER THREAD
    // ============================================================

    private void run() {

        try {

            while (true) {

                Record r = queue.take();

                if (r == POISON)
                    break;

                writeRecord(r);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Hazard JSON writer failed", e);
        }
    }

    private void writeRecord(Record r) throws IOException {

        double[] x = r.imKey.getLinearValues();

        HazardJson json = new HazardJson();

        json.lat = r.site.getLat();
        json.lon = r.site.getLon();
        json.vs30 = r.site.getVs30();

        json.imt = r.imKey.getImtString();

        json.imls = x;
        json.poe = r.y;

        writer.write(gson.toJson(json));
        writer.write("\n");
    }

    // ============================================================
    // DATA MODEL
    // ============================================================

    private static class HazardJson {

        double lat;
        double lon;
        double vs30;

        String imt;

        double[] imls;
        double[] poe;
    }

    private static class Record {

        final SiteKey site;
        final ImKey imKey;
        final double[] y;

        Record(SiteKey site, ImKey imKey, double[] y) {
            this.site = site;
            this.imKey = imKey;
            this.y = y;
        }
    }
}