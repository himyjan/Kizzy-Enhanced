import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_26.toString()
        targetCompatibility = JavaVersion.VERSION_26.toString()
    }
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_26)
            // Force Kotlin 2.4 compiler to emit metadata blobs in 2.2 format
            // so Hilt 2.60.1's shaded kotlin-metadata-jvm engine can read them
            freeCompilerArgs.add("-Xmetadata-version=2.2.0")
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            // Compose compiler reports and metrics are always enabled for
            // performance tuning. Reports go to build/compose_compiler/ in each
            // module; use them to find recomposition hot spots and unstable
            // composables that cause wasted work (and battery drain).
            freeCompilerArgs.addAll(listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.layout.buildDirectory.asFile.get().absolutePath}/compose_compiler"
            ))
            freeCompilerArgs.addAll(listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.layout.buildDirectory.asFile.get().absolutePath}/compose_compiler"
            ))
        }
    }
    // The runtime bundle of a library (the jar/dir the app actually dexes) did not
    // see the classes that KSP generated: the task stayed up to date with an
    // outdated snapshot. The APK was then built without the Hilt modules, so the
    // screen crashed with "Cannot create an instance of class ...ViewModel" as soon
    // as it was opened. The bundle is cheap to create, so it is always rebuilt.
    tasks.matching {
        it.name.startsWith("bundleLibRuntimeToJar") ||
            it.name.startsWith("bundleLibRuntimeToDir")
    }.configureEach {
        outputs.upToDateWhen { false }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name == "kotlinx-metadata-jvm") {
                useVersion("0.10.0")
            }
        }
    }
}
