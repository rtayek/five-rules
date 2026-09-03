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
import java.util.function.ToDoubleFunction;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.rules.SimulationExperimentRunner.ExperimentRow;

/** Native Swing viewer for the deterministic treatment/control experiments. */
public final class SimulationExperimentViewer {
    private static final Color CONTROL_COLOR = new Color(37, 99, 235);
    private static final Color TREATMENT_COLOR = new Color(234, 88, 12);
    private static final Map<String, ChartSpec> CHARTS = charts();

    private record ChartSpec(
        String title,
        String measure,
        String interpretation,
        ToDoubleFunction<ExperimentRow> value
    ) {}

    private SimulationExperimentViewer() {}

    public static void main(String[] arguments) {
        int ticks = SimulationExperimentRunner.DEFAULT_TICKS;
        long seed = SimulationExperimentRunner.DEFAULT_SEED;
        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 >= arguments.length) {
                throw new IllegalArgumentException("every option requires a value");
            }
            switch (arguments[index]) {
                case "--ticks" -> ticks = Integer.parseInt(arguments[index + 1]);
                case "--seed" -> seed = Long.parseLong(arguments[index + 1]);
                default -> throw new IllegalArgumentException(
                    "unknown option: " + arguments[index]
                );
            }
        }
        List<ExperimentRow> rows = new SimulationExperimentRunner().runAll(ticks, seed);
        SwingUtilities.invokeLater(() -> show(rows));
    }

    static JPanel createDashboard(List<ExperimentRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows cannot be empty");
        }
        Map<String, List<ExperimentRow>> byExperiment = new LinkedHashMap<>();
        for (ExperimentRow row : rows) {
            byExperiment.computeIfAbsent(row.experiment(), ignored -> new ArrayList<>())
                .add(row);
        }

        JPanel dashboard = new JPanel(new GridLayout(0, 2, 12, 12));
        dashboard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        byExperiment.forEach((experiment, experimentRows) -> {
            ChartSpec spec = CHARTS.get(experiment);
            if (spec != null) {
                dashboard.add(new ExperimentChart(experimentRows, spec));
            }
        });
        return dashboard;
    }

    private static void show(List<ExperimentRow> rows) {
        JFrame frame = new JFrame("Five Rules experiments");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JLabel help = new JLabel(
            "Orange dashed treatment should separate from blue control in the stated direction.",
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

    private static final class ExperimentChart extends JPanel {
        private static final int LEFT = 62;
        private static final int RIGHT = 24;
        private static final int TOP = 72;
        private static final int BOTTOM = 44;

        private final List<ExperimentRow> control;
        private final List<ExperimentRow> treatment;
        private final ChartSpec spec;
        private final long maxTick;
        private final double minimum;
        private final double maximum;

        ExperimentChart(List<ExperimentRow> rows, ChartSpec spec) {
            this.control = variant(rows, "control");
            this.treatment = variant(rows, "treatment");
            this.spec = spec;
            this.maxTick = rows.stream().mapToLong(ExperimentRow::tick).max().orElseThrow();
            double rawMinimum = Math.min(
                0.0,
                rows.stream().mapToDouble(spec.value()).min().orElse(0.0)
            );
            double rawMaximum = rows.stream().mapToDouble(spec.value()).max().orElse(1.0);
            if (rawMaximum == rawMinimum) {
                rawMaximum = rawMinimum + 1.0;
            }
            this.minimum = rawMinimum;
            this.maximum = rawMaximum + (rawMaximum - rawMinimum) * 0.08;
            setPreferredSize(new Dimension(500, 320));
            setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
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
                Color grid = new Color(
                    foreground.getRed(),
                    foreground.getGreen(),
                    foreground.getBlue(),
                    55
                );

                drawing.setColor(foreground);
                drawing.setFont(getFont().deriveFont(java.awt.Font.BOLD, 15.0f));
                drawing.drawString(spec.title(), 14, 22);
                drawing.setFont(getFont().deriveFont(12.0f));
                drawing.drawString(spec.interpretation(), 14, 42);
                drawLegend(drawing);

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
                drawing.setColor(foreground);
                drawing.drawRect(LEFT, TOP, plotWidth, plotHeight);
                drawing.drawString("1", LEFT, getHeight() - 21);
                String lastTick = Long.toString(maxTick);
                drawing.drawString(
                    lastTick,
                    LEFT + plotWidth - metrics.stringWidth(lastTick),
                    getHeight() - 21
                );
                String tickLabel = "Tick";
                drawing.drawString(
                    tickLabel,
                    LEFT + (plotWidth - metrics.stringWidth(tickLabel)) / 2,
                    getHeight() - 7
                );
                drawing.drawString(spec.measure(), LEFT, TOP - 7);

                drawSeries(drawing, control, CONTROL_COLOR, false, plotWidth, plotHeight);
                drawSeries(drawing, treatment, TREATMENT_COLOR, true, plotWidth, plotHeight);
            } finally {
                drawing.dispose();
            }
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int plotWidth = Math.max(1, getWidth() - LEFT - RIGHT);
            double ratio = Math.max(0.0, Math.min(1.0, (event.getX() - LEFT) / (double) plotWidth));
            long tick = Math.max(1, Math.round(1.0 + ratio * (maxTick - 1.0)));
            ExperimentRow controlRow = atTick(control, tick);
            ExperimentRow treatmentRow = atTick(treatment, tick);
            return String.format(
                Locale.ROOT,
                "Tick %d — control %s, treatment %s",
                tick,
                format(spec.value().applyAsDouble(controlRow)),
                format(spec.value().applyAsDouble(treatmentRow))
            );
        }

        private void drawLegend(Graphics2D drawing) {
            drawing.setFont(getFont().deriveFont(11.0f));
            drawing.setColor(CONTROL_COLOR);
            drawing.setStroke(new BasicStroke(2.5f));
            drawing.drawLine(getWidth() - 166, 19, getWidth() - 146, 19);
            drawing.setColor(getForeground());
            drawing.drawString("Control", getWidth() - 140, 23);
            drawing.setColor(TREATMENT_COLOR);
            drawing.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 1.0f, new float[] {7.0f, 5.0f}, 0.0f));
            drawing.drawLine(getWidth() - 99, 19, getWidth() - 79, 19);
            drawing.setColor(getForeground());
            drawing.drawString("Treatment", getWidth() - 73, 23);
        }

        private void drawSeries(
            Graphics2D drawing,
            List<ExperimentRow> rows,
            Color color,
            boolean dashed,
            int plotWidth,
            int plotHeight
        ) {
            drawing.setColor(color);
            drawing.setStroke(dashed
                ? new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    1.0f, new float[] {7.0f, 5.0f}, 0.0f)
                : new BasicStroke(2.5f));
            for (int index = 1; index < rows.size(); index++) {
                ExperimentRow before = rows.get(index - 1);
                ExperimentRow after = rows.get(index);
                drawing.drawLine(
                    x(before.tick(), plotWidth),
                    y(spec.value().applyAsDouble(before), plotHeight),
                    x(after.tick(), plotWidth),
                    y(spec.value().applyAsDouble(after), plotHeight)
                );
            }
        }

        private int x(long tick, int plotWidth) {
            if (maxTick <= 1) {
                return LEFT;
            }
            return LEFT + (int) Math.round((tick - 1.0) / (maxTick - 1.0) * plotWidth);
        }

        private int y(double value, int plotHeight) {
            return TOP + (int) Math.round((maximum - value) / (maximum - minimum) * plotHeight);
        }
    }

    private static List<ExperimentRow> variant(List<ExperimentRow> rows, String name) {
        return rows.stream().filter(row -> row.variant().equals(name)).toList();
    }

    private static ExperimentRow atTick(List<ExperimentRow> rows, long tick) {
        return rows.stream().filter(row -> row.tick() == tick).findFirst().orElseThrow();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Map<String, ChartSpec> charts() {
        Map<String, ChartSpec> charts = new LinkedHashMap<>();
        charts.put("persona-drift", new ChartSpec(
            "Persona drift", "Mean distance from assigned persona",
            "Drift should appear only in treatment.",
            row -> row.metrics().meanPersonaDrift()
        ));
        charts.put("sycophancy", new ChartSpec(
            "Sycophancy", "Mean deviation from evidence",
            "Treatment should move away from evidence.",
            row -> row.metrics().meanEvidenceDeviation()
        ));
        charts.put("recency", new ChartSpec(
            "Recency response", "Mean memory update",
            "Treatment should react more sharply to the surprise.",
            row -> row.metrics().meanMemoryUpdate()
        ));
        charts.put("mirror-loop", new ChartSpec(
            "Mirror-loop termination", "Active interactions",
            "Treatment should terminate the stagnant pair.",
            row -> row.metrics().activeInteractions()
        ));
        charts.put("five-rule-field", new ChartSpec(
            "Shared commons", "Resource pool",
            "Enabled rules should lead to resource use.",
            row -> row.metrics().fieldMetrics().resourcePool()
        ));
        return Map.copyOf(charts);
    }
}
