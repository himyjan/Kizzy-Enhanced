plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_settings"

    defaultConfig {
        buildConfigField("String","VERSION_NAME", "\"${libs.versions.version.name.get()}\"")
    }
}

dependencies {
    implementation(libs.androidx.material)
    implementation(libs.material.icons.extended)
    implementation(libs.coil)
    implementation(libs.coil.network)

    implementation(libs.kotlinx.serialization.json)

    implementation(projects.color)
    implementation(projects.theme)
    implementation(libs.hilt.navigation.compose)
}