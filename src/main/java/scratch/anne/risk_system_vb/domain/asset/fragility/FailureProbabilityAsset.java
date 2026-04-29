package scratch.anne.risk_system_vb.domain.asset.fragility;

import scratch.anne.risk_system_vb.domain.asset.RiskConvolutionAsset;
import scratch.anne.risk_system_vb.engine.convolution.ConvolutionResult;

public class FailureProbabilityAsset extends RiskConvolutionAsset{

    private final FragilityAsset asset;
    
    private ConvolutionResult riskResult;

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

    /** wrap the loss setter for generic workflows */
	@Override
	public void setRiskConvolutionResult(ConvolutionResult result) {
		this.riskResult = result;
		this.probabilityOfFailure = result.risk;
		
	}

	/** wrap the loss getter for generic workflows */
	@Override
	public ConvolutionResult getRiskConvolutionResult() {
		return this.riskResult;
	}
	
}
