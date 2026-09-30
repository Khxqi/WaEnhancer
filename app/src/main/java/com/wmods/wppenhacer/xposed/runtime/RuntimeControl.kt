package com.wmods.wppenhacer.xposed.runtime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.BuildConfig
import java.security.MessageDigest
import java.util.UUID

object RuntimeControl {
    const val TOKEN_KEY = "runtime_control_token"
    const val TOKEN_EXTRA = "runtime_control_token"
    const val ACTION_RESTART = "${BuildConfig.APPLICATION_ID}.WHATSAPP.RESTART"
    const val ACTION_CHECK = "${BuildConfig.APPLICATION_ID}.CHECK_WPP"
    const val ACTION_MANUAL_RESTART = "${BuildConfig.APPLICATION_ID}.MANUAL_RESTART"
    const val ACTION_RUNTIME_STATE = "${BuildConfig.APPLICATION_ID}.RECEIVER_WPP"

    fun ensureToken(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(TOKEN_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        prefs.edit().putString(TOKEN_KEY, token).commit()
        makePreferencesReadable(context)
        return token
    }

    fun token(preferences: SharedPreferences): String? =
        preferences.getString(TOKEN_KEY, null)?.takeIf { it.length >= 32 }

    fun authenticate(intent: Intent, expectedToken: String?): Boolean {
        if (expectedToken.isNullOrBlank()) return false
        val supplied = intent.getStringExtra(TOKEN_EXTRA) ?: return false
        return MessageDigest.isEqual(
            expectedToken.toByteArray(Charsets.UTF_8),
            supplied.toByteArray(Charsets.UTF_8)
        )
    }

    fun addToken(intent: Intent, context: Context): Intent =
        intent.putExtra(TOKEN_EXTRA, ensureToken(context))

    fun makePreferencesReadable(context: Context) {
        runCatching {
            val file = context.getSharedPreferencesPath(BuildConfig.APPLICATION_ID + "_preferences.xml")
            file.setReadable(true, false)
            file.parentFile?.setExecutable(true, false)
        }
    }
}
