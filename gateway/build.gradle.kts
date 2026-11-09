plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation (projects.domain)
    implementation (libs.kotlinx.coroutine)
    implementation (libs.bundles.network.ktor)
    testImplementation (libs.junit)
}