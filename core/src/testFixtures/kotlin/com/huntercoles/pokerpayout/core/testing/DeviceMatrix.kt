package com.huntercoles.pokerpayout.core.testing

import java.util.Locale

/**
 * The screens every component and screen is rendered on (PP-078 layer 1). Sizes are in dp, so
 * they match real phones whatever their density; everything renders at xhdpi (2x).
 */
enum class Device(val id: String, val widthDp: Int, val heightDp: Int) {
    /** Small phone (Galaxy A0x class, iPhone SE width). The tightest width we support. */
    SmallPhone("small", 320, 640),

    /** Standard phone. The mockup frames are this size, so goldens compare 1:1 with them. */
    Phone("phone", 360, 780),

    /** Tall phone (Pixel 7, 412 x 915). */
    TallPhone("tall", 412, 915),

    /** 7 to 8 inch tablet, or a foldable opened flat. */
    Foldable("foldable", 600, 960),

    /** 10 inch tablet. */
    Tablet("tablet", 800, 1280),

    /** Rotated small phone. The shortest height we support. */
    SmallPhoneLandscape("small-land", 640, 320),

    /** Rotated standard phone (the table view and run-it-out are designed at this size). */
    PhoneLandscape("phone-land", 780, 360),

    /** Rotated 10 inch tablet. */
    TabletLandscape("tablet-land", 1280, 800),
    ;

    val isLandscape: Boolean get() = widthDp > heightDp
}

/**
 * One cell of the matrix: a [device] at a system [fontScale]. Font scale 2.0 is Android 14's
 * largest setting; Robolectric runs SDK 34, so large text scales non-linearly exactly as on a phone.
 */
data class ScreenConfig(val device: Device, val fontScale: Float) {
    /** File-name-safe id, e.g. `phone-360x780_font1.3`. */
    val id: String = String.format(Locale.US, "%s-%dx%d_font%.1f", device.id, device.widthDp, device.heightDp, fontScale)

    /** Robolectric qualifiers: size, orientation, density and a pinned locale. */
    val qualifiers: String =
        "en-rUS-w${device.widthDp}dp-h${device.heightDp}dp-${if (device.isLandscape) "land" else "port"}-xhdpi"

    override fun toString(): String = id
}

object DeviceMatrix {
    val fontScales: List<Float> = listOf(1.0f, 1.3f, 2.0f)

    /** Every device at every font scale: 24 configs. Layout assertions run on all of them. */
    val all: List<ScreenConfig> = Device.entries.flatMap { device -> fontScales.map { ScreenConfig(device, it) } }

    /**
     * The configs that get a golden image (PP-090: six, down from ten). The layout assertions cover all
     * 24 cells without pictures; a picture is kept only where it shows a layout no other cell shows,
     * because every golden ever committed stays in the history F-Droid clones to build.
     * - `small` at 2.0: under 360 dp (12 dp gutters, the Bank's folded columns, the bell in the menu)
     *   at the largest font, where lines wrap, stats stack and the Bank's header keeps only its icons.
     *   The tightest cell.
     * - `phone` at 1.0: the layouts as drawn, 1:1 with the mockups.
     * - `phone` at 1.3: between the font thresholds (one knockout choice a row and stacked sums above
     *   1.15; the Bank header's words up to 1.3).
     * - `tablet` (800 dp, upright): the rail, with a 704 dp column, so Hand ranks shows two columns and
     *   Chip set two panes.
     * - `phone-land`: a phone on its side, the shortest common height: the table view, the odds keypad
     *   beside the cards, run it out's two panes, the rail.
     * - `tablet-land`: the only cell from 840 dp: the two-pane clock and Bank (Z4, Z5), the 720 dp column.
     *
     * Left out: `tall` (the `phone` layouts with more room), `foldable` (the rail beside a 504 dp phone
     * column), `small` at 1.0 and `phone` at 2.0 (`small` at 2.0 is tighter than both) and `small-land`.
     * Goldens drawn for one of those cells on purpose are listed in [pinned].
     */
    val goldens: List<ScreenConfig> = listOf(
        ScreenConfig(Device.SmallPhone, 2.0f),
        ScreenConfig(Device.Phone, 1.0f),
        ScreenConfig(Device.Phone, 1.3f),
        ScreenConfig(Device.Tablet, 1.0f),
        ScreenConfig(Device.PhoneLandscape, 1.0f),
        ScreenConfig(Device.TabletLandscape, 1.0f),
    )

    /**
     * Named goldens drawn for particular cells on purpose (a mockup's frame, the largest font), recorded
     * on exactly these cells whether or not [goldens] lists them. `scripts/dev/retired-goldens.sh` reads
     * this map and [goldens] to find the images no test records any more, so keep one entry a line.
     */
    val pinned: Map<String, List<ScreenConfig>> = mapOf(
        "Z1_clock_small" to listOf(ScreenConfig(Device.SmallPhone, 1.0f)),
        "Z2_bank_small" to listOf(ScreenConfig(Device.SmallPhone, 1.0f), ScreenConfig(Device.SmallPhone, 2.0f)),
        "Z3_table_small_land" to listOf(ScreenConfig(Device.SmallPhoneLandscape, 1.0f)),
        "Z4_clock_tablet" to listOf(ScreenConfig(Device.TabletLandscape, 1.0f)),
        "Z5_bank_tablet" to listOf(ScreenConfig(Device.TabletLandscape, 1.0f)),
        "S2_clock_running_font2x" to listOf(ScreenConfig(Device.TallPhone, 2.0f)),
        "S5_bank_font2x" to listOf(ScreenConfig(Device.SmallPhone, 2.0f), ScreenConfig(Device.Phone, 2.0f)),
        "S6_payouts_font2x" to listOf(ScreenConfig(Device.SmallPhone, 2.0f), ScreenConfig(Device.Phone, 2.0f)),
        "S8_odds_font2x" to listOf(ScreenConfig(Device.TallPhone, 2.0f)),
        "S10_runout_land" to listOf(ScreenConfig(Device.SmallPhoneLandscape, 1.0f)),
        "S14_seats_font2x" to listOf(ScreenConfig(Device.TallPhone, 2.0f)),
    )

    /** True if the pinned golden [name] is recorded on [config]. Fails on a name [pinned] doesn't list. */
    fun isPinned(name: String, config: ScreenConfig): Boolean = config in pinned.getValue(name)

    /** For JUnit parameterized runners: one array per config. */
    fun parameters(configs: List<ScreenConfig>): List<Array<Any>> = configs.map { arrayOf(it) }
}
