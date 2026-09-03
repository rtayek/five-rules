package com.rules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rules.NSpaceSimulation.Agent;
import com.rules.NSpaceSimulation.Connection;
import com.rules.NSpaceSimulation.Parameters;
import com.rules.NSpaceSimulation.TickMetrics;

class NSpaceSimulationTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void identicalSeedsProduceIdenticalRuns() {
        NSpaceSimulation first = new NSpaceSimulation(20, 6, 42L);
        NSpaceSimulation second = new NSpaceSimulation(20, 6, 42L);

        for (int tick = 0; tick < 25; tick++) {
            assertEquals(first.calculateTick(), second.calculateTick());
            assertSameCoordinates(first.getAgents(), second.getAgents());
        }
    }

    @Test
    void defaultConstructorUsesTheDocumentedSeed() {
        NSpaceSimulation implicitSeed = new NSpaceSimulation(5, 3);
        NSpaceSimulation explicitSeed = new NSpaceSimulation(
            5,
            3,
            NSpaceSimulation.DEFAULT_SEED
        );

        assertSameCoordinates(implicitSeed.getAgents(), explicitSeed.getAgents());
    }

    @Test
    void differentSeedsProduceDifferentInitialStates() {
        NSpaceSimulation first = new NSpaceSimulation(2, 3, 1L);
        NSpaceSimulation second = new NSpaceSimulation(2, 3, 2L);

        assertFalse(java.util.Arrays.equals(
            first.getAgents().get(0).coordinates(),
            second.getAgents().get(0).coordinates()
        ));
    }

    @Test
    void proximityAttractionReducesDispersion() {
        Parameters parameters = parameters(10.0);
        NSpaceSimulation simulation = simulation(
            List.of(
                agent(0, 0.0, 0.5, 0.0, 1, 1.0),
                agent(1, 4.0, 0.5, 0.0, 1, 1.0)
            ),
            parameters,
            100.0
        );
        double initialDispersion = simulation.measure().spatialDispersion();

        TickMetrics result = simulation.calculateTick();

        assertTrue(result.spatialDispersion() < initialDispersion);
        assertArrayEquals(new double[] {2.0}, simulation.getAgents().get(0).coordinates(), TOLERANCE);
        assertArrayEquals(new double[] {2.0}, simulation.getAgents().get(1).coordinates(), TOLERANCE);
    }

    @Test
    void hierarchyBiasesTheAttractionTarget() {
        NSpaceSimulation simulation = simulation(
            List.of(
                agent(0, 0.0, 1.0, 0.0, 2, 1.0),
                agent(1, 2.0, 0.0, 0.0, 2, 1.0),
                agent(2, 6.0, 0.0, 0.0, 2, 5.0)
            ),
            parameters(10.0),
            100.0
        );

        simulation.calculateTick();

        assertArrayEquals(
            new double[] {32.0 / 6.0},
            simulation.getAgents().get(0).coordinates(),
            TOLERANCE
        );
    }

    @Test
    void thermalMutationHasConfiguredMagnitude() {
        NSpaceSimulation simulation = simulation(
            List.of(agent(0, 0.0, 0.0, 0.75, 0, 1.0)),
            parameters(10.0),
            100.0
        );

        simulation.calculateTick();

        assertEquals(0.75, Math.abs(simulation.getAgents().get(0).coordinates()[0]), TOLERANCE);
    }

    @Test
    void crisisDampsThermalMutation() {
        NSpaceSimulation simulation = simulation(
            List.of(agent(0, 0.0, 0.0, 1.0, 0, 1.0)),
            parameters(10.0),
            10.0
        );

        simulation.calculateTick();

        assertEquals(0.1, Math.abs(simulation.getAgents().get(0).coordinates()[0]), TOLERANCE);
    }

    @Test
    void commonsRegeneratesConsumesAndNeverBecomesNegative() {
        NSpaceSimulation simulation = simulation(
            List.of(
                agent(0, 0.0, 0.0, 0.0, 0, 8.0, 1.0),
                agent(1, 0.5, 0.0, 0.0, 0, 8.0, 1.0)
            ),
            parameters(10.0),
            5.0
        );

        TickMetrics result = simulation.calculateTick();

        assertEquals(0.0, result.resourcePool(), TOLERANCE);
        assertEquals(2, result.agentsNearCommons());
    }

    @Test
    void capacityKeepsOnlyNearestNeighbors() {
        NSpaceSimulation simulation = simulation(
            List.of(
                agent(0, 0.0, 0.0, 0.0, 2, 1.0),
                agent(1, 1.0, 0.0, 0.0, 3, 1.0),
                agent(2, 2.0, 0.0, 0.0, 3, 1.0),
                agent(3, 3.0, 0.0, 0.0, 3, 1.0)
            ),
            parameters(10.0),
            100.0
        );

        List<Integer> targets = simulation.getConnections().stream()
            .filter(connection -> connection.sourceId() == 0)
            .map(Connection::targetId)
            .toList();

        assertEquals(List.of(1, 2), targets);
    }

    @Test
    void metricsReportSeparatedComponentsAndConnectionDensity() {
        NSpaceSimulation simulation = simulation(
            List.of(
                agent(0, 0.0, 0.0, 0.0, 1, 1.0),
                agent(1, 1.0, 0.0, 0.0, 1, 1.0),
                agent(2, 10.0, 0.0, 0.0, 1, 1.0),
                agent(3, 11.0, 0.0, 0.0, 1, 1.0)
            ),
            parameters(2.0),
            100.0
        );

        TickMetrics metrics = simulation.measure();

        assertEquals(4, metrics.directedConnections());
        assertEquals(1.0, metrics.meanVisibleNeighbors(), TOLERANCE);
        assertEquals(2, metrics.connectedComponents());
    }

    @Test
    void agentAndSimulationSnapshotsCannotBeMutatedByCallers() {
        double[] coordinates = {1.0, 2.0};
        Agent agent = new Agent(0, coordinates, 0.2, 0.1, 2, 1.0, 1.0);
        coordinates[0] = 99.0;
        double[] returnedCoordinates = agent.coordinates();
        returnedCoordinates[0] = 88.0;

        assertArrayEquals(new double[] {1.0, 2.0}, agent.coordinates());
        NSpaceSimulation simulation = new NSpaceSimulation(1, 2, 1L);
        assertThrows(UnsupportedOperationException.class, () -> simulation.getAgents().clear());
    }

    private static NSpaceSimulation simulation(
        List<Agent> agents,
        Parameters parameters,
        double resourcePool
    ) {
        return new NSpaceSimulation(agents, new double[] {0.0}, resourcePool, parameters, 42L);
    }

    private static Parameters parameters(double metricRadius) {
        return new Parameters(100.0, 1.5, 1.5, 15.0, 0.1, metricRadius);
    }

    private static Agent agent(
        int id,
        double coordinate,
        double openness,
        double temperature,
        int capacity,
        double hierarchy
    ) {
        return agent(id, coordinate, openness, temperature, capacity, 0.0, hierarchy);
    }

    private static Agent agent(
        int id,
        double coordinate,
        double openness,
        double temperature,
        int capacity,
        double greed,
        double hierarchy
    ) {
        return new Agent(
            id,
            new double[] {coordinate},
            openness,
            temperature,
            capacity,
            greed,
            hierarchy
        );
    }

    private static void assertSameCoordinates(List<Agent> first, List<Agent> second) {
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertArrayEquals(first.get(i).coordinates(), second.get(i).coordinates());
        }
    }
}
