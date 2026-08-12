plugins {
    id("battletech.kotlin-application")
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("battletech.tui.MainKt")
}

// See docs/tui-testing.md for why this task cannot work and what to run instead.
tasks.named<JavaExec>("run") {
    doFirst {
        throw GradleException(
            "The TUI requires a direct terminal connection that Gradle cannot provide " +
            "(Gradle always forks a separate JVM detached from the terminal).\n" +
            "Build and run the JAR directly:\n" +
            "  ./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar"
        )
    }
}

dependencies {
    implementation(project(":tactical"))
    implementation(project(":network"))
    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.shadowJar {
    archiveBaseName.set("tui")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startShadowScripts") {
    dependsOn(tasks.named("jar"))
}

val createExecutable by tasks.registering {
    group = "distribution"
    description = "Creates a self-executing tui binary (Unix/macOS)"
    dependsOn(tasks.shadowJar)
    val jarFile = tasks.shadowJar.flatMap { it.archiveFile }
    val outputFile = layout.buildDirectory.file("tui")
    inputs.file(jarFile)
    outputs.file(outputFile)
    doLast {
        val stub = "#!/bin/sh\nexec java -jar \"\$0\" \"\$@\"\n"
        outputFile.get().asFile.apply {
            writeBytes(stub.toByteArray() + jarFile.get().asFile.readBytes())
            setExecutable(true)
        }
    }
}
