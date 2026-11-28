package kizzy.gradle.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project

/**
 * Shared core compilation settings applied across all Android modules.
 */
private fun Project.configureCommonAndroid(commonExtension: CommonExtension) {
    commonExtension.compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    commonExtension.defaultConfig.minSdk = 26

    commonExtension.buildFeatures.buildConfig = true
}

// Overload entry point for Application modules
fun Project.configureKotlinAndroid(commonExtension: ApplicationExtension) {
    configureCommonAndroid(commonExtension)
    commonExtension.buildTypes.named("release").configure {
        isMinifyEnabled = true
        isShrinkResources = true

        proguardFiles(
            commonExtension.getDefaultProguardFile("proguard-android-optimize.txt"),
            file("${rootProject.rootDir.absolutePath}/app/proguard-rules.pro")
        )
    }
}

// Overload entry point for Library modules
fun Project.configureKotlinAndroid(commonExtension: LibraryExtension) {
    configureCommonAndroid(commonExtension)
    commonExtension.buildTypes.named("release").configure {
        isMinifyEnabled = false
        isShrinkResources = false
    }
}