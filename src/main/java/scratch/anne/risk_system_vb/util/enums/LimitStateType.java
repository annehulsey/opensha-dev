package scratch.anne.risk_system_vb.util.enums;

/**
 * Determines how the damage state participates in fragility calculations.
 */
public enum LimitStateType {
    ORDERED,     // Part of a cumulative sequence (P(DS ≥ i))
    COMPETING    // Independent failure mode, mutually exclusive
}
