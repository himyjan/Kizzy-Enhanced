plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.my.kizzy.navigation"
}

dependencies {
    implementation(libs.compose.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}