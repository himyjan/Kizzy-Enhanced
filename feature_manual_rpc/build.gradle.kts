plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.hilt")
    id ("kizzy.android.feature")
}

android {
    namespace = "com.my.kizzy.feature_manual_rpc"
}

dependencies {
    
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(projects.featureRpcBase)
    implementation(projects.featureProfile)
    implementation(projects.theme)

    // Hilt annotation processor required for @HiltViewModel
    ksp(libs.hilt.compiler)
}
