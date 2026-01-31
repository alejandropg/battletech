plugins {
    id("battletech.kotlin-application")
}

application {
    mainClass.set("battletech.MainKt")
}

dependencies {
    implementation(project(":strategic"))
    implementation(project(":tactical"))
}
