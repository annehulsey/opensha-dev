package scratch.anne.risk_system_vb.examples;

import scratch.anne.risk_system_vb.structural_response.fragilities.*;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.DamageStateFragility;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.DiscreteFragilityDefinition;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.FragilityDefinition;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.LognormalFragilityDefinition;
import scratch.anne.risk_system_vb.util.enums.DamageState;
import scratch.anne.risk_system_vb.util.enums.IMT;

import java.util.List;

public final class FragilityModelExample {

    private FragilityModelExample() {} // no instantiation

    /*
     * ===============================
     *  PUBLIC ENTRY POINT
     * ===============================
     */

    public static FragilityModel buildExampleModel() {

        List<DamageStateFragility> damageStates = List.of(
                new DamageStateFragility(
                        DamageState.SLIGHT,
                        slightFragility()),

                new DamageStateFragility(
                        DamageState.MODERATE,
                        moderateFragility()),

                new DamageStateFragility(
                        DamageState.COMPLETE,
                        completeFragility()),

                // competing state
                new DamageStateFragility(
                        DamageState.TOPPLE,
                        toppleFragility())
        );

        return new FragilityModel(
                "ExampleBuilding",
                IMT.SA,
                0.5,
                damageStates
        );
    }

    /*
     * ===============================
     *  FRAGILITY DEFINITIONS
     * ===============================
     */

    private static FragilityDefinition slightFragility() {
        return new DiscreteFragilityDefinition(
                new double[]{0.1, 0.2, 0.3},
                new double[]{0.05, 0.2, 0.5});
    }

    private static FragilityDefinition moderateFragility() {
        return new LognormalFragilityDefinition(0.25, 0.35);
    }

    private static FragilityDefinition completeFragility() {
        return new DiscreteFragilityDefinition(
                new double[]{0.3, 0.5, 0.7},
                new double[]{0.2, 0.6, 0.9});
    }

    private static FragilityDefinition toppleFragility() {
        return new LognormalFragilityDefinition(0.5, 0.4);
    }

    /*
     * ===============================
     *  DEMO MAIN (optional)
     * ===============================
     */

    public static void main(String[] args) {

        FragilityModel model = buildExampleModel();

        System.out.println("Fragility model: " + model.getName());

        model.getDamageStateFragilities().forEach(ds ->
                System.out.println(
                        ds.getDamageState()
                                + " -> "
                                + ds.getDefinition().getClass().getSimpleName()));
        
        System.out.println();
        System.out.println(model.toVerboseString());
    }
}