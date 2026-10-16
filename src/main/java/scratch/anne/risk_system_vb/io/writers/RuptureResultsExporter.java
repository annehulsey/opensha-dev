package scratch.anne.risk_system_vb.io.writers;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.OutputFile;

import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.engine.per_rupture.RuptureLossResultsCollection;
import scratch.anne.risk_system_vb.engine.per_rupture.RuptureLossResultsCollection.RuptureResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class RuptureResultsExporter {

    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "RuptureResult",
              "namespace": "scratch.anne.risk_system_vb",
              "fields": [
                {"name": "sourceId",             "type": "int"},
                {"name": "fltSysRupIndex",       "type": ["null", "int"], "default": null},
                {"name": "ruptureId",            "type": "int"},
                {"name": "magnitude",            "type": "double"},
                {"name": "likelihood",           "type": "double"},
                {"name": "ruptureLoss",          "type": "double"},
                {"name": "lossContribution",     "type": "double"},
                {"name": "relativeContribution", "type": "double"}
              ]
            }
            """);

    // Prefixes so erfConfig / filterConfig keys can't collide with each
    // other or with the fixed fields (erfName, gmmName, etc.) when both
    // are flattened into the same metadata map/CSV header block.
    private static final String ERF_CONFIG_PREFIX = "erfConfig.";
    private static final String FILTER_CONFIG_PREFIX = "filterConfig.";

    // ------------------------------------------------------------
    // PUBLIC ENTRY
    // ------------------------------------------------------------
    public void export(RuptureLossResultsCollection collection,
                       Path file,
                       FileFormat format) {
        switch (format) {
            case CSV     -> exportCsv(collection, file);
            case PARQUET -> exportParquet(collection, file);
            default      -> throw new IllegalArgumentException("Unknown format: " + format);
        }
    }

    // ------------------------------------------------------------
    // Shared: builds the full reproducibility metadata map
    // ------------------------------------------------------------
    private Map<String, String> buildMetadata(HazardParameters hp, double totalExpectedLoss) {

        Map<String, String> meta = new HashMap<>();
        meta.put("erfName",           hp.getErfName());
        meta.put("erfDuration",       String.valueOf(hp.getErfDuration()));
        meta.put("hazardMetric",      hp.getHazardMetric().name());
        meta.put("gmmName",           hp.getGmmName());
        meta.put("totalExpectedLoss", String.valueOf(totalExpectedLoss));

        // solutionFile: null means the self-constructing route was used
        // (erfName built its own solution internally). Recorded
        // explicitly as an empty string rather than omitted, so the
        // absence itself is unambiguous on read-back rather than looking
        // like a missing/forgotten field.
        Path solutionFile = hp.getSolutionFile();
        meta.put("solutionFile", solutionFile == null ? "" : solutionFile.toString());

        // erfConfig: the construction-time parameters (useRupMFDs,
        // includeBackground, griddedSurfaceType, griddedFiniteNumSurfaces,
        // etc. -- see ErfBuilder) needed to reproduce this exact ERF.
        // Prefixed so these keys can't collide with filterConfig entries
        // or the fixed fields above.
        Map<String, String> erfConfig = hp.getErfConfig();
        if (erfConfig != null) {
            for (Map.Entry<String, String> e : erfConfig.entrySet()) {
                meta.put(ERF_CONFIG_PREFIX + e.getKey(), e.getValue());
            }
        }

        Map<String, String> filterConfig = hp.getFilterConfig();
        if (filterConfig != null) {
            for (Map.Entry<String, String> e : filterConfig.entrySet()) {
                meta.put(FILTER_CONFIG_PREFIX + e.getKey(), e.getValue());
            }
        }

        return meta;
    }

    // ------------------------------------------------------------
    // PARQUET EXPORT
    // ------------------------------------------------------------
    private void exportParquet(RuptureLossResultsCollection collection, Path file) {
        HazardParameters hp = collection.getHazardParameters();

        Map<String, String> meta = buildMetadata(hp, collection.getTotalExpectedLoss());

        OutputFile out = new LocalOutputFile(file.toAbsolutePath().normalize());

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter
                .<GenericRecord>builder(out)
                .withSchema(SCHEMA)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                .withExtraMetaData(meta)
                .build()) {

            for (RuptureResult r : collection.results()) {
                GenericRecord record = new GenericData.Record(SCHEMA);
                record.put("sourceId",            r.key().sourceId());
                record.put("fltSysRupIndex",      r.fltSysRupIndex());
                record.put("ruptureId",           r.key().ruptureId());
                record.put("magnitude",           r.magnitude());
                record.put("likelihood",          r.likelihood());
                record.put("ruptureLoss",         r.ruptureLoss());
                record.put("lossContribution",    r.lossContribution());
                record.put("relativeContribution", r.relativeContribution());
                writer.write(record);
            }

        } catch (IOException e) {
            throw new RuntimeException("Parquet export failed", e);
        }
        System.out.println("Parquet export complete: " + file.toAbsolutePath());
    }

    // ------------------------------------------------------------
    // CSV EXPORT
    // ------------------------------------------------------------
    private void exportCsv(RuptureLossResultsCollection collection, Path file) {
        HazardParameters hazardParameters = collection.getHazardParameters();

        Map<String, String> meta = buildMetadata(hazardParameters, collection.getTotalExpectedLoss());
        // totalExpectedLoss is written separately below, in its own
        // blank-line-delimited block, matching the original file's
        // layout -- pull it out here so it isn't double-written above.
        String totalExpectedLossStr = meta.remove("totalExpectedLoss");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("# HazardParameters");
            writer.newLine();

            // Fixed fields first, in a stable order.
            writeParam(writer, "erfName",      meta.remove("erfName"));
            writeParam(writer, "erfDuration",  meta.remove("erfDuration"));
            writeParam(writer, "hazardMetric", meta.remove("hazardMetric"));
            writeParam(writer, "gmmName",      meta.remove("gmmName"));
            writeParam(writer, "solutionFile", meta.remove("solutionFile"));

            // Remaining entries: erfConfig.* and filterConfig.* (order
            // not guaranteed, same as the original filterConfig loop).
            for (Map.Entry<String, String> e : meta.entrySet()) {
                writeParam(writer, e.getKey(), e.getValue());
            }

            writer.newLine();
            writeParam(writer, "totalExpectedLoss", totalExpectedLossStr);
            writer.newLine();

            writer.write("sourceId,fltSysRupIndex,ruptureId,magnitude,likelihood,ruptureLoss,lossContribution,relativeContribution");
            writer.newLine();

            for (RuptureResult r : collection.results()) {
                writer.write(formatCsvRow(r));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV export failed", e);
        }
    }

    private String formatCsvRow(RuptureResult r) {
        return r.key().sourceId()
        		+ "," + (r.fltSysRupIndex() == null ? "" : r.fltSysRupIndex())
                + "," + r.key().ruptureId()                
                + "," + r.magnitude()
                + "," + r.likelihood()
                + "," + r.ruptureLoss()
                + "," + r.lossContribution()
                + "," + r.relativeContribution();
    }

    private void writeParam(BufferedWriter writer, String param, String value) {
        try {
            writer.write(param + "," + value);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}