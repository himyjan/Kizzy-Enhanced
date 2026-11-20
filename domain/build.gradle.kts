plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(26)
}
dependencies{
    implementation(libs.kotlinx.coroutine)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jakarta.inject)
}