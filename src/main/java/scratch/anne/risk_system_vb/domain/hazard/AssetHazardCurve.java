package scratch.anne.risk_system_vb.domain.hazard;

import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

public final class AssetHazardCurve {
    public final SiteKey siteKey;
    public final ImKey imKey;
    public final double[] imls;
    public final double[] hazard;

    public AssetHazardCurve(SiteKey siteKey,
                           ImKey imKey,
                           double[] imls,
                           double[] hazard) {
        this.siteKey = siteKey;
        this.imKey = imKey;
        this.imls = imls;
        this.hazard = hazard;
    }
}
