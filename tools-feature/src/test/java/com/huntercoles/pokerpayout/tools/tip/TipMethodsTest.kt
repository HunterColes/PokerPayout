package com.huntercoles.pokerpayout.tools.tip

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The ways to tip are exactly the repository's (PP-112), never one the app made up: the addresses
 * are `crypto/DONATIONS.md`'s, the donation page is the one `.github/FUNDING.yml` links, every page
 * is the project's own on GitHub, and each QR code the app shows is the repository's own code
 * (`crypto/ETH.png`, `crypto/XMR.png`), module for module, and reads back as its address.
 *
 * Robolectric with native graphics, to read the PNGs pixel for pixel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TipMethodsTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    /** The PNG at [path] as it is, pixel for pixel: never scaled for a screen density. */
    private fun image(path: String): Bitmap {
        val bytes = File(root, path).readBytes()
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("can't read $path")
    }

    @Test
    fun theAddressesAreTheDonationPagesInItsOrder() {
        val page = File(root, "crypto/DONATIONS.md").readText()
        val listed = Regex("<code>([^<]+)</code>").findAll(page).map { it.groupValues[1] }.toList()
        assertEquals(listed, TipCoin.entries.map { it.address })
        assertTrue(page.contains("Ethereum (ETH)") && page.contains("Monero (XMR)"))
    }

    @Test
    fun theDonationPageIsTheOneTheRepositoryLinks() {
        val funding = File(root, ".github/FUNDING.yml").readText()
        assertTrue("FUNDING.yml doesn't link ${TipLink.DONATION_PAGE.url}", TipLink.DONATION_PAGE.url in funding)
        assertTrue(TipLink.DONATION_PAGE.url.endsWith("/crypto/DONATIONS.md"))
    }

    @Test
    fun everyPageIsTheProjectsOwn() {
        TipLink.entries.forEach { link ->
            assertTrue("${link.name} goes to ${link.url}", link.url.startsWith("https://github.com/HunterColes/PokerPayout"))
        }
    }

    @Test
    fun eachQrCodeIsTheRepositorysOwnAndReadsBackAsItsAddress() {
        mapOf(TipCoin.ETH to "ETH", TipCoin.XMR to "XMR").forEach { (coin, file) ->
            val shipped = QrReader.modules(image(shippedPath(file)))
            val original = QrReader.modules(image("crypto/$file.png"))
            assertEquals("the app's $file code isn't the repository's", original, shipped)
            assertEquals("one pixel a module, a 4-module quiet zone", original.size + 8, image(shippedPath(file)).width)
            assertEquals(coin.qrText, QrReader.read(shipped))
            assertTrue(coin.address in coin.qrText)
        }
    }

    private fun shippedPath(file: String) = "tools-feature/src/main/res/drawable-nodpi/tip_qr_${file.lowercase()}.png"

    @Test
    fun theGroupedAddressIsTheAddressInFours() {
        TipCoin.entries.forEach { coin ->
            assertEquals(coin.address, coin.grouped.replace(" ", ""))
            val groups = coin.grouped.split(" ")
            assertTrue(coin.grouped, groups.all { it.length <= 4 })
        }
        assertEquals("0x b93C D995 950A D1E0 16c9 Bf17 3999 A887 Bc18 1fED", TipCoin.ETH.grouped)
    }
}
