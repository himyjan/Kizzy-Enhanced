plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_logs"
}

dependencies {
    implementation (projects.theme)
    implementation(projects.featureRpcBase)
    implementation(projects.domain)
    implementation(libs.activity.compose)
    implementation(libs.material.icons.extended)

    // Hilt annotation processor
    ksp(libs.hilt.compiler)
}