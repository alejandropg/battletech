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

check(runtimeClasspath.none { it.absolutePath.contains("/battletech/") }) {
    "The packaged consumer runtime classpath must not contain another repository module"
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
