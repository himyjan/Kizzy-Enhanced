plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(28)
}

dependencies {
    implementation(projects.domain)
    implementation(libs.kotlinx.coroutine)
    implementation(libs.bundles.network.ktor)
    testImplementation(libs.junit)
}

// ============================================================================
// Manual bytecode patching: Kotlin compiles with JDK 28 toolchain (major version
// 72), but the Kotlin compiler only supports up to class file major version 70.
// Patch all compiled .class files down to major version 65 (Java 21) before
// the JAR is packaged, so downstream Android modules can process them.
// ============================================================================

private fun patchClassFileMajorVersion(file: File, newMajor: Int) {
    val data = file.readBytes()
    if (data.size < 8) return
    if (data[0] != 0xCA.toByte() || data[1] != 0xFE.toByte() ||
        data[2] != 0xBA.toByte() || data[3] != 0xBE.toByte()) return
    val major = (data[6].toInt() and 0xFF) shl 8 or (data[7].toInt() and 0xFF)
    if (major == 72) {
        val b = data.copyOf(data.size)
        b[6] = (newMajor shr 8 and 0xFF).toByte()
        b[7] = (newMajor and 0xFF).toByte()
        file.writeBytes(b)
    }
}

tasks.register("patchJvm28To21") {
    group = "build"
    description = "Patch JVM 28 class files (major 72) down to JVM 21 (major 65) for Kotlin compiler compatibility"
    dependsOn(tasks.named("compileKotlin"))
    val buildDir = project.buildDir
    doLast {
        val classesDir = File(buildDir, "classes/kotlin/main")
        if (classesDir.exists()) {
            classesDir.walkTopDown().filter { it.extension == "class" }.forEach { file ->
                patchClassFileMajorVersion(file, 65)
            }
        }
    }
}

tasks.named("jar") {
    dependsOn(tasks.named("patchJvm28To21"))
}
