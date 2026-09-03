plugins {
    java
    eclipse
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources {
            setSrcDirs(listOf("src"))
            exclude("**/*.java")
        }
    }
    test {
        java.setSrcDirs(listOf("tst"))
        resources {
            setSrcDirs(listOf("tst"))
            exclude("**/*.java")
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runExperiments") {
    group = "application"
    description = "Runs deterministic treatment/control experiments and writes CSV metrics."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rules.SimulationExperimentRunner")
    args(
        "--ticks", providers.gradleProperty("ticks").getOrElse("40"),
        "--seed", providers.gradleProperty("seed").getOrElse("42"),
        "--output", providers.gradleProperty("output").getOrElse(
            layout.buildDirectory.file("reports/five-rules/experiments.csv")
                .get().asFile.absolutePath
        ),
        "--report", providers.gradleProperty("report").getOrElse(
            layout.buildDirectory.file("reports/five-rules/experiments.html")
                .get().asFile.absolutePath
        )
    )
}

tasks.register<JavaExec>("viewExperiments") {
    group = "application"
    description = "Opens a native Swing treatment/control experiment dashboard."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rules.SimulationExperimentViewer")
    args(
        "--ticks", providers.gradleProperty("ticks").getOrElse("40"),
        "--seed", providers.gradleProperty("seed").getOrElse("42")
    )
}
