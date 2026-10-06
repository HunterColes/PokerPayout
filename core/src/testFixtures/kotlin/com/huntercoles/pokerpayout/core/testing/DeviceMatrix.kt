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
     * The configs that get a golden image: every width at 1.0, plus the tight phones at 1.3 and
     * 2.0. (The layout assertions cover the rest of the matrix without storing pictures.)
     */
    val goldens: List<ScreenConfig> = listOf(
        ScreenConfig(Device.SmallPhone, 1.0f),
        ScreenConfig(Device.Phone, 1.0f),
        ScreenConfig(Device.TallPhone, 1.0f),
        ScreenConfig(Device.Foldable, 1.0f),
        ScreenConfig(Device.Tablet, 1.0f),
        ScreenConfig(Device.PhoneLandscape, 1.0f),
        ScreenConfig(Device.TabletLandscape, 1.0f),
        ScreenConfig(Device.Phone, 1.3f),
        ScreenConfig(Device.SmallPhone, 2.0f),
        ScreenConfig(Device.Phone, 2.0f),
    )

    /** For JUnit parameterized runners: one array per config. */
    fun parameters(configs: List<ScreenConfig>): List<Array<Any>> = configs.map { arrayOf(it) }
}
