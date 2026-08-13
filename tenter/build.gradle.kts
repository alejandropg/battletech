plugins {
    id("battletech.kotlin-library")
    `java-test-fixtures`
}

dependencies {
    api(libs.mordant)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
}
