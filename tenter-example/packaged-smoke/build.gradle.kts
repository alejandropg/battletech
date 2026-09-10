import java.io.File

plugins {
    kotlin("jvm") version "2.4.10"
    application
}

repositories {
    mavenCentral()
}

val tenterJar = file(property("tenterJar") as String)
val runtimeClasspath = (property("runtimeClasspath") as String)
    .split(File.pathSeparator)
    .filter(String::isNotEmpty)
    .map(::file)

// The consumer must resolve everything but tenter.jar from the dependency cache. Comparing against
// the repository root the outer build passed in keeps this true wherever the repository is cloned;
// matching a hardcoded directory name would silently pass under any other checkout path.
val repositoryRoot = file(property("repositoryRoot") as String).canonicalFile.toPath()
val leaked = runtimeClasspath.filter { it.canonicalFile.toPath().startsWith(repositoryRoot) }
check(leaked.isEmpty()) {
    "The packaged consumer runtime classpath must not contain repository outputs: $leaked"
}

dependencies {
    implementation(files(tenterJar))
    implementation(files(runtimeClasspath))
}

kotlin {
    jvmToolchain((property("jvmVersion") as String).toInt())
}

sourceSets {
    named("main") {
        java.setSrcDirs(emptyList<String>())
        kotlin.srcDir(file(property("exampleSources") as String))
    }
}

application {
    mainClass.set("tenterexample.ExampleMainKt")
}

tasks.named<JavaExec>("run") {
    args("--headless")
}
