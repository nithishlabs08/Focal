package com.focal.android.transport

/**
 * Manages short-lived pairing PIN codes for Wi-Fi and ADB stream authentication.
 * Enforces expiration to harden transport pairing without requiring QR scanning.
 */
object PairingManager {

    const val DEFAULT_VALIDITY_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    @Volatile
    var currentPin: String = "849207"
        private set

    @Volatile
    var pinExpiryEpochMs: Long = System.currentTimeMillis() + DEFAULT_VALIDITY_DURATION_MS
        private set

    fun generateNewPin(validityMs: Long = DEFAULT_VALIDITY_DURATION_MS): String {
        val newPin = (100000..999999).random().toString()
        currentPin = newPin
        pinExpiryEpochMs = System.currentTimeMillis() + validityMs
        return newPin
    }

    fun setPin(pin: String, validityMs: Long = DEFAULT_VALIDITY_DURATION_MS) {
        currentPin = pin
        pinExpiryEpochMs = System.currentTimeMillis() + validityMs
    }

    fun isPinValid(candidatePin: String?): Boolean {
        if (candidatePin.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        return candidatePin == currentPin && now <= pinExpiryEpochMs
    }

    fun getTimeRemainingSeconds(): Long {
        val remaining = pinExpiryEpochMs - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000L else 0L
    }
}
