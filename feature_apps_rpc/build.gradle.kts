plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_apps_rpc"
}

dependencies {
    implementation(projects.featureRpcBase)
    implementation(projects.data)
    implementation(projects.common.preference)
    implementation (libs.material.icons.extended)

    // Hilt annotation processor required for @HiltViewModel
    ksp(libs.hilt.compiler)
}