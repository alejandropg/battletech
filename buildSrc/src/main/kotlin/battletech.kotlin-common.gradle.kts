import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("jvm").get().toString()))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

dependencies {
    testImplementation(libs.findBundle("junit").get())
    testRuntimeOnly(libs.findLibrary("junit-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}
