package scratch.anne.risk_system_vb.io.writers;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.IOException;

public final class AssetRiskForRuptureWriter implements AutoCloseable {

    private final ParquetWriter<GenericRecord> writer;
    
    private final Schema schema = new Schema.Parser().parse("""
							    {
							      "type": "record",
							      "name": "RuptureAssetLoss",
							      "fields": [
							        {"name": "assetId", "type": "string"},
							        {"name": "sourceId", "type": "int"},
							        {"name": "ruptureId", "type": "int"},
							        {"name": "conditionalRisk", "type": "double"}
							      ]
							    }
							    """);
    
    private final GenericRecord reusableRecord = new GenericData.Record(schema);

    public AssetRiskForRuptureWriter(java.nio.file.Path file) throws IOException {

        org.apache.parquet.io.OutputFile outputFile =
                new org.apache.parquet.io.LocalOutputFile(file.toAbsolutePath().normalize());

        this.writer =
                AvroParquetWriter.<GenericRecord>builder(outputFile)
                        .withSchema(schema)
                        .withCompressionCodec(CompressionCodecName.SNAPPY)
                        .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                        .build();
    }

    public synchronized void write(String assetId, int sourceId, int ruptureId, double risk) {
        try {
            reusableRecord.put("assetId", assetId);
            reusableRecord.put("sourceId", sourceId);
            reusableRecord.put("ruptureId", ruptureId);
            reusableRecord.put("conditionalRisk", risk);

            writer.write(reusableRecord);

        } catch (IOException e) {
            throw new RuntimeException("Failed writing Parquet record", e);
        }
    }

        @Override
        public void close() {
            try {  
                writer.close(); 
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }