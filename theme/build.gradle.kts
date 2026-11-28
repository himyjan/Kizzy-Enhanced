plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
}

android {
    namespace = "com.my.kizzy.ui.theme"
}

dependencies {
    implementation(libs.material3.core)
    implementation(libs.material3.wsc)
    implementation(projects.domain)
    implementation(projects.common.preference)
    implementation(projects.common.resources)
    implementation(projects.color)
    implementation(libs.material)
}