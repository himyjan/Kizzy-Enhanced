import java.io.File

plugins {
    id ("kizzy.android.library")
}

android {
    namespace = "com.my.kizzy.resources"
}

tasks.register("normalizeStringResources") {
    group = "verification"
    description = "Normalizes text formatting tokens and removes illegal escapes from strings.xml files."

    val resDir = file("src/main/res")

    doLast {
        if (!resDir.exists() || !resDir.isDirectory) {
            logger.warn("Target res directory does not exist: ${resDir.absolutePath}")
            return@doLast
        }

        resDir.walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile.name.startsWith("values") }
            .forEach { stringFile ->
                logger.lifecycle("Normalizing resource string format rules for: ${stringFile.parentFile.name}/${stringFile.name}")

                var content = stringFile.readText(Charsets.UTF_8)
                val originalContent = content

                // Rule 4: \&quot; -> &quot;
                content = content.replace(Regex("\\\\&quot;"), "&quot;")

                // Rule 5: \&apos; -> &apos;
                content = content.replace(Regex("\\\\&apos;"), "&apos;")

                // Rule 1: \' -> &apos;
                content = content.replace(Regex("\\\\'"), "&apos;")

                // Rule 2: \" -> &quot;
                content = content.replace(Regex("\\\\\""), "&quot;")

                // Rule 3: \{\{ -> &apos;{{&apos;
                content = content.replace(Regex("\\\\\\{\\{"), "&apos;{{&apos;")

                // Rule 6: {{ -> &apos;{{&apos; (Only if not already wrapped by &apos; labels)
                content = content.replace(Regex("(?<!&apos;)\\{\\{"), "&apos;{{&apos;")

                // Rule 7: \{ -> {  (Crucial: Safely clears the AAPT2 unicode escape crash)
                content = content.replace(Regex("\\\\\\{"), "{")
                content = content.replace(Regex("\\\\\\}"), "}") // Clean the trailing brace too

                if (content != originalContent) {
                    stringFile.writeText(content, Charsets.UTF_8)
                    logger.lifecycle("Successfully cleaned and updated formatting constraints.")
                }
            }
    }
}

tasks.matching { it.name.startsWith("preBuild") }.configureEach {
    dependsOn("normalizeStringResources")
}