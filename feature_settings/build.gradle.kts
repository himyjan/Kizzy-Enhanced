plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_settings"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(libs.androidx.material)
    implementation(libs.material.icons.extended)
    implementation(libs.coil)
    implementation(libs.coil.network)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.activity.compose)

    implementation(projects.color)
    implementation(projects.theme)
    implementation(projects.featureRpcBase)
    implementation(projects.common.components)
}