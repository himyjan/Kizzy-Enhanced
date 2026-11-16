import nl.littlerobots.vcu.plugin.versionSelector

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.baselineprofile) apply false
    id("io.github.ben-manes.versions") version "0.67.0"
    id("nl.littlerobots.version-catalog-update") version "1.1.2"
}

versionCatalogUpdate {
    versionSelector { candidate ->
        !candidate.candidate.version.isBlank()
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.5.0-RC")
        }
    }
}