package com.cocode.claudeemailapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-sensitive notification preferences. Currently just the master toggle
 * ("Notify on replies"). Plain SharedPreferences — no PII, no secrets.
 */
class InboxNotificationPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        const val PREFS_NAME = "notification_prefs"
        private const val KEY_ENABLED = "enabled"
    }
}
