package com.huntercoles.pokerpayout.core.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How the phone is held (PP-094 #2), from the accelerometer's angle and from the user's rotation. */
class PhoneHoldRulesTest {

    @Test
    fun `within 30 degrees of upright, either way up, is upright`() {
        listOf(0, 15, 30, 330, 359, 150, 180, 210).forEach { angle ->
            assertEquals(true, PhoneHoldRules.fromAngle(angle), "$angle degrees")
        }
    }

    @Test
    fun `within 30 degrees of either side is on its side`() {
        listOf(60, 90, 120, 240, 270, 300).forEach { angle ->
            assertEquals(false, PhoneHoldRules.fromAngle(angle), "$angle degrees")
        }
    }

    @Test
    fun `in between, or lying flat, says nothing`() {
        listOf(31, 45, 59, 135, 225, 315).forEach { angle -> assertNull(PhoneHoldRules.fromAngle(angle), "$angle degrees") }
        assertNull(PhoneHoldRules.fromAngle(-1)) // OrientationEventListener.ORIENTATION_UNKNOWN
    }

    @Test
    fun `with auto-rotate off, the rotation the user picked says it`() {
        assertTrue(PhoneHoldRules.fromUserRotation(0))
        assertFalse(PhoneHoldRules.fromUserRotation(1))
        assertTrue(PhoneHoldRules.fromUserRotation(2))
        assertFalse(PhoneHoldRules.fromUserRotation(3))
    }
}
