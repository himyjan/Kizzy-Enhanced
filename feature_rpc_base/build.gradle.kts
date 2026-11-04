plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_rpc_base"
}

dependencies {
    implementation (libs.blankj.utilcodex)
    implementation(libs.androidx.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil)
    implementation(libs.compose.ui)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(projects.gateway)
}
