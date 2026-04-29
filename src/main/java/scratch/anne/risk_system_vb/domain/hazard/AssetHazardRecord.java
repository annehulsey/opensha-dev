package scratch.anne.risk_system_vb.domain.hazard;

import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

public final class AssetHazardRecord {
    public final SiteKey siteKey;
    public final ImKey imKey;
    public final double[] imls;
    public final double[] poe;

    public AssetHazardRecord(SiteKey siteKey,
                           ImKey imKey,
                           double[] imls,
                           double[] poe) {
        this.siteKey = siteKey;
        this.imKey = imKey;
        this.imls = imls;
        this.poe = poe;
    }
}
