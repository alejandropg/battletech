plugins {
    id("battletech.kotlin-application")
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("battletech.tui.MainKt")
}

// Gradle always runs tasks in a forked JVM that has no controlling terminal,
// so Mordant's enterRawMode() cannot work here. Run the JAR directly instead:
//   ./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
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
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)

}

tasks.shadowJar {
    archiveBaseName.set("tui")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
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
