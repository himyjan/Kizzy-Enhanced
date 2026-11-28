plugins {
    id("kizzy.android.library")
    id("kizzy.android.library.compose")
}

android {
    namespace = "com.my.kizzy.ui.components"
}

dependencies {
    implementation(projects.theme)
    implementation(libs.material3.core)
    implementation(libs.material3.wsc)
    implementation(projects.color)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    implementation(projects.common.resources)
    implementation(libs.material.icons.extended)
}
