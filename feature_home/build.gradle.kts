plugins {
    id("kizzy.android.library")
    id("kizzy.android.library.compose")
    id("kizzy.android.feature")
    id("kizzy.android.hilt")
}

android {
    namespace = "com.my.kizzy.feature_home"

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
    implementation(projects.featureRpcBase)
    implementation(projects.featureSettings)
    implementation(projects.common.navigation)

    implementation(libs.coil)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
}