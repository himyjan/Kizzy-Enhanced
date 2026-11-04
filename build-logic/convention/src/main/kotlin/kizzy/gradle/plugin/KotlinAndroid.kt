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

    commonExtension.buildTypes.named("release").configure {
        isMinifyEnabled = true

        proguardFiles(
            commonExtension.getDefaultProguardFile("proguard-android-optimize.txt"),
            "${rootProject.rootDir.absolutePath}/app/proguard-rules.pro"
        )
    }

    // Configures all Kotlin tasks inside this module to target Java 26 bytecode
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_26)
        }
    }
}

// Overload entry point for Application modules
fun Project.configureKotlinAndroid(commonExtension: ApplicationExtension) {
    configureCommonAndroidBody(commonExtension)
}

// Overload entry point for Library modules
fun Project.configureKotlinAndroid(commonExtension: LibraryExtension) {
    configureCommonAndroidBody(commonExtension)
}

// Overload entry point for CommonExtension (generic fallback)
fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    configureCommonAndroidBody(commonExtension)
}
