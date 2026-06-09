package scratch.anne.risk_system_vb.io.writers;

import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.engine.accumulators.RuptureResultsCollection;
import scratch.anne.risk_system_vb.engine.accumulators.RuptureResultsCollection.RuptureResult;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RuptureResultsExporter {

    

    // ------------------------------------------------------------
    // PUBLIC ENTRY
    // ------------------------------------------------------------

    public void export(RuptureResultsCollection collection,
                       Path file,
                       FileFormat format) {

        switch (format) {
            case CSV -> exportCsv(collection, file);
//            case PARQUET -> exportParquet(collection, file);
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        }
    }

    // ------------------------------------------------------------
    // CSV EXPORT
    // ------------------------------------------------------------

    private void exportCsv(RuptureResultsCollection collection, Path file) {

        HazardParameters hazardParameters = collection.getHazardParameters();
        
        Map<String, String> filterConfig = hazardParameters.getFilterConfig();

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {

            // ---------------- HEADER ----------------
            writer.write("# HazardParameters");
            writer.newLine();
            
            writeParam(writer, "erfName", hazardParameters.getErfName());
            writeParam(writer, "erfDuration", String.valueOf(hazardParameters.getErfDuration()));
            writeParam(writer, "hazardMetric", hazardParameters.getHazardMetric().name());
            writeParam(writer, "gmmName", hazardParameters.getGmmName());

            if (filterConfig != null) {
                for (Map.Entry<String, String> e : filterConfig.entrySet()) {
                    writeParam(writer, e.getKey(), e.getValue());
                }
            }

            writer.newLine(); // separator
            
            writeParam(writer, "totalLoss", String.valueOf(collection.getTotalLoss()));
            
            writer.newLine(); // separator

            // ---------------- TABLE HEADER ----------------
            writer.write("sourceId,ruptureId,likelihood,ruptureLoss,lossContribution,relativeContribution");
            writer.newLine();

            // ---------------- DATA ----------------
            for (RuptureResult r : collection.results()) {
                writer.write(formatCsvRow(r));
                writer.newLine();
            };

        } catch (IOException e) {
            throw new RuntimeException("CSV export failed", e);
        }
    }

    private String formatCsvRow(RuptureResult result) {

        return result.key().sourceId()
                + ","
                + result.key().ruptureId()
                + ","
                + result.likelihood()
                + ","
                + result.ruptureLoss()
        		+ ","
        		+ result.lossContribution()
				+ ","
				+ result.relativeContribution();
    }
    
    private void writeParam(BufferedWriter writer, String param, String value) {
        try {
            writer.write(param + "," + value);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    // ------------------------------------------------------------
//    // PARQUET EXPORT
//    // ------------------------------------------------------------
//    private void exportParquet(
//            RuptureResultsCollection collection,
//            Path file) {
//
//        HazardParameters p = collection.getHazardParameters();
//
//        try (RuptureParquetWriter writer =
//                     new RuptureParquetWriter(
//                             file,
//                             Map.of(
//                                 "erfName", p.getErfName(),
//                                 "erfDurationYears", String.valueOf(p.getErfDurationYears()),
//                                 "hazardMetric", p.getHazardMetric().name(),
//                                 "gmmName", p.getGmmName()
//                             )
//                     )) {
//
//            for (RuptureResult r : collection.results()) {
//
//                writer.write(
//                        r.key().sourceId(),
//                        r.key().ruptureId(),
//                        r.likelihood(),
//                        r.ruptureLoss(),
//                        r.lossContribution()
//                );
//            }
//
//        } catch (Exception e) {
//            throw new RuntimeException("Parquet export failed", e);
//        }
//    }
//    
//    public class RuptureParquetWriter implements Closeable {
//
//        private static final Schema SCHEMA =
//                new Schema.Parser().parse("""
//                {
//                  "type":"record",
//                  "name":"RuptureResult",
//                  "fields":[
//                    {"name":"sourceId","type":"int"},
//                    {"name":"ruptureId","type":"int"},
//                    {"name":"likelihood","type":"double"},
//                    {"name":"ruptureLoss","type":"double"},
//                    {"name":"lossContribution","type":"double"}
//                  ]
//                }
//                """);
//
//        private final ParquetWriter<GenericRecord> writer;
//
//        public RuptureParquetWriter(
//                Path file,
//                Map<String,String> metadata
//        ) throws IOException {
//
//            Configuration conf = new Configuration();
//
//            // attach hazard parameters as parquet metadata
//            metadata.forEach(conf::set);
//
//            writer = AvroParquetWriter.<GenericRecord>builder(
//                        new HadoopPath(file.toUri()))
//                    .withSchema(SCHEMA)
//                    .withConf(conf)
//                    .build();
//        }
//
//        public void write(
//                int sourceId,
//                int ruptureId,
//                double likelihood,
//                double totalLoss
//        ) throws IOException {
//
//            GenericRecord record =
//                    new GenericData.Record(SCHEMA);
//
//            record.put("sourceId", sourceId);
//            record.put("ruptureId", ruptureId);
//            record.put("likelihood", likelihood);
//            record.put("totalLoss", totalLoss);
//
//            writer.write(record);
//        }
//
//        @Override
//        public void close() throws IOException {
//            writer.close();
//        }
//    }
    
    
}
