plugins {
    id("kizzy.android.library")
    id("kizzy.android.library.compose")
    id("kizzy.android.feature")
    id("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_about"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.coil)
    implementation(libs.coil.network)

    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
}