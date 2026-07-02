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
import scratch.anne.risk_system_vb.engine.accumulators.RuptureResultsCollection;
import scratch.anne.risk_system_vb.engine.accumulators.RuptureResultsCollection.RuptureResult;

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
                {"name": "ruptureId",            "type": "int"},
                {"name": "likelihood",           "type": "double"},
                {"name": "ruptureLoss",          "type": "double"},
                {"name": "lossContribution",     "type": "double"},
                {"name": "relativeContribution", "type": "double"}
              ]
            }
            """);

    // ------------------------------------------------------------
    // PUBLIC ENTRY
    // ------------------------------------------------------------
    public void export(RuptureResultsCollection collection,
                       Path file,
                       FileFormat format) {
        switch (format) {
            case CSV     -> exportCsv(collection, file);
            case PARQUET -> exportParquet(collection, file);
            default      -> throw new IllegalArgumentException("Unknown format: " + format);
        }
    }

    // ------------------------------------------------------------
    // PARQUET EXPORT
    // ------------------------------------------------------------
    private void exportParquet(RuptureResultsCollection collection, Path file) {
        HazardParameters hp = collection.getHazardParameters();

        // Build metadata map — same fields as the CSV header block
        Map<String, String> meta = new HashMap<>();
        meta.put("erfName",           hp.getErfName());
        meta.put("erfDuration",       String.valueOf(hp.getErfDuration()));
        meta.put("hazardMetric",      hp.getHazardMetric().name());
        meta.put("gmmName",           hp.getGmmName());
        meta.put("totalExpectedLoss", String.valueOf(collection.getTotalExpectedLoss()));

        Map<String, String> filterConfig = hp.getFilterConfig();
        if (filterConfig != null) {
            meta.putAll(filterConfig);
        }

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
                record.put("ruptureId",           r.key().ruptureId());
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
    // CSV EXPORT (unchanged)
    // ------------------------------------------------------------
    private void exportCsv(RuptureResultsCollection collection, Path file) {
        HazardParameters hazardParameters = collection.getHazardParameters();
        Map<String, String> filterConfig = hazardParameters.getFilterConfig();

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("# HazardParameters");
            writer.newLine();
            writeParam(writer, "erfName",     hazardParameters.getErfName());
            writeParam(writer, "erfDuration", String.valueOf(hazardParameters.getErfDuration()));
            writeParam(writer, "hazardMetric", hazardParameters.getHazardMetric().name());
            writeParam(writer, "gmmName",     hazardParameters.getGmmName());
            if (filterConfig != null) {
                for (Map.Entry<String, String> e : filterConfig.entrySet()) {
                    writeParam(writer, e.getKey(), e.getValue());
                }
            }
            writer.newLine();
            writeParam(writer, "totalExpectedLoss", String.valueOf(collection.getTotalExpectedLoss()));
            writer.newLine();

            writer.write("sourceId,ruptureId,likelihood,ruptureLoss,lossContribution,relativeContribution");
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
                + "," + r.key().ruptureId()
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