package scratch.anne.risk_system_vb.engine.portfolio_workflow;

public record RuptureAssetContribution(
    int sourceId,
    int ruptureId,
    String assetId,
    double conditionalLoss
) {}