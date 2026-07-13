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
