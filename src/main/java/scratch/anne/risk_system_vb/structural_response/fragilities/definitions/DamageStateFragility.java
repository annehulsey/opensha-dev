package scratch.anne.risk_system_vb.structural_response.fragilities.definitions;

import scratch.anne.risk_system_vb.util.enums.DamageState;

public class DamageStateFragility {

    private final DamageState damageState;
    private final FragilityDefinition definition;

    public DamageStateFragility(DamageState damageState,
                                FragilityDefinition definition) {

        if (damageState == null)
            throw new IllegalArgumentException("Damage state required");

        if (definition == null)
            throw new IllegalArgumentException("Definition cannot be null");

        this.damageState = damageState;
        this.definition = definition;
    }

    public DamageState getDamageState() {
        return damageState;
    }

    public FragilityDefinition getDefinition() {
        return definition;
    }

    public boolean isOrdered() {
        return damageState.isOrdered();
    }

    public boolean isCompeting() {
        return damageState.isCompeting();
    }
}