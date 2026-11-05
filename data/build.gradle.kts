import com.android.build.api.dsl.LibraryDefaultConfig

plugins {
    id("kizzy.android.library")
    id("kizzy.android.hilt")
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.my.kizzy.data"
    defaultConfig {
        buildConfigFieldFromGradleProperty("BASE_URL","BASE_URL")
        buildConfigFieldFromGradleProperty("DISCORD_API_BASE_URL","DISCORD_API_URL")
        buildConfigFieldFromGradleProperty("GITHUB_API_BASE_URL","GITHUB_API_URL")
        buildConfigFieldFromGradleProperty("IMGUR_API_BASE_URL", "IMGUR_API_BASE_URL")
    }

    buildTypes {
        release {
            matchingFallbacks.clear()
            matchingFallbacks.addAll(listOf("release", "debug"))
        }
        debug {
            matchingFallbacks.clear()
            matchingFallbacks.addAll(listOf("debug", "release"))
        }
    }

    publishing {
        singleVariant("debug") {
            withSourcesJar()
        }
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation (libs.core.ktx)
    implementation (projects.domain)
    implementation (libs.bundles.network.ktor)
    implementation (libs.ktor.content.negotiation)
    implementation (libs.ktor.logging)
    implementation (libs.work.runtime.ktx)
    implementation (libs.hilt.work)
    ksp(libs.hilt.work)
    implementation (projects.common.preference)
    implementation (projects.common.resources)
    implementation (projects.gateway)
    implementation (libs.blankj.utilcodex)
    testImplementation(libs.junit)
}

fun LibraryDefaultConfig.buildConfigFieldFromGradleProperty(fieldName: String, gradlePropertyName: String) {
    val propertyValue = project.findProperty(gradlePropertyName) as? String
    if (propertyValue != null) {
        buildConfigField("String", fieldName, propertyValue)
    }
}