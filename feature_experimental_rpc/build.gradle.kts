plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.hilt")
    id ("kizzy.android.feature")
}

android {
    namespace = "com.my.kizzy.feature_experimental_rpc"
}

dependencies {
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.coil)
    implementation(libs.coil.network)

    implementation(projects.featureRpcBase)
    implementation(projects.featureProfile)
    implementation(projects.theme)

    // Hilt annotation processor required for @HiltViewModel
    ksp(libs.hilt.compiler)
}
