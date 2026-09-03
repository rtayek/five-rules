package com.rules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.rules.LlmBehaviorSimulation.LlmProfile;
import com.rules.LlmBehaviorSimulation.LlmTickMetrics;
import com.rules.LlmBehaviorSimulation.LoopParameters;
import com.rules.LlmBehaviorSimulation.Observation;
import com.rules.NSpaceSimulation.Agent;
import com.rules.NSpaceSimulation.Parameters;

class LlmBehaviorSimulationTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void composesWithAndAdvancesTheSocialField() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 0.0, 1.0),
            profile(1.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        );

        LlmTickMetrics metrics = simulation.calculateTick();

        assertEquals(1L, metrics.fieldMetrics().tick());
        assertEquals(1L, simulation.getSocialField().getTick());
        assertEquals(1, metrics.activeInteractions());
    }

    @Test
    void peerExposureCausesPersonaDriftAndConvergence() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.5, 0.0, 0.0, 1.0),
            profile(10.0, 10.0, 0.5, 0.0, 0.0, 1.0)
        );
        double initialDispersion = simulation.measure().personaDispersion();

        LlmTickMetrics metrics = simulation.calculateTick();

        assertArrayEquals(
            new double[] {5.0},
            simulation.getStates().get(0).currentPersona(),
            TOLERANCE
        );
        assertArrayEquals(
            new double[] {5.0},
            simulation.getStates().get(1).currentPersona(),
            TOLERANCE
        );
        assertEquals(5.0, metrics.meanPersonaDrift(), TOLERANCE);
        assertTrue(metrics.personaDispersion() < initialDispersion);
    }

    @Test
    void personaAnchorPullsAResponseBackTowardItsAssignedIdentity() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 1.0, 0.5, 0.0, 1.0),
            profile(10.0, 10.0, 1.0, 0.5, 0.0, 1.0)
        );

        simulation.calculateTick();

        assertArrayEquals(
            new double[] {5.0},
            simulation.getStates().get(0).currentPersona(),
            TOLERANCE
        );
        assertArrayEquals(
            new double[] {5.0},
            simulation.getStates().get(1).currentPersona(),
            TOLERANCE
        );
    }

    @Test
    void recencyWeightedMemoryUsesExponentialRetention() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 0.0, 0.75),
            profile(1.0, 8.0, 0.0, 0.0, 0.0, 1.0)
        );

        simulation.calculateTick();
        assertArrayEquals(
            new double[] {2.0},
            simulation.getStates().get(0).memory(),
            TOLERANCE
        );

        simulation.calculateTick();
        assertArrayEquals(
            new double[] {3.5},
            simulation.getStates().get(0).memory(),
            TOLERANCE
        );
    }

    @Test
    void observationInjectionUsesMemoryRetention() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 0.0, 0.75),
            profile(1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        );

        LlmTickMetrics metrics = simulation.calculateTick(Map.of(
            0,
            new Observation(new double[] {10.0}, 1.0)
        ));

        assertArrayEquals(
            new double[] {2.5},
            simulation.getStates().get(0).memory(),
            TOLERANCE
        );
        assertArrayEquals(
            new double[] {2.5},
            simulation.getStates().get(0).currentBelief(),
            TOLERANCE
        );
        assertTrue(metrics.meanMemoryUpdate() > 0.0);
    }

    @Test
    void sycophancyMovesBeliefTowardPeerDespiteConflictingEvidence() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            profile(1.0, 10.0, 0.0, 0.0, 0.0, 0.0)
        );

        LlmTickMetrics metrics = simulation.calculateTick();

        assertArrayEquals(
            new double[] {10.0},
            simulation.getStates().get(0).currentBelief(),
            TOLERANCE
        );
        assertTrue(metrics.meanEvidenceDeviation() > 0.0);
        assertTrue(metrics.beliefDispersion() < 10.0);
    }

    @Test
    void evidenceStrengthCanResistSycophancy() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0),
            profile(1.0, 10.0, 0.0, 0.0, 0.0, 0.0)
        );

        simulation.calculateTick();

        assertArrayEquals(
            new double[] {0.0},
            simulation.getStates().get(0).currentBelief(),
            TOLERANCE
        );
    }

    @Test
    void stagnantInteractionTerminatesAtItsBudget() {
        LoopParameters loops = new LoopParameters(2, 0.0);
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 0.0, 1.0),
            profile(1.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            loops
        );

        LlmTickMetrics first = simulation.calculateTick();
        LlmTickMetrics second = simulation.calculateTick();

        assertEquals(1, first.activeInteractions());
        assertEquals(0, first.terminatedLoops());
        assertEquals(0, second.activeInteractions());
        assertEquals(1, second.terminatedLoops());
        assertEquals(1, second.totalTerminatedLoops());
        assertEquals(1, simulation.getTerminatedInteractions().size());
    }

    @Test
    void stateSnapshotsAreDefensiveAndReadOnly() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 0.0, 1.0),
            profile(1.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        );
        double[] persona = simulation.getStates().get(0).currentPersona();
        persona[0] = 99.0;

        assertArrayEquals(
            new double[] {0.0},
            simulation.getStates().get(0).currentPersona(),
            TOLERANCE
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> simulation.getStates().clear()
        );
    }

    @Test
    void profilesMustMatchTheSocialFieldAgents() {
        NSpaceSimulation field = socialField();

        assertThrows(
            IllegalArgumentException.class,
            () -> new LlmBehaviorSimulation(field, Map.of(0, profile(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0
            )))
        );
    }

    @Test
    void observationsMustMatchKnownAgentsAndBeliefDimensions() {
        LlmBehaviorSimulation simulation = twoAgentSimulation(
            profile(0.0, 0.0, 0.0, 0.0, 0.0, 1.0),
            profile(1.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> simulation.calculateTick(Map.of(
                99,
                new Observation(new double[] {1.0}, 1.0)
            ))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> simulation.calculateTick(Map.of(
                0,
                new Observation(new double[] {1.0, 2.0}, 1.0)
            ))
        );
    }

    private static LlmBehaviorSimulation twoAgentSimulation(
        LlmProfile first,
        LlmProfile second
    ) {
        return twoAgentSimulation(
            first,
            second,
            LlmBehaviorSimulation.DEFAULT_LOOP_PARAMETERS
        );
    }

    private static LlmBehaviorSimulation twoAgentSimulation(
        LlmProfile first,
        LlmProfile second,
        LoopParameters loops
    ) {
        Map<Integer, LlmProfile> profiles = new LinkedHashMap<>();
        profiles.put(0, first);
        profiles.put(1, second);
        return new LlmBehaviorSimulation(socialField(), profiles, loops);
    }

    private static NSpaceSimulation socialField() {
        Parameters parameters = new Parameters(100.0, 0.0, 0.0, 15.0, 0.1, 10.0);
        return new NSpaceSimulation(
            List.of(
                new Agent(0, new double[] {0.0}, 0.0, 0.0, 1, 0.0, 1.0),
                new Agent(1, new double[] {1.0}, 0.0, 0.0, 1, 0.0, 1.0)
            ),
            new double[] {100.0},
            100.0,
            parameters,
            42L
        );
    }

    private static LlmProfile profile(
        double persona,
        double belief,
        double personaDriftRate,
        double personaAnchorStrength,
        double sycophancyRate,
        double memoryRetention
    ) {
        return profile(
            persona,
            belief,
            personaDriftRate,
            personaAnchorStrength,
            sycophancyRate,
            memoryRetention,
            0.0
        );
    }

    private static LlmProfile profile(
        double persona,
        double belief,
        double personaDriftRate,
        double personaAnchorStrength,
        double sycophancyRate,
        double memoryRetention,
        double evidenceStrength
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
}
