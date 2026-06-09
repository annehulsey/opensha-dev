package scratch.anne.risk_system_vb.engine.accumulators;

public record RuptureAssetLoss(
	    String assetId,
	    int sourceId,
	    int ruptureId,
	    double conditionalLoss
	) {}
