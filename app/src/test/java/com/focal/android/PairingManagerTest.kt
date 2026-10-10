package com.focal.android

import com.focal.android.transport.PairingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingManagerTest {

    @Test
    fun isPinValid_matchesCurrentPinBeforeExpiry() {
        PairingManager.setPin("123456", validityMs = 60_000L)
        assertTrue(PairingManager.isPinValid("123456"))
        assertFalse(PairingManager.isPinValid("000000"))
    }

    @Test
    fun isPinValid_rejectsExpiredPin() {
        PairingManager.setPin("654321", validityMs = 1L)
        Thread.sleep(5)
        assertFalse(PairingManager.isPinValid("654321"))
    }

    @Test
    fun generateNewPin_returnsValidPinDigits() {
        val pin = PairingManager.generateNewPin()
        assertEquals(PairingManager.PIN_LENGTH, pin.length)
        assertTrue(pin.all { it.isDigit() })
        assertTrue(PairingManager.isPinValid(pin))
    }
}
