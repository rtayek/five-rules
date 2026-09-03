package com.rules;

import java.io.BufferedWriter;
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

/** Runs paired experiments over many seeded population realizations. */
public final class SimulationSweepRunner {
    public static final int DEFAULT_SEEDS = 100;
    public static final long DEFAULT_FIRST_SEED = 0L;
    public static final Path DEFAULT_OUTPUT = Path.of(
        "build", "reports", "five-rules", "seed-sweep.csv"
    );

    public static record SweepRow(
        String experiment,
        long seed,
        double control,
        double treatment,
        double effect
    ) {}

    public static record SweepSummary(
        String experiment,
        int samples,
        double meanEffect,
        double standardDeviation,
        double confidenceLow,
        double confidenceHigh,
        double minimumEffect,
        double maximumEffect,
        int positiveEffects
    ) {}

    private record OutcomeSpec(
        String experiment,
        boolean peak,
        double direction,
        ToDoubleFunction<ExperimentRow> value
    ) {}

    private record Options(Path output, int ticks, long firstSeed, int seeds) {
        static Options parse(String[] arguments) {
            Path output = DEFAULT_OUTPUT;
            int ticks = SimulationExperimentRunner.DEFAULT_TICKS;
            long firstSeed = DEFAULT_FIRST_SEED;
            int seeds = DEFAULT_SEEDS;
            for (int index = 0; index < arguments.length; index += 2) {
                if (index + 1 >= arguments.length) {
                    throw new IllegalArgumentException("every option requires a value");
                }
                switch (arguments[index]) {
                    case "--output" -> output = Path.of(arguments[index + 1]);
                    case "--ticks" -> ticks = Integer.parseInt(arguments[index + 1]);
                    case "--first-seed" -> firstSeed = Long.parseLong(arguments[index + 1]);
                    case "--seeds" -> seeds = Integer.parseInt(arguments[index + 1]);
                    default -> throw new IllegalArgumentException(
                        "unknown option: " + arguments[index]
                    );
                }
            }
            if (ticks <= 0 || seeds <= 0) {
                throw new IllegalArgumentException("ticks and seeds must be positive");
            }
            return new Options(output, ticks, firstSeed, seeds);
        }
    }

    public static void main(String[] arguments) throws IOException {
        Options options = Options.parse(arguments);
        SimulationSweepRunner runner = new SimulationSweepRunner();
        List<SweepRow> rows = runner.run(
            options.ticks(), options.firstSeed(), options.seeds()
        );
        runner.writeCsv(options.output(), rows);
        System.out.printf(
            "Wrote %,d paired outcomes from %d seeds to %s%n",
            rows.size(), options.seeds(), options.output().toAbsolutePath()
        );
        printSummaries(runner.summarize(rows));
    }

    public List<SweepRow> run(int ticks, long firstSeed, int seedCount) {
        if (ticks <= 0 || seedCount <= 0) {
            throw new IllegalArgumentException("ticks and seedCount must be positive");
        }
        List<SweepRow> sweep = new ArrayList<>(seedCount * specs().size());
        SimulationExperimentRunner experiments = new SimulationExperimentRunner();
        for (long seed = firstSeed; seed < firstSeed + seedCount; seed++) {
            List<ExperimentRow> rows = experiments.runAll(ticks, seed);
            for (OutcomeSpec spec : specs()) {
                double control = outcome(rows, spec, "control");
                double treatment = outcome(rows, spec, "treatment");
                sweep.add(new SweepRow(
                    spec.experiment(), seed, control, treatment,
                    spec.direction() * (treatment - control)
                ));
            }
        }
        return List.copyOf(sweep);
    }

    public List<SweepSummary> summarize(List<SweepRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows cannot be empty");
        }
        Map<String, List<SweepRow>> groups = new LinkedHashMap<>();
        for (SweepRow row : rows) {
            groups.computeIfAbsent(row.experiment(), ignored -> new ArrayList<>()).add(row);
        }
        List<SweepSummary> summaries = new ArrayList<>();
        groups.forEach((experiment, samples) -> summaries.add(summary(experiment, samples)));
        return List.copyOf(summaries);
    }

    public void writeCsv(Path output, List<SweepRow> rows) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
            output, StandardCharsets.UTF_8
        )) {
            writer.write("experiment,seed,control,treatment,expectedDirectionEffect");
            writer.newLine();
            for (SweepRow row : rows) {
                writer.write(String.join(",",
                    row.experiment(),
                    Long.toString(row.seed()),
                    Double.toString(row.control()),
                    Double.toString(row.treatment()),
                    Double.toString(row.effect())
                ));
                writer.newLine();
            }
        }
    }

    private static SweepSummary summary(String experiment, List<SweepRow> rows) {
        int samples = rows.size();
        double mean = rows.stream().mapToDouble(SweepRow::effect).average().orElseThrow();
        double sumSquared = rows.stream()
            .mapToDouble(row -> {
                double difference = row.effect() - mean;
                return difference * difference;
            })
            .sum();
        double deviation = samples > 1 ? Math.sqrt(sumSquared / (samples - 1)) : 0.0;
        double margin = samples > 1 ? 1.96 * deviation / Math.sqrt(samples) : 0.0;
        return new SweepSummary(
            experiment,
            samples,
            mean,
            deviation,
            mean - margin,
            mean + margin,
            rows.stream().mapToDouble(SweepRow::effect).min().orElseThrow(),
            rows.stream().mapToDouble(SweepRow::effect).max().orElseThrow(),
            (int) rows.stream().filter(row -> row.effect() > 0.0).count()
        );
    }

    private static double outcome(
        List<ExperimentRow> rows,
        OutcomeSpec spec,
        String variant
    ) {
        var matching = rows.stream()
            .filter(row -> row.experiment().equals(spec.experiment()))
            .filter(row -> row.variant().equals(variant));
        if (spec.peak()) {
            return matching.mapToDouble(spec.value()).max().orElseThrow();
        }
        return spec.value().applyAsDouble(matching.reduce((first, second) -> second)
            .orElseThrow());
    }

    private static List<OutcomeSpec> specs() {
        return List.of(
            new OutcomeSpec("persona-drift", false, 1.0,
                row -> row.metrics().meanPersonaDrift()),
            new OutcomeSpec("sycophancy", false, 1.0,
                row -> row.metrics().meanEvidenceDeviation()),
            new OutcomeSpec("recency", true, 1.0,
                row -> row.metrics().meanMemoryUpdate()),
            new OutcomeSpec("mirror-loop", false, -1.0,
                row -> row.metrics().activeInteractions()),
            new OutcomeSpec("five-rule-field", false, -1.0,
                row -> row.metrics().fieldMetrics().resourcePool())
        );
    }

    private static void printSummaries(List<SweepSummary> summaries) {
        System.out.println("Positive effect means the treatment behaved as predicted.");
        System.out.println("experiment          mean effect       95% CI         min        max   positive");
        for (SweepSummary summary : summaries) {
            System.out.printf(Locale.ROOT,
                "%-19s %11.4f  [%7.4f,%7.4f] %8.4f %8.4f  %d/%d%n",
                summary.experiment(),
                summary.meanEffect(),
                summary.confidenceLow(),
                summary.confidenceHigh(),
                summary.minimumEffect(),
                summary.maximumEffect(),
                summary.positiveEffects(),
                summary.samples()
            );
        }
    }
}
