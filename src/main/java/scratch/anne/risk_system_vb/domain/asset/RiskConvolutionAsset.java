package scratch.anne.risk_system_vb.domain.asset;

import java.util.Map;

import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;

public abstract class RiskConvolutionAsset extends AbstractAsset {

    protected RiskConvolutionAsset(
            String assetID,
            double lat,
            double lon,
            double vs30,
            String modelName,
            Map<String, String> additionalFields
    ) {
        super(assetID, lat, lon, vs30, modelName, additionalFields);
    }
    
    private ImKey imKey;

    public abstract void setRiskConvolutionResult(double v);
    public abstract double getRiskConvolutionResult();
    
    
    public ImKey getImKey() {
        return imKey;
    }
    public void setImKey(ImKey imKey) {
        this.imKey = imKey;
    }
}
