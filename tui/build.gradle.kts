import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("battletech.kotlin-application")
    id("battletech.kotlin-serialization")
    alias(libs.plugins.shadow)
}

// Mordant's FFM terminal backend runs only with native access enabled; without it Mordant falls back
// to JNA, whose native library load makes the JVM print restricted-method warnings at startup.
val nativeAccess = "ALL-UNNAMED"

application {
    mainClass.set("io.archinaut.battletech.tui.MainKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=$nativeAccess")
}

repositories {
    mavenLocal()
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("theme")) {
        into("theme")
        exclude(".DS_Store")
    }
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
    implementation(libs.tenter)
    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.konsist)
}

tasks.shadowJar {
    archiveBaseName.set("tui")
    archiveClassifier.set("")
    archiveVersion.set("")
    // Shadow excludes duplicate paths before transformers run, which would keep only one backend's
    // service file; include them so service files merge, and exclude every other duplicate.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    // `java -jar` reads this instead of a command-line flag.
    manifest { attributes("Enable-Native-Access" to nativeAccess) }
}

tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startShadowScripts") {
    dependsOn(tasks.named("jar"))
}

val createExecutable = tasks.register("createExecutable") {
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
