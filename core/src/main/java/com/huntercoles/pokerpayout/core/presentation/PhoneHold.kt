package com.huntercoles.pokerpayout.core.presentation

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * How the phone is held, whatever the screen shows (PP-094 #2): the Tournament tab holds the clock
 * upright after ✕ in a turned table view, and lets the phone turn again once it is held upright.
 * While the app holds the screen portrait no configuration change says how the phone is held, so
 * this asks the system the way it would decide to turn the screen.
 */
interface PhoneHold {
    /** True while the phone is held upright, false while it is on its side; emits on each change it sees. */
    fun upright(context: Context): Flow<Boolean>
}

/** Turning [PhoneHold]'s readings into upright or on its side. */
object PhoneHoldRules {
    private const val FULL_TURN = 360
    private const val HALF_TURN = 180
    private const val QUARTER_TURN = 90

    /** Within this many degrees of upright (either way up) or of on its side; in between keeps the last. */
    private const val MARGIN = 30

    /**
     * From an [OrientationEventListener] angle (0 = upright, 90 = on its side): true upright, false on
     * its side, null in between or lying flat ([OrientationEventListener.ORIENTATION_UNKNOWN]).
     */
    fun fromAngle(degrees: Int): Boolean? {
        if (degrees < 0) return null
        val half = degrees % FULL_TURN % HALF_TURN
        return when {
            minOf(half, HALF_TURN - half) <= MARGIN -> true
            abs(half - QUARTER_TURN) <= MARGIN -> false
            else -> null
        }
    }

    /** From the rotation the user picked with auto-rotate off ([Surface] `ROTATION_*`): 0 and 180 are upright. */
    fun fromUserRotation(rotation: Int): Boolean = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
}

/**
 * The phone's hold as the system would turn the screen: by the accelerometer with auto-rotate on, by
 * the rotation the user picked (the rotation button, or `settings put system user_rotation`) with it off.
 */
object SystemPhoneHold : PhoneHold {
    override fun upright(context: Context): Flow<Boolean> = callbackFlow {
        val resolver = context.contentResolver
        var sensed: Boolean? = null
        fun send() {
            val autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            val picked = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            val upright = if (autoRotate) sensed else PhoneHoldRules.fromUserRotation(picked)
            upright?.let { trySend(it) }
        }
        val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = send()
        }
        val sensor = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                PhoneHoldRules.fromAngle(orientation)?.let { held ->
                    sensed = held
                    send()
                }
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, settings)
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.USER_ROTATION), false, settings)
        if (sensor.canDetectOrientation()) sensor.enable()
        send()
        awaitClose {
            sensor.disable()
            resolver.unregisterContentObserver(settings)
        }
    }
}

/** How the phone is held; tests provide their own. */
val LocalPhoneHold = staticCompositionLocalOf<PhoneHold> { SystemPhoneHold }

/** A phone held upright this long counts as turned upright: a pass through upright while turning doesn't. */
const val UPRIGHT_HOLD_MILLIS = 1_000L

/**
 * While [enabled], calls [onUpright] once the phone has been held upright for [UPRIGHT_HOLD_MILLIS]
 * ([LocalPhoneHold]). Works while the app holds the screen portrait, when the screen itself can't tell.
 */
@Composable
fun OnPhoneUpright(enabled: Boolean, onUpright: () -> Unit) {
    val context = LocalContext.current
    val hold = LocalPhoneHold.current
    val latest by rememberUpdatedState(onUpright)
    LaunchedEffect(enabled, hold, context) {
        if (!enabled) return@LaunchedEffect
        hold.upright(context).distinctUntilChanged().collectLatest { upright ->
            if (upright) {
                delay(UPRIGHT_HOLD_MILLIS)
                latest()
            }
        }
    }
}
