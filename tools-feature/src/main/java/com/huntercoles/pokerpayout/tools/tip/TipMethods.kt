package com.huntercoles.pokerpayout.tools.tip

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.huntercoles.pokerpayout.tools.R

/**
 * The ways to tip (PP-112), exactly as the repository lists them: the donation page that
 * `.github/FUNDING.yml` points to (`crypto/DONATIONS.md`), with its Ethereum and Monero addresses.
 * Never add one here that the repository doesn't list (`TipMethodsTest` checks): an account has to
 * exist before the app sends anyone to it.
 *
 * Each [qr] is the repository's own QR code (`crypto/ETH.png`, `crypto/XMR.png`) redrawn one pixel per
 * module, so it stays sharp at any size; `TipMethodsTest` decodes it back to [qrText].
 */
enum class TipCoin(
    /** "Ethereum (ETH)", its heading. */
    @StringRes val label: Int,
    /** "Ethereum", in what TalkBack reads. */
    @StringRes val coinName: Int,
    val address: String,
    @DrawableRes val qr: Int,
    /** What the QR code holds: the address, or a wallet link to it. */
    val qrText: String,
) {
    ETH(
        label = R.string.tip_eth,
        coinName = R.string.tip_eth_name,
        address = "0xb93CD995950AD1E016c9Bf173999A887Bc181fED",
        qr = R.drawable.tip_qr_eth,
        qrText = "0xb93CD995950AD1E016c9Bf173999A887Bc181fED",
    ),
    XMR(
        label = R.string.tip_xmr,
        coinName = R.string.tip_xmr_name,
        address = MONERO,
        qr = R.drawable.tip_qr_xmr,
        qrText = "monero:$MONERO",
    ),
    ;

    /**
     * The address in groups of four, to read and check against a wallet (and to wrap between groups,
     * never inside one). "0x" stands on its own. Copying always copies [address] as it is.
     */
    val grouped: String
        get() {
            val groups = address.removePrefix(HEX_PREFIX).chunked(GROUP)
            return (if (address.startsWith(HEX_PREFIX)) listOf(HEX_PREFIX) + groups else groups).joinToString(" ")
        }

    private companion object {
        const val GROUP = 4
        const val HEX_PREFIX = "0x"
    }
}

private const val MONERO =
    "4ANUxAZ5Ra3FewyyiHnKKahetsvXLMgocCwEiYeK1qDLjCjxSJS275XRvpZ1JRMWjvLS5xDYzwjESY2qF4UE4v1R2cz1QeU"

/** Pages the Tip the dealer screen opens in the browser (an intent: the app has no internet permission). */
enum class TipLink(val url: String) {
    /** Every way to tip, kept current without an app update. `.github/FUNDING.yml` links the same page. */
    DONATION_PAGE("https://github.com/HunterColes/PokerPayout/blob/master/crypto/DONATIONS.md"),

    /** The repository, to star. */
    REPOSITORY("https://github.com/HunterColes/PokerPayout"),

    /** The issue forms: a bug or an idea. */
    IDEAS("https://github.com/HunterColes/PokerPayout/issues/new/choose"),
}
