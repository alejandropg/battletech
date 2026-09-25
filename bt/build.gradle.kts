plugins {
    id("battletech.kotlin-application")
}

application {
    mainClass.set("io.archinaut.battletech.MainKt")
}

dependencies {
    implementation(project(":strategic"))
}
