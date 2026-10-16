package scratch.anne.risk_system_vb.engine.lec_pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import scratch.anne.risk_system_vb.domain.asset.vulnerability.VulnerabilityAsset;
import scratch.anne.risk_system_vb.domain.hazard.HazardParameters;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio.ConvolutionMode;
import scratch.anne.risk_system_vb.domain.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.vulnerabilities.SimpleImVulnLibraryPreparer;
import scratch.anne.risk_system_vb.domain.structural_response.vulnerabilities.VulnerabilityModel;
import scratch.anne.risk_system_vb.engine.convolution.RiskConvolution;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioPerRuptureRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.io.readers.ParseRiskRunParametersCSV;
import scratch.anne.risk_system_vb.io.readers.PortfolioReader;
import scratch.anne.risk_system_vb.io.readers.VulnerabilityLibraryReader;
import scratch.anne.risk_system_vb.io.writers.AssetRiskForRuptureWriter;
import scratch.anne.risk_system_vb.util.ImValueTransformer;
import scratch.anne.risk_system_vb.util.IO;
import scratch.anne.risk_system_vb.util.StringUtil;

/**
 * Pipeline stage 1: per-rupture expected annual losses for the true-mean
 * solution.
 *
 * <p>Replicates {@code PortfolioExpectedLossDualCalcByCSV}'s body, but
 * ONLY the {@link ConvolutionMode#PER_RUPTURE} path -- this pipeline has
 * no use for {@code FULL_HCURVE}, so that branch is omitted entirely
 * rather than left as dead config. No existing class is modified; this
 * class only calls the same public readers/calculators/writers the
 * original script already used.</p>
 *
 * <p>Gated by existence: if {@link PipelineConfig#getPerRuptureEalCsv()}
 * already exists, this stage is skipped -- the file is a complete,
 * self-contained artifact (every later stage reads it fresh via
 * RuptureLossLookupReader / TrueMeanSetupFromRuptureResults), so there is
 * nothing to resume or append; it is either there or it needs a full
 * re-run.</p>
 */
public final class PerRuptureEalStage {

    private PerRuptureEalStage() {}

    public static void run(PipelineConfig config) throws Exception {

        Path perRuptureEalCsv = config.getPerRuptureEalCsv();

        if (Files.exists(perRuptureEalCsv)) {
            System.out.println("[Stage 1] Skipping -- already exists: " + perRuptureEalCsv);
            return;
        }

        System.out.println("[Stage 1] " + perRuptureEalCsv + " not found -- running per-rupture EAL calculation...");

        Path runFolder = config.getRunFolder();

        if (!Files.isDirectory(runFolder))
            throw new IllegalArgumentException("Run folder does not exist: " + runFolder);

        Path configCSV = runFolder.resolve("input.csv");

        if (!Files.exists(configCSV))
            throw new IllegalArgumentException("Missing input.csv in run folder: " + runFolder);

        System.out.println("Running portfolio from folder:");
        System.out.println(runFolder);

        Map<String, String> runConfig = ParseRiskRunParametersCSV.loadConfig(configCSV);

        // erf_config.csv is optional -- returns ErfConfig.EMPTY if absent,
        // meaning the ERF self-constructs from input.csv's erf_class with
        // no extra parameters.
        ParseRiskRunParametersCSV.ErfConfig erfConfig =
                ParseRiskRunParametersCSV.loadErfConfig(runFolder, runConfig);

        if (erfConfig.usesSolutionFile()) {
            System.out.println("erf_config.csv found with solutionFile set -- using solution-file "
                    + "route. input.csv's erf_class ('" + runConfig.get("erf_class")
                    + "') will be IGNORED; erf_config.csv's erfClass ('" + erfConfig.erfClass()
                    + "') governs construction instead.");
        }

        // ------------------- 1. Resolve input files -------------------
        Path portfolioCSV = Path.of(runConfig.get("portfolio_csv"));
        Path vulnLibraryJSON = Path.of(runConfig.get("vuln_library_json"));

        IO.verifyFileExists(portfolioCSV, "Portfolio CSV");
        IO.verifyFileExists(vulnLibraryJSON, "Vulnerability JSON");

        // ------------------- 2. output paths -------------------
        AssetRiskForRuptureWriter assetWriter = config.isKeepAssetLoss()
                ? new AssetRiskForRuptureWriter(runFolder, config.getFileTag() + "_asset-eal-per-rup",
                        config.getFRelative(), config.getFAbsolute())
                : null;

        // ------------------- 3. hazard and integration parameters -------------------
        HazardParameters hazardParameters =
                ParseRiskRunParametersCSV.buildHazardParameters(runConfig, erfConfig);

        RiskConvolution.IntegrationMethod integrationMethod =
                ParseRiskRunParametersCSV.parseIntegrationMethod(runConfig.get("integration_method"));
        double logImStep = StringUtil.parseDoubleOrDefault(runConfig.get("log_im_step"), Double.NaN);

        // ------------------- 4. Load portfolio -------------------
        System.out.println("Loading portfolio...");
        Portfolio<VulnerabilityAsset> basePortfolio =
                PortfolioReader.readCSV(portfolioCSV, VulnerabilityAsset.class);

        Set<String> portfolioVulns = basePortfolio.getResponseModelNames();

        // ------------------- 5. Load vulnerability library -------------------
        System.out.println("Loading full vulnerability library...");
        ResponseModelLibrary<VulnerabilityModel> vulnLib =
                VulnerabilityLibraryReader.readLibrary(vulnLibraryJSON);

        // ------------------- 6. Prepare Expected Vulnerability Library -------------------
        System.out.println("Preparing vulnerability library...");
        ImValueTransformer transformer = Double.isNaN(logImStep)
                ? new ImValueTransformer.NoImTransformation()
                : new ImValueTransformer.LogInterpTransformation(logImStep);
        SimpleImResponseLibrary expVulnLib =
                SimpleImVulnLibraryPreparer.prepare(vulnLib, portfolioVulns, transformer);

        // ------------------- 7. PER_RUPTURE convolution only -------------------
        RiskConvolutionPortfolio riskConvolutionPortfolio = new RiskConvolutionPortfolio(basePortfolio, expVulnLib);

        PortfolioPerRuptureRiskConvolutionCalculator calculator =
                new PortfolioPerRuptureRiskConvolutionCalculator(
                        riskConvolutionPortfolio,
                        expVulnLib,
                        hazardParameters,
                        integrationMethod,
                        assetWriter
                );

        riskConvolutionPortfolio = calculator.computeRisk();
        riskConvolutionPortfolio.assertConvolutionExecutedAs(ConvolutionMode.PER_RUPTURE);
        riskConvolutionPortfolio.exportRuptureResults(perRuptureEalCsv);

        riskConvolutionPortfolio.getRuptureResults().printSummary();

        System.out.println("[Stage 1] Wrote: " + perRuptureEalCsv);
    }
}
