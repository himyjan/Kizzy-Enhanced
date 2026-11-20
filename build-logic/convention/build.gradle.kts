plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(26)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "kizzy.android.application.compose"
            implementationClass = "AndroidApplicationComposePlugin"
        }
        register("androidApplication") {
            id = "kizzy.android.application"
            implementationClass = "AndroidApplicationPlugin"
        }
        register("androidLibraryCompose") {
            id = "kizzy.android.library.compose"
            implementationClass = "AndroidLibraryComposePlugin"
        }
        register("androidLibrary") {
            id = "kizzy.android.library"
            implementationClass = "AndroidLibraryPlugin"
        }
        register("androidFeature") {
            id = "kizzy.android.feature"
            implementationClass = "AndroidFeaturePlugin"
        }
        register("androidHilt") {
            id = "kizzy.android.hilt"
            implementationClass = "AndroidHiltPlugin"
        }
    }
}