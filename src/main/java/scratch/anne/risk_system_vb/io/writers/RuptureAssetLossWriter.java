//import org.apache.avro.Schema;
//import org.apache.avro.generic.*;
//import org.apache.parquet.avro.AvroParquetWriter;
//import org.apache.parquet.hadoop.ParquetWriter;
//import org.apache.parquet.hadoop.metadata.CompressionCodecName;
//
//import java.io.IOException;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//
//public final class RuptureAssetLossWriter implements AutoCloseable {
//
//    private static final Schema SCHEMA = new Schema.Parser().parse("""
//    {
//      "type": "record",
//      "name": "RuptureAssetContribution",
//      "fields": [
//        {"name": "assetId", "type": "string"},
//        {"name": "sourceId", "type": "int"},
//        {"name": "ruptureId", "type": "int"},
//        {"name": "conditionalLoss", "type": "double"}
//      ]
//    }
//    """);
//
//    private static final int BUFFER_SIZE = 50_000;
//
//    private final Path basePath;
//
//    private final ThreadLocal<Sink> sinks = ThreadLocal.withInitial(() -> {
//        try {
//            return new Sink(basePathForThread());
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    });
//
//    public RuptureAssetLossWriter(Path basePath) {
//        this.basePath = basePath;
//    }
//
//    public void write(RuptureAssetContributionRow row) {
//        sinks.get().write(row);
//    }
//
//    // ------------------------------------------------------------
//    // THREAD LOCAL SINK
//    // ------------------------------------------------------------
//
//    private static final class Sink {
//
//        private final ParquetWriter<GenericRecord> writer;
//        private final List<GenericRecord> buffer = new ArrayList<>(BUFFER_SIZE);
//
//        Sink(Path file) throws IOException {
//            this.writer =
//                    AvroParquetWriter.<GenericRecord>builder(
//                            new org.apache.hadoop.fs.Path(file.toUri())
//                    )
//                    .withSchema(SCHEMA)
//                    .withCompressionCodec(CompressionCodecName.SNAPPY)
//                    .build();
//        }
//
//        void write(RuptureAssetContributionRow row) {
//            buffer.add(toRecord(row));
//
//            if (buffer.size() >= BUFFER_SIZE) {
//                flush();
//            }
//        }
//
//        void flush() {
//            try {
//                for (GenericRecord r : buffer) {
//                    writer.write(r);
//                }
//                buffer.clear();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        void close() {
//            flush();
//            try {
//                writer.close();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        private GenericRecord toRecord(RuptureAssetContributionRow row) {
//            GenericRecord r = new GenericData.Record(SCHEMA);
//            r.put("assetId", row.assetId());
//            r.put("sourceId", row.sourceId());
//            r.put("ruptureId", row.ruptureId());
//            r.put("conditionalLoss", row.conditionalLoss());
//            return r;
//        }
//    }
//
//    // ------------------------------------------------------------
//    // LIFECYCLE
//    // ------------------------------------------------------------
//
//    @Override
//    public void close() {
//        // flush/close all thread-local sinks that were created
//        // (ThreadLocal does not track all instances, so we must force cleanup pattern externally)
//        // In practice: call close() per thread OR manage externally (see note below)
//        sinks.get().close();
//    }
//}