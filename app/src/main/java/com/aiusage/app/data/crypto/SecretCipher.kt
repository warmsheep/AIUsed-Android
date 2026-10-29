package com.aiusage.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small AES-GCM helper backed by the Android Keystore. The key material never
 * leaves the keystore; only ciphertext is written to DataStore.
 */
class SecretCipher {
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(2 + iv.size + cipherText.size)
        payload[0] = FORMAT_VERSION
        payload[1] = iv.size.toByte()
        iv.copyInto(payload, destinationOffset = 2)
        cipherText.copyInto(payload, destinationOffset = 2 + iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Returns null when the keystore entry was invalidated or the payload is corrupt. */
    fun decrypt(encoded: String): String? {
        if (encoded.isBlank()) return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size < 3 + 12 || payload[0] != FORMAT_VERSION) return null
            val ivSize = payload[1].toInt() and 0xFF
            if (ivSize < 12 || payload.size <= 2 + ivSize) return null
            val iv = payload.copyOfRange(2, 2 + ivSize)
            val cipherText = payload.copyOfRange(2 + ivSize, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(cipherText).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aiusage_secret_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
    }
}
