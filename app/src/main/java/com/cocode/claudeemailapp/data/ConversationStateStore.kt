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
    fun loadRecentProjects(): List<String>
    fun pushRecentProject(project: String)

    /** Wipe all per-account UX state on sign-out so prefs do not leak across users. */
    fun clear()

    companion object {
        operator fun invoke(context: Context): ConversationStateStore =
            SharedPrefsConversationStateStore(context)

        const val DEFAULT_SYNC_INTERVAL_MS = 60_000L
        const val RECENT_PROJECTS_CAP = 5
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

    override fun loadRecentProjects(): List<String> {
        val raw = prefs.getString(KEY_RECENT_PROJECTS, null) ?: return emptyList()
        return raw.split('').filter { it.isNotBlank() }
    }

    override fun pushRecentProject(project: String) {
        val trimmed = project.trim()
        if (trimmed.isBlank()) return
        val existing = loadRecentProjects()
        // Most-recent-first, de-duplicated, capped.
        val next = (listOf(trimmed) + existing.filter { it != trimmed })
            .take(ConversationStateStore.RECENT_PROJECTS_CAP)
        prefs.edit().putString(KEY_RECENT_PROJECTS, next.joinToString("")).apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ARCHIVED)
            .remove(KEY_SYNC_INTERVAL)
            .remove(KEY_RECENT_PROJECTS)
            .apply()
    }

    companion object {
        internal const val PREFS_NAME = "conversation_state"
        private const val KEY_ARCHIVED = "archived_ids"
        private const val KEY_SYNC_INTERVAL = "sync_interval_ms"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_RECENT_PROJECTS = "recent_projects"
    }
}
