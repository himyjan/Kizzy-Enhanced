plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
    id ("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_rpc_base"

    buildTypes {
        release {
            matchingFallbacks.clear()
            matchingFallbacks.addAll(listOf("release", "debug"))
        }
        debug {
            matchingFallbacks.clear()
            matchingFallbacks.addAll(listOf("debug", "release"))
        }
    }
}

dependencies {
    implementation (libs.blankj.utilcodex)
    implementation(libs.androidx.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil)
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)
    implementation(libs.material3)
    implementation(libs.material3.windows.size)
    implementation(libs.material.icons.extended)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.hilt.navigation.compose)
    implementation(projects.gateway)
    implementation(projects.data)
    ksp(projects.data)
}
