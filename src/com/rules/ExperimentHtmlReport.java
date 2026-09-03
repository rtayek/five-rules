package com.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.rules.SimulationExperimentRunner.ExperimentRow;

/** Writes a dependency-free visual report for the treatment/control experiments. */
public final class ExperimentHtmlReport {
    private static final int WIDTH = 760;
    private static final int HEIGHT = 270;
    private static final int LEFT = 68;
    private static final int RIGHT = 24;
    private static final int TOP = 22;
    private static final int BOTTOM = 48;

    private static final Map<String, ChartSpec> CHARTS = charts();

    private record ChartSpec(
        String title,
        String measure,
        String interpretation,
        boolean summarizePeak,
        ToDoubleFunction<ExperimentRow> value
    ) {}

    public void write(Path output, List<ExperimentRow> rows) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, render(rows), StandardCharsets.UTF_8);
    }

    public String render(List<ExperimentRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows cannot be empty");
        }
        Map<String, List<ExperimentRow>> byExperiment = new LinkedHashMap<>();
        for (ExperimentRow row : rows) {
            byExperiment.computeIfAbsent(row.experiment(), ignored -> new ArrayList<>())
                .add(row);
        }

        StringBuilder html = new StringBuilder();
        html.append("""
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Five Rules experiment report</title>
              <style>
                :root { color-scheme: light dark; --control:#2563eb; --treatment:#ea580c;
                  --grid:#94a3b8; --surface:#ffffff; --ink:#172033; --muted:#526076; }
                @media (prefers-color-scheme:dark) { :root { --control:#60a5fa;
                  --treatment:#fb923c; --grid:#64748b; --surface:#111827;
                  --ink:#e5e7eb; --muted:#aeb8c8; } }
                * { box-sizing:border-box; }
                body { max-width:960px; margin:0 auto; padding:28px 20px 48px;
                  background:var(--surface); color:var(--ink);
                  font:15px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif; }
                h1 { margin:0 0 6px; font-size:1.7rem; }
                .lede { color:var(--muted); margin:0 0 24px; }
                section { margin:28px 0 36px; }
                h2 { margin:0; font-size:1.2rem; }
                .interpretation { color:var(--muted); margin:4px 0 10px; }
                svg { display:block; width:100%; height:auto; overflow:visible; }
                .frame,.grid { fill:none; stroke:var(--grid); }
                .grid { opacity:.28; }
                .axis-label,.tick,.end-label { fill:var(--ink); font-size:12px; }
                .axis-label { font-weight:600; }
                .control { fill:none; stroke:var(--control); stroke-width:2.5; }
                .treatment { fill:none; stroke:var(--treatment); stroke-width:2.5; }
                .endpoint.control { fill:var(--control); stroke:none; }
                .endpoint.treatment { fill:var(--treatment); stroke:none; }
                .legend { display:flex; gap:20px; margin:0 0 2px 68px; }
                .swatch { width:18px; height:3px; display:inline-block; margin:0 7px 3px 0; }
                .swatch.control { background:var(--control); }
                .swatch.treatment { background:var(--treatment); }
                table { border-collapse:collapse; width:100%; margin-top:12px; }
                th,td { border-bottom:1px solid color-mix(in srgb,var(--grid) 35%,transparent);
                  padding:7px 8px; text-align:right; }
                th:first-child,td:first-child { text-align:left; }
                th { color:var(--muted); font-weight:600; }
                @media (max-width:520px) { body { padding:18px 12px 32px; }
                  .legend { margin-left:0; } .end-label { display:none; } }
              </style>
            </head>
            <body>
              <h1>Five Rules treatment/control experiments</h1>
              <p class="lede">Lines show the complete run. A useful experiment separates the orange treatment from the blue control in the predicted direction.</p>
            """);

        for (Map.Entry<String, List<ExperimentRow>> entry : byExperiment.entrySet()) {
            ChartSpec spec = CHARTS.get(entry.getKey());
            if (spec != null) {
                html.append(renderChart(entry.getKey(), entry.getValue(), spec));
            }
        }
        html.append(renderFinalTable(byExperiment));
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static String renderChart(
        String experiment,
        List<ExperimentRow> rows,
        ChartSpec spec
    ) {
        List<ExperimentRow> control = variant(rows, "control");
        List<ExperimentRow> treatment = variant(rows, "treatment");
        long maxTick = rows.stream().mapToLong(ExperimentRow::tick).max().orElseThrow();
        double maximum = rows.stream().mapToDouble(spec.value()).max().orElse(1.0);
        double minimum = Math.min(0.0, rows.stream().mapToDouble(spec.value()).min().orElse(0.0));
        if (maximum == minimum) {
            maximum = minimum + 1.0;
        }
        double padding = (maximum - minimum) * 0.08;
        double yMaximum = maximum + padding;
        double yMinimum = minimum;

        StringBuilder chart = new StringBuilder();
        chart.append("<section id=\"").append(experiment).append("\">\n")
            .append("<h2>").append(spec.title()).append("</h2>\n")
            .append("<p class=\"interpretation\">").append(spec.interpretation())
            .append("</p>\n")
            .append("<div class=\"legend\"><span><i class=\"swatch control\"></i>Control</span>")
            .append("<span><i class=\"swatch treatment\"></i>Treatment</span></div>\n")
            .append("<svg viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
            .append("\" role=\"img\" aria-label=\"").append(spec.title())
            .append(" over simulation ticks\">\n")
            .append("<rect class=\"frame\" x=\"").append(LEFT).append("\" y=\"")
            .append(TOP).append("\" width=\"").append(WIDTH - LEFT - RIGHT)
            .append("\" height=\"").append(HEIGHT - TOP - BOTTOM).append("\"/>\n");

        for (int index = 0; index <= 4; index++) {
            double ratio = index / 4.0;
            double y = TOP + ratio * (HEIGHT - TOP - BOTTOM);
            double value = yMaximum - ratio * (yMaximum - yMinimum);
            chart.append("<line class=\"grid\" x1=\"").append(LEFT).append("\" y1=\"")
                .append(format(y)).append("\" x2=\"").append(WIDTH - RIGHT)
                .append("\" y2=\"").append(format(y)).append("\"/>\n")
                .append("<text class=\"tick\" x=\"").append(LEFT - 8).append("\" y=\"")
                .append(format(y + 4)).append("\" text-anchor=\"end\">")
                .append(format(value)).append("</text>\n");
        }
        chart.append("<text class=\"tick\" x=\"").append(LEFT).append("\" y=\"")
            .append(HEIGHT - 20).append("\">1</text>\n")
            .append("<text class=\"tick\" x=\"").append(WIDTH - RIGHT).append("\" y=\"")
            .append(HEIGHT - 20).append("\" text-anchor=\"end\">").append(maxTick)
            .append("</text>\n")
            .append("<text class=\"axis-label\" x=\"").append((LEFT + WIDTH - RIGHT) / 2)
            .append("\" y=\"").append(HEIGHT - 3)
            .append("\" text-anchor=\"middle\">Tick</text>\n")
            .append("<text class=\"axis-label\" transform=\"translate(15 ")
            .append((TOP + HEIGHT - BOTTOM) / 2)
            .append(") rotate(-90)\" text-anchor=\"middle\">")
            .append(spec.measure()).append("</text>\n");

        chart.append(series(control, "control", spec.value(), maxTick, yMinimum, yMaximum));
        chart.append(series(treatment, "treatment", spec.value(), maxTick, yMinimum, yMaximum));
        chart.append("</svg>\n</section>\n");
        return chart.toString();
    }

    private static String series(
        List<ExperimentRow> rows,
        String cssClass,
        ToDoubleFunction<ExperimentRow> value,
        long maxTick,
        double yMinimum,
        double yMaximum
    ) {
        StringBuilder points = new StringBuilder();
        for (ExperimentRow row : rows) {
            if (!points.isEmpty()) {
                points.append(' ');
            }
            points.append(format(x(row.tick(), maxTick))).append(',')
                .append(format(y(value.applyAsDouble(row), yMinimum, yMaximum)));
        }
        ExperimentRow last = rows.get(rows.size() - 1);
        double lastX = x(last.tick(), maxTick);
        double lastY = y(value.applyAsDouble(last), yMinimum, yMaximum);
        return "<polyline class=\"" + cssClass + "\" points=\"" + points + "\"/>\n"
            + "<circle class=\"endpoint " + cssClass + "\" cx=\"" + format(lastX)
            + "\" cy=\"" + format(lastY) + "\" r=\"4\"/>\n"
            + "<text class=\"end-label\" x=\"" + format(lastX - 8) + "\" y=\""
            + format(lastY - 8) + "\" text-anchor=\"end\">"
            + format(value.applyAsDouble(last)) + "</text>\n";
    }

    private static String renderFinalTable(Map<String, List<ExperimentRow>> experiments) {
        StringBuilder table = new StringBuilder("""
            <section>
            <h2>Key measured contrasts</h2>
            <table>
              <thead><tr><th>Experiment</th><th>Measure</th><th>Control</th><th>Treatment</th></tr></thead>
              <tbody>
            """);
        experiments.forEach((name, rows) -> {
            ChartSpec spec = CHARTS.get(name);
            if (spec == null) {
                return;
            }
            ExperimentRow control = summaryRow(variant(rows, "control"), spec);
            ExperimentRow treatment = summaryRow(variant(rows, "treatment"), spec);
            table.append("<tr><td>").append(spec.title()).append("</td><td>")
                .append(spec.measure()).append(spec.summarizePeak() ? " (peak)" : " (final)")
                .append("</td><td>")
                .append(format(spec.value().applyAsDouble(control))).append("</td><td>")
                .append(format(spec.value().applyAsDouble(treatment))).append("</td></tr>\n");
        });
        return table.append("</tbody></table>\n</section>\n").toString();
    }

    private static List<ExperimentRow> variant(List<ExperimentRow> rows, String name) {
        return rows.stream().filter(row -> row.variant().equals(name)).toList();
    }

    private static ExperimentRow last(List<ExperimentRow> rows) {
        return rows.get(rows.size() - 1);
    }

    private static ExperimentRow summaryRow(List<ExperimentRow> rows, ChartSpec spec) {
        if (!spec.summarizePeak()) {
            return last(rows);
        }
        return rows.stream()
            .max(java.util.Comparator.comparingDouble(spec.value()))
            .orElseThrow();
    }

    private static double x(long tick, long maxTick) {
        if (maxTick <= 1) {
            return LEFT;
        }
        return LEFT + (tick - 1.0) / (maxTick - 1.0) * (WIDTH - LEFT - RIGHT);
    }

    private static double y(double value, double minimum, double maximum) {
        return TOP + (maximum - value) / (maximum - minimum) * (HEIGHT - TOP - BOTTOM);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Map<String, ChartSpec> charts() {
        Map<String, ChartSpec> charts = new LinkedHashMap<>();
        charts.put("persona-drift", new ChartSpec(
            "Persona drift",
            "Mean distance from assigned persona",
            "Treatment should drift while the anchored control stays near zero.",
            false,
            row -> row.metrics().meanPersonaDrift()
        ));
        charts.put("sycophancy", new ChartSpec(
            "Sycophancy",
            "Mean deviation from evidence",
            "Treatment should move away from its evidence anchor toward peers.",
            false,
            row -> row.metrics().meanEvidenceDeviation()
        ));
        charts.put("recency", new ChartSpec(
            "Recency response",
            "Mean memory update",
            "Low-retention treatment should react more sharply to each new observation, especially the surprise.",
            true,
            row -> row.metrics().meanMemoryUpdate()
        ));
        charts.put("mirror-loop", new ChartSpec(
            "Mirror-loop termination",
            "Active interactions",
            "Treatment should terminate the stagnant pair; control should leave it active.",
            false,
            row -> row.metrics().activeInteractions()
        ));
        charts.put("five-rule-field", new ChartSpec(
            "Shared commons",
            "Resource pool",
            "With the five field rules enabled, agents reach and consume the commons.",
            false,
            row -> row.metrics().fieldMetrics().resourcePool()
        ));
        return Map.copyOf(charts);
    }
}
