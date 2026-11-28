plugins {
    alias(libs.plugins.kotlinx.serialization)
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
    implementation(libs.material)
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)
    implementation(libs.material3.core)
    implementation(libs.material3.wsc)
    implementation(libs.material.icons.extended)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation(projects.gateway)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp.android)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.junit)

    // Hilt annotation processor
    ksp(libs.hilt.compiler)
}
