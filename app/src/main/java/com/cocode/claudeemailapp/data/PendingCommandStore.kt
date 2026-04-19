package com.cocode.claudeemailapp.data

import android.content.Context
import android.content.SharedPreferences
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.EnvelopeJson
import com.cocode.claudeemailapp.protocol.Kinds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

interface PendingCommandStore {
    fun add(pending: PendingCommand)
    fun findByMessageId(messageId: String): PendingCommand?
    fun findByTaskId(taskId: Long): PendingCommand?
    fun all(): List<PendingCommand>
    fun clear()
    /**
     * Apply an inbound envelope. Returns the updated record if it matched something we were
     * tracking, or null otherwise (unknown reply — still worth rendering in the inbox UI).
     */
    fun applyInbound(envelope: Envelope, inReplyTo: String?): PendingCommand?

    companion object {
        operator fun invoke(context: Context): PendingCommandStore = SharedPrefsPendingCommandStore(context)
    }
}

internal class SharedPrefsPendingCommandStore(
    private val prefs: SharedPreferences
) : PendingCommandStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun add(pending: PendingCommand) {
        prefs.edit().putString(pending.messageId, EnvelopeJson.encodeToString(pending)).apply()
    }

    override fun findByMessageId(messageId: String): PendingCommand? {
        val raw = prefs.getString(messageId, null) ?: return null
        return try {
            EnvelopeJson.decodeFromString(PendingCommand.serializer(), raw)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findByTaskId(taskId: Long): PendingCommand? =
        all().firstOrNull { it.taskId == taskId }

    override fun all(): List<PendingCommand> =
        prefs.all.values.mapNotNull { raw ->
            (raw as? String)?.let {
                try { EnvelopeJson.decodeFromString(PendingCommand.serializer(), it) } catch (_: Throwable) { null }
            }
        }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun applyInbound(envelope: Envelope, inReplyTo: String?): PendingCommand? {
        val matched = inReplyTo?.let(::findByMessageId)
            ?: envelope.taskId?.let(::findByTaskId)
            ?: return null

        val now = System.currentTimeMillis()
        val updated = when (envelope.kind) {
            Kinds.ACK -> matched.copy(
                taskId = envelope.taskId ?: matched.taskId,
                branch = envelope.data.string("branch") ?: matched.branch,
                status = envelope.data.string("status") ?: PendingStatus.QUEUED,
                lastUpdatedAt = now
            )
            Kinds.PROGRESS -> matched.copy(
                taskId = envelope.taskId ?: matched.taskId,
                status = envelope.data.string("status") ?: PendingStatus.RUNNING,
                lastUpdatedAt = now
            )
            Kinds.QUESTION -> matched.copy(
                taskId = envelope.taskId ?: matched.taskId,
                status = PendingStatus.AWAITING_USER,
                askId = envelope.data.string("ask_id") ?: matched.askId,
                lastUpdatedAt = now
            )
            Kinds.RESULT -> {
                val reportedStatus = envelope.data.string("status")
                val status = when (reportedStatus) {
                    "completed", "done", "success" -> PendingStatus.DONE
                    "failed", "error" -> PendingStatus.FAILED
                    else -> reportedStatus ?: PendingStatus.DONE
                }
                matched.copy(
                    taskId = envelope.taskId ?: matched.taskId,
                    branch = envelope.data.string("branch") ?: matched.branch,
                    status = status,
                    lastUpdatedAt = now,
                    lastError = envelope.data.string("error") ?: matched.lastError
                )
            }
            Kinds.ERROR -> matched.copy(
                status = PendingStatus.ERROR,
                lastUpdatedAt = now,
                lastError = envelope.error?.let { "${it.code}: ${it.message}" } ?: matched.lastError
            )
            else -> matched
        }
        add(updated)
        return updated
    }

    private fun JsonObject?.string(key: String): String? = this?.get(key)?.let {
        runCatching { it.jsonPrimitive.content }.getOrNull()
    }

    companion object {
        internal const val PREFS_NAME = "pending_commands"
    }
}
