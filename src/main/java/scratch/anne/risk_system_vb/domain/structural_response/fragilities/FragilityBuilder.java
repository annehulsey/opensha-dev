package scratch.anne.risk_system_vb.domain.structural_response.fragilities;

import java.util.ArrayList;
import java.util.List;

import scratch.anne.risk_system_vb.domain.structural_response.fragilities.definitions.*;
import scratch.anne.risk_system_vb.util.enums.IMT;
import scratch.anne.risk_system_vb.util.enums.LimitState;
import scratch.anne.risk_system_vb.util.StringUtil;

public class FragilityBuilder {

    private final String compID;
    private IMT imt;
    private Double period;

    private final List<LimitStateFragility> lsList = new ArrayList<>();

    private LimitState primaryLS;

    private FragilityBuilder(String compID) {
        this.compID = compID;
    }

    public static FragilityBuilder component(String compID) {
        return new FragilityBuilder(compID);
    }
    
    public FragilityBuilder imtString(String imtString) {
    	StringUtil.ImtPeriod imtPeriod= StringUtil.stringToImtPeriod(imtString);
        this.imt = imtPeriod.imt;
        this.period = imtPeriod.period;
        return this;
    }
    
    public FragilityBuilder primaryLS(LimitState ls) {
        this.primaryLS = ls;
        return this;
    }
    
    public FragilityBuilder lognormal(LimitState ls, double median, double beta) {

        FragilityDefinition def =
                new LognormalFragilityDefinition(median, beta);

        lsList.add(new LimitStateFragility(ls, def));

        return this;
    }
    
    public FragilityBuilder discrete(
            LimitState ls,
            double[] imLevels,
            double[] probabilities) {

        FragilityDefinition def =
                new DiscreteFragilityDefinition(imLevels, probabilities);

        lsList.add(new LimitStateFragility(ls, def));

        return this;
    }
    
    public FragilityModel build() {

        if (imt == null)
            throw new IllegalStateException("IMT must be set");

        if (lsList.isEmpty())
            throw new IllegalStateException("At least one LimitState required");

        return new FragilityModel(
                compID,
                imt,
                period,
                lsList,
                primaryLS
        );
    }
    
}
