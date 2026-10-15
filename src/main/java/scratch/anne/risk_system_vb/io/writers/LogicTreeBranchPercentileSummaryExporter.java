package scratch.anne.risk_system_vb.io.writers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import scratch.anne.risk_system_vb.util.StringUtil;
import scratch.anne.risk_system_vb.engine.logic_tree.BranchLecDistributionBuilder.LevelDistribution;

/**
 * Writes the ensemble-level percentile summary CSV: one row per metric
 * (EAL, plus one row per requested likelihood level), each with its own
 * weighted mean and weighted percentiles across all branches.
 *
 * <p>Pure writer -- no file reading, no Parquet/streaming knowledge, no
 * percentile math. Takes already-built LevelDistributions (from
 * BranchLecDistributionBuilder) and formats them. Same role as
 * BranchEalSummaryExporter / BranchContributionExporter: a small,
 * focused output class with no upstream responsibilities.</p>
 */
public final class LogicTreeBranchPercentileSummaryExporter {

    private LogicTreeBranchPercentileSummaryExporter() {}

    /**
     * @param ealDistribution        the EAL metric's own LevelDistribution, written as the first row
     * @param levelDistributions     one LevelDistribution per requested likelihood level, in the order to write
     * @param percentilesOfInterest  same array used to build the distributions -- controls column order
     */
    public static void write(LevelDistribution ealDistribution,
                              Map<Double, LevelDistribution> levelDistributions,
                              double[] percentilesOfInterest,
                              Path outputCsv) throws IOException {

        System.out.println("Writing ensemble percentile summary to: " + outputCsv);

        try (BufferedWriter w = Files.newBufferedWriter(outputCsv)) {
            StringBuilder header = new StringBuilder("likelihood,mean");
            for (double p : percentilesOfInterest)
                header.append(",p").append(StringUtil.formatPercentile(p));
            header.append(",missingCount,missingFraction");
            w.write(header.toString());
            w.newLine();

            writeRow("eal", ealDistribution, percentilesOfInterest, w);

            for (LevelDistribution dist : levelDistributions.values())
                writeRow(StringUtil.formatLikelihood(dist.requestedLevel()), dist, percentilesOfInterest, w);
        }
    }

    private static void writeRow(String label, LevelDistribution dist, double[] percentilesOfInterest,
                                  BufferedWriter w) throws IOException {
        StringBuilder line = new StringBuilder(label).append(',').append(dist.mean());
        for (double p : percentilesOfInterest)
            line.append(',').append(dist.percentiles().get(p));
        line.append(',').append(dist.missingCount()).append(',').append(dist.missingFraction());
        w.write(line.toString());
        w.newLine();
    }
}