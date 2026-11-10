import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("battletech.kotlin-library")
    id("battletech.kotlin-serialization")
}

dependencies {
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.konsist)
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("map")) {
        into("map")
        exclude(".DS_Store")
    }
    from(rootProject.layout.projectDirectory.dir("unit")) {
        into("unit")
        exclude(".DS_Store")
    }
    from(rootProject.layout.projectDirectory.dir("mech")) {
        into("mech")
        exclude(".DS_Store")
    }
}
