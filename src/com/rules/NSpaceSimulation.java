package com.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

public class NSpaceSimulation {
    public static final long DEFAULT_SEED = 0L;
    public static final Parameters DEFAULT_PARAMETERS = new Parameters(
        100.0,
        1.5,
        1.5,
        15.0,
        0.1,
        8.0
    );

    public static record Agent(
        int id,
        double[] coordinates,
        double openness,
        double temperature,
        int capacity,
        double greed,
        double hierarchy
    ) {
        public Agent {
            Objects.requireNonNull(coordinates, "coordinates");
            coordinates = Arrays.copyOf(coordinates, coordinates.length);
            requireFinite(openness, "openness");
            requireFinite(temperature, "temperature");
            requireFinite(greed, "greed");
            requireFinite(hierarchy, "hierarchy");
            if (openness < 0.0 || openness > 1.0) {
                throw new IllegalArgumentException("openness must be between 0 and 1");
            }
            if (temperature < 0.0) {
                throw new IllegalArgumentException("temperature must not be negative");
            }
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity must not be negative");
            }
            if (greed < 0.0) {
                throw new IllegalArgumentException("greed must not be negative");
            }
            if (hierarchy <= 0.0) {
                throw new IllegalArgumentException("hierarchy must be positive");
            }
            for (double coordinate : coordinates) {
                requireFinite(coordinate, "coordinate");
            }
        }

        @Override
        public double[] coordinates() {
            return Arrays.copyOf(coordinates, coordinates.length);
        }

        public int dimensions() {
            return coordinates.length;
        }

        @Override
        public String toString() {
            return "Agent(id=%d, dimensions=%d, coordinates=%s)"
                .formatted(id, dimensions(), Arrays.toString(coordinates));
        }

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
    }

    public static record Parameters(
        double maxResourceCapacity,
        double regenerationRate,
        double depletionDistance,
        double crisisThreshold,
        double crisisVelocityMultiplier,
        double metricRadius
    ) {
        public Parameters {
            requirePositive(maxResourceCapacity, "maxResourceCapacity");
            requireNotNegative(regenerationRate, "regenerationRate");
            requireNotNegative(depletionDistance, "depletionDistance");
            requireNotNegative(crisisThreshold, "crisisThreshold");
            requireNotNegative(crisisVelocityMultiplier, "crisisVelocityMultiplier");
            requireNotNegative(metricRadius, "metricRadius");
        }

        private static void requirePositive(double value, String name) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException(name + " must be finite and positive");
            }
        }

        private static void requireNotNegative(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(name + " must be finite and not negative");
            }
        }
    }

    public static record Connection(int sourceId, int targetId, double distance) {}

    public static record TickMetrics(
        long tick,
        double resourcePool,
        double meanPairwiseDistance,
        double spatialDispersion,
        int directedConnections,
        double meanVisibleNeighbors,
        int connectedComponents,
        int agentsNearCommons
    ) {}

    private static record MetricEdge(double distance, Agent targetNode) {}

    private final int numAgents;
    private final int dimensions;
    private final double[] resourceVector;
    private final Parameters parameters;
    private final RandomGenerator rng;
    private List<Agent> agents;
    private double resourcePool;
    private long tick;

    public NSpaceSimulation(int numAgents, int dimensions) {
        this(numAgents, dimensions, DEFAULT_SEED);
    }

    public NSpaceSimulation(int numAgents, int dimensions, long seed) {
        if (numAgents < 0) {
            throw new IllegalArgumentException("numAgents must not be negative");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.numAgents = numAgents;
        this.dimensions = dimensions;
        this.resourceVector = new double[dimensions];
        this.parameters = DEFAULT_PARAMETERS;
        this.rng = new Random(seed);
        this.resourcePool = parameters.maxResourceCapacity();
        this.agents = initializeAgents();
    }

    public NSpaceSimulation(
        List<Agent> initialAgents,
        double[] resourceVector,
        double initialResourcePool,
        Parameters parameters,
        long seed
    ) {
        Objects.requireNonNull(initialAgents, "initialAgents");
        Objects.requireNonNull(resourceVector, "resourceVector");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        if (resourceVector.length == 0) {
            throw new IllegalArgumentException("resourceVector must have at least one dimension");
        }
        if (!Double.isFinite(initialResourcePool)
            || initialResourcePool < 0.0
            || initialResourcePool > parameters.maxResourceCapacity()) {
            throw new IllegalArgumentException("initialResourcePool is outside resource capacity");
        }
        this.numAgents = initialAgents.size();
        this.dimensions = resourceVector.length;
        this.resourceVector = Arrays.copyOf(resourceVector, resourceVector.length);
        this.rng = new Random(seed);
        this.resourcePool = initialResourcePool;
        this.agents = validatedAgents(initialAgents);
    }

    private List<Agent> initializeAgents() {
        List<Agent> initialized = new ArrayList<>(numAgents);
        for (int i = 0; i < numAgents; i++) {
            double[] coordinates = new double[dimensions];
            for (int d = 0; d < dimensions; d++) {
                coordinates[d] = rng.nextDouble(-10.0, 10.0);
            }
            initialized.add(new Agent(
                i,
                coordinates,
                rng.nextDouble(0.1, 0.4),
                rng.nextDouble(0.1, 0.5),
                rng.nextInt(2, 5),
                rng.nextDouble(0.5, 1.5),
                i == 0 ? 5.0 : 1.0
            ));
        }
        return List.copyOf(initialized);
    }

    private List<Agent> validatedAgents(List<Agent> initialAgents) {
        List<Agent> validated = new ArrayList<>(initialAgents.size());
        Set<Integer> ids = new HashSet<>();
        for (Agent agent : initialAgents) {
            Objects.requireNonNull(agent, "agent");
            if (agent.dimensions() != dimensions) {
                throw new IllegalArgumentException("all agents must match the resource dimensions");
            }
            if (!ids.add(agent.id())) {
                throw new IllegalArgumentException("agent ids must be unique");
            }
            validated.add(agent);
        }
        return List.copyOf(validated);
    }

    public TickMetrics calculateTick() {
        resourcePool = Math.min(
            parameters.maxResourceCapacity(),
            resourcePool + parameters.regenerationRate()
        );
        double velocityMultiplier = resourcePool > parameters.crisisThreshold()
            ? 1.0
            : parameters.crisisVelocityMultiplier();
        List<Agent> nextAgents = new ArrayList<>(numAgents);
        double resourceDraw = 0.0;

        for (Agent source : agents) {
            double[] updatedCoordinates = Arrays.copyOf(source.coordinates, dimensions);
            List<MetricEdge> visibleNeighbors = evaluateMetricSilos(source);
            if (!visibleNeighbors.isEmpty()) {
                double[] gravityTarget = computeWeightedGravityTarget(visibleNeighbors);
                for (int d = 0; d < dimensions; d++) {
                    updatedCoordinates[d] += source.openness()
                        * (gravityTarget[d] - updatedCoordinates[d]);
                }
            }
            addThermalMutation(source, updatedCoordinates, velocityMultiplier);
            if (computeEuclideanDistance(updatedCoordinates, resourceVector)
                < parameters.depletionDistance()) {
                resourceDraw += source.greed();
            }
            nextAgents.add(new Agent(
                source.id(),
                updatedCoordinates,
                source.openness(),
                source.temperature(),
                source.capacity(),
                source.greed(),
                source.hierarchy()
            ));
        }

        resourcePool = Math.max(0.0, resourcePool - resourceDraw);
        agents = List.copyOf(nextAgents);
        tick++;
        return measure();
    }

    private void addThermalMutation(
        Agent source,
        double[] updatedCoordinates,
        double velocityMultiplier
    ) {
        if (source.temperature() == 0.0) {
            return;
        }
        double[] randomDirection = generateGaussianDirection();
        double magnitude = computeVectorMagnitude(randomDirection);
        if (magnitude == 0.0) {
            return;
        }
        for (int d = 0; d < dimensions; d++) {
            updatedCoordinates[d] += randomDirection[d] / magnitude
                * source.temperature()
                * velocityMultiplier;
        }
    }

    public TickMetrics measure() {
        List<Connection> connections = getConnections();
        int agentCount = agents.size();
        return new TickMetrics(
            tick,
            resourcePool,
            meanPairwiseDistance(),
            spatialDispersion(),
            connections.size(),
            agentCount == 0 ? 0.0 : (double) connections.size() / agentCount,
            countConnectedComponents(connections),
            countAgentsNearCommons()
        );
    }

    public List<Connection> getConnections() {
        List<Connection> connections = new ArrayList<>();
        for (Agent source : agents) {
            for (MetricEdge edge : evaluateMetricSilos(source)) {
                connections.add(new Connection(
                    source.id(),
                    edge.targetNode().id(),
                    edge.distance()
                ));
            }
        }
        return List.copyOf(connections);
    }

    private List<MetricEdge> evaluateMetricSilos(Agent actor) {
        List<MetricEdge> activeEdges = new ArrayList<>();
        for (Agent peer : agents) {
            if (actor.id() == peer.id()) {
                continue;
            }
            double distance = computeEuclideanDistance(actor.coordinates, peer.coordinates);
            if (distance < parameters.metricRadius()) {
                activeEdges.add(new MetricEdge(distance, peer));
            }
        }
        activeEdges.sort(
            Comparator.comparingDouble(MetricEdge::distance)
                .thenComparingInt(edge -> edge.targetNode().id())
        );
        int retainedEdges = Math.min(actor.capacity(), activeEdges.size());
        return List.copyOf(activeEdges.subList(0, retainedEdges));
    }

    private double[] computeWeightedGravityTarget(List<MetricEdge> networkSilos) {
        double[] target = new double[dimensions];
        double totalWeight = 0.0;
        for (MetricEdge edge : networkSilos) {
            Agent neighbor = edge.targetNode();
            totalWeight += neighbor.hierarchy();
            for (int d = 0; d < dimensions; d++) {
                target[d] += neighbor.coordinates[d] * neighbor.hierarchy();
            }
        }
        for (int d = 0; d < dimensions; d++) {
            target[d] /= totalWeight;
        }
        return target;
    }

    private double[] generateGaussianDirection() {
        double[] vector = new double[dimensions];
        for (int d = 0; d < dimensions; d++) {
            vector[d] = rng.nextGaussian();
        }
        return vector;
    }

    private double meanPairwiseDistance() {
        if (agents.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        int pairs = 0;
        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                total += computeEuclideanDistance(
                    agents.get(i).coordinates,
                    agents.get(j).coordinates
                );
                pairs++;
            }
        }
        return total / pairs;
    }

    private double spatialDispersion() {
        if (agents.isEmpty()) {
            return 0.0;
        }
        double[] centroid = new double[dimensions];
        for (Agent agent : agents) {
            for (int d = 0; d < dimensions; d++) {
                centroid[d] += agent.coordinates[d];
            }
        }
        for (int d = 0; d < dimensions; d++) {
            centroid[d] /= agents.size();
        }
        double squaredDistance = 0.0;
        for (Agent agent : agents) {
            double distance = computeEuclideanDistance(agent.coordinates, centroid);
            squaredDistance += distance * distance;
        }
        return Math.sqrt(squaredDistance / agents.size());
    }

    private int countAgentsNearCommons() {
        int count = 0;
        for (Agent agent : agents) {
            if (computeEuclideanDistance(agent.coordinates, resourceVector)
                < parameters.depletionDistance()) {
                count++;
            }
        }
        return count;
    }

    private int countConnectedComponents(List<Connection> connections) {
        Map<Integer, Set<Integer>> neighborsById = new HashMap<>();
        for (Agent agent : agents) {
            neighborsById.put(agent.id(), new HashSet<>());
        }
        for (Connection connection : connections) {
            neighborsById.get(connection.sourceId()).add(connection.targetId());
            neighborsById.get(connection.targetId()).add(connection.sourceId());
        }
        Set<Integer> visited = new HashSet<>();
        int components = 0;
        for (int id : neighborsById.keySet()) {
            if (!visited.add(id)) {
                continue;
            }
            components++;
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(id);
            while (!pending.isEmpty()) {
                int current = pending.removeFirst();
                for (int neighbor : neighborsById.get(current)) {
                    if (visited.add(neighbor)) {
                        pending.addLast(neighbor);
                    }
                }
            }
        }
        return components;
    }

    private static double computeEuclideanDistance(double[] left, double[] right) {
        double squaredDistance = 0.0;
        for (int i = 0; i < left.length; i++) {
            double difference = left[i] - right[i];
            squaredDistance += difference * difference;
        }
        return Math.sqrt(squaredDistance);
    }

    private static double computeVectorMagnitude(double[] vector) {
        double squaredMagnitude = 0.0;
        for (double coordinate : vector) {
            squaredMagnitude += coordinate * coordinate;
        }
        return Math.sqrt(squaredMagnitude);
    }

    public double getResourcePool() {
        return resourcePool;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public long getTick() {
        return tick;
    }
}
