package kizzy.gradle.plugin

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    commonExtension.buildFeatures.compose = true

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies {
        val composeBom = libs.findLibrary("compose-bom").get()
        add("implementation", platform(composeBom))
        add("androidTestImplementation", platform(composeBom))

        add("implementation", libs.findLibrary("compose-ui").get())
        add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    }
}