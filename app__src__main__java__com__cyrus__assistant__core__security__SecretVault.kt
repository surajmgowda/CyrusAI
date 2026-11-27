package com.cyrus.assistant.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore-backed encrypted secret storage for provider API keys.
 * Raw keys are never written to Room, DataStore plaintext, or diagnostic logs.
 */
@Singleton
class SecretVault @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("cyrus_vault_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "cyrus_master_aes_key"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveSecret(alias: String, secret: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(alias, payload).apply()
    }

    fun readSecret(alias: String): String? {
        val payload = prefs.getString(alias, null) ?: return null
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        if (bytes.size <= 12) return null
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun deleteSecret(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    fun hasSecret(alias: String): Boolean = prefs.contains(alias)
}
