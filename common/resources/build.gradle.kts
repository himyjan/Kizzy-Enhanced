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
            // Do not match the `?>` of the XML declaration, otherwise the
            // newline between the declaration and <resources> gets wrapped in
            // CDATA, producing invalid XML (markup before the root element).
            // Only capture text nodes that contain real content (\\S); pure
            // whitespace gaps between tags are structural and must be left
            // untouched so the XML remains well-formed.
            // [^<>]* keeps the match inside a single tag so it cannot bleed
            // across element boundaries (\\S matches < and > too).
            val tagContentRegex = Regex("(?<![?])>([^<>]*\\S[^<>]*)<")

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

                // Rule 3 & 6: Wrap {{ and }} in &apos; entities to match the
                // default strings.xml placeholder format, but only when not
                // already wrapped (avoid double-escaping &apos;{{&apos;;)
                updatedText = updatedText.replace(Regex("(?<!!&apos;)\\{\\{(?!!&apos;)"), "&apos;{{&apos;;")
                updatedText = updatedText.replace(Regex("(?<!!&apos;)\\}\\}(?!!&apos;)"), "&apos;}}&apos;")

                // Rule 8: Wrap ALL text content in CDATA to prevent AAPT2 from
                // decoding XML entities (like &apos;) and re-escaping them as
                // backslash escapes (\', \{) in its intermediate format, which
                // it then rejects as "Invalid unicode escape sequence".
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
