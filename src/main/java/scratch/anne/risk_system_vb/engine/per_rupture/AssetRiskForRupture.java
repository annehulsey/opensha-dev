package scratch.anne.risk_system_vb.engine.per_rupture;

public record AssetRiskForRupture(
	    String assetId,
	    int sourceId,
	    int ruptureId,
	    double conditionalRisk
	) {}
