package com.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.rules.SimulationExperimentRunner.ExperimentRow;

class SimulationExperimentRunnerTest {
    private static final int TICKS = 10;
    private static final long SEED = 42L;

    @Test
    void runsTenDeterministicTreatmentControlScenarios() {
        List<ExperimentRow> rows = new SimulationExperimentRunner().runAll(TICKS, SEED);

        assertEquals(10 * TICKS, rows.size());
        assertEquals(10, rows.stream()
            .map(row -> row.experiment() + ":" + row.variant())
            .distinct()
            .count());
        assertTrue(rows.stream().allMatch(row -> row.seed() == SEED));
    }

    @Test
    void treatmentsProduceTheirExpectedContrasts() {
        List<ExperimentRow> rows = new SimulationExperimentRunner().runAll(TICKS, SEED);

        ExperimentRow personaControl = finalRow(rows, "persona-drift", "control");
        ExperimentRow personaTreatment = finalRow(rows, "persona-drift", "treatment");
        assertTrue(personaTreatment.metrics().meanPersonaDrift()
            > personaControl.metrics().meanPersonaDrift());
        assertTrue(personaTreatment.metrics().personaDispersion()
            < personaControl.metrics().personaDispersion());

        ExperimentRow sycophancyControl = finalRow(rows, "sycophancy", "control");
        ExperimentRow sycophancyTreatment = finalRow(rows, "sycophancy", "treatment");
        assertTrue(sycophancyTreatment.metrics().meanEvidenceDeviation()
            > sycophancyControl.metrics().meanEvidenceDeviation());
        assertTrue(sycophancyTreatment.metrics().beliefDispersion()
            < sycophancyControl.metrics().beliefDispersion());

        ExperimentRow loopControl = finalRow(rows, "mirror-loop", "control");
        ExperimentRow loopTreatment = finalRow(rows, "mirror-loop", "treatment");
        assertEquals(1, loopControl.metrics().activeInteractions());
        assertEquals(0, loopTreatment.metrics().activeInteractions());
        assertEquals(1, loopTreatment.metrics().totalTerminatedLoops());

        ExperimentRow fieldControl = finalRow(rows, "five-rule-field", "control");
        ExperimentRow fieldTreatment = finalRow(rows, "five-rule-field", "treatment");
        assertTrue(fieldTreatment.metrics().fieldMetrics().resourcePool()
            < fieldControl.metrics().fieldMetrics().resourcePool());
    }

    @Test
    void lowRetentionRespondsMoreStronglyToTheSurprise() {
        List<ExperimentRow> rows = new SimulationExperimentRunner().runAll(TICKS, SEED);
        long surpriseTick = TICKS / 2;
        ExperimentRow control = rowAt(rows, "recency", "control", surpriseTick);
        ExperimentRow treatment = rowAt(rows, "recency", "treatment", surpriseTick);

        assertTrue(treatment.metrics().meanMemoryUpdate()
            > control.metrics().meanMemoryUpdate());
    }

    @Test
    void writesOneCsvLinePerResult() throws IOException {
        SimulationExperimentRunner runner = new SimulationExperimentRunner();
        List<ExperimentRow> rows = runner.runAll(2, SEED);
        Path output = Files.createTempFile("five-rules-experiments-", ".csv");
        try {
            runner.writeCsv(output, rows);
            List<String> lines = Files.readAllLines(output);

            assertEquals(SimulationExperimentRunner.csvHeader(), lines.get(0));
            assertEquals(rows.size() + 1, lines.size());
            assertEquals(19, lines.get(1).split(",", -1).length);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void writesSelfContainedVisualReport() throws IOException {
        List<ExperimentRow> rows = new SimulationExperimentRunner().runAll(4, SEED);
        Path output = Files.createTempFile("five-rules-experiments-", ".html");
        try {
            new ExperimentHtmlReport().write(output, rows);
            String html = Files.readString(output);

            assertTrue(html.contains("<svg"));
            assertTrue(html.contains("Persona drift"));
            assertTrue(html.contains("Sycophancy"));
            assertTrue(html.contains("Recency response"));
            assertTrue(html.contains("Mirror-loop termination"));
            assertTrue(html.contains("Shared commons"));
            assertTrue(html.contains("Control"));
            assertTrue(html.contains("Treatment"));
            assertTrue(!html.contains("<script"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void createsOneSwingChartForEachExperiment() {
        List<ExperimentRow> rows = new SimulationExperimentRunner().runAll(4, SEED);

        assertEquals(5, SimulationExperimentViewer.createDashboard(rows).getComponentCount());
    }

    private static ExperimentRow finalRow(
        List<ExperimentRow> rows,
        String experiment,
        String variant
    ) {
        return rowAt(rows, experiment, variant, TICKS);
    }

    private static ExperimentRow rowAt(
        List<ExperimentRow> rows,
        String experiment,
        String variant,
        long tick
    ) {
        return rows.stream()
            .filter(row -> row.experiment().equals(experiment))
            .filter(row -> row.variant().equals(variant))
            .filter(row -> row.tick() == tick)
            .findFirst()
            .orElseThrow();
    }
}
