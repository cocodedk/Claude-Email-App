package com.cocode.claudeemailapp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Client-side store for conversation state that the wire protocol does
 * not carry: archived flag (and, in future, title overrides). Backed
 * by an unencrypted SharedPreferences — this is a UX preference, not
 * sensitive data.
 */
interface ConversationStateStore {
    fun loadArchivedIds(): Set<String>
    fun saveArchivedIds(ids: Set<String>)
    fun loadSyncIntervalMs(): Long
    fun saveSyncIntervalMs(ms: Long)
    fun loadHasSeenOnboarding(): Boolean
    fun markOnboardingSeen()

    companion object {
        operator fun invoke(context: Context): ConversationStateStore =
            SharedPrefsConversationStateStore(context)

        const val DEFAULT_SYNC_INTERVAL_MS = 60_000L
    }
}

internal class SharedPrefsConversationStateStore(
    private val prefs: SharedPreferences
) : ConversationStateStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun loadArchivedIds(): Set<String> =
        prefs.getStringSet(KEY_ARCHIVED, null)?.toSet() ?: emptySet()

    override fun saveArchivedIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_ARCHIVED, ids.toHashSet()).apply()
    }

    override fun loadSyncIntervalMs(): Long =
        prefs.getLong(KEY_SYNC_INTERVAL, ConversationStateStore.DEFAULT_SYNC_INTERVAL_MS)

    override fun saveSyncIntervalMs(ms: Long) {
        prefs.edit().putLong(KEY_SYNC_INTERVAL, ms).apply()
    }

    override fun loadHasSeenOnboarding(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_SEEN, false)

    override fun markOnboardingSeen() {
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
    }

    companion object {
        internal const val PREFS_NAME = "conversation_state"
        private const val KEY_ARCHIVED = "archived_ids"
        private const val KEY_SYNC_INTERVAL = "sync_interval_ms"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }
}
