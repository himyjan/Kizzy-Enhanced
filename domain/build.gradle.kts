plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies{
    implementation(libs.kotlinx.coroutine)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax)
}