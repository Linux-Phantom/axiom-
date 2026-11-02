plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    val linuxTargets = listOf(linuxX64())
    linuxTargets.forEach { target ->
        target.binaries.sharedLib {
            baseName = "axiomcore"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:2.3.12")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:2.3.12")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        linuxMain.dependencies {
            implementation("io.ktor:ktor-client-curl:2.3.12")
        }
    }
}

val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")

tasks.register<JavaExec>("runAgentSimulator") {
    group = "application"
    description = "Runs simulated device scenarios through all AI agents."
    dependsOn(jvmMainCompilation.compileTaskProvider)
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("com.apple.axiom.runner.SimulatorRunnerKt")
}

tasks.register<JavaExec>("runOpenRouterSmokeTest") {
    group = "verification"
    description = "Runs a free-model OpenRouter smoke test without calling Groq."
    dependsOn(jvmMainCompilation.compileTaskProvider)
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("com.apple.axiom.runner.SimulatorRunnerKt")
    args("--openrouter-smoke")
}

tasks.register<JavaExec>("runLinuxDesktop") {
    group = "application"
    description = "Runs Axiom's Linux system-tray and floating-mascot desktop shell."
    dependsOn(jvmMainCompilation.compileTaskProvider)
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("com.apple.axiom.platform.LinuxDesktopApp")
}

tasks.register<JavaExec>("runLinuxMetricsProbe") {
    group = "verification"
    description = "Prints one read-only sample from Linux sysfs and procfs."
    dependsOn(jvmMainCompilation.compileTaskProvider)
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("com.apple.axiom.platform.LinuxMetricsProbe")
}
