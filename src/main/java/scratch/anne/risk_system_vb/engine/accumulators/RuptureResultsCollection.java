package scratch.anne.risk_system_vb.engine.accumulators;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;

import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters.HazardMetricType;

public final class RuptureResultsCollection {

    private final HazardParameters hazardParameters;

    private final Map<RuptureKey, Double> ruptureLikelihoods;
    
    private final RuptureLossAccumulator accumulator;
    
    private double totalExpectedLoss;
    private Map<RuptureKey, Double> lossContributions;
    private Map<RuptureKey, Double> relativeContributions;
    
    private boolean frozen;

    public RuptureResultsCollection(
            HazardParameters hazardParameters,
            Map<RuptureKey, Double> ruptureLikelihoods
    ) {
        this.hazardParameters = hazardParameters;
        
        if (hazardParameters.getHazardMetricType() != HazardMetricType.EXCEEDANCE) {
            throw new IllegalArgumentException(
                "RuptureResultsCollection requires EXCEEDANCE hazard metric type, but got: "
                + hazardParameters.getHazardMetricType()
            );
        }
        
        this.ruptureLikelihoods = ruptureLikelihoods;
        
        this.accumulator = new RuptureLossAccumulator(ruptureLikelihoods.keySet());
        
        this.totalExpectedLoss = 0.0;
        this.lossContributions = new LinkedHashMap<>();
        this.relativeContributions = new LinkedHashMap<>();
        
    }

        
    public void accumulateRuptureLoss(RuptureKey key, double loss) {
    	if (frozen)
            throw new IllegalStateException(
                "RuptureResultsCollection is frozen");
        accumulator.accumulateRuptureLoss(key, loss);
    }

    
    public double getLikelihood(RuptureKey key) {
        return ruptureLikelihoods.get(key);
    }

    
    public double getRuptureLoss(RuptureKey key) {
        return accumulator.getLoss(key);
    }
    
    
    public double getRuptureContribution(RuptureKey key) {

        if (!frozen)
            throw new IllegalStateException(
                "Rupture contributions not yet computed");

        return lossContributions.getOrDefault(key, 0.0);
    }
    
    
    public double getTotalExpectedLoss() {
    	if (!frozen)
            throw new IllegalStateException(
                "Total expected loss not yet computed");
    	return totalExpectedLoss;
    }
    
    public int getRuptureCount() {
        return ruptureLikelihoods.size();
    }


    public HazardParameters getHazardParameters() {
        return hazardParameters;
    }
    
    
    public RuptureResult getTopContributor() {

        if (!frozen)
            throw new IllegalStateException(
            		"Rupture contributions not yet computed");

        RuptureKey key = lossContributions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow()
                .getKey();

        return get(key);
    }
    
    public RuptureKey getTopContributorKey() {

        return getTopContributor().key();
    }
    
    
    public List<RuptureResult> getTopContributors(int n) {

        if (!frozen)
            throw new IllegalStateException(
            		"Rupture contributions not yet computed");

        return lossContributions.entrySet().stream()
                .sorted(Map.Entry.<RuptureKey, Double>comparingByValue()
                        .reversed())
                .limit(n)
                .map(e -> get(e.getKey()))
                .toList();
    }
    
    
    public List<RuptureKey> getTopContributorsKeys(int n) {
        return getTopContributors(n).stream()
                .map(RuptureResult::key)
                .toList();
    }

    
    /**
     * Immutable result for export (CSV / Parquet).
     */
    public record RuptureResult(
            RuptureKey key,
            double likelihood,
            double ruptureLoss,
            double lossContribution,
            double relativeContribution
    ) {}

    public RuptureResult get(RuptureKey key) {
        return new RuptureResult(
                key,
                ruptureLikelihoods.get(key),
                accumulator.getLoss(key),
                lossContributions.getOrDefault(key, 0.0),
                relativeContributions.getOrDefault(key, 0.0)
                
        );
    }

    public Iterable<RuptureResult> results() {
        return ruptureLikelihoods.keySet().stream()
                .map(this::get)
                .toList();
    }
    
    
    /**
     * Builds a rupture results collection from an already-initialized ERF.
     *
     * <p>The supplied ERF must have already had
     * {@code updateForecast()} called.</p>
     */
    public static RuptureResultsCollection fromERF(
            AbstractERF erf,
            HazardParameters hazardParameters
    ) {

    	Map<RuptureKey, Double> ruptureLikelihoods = new LinkedHashMap<>();

        int numSources = erf.getNumSources();

        for (int sourceID = 0; sourceID < numSources; sourceID++) {

            int numRuptures = erf.getNumRuptures(sourceID);

            for (int ruptureID = 0; ruptureID < numRuptures; ruptureID++) {

                ProbEqkRupture rupture =
                        erf.getSource(sourceID).getRupture(ruptureID);

                double likelihood;

                switch (hazardParameters.getHazardMetric()) {

                    case RATE_EXCEEDANCE:
                    	// average rate over full ERF duration (not annualized)
                        likelihood = rupture.getMeanAnnualRate(1.0);
                        break;

                    case PROBABILITY_EXCEEDANCE:
                        likelihood = rupture.getProbability();
                        break;

                    default:
                        throw new IllegalStateException(
                                "Unsupported hazard metric: "
                                        + hazardParameters.getHazardMetric()
                        );
                }

                ruptureLikelihoods.put(
                        new RuptureKey(sourceID, ruptureID),
                        likelihood
                );
            }
        }

        return new RuptureResultsCollection(
                hazardParameters,
                ruptureLikelihoods
        );
    }
        
    public void freeze() {
        if (frozen)
            return;

        accumulator.freeze();

        frozen = true;
        
        totalExpectedLoss = computeLossOverAllRuptures();
    }
    
    private double computeLossOverAllRuptures() {
    	
    	if (!frozen)
            throw new IllegalStateException(
                "Total loss can only be calculated after all ruptures are accumulated and frozen");
    	
    	// for both rate and probability, total loss is the sum of (likelihood * conditional loss)
    	// the difference is handled in the rupture likelihood, not in the combination equation
    	//TODO confirm whether prob_exc is appropriate for per-rupture

        lossContributions.clear();
        relativeContributions.clear();

        double total = 0.0;

        // first pass: absolute contributions
        for (Map.Entry<RuptureKey, Double> entry : ruptureLikelihoods.entrySet()) {

            RuptureKey key = entry.getKey();

            double likelihood = entry.getValue();
            double loss = accumulator.getLoss(key);

            double contribution = likelihood * loss;

            lossContributions.put(key, contribution);

            total += contribution;
        }

        // second pass: relative contributions
        if (total > 0.0) {
            for (Map.Entry<RuptureKey, Double> entry : lossContributions.entrySet()) {

                double relative = entry.getValue() / total;

                relativeContributions.put(entry.getKey(), relative);
            }
        } else {
            // edge case: no loss
            for (RuptureKey key : lossContributions.keySet()) {
                relativeContributions.put(key, 0.0);
            }
        }

        return total;
    }
    
    
    // ---------------------------------------------------------------------
    // UTILITIES
    // ---------------------------------------------------------------------
    
    /** Prints a summary of portfolio losses. */
    public void printSummary() {
        System.out.println("----- ELossPortfolio Summary -----");
        System.out.printf("Total ruptures: %d%n", getRuptureCount());
        System.out.printf("Total expected loss: %.2e%n", getTotalExpectedLoss());
    }

}