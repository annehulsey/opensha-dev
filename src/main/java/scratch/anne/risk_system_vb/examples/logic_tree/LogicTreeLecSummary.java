package scratch.anne.risk_system_vb.examples.logic_tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.sql.SQLException;

import scratch.anne.risk_system_vb.domain.hazard.LossExceedanceCurve;
import scratch.anne.risk_system_vb.domain.hazard.RuptureKey;
import scratch.anne.risk_system_vb.engine.logic_tree.BranchLecDistributionBuilder;
import scratch.anne.risk_system_vb.engine.logic_tree.StreamingBranchLecBuilder;
import scratch.anne.risk_system_vb.io.readers.RuptureLossLookupReader;
import scratch.anne.risk_system_vb.io.writers.LogicTreeBranchPercentileSummaryExporter;
import scratch.anne.risk_system_vb.io.writers.LogicTreeBranchLecExporter;
import scratch.anne.risk_system_vb.util.StandardRateLikelihoods;

/**
 * Orchestration only: reads the existing branch-eals.csv and
 * branch-contributions.parquet, streams every branch's discrete-rank
 * LEC in one pass, and writes two outputs:
 *
 * <ul>
 *   <li>a per-branch CSV: the original summary + one loss column per
 *       requested exceedance-rate level</li>
 *   <li>an ensemble-level percentile summary CSV (via
 *       BranchLecPercentileSummaryExporter): one row for EAL, one row
 *       per likelihood level, each with its own weighted mean and
 *       weighted percentiles across branches</li>
 * </ul>
 *
 * <p>All percentile/mean math lives in BranchLecDistributionBuilder;
 * all CSV-writing for the ensemble summary lives in
 * BranchLecPercentileSummaryExporter. This class only gathers data and
 * calls them.</p>
 */
public class LogicTreeLecSummary {

    public static void main(String[] args) throws IOException, SQLException {

        Path baseFolder = Path.of("C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\BERM\\results\\EAL\\true_mean_solution");
        Path inputFolder = Path.of("RES1-W1-MC_Porter-vuln_vs-360");
        String fileTag = inputFolder.getFileName().toString();

//		int maxBranches = 10;
		int maxBranches = Integer.MAX_VALUE;
		String fileSuffix = maxBranches != Integer.MAX_VALUE ? "_subset-n-" + maxBranches : "";

        Path exportedCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_eal-per-rup.csv");
        Path branchEALsCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-eals" + fileSuffix + ".csv");
        Path contributionsParquet = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-rupture-contributions" + fileSuffix + ".parquet");
        Path branchSummaryCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-eals-with-lec" + fileSuffix + ".csv");
        Path branchPercentilesCsv = baseFolder.resolve(inputFolder).resolve(fileTag + "_branch-percentiles" + fileSuffix + ".csv");

        // fixed loss lookup: same one built for the original branch loop,
        // read fresh here since this script runs independently of that loop
        System.out.println("Loading fixed loss lookup from: " + exportedCsv);
        Map<RuptureKey, Double> fixedLossLookup = RuptureLossLookupReader.read(exportedCsv);
        System.out.println("Loaded fixed loss lookup for " + fixedLossLookup.size() + " ruptures");

        // exceedance-rate levels to evaluate each branch's curve at --
        // change this and rerun any time, no need to reprocess branches        
        double[] rates = StandardRateLikelihoods.get();

        // weighted percentiles to report across the branch ensemble, per level
        double[] percentilesOfInterest = { 1, 2.5, 5, 10, 16, 50, 84, 90, 95, 97.5, 99 };

        // branch weights, needed for the percentile pass
        Map<Integer, Double> weightByBranch = readBranchWeights(branchEALsCsv);
        List<BranchLecDistributionBuilder.BranchLevelSample> ealSamples = readEalSamples(branchEALsCsv);

        // ------------------------------------------------------------
        // SINGLE streaming pass over the Parquet file, feeding both
        // the per-branch output and the ensemble percentile output
        // ------------------------------------------------------------
        Map<Integer, double[]> lossByBranchAndLevel = new HashMap<>();

        Map<Double, List<BranchLecDistributionBuilder.BranchLevelSample>> samplesByLikelihood = new LinkedHashMap<>();
        for (double level : rates)
            samplesByLikelihood.put(level, new ArrayList<>());

        System.out.println("Streaming branch contributions from: " + contributionsParquet);
        StreamingBranchLecBuilder.streamDiscreteRankCurves(contributionsParquet, fixedLossLookup, (branchIndex, curve) -> {
            List<LossExceedanceCurve.LossAtLikelihood> results = curve.sampleAtLikelihoods(rates);

            Double weight = weightByBranch.get(branchIndex);
            if (weight == null)
                throw new IllegalStateException("No branch weight found for branchIndex " + branchIndex);

            double[] losses = new double[rates.length];
            for (int i = 0; i < results.size(); i++) {
                double loss = results.get(i).loss();
                losses[i] = loss;
                samplesByLikelihood.get(rates[i]).add(
                        new BranchLecDistributionBuilder.BranchLevelSample(branchIndex, weight, loss));
            }
            lossByBranchAndLevel.put(branchIndex, losses);
        });
        System.out.println("Built LEC-derived losses for " + lossByBranchAndLevel.size() + " branches");
        
        
        // build the EAL distribution and every likelihood-level distribution
        // via the same metric-agnostic summarize/summarizeAll methods
        BranchLecDistributionBuilder.LevelDistribution ealDistribution =
                BranchLecDistributionBuilder.summarize(BranchLecDistributionBuilder.EAL_LEVEL, ealSamples, percentilesOfInterest);
        Map<Double, BranchLecDistributionBuilder.LevelDistribution> levelDistributions =
                BranchLecDistributionBuilder.summarizeAll(samplesByLikelihood, percentilesOfInterest);


        // ------------------------------------------------------------
        // Write output 1: per-branch CSV
        // ------------------------------------------------------------
        LogicTreeBranchLecExporter.write(branchEALsCsv, rates, lossByBranchAndLevel,
                branchSummaryCsv);

        // ------------------------------------------------------------
        // Write output 2: ensemble percentile summary, built from the
        // same samplesByLevel collected during the single pass above --
        // no second Parquet read
        // ------------------------------------------------------------
        LogicTreeBranchPercentileSummaryExporter.write(ealDistribution, levelDistributions, percentilesOfInterest,
        		branchPercentilesCsv);


        System.out.println("DONE");
    }


    private static Map<Integer, Double> readBranchWeights(Path branchEALsCsv) throws IOException {
        List<String> lines = Files.readAllLines(branchEALsCsv);
        String[] header = lines.get(0).split(",");
        int weightCol = List.of(header).indexOf("branchWeight");
        if (weightCol < 0)
            throw new IllegalStateException("Could not find 'branchWeight' column in header: " + lines.get(0));

        Map<Integer, Double> weights = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] tokens = lines.get(i).split(",");
            weights.put(Integer.parseInt(tokens[0].trim()), Double.parseDouble(tokens[weightCol].trim()));
        }
        return weights;
    }
    
    private static List<BranchLecDistributionBuilder.BranchLevelSample> readEalSamples(Path branchEALsCsv) throws IOException {
        List<String> lines = Files.readAllLines(branchEALsCsv);
        String[] header = lines.get(0).split(",");
        int weightCol = List.of(header).indexOf("branchWeight");
        int ealCol = List.of(header).indexOf("eal");
        if (weightCol < 0 || ealCol < 0)
            throw new IllegalStateException("Could not find 'branchWeight'/'eal' column in header: " + lines.get(0));

        List<BranchLecDistributionBuilder.BranchLevelSample> samples = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] tokens = lines.get(i).split(",");
            int branchIndex = Integer.parseInt(tokens[0].trim());
            double weight = Double.parseDouble(tokens[weightCol].trim());
            double eal = Double.parseDouble(tokens[ealCol].trim());
            samples.add(new BranchLecDistributionBuilder.BranchLevelSample(branchIndex, weight, eal));
        }
        return samples;
    }
}