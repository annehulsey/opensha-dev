package scratch.anne.risk_system_vb.util;

import java.util.Arrays;

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
     * Transformer that resamples IM and response arrays onto a log-spaced IM grid.
     * Linear interpolation is used for the response values.
     */
    class LogInterpTransformation implements ImValueTransformer {

        private final double deltaLog10; // spacing in log10(IM)

        /**
         * @param deltaLog10 spacing in log10(IM), e.g., 0.025 gives ~90 bins per decade
         */
        public LogInterpTransformation(double deltaLog10) {
            if (deltaLog10 <= 0) throw new IllegalArgumentException("deltaLog10 must be positive");
            this.deltaLog10 = deltaLog10;
        }

        /**
         * Resample IM values onto a log-spaced grid, linearly interpolating
         * the corresponding response values.
         *
         * @param imValues original IM array (must be sorted ascending)
         * @param respValues original response array
         * @return transformed IM and response arrays
         */
        @Override
        public Result transform(double[] imValues, double[] respValues) {
            if (imValues.length != respValues.length) {
                throw new IllegalArgumentException("IM and response arrays must have same length");
            }
            int nOriginal = imValues.length;
            double imMin = imValues[0];
            double imMax = imValues[nOriginal - 1];

            // Compute number of bins
            int nBins = (int) Math.ceil(Math.log10(imMax / imMin) / deltaLog10);

            double[] logIMGrid = new double[nBins + 1];
            double[] respGrid = new double[nBins + 1];

            // Fill log-spaced IM values
            for (int i = 0; i <= nBins; i++) {
                logIMGrid[i] = imMin * Math.pow(10, i * deltaLog10);
            }
            logIMGrid[nBins] = imMax; // ensure exact max

            // Linear interpolation of response values
            for (int i = 0; i <= nBins; i++) {
                double im = logIMGrid[i];
                int idx = Arrays.binarySearch(imValues, im);

                if (idx >= 0) {
                    respGrid[i] = respValues[idx]; // exact match
                } else {
                    int insert = -idx - 1;
                    if (insert == 0) {
                        respGrid[i] = respValues[0]; // extrapolate below
                    } else if (insert >= nOriginal) {
                        respGrid[i] = respValues[nOriginal - 1]; // extrapolate above
                    } else {
                        double x0 = imValues[insert - 1];
                        double x1 = imValues[insert];
                        double y0 = respValues[insert - 1];
                        double y1 = respValues[insert];
                        respGrid[i] = y0 + (y1 - y0) * (im - x0) / (x1 - x0);
                    }
                }
            }

            return new Result(logIMGrid, respGrid);
        }
    }

    /**
     * Placeholder transformer for future resampling logic.
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