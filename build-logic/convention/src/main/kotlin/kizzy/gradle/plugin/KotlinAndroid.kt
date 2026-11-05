package kizzy.gradle.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared core compilation settings applied across all Android modules.
 */
private fun Project.configureCommonAndroidBody(commonExtension: CommonExtension) {
    commonExtension.compileSdk = 37

    commonExtension.defaultConfig.minSdk = 26

    commonExtension.buildFeatures.buildConfig = true

    commonExtension.compileOptions.sourceCompatibility = JavaVersion.VERSION_26
    commonExtension.compileOptions.targetCompatibility = JavaVersion.VERSION_26

    // Configures all Kotlin tasks inside this module to target Java 26 bytecode
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_26)
        }
    }
}

// Overload entry point for Application modules — only the app container
// should run R8 minification/shrinking since it sees the full dependency graph.
fun Project.configureKotlinAndroid(commonExtension: ApplicationExtension) {
    configureCommonAndroidBody(commonExtension)
    commonExtension.buildTypes.named("release").configure {
        isMinifyEnabled = true
        isShrinkResources = true

        proguardFiles(
            commonExtension.getDefaultProguardFile("proguard-android-optimize.txt"),
            "${rootProject.rootDir.absolutePath}/app/proguard-rules.pro"
        )
    }
}

// Overload entry point for Library modules — libraries must NOT minify
// themselves; R8 runs once at the :app level where all 17 modules are visible.
fun Project.configureKotlinAndroid(commonExtension: LibraryExtension) {
    configureCommonAndroidBody(commonExtension)
    commonExtension.buildTypes.named("release").configure {
        isMinifyEnabled = false
        isShrinkResources = false
    }
}

// Overload entry point for CommonExtension (generic fallback)
fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    configureCommonAndroidBody(commonExtension)
}
