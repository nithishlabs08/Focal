package com.focal.android.transport

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM payload protection derived from the pairing PIN (LAN pairing establishes the key).
 */
object FocalSessionCrypto {

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    fun deriveKey(pin: String): SecretKey {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest("FOCL:$pin".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(key: SecretKey, encrypted: ByteArray): ByteArray? {
        if (encrypted.size <= GCM_IV_BYTES) return null
        return try {
            val iv = encrypted.copyOfRange(0, GCM_IV_BYTES)
            val body = encrypted.copyOfRange(GCM_IV_BYTES, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(body)
        } catch (_: Exception) {
            null
        }
    }
}
