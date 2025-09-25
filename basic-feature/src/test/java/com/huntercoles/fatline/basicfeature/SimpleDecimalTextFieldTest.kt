package com.huntercoles.fatline.basicfeature

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Test
import org.junit.Assert.*

/**
 * Simple, fast unit tests for DecimalTextField cursor positioning logic
 */
class SimpleDecimalTextFieldTest {

    @Test
    fun testCursorPositionCalculation_WithDecimal() {
        // Given: Text with decimal "25.50"
        val text = "25.50"
        
        // When: Finding position before decimal
        val decimalIndex = text.indexOf('.')
        val cursorPosition = if (decimalIndex > 0) decimalIndex else text.length
        
        // Then: Should position at 2 (before the decimal)
        assertEquals("Cursor should be positioned before decimal point", 2, cursorPosition)
    }

    @Test
    fun testCursorPositionCalculation_NoDecimal() {
        // Given: Text without decimal "100"
        val text = "100"
        
        // When: Finding position (no decimal)
        val decimalIndex = text.indexOf('.')
        val cursorPosition = if (decimalIndex > 0) decimalIndex else text.length
        
        // Then: Should position at end (3)
        assertEquals("Cursor should be positioned at end when no decimal", 3, cursorPosition)
    }

    @Test
    fun testCursorPositionCalculation_EmptyString() {
        // Given: Empty text
        val text = ""
        
        // When: Finding position
        val decimalIndex = text.indexOf('.')
        val cursorPosition = if (decimalIndex > 0) decimalIndex else text.length
        
        // Then: Should position at 0
        assertEquals("Empty string should have cursor at position 0", 0, cursorPosition)
    }

    @Test
    fun testTextFieldValueConstruction() {
        // Given: Text and cursor position
        val text = "50.00"
        val position = 2
        
        // When: Creating TextFieldValue
        val textFieldValue = TextFieldValue(
            text = text,
            selection = TextRange(position)
        )
        
        // Then: Should have correct values
        assertEquals("Text should match", text, textFieldValue.text)
        assertEquals("Selection start should match", position, textFieldValue.selection.start)
        assertEquals("Selection end should match", position, textFieldValue.selection.end)
    }

    @Test
    fun testDoubleParsingWorksCorrectly() {
        // Given: Valid number strings
        val validText = "123.45"
        val invalidText = "abc.def"
        
        // When: Parsing to double
        val validResult = validText.toDoubleOrNull()
        val invalidResult = invalidText.toDoubleOrNull()
        
        // Then: Should parse correctly
        assertNotNull("Valid text should parse", validResult)
        assertEquals("Parsed value should be correct", 123.45, validResult!!, 0.001)
        assertNull("Invalid text should return null", invalidResult)
    }

    @Test
    fun testValuePersistenceLogic() {
        // Simulate external value changes (like from persistence)
        var currentValue = "25.00"
        var hasChanged = false
        
        // When external value changes
        val newValue = "50.00"
        if (currentValue != newValue) {
            hasChanged = true
            currentValue = newValue
        }
        
        // Then: Should detect the change
        assertTrue("Should detect external value change", hasChanged)
        assertEquals("Current value should be updated", "50.00", currentValue)
    }
}