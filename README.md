# five-rules

An N-dimensional agent simulation for studying proximity attraction, thermal
exploration, hierarchical influence, shared-resource depletion, and bounded
network connections.

`NSpaceSimulation(int agents, int dimensions)` uses the documented default seed
of `0`. Supply a seed explicitly to create another reproducible run:

```java
var simulation = new NSpaceSimulation(100, 16, 42L);

NSpaceSimulation.TickMetrics initial = simulation.measure();
NSpaceSimulation.TickMetrics afterOneTick = simulation.calculateTick();
List<NSpaceSimulation.Connection> topology = simulation.getConnections();
```

Each measurement reports the resource pool, mean pairwise distance, spatial
dispersion, directed connection count, mean visible neighbors, connected
components, and number of agents near the commons. A constructor accepting
explicit agents, a resource vector, parameters, and a seed supports controlled
experiments.

Build and test:

```sh
./gradlew test
```

Regenerate Eclipse metadata:

```sh
./gradlew eclipse
```
