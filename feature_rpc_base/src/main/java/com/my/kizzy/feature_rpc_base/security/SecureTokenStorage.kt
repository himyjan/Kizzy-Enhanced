package com.my.kizzy.feature_rpc_base.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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

    companion object {
        private const val KEY_DISCORD_TOKEN = "discord_auth_token"
    }

    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_DISCORD_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_DISCORD_TOKEN, null)
    }

    fun clearToken() {
        sharedPreferences.edit().remove(KEY_DISCORD_TOKEN).apply()
    }
}
