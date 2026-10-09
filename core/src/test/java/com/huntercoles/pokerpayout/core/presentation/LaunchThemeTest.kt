package com.huntercoles.pokerpayout.core.presentation

import androidx.compose.ui.graphics.toArgb
import com.huntercoles.pokerpayout.core.design.PokerColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launch screen is the felt green, not a white flash: the app theme (in `app`, which has no
 * tests of its own) paints the window, and on Android 12 and up the splash screen, with
 * `@color/poker_felt_green`, which must be [PokerColors.FeltGreen]. Read from the source files,
 * since the theme only exists once the app is built.
 */
class LaunchThemeTest {
    private val res = File(repoRoot(), "app/src/main/res")

    @Test
    fun `the launch colour is the design system's felt green`() {
        val colour = resources("values/colors.xml").single { it.getAttribute("name") == "poker_felt_green" }
        assertEquals(argbHex(PokerColors.FeltGreen.toArgb()), colour.textContent.trim().uppercase())
    }

    @Test
    fun `the window, the bars and the Android 12 splash screen are felt green`() {
        val base = style("values/themes.xml", "Base.Theme.PokerPayout")
        listOf("android:windowBackground", "android:statusBarColor", "android:navigationBarColor").forEach { item ->
            assertEquals(FELT, base[item], item)
        }
        assertFalse(parentOf("values/themes.xml", "Base.Theme.PokerPayout").contains("Light"), "a dark parent: light bar icons")

        assertEquals("Base.Theme.PokerPayout", parentOf("values/themes.xml", "Theme.PokerPayout"))
        assertEquals("Base.Theme.PokerPayout", parentOf("values-v31/themes.xml", "Theme.PokerPayout"))
        assertEquals(FELT, style("values-v31/themes.xml", "Theme.PokerPayout")["android:windowSplashScreenBackground"])
    }

    @Test
    fun `no night theme paints the window another colour`() {
        val night = res.listFiles().orEmpty().filter { it.name.startsWith("values-night") }
        assertEquals(emptyList<File>(), night.filter { File(it, "themes.xml").exists() })
    }

    private fun resources(path: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, path))
        val nodes = document.documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun styleElement(path: String, name: String): Element =
        resources(path).single { it.tagName == "style" && it.getAttribute("name") == name }

    private fun parentOf(path: String, name: String): String = styleElement(path, name).getAttribute("parent")

    private fun style(path: String, name: String): Map<String, String> {
        val items = styleElement(path, name).getElementsByTagName("item")
        return (0 until items.length).map { items.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private fun argbHex(argb: Int): String = String.format(Locale.ROOT, "#%08X", argb)

    /** The checkout: tests run in the module's directory, the repository is above it. */
    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }

    private companion object {
        const val FELT = "@color/poker_felt_green"
    }
}
