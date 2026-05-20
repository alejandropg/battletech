plugins {
    id("battletech.kotlin-library")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(libs.findLibrary("konsist").get())
}
