package com.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.rules.SimulationSweepRunner.SweepRow;
import com.rules.SimulationSweepRunner.SweepSummary;

class SimulationSweepRunnerTest {
    private static final int TICKS = 10;
    private static final int SEEDS = 8;

    @Test
    void runsFivePairedOutcomesForEverySeed() {
        List<SweepRow> rows = new SimulationSweepRunner().run(TICKS, 0L, SEEDS);

        assertEquals(5 * SEEDS, rows.size());
        assertEquals(5, rows.stream().map(SweepRow::experiment).distinct().count());
        assertTrue(rows.stream().allMatch(row -> row.effect() > 0.0));
    }

    @Test
    void summarizesVariationAndConfidenceIntervals() {
        SimulationSweepRunner runner = new SimulationSweepRunner();
        List<SweepSummary> summaries = runner.summarize(runner.run(TICKS, 0L, SEEDS));

        assertEquals(5, summaries.size());
        assertTrue(summary(summaries, "persona-drift").standardDeviation() > 0.0);
        assertTrue(summary(summaries, "sycophancy").standardDeviation() > 0.0);
        assertTrue(summary(summaries, "recency").standardDeviation() > 0.0);
        assertEquals(0.0,
            summary(summaries, "mirror-loop").standardDeviation(), 0.0);
        assertEquals(0.0,
            summary(summaries, "five-rule-field").standardDeviation(), 0.0);
        assertTrue(summaries.stream().allMatch(summary ->
            summary.confidenceLow() <= summary.meanEffect()
                && summary.meanEffect() <= summary.confidenceHigh()));
    }

    @Test
    void writesOneCsvLinePerPairedOutcome() throws IOException {
        SimulationSweepRunner runner = new SimulationSweepRunner();
        List<SweepRow> rows = runner.run(TICKS, 0L, 2);
        Path output = Files.createTempFile("five-rules-sweep-", ".csv");
        try {
            runner.writeCsv(output, rows);
            assertEquals(rows.size() + 1, Files.readAllLines(output).size());
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void createsOneSwingChartForEachSweptExperiment() {
        List<SweepRow> rows = new SimulationSweepRunner().run(TICKS, 0L, 2);

        assertEquals(5, SimulationSweepViewer.createDashboard(rows).getComponentCount());
    }

    private static SweepSummary summary(
        List<SweepSummary> summaries,
        String experiment
    ) {
        return summaries.stream()
            .filter(summary -> summary.experiment().equals(experiment))
            .findFirst()
            .orElseThrow();
    }
}
