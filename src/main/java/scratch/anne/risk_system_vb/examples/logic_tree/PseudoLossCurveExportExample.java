package scratch.anne.risk_system_vb.examples.logic_tree;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import scratch.anne.risk_system_vb.domain.hazard.LossExceedanceCurve;
import scratch.anne.risk_system_vb.domain.hazard.RuptureKey;
import scratch.anne.risk_system_vb.engine.logic_tree.StreamingBranchLecBuilder;
import scratch.anne.risk_system_vb.io.readers.RuptureLossLookupReader;
import scratch.anne.risk_system_vb.io.readers.RuptureLossLookupReader.RuptureAttributes;
import scratch.anne.risk_system_vb.io.writers.PseudoLossCurveExporter;

/**
 * One-off diagnostic: exports the FULL discrete-rank curve (every
 * distinct rupture loss, not just the summary probLevels) for a small
 * set of specific branches, so you can check them directly against the
 * ~27-point summary curve / EAL ratio computation in Python.
 */
public class PseudoLossCurveExportExample {

    public static void main(String[] args) throws Exception {
        
        Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\BERM\\results\\EAL\\true_mean_solution");
        Path inputFolder = Path.of("RES1-W1-MC_Porter-vuln_vs-360");
        String fileTag = inputFolder.getFileName().toString();

		int maxBranches = 10;
//		int maxBranches = Integer.MAX_VALUE;
		String fileSuffix = maxBranches != Integer.MAX_VALUE ? "_subset-n-" + maxBranches : "";

        Path inputCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_eal-per-rup.csv");
        Path contributionsParquet = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-rupture-contributions" + fileSuffix + ".parquet");
        
        Path outputCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-pseudo-loss-curve" + fileSuffix + ".csv");

        // long-format output, one row per (branch, rank), with the
        // specific sourceId/ruptureId/fltSysRupIndex/magnitude
        // responsible for each rank's loss threshold -- see
        // FullRuptureCurveExporter. Parquet only (not human-readable);
        // the wide CSV below carries the same per-rupture attributes in
        // a human-readable form instead.
        Path outputParquet = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-pseudo-loss-curve" + fileSuffix + ".parquet");

        Map<RuptureKey, Double> fixedLossLookup = RuptureLossLookupReader.read(inputCsv);
        Map<RuptureKey, RuptureAttributes> ruptureAttributes = RuptureLossLookupReader.readRuptureAttributes(inputCsv);

        // pick a few branches to check -- e.g. the first few, or specific indices.
        // This subset only ever limits the wide CSV output below; the
        // Parquet output always covers every branch in
        // contributionsParquet, regardless of this set.
        Set<Integer> branchesToExport = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        exportPseudoLossCurves(contributionsParquet, fixedLossLookup, ruptureAttributes, branchesToExport,
	                outputCsv, outputParquet);
	    }
    
    
    private static void exportPseudoLossCurves(Path contributionsParquet, Map<RuptureKey, Double> fixedLossLookup,
        Map<RuptureKey, RuptureAttributes> ruptureAttributes, Set<Integer> targetBranches, Path outputCsv, Path outputParquet)
        throws SQLException, java.io.IOException {

		System.out.println("Extracting full curves for branches: " + targetBranches
				+ " (CSV only -- Parquet output always covers every branch)");
		
		// gather each requested branch's curve fully in memory -- fine
		// for a handful of branches (the wide CSV output needs the full
		// set at once to compute its shared union loss axis), unlike a
		// full-scale export. The Parquet output is written per branch
		// as curves stream past, below, and is never collected here, so
		// it stays flat regardless of total branch count.
		Map<Integer, LossExceedanceCurve> curvesByBranch = new java.util.HashMap<>();

		try (PseudoLossCurveExporter exporter = new PseudoLossCurveExporter(outputParquet)) {
			StreamingBranchLecBuilder.streamDiscreteRankCurves(contributionsParquet, fixedLossLookup, (branchIndex, curve) -> {
				// always written, for every branch, regardless of targetBranches
				exporter.writeParquet(branchIndex, curve, ruptureAttributes);

				if (targetBranches.contains(branchIndex))
					curvesByBranch.put(branchIndex, curve);
			});

			// wide/pivoted CSV, limited to targetBranches -- carries the
			// same cumulative-likelihood information as the Parquet
			// output for these branches, human-readable, with
			// sourceId/ruptureId/fltSysRupIndex/magnitude attributed per
			// row (see FullRuptureCurveExporter)
			exporter.writeCsv(curvesByBranch, ruptureAttributes, outputCsv);
		}

		System.out.println("Wrote curves CSV (branches " + targetBranches + ") to " + outputCsv);
		System.out.println("Wrote per-rupture curve keys Parquet (all branches) to " + outputParquet);
	}
    
}