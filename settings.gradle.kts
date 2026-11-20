@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.*") // Scope JitPack strictly to GitHub packages
            }
        }
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(
    ":app",
    ":gateway",
    ":color",
    ":common:preference",
    ":common:resources",
    ":domain",
    ":data",
    ":common:components",
    ":theme",
    ":feature_crash_handler",
    ":feature_startup",
    ":feature_profile",
    ":feature_about",
    ":feature_settings",
    ":feature_logs",
    ":feature_rpc_base",
    ":feature_apps_rpc",
    ":feature_media_rpc",
    ":feature_console_rpc",
    ":feature_custom_rpc",
    ":feature_home",
    ":common:navigation",
    ":feature_manual_rpc",
    ":macrobenchmark"

)
rootProject.name = "Kizzy"