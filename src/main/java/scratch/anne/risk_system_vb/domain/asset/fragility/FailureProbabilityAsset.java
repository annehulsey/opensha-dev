package scratch.anne.risk_system_vb.domain.asset.fragility;

import scratch.anne.risk_system_vb.domain.asset.RiskConvolutionAsset;

public class FailureProbabilityAsset extends RiskConvolutionAsset{

    private final FragilityAsset asset;

    private double probabilityOfFailure = Double.NaN;

    public FailureProbabilityAsset(FragilityAsset asset) {

        super(
            asset.getAssetID(),
            asset.getLatitude(),
            asset.getLongitude(),
            asset.getVs30(),
            asset.getModelName(),
            asset.getAdditionalFields()
        );

        this.asset = asset;
    }

    public FragilityAsset getAsset() {
        return asset;
    }

    public double getProbabilityOfFailure() {
        return probabilityOfFailure;
    }

    public void setProbabilityOfFailure(double value) {
        this.probabilityOfFailure = value;
    }

    /** wrap the loss setter for generic workflows */
	@Override
	public void setRiskConvolutionResult(double value) {
		this.setProbabilityOfFailure(value);
		
	}

	/** wrap the loss getter for generic workflows */
	@Override
	public double getRiskConvolutionResult() {
		return this.getProbabilityOfFailure();
	}
	
}
