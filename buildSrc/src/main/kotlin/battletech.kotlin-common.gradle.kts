import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    val jvmVersion = libs.findVersion("jvm").get().toString()
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(jvmVersion)
    }
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jvmVersion)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findBundle("junit").get())
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}
