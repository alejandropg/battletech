plugins {
    id("battletech.kotlin-application")
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("battletech.tui.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

dependencies {
    implementation(project(":tactical"))
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
}

tasks.shadowJar {
    archiveBaseName.set("tui")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

val createExecutable by tasks.registering {
    group = "distribution"
    description = "Creates a self-executing tui binary (Unix/macOS)"
    dependsOn(tasks.shadowJar)
    val jarFile = tasks.shadowJar.flatMap { it.archiveFile }
    val outputFile = layout.buildDirectory.file("tui")
    inputs.file(jarFile)
    outputs.file(outputFile)
    doLast {
        val stub = "#!/bin/sh\nexec java -jar \"\$0\" \"\$@\"\n"
        outputFile.get().asFile.apply {
            writeBytes(stub.toByteArray() + jarFile.get().asFile.readBytes())
            setExecutable(true)
        }
    }
}
