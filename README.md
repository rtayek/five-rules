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

`LlmBehaviorSimulation` composes the spatial engine with a separate LLM behavior
layer. Each LLM has an assigned persona, an evidence anchor, a current belief,
and recency-weighted memory. The layer models peer-driven persona drift,
evidence-resistant or sycophantic belief updates, and termination of connected
agent pairs whose behavior stops changing.

```java
var llmSimulation = new LlmBehaviorSimulation(
    simulation,
    profiles,
    new LlmBehaviorSimulation.LoopParameters(3, 1.0e-9)
);

LlmBehaviorSimulation.LlmTickMetrics metrics = llmSimulation.calculateTick();
```

The combined metrics keep semantic field movement distinct from persona drift,
persona diversity, belief deviation from evidence, memory updates, and stagnant
interaction termination.

Run all five paired treatment/control experiments:

```sh
./gradlew runExperiments
```

Or open the same experiment set as a native Swing dashboard:

```sh
./gradlew viewExperiments
```

Test robustness across 100 paired seeds and inspect the effect distribution:

```sh
./gradlew runSweeps
./gradlew viewSweeps
```

The sweep prints the mean expected-direction treatment effect, its sample
standard deviation, a normal-approximation 95% confidence interval, observed
range, and count of positive effects. The persona, evidence, and surprise
inputs receive small seeded perturbations; treatment and control always share
the same realization. This makes the comparisons paired while preserving the
single-seed reproducibility guarantee. The raw sweep data is written to
`build/reports/five-rules/seed-sweep.csv`.

Override the sweep size or seed range with Gradle properties:

```sh
./gradlew viewSweeps -Pseeds=250 -PfirstSeed=1000 -Pticks=60
```

The default run uses seed `42` for 40 ticks and writes both the raw data and a
self-contained visual report:

```text
build/reports/five-rules/experiments.csv
build/reports/five-rules/experiments.html
```

Override those values with Gradle properties:

```sh
./gradlew runExperiments -Pticks=100 -Pseed=7 \
    -Poutput=results.csv -Preport=results.html
```

The experiment pairs cover persona drift, sycophancy, recency response to a
surprising observation, stagnant mirror-loop termination, and the combined
five-rule spatial field. Open the HTML report in a browser to compare each
treatment/control trajectory; CSV output remains available for further analysis.

Build and test:

```sh
./gradlew test
```

Regenerate Eclipse metadata:

```sh
./gradlew eclipse
```
