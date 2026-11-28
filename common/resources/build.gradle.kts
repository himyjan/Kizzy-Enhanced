import java.io.File
import javax.inject.Inject
import org.gradle.api.file.FileSystemOperations

plugins {
    id("kizzy.android.library")
}

android {
    namespace = "com.my.kizzy.resources"
}

abstract class NormalizeStringsTask @Inject constructor(
    private val fs: FileSystemOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun filterStrings() {
        val targetDirectory = outputDir.get().asFile
        fs.delete { delete(targetDirectory) }
        targetDirectory.mkdirs()

        // Fixed-width negative lookbehind: do not match processing instruction end '?>'
        val textNodeRegex = Regex("""(?<!\?)>([^<>]*\S[^<>]*)<""")

        inputFiles.files.forEach { file ->
            if (!file.exists()) return@forEach

            val valuesDirName = file.parentFile?.name ?: "values"
            val destinationFolder = File(targetDirectory, valuesDirName).apply { mkdirs() }
            val destinationFile = File(destinationFolder, file.name)

            val content = file.readText(Charsets.UTF_8)

            val normalizedContent = textNodeRegex.replace(content) { matchResult ->
                val rawText = matchResult.groupValues[1]

                // Leave existing CDATA sections untouched
                if (rawText.trimStart().startsWith("<![CDATA[") || rawText.endsWith("]]>")) {
                    return@replace matchResult.value
                }

                val updated = rawText
                    // Clear malformed backslash-escaped entities
                    .replace("\\&quot;", "&quot;")
                    .replace("\\&apos;", "&apos;")
                    // Clean broken backslash-brace escapes
                    .replace("\\{", "{")
                    .replace("\\}", "}")
                    // Restore placeholders {{ / }} from translation artifacts
                    .replace("&apos;{{&apos;;", "{{")
                    .replace("&apos;{{&apos;", "{{")
                    .replace("&apos;}}&apos;;", "}}")
                    .replace("&apos;}}&apos;", "}}")
                    .replace("&apos;&apos;", "")
                    // Decode XML entities to literal characters inside CDATA
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")

                val safeCdata = updated.replace("]]>", "]]]]><![CDATA[>")
                ">" + "<![CDATA[$safeCdata]]>" + "<"
            }

            destinationFile.writeText(normalizedContent, Charsets.UTF_8)
        }
    }
}

val normalizeStrings = tasks.register<NormalizeStringsTask>("normalizeStringResources") {
    group = "verification"
    description = "Normalizes string resource XML content into generated build resources without mutating source files."

    inputFiles.from(
        layout.projectDirectory.dir("src/main/res").asFileTree.matching {
            include("**/strings.xml")
        },
    )
    outputDir.set(layout.buildDirectory.dir("generated/res/normalized"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            taskProvider = normalizeStrings,
            wiredWith = NormalizeStringsTask::outputDir,
        )
    }
}