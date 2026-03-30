package scratch.anne.risk_system_vb.util;

/**
 * Strategy interface for transforming intensity measure (IM) arrays and associated
 * response arrays in {@link ExpectedVulnerability} preparation.
 *
 * <p>This allows flexible interpolation, resampling, or other modifications of the
 * IM values before they are stored in a {@link SimpleImResponseLibrary}.
 */
public interface ImValueTransformer {

    /**
     * Encapsulates the transformed arrays.
     */
    class Result {
        /** Transformed intensity measure values (IM bin edges) */
        public final double[] imValues;
        /** Transformed response values (same length as imValues) */
        public final double[] respValues;

        public Result(double[] imValues, double[] respValues) {
            this.imValues = imValues;
            this.respValues = respValues;
        }
    }

    /**
     * Transform the provided IM and response arrays into new arrays.
     *
     * @param imValues original intensity measure values
     * @param respValues original response values (damage ratios)
     * @return transformed IM and response arrays encapsulated in a Result
     */
    Result transform(double[] imValues, double[] respValues);


    // ------------------- Built-in implementations -------------------

    /**
     * Identity transformer that returns the original arrays unchanged.
     * Use this when no transformation (interpolation, resampling) is needed.
     */
    class NoImTransformation implements ImValueTransformer {
        @Override
        public Result transform(double[] imValues, double[] respValues) {
            // simply return clones to prevent accidental mutation
            return new Result(imValues.clone(), respValues.clone());
        }
    }

    /**
     * Placeholder transformer for future logspace or linspace resampling.
     * Currently behaves as identity.
     */
    class PlaceholderImTransformation implements ImValueTransformer {
        @Override
        public Result transform(double[] imValues, double[] respValues) {
            // TODO: implement optional interpolation (logspace, linspace, fixed points, etc.)
            return new Result(imValues.clone(), respValues.clone());
        }
    }
}
