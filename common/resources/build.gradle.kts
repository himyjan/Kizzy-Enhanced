import java.io.File

plugins {
    id ("kizzy.android.library")
}

android {
    namespace = "com.my.kizzy.resources"
}

abstract class NormalizeStringsTask : DefaultTask() {
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun filterStrings() {
        val targetDirectory = outputDir.get().asFile
        targetDirectory.deleteRecursively()

        inputFiles.forEach { file ->
            // Reconstruct the correct values subfolder path (values, values-hi, values-ps, etc.)
            val valuesDirName = file.parentFile.name
            val destinationFolder = File(targetDirectory, valuesDirName).apply { mkdirs() }
            val destinationFile = File(destinationFolder, file.name)

            var content = file.readText(Charsets.UTF_8)
            val tagContentRegex = Regex(">([^<]+)<")

            // Run safe normalization regex chain on tag content only
            content = tagContentRegex.replace(content) { matchResult ->
                val originalText = matchResult.groupValues[1]
                var updatedText = originalText

                // Rule 4 & 5: Clear pre-existing bad backslash entities
                updatedText = updatedText.replace("\\&quot;", "&quot;")
                updatedText = updatedText.replace("\\&apos;", "&apos;")

                // Rule 1 & 2: Fix raw escaped quotes safely inside text bodies
                updatedText = updatedText.replace("\\'", "&apos;")
                updatedText = updatedText.replace("\\\"", "&quot;")

                // Rule 7: Clean broken backslash-brace combinations
                updatedText = updatedText.replace("\\{", "{")
                updatedText = updatedText.replace("\\}", "}")

                // Rule 8: Escape any remaining bare braces that AAPT2 might misinterpret
                // as unicode escape starts (\uXXXX). Use numeric entities which AAPT2 handles safely.
                updatedText = updatedText.replace("{", "&#123;")
                updatedText = updatedText.replace("}", "&#125;")

                ">" + updatedText + "<"
            }

            destinationFile.writeText(content, Charsets.UTF_8)
        }
    }
}

// Register the modern task: read-only inputs, outputs to build/generated/
// Task is standalone (verification only) — not wired into Variant API
// to avoid duplicate resource processing by AAPT2
val normalizeStrings = tasks.register<NormalizeStringsTask>("normalizeStringResources") {
    group = "verification"
    description = "Normalizes string resource XML content into build output without mutating source files."

    // Collect every strings.xml file as read-only input
    inputFiles.from(fileTree("src/main/res") { include("**/strings.xml") })

    // Write normalized output to generated directory (never touches source files)
    outputDir.set(layout.buildDirectory.dir("generated/res/normalized"))
}
