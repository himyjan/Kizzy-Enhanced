plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.my.kizzy.preference"
}

dependencies {
    implementation(projects.color)
    implementation(projects.domain)
    implementation(projects.common.resources)
    implementation(libs.material3.core)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)
}