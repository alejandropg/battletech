import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import java.io.File

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
    args("--headless")
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
