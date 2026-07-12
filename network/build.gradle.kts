plugins {
    id("battletech.kotlin-library")
    id("battletech.kotlin-serialization")
}

dependencies {
    api(project(":tactical"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("reflect"))
}
