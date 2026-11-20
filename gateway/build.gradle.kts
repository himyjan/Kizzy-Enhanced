plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(27)
}

dependencies {
    implementation(projects.domain)
    implementation(libs.kotlinx.coroutine)
    implementation(libs.bundles.network.ktor)
    testImplementation(libs.junit)
}