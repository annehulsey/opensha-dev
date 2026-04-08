package scratch.anne.risk_system_vb.util.enums;

public enum DamageState {

    SLIGHT(LimitStateType.ORDERED),
    MODERATE(LimitStateType.ORDERED),
    EXTENSIVE(LimitStateType.ORDERED),
    COMPLETE(LimitStateType.ORDERED),

    TOPPLE(LimitStateType.COMPETING); // separate failure mode

    private final LimitStateType limitStateType;

    DamageState(LimitStateType limitStateType) {
        this.limitStateType = limitStateType;
    }

    public LimitStateType getLimitStateType() {
        return limitStateType;
    }

    public boolean isOrdered() {
        return limitStateType == LimitStateType.ORDERED;
    }

    public boolean isCompeting() {
        return limitStateType == LimitStateType.COMPETING;
    }
}