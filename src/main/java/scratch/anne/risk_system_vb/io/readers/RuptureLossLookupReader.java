package scratch.anne.risk_system_vb.io.readers;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import scratch.anne.risk_system_vb.domain.hazard.RuptureKey;

/**
 * Reads the per-rupture data block of a RuptureResultsExporter CSV
 * directly into a fixed {@code Map<RuptureKey, Double>} of ruptureLoss
 * values, WITHOUT reconstructing a full RuptureLossResultsCollection.
 *
 * <p>Per confirmed requirements: only ruptureLoss is needed from this
 * file. likelihood will be replaced by each branch's own rate in the
 * branch loop, and lossContribution/relativeContribution are derived
 * from likelihood*ruptureLoss -- neither is meaningful input once
 * likelihood is being swapped out. This reader intentionally ignores
 * those three columns rather than reconstructing a RuptureResult that
 * would need them.</p>
 *
 * <p>The output map plugs directly into {@link BranchEALCalculator}'s
 * constructor, in place of {@link BranchEALCalculator#buildFixedLossLookup}
 * (which builds the same shape from an in-memory, already-frozen
 * RuptureLossResultsCollection -- this is the equivalent for the
 * already-exported-to-disk case).</p>
 */
public final class RuptureLossLookupReader {

    private RuptureLossLookupReader() {}

    /**
     * Parses sourceId, ruptureId, ruptureLoss from the exported CSV's data
     * rows (skipping the "# HazardParameters" metadata block, the blank
     * lines, and the totalExpectedLoss line, exactly matching
     * RuptureResultsExporter.exportCsv's own layout).
     *
     * @throws IllegalArgumentException if the expected data header row
     *         ("sourceId,ruptureId,...") is never found
     */
    public static Map<RuptureKey, Double> read(Path exportedCsv) throws IOException {

        Map<RuptureKey, Double> lookup = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(exportedCsv)) {

            String line;
            boolean foundDataHeader = false;

            // Skip everything up through the data header row.
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("sourceId")) {
                    foundDataHeader = true;
                    break;
                }
            }

            if (!foundDataHeader) {
                throw new IllegalArgumentException(
                        "Could not find 'sourceId,ruptureId,...' data header row in: " + exportedCsv);
            }

            // Remaining lines are data rows:
            // sourceId,ruptureId,likelihood,ruptureLoss,lossContribution,relativeContribution
            while ((line = br.readLine()) != null) {
                if (line.isBlank())
                    continue;

                String[] tokens = line.split(",");
                if (tokens.length < 4) {
                    throw new IllegalArgumentException(
                            "Malformed data row (expected at least 4 columns): '" + line + "' in " + exportedCsv);
                }

                int sourceId = Integer.parseInt(tokens[0].trim());
                int ruptureId = Integer.parseInt(tokens[1].trim());
                double ruptureLoss = Double.parseDouble(tokens[3].trim()); // index 3 = ruptureLoss

                lookup.put(new RuptureKey(sourceId, ruptureId), ruptureLoss);
            }
        }

        return lookup;
    }
}