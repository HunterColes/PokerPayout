package com.huntercoles.pokerpayout.core.utils

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * PP-114's guard: no "$" written into the app by hand. Every amount goes through [FormatUtils], which
 * writes the host's currency ([AppCurrency], the one file allowed a symbol). The sources are read as
 * text: a string literal in any module's main Kotlin holding a "$" that isn't a template ("$name",
 * "${...}") or a format argument ("%1\$s"), and any "$" in a string resource outside a format argument,
 * fails here. Comments may show "$450" as an example; they aren't on screen.
 */
class NoHardCodedDollarTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val modules: List<File> = root.listFiles().orEmpty().filter { File(it, "src/main").isDirectory }

    private fun File.relative(): String = relativeTo(root).path

    @Test
    fun `no Kotlin string in the app writes a dollar sign by hand`() {
        val found = modules
            .flatMap { module -> File(module, "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { it.relative() !in ALLOWED }
            .flatMap { file -> dollarLiterals(file.readText()).map { "${file.relative()}: $it" } }
        assertTrue(found.isEmpty(), "Hard-coded \"$\" (use FormatUtils.formatMoney or the currency's symbol):\n" + found.joinToString("\n"))
    }

    @Test
    fun `no string resource writes a dollar sign by hand`() {
        val found = modules
            .flatMap { module -> File(module, "src/main/res").walkTopDown().filter { it.isFile && it.extension == "xml" }.toList() }
            .flatMap { file ->
                file.readText().replace(XML_COMMENT, "").lines().filter { line ->
                    FORMAT_ARGUMENT.replace(line, "").contains('$')
                }.map { "${file.relative()}: ${it.trim()}" }
            }
        assertTrue(found.isEmpty(), "Hard-coded \"$\" in resources (pass the amount from FormatUtils.formatMoney):\n" + found.joinToString("\n"))
    }

    @Test
    fun `the guard finds what it looks for`() {
        val source = """
            val a = "${'$'}5"
            val b = "-${'$'}"
            val c = "\${'$'}{x}"
            val ok1 = "${'$'}name and ${'$'}{x.y}"
            val ok2 = "%1\${'$'}s of %2\${'$'}d"
            val ok3 = Regex("^Player \\d+${'$'}")
            // val d = "${'$'}9" in a comment
            /** "${'$'}450" in KDoc */
        """.trimIndent()
        assertTrue(dollarLiterals(source).size == 3, "found ${dollarLiterals(source)}")
    }

    /** The string literals in [source] (comments left out) that hold a dollar sign of their own. */
    private fun dollarLiterals(source: String): List<String> =
        source.replace(BLOCK_COMMENT, "").lines().flatMap { line ->
            val literals = STRING.findAll(line).toList()
            val comment = LINE_COMMENT.findAll(line).map { it.range.first }
                .firstOrNull { start -> literals.none { start in it.range } } ?: line.length
            literals.filter { it.range.first < comment }.map { it.value }.filter { literal ->
                val unformatted = FORMAT_ARGUMENT.replace(literal, "")
                // A regex's end anchor ("^Player \\d+$") isn't money
                val text = if (unformatted.startsWith("\"^")) unformatted.removeSuffix(REGEX_END) else unformatted
                ESCAPED_DOLLAR in text || BARE_DOLLAR.containsMatchIn(text)
            }
        }

    private companion object {
        /** The one file that holds currency symbols: the currencies themselves. */
        val ALLOWED = setOf("core/src/main/java/com/huntercoles/pokerpayout/core/utils/AppCurrency.kt")

        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val LINE_COMMENT = Regex("//")
        val XML_COMMENT = Regex("""<!--[\s\S]*?-->""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val FORMAT_ARGUMENT = Regex("""%\d+\\?\$""")
        const val ESCAPED_DOLLAR = "\\$"
        const val REGEX_END = "$\""

        /** A "$" that doesn't start a template ("$name", "${...}") and isn't escaped. */
        val BARE_DOLLAR = Regex("""(?<!\\)\$(?![A-Za-z_{])""")
    }
}
