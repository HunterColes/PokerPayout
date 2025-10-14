package com.huntercoles.pokerpayout.tournament

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for DecimalTextField cursor positioning logic
 * 
 * Tests the mathematical logic for cursor positioning without UI dependencies
 */
class DecimalTextFieldLogicTest {

    @Test
    fun testCursorPositioning_WithDecimal_PositionsBeforeDecimal() {
        // Given: Text with decimal point
        val text = "25.50"
        
        // When: Calculating ideal cursor position
        val decimalIndex = text.indexOf('.')
        val idealPosition = if (decimalIndex > 0) decimalIndex else text.length
        
        // Then: Position should be before decimal
        assertEquals(2, idealPosition)
    }

    @Test
    fun testCursorPositioning_NoDecimal_PositionsAtEnd() {
        // Given: Text without decimal point
        val text = "100"
        
        // When: Calculating ideal cursor position
        val decimalIndex = text.indexOf('.')
        val idealPosition = if (decimalIndex > 0) decimalIndex else text.length
        
        // Then: Position should be at end
        assertEquals(3, idealPosition)
    }

    @Test
    fun testCursorPositioning_EmptyText_PositionsAtZero() {
        // Given: Empty text
        val text = ""
        
        // When: Calculating ideal cursor position
        val decimalIndex = text.indexOf('.')
        val idealPosition = if (decimalIndex > 0) decimalIndex else text.length
        
        // Then: Position should be at start
        assertEquals(0, idealPosition)
    }

    @Test
    fun testValueConversion_ValidDouble_Converts() {
        // Given: Valid numeric string
        val text = "123.45"
        
        // When: Converting to double
        val result = text.toDoubleOrNull()
        
        // Then: Should convert successfully
        assertNotNull(result)
        assertEquals(123.45, result!!, 0.001)
    }

    @Test
    fun testValueConversion_InvalidText_ReturnsNull() {
        // Given: Invalid numeric string
        val text = "abc"
        
        // When: Converting to double
        val result = text.toDoubleOrNull()
        
        // Then: Should return null
        assertNull(result)
    }

    @Test
    fun testTextFieldValueCreation_WithCursorPosition() {
        // Given: Text and cursor position
        val text = "50.00"
        val cursorPosition = 2
        
        // When: Creating TextFieldValue with selection
        val textFieldValue = TextFieldValue(
            text = text,
            selection = TextRange(cursorPosition)
        )
        
        // Then: Values should be set correctly
        assertEquals(text, textFieldValue.text)
        assertEquals(cursorPosition, textFieldValue.selection.start)
        assertEquals(cursorPosition, textFieldValue.selection.end)
        assertTrue(textFieldValue.selection.collapsed)
    }

    @Test
    fun testChangeDetection_TextVsCursor() {
        // Given: Two TextFieldValue instances
        val original = TextFieldValue("25.00", TextRange(2))
        val textChange = TextFieldValue("30.00", TextRange(2))
        val cursorChange = TextFieldValue("25.00", TextRange(5))
        
        // When: Checking for changes
        val isTextChange1 = textChange.text != original.text
        val isTextChange2 = cursorChange.text != original.text
        val isCursorChange1 = !isTextChange1 && textChange.selection != original.selection
        val isCursorChange2 = !isTextChange2 && cursorChange.selection != original.selection
        
        // Then: Should correctly identify change types
        assertTrue("Should detect text change", isTextChange1)
        assertFalse("Should not detect text change", isTextChange2)
        assertFalse("Should not detect cursor change when text changed", isCursorChange1)
        assertTrue("Should detect cursor change", isCursorChange2)
    }
}