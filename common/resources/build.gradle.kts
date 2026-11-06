import java.io.File

plugins {
    id ("kizzy.android.library")
}

android {
    namespace = "com.my.kizzy.resources"
}

tasks.register("normalizeStringResources") {
    group = "verification"
    description = "Safely normalizes values inside string resource tags without corrupting XML structural tags."

    val resDir = file("src/main/res")

    doLast {
        if (!resDir.exists() || !resDir.isDirectory) return@doLast

        resDir.walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile.name.startsWith("values") }
            .forEach { stringFile ->
                var content = stringFile.readText(Charsets.UTF_8)
                val originalContent = content

                // Match only content located between > and < XML tag blocks
                val tagContentRegex = Regex(">([^<]+)<")

                content = tagContentRegex.replace(content) { matchResult ->
                    val originalText = matchResult.groupValues[1]
                    var updatedText = originalText

                    // Rule 4 & 5: Clear pre-existing bad backslash entities
                    updatedText = updatedText.replace("\\&quot;", "&quot;")
                    updatedText = updatedText.replace("\\&apos;", "&apos;")

                    // Rule 1 & 2: Fix raw escaped quotes safely inside text bodies
                    updatedText = updatedText.replace("\\'", "&apos;")
                    updatedText = updatedText.replace("\\\"", "&quot;")

                    // Rule 3 & 6: Handle template brackets
                    updatedText = updatedText.replace("\\{{", "&apos;{{&apos;")
                    updatedText = updatedText.replace("(?<!&apos;)\\{\\{".toRegex(), "&apos;{{&apos;")

                    // Rule 7: Clean broken Unicode escape combinations
                    updatedText = updatedText.replace("\\{", "{")
                    updatedText = updatedText.replace("\\}", "}")

                    // Reconstruct the valid XML tag boundary block
                    ">" + updatedText + "<"
                }

                if (content != originalContent) {
                    stringFile.writeText(content, Charsets.UTF_8)
                    logger.lifecycle("Safely normalized text assets for: ${stringFile.parentFile.name}/${stringFile.name}")
                }
            }
    }
}

tasks.matching { it.name.startsWith("preBuild") }.configureEach {
    dependsOn("normalizeStringResources")
}