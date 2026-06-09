package scratch.anne.risk_system_vb.domain.asset;

import java.util.Map;

import scratch.anne.risk_system_vb.engine.convolution.ConvolutionResult;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;

public abstract class RiskConvolutionAsset extends AbstractAsset {
	
	private RiskMetricType riskMetricType;

    protected RiskConvolutionAsset(
            String assetID,
            double lat,
            double lon,
            double vs30,
            String modelName,
            Map<String, String> additionalFields,
            AssetType assetType,
            RiskMetricType riskMetricType
    ) {
        super(assetID, lat, lon, vs30, modelName, additionalFields, assetType);
        this.riskMetricType = riskMetricType;
    }
    
    private ImKey imKey;

    public abstract void setRiskConvolutionResult(ConvolutionResult result);
    public abstract ConvolutionResult getRiskConvolutionResult();
    
    
    public ImKey getImKey() {
        return imKey;
    }
    public void setImKey(ImKey imKey) {
        this.imKey = imKey;
    }
    public RiskMetricType getRiskMetricType() {
    	return riskMetricType;
    }
    
    public enum RiskMetricType {
    	EXPECTED_LOSS,
        FAILURE_PROBABILITY,
        PBR_SURVIVAL
    }
    
}
