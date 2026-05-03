package com.cocode.claudeemailapp.protocol

import com.cocode.claudeemailapp.mail.OutgoingMessage
import kotlinx.serialization.encodeToString
import java.time.Instant

/** Build an OutgoingMessage whose body is the given envelope serialized as JSON. */
fun OutgoingMessage.Companion.envelope(
    to: String,
    subject: String,
    envelope: Envelope,
    inReplyTo: String? = null,
    references: List<String> = emptyList(),
    extraHeaders: Map<String, String> = emptyMap()
): OutgoingMessage {
    val body = EnvelopeJson.encodeToString(envelope)
    val headers = buildMap {
        put(CLIENT_HEADER, envelope.meta.client ?: CLIENT_ID)
        putAll(extraHeaders)
    }
    return OutgoingMessage(
        to = to,
        subject = subject,
        body = body,
        contentType = ENVELOPE_CONTENT_TYPE,
        inReplyTo = inReplyTo,
        references = references,
        extraHeaders = headers
    )
}

/** Convenience constructor for the most common app→backend kinds. */
object Envelopes {
    fun command(
        body: String,
        project: String,
        priority: Int? = null,
        planFirst: Boolean? = null,
        preferLiveAgent: Boolean? = null,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.COMMAND,
        body = body,
        project = project,
        priority = priority,
        planFirst = planFirst,
        meta = EnvelopeMeta(
            client = client,
            sentAt = sentAt,
            auth = auth,
            preferLiveAgent = preferLiveAgent
        )
    )

    fun reply(
        taskId: Long,
        body: String,
        askId: Long? = null,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.REPLY,
        taskId = taskId,
        body = body,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth, askId = askId)
    )

    fun status(
        taskId: Long? = null,
        project: String? = null,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.STATUS,
        taskId = taskId,
        project = project,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )

    /**
     * Discovery query: backend enumerates git repos under allowed_base + merges
     * task-history state per project. Response is an `ack` carrying
     * `data.projects: [{name, path, running_task_id, queue_depth, last_activity_at}]`.
     */
    fun listProjects(
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.LIST_PROJECTS,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )

    fun cancel(
        project: String,
        drainQueue: Boolean = false,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.CANCEL,
        project = project,
        drainQueue = drainQueue,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )

    fun retry(
        taskId: Long,
        newBody: String? = null,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.RETRY,
        taskId = taskId,
        newBody = newBody,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )

    fun commit(
        project: String,
        body: String,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.COMMIT,
        body = body,
        project = project,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )

    fun reset(
        project: String,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.RESET,
        project = project,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )

    fun confirmReset(
        project: String,
        token: String,
        auth: String? = null,
        client: String = CLIENT_ID,
        sentAt: String = Instant.now().toString()
    ): Envelope = Envelope(
        kind = Kinds.CONFIRM_RESET,
        project = project,
        token = token,
        meta = EnvelopeMeta(client = client, sentAt = sentAt, auth = auth)
    )
}
