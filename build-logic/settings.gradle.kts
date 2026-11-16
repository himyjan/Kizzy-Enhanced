dependencyResolutionManagement {
    repositories {
        // Explicitly point to Google's Maven repo URL here as well
        maven { url = uri("https://maven.google.com") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")