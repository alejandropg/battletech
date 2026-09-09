@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

plugins {
    id("battletech.kotlin-library")
    `java-test-fixtures`
}

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
