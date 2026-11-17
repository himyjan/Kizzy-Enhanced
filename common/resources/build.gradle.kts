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

            // Match tag text content securely without breaking structural whitespace
            val tagContentRegex = Regex("(?<![?])>([^<>]*\\S[^<>]*)<")

            // Run safe normalization regex chain on tag content only
            content = tagContentRegex.replace(content) { matchResult ->
                val originalText = matchResult.groupValues[1]
                var updatedText = originalText

                // Clear pre-existing bad backslash entities
                updatedText = updatedText.replace("\\&quot;", "&quot;")
                updatedText = updatedText.replace("\\&apos;", "&apos;")

                // Clean broken backslash-brace combinations
                updatedText = updatedText.replace("\\{", "{")
                updatedText = updatedText.replace("\\}", "}")

                // Restore placeholders {{ }} encoded during translation passes
                updatedText = updatedText
                    .replace("&apos;{{&apos;;", "{{")
                    .replace("&apos;{{&apos;", "{{")
                    .replace("&apos;}}&apos;;", "}}")
                    .replace("&apos;}}&apos;", "}}")
                    .replace("&apos;&apos;", "")

                // Convert standard XML entities to literal characters inside CDATA.
                // Do NOT use Java backslash escapes like \" or \' inside CDATA,
                // as aapt2 will read the literal backslash and throw an escape error.
                updatedText = updatedText
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")

                // Wrap text in CDATA to protect special characters from aapt2 re-escaping
                updatedText = "<![CDATA[$updatedText]]>"

                ">" + updatedText + "<"
            }

            destinationFile.writeText(content, Charsets.UTF_8)
        }
    }
}

// Register the modern task: read-only inputs, outputs to build/generated/
val normalizeStrings = tasks.register<NormalizeStringsTask>("normalizeStringResources") {
    group = "verification"
    description = "Normalizes string resource XML content into build output without mutating source files."

    // Collect every strings.xml file as read-only input
    inputFiles.from(fileTree("src/main/res") { include("**/strings.xml") })

    // Write normalized output to generated directory (never touches source files)
    outputDir.set(layout.buildDirectory.dir("generated/res/normalized"))
}

// Wire the generated normalized resources into the Android resource pipeline via Variant API
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            taskProvider = normalizeStrings,
            wiredWith = NormalizeStringsTask::outputDir
        )
    }
}