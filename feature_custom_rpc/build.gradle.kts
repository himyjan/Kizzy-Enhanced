plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_custom_rpc"
}

dependencies {
    implementation(libs.material3.core)
    
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(projects.featureRpcBase)
    implementation(projects.featureProfile)

    // Hilt annotation processor required for @HiltViewModel
    ksp(libs.hilt.compiler)
}