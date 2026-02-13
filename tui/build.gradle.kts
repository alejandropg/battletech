plugins {
    id("battletech.kotlin-application")
}

application {
    mainClass.set("battletech.tui.MainKt")
}

dependencies {
    implementation(project(":tactical"))
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
}
