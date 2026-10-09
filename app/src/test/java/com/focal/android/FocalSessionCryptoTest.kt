package com.focal.android

import com.focal.android.transport.FocalSessionCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocalSessionCryptoTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val key = FocalSessionCrypto.deriveKey("482910")
        val plain = byteArrayOf(0, 1, 2, 3, 4, 5)
        val encrypted = FocalSessionCrypto.encrypt(key, plain)
        assertFalse(encrypted.contentEquals(plain))
        val decrypted = FocalSessionCrypto.decrypt(key, encrypted)
        assertTrue(decrypted?.contentEquals(plain) == true)
    }

    @Test
    fun decrypt_failsWithWrongPin() {
        val keyA = FocalSessionCrypto.deriveKey("111111")
        val keyB = FocalSessionCrypto.deriveKey("222222")
        val encrypted = FocalSessionCrypto.encrypt(keyA, byteArrayOf(9, 8, 7))
        assertTrue(FocalSessionCrypto.decrypt(keyB, encrypted) == null)
    }
}
