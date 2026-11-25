package com.j2me.multibox.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String
)

interface CryptoProvider {
    fun encrypt(plain: CharArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): CharArray
}

/**
 * Hardware-backed AES-256-GCM encryption using AndroidKeyStore.
 * Keys never leave the secure hardware/TEE and cannot be exported.
 */
class KeystoreCrypto(
    private val keyAlias: String = "j2me_multibox_vault_key_v1"
) : CryptoProvider {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plain: CharArray): EncryptedPayload {
        val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(plain))
        val plainBytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(plainBytes)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainBytes)

            EncryptedPayload(
                cipherTextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } finally {
            plainBytes.fill(0)
        }
    }

    override fun decrypt(payload: EncryptedPayload): CharArray {
        val cipherBytes = Base64.decode(payload.cipherTextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)

        return try {
            val charBuffer = Charsets.UTF_8.decode(ByteBuffer.wrap(plainBytes))
            CharArray(charBuffer.remaining()).also { charBuffer.get(it) }
        } finally {
            plainBytes.fill(0)
        }
    }
}
