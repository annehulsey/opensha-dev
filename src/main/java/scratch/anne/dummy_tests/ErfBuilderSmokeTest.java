package scratch.anne.dummy_tests;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.AbstractERF;

import scratch.anne.risk_system_vb.domain.hazard.ErfBuilder;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters.HazardMetric;

/**
 * Standalone smoke test for ErfBuilder, using the true mean solution zip
 * from TrueMeanERFDemo and an erf_config equivalent to that demo's full
 * construction sequence:
 *
 *   trueMeanERF.setParameter(UseRupMFDsParam.NAME, true);
 *   trueMeanERF.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
 *   GriddedSeismicitySettings gridSettings = GriddedSeismicitySettings.DEFAULT
 *           .forSurfaceType(BackgroundRupType.FINITE)
 *           .forFiniteRuptureSettings(GriddedFiniteRuptureSettings.DEFAULT_SINGLE)
 *           .forPointSourceMagCutoff(5d);
 *   trueMeanERF.setGriddedSeismicitySettings(gridSettings);
 *
 * <p>Runs three builds:</p>
 * <ol>
 *   <li>BARE -- solution file, no erfConfig at all. Shows the ERF's own
 *       defaults (including its default GriddedSeismicitySettings, which
 *       defaults to POINT surfaces, not FINITE).</li>
 *   <li>DEMO_EQUIVALENT -- solution file + full erfConfig replicating the
 *       demo exactly. Rupture/source counts should differ from BARE if
 *       the config was actually applied (FINITE surfaces + Use Rupture
 *       MFDs both affect rupture composition).</li>
 *   <li>FLIPPED -- same as DEMO_EQUIVALENT but with "Use Rupture MFDs"
 *       set to false, deliberately AWAY from its true default (recall:
 *       true was a no-op test earlier since it matched the default).
 *       This isolates whether that one parameter's coercion path works,
 *       independent of the gridded settings.</li>
 * </ol>
 */
public class ErfBuilderSmokeTest {

    // ------------------------------------------------------------
    // EDIT THESE for your environment
    // ------------------------------------------------------------
    private static final File DATA_DIR = new File(
            "C:\\Users\\ahulsey\\OneDrive - DOI\\Desktop\\Research\\openSRA\\software architecture\\"
            + "my_scratch\\logic_tree_reweighting\\2024_02_02-nshm23_branches-WUS_FM_v3-gridded_rebuild"
    );

    private static final File SOLUTION_FILE = new File(DATA_DIR, "true_mean_solution.zip");

    // Must be a BaseFaultSystemSolutionERF-family class per the
    // solution-file route's instanceof check in ErfBuilder.
    private static final String ERF_CLASS =
            "org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF";

    private static final double ERF_DURATION = 1.0;
    // ------------------------------------------------------------

    public static void main(String[] args) throws Exception {

        if (!SOLUTION_FILE.exists()) {
            throw new IllegalStateException(
                    "Solution file not found: " + SOLUTION_FILE.getAbsolutePath()
                    + " -- update DATA_DIR/SOLUTION_FILE to point at a real true_mean_solution.zip");
        }

        // ------------------------------------------------------------
        // Run 1: BARE -- solution file only, no erfConfig
        // ------------------------------------------------------------
        AbstractERF bareErf = runBuild("BARE (no erfConfig)", Map.of());
        dumpParams(bareErf);
        long bareRups = countRuptures(bareErf);

        // ------------------------------------------------------------
        // Run 2: DEMO_EQUIVALENT -- full erfConfig replicating the demo
        // ------------------------------------------------------------
        Map<String, String> demoEquivalentConfig = new LinkedHashMap<>();
        demoEquivalentConfig.put("Use Rupture MFDs", "true");
        demoEquivalentConfig.put("Background Seismicity", "INCLUDE");
        demoEquivalentConfig.put(ErfBuilder.GRIDDED_SURFACE_TYPE_KEY, "FINITE");
        demoEquivalentConfig.put(ErfBuilder.GRIDDED_FINITE_NUM_SURFACES_KEY, "1"); // == DEFAULT_SINGLE
        demoEquivalentConfig.put(ErfBuilder.GRIDDED_POINT_SOURCE_MAG_CUTOFF_KEY, "5.0");

        AbstractERF demoEquivalentErf = runBuild("DEMO_EQUIVALENT", demoEquivalentConfig);
        dumpParams(demoEquivalentErf);
        long demoEquivalentRups = countRuptures(demoEquivalentErf);

        // ------------------------------------------------------------
        // Run 3: FLIPPED -- same as DEMO_EQUIVALENT but Use Rupture MFDs
        // forced to false, deliberately away from default, to isolate
        // that one parameter's coercion path (see: "true" was a no-op
        // test earlier since BaseFaultSystemSolutionERF already
        // defaults to true).
        // ------------------------------------------------------------
        Map<String, String> flippedConfig = new LinkedHashMap<>(demoEquivalentConfig);
        flippedConfig.put("Use Rupture MFDs", "false");

        AbstractERF flippedErf = runBuild("FLIPPED (Use Rupture MFDs=false)", flippedConfig);
        dumpParams(flippedErf);
        long flippedRups = countRuptures(flippedErf);

        // ------------------------------------------------------------
        // Summary: did anything actually change?
        // ------------------------------------------------------------
        System.out.println("\n=== SUMMARY ===");
        System.out.println("BARE total ruptures:            " + bareRups);
        System.out.println("DEMO_EQUIVALENT total ruptures: " + demoEquivalentRups);
        System.out.println("FLIPPED total ruptures:         " + flippedRups);

        if (demoEquivalentRups == bareRups) {
            System.out.println("WARNING: DEMO_EQUIVALENT rupture count matches BARE exactly -- "
                    + "this does NOT prove erfConfig was applied. Check the 'Gridded Seismicity "
                    + "Settings' and 'Use Rupture MFDs' values printed above against BARE's values.");
        } else {
            System.out.println("OK: DEMO_EQUIVALENT rupture count differs from BARE, consistent "
                    + "with erfConfig actually being applied.");
        }

        if (flippedRups == demoEquivalentRups) {
            System.out.println("WARNING: FLIPPED rupture count matches DEMO_EQUIVALENT exactly -- "
                    + "'Use Rupture MFDs' may not be taking effect. Expected fewer ruptures with "
                    + "Use Rupture MFDs=false (branch-averaged magnitude collapses each fault "
                    + "source to one rupture instead of one per distinct branch magnitude).");
        } else {
            System.out.println("OK: FLIPPED rupture count differs from DEMO_EQUIVALENT, consistent "
                    + "with 'Use Rupture MFDs' coercion actually working.");
        }
    }

    private static AbstractERF runBuild(String label, Map<String, String> erfConfig) {
        System.out.println("\n===================================================");
        System.out.println("Building: " + label);
        System.out.println("erfConfig: " + erfConfig);

        HazardParameters params = new HazardParameters(
                ERF_CLASS,
                ERF_DURATION,
                HazardMetric.RATE_EXCEEDANCE,
                "ASK_2014",              // any valid AttenRelRef name; unused by ErfBuilder itself
                Map.of(),                // filterConfig
                erfConfig,
                SOLUTION_FILE.toPath()   // solution-file route
        );

        long t0 = System.currentTimeMillis();
        AbstractERF erf = ErfBuilder.build(params);
        System.out.printf("Built in %.1f s%n", (System.currentTimeMillis() - t0) / 1000.0);
        System.out.println("Total sources: " + erf.getNumSources());
        System.out.println("Total ruptures: " + countRuptures(erf));
        return erf;
    }

    private static void dumpParams(AbstractERF erf) {
        System.out.println("--- Parameters ---");
        for (Parameter<?> p : erf.getAdjustableParameterList()) {
            System.out.printf("  name=%-40s currentValue=%s%n",
                    "'" + p.getName() + "'", p.getValue());
        }
    }

    private static long countRuptures(AbstractERF erf) {
        long total = 0;
        for (int s = 0; s < erf.getNumSources(); s++)
            total += erf.getSource(s).getNumRuptures();
        return total;
    }
}