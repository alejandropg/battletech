@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

plugins {
    id("tenter.kotlin-library")
    `maven-publish`
}

group = "com.github.tenter"
version = libs.versions.tenter.get()

kotlin {
    abiValidation {
        referenceDumpDir.set(layout.projectDirectory.dir("api"))
    }
}

dependencies {
    api(libs.mordant)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "tenter"
        }
    }
}
