package scratch.anne.risk_system_vb.structural_response.fragilities;

import java.util.List;

import scratch.anne.risk_system_vb.structural_response.NamedResponseModel;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.DamageStateFragility;
import scratch.anne.risk_system_vb.util.StringUtil;
import scratch.anne.risk_system_vb.util.enums.IMT;

public class FragilityModel implements NamedResponseModel {

    private final String name;
    private final IMT imt;
    private final Double period;
    private final String imtString;

    private final List<DamageStateFragility> damageStates;

    public FragilityModel(
            String name,
            IMT imt,
            Double period,
            List<DamageStateFragility> damageStates) {

        if (damageStates == null || damageStates.isEmpty())
            throw new IllegalArgumentException("At least one damage state required");

        this.name = name;
        this.imt = imt;
        this.period = period;
        this.imtString = StringUtil.imtToString(imt, period);
        this.damageStates = List.copyOf(damageStates);

        validate();
    }

    private void validate() {

        if (imt == IMT.SA && period == null)
            throw new IllegalStateException("SA requires a period");

        if (imt != IMT.SA && period != null)
            throw new IllegalStateException("Only SA can have a period");
    }

    public List<DamageStateFragility> getDamageStateFragilities() {
        return damageStates;
    }

    @Override
    public String getName() { return name; }

    @Override
    public IMT getImt() { return imt; }

    @Override
    public Double getPeriod() { return period; }

    @Override
    public String getImtString() { return imtString; }
    
	@Override
	public String toString() {
	  return "FragilityModel{name='" + name + "', imt=" + imtString + "}";
	}
	
	public String toVerboseString() {

	    StringBuilder sb = new StringBuilder();

	    sb.append("Fragility Model: ").append(name).append("\n");
	    sb.append("IMT: ").append(imtString).append("\n");
	    sb.append("Damage States:\n");

	    for (DamageStateFragility dsFrag : damageStates) {

	        sb.append("\n");
	        sb.append("  Damage State: ")
	          .append(dsFrag.getDamageState())
	          .append("\n");
	        
	        sb.append(dsFrag.getDefinition().toVerboseString());
	    }
	    return sb.toString();
	}
}