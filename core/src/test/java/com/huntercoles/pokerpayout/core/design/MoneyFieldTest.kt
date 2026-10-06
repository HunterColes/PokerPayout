package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.huntercoles.pokerpayout.core.design.components.MoneyField
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MoneyField: typed text is kept as typed, amounts go out as cents, and leaving the field commits
 * once (empty is "no amount", 0 is an amount).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyFieldTest {
    @get:Rule
    val screen = ScreenTestRule(ScreenConfig(Device.Phone, 1.0f))
    private val rule get() = screen.compose

    private val typed = mutableListOf<Long?>()
    private val committed = mutableListOf<Long?>()
    private var saved by mutableStateOf<Long?>(null)
    private var shown by mutableStateOf(true)

    private fun show(start: Long?) {
        saved = start
        rule.setContent {
            PokerTheme(reducedMotion = true) {
                Column {
                    if (shown) {
                        MoneyField(
                            valueCents = saved,
                            label = "Chips counted out",
                            onValueChange = { typed += it },
                            onCommit = {
                                committed += it
                                saved = it
                            },
                            description = "Dana's chips",
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private val field get() = rule.onNodeWithContentDescription("Dana's chips")

    private fun fieldText(): String = field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun keyByKeyTheTextStaysAsTypedAndDoneCommitsOnce() {
        show(null)
        assertEquals("", fieldText())
        listOf("1", "2", ".", "5", "0").forEach { field.performTextInput(it) }
        assertEquals("12.50", fieldText())
        assertEquals(listOf(100L, 1_200L, 1_200L, 1_250L, 1_250L), typed)
        field.performImeAction()
        rule.waitForIdle()
        assertEquals(listOf<Long?>(1_250L), committed)
        assertEquals("12.50", fieldText())
    }

    @Test
    fun zeroIsAnAmountAndEmptyIsNone() {
        show(0L)
        assertEquals("0", fieldText())
        field.performTextReplacement("")
        field.performImeAction()
        rule.waitForIdle()
        assertEquals(listOf<Long?>(null), committed)
        assertEquals("", fieldText())
    }

    @Test
    fun anUnchangedAmountIsNotCommittedAndBadKeysAreRefused() {
        show(4_000L)
        field.performTextReplacement("40")
        field.performTextInput("x")
        field.performTextInput(",")
        listOf("1", "2", "3").forEach { field.performTextInput(it) }
        assertEquals("40,12", fieldText())
        field.performTextReplacement("40")
        field.performImeAction()
        rule.waitForIdle()
        assertEquals(emptyList<Long?>(), committed)
    }

    @Test
    fun anOutsideChangeShowsAndClosingWhileTypingCommits() {
        show(1_000L)
        saved = 2_550L // Undo, say
        rule.waitForIdle()
        assertEquals("25.50", fieldText())
        field.performTextReplacement("30")
        // The sheet closes with the field still focused
        shown = false
        rule.waitForIdle()
        assertEquals(listOf<Long?>(3_000L), committed)
    }
}
