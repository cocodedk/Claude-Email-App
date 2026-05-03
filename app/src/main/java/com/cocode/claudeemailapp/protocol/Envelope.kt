package com.cocode.claudeemailapp.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Claude-email v1 envelope. Wire format agreed with backend (agent-claude-email).
 *
 *   {"v":1, "kind":"...", "task_id":42, "body":"...", "meta":{...}, "data":{...}}
 *
 * - body is always human-readable (for UI)
 * - data is for programmatic action (typed per kind)
 * - inbound kinds (app→backend): command, reply, status, cancel, retry, commit, reset, confirm_reset
 * - outbound kinds (backend→app): ack, progress, question, result, error
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Envelope(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val v: Int = 1,
    val kind: String,
    @SerialName("task_id") val taskId: Long? = null,
    val body: String = "",
    /** command kind: target project path. */
    val project: String? = null,
    /** command kind: priority 0–10. */
    val priority: Int? = null,
    /** command kind: ask the agent to plan before running. */
    @SerialName("plan_first") val planFirst: Boolean? = null,
    /** cancel kind: also drain pending queue. */
    @SerialName("drain_queue") val drainQueue: Boolean? = null,
    /** retry kind: replace original command body. */
    @SerialName("new_body") val newBody: String? = null,
    /** confirm_reset kind: token issued by backend on reset. */
    val token: String? = null,
    val meta: EnvelopeMeta = EnvelopeMeta(),
    val data: JsonObject? = null,
    val error: EnvelopeError? = null
)

@Serializable
data class EnvelopeMeta(
    val client: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    val auth: String? = null,
    /** When replying to a question, echo back the ask_id so backend unblocks the right chat_ask. */
    @SerialName("ask_id") val askId: Long? = null,
    /** On `kind=question`: short canned reply chips (≤4, ≤30 chars each per spec, but app validates defensively). */
    @SerialName("suggested_replies") val suggestedReplies: List<String>? = null,
    /** Structured progress payload on `kind=progress` envelopes; renders as a progress bar when present. */
    val progress: ProgressInfo? = null
)

/**
 * Structured progress info on `kind=progress` envelopes. All fields optional —
 * UI shows whatever the backend chose to populate (a counter, a percent bar,
 * a label, or any combination). Empty `{}` is valid and renders nothing extra.
 */
@Serializable
data class ProgressInfo(
    val current: Int? = null,
    val total: Int? = null,
    val percent: Double? = null,
    val label: String? = null
)

@Serializable
data class EnvelopeError(
    val code: String,
    val message: String,
    /** Server signal: true = transient, retry may succeed; false = permanent. Null = unknown (legacy payloads). */
    val retryable: Boolean? = null,
    /** Optional next-step copy the client renders verbatim. */
    val hint: String? = null,
    /** Optional rate_limited backoff floor; client waits at least this long before retrying. */
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null
)

/**
 * Locked 9-code enum (claude-email JSON envelope v1). Unknown incoming codes
 * are treated as [INTERNAL] by [classifyErrorCode]; emitting unknown codes is
 * prevented server-side.
 */
object ErrorCodes {
    const val BAD_ENVELOPE = "bad_envelope"
    const val UNKNOWN_KIND = "unknown_kind"
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val PROJECT_NOT_FOUND = "project_not_found"
    const val INVALID_STATE = "invalid_state"
    const val NOT_IMPLEMENTED = "not_implemented"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL = "internal"
}

object Kinds {
    // Inbound (app → backend)
    const val COMMAND = "command"
    const val REPLY = "reply"
    const val STATUS = "status"
    const val CANCEL = "cancel"
    const val RETRY = "retry"
    const val COMMIT = "commit"
    const val RESET = "reset"
    const val CONFIRM_RESET = "confirm_reset"
    const val LIST_PROJECTS = "list_projects"

    // Outbound (backend → app)
    const val ACK = "ack"
    const val PROGRESS = "progress"
    const val QUESTION = "question"
    const val RESULT = "result"
    const val ERROR = "error"
}

/** Shared JSON configuration: lenient about unknown fields so future server additions don't crash. */
val EnvelopeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

const val ENVELOPE_CONTENT_TYPE = "application/json; charset=utf-8"
const val CLIENT_ID = "cocode-android/1.0"
const val CLIENT_HEADER = "X-Claude-Email-Client"
