package com.rules;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rules.LlmBehaviorSimulation.LlmProfile;
import com.rules.LlmBehaviorSimulation.LlmTickMetrics;
import com.rules.LlmBehaviorSimulation.LoopParameters;
import com.rules.LlmBehaviorSimulation.Observation;
import com.rules.NSpaceSimulation.Agent;
import com.rules.NSpaceSimulation.Parameters;
import com.rules.NSpaceSimulation.TickMetrics;

public class SimulationExperimentRunner {
    public static final int DEFAULT_TICKS = 40;
    public static final long DEFAULT_SEED = 42L;
    public static final Path DEFAULT_OUTPUT = Path.of(
        "build",
        "reports",
        "five-rules",
        "experiments.csv"
    );

    public static record ExperimentRow(
        String experiment,
        String variant,
        long seed,
        LlmTickMetrics metrics
    ) {
        public long tick() {
            return metrics.fieldMetrics().tick();
        }
    }

    private static record Scenario(
        String experiment,
        String variant,
        LlmBehaviorSimulation simulation,
        ObservationSchedule observations
    ) {}

    @FunctionalInterface
    private interface ObservationSchedule {
        Map<Integer, Observation> atTick(int tick);
    }

    private static record Options(Path output, int ticks, long seed) {
        static Options parse(String[] arguments) {
            Path output = DEFAULT_OUTPUT;
            int ticks = DEFAULT_TICKS;
            long seed = DEFAULT_SEED;
            for (int index = 0; index < arguments.length; index += 2) {
                if (index + 1 >= arguments.length) {
                    throw new IllegalArgumentException("every option requires a value");
                }
                String option = arguments[index];
                String value = arguments[index + 1];
                switch (option) {
                    case "--output" -> output = Path.of(value);
                    case "--ticks" -> ticks = Integer.parseInt(value);
                    case "--seed" -> seed = Long.parseLong(value);
                    default -> throw new IllegalArgumentException("unknown option: " + option);
                }
            }
            if (ticks <= 0) {
                throw new IllegalArgumentException("ticks must be positive");
            }
            return new Options(output, ticks, seed);
        }
    }

    public static void main(String[] arguments) throws IOException {
        Options options = Options.parse(arguments);
        SimulationExperimentRunner runner = new SimulationExperimentRunner();
        List<ExperimentRow> rows = runner.runAll(options.ticks(), options.seed());
        runner.writeCsv(options.output(), rows);
        System.out.printf(
            "Wrote %,d rows from %d scenarios to %s%n",
            rows.size(),
            scenarioCount(rows),
            options.output().toAbsolutePath()
        );
        printFinalComparisons(rows);
    }

    public List<ExperimentRow> runAll(int ticks, long seed) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive");
        }
        List<ExperimentRow> rows = new ArrayList<>();
        for (Scenario scenario : scenarios(ticks, seed)) {
            for (int tick = 1; tick <= ticks; tick++) {
                LlmTickMetrics metrics = scenario.simulation().calculateTick(
                    scenario.observations().atTick(tick)
                );
                rows.add(new ExperimentRow(
                    scenario.experiment(),
                    scenario.variant(),
                    seed,
                    metrics
                ));
            }
        }
        return List.copyOf(rows);
    }

    public void writeCsv(Path output, List<ExperimentRow> rows) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
            output,
            StandardCharsets.UTF_8
        )) {
            writer.write(csvHeader());
            writer.newLine();
            for (ExperimentRow row : rows) {
                writer.write(toCsv(row));
                writer.newLine();
            }
        }
    }

    public static String csvHeader() {
        return String.join(",",
            "experiment",
            "variant",
            "seed",
            "tick",
            "resourcePool",
            "meanPairwiseDistance",
            "spatialDispersion",
            "directedConnections",
            "meanVisibleNeighbors",
            "connectedComponents",
            "agentsNearCommons",
            "meanPersonaDrift",
            "personaDispersion",
            "meanEvidenceDeviation",
            "beliefDispersion",
            "meanMemoryUpdate",
            "activeInteractions",
            "terminatedLoops",
            "totalTerminatedLoops"
        );
    }

    public static String toCsv(ExperimentRow row) {
        LlmTickMetrics llm = row.metrics();
        TickMetrics field = llm.fieldMetrics();
        return String.join(",",
            row.experiment(),
            row.variant(),
            Long.toString(row.seed()),
            Long.toString(field.tick()),
            Double.toString(field.resourcePool()),
            Double.toString(field.meanPairwiseDistance()),
            Double.toString(field.spatialDispersion()),
            Integer.toString(field.directedConnections()),
            Double.toString(field.meanVisibleNeighbors()),
            Integer.toString(field.connectedComponents()),
            Integer.toString(field.agentsNearCommons()),
            Double.toString(llm.meanPersonaDrift()),
            Double.toString(llm.personaDispersion()),
            Double.toString(llm.meanEvidenceDeviation()),
            Double.toString(llm.beliefDispersion()),
            Double.toString(llm.meanMemoryUpdate()),
            Integer.toString(llm.activeInteractions()),
            Integer.toString(llm.terminatedLoops()),
            Integer.toString(llm.totalTerminatedLoops())
        );
    }

    private static List<Scenario> scenarios(int ticks, long seed) {
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(personaScenario("control", 0.0, ticks, seed));
        scenarios.add(personaScenario("treatment", 0.25, ticks, seed));
        scenarios.add(sycophancyScenario("control", 0.0, ticks, seed));
        scenarios.add(sycophancyScenario("treatment", 0.35, ticks, seed));
        scenarios.add(recencyScenario("control", 0.90, ticks, seed));
        scenarios.add(recencyScenario("treatment", 0.10, ticks, seed));
        scenarios.add(loopScenario("control", ticks + 1, seed));
        scenarios.add(loopScenario("treatment", 3, seed));
        scenarios.add(fieldScenario("control", false, ticks, seed));
        scenarios.add(fieldScenario("treatment", true, ticks, seed));
        return List.copyOf(scenarios);
    }

    private static Scenario personaScenario(
        String variant,
        double personaDriftRate,
        int ticks,
        long seed
    ) {
        NSpaceSimulation field = connectedStaticField(6, seed);
        Map<Integer, LlmProfile> profiles = new LinkedHashMap<>();
        for (int id = 0; id < 6; id++) {
            profiles.put(id, profile(
                id,
                id % 2 == 0 ? -1.0 : 1.0,
                personaDriftRate,
                0.01,
                0.0,
                0.0,
                1.0
            ));
        }
        return scenario(
            "persona-drift",
            variant,
            field,
            profiles,
            new LoopParameters(ticks + 1, 0.0),
            ignored -> Map.of()
        );
    }

    private static Scenario sycophancyScenario(
        String variant,
        double sycophancyRate,
        int ticks,
        long seed
    ) {
        NSpaceSimulation field = connectedStaticField(6, seed);
        Map<Integer, LlmProfile> profiles = new LinkedHashMap<>();
        for (int id = 0; id < 6; id++) {
            double evidence = id % 2 == 0 ? -1.0 : 1.0;
            profiles.put(id, profile(
                id,
                evidence,
                0.0,
                1.0,
                sycophancyRate,
                0.02,
                0.40
            ));
        }
        return scenario(
            "sycophancy",
            variant,
            field,
            profiles,
            new LoopParameters(ticks + 1, 0.0),
            ignored -> Map.of()
        );
    }

    private static Scenario recencyScenario(
        String variant,
        double memoryRetention,
        int ticks,
        long seed
    ) {
        int agents = 4;
        NSpaceSimulation field = disconnectedStaticField(agents, seed);
        Map<Integer, LlmProfile> profiles = new LinkedHashMap<>();
        for (int id = 0; id < agents; id++) {
            profiles.put(id, profile(
                id,
                0.0,
                0.0,
                1.0,
                0.0,
                0.0,
                memoryRetention
            ));
        }
        int surpriseTick = Math.max(2, ticks / 2);
        ObservationSchedule observations = tick -> observations(
            agents,
            tick == surpriseTick ? -8.0 : 1.0,
            1.0
        );
        return scenario(
            "recency",
            variant,
            field,
            profiles,
            new LoopParameters(ticks + 1, 0.0),
            observations
        );
    }

    private static Scenario loopScenario(String variant, int limit, long seed) {
        NSpaceSimulation field = connectedStaticField(2, seed);
        Map<Integer, LlmProfile> profiles = new LinkedHashMap<>();
        profiles.put(0, profile(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0));
        profiles.put(1, profile(1.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0));
        return scenario(
            "mirror-loop",
            variant,
            field,
            profiles,
            new LoopParameters(limit, 0.0),
            ignored -> Map.of()
        );
    }

    private static Scenario fieldScenario(
        String variant,
        boolean rulesEnabled,
        int ticks,
        long seed
    ) {
        List<Agent> agents = new ArrayList<>();
        double[] positions = {-1.0, -0.5, 0.5, 1.0, 6.0, 6.5};
        for (int id = 0; id < positions.length; id++) {
            agents.add(new Agent(
                id,
                new double[] {positions[id], id % 2 == 0 ? -0.25 : 0.25},
                rulesEnabled ? 0.25 : 0.0,
                rulesEnabled ? 0.10 : 0.0,
                rulesEnabled ? 2 : 0,
                rulesEnabled ? 1.0 : 0.0,
                rulesEnabled && id == 0 ? 5.0 : 1.0
            ));
        }
        Parameters parameters = new Parameters(100.0, 1.5, 1.5, 15.0, 0.1, 4.0);
        NSpaceSimulation field = new NSpaceSimulation(
            agents,
            new double[] {0.0, 0.0},
            50.0,
            parameters,
            seed
        );
        Map<Integer, LlmProfile> profiles = inertProfiles(positions.length);
        return scenario(
            "five-rule-field",
            variant,
            field,
            profiles,
            new LoopParameters(ticks + 1, 0.0),
            ignored -> Map.of()
        );
    }

    private static Scenario scenario(
        String experiment,
        String variant,
        NSpaceSimulation field,
        Map<Integer, LlmProfile> profiles,
        LoopParameters loops,
        ObservationSchedule observations
    ) {
        return new Scenario(
            experiment,
            variant,
            new LlmBehaviorSimulation(field, profiles, loops),
            observations
        );
    }

    private static NSpaceSimulation connectedStaticField(int agentCount, long seed) {
        List<Agent> agents = new ArrayList<>();
        for (int id = 0; id < agentCount; id++) {
            agents.add(new Agent(
                id,
                new double[] {id * 0.5},
                0.0,
                0.0,
                agentCount - 1,
                0.0,
                1.0
            ));
        }
        Parameters parameters = new Parameters(100.0, 0.0, 0.0, 15.0, 0.1, 10.0);
        return new NSpaceSimulation(
            agents,
            new double[] {100.0},
            100.0,
            parameters,
            seed
        );
    }

    private static NSpaceSimulation disconnectedStaticField(int agentCount, long seed) {
        List<Agent> agents = new ArrayList<>();
        for (int id = 0; id < agentCount; id++) {
            agents.add(new Agent(
                id,
                new double[] {id},
                0.0,
                0.0,
                0,
                0.0,
                1.0
            ));
        }
        Parameters parameters = new Parameters(100.0, 0.0, 0.0, 15.0, 0.1, 0.0);
        return new NSpaceSimulation(
            agents,
            new double[] {100.0},
            100.0,
            parameters,
            seed
        );
    }

    private static Map<Integer, LlmProfile> inertProfiles(int agentCount) {
        Map<Integer, LlmProfile> profiles = new LinkedHashMap<>();
        for (int id = 0; id < agentCount; id++) {
            profiles.put(id, profile(id, id, 0.0, 1.0, 0.0, 0.0, 1.0));
        }
        return profiles;
    }

    private static LlmProfile profile(
        double persona,
        double belief,
        double personaDriftRate,
        double personaAnchorStrength,
        double sycophancyRate,
        double evidenceStrength,
        double memoryRetention
    ) {
        return new LlmProfile(
            new double[] {persona},
            new double[] {belief},
            new double[] {belief},
            personaDriftRate,
            personaAnchorStrength,
            sycophancyRate,
            evidenceStrength,
            memoryRetention
        );
    }

    private static Map<Integer, Observation> observations(
        int agentCount,
        double value,
        double influence
    ) {
        Map<Integer, Observation> observations = new LinkedHashMap<>();
        for (int id = 0; id < agentCount; id++) {
            observations.put(id, new Observation(new double[] {value}, influence));
        }
        return observations;
    }

    private static int scenarioCount(List<ExperimentRow> rows) {
        return (int) rows.stream()
            .map(row -> row.experiment() + ":" + row.variant())
            .distinct()
            .count();
    }

    private static void printFinalComparisons(List<ExperimentRow> rows) {
        rows.stream()
            .filter(row -> row.tick() == rows.stream()
                .filter(candidate -> candidate.experiment().equals(row.experiment()))
                .filter(candidate -> candidate.variant().equals(row.variant()))
                .mapToLong(ExperimentRow::tick)
                .max()
                .orElseThrow())
            .forEach(row -> System.out.printf(
                "%s %-9s personaDrift=%8.4f evidenceDeviation=%8.4f "
                    + "active=%d terminated=%d resource=%8.4f%n",
                row.experiment(),
                row.variant(),
                row.metrics().meanPersonaDrift(),
                row.metrics().meanEvidenceDeviation(),
                row.metrics().activeInteractions(),
                row.metrics().totalTerminatedLoops(),
                row.metrics().fieldMetrics().resourcePool()
            ));
    }
}
