package scratch.anne.risk_system_vb.structural_response.fragilities;

import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.SimpleImResponse;
import scratch.anne.risk_system_vb.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.DamageStateFragility;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.DiscreteFragilityDefinition;
import scratch.anne.risk_system_vb.structural_response.fragilities.definitions.LognormalFragilityDefinition;
import scratch.anne.risk_system_vb.util.ImValueTransformer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Preparer that converts a {@link ResponseModelLibrary} of {@link FragilityModel}
 * into a {@link SimpleImResponseLibrary} of {@link SimpleImResponse}.
 *
 * <p>Supports optional filtering by name, optional IM transformation (for discrete fragilities),
 * optional custom IM values for lognormal fragilities, and primary damage state selection.
 */
public class SimpleImFragilityLibraryPreparer {

    private SimpleImFragilityLibraryPreparer() {
        // static utility class
    }

    // ---------------- Convenience prepare methods ----------------

    public static SimpleImResponseLibrary prepare(
            ResponseModelLibrary<FragilityModel> modelLibrary) {
        return prepare(modelLibrary, null, new ImValueTransformer.NoImTransformation(), null, null);
    }

    public static SimpleImResponseLibrary prepare(
            ResponseModelLibrary<FragilityModel> modelLibrary,
            Set<String> filterNames) {
        return prepare(modelLibrary, filterNames, new ImValueTransformer.NoImTransformation(), null, null);
    }

    public static SimpleImResponseLibrary prepare(
            ResponseModelLibrary<FragilityModel> modelLibrary,
            Set<String> filterNames,
            ImValueTransformer transformer,
            String primaryDS,
            double[] customImValues) {

        // Convert filtered models
        List<SimpleImResponse> responses = modelLibrary.getModels().stream()
                .filter(m -> filterNames == null || filterNames.contains(m.getName()))
                .map(m -> convert(m, transformer, primaryDS, customImValues))
                .collect(Collectors.toList());

        // Wrap into a fully-typed SimpleImResponseLibrary
        return SimpleImResponseLibrary.of(responses, modelLibrary.getMetadata());
    }

    // ---------------- Core conversion helper ----------------

    private static SimpleImResponse convert(
            FragilityModel model,
            ImValueTransformer transformer,
            String primaryDS,
            double[] customImValues) {

        List<DamageStateFragility> dsList = model.getDamageStateFragilities();
        if (dsList.isEmpty()) {
            throw new IllegalStateException("FragilityModel has no damage states: " + model.getName());
        }

        // Select primary damage state
        DamageStateFragility selectedDS;
        if (primaryDS != null) {
            selectedDS = dsList.stream()
                    .filter(ds -> ds.getDamageState().equals(primaryDS))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Primary DS not found in fragility model " + model.getName() + ": " + primaryDS));
        } else {
            selectedDS = dsList.get(0); // default to first
        }

        double[] imValues;
        double[] respValues;

        if (selectedDS.getDefinition() instanceof DiscreteFragilityDefinition) {
            var def = (DiscreteFragilityDefinition) selectedDS.getDefinition();
            ImValueTransformer.Result result = transformer.transform(def.getImLevels(), def.getProbability());
            imValues = result.imValues;
            respValues = result.respValues;

        } else if (selectedDS.getDefinition() instanceof LognormalFragilityDefinition) {
            var def = (LognormalFragilityDefinition) selectedDS.getDefinition();

            // Use custom IM grid if provided, otherwise default to 50 log-spaced points around median
            if (customImValues != null) {
                imValues = customImValues.clone();
            } else {
                int n = 50;
                imValues = new double[n];
                double min = def.getMedian() / 10.0;
                double max = def.getMedian() * 10.0;
                double logMin = Math.log(min);
                double logMax = Math.log(max);
                for (int i = 0; i < n; i++) {
                    imValues[i] = Math.exp(logMin + i * (logMax - logMin) / (n - 1));
                }
            }

            respValues = def.getProbability(imValues);

        } else {
            throw new IllegalStateException("Unknown FragilityDefinition type: " + selectedDS.getDefinition().getClass());
        }

        return new SimpleImResponse(
                model.getName(),
                model.getImt(),
                model.getPeriod(),
                imValues,
                respValues
        );
    }
}