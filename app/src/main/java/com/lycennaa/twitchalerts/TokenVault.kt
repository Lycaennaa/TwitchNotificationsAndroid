package com.lycaennaa.twitchalerts

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.locks.ReentrantLock

internal object TwitchAccountStateGuard {
    private val lock = ReentrantLock()

    fun acquire() = lock.lock()

    fun release() = lock.unlock()
}

internal class TokenVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Throws(TokenVaultException::class)
    fun save(token: String) {
        if (token.isBlank()) throw TokenVaultException("Enter a Twitch OAuth token.")
        TwitchAccountStateGuard.acquire()
        try {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
                val packed = cipher.iv + encrypted
                val stored = Base64.encodeToString(packed, Base64.NO_WRAP)
                if (!preferences.edit().putString(KEY_CIPHERTEXT, stored).commit()) {
                    throw TokenVaultException("Could not save the encrypted Twitch token.")
                }
            } catch (exception: TokenVaultException) {
                throw exception
            } catch (exception: Exception) {
                throw TokenVaultException("Could not encrypt the Twitch token.", exception)
            }
        } finally {
            TwitchAccountStateGuard.release()
        }
    }

    @Throws(TokenVaultException::class)
    fun read(): String? {
        val stored = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        try {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            if (packed.size <= GCM_IV_BYTES) {
                throw GeneralSecurityException("Encrypted token data is incomplete.")
            }
            val iv = packed.copyOfRange(0, GCM_IV_BYTES)
            val encrypted = packed.copyOfRange(GCM_IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (exception: Exception) {
            throw TokenVaultException(
                "The saved token could not be decrypted. Enter it again.",
                exception,
            )
        }
    }

    @Throws(TokenVaultException::class)
    fun clear() {
        try {
            TwitchAccountStateGuard.acquire()
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
                if (!preferences.edit().remove(KEY_CIPHERTEXT).commit()) {
                    throw TokenVaultException("Could not remove the saved Twitch token.")
                }
            } finally {
                TwitchAccountStateGuard.release()
            }
        } catch (exception: TokenVaultException) {
            throw exception
        } catch (exception: Exception) {
            throw TokenVaultException("Could not remove the saved Twitch token.", exception)
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "twitch_oauth_secret"
        private const val KEY_CIPHERTEXT = "oauth_token"
        private const val KEY_ALIAS = "twitch_oauth_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

internal class TokenVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)
