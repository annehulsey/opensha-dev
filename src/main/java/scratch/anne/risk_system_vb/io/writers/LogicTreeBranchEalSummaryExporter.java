package scratch.anne.risk_system_vb.io.writers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;

/**
 * Writes the per-branch summary CSV: one row per branch with branchIndex,
 * branchWeight, eal, and per-category mapped-rate sanity-check totals,
 * plus one column per logic-tree branch level (fault model, deformation
 * model, etc.) so branches can be filtered/grouped by any single choice.
 *
 * <p>Deliberately branch-metadata + scalar-EAL only -- no LEC/exceedance
 * data of any kind belongs in this file. Anything requiring the full
 * per-rupture (loss, rate) contributions is a separate, later, replayable
 * step over the raw contributions Parquet, not something this exporter
 * should ever grow columns for.</p>
 */
public final class LogicTreeBranchEalSummaryExporter {

    /** One row's worth of data; caller (the branch loop) builds these. */
    public record BranchEalRow(
            int branchIndex,
            double branchWeight,
            double eal,
            double totalFssRate,
            double totalGriddedRate,
            LogicTreeBranch<?> branch
    ) {}

    private LogicTreeBranchEalSummaryExporter() {}

    /**
     * @param rows        one per branch, in the order to be written
     * @param logicTree   used only to read level headers (via branch 0's
     *                    levels) -- same level set is shared by every
     *                    branch drawn from a given SolutionLogicTree
     * @param outputCsv   destination file
     */
    public static void write(List<BranchEalRow> rows, LogicTree<?> logicTree, Path outputCsv) throws IOException {

        if (rows.isEmpty())
            throw new IllegalArgumentException("No rows to write");

        LogicTreeBranch<?> branch0 = logicTree.getBranch(0);
        int numLevels = branch0.size();
        String[] levelHeaders = new String[numLevels];
        for (int i = 0; i < numLevels; i++)
            levelHeaders[i] = branch0.getLevel(i).getShortName();

        try (BufferedWriter w = Files.newBufferedWriter(outputCsv)) {
            StringBuilder header = new StringBuilder("branchIndex,branchWeight,eal,totalFssRate,totalGriddedRate");
            for (String levelHeader : levelHeaders)
                header.append(',').append(levelHeader);
            w.write(header.toString());
            w.newLine();

            for (BranchEalRow row : rows) {
                StringBuilder line = new StringBuilder();
                line.append(row.branchIndex()).append(',')
                    .append(row.branchWeight()).append(',')
                    .append(row.eal()).append(',')
                    .append(row.totalFssRate()).append(',')
                    .append(row.totalGriddedRate());
                for (int i = 0; i < numLevels; i++) {
                    var value = row.branch().getValue(i);
                    line.append(',').append(value == null ? "" : value.getFilePrefix());
                }
                w.write(line.toString());
                w.newLine();
            }
        }
    }
}