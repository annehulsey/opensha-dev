package scratch.anne.risk_system_vb.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.imr.AttenRelRef;

import scratch.anne.risk_system_vb.util.enums.IMT;
import scratch.anne.risk_system_vb.util.enums.LimitState;
import scratch.anne.risk_system_vb.domain.hazard.AssetHazardRecord;
import scratch.anne.risk_system_vb.domain.asset.fragility.FragilityAsset;
import scratch.anne.risk_system_vb.domain.asset.fragility.PBRSurvivalAsset;
import scratch.anne.risk_system_vb.domain.portfolio.Portfolio;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.PBRSurvivalPortfolioReporter;
import scratch.anne.risk_system_vb.domain.portfolio.portfolio_wrappers.RiskConvolutionPortfolio;
import scratch.anne.risk_system_vb.domain.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.SimpleImResponseLibrary;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.FragilityBuilder;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.FragilityModel;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.SimpleImFragilityLibraryPreparer;
import scratch.anne.risk_system_vb.domain.structural_response.fragilities.SimpleImFragilityLibraryPreparer.PrepareSpec;
import scratch.anne.risk_system_vb.engine.portfolio_workflow.PortfolioRiskConvolutionCalculator;
import scratch.anne.risk_system_vb.util.AssetKeys.ImKey;
import scratch.anne.risk_system_vb.util.Metadata;
import scratch.anne.risk_system_vb.util.StringUtil;

public class PBRAppUI extends JFrame {
	
    // =========================
    // UI STATE (FIELDS)
    // =========================

	// --- Site ---
	private JTextField latField, lonField, vs30Field;

	// --- Hazard ---
	private JComboBox<ERFOption> erfCombo;
	private JComboBox<GMMOption> gmmCombo;
	
	// --- Fragility ---
	private JTextField ageField, medianField, betaField;
	
	private JComboBox<IMTOption> imtCombo;

	// --- Probability ---
	private JTextField probField;
	
	// --- Output ---
	private PlotPanel plotPanel;
	private JTextArea resultsText;
	
	private JProgressBar progressBar;
	private JLabel progressLabel;

	
	private static final ERFOption[] ALLOWED_ERFS = {
		new ERFOption("org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf.NSHM23_WUS_BranchAveragedERF"),
	    new ERFOption("org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final"),
	    new ERFOption("org.opensha.sha.earthquake.rupForecastImpl.MeanUCERF3"),
	};
	

	private static final GMMOption[] ALLOWED_GMMS = {
			new GMMOption(AttenRelRef.USGS_NSHM23_ACTIVE),
		    new GMMOption(AttenRelRef.ASK_2014),
		    new GMMOption(AttenRelRef.BSSA_2014),
		    new GMMOption(AttenRelRef.CB_2014)
		};
	
	private static final IMTOption[] ALLOWED_IMTS = {
		    new IMTOption(IMT.PGA, null),
		    new IMTOption(IMT.PGV, null),
		    new IMTOption(IMT.SA, 0.3),
		    new IMTOption(IMT.SA, 1.0),
		    new IMTOption(IMT.SA, 2.0)
		};


    public PBRAppUI() {
        super("Precarious Balanced Rock: Hazard Adjustment Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildMainPanel(), BorderLayout.CENTER);
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        // LEFT: Plot
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.6;
        gbc.weighty = 0.7;
        panel.add(createPlotPanel(), gbc);

        // LEFT: Results
        gbc.gridy = 1;
        gbc.weighty = 0.3;
        panel.add(createResultsPanel(), gbc);

        // RIGHT: Controls (scrollable)
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0.4;
        gbc.weighty = 1.0;
        panel.add(buildControlPanel(), gbc);

        return panel;
    }

    private JScrollPane buildControlPanel() {
    	JPanel container = new JPanel();
    	container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0;
        formPanel.add(createSitePanel(), gbc);

        gbc.gridy++;
        formPanel.add(createHazardPanel(), gbc);

        gbc.gridy++;
        formPanel.add(createFragilityPanel(), gbc);

        gbc.gridy++;
        formPanel.add(createProbabilityPanel(), gbc);

        // push everything up
        gbc.gridy++;
        gbc.weighty = 1;
        formPanel.add(Box.createVerticalGlue(), gbc);
        
        // Progress panel (above buttons)
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressLabel = new JLabel("Idle");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);

        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 45)
        );
        progressBar.setPreferredSize(new Dimension(200, 18));
        progressLabel.setBorder(
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
        );

        // Bottom button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton calc = new JButton("Calculate");
        calc.addActionListener(e -> runCalculation());
        buttonPanel.add(calc);
        
        JButton clear = new JButton("Clear Results");
        clear.addActionListener(e -> {
            resultsText.setText("");
            plotPanel.clear();
        });
        buttonPanel.add(clear);

        container.add(formPanel);
        container.add(Box.createVerticalStrut(5));
        container.add(progressPanel);
        container.add(Box.createVerticalStrut(5));
        container.add(buttonPanel);

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(10);

        return scroll;
    }

    // ---------- Panels ----------

    private JPanel createPlotPanel() {
        plotPanel = new PlotPanel();
        plotPanel.setBorder(new TitledBorder(new EtchedBorder(), "Results Plot"));
        return plotPanel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(new EtchedBorder(), "Results"));

        resultsText = new JTextArea();
        resultsText.setEditable(false);
        resultsText.setLineWrap(true);
        resultsText.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(resultsText);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSitePanel() {
        JPanel panel = createSection("Site");
        
        latField = new JTextField("34.18", 10);
        lonField = new JTextField("-117.31", 10);
        vs30Field = new JTextField("760", 10);

        panel.add(new JLabel("Lat:"), labelGbc(0));
        panel.add(latField, fieldGbc(0));

        panel.add(new JLabel("Lon:"), labelGbc(1));
        panel.add(lonField, fieldGbc(1));

        panel.add(new JLabel("Vs30:"), labelGbc(2));
        panel.add(vs30Field, fieldGbc(2));

        return panel;
    }

    private JPanel createHazardPanel() {
        JPanel panel = createSection("Hazard");
        
        // ERF dropdown
        erfCombo = new JComboBox<>(ALLOWED_ERFS);

        // GMM dropdown
        gmmCombo = new JComboBox<>(ALLOWED_GMMS);

        panel.add(new JLabel("ERF:"), labelGbc(0));
        panel.add(erfCombo, fieldGbc(0));

        panel.add(new JLabel("GMM:"), labelGbc(1));
        panel.add(gmmCombo, fieldGbc(1));

        return panel;
    }

    private JPanel createFragilityPanel() {
        JPanel panel = createSection("Fragility");
        
        ageField = new JTextField("16", 10);
        medianField = new JTextField("1", 10);
        betaField = new JTextField("0.5", 10);
        
        imtCombo = new JComboBox<>(ALLOWED_IMTS);

        imtCombo.addActionListener(e -> {
            IMTOption sel = (IMTOption) imtCombo.getSelectedItem();
        });
        

        panel.add(new JLabel("Age (ka):"), labelGbc(0));
        panel.add(ageField, fieldGbc(0));

        panel.add(new JLabel("Median:"), labelGbc(1));
        panel.add(medianField, fieldGbc(1));

        panel.add(new JLabel("LogStdDev:"), labelGbc(2));
        panel.add(betaField, fieldGbc(2));

        panel.add(new JLabel("IMT:"), labelGbc(3));
        panel.add(imtCombo, fieldGbc(3));

        return panel;
    }

    private JPanel createProbabilityPanel() {
        JPanel panel = createSection("Probability of Survival");
        
        probField = new JTextField("0.05",10);

        panel.add(new JLabel("Target Probability:"), labelGbc(0));
        panel.add(probField, fieldGbc(0));

        return panel;
    }

    private JPanel createSection(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(new EtchedBorder(), title));
        panel.setOpaque(false);
        return panel;
    }

    // ---------- Layout Helpers ----------

    private GridBagConstraints defaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3); // tighter OpenSHA-like spacing
        gbc.fill = GridBagConstraints.BOTH;
        return gbc;
    }

    private GridBagConstraints labelGbc(int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(2, 2, 2, 4);
        return gbc;
    }

    private GridBagConstraints fieldGbc(int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = y;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(2, 2, 2, 2);
        return gbc;
    }

    // ---------- Main ----------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PBRAppUI().setVisible(true);
        });
    }
    
    
    // --------- collect inputs and run ----------------
    private void runCalculation() {

        plotPanel.clear();
        
        setStage(Stage.IDLE);
        setStage(Stage.PARSING_INPUTS);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {

            // store computed results as fields of the anonymous worker
            AssetHazardRecord hazard;
            PBRSurvivalAsset asset;
            double hazardFactor;

            @Override
            protected Void doInBackground() throws Exception {

                String assetID = "GUI_Asset";
                String fragilityID = "GUI_Fragility";
                
                setStage(Stage.PARSING_INPUTS);

                double lat = Double.parseDouble(latField.getText());
                double lon = Double.parseDouble(lonField.getText());
                double vs30 = Double.parseDouble(vs30Field.getText());

                double age = Double.parseDouble(ageField.getText());
                double median = Double.parseDouble(medianField.getText());
                double beta = Double.parseDouble(betaField.getText());
                double targetP = Double.parseDouble(probField.getText());

                ERFOption selectedERF = (ERFOption) erfCombo.getSelectedItem();
                GMMOption selectedGMM = (GMMOption) gmmCombo.getSelectedItem();
                IMTOption imtOption = (IMTOption) imtCombo.getSelectedItem();

                String erfClass = selectedERF.getClassName();
                AttenRelRef gmm = selectedGMM.getRef();
                String imtString = imtOption.getImtString();
                
                setStage(Stage.BUILDING_MODELS);

                FragilityModel fragility =
                        FragilityBuilder.component(fragilityID)
                                .imtString(imtString)
                                .lognormal(LimitState.TOPPLE, median, beta)
                                .build();

                ResponseModelLibrary<FragilityModel> baseFragilityLib =
                        ResponseModelLibrary.of(List.of(fragility), new Metadata.Builder().build());

                SimpleImResponseLibrary fragilityLib =
                        SimpleImFragilityLibraryPreparer.prepare(
                                baseFragilityLib,
                                PrepareSpec.builder().build()
                        );
                
                setStage(Stage.BUILDING_PORTFOLIO);

                List<FragilityAsset> assets = List.of(new FragilityAsset(
                        assetID, lat, lon, vs30, fragilityID,
                        Map.of("age", String.valueOf(age))
                ));

                Portfolio<FragilityAsset> basePortfolio =
                        new Portfolio<>(assets, List.of(), new Metadata.Builder().build());

                RiskConvolutionPortfolio riskPortfolio =
                        new RiskConvolutionPortfolio(basePortfolio, fragilityLib);

                setStage(Stage.BUILDING_ERF);
                
                AbstractERF erf = buildERF(erfClass);
                erf.getTimeSpan().setDuration(1.0);
                erf.updateForecast();
                
                setStage(Stage.ASSESSING_RISK);

                PortfolioRiskConvolutionCalculator calculator =
                        new PortfolioRiskConvolutionCalculator(
                                riskPortfolio,
                                fragilityLib,
                                gmm,
                                erf
                        );

                riskPortfolio = calculator.computeRiskConvolution();

                setStage(Stage.POST_PROCESSING);
                
                PBRSurvivalPortfolioReporter reporter =
                        new PBRSurvivalPortfolioReporter(riskPortfolio);

                asset = reporter.getAssetByID(assetID);
                hazard = riskPortfolio.getHazardForAsset(assetID);

                hazardFactor = asset.getHazardAdjustment(targetP);

                return null;
            }

            @Override
            protected void done() {
            	
            	setStage(Stage.COMPLETE);

                // ---------------- PLOT ----------------
                plotPanel.addCurve(
                        hazard.imls,
                        hazard.poe,
                        "Computed hazard"
                );

                double[] scaled = new double[hazard.poe.length];
                for (int i = 0; i < scaled.length; i++) {
                    scaled[i] = hazard.poe[i] * hazardFactor;
                }

                plotPanel.addCurve(
                        hazard.imls,
                        scaled,
                        String.format("Adjusted hazard (×%.2e)", hazardFactor)
                );

                // ---------------- ORIGINAL LOG BLOCK (RESTORED) ----------------
                logResult(" New Calculation");
                logResult("================================");
                logResult(" Hazard");
                logResult("    ERF = " + erfCombo.getSelectedItem());
                logResult("    GMM = " + gmmCombo.getSelectedItem());
                logResult(String.format(
                        "    Lat, Lon = (%.5f, %.5f), Vs30 = %.0f",
                        Double.parseDouble(latField.getText()),
                        Double.parseDouble(lonField.getText()),
                        Double.parseDouble(vs30Field.getText())
                ));
                logResult("---------------------------------------------------------");
                logResult(" Fragility");
                logResult("    Age = " + ageField.getText() + "ka");
                logResult("    IMT = " + imtCombo.getSelectedItem());
                logResult("    Median = " + medianField.getText() + " [g]");
                logResult("    LogStdDev = " + betaField.getText());

                // ---------------- RESULTS ----------------
                logResult("---------------------------------------------------------");
                logResult(" Results");

                logResult(String.format(
                        "    Annual probability of failure = %.2e",
                        asset.getAnnualProbabilityOfFailure()
                ));

                logResult(String.format(
                        "    Probability of survival over %.0f ka = %.2e",
                        asset.getAge() / 1000,
                        asset.getProbabilityOfSurvival()
                ));

                logResult(String.format(
                        "    Hazard adjustment factor = %.2e",
                        hazardFactor
                ));
                
                logResult("");
                logResult("");
            }
        };

        worker.execute();
    }
    
    private AbstractERF buildERF(String className) throws Exception {

        Class<?> clazz = Class.forName(className);

        if (!AbstractERF.class.isAssignableFrom(clazz))
            throw new IllegalArgumentException(
                className + " is not an AbstractERF");

        return (AbstractERF)
                clazz.getDeclaredConstructor().newInstance();
    }
    
    private static class ERFOption {

        private final String className;
        private final String displayName;

        public ERFOption(String className) {
            this.className = className;
            this.displayName = className.substring(className.lastIndexOf('.') + 1);
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private static class GMMOption {

        private final AttenRelRef ref;

        public GMMOption(AttenRelRef ref) {
            this.ref = ref;
        }

        public AttenRelRef getRef() {
            return ref;
        }

        @Override
        public String toString() {
            return ref.toString();
        }
    }
    
    private static class IMTOption {

        private final IMT imt;     // e.g. "SA", "PGA"
        private final Double period;  // null unless SA

        public IMTOption(IMT imt, Double period) {
            this.imt = imt;
            this.period = period;
        }

        public Double getPeriod() {
            return period;
        }
        
        public String getImtString() {
        	return StringUtil.imtToString(imt, period);
        }
        
        @Override
        public String toString() {
            return StringUtil.imtToString(imt, period);
        }
    }
    
    
    public class FragilityAssetInput {
        public String assetID;
        public double lat;
        public double lon;
        public double vs30;
        public String responseModel;
        public double age;
    }
    
    private FragilityAsset buildFragilityAsset(FragilityAssetInput in) {

        Map<String, String> additional = new LinkedHashMap<>();
        additional.put("age", String.valueOf(in.age));

        return new FragilityAsset(
                in.assetID,
                in.lat,
                in.lon,
                in.vs30,
                in.responseModel,
                additional
        );
    }
    
    private void logResult(String text) {
        resultsText.append(text + "\n");
        resultsText.setCaretPosition(resultsText.getDocument().getLength());
    }
    
    private static class PlotPanel extends JPanel {

        // ============================================================
        // Curve container
        // ============================================================

        private static class Curve {
            final double[] x;
            final double[] y;
            final String label;

            Curve(double[] x, double[] y, String label) {
                this.x = x;
                this.y = y;
                this.label = label;
            }
        }

        private final List<Curve> curves = new ArrayList<>();

        // log10 bounds
        private double minX, maxX;
        private double minY, maxY;

        // ============================================================
        // Public API
        // ============================================================

        public void addCurve(double[] x, double[] y, String label) {
            curves.add(new Curve(x, y, label));
            recomputeBounds();
            repaint();
        }

        public void clear() {
            curves.clear();
            minX = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
            repaint();
        }

        // ============================================================
        // Compute log bounds
        // ============================================================

        private void recomputeBounds() {

            minX = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;

            for (Curve c : curves) {
                for (int i = 0; i < c.x.length; i++) {

                    double x = c.x[i];
                    double y = c.y[i];

                    if (x <= 0 || y <= 0) continue;

                    double lx = Math.log10(x);
                    double ly = Math.log10(y);

                    minX = Math.min(minX, lx);
                    maxX = Math.max(maxX, lx);
                    minY = Math.min(minY, ly);
                    maxY = Math.max(maxY, ly);
                }
            }

            if (!Double.isFinite(minX)) return;

            minX = Math.floor(minX);
            maxX = Math.ceil(maxX);
            minY = Math.floor(minY);
            maxY = Math.ceil(maxY);
        }

        // ============================================================
        // Coordinate transforms (LOG → SCREEN)
        // ============================================================

        private int xToScreen(double x, int w, int left, int right) {
            double lx = Math.log10(x);
            return (int)(left +
                    (lx - minX)/(maxX - minX)*(w - left - right));
        }

        private int yToScreen(double y, int h, int top, int bottom) {
            double ly = Math.log10(y);
            return (int)(h - bottom -
                    (ly - minY)/(maxY - minY)*(h - top - bottom));
        }

        // ============================================================
        // Painting
        // ============================================================

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (curves.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // -------- AXIS GEOMETRY (single source of truth) --------
            int left   = 75;
            int right  = 20;
            int top    = 20;
            int bottom = 55;

            drawAxes(g2, w, h, left, right, top, bottom);
            
            int plotBottom = h - bottom;
            int plotTop = top;
            
            int labelX = left + 10;
            int labelBaseY = (int) (plotBottom - 0.1 * (plotBottom - plotTop));
            
            // -------- Draw curves --------
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();
            	
        	for (int j = 0; j < curves.size(); j++) {
        	    Curve c = curves.get(j);

                g2.setColor(Color.getHSBColor(j / 10f, 0.8f, 0.9f));

                for (int i = 1; i < c.x.length; i++) {

                    if (c.x[i-1] <= 0 || c.y[i-1] <= 0) continue;
                    if (c.x[i]   <= 0 || c.y[i]   <= 0) continue;

                    int x1 = xToScreen(c.x[i-1], w, left, right);
                    int x2 = xToScreen(c.x[i],   w, left, right);

                    int y1 = yToScreen(c.y[i-1], h, top, bottom);
                    int y2 = yToScreen(c.y[i],   h, top, bottom);

                    g2.drawLine(x1, y1, x2, y2);
                }

                int labelY = labelBaseY + (j * lineHeight);
                g2.drawString(
                        c.label,
                        labelX,
                        labelY
                );

            }
        }

        // ============================================================
        // Axes + ticks
        // ============================================================

        private void drawAxes(Graphics2D g2, int w, int h,
                              int left, int right, int top, int bottom) {

            g2.setColor(Color.BLACK);

            int x0 = left;
            int y0 = h - bottom;

            // axes
            g2.drawLine(x0, y0, w - right, y0);
            g2.drawLine(x0, y0, x0, top);

            FontMetrics fm = g2.getFontMetrics();

            // ---------------- X ticks ----------------
            for (int p = (int)minX; p <= (int)maxX; p++) {

                double val = Math.pow(10, p);
                int x = xToScreen(val, w, left, right);

                g2.drawLine(x, y0, x, y0 + 5);

                String label = "10^" + p;
                int lw = fm.stringWidth(label);

                g2.drawString(label, x - lw/2, y0 + 20);
            }

            // ---------------- Y ticks (AXIS-LOCKED) ----------------
            for (int p = (int)minY; p <= (int)maxY; p++) {

                double val = Math.pow(10, p);
                int y = yToScreen(val, h, top, bottom);

                g2.drawLine(x0 - 5, y, x0, y);

                String label = "10^" + p;
                int lw = fm.stringWidth(label);

                g2.drawString(
                        label,
                        x0 - 8 - lw,
                        y + fm.getAscent()/2 - 2
                );
            }

            // axis labels
            g2.drawString("Intensity Level (g)", w/2 - 20, h - 10);

            drawRotatedYLabel(g2, "Annual Probability of Exceedance", left, top, h);
        }

        // ============================================================
        // Rotated Y label
        // ============================================================

        private void drawRotatedYLabel(Graphics2D g2,
                                       String text,
                                       int left,
                                       int top,
                                       int h) {

            AffineTransform old = g2.getTransform();

            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(text);

            g2.rotate(-Math.PI/2);

            g2.drawString(
                    text,
                    -(top + (h - top)/2 + textWidth/2),
                    left - 45
            );

            g2.setTransform(old);
        }
    }
    
    private void setStage(Stage stage) {
        progressLabel.setText(stage.label);

        switch (stage) {
            case IDLE -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
            }
            case PARSING_INPUTS -> progressBar.setIndeterminate(true);
            case BUILDING_MODELS -> progressBar.setIndeterminate(true);
            case BUILDING_PORTFOLIO -> progressBar.setIndeterminate(true);
            case BUILDING_ERF -> progressBar.setIndeterminate(true);
            case ASSESSING_RISK -> progressBar.setIndeterminate(true);
            case POST_PROCESSING -> progressBar.setIndeterminate(true);
            case COMPLETE -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
            }
        }
    }
    
    private enum Stage {
        IDLE("Idle"),
        PARSING_INPUTS("Reading inputs..."),
        BUILDING_MODELS("Building models..."),
        BUILDING_PORTFOLIO("Building portfolio..."),
        BUILDING_ERF("Building ERF..."),
        ASSESSING_RISK("Assessing risk..."),
        POST_PROCESSING("Post-processing..."),
        COMPLETE("Complete");

        final String label;

        Stage(String label) {
            this.label = label;
        }
    }

}
    

