plugins {
    id("com.android.test")
}

android {
    namespace = "com.my.kizzy.macrobenchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔓 Allow macrobenchmark to run on a rooted physical device
        // UNLOCKED: bypasses CPU clock-locking requirement
        // LOW-BATTERY: bypasses battery-level safety check
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "UNLOCKED,LOW-BATTERY"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("debug"))
            matchingFallbacks.add("debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.macro.benchmark)
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

// Match project-wide Kotlin JVM target (VERSION_26 per build-logic convention)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26)
    }
}
