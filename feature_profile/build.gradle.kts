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
    implementation (projects.theme)
    implementation (projects.gateway)
    implementation (projects.featureRpcBase)
    implementation (projects.featureLogs)
    implementation (projects.data)
    implementation (libs.coil)
    implementation (libs.activity.compose)
    implementation (libs.kotlinx.serialization.json)
    implementation (libs.material.icons.extended)

    // Hilt annotation processor required for @HiltViewModel
    ksp(libs.hilt.compiler)
}