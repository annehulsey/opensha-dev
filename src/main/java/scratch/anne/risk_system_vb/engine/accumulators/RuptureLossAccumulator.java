package scratch.anne.risk_system_vb.engine.accumulators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Thread-safe accumulator for rupture-level losses.
 *
 * <p>Stores running total loss per rupture key using DoubleAdder
 * to minimize contention in parallel rupture/asset loops.</p>
 */
public final class RuptureLossAccumulator {
	
	private boolean frozen = false;

    /**
     * Key → accumulated loss
     */
    private final ConcurrentHashMap<RuptureKey, DoubleAdder> lossByRupture =
            new ConcurrentHashMap<>();

    /**
     * Adds loss contribution for a rupture.
     *
     * <p>This is the ONLY mutation method intended for hot-loop usage.</p>
     */
    public void accumulateRuptureLoss(RuptureKey key, double loss) {
        if (frozen)
            throw new IllegalStateException(
                "RuptureLossAccumulator is frozen");
        lossByRupture
                .computeIfAbsent(key, k -> new DoubleAdder())
                .add(loss);
    }

    /**
     * Gets accumulated loss for a rupture.
     * Returns 0 if no loss was recorded.
     */
    public double getLoss(RuptureKey key) {
        DoubleAdder adder = lossByRupture.get(key);
        return adder == null ? 0.0 : adder.sum();
    }

    /**
     * Direct access to internal map (for export / materialization).
     * Avoid using in hot loops.
     */
    public ConcurrentHashMap<RuptureKey, DoubleAdder> raw() {
        return lossByRupture;
    }

    /**
     * Optional: number of ruptures that received any loss.
     */
    public int size() {
        return lossByRupture.size();
    }

    /**
     * Optional: clears accumulator (useful for reuse across runs).
     */
    public void clear() {
        lossByRupture.clear();
    }
    
    public void freeze() {
        frozen = true;
    }
    
}
