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
    implementation(libs.material)
    
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp.android)
    implementation(libs.androidx.browser)
    implementation(libs.activity.compose)

    implementation(projects.color)
    implementation(projects.theme)
    implementation(projects.featureRpcBase)
    implementation(projects.common.components)
}