package com.my.kizzy.feature_rpc_base.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "secure_discord_prefs",
        Context.MODE_PRIVATE
    )

    private object PreferencesKeys {
        val SECURE_TOKEN = "encrypted_discord_token"
    }

    private val cryptoManager = TinkCryptoManager(context)

    /**
     * Reactive token flow. Emits the current decrypted token value.
     * Uses flow { } to read from SharedPreferences on each collection.
     */
    val tokenFlow: Flow<String?> = flow {
        val encryptedToken = sharedPreferences.getString(PreferencesKeys.SECURE_TOKEN, null)
        emit(decryptToken(encryptedToken))
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = null
    )

    /**
     * Saves a Discord auth token, encrypting it via Tink/AES-256-GCM backed by Android Keystore.
     */
    suspend fun saveToken(token: String) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = cryptoManager.encrypt(token)
                val encodedCiphertext = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                sharedPreferences.edit().putString(PreferencesKeys.SECURE_TOKEN, encodedCiphertext).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Clears the stored token. */
    suspend fun clearToken() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().remove(PreferencesKeys.SECURE_TOKEN).apply()
        }
    }

    /** Decrypts a Base64-encoded ciphertext, returning null if decryption fails. */
    private fun decryptToken(cipherTextBase64: String?): String? {
        if (cipherTextBase64.isNullOrBlank()) return null
        return try {
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            cryptoManager.decrypt(cipherBytes)
        } catch (e: GeneralSecurityException) {
            // Decryption failed (e.g., master key changed) -> return null to force re-login
            null
        }
    }
}

/**
 * Google Tink wrapper anchored to Android Keystore.
 * Keyset is encrypted at rest using a hardware-backed master key.
 */
class TinkCryptoManager(context: Context) {

    private val aead: Aead

    init {
        // Register AEAD primitive configuration with Tink runtime
        AeadConfig.register()

        val masterKeyUri = "android-keystore://kizzy_master_key"
        val keysetFileName = "kizzy_secure_keyset"
        val preferenceFileName = "kizzy_keyset_prefs"

        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, keysetFileName, preferenceFileName)
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plainText: String): ByteArray {
        val associatedData = "kizzy_rpc_token_aad".toByteArray()
        return aead.encrypt(plainText.toByteArray(), associatedData)
    }

    fun decrypt(cipherText: ByteArray): String {
        val associatedData = "kizzy_rpc_token_aad".toByteArray()
        val decryptedBytes = aead.decrypt(cipherText, associatedData)
        return String(decryptedBytes)
    }
}
