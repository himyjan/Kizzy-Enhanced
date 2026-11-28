plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_profile"
}

dependencies {
    implementation(projects.theme)
    implementation(projects.gateway)
    implementation(projects.common.navigation)
    implementation(projects.featureRpcBase)
    implementation(projects.featureLogs)
    implementation(projects.featureSettings)
    implementation(projects.data)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.bundles.network.ktor)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.browser)
    implementation(libs.material.icons.extended)

    // Hilt annotation processor required for @HiltViewModel
    ksp(libs.hilt.compiler)
}