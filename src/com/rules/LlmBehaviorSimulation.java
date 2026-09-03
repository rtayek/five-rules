package com.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.rules.NSpaceSimulation.Agent;
import com.rules.NSpaceSimulation.Connection;
import com.rules.NSpaceSimulation.TickMetrics;

public class LlmBehaviorSimulation {
    public static final LoopParameters DEFAULT_LOOP_PARAMETERS = new LoopParameters(3, 1.0e-9);

    public static record LlmProfile(
        double[] personaAnchor,
        double[] evidenceAnchor,
        double[] initialBelief,
        double personaDriftRate,
        double personaAnchorStrength,
        double sycophancyRate,
        double evidenceStrength,
        double memoryRetention
    ) {
        public LlmProfile {
            personaAnchor = copiedVector(personaAnchor, "personaAnchor");
            evidenceAnchor = copiedVector(evidenceAnchor, "evidenceAnchor");
            initialBelief = copiedVector(initialBelief, "initialBelief");
            if (evidenceAnchor.length != initialBelief.length) {
                throw new IllegalArgumentException(
                    "evidenceAnchor and initialBelief must have equal dimensions"
                );
            }
            requireUnitInterval(personaDriftRate, "personaDriftRate");
            requireUnitInterval(personaAnchorStrength, "personaAnchorStrength");
            requireUnitInterval(sycophancyRate, "sycophancyRate");
            requireUnitInterval(evidenceStrength, "evidenceStrength");
            requireUnitInterval(memoryRetention, "memoryRetention");
        }

        @Override
        public double[] personaAnchor() {
            return Arrays.copyOf(personaAnchor, personaAnchor.length);
        }

        @Override
        public double[] evidenceAnchor() {
            return Arrays.copyOf(evidenceAnchor, evidenceAnchor.length);
        }

        @Override
        public double[] initialBelief() {
            return Arrays.copyOf(initialBelief, initialBelief.length);
        }
    }

    public static record LlmState(
        double[] currentPersona,
        double[] currentBelief,
        double[] memory
    ) {
        public LlmState {
            currentPersona = copiedVector(currentPersona, "currentPersona");
            currentBelief = copiedVector(currentBelief, "currentBelief");
            memory = copiedVector(memory, "memory");
            if (currentBelief.length != memory.length) {
                throw new IllegalArgumentException(
                    "currentBelief and memory must have equal dimensions"
                );
            }
        }

        @Override
        public double[] currentPersona() {
            return Arrays.copyOf(currentPersona, currentPersona.length);
        }

        @Override
        public double[] currentBelief() {
            return Arrays.copyOf(currentBelief, currentBelief.length);
        }

        @Override
        public double[] memory() {
            return Arrays.copyOf(memory, memory.length);
        }
    }

    public static record LoopParameters(
        int stagnantInteractionLimit,
        double stagnationTolerance
    ) {
        public LoopParameters {
            if (stagnantInteractionLimit <= 0) {
                throw new IllegalArgumentException("stagnantInteractionLimit must be positive");
            }
            if (!Double.isFinite(stagnationTolerance) || stagnationTolerance < 0.0) {
                throw new IllegalArgumentException(
                    "stagnationTolerance must be finite and not negative"
                );
            }
        }
    }

    public static record Interaction(
        int firstAgentId,
        int secondAgentId,
        int stagnantTicks
    ) {}

    public static record LlmTickMetrics(
        TickMetrics fieldMetrics,
        double meanPersonaDrift,
        double personaDispersion,
        double meanEvidenceDeviation,
        double meanMemoryUpdate,
        int activeInteractions,
        int terminatedLoops,
        int totalTerminatedLoops
    ) {}

    private static record AgentPair(int firstAgentId, int secondAgentId) {
        static AgentPair of(int leftAgentId, int rightAgentId) {
            return leftAgentId < rightAgentId
                ? new AgentPair(leftAgentId, rightAgentId)
                : new AgentPair(rightAgentId, leftAgentId);
        }
    }

    private final NSpaceSimulation socialField;
    private final Map<Integer, LlmProfile> profiles;
    private final LoopParameters loopParameters;
    private final Map<AgentPair, Integer> stagnantTicksByPair = new HashMap<>();
    private final Set<AgentPair> terminatedPairs = new HashSet<>();
    private Map<Integer, LlmState> states;

    public LlmBehaviorSimulation(
        NSpaceSimulation socialField,
        Map<Integer, LlmProfile> profiles
    ) {
        this(socialField, profiles, DEFAULT_LOOP_PARAMETERS);
    }

    public LlmBehaviorSimulation(
        NSpaceSimulation socialField,
        Map<Integer, LlmProfile> profiles,
        LoopParameters loopParameters
    ) {
        this.socialField = Objects.requireNonNull(socialField, "socialField");
        this.loopParameters = Objects.requireNonNull(loopParameters, "loopParameters");
        this.profiles = validatedProfiles(profiles);
        this.states = initialStates();
    }

    public LlmTickMetrics calculateTick() {
        TickMetrics fieldMetrics = socialField.calculateTick();
        List<Connection> activeConnections = activeConnections();
        Map<Integer, List<Integer>> neighborIdsByAgent = neighborsByAgent(activeConnections);
        Map<Integer, LlmState> previousStates = states;
        Map<Integer, LlmState> nextStates = new LinkedHashMap<>();
        double totalMemoryUpdate = 0.0;

        for (int agentId : profiles.keySet()) {
            LlmProfile profile = profiles.get(agentId);
            LlmState previous = previousStates.get(agentId);
            List<Integer> neighborIds = neighborIdsByAgent.getOrDefault(agentId, List.of());
            LlmState next = updateState(profile, previous, neighborIds, previousStates);
            nextStates.put(agentId, next);
            totalMemoryUpdate += distance(previous.memory, next.memory);
        }

        states = immutableOrderedMap(nextStates);
        int terminatedThisTick = updateLoopStates(
            undirectedPairs(activeConnections),
            previousStates,
            states
        );
        int activeInteractions = getActiveInteractions().size();
        return measure(
            fieldMetrics,
            totalMemoryUpdate / profiles.size(),
            activeInteractions,
            terminatedThisTick
        );
    }

    public LlmTickMetrics measure() {
        return measure(
            socialField.measure(),
            0.0,
            getActiveInteractions().size(),
            0
        );
    }

    public Map<Integer, LlmState> getStates() {
        return states;
    }

    public List<Interaction> getActiveInteractions() {
        List<Interaction> interactions = new ArrayList<>();
        for (AgentPair pair : undirectedPairs(activeConnections())) {
            interactions.add(new Interaction(
                pair.firstAgentId(),
                pair.secondAgentId(),
                stagnantTicksByPair.getOrDefault(pair, 0)
            ));
        }
        return List.copyOf(interactions);
    }

    public List<Interaction> getTerminatedInteractions() {
        return terminatedPairs.stream()
            .sorted((left, right) -> {
                int firstComparison = Integer.compare(
                    left.firstAgentId(),
                    right.firstAgentId()
                );
                return firstComparison != 0
                    ? firstComparison
                    : Integer.compare(left.secondAgentId(), right.secondAgentId());
            })
            .map(pair -> new Interaction(
                pair.firstAgentId(),
                pair.secondAgentId(),
                loopParameters.stagnantInteractionLimit()
            ))
            .toList();
    }

    public NSpaceSimulation getSocialField() {
        return socialField;
    }

    private Map<Integer, LlmProfile> validatedProfiles(Map<Integer, LlmProfile> suppliedProfiles) {
        Objects.requireNonNull(suppliedProfiles, "profiles");
        Set<Integer> agentIds = new HashSet<>();
        for (Agent agent : socialField.getAgents()) {
            agentIds.add(agent.id());
        }
        if (!agentIds.equals(suppliedProfiles.keySet())) {
            throw new IllegalArgumentException("profiles must contain exactly the social-field agent ids");
        }
        Map<Integer, LlmProfile> validated = new LinkedHashMap<>();
        int personaDimensions = -1;
        int beliefDimensions = -1;
        for (Agent agent : socialField.getAgents()) {
            LlmProfile profile = Objects.requireNonNull(
                suppliedProfiles.get(agent.id()),
                "profile"
            );
            if (personaDimensions < 0) {
                personaDimensions = profile.personaAnchor.length;
                beliefDimensions = profile.initialBelief.length;
            }
            if (profile.personaAnchor.length != personaDimensions) {
                throw new IllegalArgumentException("all persona vectors must have equal dimensions");
            }
            if (profile.initialBelief.length != beliefDimensions) {
                throw new IllegalArgumentException("all belief vectors must have equal dimensions");
            }
            validated.put(agent.id(), profile);
        }
        if (validated.isEmpty()) {
            throw new IllegalArgumentException("at least one LLM profile is required");
        }
        return immutableOrderedMap(validated);
    }

    private Map<Integer, LlmState> initialStates() {
        Map<Integer, LlmState> initial = new LinkedHashMap<>();
        for (Map.Entry<Integer, LlmProfile> entry : profiles.entrySet()) {
            LlmProfile profile = entry.getValue();
            initial.put(entry.getKey(), new LlmState(
                profile.personaAnchor,
                profile.initialBelief,
                profile.initialBelief
            ));
        }
        return immutableOrderedMap(initial);
    }

    private LlmState updateState(
        LlmProfile profile,
        LlmState previous,
        List<Integer> neighborIds,
        Map<Integer, LlmState> previousStates
    ) {
        double[] personaAfterPeers = previous.currentPersona;
        double[] nextMemory = previous.memory;
        double[] beliefAfterPeers = previous.currentBelief;
        if (!neighborIds.isEmpty()) {
            double[] peerPersona = meanVector(neighborIds, previousStates, true);
            double[] peerBelief = meanVector(neighborIds, previousStates, false);
            personaAfterPeers = interpolate(
                previous.currentPersona,
                peerPersona,
                profile.personaDriftRate()
            );
            nextMemory = interpolate(
                previous.memory,
                peerBelief,
                1.0 - profile.memoryRetention()
            );
            beliefAfterPeers = interpolate(
                previous.currentBelief,
                nextMemory,
                profile.sycophancyRate()
            );
        }
        double[] nextPersona = interpolate(
            personaAfterPeers,
            profile.personaAnchor,
            profile.personaAnchorStrength()
        );
        double[] nextBelief = interpolate(
            beliefAfterPeers,
            profile.evidenceAnchor,
            profile.evidenceStrength()
        );
        return new LlmState(nextPersona, nextBelief, nextMemory);
    }

    private double[] meanVector(
        List<Integer> agentIds,
        Map<Integer, LlmState> sourceStates,
        boolean persona
    ) {
        double[] first = persona
            ? sourceStates.get(agentIds.get(0)).currentPersona
            : sourceStates.get(agentIds.get(0)).currentBelief;
        double[] mean = new double[first.length];
        for (int agentId : agentIds) {
            double[] vector = persona
                ? sourceStates.get(agentId).currentPersona
                : sourceStates.get(agentId).currentBelief;
            for (int dimension = 0; dimension < mean.length; dimension++) {
                mean[dimension] += vector[dimension];
            }
        }
        for (int dimension = 0; dimension < mean.length; dimension++) {
            mean[dimension] /= agentIds.size();
        }
        return mean;
    }

    private List<Connection> activeConnections() {
        return socialField.getConnections().stream()
            .filter(connection -> !terminatedPairs.contains(AgentPair.of(
                connection.sourceId(),
                connection.targetId()
            )))
            .toList();
    }

    private static Map<Integer, List<Integer>> neighborsByAgent(
        List<Connection> connections
    ) {
        Map<Integer, List<Integer>> neighbors = new HashMap<>();
        for (Connection connection : connections) {
            neighbors.computeIfAbsent(connection.sourceId(), ignored -> new ArrayList<>())
                .add(connection.targetId());
        }
        return neighbors;
    }

    private static List<AgentPair> undirectedPairs(List<Connection> connections) {
        Set<AgentPair> pairs = new HashSet<>();
        for (Connection connection : connections) {
            pairs.add(AgentPair.of(connection.sourceId(), connection.targetId()));
        }
        return pairs.stream()
            .sorted((left, right) -> {
                int firstComparison = Integer.compare(
                    left.firstAgentId(),
                    right.firstAgentId()
                );
                return firstComparison != 0
                    ? firstComparison
                    : Integer.compare(left.secondAgentId(), right.secondAgentId());
            })
            .toList();
    }

    private int updateLoopStates(
        List<AgentPair> activePairs,
        Map<Integer, LlmState> previousStates,
        Map<Integer, LlmState> nextStates
    ) {
        Set<AgentPair> currentlyConnected = Set.copyOf(activePairs);
        stagnantTicksByPair.keySet().retainAll(currentlyConnected);
        int terminatedThisTick = 0;
        for (AgentPair pair : activePairs) {
            double firstChange = behavioralChange(
                previousStates.get(pair.firstAgentId()),
                nextStates.get(pair.firstAgentId())
            );
            double secondChange = behavioralChange(
                previousStates.get(pair.secondAgentId()),
                nextStates.get(pair.secondAgentId())
            );
            int stagnantTicks = Math.max(firstChange, secondChange)
                <= loopParameters.stagnationTolerance()
                ? stagnantTicksByPair.getOrDefault(pair, 0) + 1
                : 0;
            if (stagnantTicks >= loopParameters.stagnantInteractionLimit()) {
                terminatedPairs.add(pair);
                stagnantTicksByPair.remove(pair);
                terminatedThisTick++;
            } else {
                stagnantTicksByPair.put(pair, stagnantTicks);
            }
        }
        return terminatedThisTick;
    }

    private static double behavioralChange(LlmState previous, LlmState next) {
        return Math.max(
            distance(previous.currentPersona, next.currentPersona),
            Math.max(
                distance(previous.currentBelief, next.currentBelief),
                distance(previous.memory, next.memory)
            )
        );
    }

    private LlmTickMetrics measure(
        TickMetrics fieldMetrics,
        double meanMemoryUpdate,
        int activeInteractions,
        int terminatedThisTick
    ) {
        double personaDrift = 0.0;
        double evidenceDeviation = 0.0;
        for (int agentId : profiles.keySet()) {
            LlmProfile profile = profiles.get(agentId);
            LlmState state = states.get(agentId);
            personaDrift += distance(state.currentPersona, profile.personaAnchor);
            evidenceDeviation += distance(state.currentBelief, profile.evidenceAnchor);
        }
        return new LlmTickMetrics(
            fieldMetrics,
            personaDrift / profiles.size(),
            personaDispersion(),
            evidenceDeviation / profiles.size(),
            meanMemoryUpdate,
            activeInteractions,
            terminatedThisTick,
            terminatedPairs.size()
        );
    }

    private double personaDispersion() {
        if (states.size() < 2) {
            return 0.0;
        }
        List<LlmState> stateList = List.copyOf(states.values());
        double totalDistance = 0.0;
        int pairs = 0;
        for (int left = 0; left < stateList.size(); left++) {
            for (int right = left + 1; right < stateList.size(); right++) {
                totalDistance += distance(
                    stateList.get(left).currentPersona,
                    stateList.get(right).currentPersona
                );
                pairs++;
            }
        }
        return totalDistance / pairs;
    }

    private static double[] interpolate(double[] from, double[] to, double rate) {
        double[] result = new double[from.length];
        for (int dimension = 0; dimension < from.length; dimension++) {
            result[dimension] = from[dimension] + rate * (to[dimension] - from[dimension]);
        }
        return result;
    }

    private static double distance(double[] left, double[] right) {
        double squaredDistance = 0.0;
        for (int dimension = 0; dimension < left.length; dimension++) {
            double difference = left[dimension] - right[dimension];
            squaredDistance += difference * difference;
        }
        return Math.sqrt(squaredDistance);
    }

    private static double[] copiedVector(double[] vector, String name) {
        Objects.requireNonNull(vector, name);
        if (vector.length == 0) {
            throw new IllegalArgumentException(name + " must have at least one dimension");
        }
        double[] copy = Arrays.copyOf(vector, vector.length);
        for (double coordinate : copy) {
            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException(name + " must contain finite values");
            }
        }
        return copy;
    }

    private static void requireUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
