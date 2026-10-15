package scratch.anne.risk_system_vb.examples.logic_tree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scratch.anne.risk_system_vb.domain.hazard.LossExceedanceCurve;
import scratch.anne.risk_system_vb.domain.hazard.RuptureKey;
import scratch.anne.risk_system_vb.engine.logic_tree.StreamingBranchLecBuilder;
import scratch.anne.risk_system_vb.io.readers.RuptureLossLookupReader;

/**
 * One-off diagnostic: exports the FULL discrete-rank curve (every
 * distinct rupture loss, not just the summary probLevels) for a small
 * set of specific branches, so you can check them directly against the
 * ~27-point summary curve / EAL ratio computation in Python.
 */
public class FullRuptureCurveExportExample {

    public static void main(String[] args) throws Exception {
        
        Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\BERM\\results\\EAL\\true_mean_solution");
        Path inputFolder = Path.of("RES1-W1-MC_Porter-vuln_vs-360");
        String fileTag = inputFolder.getFileName().toString();

		int maxBranches = 10;
//		int maxBranches = Integer.MAX_VALUE;
		String fileSuffix = maxBranches != Integer.MAX_VALUE ? "_subset-n-" + maxBranches : "";

        Path exportedCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_eal-per-rup.csv");
        Path contributionsParquet = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-rupture-contributions" + fileSuffix + ".parquet");
        
        Path outputCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-full-lecs" + fileSuffix + ".csv");

        Map<RuptureKey, Double> fixedLossLookup = RuptureLossLookupReader.read(exportedCsv);

        // pick a few branches to check -- e.g. the first few, or specific indices
        Set<Integer> branchesToExport = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        exportFullCurves(contributionsParquet, fixedLossLookup, branchesToExport,
	                outputCsv);
	    }
    
    
    private static void exportFullCurves(Path contributionsParquet, Map<RuptureKey, Double> fixedLossLookup,
        Set<Integer> targetBranches, Path outputCsv) throws SQLException, java.io.IOException {

		System.out.println("Extracting full curves for branches: " + targetBranches);
		
		// gather each requested branch's curve fully in memory first --
		// fine for a handful of branches, unlike a full-scale export
		Map<Integer, LossExceedanceCurve> curvesByBranch = new java.util.HashMap<>();
		StreamingBranchLecBuilder.streamDiscreteRankCurves(contributionsParquet, fixedLossLookup, (branchIndex, curve) -> {
			if (targetBranches.contains(branchIndex))
				curvesByBranch.put(branchIndex, curve);
		});
		
		// union of every distinct loss value across all requested branches
		java.util.TreeSet<Double> allLosses = new java.util.TreeSet<>();
		for (LossExceedanceCurve curve : curvesByBranch.values())
			for (int i = 0; i < curve.size(); i++)
				allLosses.add(curve.lossAt(i));
		
		double[] lossAxis = allLosses.stream().mapToDouble(Double::doubleValue).toArray();
		
		// sorted branch indices, for stable column order
		List<Integer> branchOrder = new ArrayList<>(curvesByBranch.keySet());
		Collections.sort(branchOrder);
		
		try (var w = Files.newBufferedWriter(outputCsv)) {
			StringBuilder header = new StringBuilder("loss");
			for (int b : branchOrder)
				header.append(",branch_").append(b);
			w.write(header.toString());
			w.newLine();
			
			for (double loss : lossAxis) {
				StringBuilder row = new StringBuilder(String.valueOf(loss));
				for (int b : branchOrder) {
					LossExceedanceCurve curve = curvesByBranch.get(b);
					row.append(',').append(cumulativeRateAtOrBelow(curve, loss));
				}
				w.write(row.toString());
				w.newLine();
			}
		}
		
		System.out.println("Wrote curves (" + lossAxis.length + " loss rows, "
		+ branchOrder.size() + " branches) to " + outputCsv);
	}
	
	/**
	* Cumulative rate for this curve at the given loss, treating losses this
	* curve doesn't have a rupture at as carrying forward the cumulative
	* rate from the nearest loss value <= the requested loss (0 if below
	* this curve's smallest loss) -- the correct value of the step function
	* at any point, not an approximation.
	*/
	private static double cumulativeRateAtOrBelow(LossExceedanceCurve curve, double targetLoss) {
	int lo = 0, hi = curve.size() - 1, result = -1;
	while (lo <= hi) {
	int mid = (lo + hi) / 2;
	if (curve.lossAt(mid) <= targetLoss) {
	result = mid;
	lo = mid + 1;
	} else {
	hi = mid - 1;
	}
	}
	return result < 0 ? 0d : curve.cumulativeLikelihoodAt(result);
	}    
    
//    private static void exportFullCurves(Path contributionsParquet, Map<RuptureKey, Double> fixedLossLookup,
//        Set<Integer> targetBranches, Path outputCsv) throws SQLException, java.io.IOException {
//
//		System.out.println("Extracting full curves for branches: " + targetBranches);
//		
//		try (var w = java.nio.file.Files.newBufferedWriter(outputCsv)) {
//			w.write("branchIndex,rank,loss,cumulativeRate,sourceId,ruptureId");
//			w.newLine();
//			
//			StreamingBranchLecBuilder.streamDiscreteRankCurves(contributionsParquet, fixedLossLookup, (branchIndex, curve) -> {
//			if (!targetBranches.contains(branchIndex))
//			return;
//		
//		try {
//			for (int i = 0; i < curve.size(); i++) {
//				RuptureKey key = curve.keyAt(i);
//				w.write(branchIndex + "," + i + "," + curve.lossAt(i) + "," + curve.cumulativeLikelihoodAt(i) + ","
//				  + (key == null ? "" : key.sourceId()) + "," + (key == null ? "" : key.ruptureId()));
//				w.newLine();
//			}
//			} catch (java.io.IOException e) {
//				throw new java.io.UncheckedIOException(e);
//				}
//			});
//		}
//		
//		System.out.println("Wrote full curves to " + outputCsv);
//		}
}