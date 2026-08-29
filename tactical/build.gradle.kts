import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("battletech.kotlin-library")
    id("battletech.kotlin-serialization")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    api(libs.findLibrary("kotlinx-serialization-core").get())
    implementation(libs.findLibrary("kotlinx-serialization-json").get())
    testImplementation(libs.findLibrary("konsist").get())
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("map")) {
        into("map")
        exclude(".DS_Store")
    }
    from(rootProject.layout.projectDirectory.dir("game")) {
        into("game")
        exclude(".DS_Store")
    }
    from(rootProject.layout.projectDirectory.dir("mech")) {
        into("mech")
        exclude(".DS_Store")
    }
}
