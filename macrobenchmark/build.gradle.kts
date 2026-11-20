plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.my.kizzy.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔓 Allow macrobenchmark to run on a rooted physical device
        // UNLOCKED: bypasses CPU clock-locking requirement
        // LOW-BATTERY: bypasses battery-level safety check
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "UNLOCKED,LOW-BATTERY"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            initWith(getByName("debug"))
            matchingFallbacks.add("debug")
        }
    }
    compileSdkMinor = 1
}

dependencies {
    implementation(libs.androidx.macro.benchmark)
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.profileinstaller)
}
