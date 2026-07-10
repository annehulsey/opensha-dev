package scratch.anne.risk_system_vb.domain.hazard;

/**
 * Unique identifier for a rupture within a source.
 */
public record RuptureKey(
        int sourceId,
        int ruptureId
) {}