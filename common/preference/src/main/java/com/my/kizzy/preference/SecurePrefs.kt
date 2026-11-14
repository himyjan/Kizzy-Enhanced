/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * SecurePrefs.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.preference

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Encrypted storage for the Discord account token.
 *
 * A Discord user token is the master key of the user's account, so it must not
 * sit in plain text inside the preferences file. Everything else (themes, RPC
 * configuration, ...) stays in the regular [Prefs] store - only the secrets go
 * through here.
 *
 * The encryption key never leaves the Android Keystore: Tink generates it
 * there and only ever hands out the AEAD primitive, so the key material is
 * not readable by the app itself, let alone by an attacker with file access to
 * the app's data directory.
 *
 * Ciphertexts are stored with an [ENCRYPTED_PREFIX] marker so that a value
 * written by an older version of the app (plain text) can still be read and
 * is transparently re-encrypted the next time it is written.
 */
object SecurePrefs {

    private const val ENCRYPTED_PREFIX = "enc:"

    private const val KEYSET_NAME = "kizzy_secure_prefs"
    private const val MASTER_KEY_URI = "android-keystore://kizzy_secure_prefs_master_key"

    /**
     * Associated data of the AEAD. It is authenticated but not encrypted, so
     * it can be used to bind a ciphertext to its purpose.
     */
    private val ASSOCIATED_DATA = "kizzy_secure_prefs".toByteArray(Charsets.UTF_8)

    @Volatile
    private var aead: Aead? = null

    /**
     * Set when the keystore is not usable (for example on a heavily modified
     * emulator). The secrets are then stored in plain text, which is not
     * ideal, but it keeps the app working instead of locking the user out.
     */
    @Volatile
    private var encryptionUnavailable = false

    /**
     * Creates the encryption key in the Android Keystore if it does not exist
     * yet and prepares the AEAD primitive.
     */
    @Suppress("DEPRECATION")
    fun init(context: Context) {
        if (aead != null || encryptionUnavailable) return
        synchronized(this) {
            if (aead != null || encryptionUnavailable) return
            try {
                val keysetManager = AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, MASTER_KEY_URI)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .build()
                aead = keysetManager.getKeysetHandle().getPrimitive(Aead::class.java)
            } catch (_: Exception) {
                // The keystore can be unavailable on some devices/emulators.
                // Fall back to plain text storage instead of crashing.
                encryptionUnavailable = true
                aead = null
            }
        }
    }

    /**
     * Encrypts [plainText] and returns the value that should be written to
     * disk, or `null` when the encryption is not available.
     */
    fun encrypt(plainText: String): String? {
        val primitive = aead ?: return null
        return try {
            val cipherText = primitive.encrypt(
                plainText.toByteArray(Charsets.UTF_8),
                ASSOCIATED_DATA,
            )
            ENCRYPTED_PREFIX + Base64.encodeToString(cipherText, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts a value that was written by [encrypt].
     *
     * Values that were stored before the encryption was introduced are returned
     * unchanged, so existing installs keep working and the value is encrypted
     * again the next time it is written.
     */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(ENCRYPTED_PREFIX)) return stored
        val primitive = aead ?: return ""
        return try {
            val cipherText = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            String(primitive.decrypt(cipherText, ASSOCIATED_DATA), Charsets.UTF_8)
        } catch (_: Exception) {
            // The keystore was reset or the value was tampered with.
            ""
        }
    }

    /**
     * Whether the value is already stored in the encrypted format.
     */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(ENCRYPTED_PREFIX)
}
