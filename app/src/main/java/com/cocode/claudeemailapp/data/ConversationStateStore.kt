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

    companion object {
        operator fun invoke(context: Context): ConversationStateStore =
            SharedPrefsConversationStateStore(context)
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

    companion object {
        internal const val PREFS_NAME = "conversation_state"
        private const val KEY_ARCHIVED = "archived_ids"
    }
}
