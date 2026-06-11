package scratch.anne.risk_system_vb.engine.accumulators;

public record AssetRiskForRupture(
	    String assetId,
	    int sourceId,
	    int ruptureId,
	    double conditionalRisk
	) {}
