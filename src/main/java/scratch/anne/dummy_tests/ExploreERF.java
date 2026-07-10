package scratch.anne.dummy_tests;

import java.nio.file.Path;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF;

import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters.HazardMetric;
import scratch.anne.risk_system_vb.engine.per_rupture.RuptureLossResultsCollection;
import scratch.anne.risk_system_vb.io.writers.FileFormat;
import scratch.anne.risk_system_vb.io.writers.RuptureResultsExporter;

public class ExploreERF {

	public static void main(String[] args) throws Exception {

		String erfClassName = 
				"org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF";
//				"org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2";
//				"scratch.UCERF3.erf.mean.MeanUCERF3";

        Path baseFolder = Path.of(
                "C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\software architecture\\my_scratch\\conversion to Java project\\BERM_test-outputs\\dummy_tests\\rupture-results\\extra_tests"
        );
        
        
        HazardMetric[] metrics = {
                HazardMetric.RATE_EXCEEDANCE,
        };

        double[] durations = {50d};

        
//        HazardMetric[] metrics = {
//                HazardMetric.RATE_EXCEEDANCE,
//                HazardMetric.PROBABILITY_EXCEEDANCE
//        };
//
//        double[] durations = {1d, 25d, 50d, 100d};
        
        Class<?> erfClass = Class.forName(erfClassName);

        if (!AbstractERF.class.isAssignableFrom(erfClass)) {
            throw new IllegalArgumentException(
                    "Specified ERF class does not extend AbstractERF: " + erfClassName
            );
        }

        for (HazardMetric metric : metrics) {
            for (double erfDuration : durations) {

                // -----------------------------
                // fresh ERF EACH iteration
                // -----------------------------
                AbstractERF erf = (AbstractERF)
                        erfClass.getDeclaredConstructor()
                                .newInstance();

                configureERF(erf);

                erf.getTimeSpan().setDuration(erfDuration);
                erf.updateForecast();

                // -----------------------------
                // filename
                // -----------------------------
                String metricTag = switch (metric) {
                    case PROBABILITY_EXCEEDANCE -> "prob";
                    case RATE_EXCEEDANCE -> "rate";
                    default -> throw new IllegalStateException();
                };

                String durationTag = (erfDuration == (long) erfDuration)
                        ? String.valueOf((long) erfDuration)
                        : String.valueOf(erfDuration);

                Path outputCsv = baseFolder.resolve(
                        String.format("rupture-results_%s-%s.csv", metricTag, durationTag)
                );

                // -----------------------------
                // hazard parameters
                // -----------------------------
                HazardParameters params =
                        new HazardParameters(
                                erf.getName(),
                                erf.getTimeSpan().getDuration(),
                                metric,
                                "placeholder GMM name"
                        );

                // -----------------------------
                // build + export
                // -----------------------------
                RuptureLossResultsCollection results =
                        RuptureLossResultsCollection.fromERF(erf, params);

                RuptureResultsExporter exporter = new RuptureResultsExporter();
                exporter.export(results, outputCsv, FileFormat.CSV);

                System.out.println("Done → " + outputCsv);
            }
        }
	}
    
    
    static void configureERF(AbstractERF erf) {

        if (erf instanceof UCERF2 u) {
            u.setParameter(
                UCERF2.PROB_MODEL_PARAM_NAME,
                UCERF2.PROB_MODEL_POISSON
            );
        }

        else if (erf instanceof MeanUCERF3 m) {
            m.setPreset(MeanUCERF3.Presets.FM3_1_BRANCH_AVG);
            m.setParameter(
                ProbabilityModelParam.NAME,
                ProbabilityModelOptions.POISSON
            );
        }

        else if (erf instanceof NSHM23_WUS_BranchAveragedERF) {
            // no-op
        }
    }
}