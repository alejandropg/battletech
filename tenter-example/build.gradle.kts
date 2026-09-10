import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import java.io.File
import java.io.ByteArrayOutputStream

plugins {
    id("battletech.kotlin-application")
}

application {
    mainClass.set("tenterexample.ExampleMainKt")
}

dependencies {
    implementation(project(":tenter"))
}

tasks.named<JavaExec>("run") {
    doFirst {
        error("Run :tenter-example:installDist, then launch tenter-example/build/install/tenter-example/bin/tenter-example from a terminal")
    }
}

val tenterJar = project(":tenter").tasks.named<Jar>("jar")
val tenterBuildDirectory = project(":tenter").layout.buildDirectory.get().asFile.toPath()
val exampleBuildDirectory = layout.buildDirectory.get().asFile.toPath()
val packagedDependencies = configurations.runtimeClasspath.map { resolved ->
    resolved.files.asSequence()
        .filterNot {
            it.toPath().startsWith(tenterBuildDirectory) ||
                it.toPath().startsWith(exampleBuildDirectory)
        }
        .toList()
}
val packagedSmokeDirectory = layout.buildDirectory.dir("packaged-smoke")
val preparePackagedSmoke = tasks.register<Sync>("preparePackagedSmoke") {
    from(fileTree("packaged-smoke"))
    into(packagedSmokeDirectory)
}

tasks.register<Exec>("packagedSmoke") {
    group = "verification"
    description = "Compiles and runs the example against only the packaged tenter jar and its runtime closure."
    dependsOn(preparePackagedSmoke, tenterJar)
    workingDir(packagedSmokeDirectory)
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "--no-daemon",
        "--no-configuration-cache",
        "--rerun-tasks",
        "--project-dir",
        packagedSmokeDirectory.get().asFile.absolutePath,
        "run",
        "-PtenterJar=${tenterJar.flatMap { it.archiveFile }.get().asFile.absolutePath}",
        "-PruntimeClasspath=${packagedDependencies.get().joinToString(File.pathSeparator) { it.absolutePath }}",
        "-PexampleSources=${file("src/main/kotlin").absolutePath}",
        "-PjvmVersion=${libs.versions.jvm.get()}",
    )
}

val negativeCompileDirectory = layout.buildDirectory.dir("negative-compile")
val prepareNegativeCompile = tasks.register<Sync>("prepareNegativeCompile") {
    from(fileTree("packaged-smoke"))
    into(negativeCompileDirectory)
}
val rejectRawComposition = tasks.register<Exec>("rejectRawComposition") {
    group = "verification"
    description = "Checks that an external Kotlin consumer cannot put raw views into prepared composition."
    dependsOn(prepareNegativeCompile, tenterJar)
    workingDir(negativeCompileDirectory)
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "--no-daemon", "--no-configuration-cache", "--rerun-tasks", "--console=plain",
        "--project-dir", negativeCompileDirectory.get().asFile.absolutePath,
        "compileKotlin",
        "-PtenterJar=${tenterJar.flatMap { it.archiveFile }.get().asFile.absolutePath}",
        "-PruntimeClasspath=${packagedDependencies.get().joinToString(File.pathSeparator) { it.absolutePath }}",
        "-PexampleSources=${file("compile-fail").absolutePath}",
        "-PjvmVersion=${libs.versions.jvm.get()}",
    )
    isIgnoreExitValue = true
    doFirst {
        standardOutput = ByteArrayOutputStream()
        errorOutput = standardOutput
    }
    doLast {
        val diagnostics = standardOutput.toString()
        check(executionResult.get().exitValue != 0) { "Raw composition unexpectedly compiled" }
        for (fixture in listOf("RawStack.kt", "RawColumns.kt")) {
            check(diagnostics.lineSequence().any {
                fixture in it && "Argument type mismatch" in it && "ContentView" in it
            }) { "Missing expected type rejection for $fixture:\n$diagnostics" }
        }
    }
}

tasks.named("check") { dependsOn(rejectRawComposition) }
