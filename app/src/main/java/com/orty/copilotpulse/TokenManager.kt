package com.orty.copilotpulse

import android.content.Context
import android.content.SharedPreferences

object TokenManager {

    private const val PREFS_NAME = "copilot_pulse_credentials"
    private const val KEY_TOKEN = "github_token"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCredentials(context: Context): Boolean =
        getPrefs(context).getString(KEY_TOKEN, null) != null

    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).commit()
    }

    fun getToken(context: Context): String? =
        getPrefs(context).getString(KEY_TOKEN, null)

    fun clearCredentials(context: Context) {
        getPrefs(context).edit().clear().commit()
    }

    fun getMaskedToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_TOKEN, null) ?: return null
        if (token.length < 8) return "****"
        return "${token.take(4)}...${token.takeLast(4)}"
    }
}
