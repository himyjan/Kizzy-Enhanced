plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

kotlin {
    jvmToolchain(27)
}

android {
    namespace = "com.my.kizzy.macrobenchmark"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔓 Allow macrobenchmark to run on a rooted physical device
        // UNLOCKED: bypasses CPU clock-locking requirement
        // LOW-BATTERY: bypasses battery-level safety check
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "UNLOCKED,LOW-BATTERY,EMULATOR"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            // Benchmark the R8-optimized, non-debuggable app. Falling back to the
            // app's "debug" variant made the target package debuggable, which the
            // benchmark framework rejects (and which would make every number
            // meaningless). :app defines its own "benchmark" build type derived
            // from release with isProfileable = true, so prefer it and release,
            // never debug.
            initWith(getByName("debug"))
            matchingFallbacks.addAll(listOf("benchmark", "release"))
            isDebuggable = false
        }
    }
    compileSdkMinor = 1
}

dependencies {
    implementation(libs.androidx.macrobenchmark.junit4)
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.profileinstaller)
}
