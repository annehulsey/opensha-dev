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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Writes the raw per-branch, per-rupture (loss, rate) contribution file --
 * NOT an accumulated/ranked curve of any kind. One row per rupture that
 * had nonzero rate on a given branch, in whatever order the branch loop
 * naturally produces them (fault ruptures then gridded ruptures, per the
 * existing mapping loops).
 *
 * <p>This is the single frozen artifact that every downstream LEC
 * question -- discrete-rank curves, lognormal-COV smearing, fixed
 * exceedance-probability levels, or any future accumulation strategy --
 * replays against. None of that logic runs here; this class only writes
 * rows.</p>
 *
 * <p>Usage: open once per export run, call {@link #write} for every
 * nonzero-rate rupture contribution as the branch loop computes them,
 * close once at the very end (try-with-resources across the whole
 * branch loop, not per-branch).</p>
 */
public final class LogicTreeBranchRuptureContributionExporter implements AutoCloseable {

    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "BranchRuptureContribution",
              "namespace": "scratch.anne.risk_system_vb",
              "fields": [
                {"name": "branchIndex", "type": "int"},
                {"name": "sourceId",    "type": "int"},
                {"name": "ruptureId",   "type": "int"},
                {"name": "loss",        "type": "double"},
                {"name": "rate",        "type": "double"}
              ]
            }
            """);

    private final ParquetWriter<GenericRecord> writer;

    public LogicTreeBranchRuptureContributionExporter(Path file) throws IOException {
        OutputFile out = new LocalOutputFile(file.toAbsolutePath().normalize());
        this.writer = AvroParquetWriter
                .<GenericRecord>builder(out)
                .withSchema(SCHEMA)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                .build();
    }

    /**
     * Writes one rupture's contribution for one branch. Skips silently if
     * rate is zero (nothing for downstream accumulation to do with a
     * zero-rate row -- same "skip the lookup entirely" convention as
     * BranchEALCalculator.accumulate).
     */
    public void write(int branchIndex, int sourceId, int ruptureId, double loss, double rate) {
        if (rate == 0d)
            return;
        GenericRecord record = new GenericData.Record(SCHEMA);
        record.put("branchIndex", branchIndex);
        record.put("sourceId", sourceId);
        record.put("ruptureId", ruptureId);
        record.put("loss", loss);
        record.put("rate", rate);
        try {
            writer.write(record);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}