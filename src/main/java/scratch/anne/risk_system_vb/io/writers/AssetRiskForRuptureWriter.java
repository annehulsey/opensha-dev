package scratch.anne.risk_system_vb.io.writers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.OutputFile;

public final class AssetRiskForRuptureWriter implements AutoCloseable {

    /** Fraction of site&IM asset value — write if loss exceeds this proportion of local exposure */
    public static final double DEFAULT_F_RELATIVE = 0.01;

    /** Fraction of total portfolio value — write if loss exceeds this proportion of portfolio */
    public static final double DEFAULT_F_ABSOLUTE = 1e-7;

    private final double fRelative;
    private final double fAbsolute;

    private final Schema schema = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "RuptureAssetLoss",
              "fields": [
                {"name": "assetId",         "type": "int"},
                {"name": "sourceId",        "type": "int"},
                {"name": "ruptureId",       "type": "int"},
                {"name": "conditionalRisk", "type": "double"}
              ]
            }
            """);

    private final ConcurrentHashMap<Long, ParquetWriter<GenericRecord>> threadWriters =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, GenericRecord> threadRecords =
            new ConcurrentHashMap<>();

    private final Path parquetDir;

    private final AtomicInteger threadCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, Integer> threadIndex = new ConcurrentHashMap<>();

    /** Full constructor */
    public AssetRiskForRuptureWriter(Path outputDir, String fileTag,
                                     double fRelative, double fAbsolute) throws IOException {
        this.fRelative = fRelative;
        this.fAbsolute = fAbsolute;
        this.parquetDir = outputDir.resolve(fileTag);

        if (Files.exists(parquetDir)) {
            try (var stream = Files.walk(parquetDir)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> {
                          try { Files.delete(p); }
                          catch (IOException e) {
                              System.out.println("Warning: could not delete " + p);
                          }
                      });
            }
            try (var remaining = Files.list(parquetDir)) {
                List<Path> leftover = remaining.toList();
                if (!leftover.isEmpty()) {
                    throw new IOException(
                        "Output directory could not be cleared, leftover files may corrupt results: "
                        + parquetDir + "\nLeftover: " + leftover);
                }
            }
        }
        Files.createDirectories(parquetDir);
    }

    /** Default thresholds */
    public AssetRiskForRuptureWriter(Path outputDir, String fileTag) throws IOException {
        this(outputDir, fileTag, DEFAULT_F_RELATIVE, DEFAULT_F_ABSOLUTE);
    }

    /**
     * Returns the write threshold for a given site&IM loss context.
     * Write if siteImLoss exceeds this value.
     */
    public double writeThresholdValue(double siteImAssetValue, double totalPortfolioValue) {
        return Math.min(fRelative * siteImAssetValue, fAbsolute * totalPortfolioValue);
    }

    public double getFRelative() { return fRelative; }
    public double getFAbsolute() { return fAbsolute; }

    private ParquetWriter<GenericRecord> writerForThread() {
        long tid = Thread.currentThread().threadId();
        int idx = threadIndex.computeIfAbsent(tid, id -> threadCounter.getAndIncrement());
        return threadWriters.computeIfAbsent(tid, id -> {
            try {
                Path file = parquetDir.resolve(String.format("part-%05d.parquet", idx));
                OutputFile out = new LocalOutputFile(file.toAbsolutePath().normalize());
                return AvroParquetWriter.<GenericRecord>builder(out)
                        .withSchema(schema)
                        .withCompressionCodec(CompressionCodecName.SNAPPY)
                        .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                        .build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private GenericRecord recordForThread() {
        long tid = Thread.currentThread().threadId();
        return threadRecords.computeIfAbsent(tid, id -> new GenericData.Record(schema));
    }

    public void write(int assetId, int sourceId, int ruptureId, double risk) {
        try {
            GenericRecord record = recordForThread();
            record.put("assetId",         assetId);
            record.put("sourceId",        sourceId);
            record.put("ruptureId",       ruptureId);
            record.put("conditionalRisk", risk);
            writerForThread().write(record);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing Parquet record", e);
        }
    }

    @Override
    public void close() {
        try {
            for (ParquetWriter<GenericRecord> w : threadWriters.values())
                w.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to close asset writer", e);
        }
    }
}