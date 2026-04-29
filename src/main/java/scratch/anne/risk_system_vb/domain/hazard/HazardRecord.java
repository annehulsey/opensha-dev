package scratch.anne.risk_system_vb.domain.hazard;

import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.AssetKeys.SiteKey;

public final class HazardRecord {

    public final SiteKey site;
    public final ImKey im;
    public final HazardResult result;

    public HazardRecord(SiteKey site, ImKey im, HazardResult result) {
        this.site = site;
        this.im = im;
        this.result = result;
    }
}
