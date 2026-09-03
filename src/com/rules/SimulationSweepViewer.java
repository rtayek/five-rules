package com.rules;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.rules.SimulationSweepRunner.SweepRow;
import com.rules.SimulationSweepRunner.SweepSummary;

/** Native Swing viewer for multi-seed treatment-effect sweeps. */
public final class SimulationSweepViewer {
    private static final Color SAMPLE_COLOR = new Color(234, 88, 12);
    private static final Color MEAN_COLOR = new Color(37, 99, 235);
    private static final Map<String, String> TITLES = Map.of(
        "persona-drift", "Persona drift",
        "sycophancy", "Sycophancy",
        "recency", "Recency response",
        "mirror-loop", "Mirror-loop termination",
        "five-rule-field", "Shared commons"
    );

    private SimulationSweepViewer() {}

    public static void main(String[] arguments) {
        int ticks = SimulationExperimentRunner.DEFAULT_TICKS;
        long firstSeed = SimulationSweepRunner.DEFAULT_FIRST_SEED;
        int seeds = SimulationSweepRunner.DEFAULT_SEEDS;
        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 >= arguments.length) {
                throw new IllegalArgumentException("every option requires a value");
            }
            switch (arguments[index]) {
                case "--ticks" -> ticks = Integer.parseInt(arguments[index + 1]);
                case "--first-seed" -> firstSeed = Long.parseLong(arguments[index + 1]);
                case "--seeds" -> seeds = Integer.parseInt(arguments[index + 1]);
                default -> throw new IllegalArgumentException(
                    "unknown option: " + arguments[index]
                );
            }
        }
        SimulationSweepRunner runner = new SimulationSweepRunner();
        List<SweepRow> rows = runner.run(ticks, firstSeed, seeds);
        SwingUtilities.invokeLater(() -> show(rows));
    }

    static JPanel createDashboard(List<SweepRow> rows) {
        SimulationSweepRunner runner = new SimulationSweepRunner();
        Map<String, SweepSummary> summaries = new LinkedHashMap<>();
        for (SweepSummary summary : runner.summarize(rows)) {
            summaries.put(summary.experiment(), summary);
        }
        Map<String, List<SweepRow>> groups = new LinkedHashMap<>();
        for (SweepRow row : rows) {
            groups.computeIfAbsent(row.experiment(), ignored -> new ArrayList<>()).add(row);
        }

        JPanel dashboard = new JPanel(new GridLayout(0, 2, 12, 12));
        dashboard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        groups.forEach((experiment, samples) -> dashboard.add(
            new SweepChart(samples, summaries.get(experiment))
        ));
        return dashboard;
    }

    private static void show(List<SweepRow> rows) {
        JFrame frame = new JFrame("Five Rules multi-seed sweep");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        JLabel help = new JLabel(
            "Each orange point is one paired seed. Positive effects support the prediction.",
            SwingConstants.CENTER
        );
        help.setBorder(BorderFactory.createEmptyBorder(10, 8, 4, 8));
        frame.add(help, BorderLayout.NORTH);

        JPanel dashboard = createDashboard(rows);
        dashboard.setPreferredSize(new Dimension(1040, 1020));
        frame.add(new JScrollPane(dashboard), BorderLayout.CENTER);
        frame.setSize(1120, 820);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static final class SweepChart extends JPanel {
        private static final int LEFT = 62;
        private static final int RIGHT = 24;
        private static final int TOP = 78;
        private static final int BOTTOM = 44;

        private final List<SweepRow> rows;
        private final SweepSummary summary;
        private final double minimum;
        private final double maximum;

        SweepChart(List<SweepRow> rows, SweepSummary summary) {
            this.rows = List.copyOf(rows);
            this.summary = summary;
            double rawMinimum = Math.min(0.0, summary.minimumEffect());
            double rawMaximum = Math.max(0.0, summary.maximumEffect());
            if (rawMaximum == rawMinimum) {
                rawMaximum = rawMinimum + 1.0;
            }
            double padding = (rawMaximum - rawMinimum) * 0.08;
            this.minimum = rawMinimum - padding;
            this.maximum = rawMaximum + padding;
            setPreferredSize(new Dimension(500, 320));
            Color border = UIManager.getColor("Separator.foreground");
            setBorder(BorderFactory.createLineBorder(border == null ? Color.GRAY : border));
            setToolTipText("");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D drawing = (Graphics2D) graphics.create();
            try {
                drawing.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                );
                int plotWidth = Math.max(1, getWidth() - LEFT - RIGHT);
                int plotHeight = Math.max(1, getHeight() - TOP - BOTTOM);
                Color foreground = getForeground();
                Color grid = new Color(foreground.getRed(), foreground.getGreen(),
                    foreground.getBlue(), 55);

                drawing.setColor(foreground);
                drawing.setFont(getFont().deriveFont(java.awt.Font.BOLD, 15.0f));
                drawing.drawString(TITLES.get(summary.experiment()), 14, 22);
                drawing.setFont(getFont().deriveFont(12.0f));
                drawing.drawString(String.format(Locale.ROOT,
                    "mean %.3f; 95%% CI [%.3f, %.3f]; positive %d/%d",
                    summary.meanEffect(), summary.confidenceLow(),
                    summary.confidenceHigh(), summary.positiveEffects(), summary.samples()
                ), 14, 43);
                drawing.drawString("Expected-direction effect", LEFT, TOP - 8);

                drawing.setFont(getFont().deriveFont(11.0f));
                FontMetrics metrics = drawing.getFontMetrics();
                for (int index = 0; index <= 4; index++) {
                    double ratio = index / 4.0;
                    int y = TOP + (int) Math.round(ratio * plotHeight);
                    double value = maximum - ratio * (maximum - minimum);
                    drawing.setColor(grid);
                    drawing.drawLine(LEFT, y, LEFT + plotWidth, y);
                    drawing.setColor(foreground);
                    String label = format(value);
                    drawing.drawString(label, LEFT - metrics.stringWidth(label) - 7, y + 4);
                }

                int confidenceTop = y(summary.confidenceHigh(), plotHeight);
                int confidenceBottom = y(summary.confidenceLow(), plotHeight);
                drawing.setColor(new Color(MEAN_COLOR.getRed(), MEAN_COLOR.getGreen(),
                    MEAN_COLOR.getBlue(), 40));
                drawing.fillRect(LEFT, confidenceTop, plotWidth,
                    Math.max(1, confidenceBottom - confidenceTop));
                drawing.setColor(MEAN_COLOR);
                drawing.setStroke(new BasicStroke(2.0f));
                int meanY = y(summary.meanEffect(), plotHeight);
                drawing.drawLine(LEFT, meanY, LEFT + plotWidth, meanY);

                drawing.setColor(SAMPLE_COLOR);
                for (int index = 0; index < rows.size(); index++) {
                    int x = x(index, plotWidth);
                    int y = y(rows.get(index).effect(), plotHeight);
                    drawing.fillOval(x - 3, y - 3, 7, 7);
                }

                drawing.setColor(foreground);
                drawing.drawRect(LEFT, TOP, plotWidth, plotHeight);
                String first = Long.toString(rows.get(0).seed());
                String last = Long.toString(rows.get(rows.size() - 1).seed());
                drawing.drawString(first, LEFT, getHeight() - 21);
                drawing.drawString(last, LEFT + plotWidth - metrics.stringWidth(last),
                    getHeight() - 21);
                String label = "Seed";
                drawing.drawString(label,
                    LEFT + (plotWidth - metrics.stringWidth(label)) / 2,
                    getHeight() - 7);
            } finally {
                drawing.dispose();
            }
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int plotWidth = Math.max(1, getWidth() - LEFT - RIGHT);
            double ratio = Math.max(0.0,
                Math.min(1.0, (event.getX() - LEFT) / (double) plotWidth));
            int index = rows.size() == 1
                ? 0
                : (int) Math.round(ratio * (rows.size() - 1));
            SweepRow row = rows.get(index);
            return String.format(Locale.ROOT,
                "Seed %d — control %s, treatment %s, effect %s",
                row.seed(), format(row.control()), format(row.treatment()),
                format(row.effect()));
        }

        private int x(int index, int plotWidth) {
            if (rows.size() == 1) {
                return LEFT + plotWidth / 2;
            }
            return LEFT + (int) Math.round(index / (double) (rows.size() - 1) * plotWidth);
        }

        private int y(double value, int plotHeight) {
            return TOP + (int) Math.round((maximum - value) / (maximum - minimum)
                * plotHeight);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
