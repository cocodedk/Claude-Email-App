package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.ErrorCodes

/**
 * Purely presentational classification of an [EnvelopeError] emitted by the
 * claude-email backend. Locked code enum lives in [ErrorCodes]; anything we
 * don't recognize collapses onto [UiErrorAction.Retry] per the contract
 * ("unknown codes → treat as `internal`").
 */
data class UiError(
    val title: String,
    val message: String,
    val hint: String?,
    val action: UiErrorAction,
    val showDiagnostics: Boolean
)

enum class UiErrorAction { Retry, OpenSettings, EditCommand, Dismiss }

/**
 * Short chip-friendly label for an envelope error. Reads as "the agent
 * replied with an error" rather than "transport failed" — paired with
 * [tertiary] coloring at the call site so it doesn't look like the red
 * "send failed" status the user sees on actual SMTP/IMAP failures.
 */
fun envelopeErrorChipLabel(error: EnvelopeError?): String = when (error?.code) {
    ErrorCodes.PROJECT_NOT_FOUND -> "no project"
    ErrorCodes.UNAUTHORIZED -> "auth"
    ErrorCodes.RATE_LIMITED -> "throttled"
    ErrorCodes.NOT_IMPLEMENTED -> "not built"
    ErrorCodes.INVALID_STATE -> "bad state"
    ErrorCodes.INTERNAL -> "server"
    else -> "agent error"
}

/**
 * Map [EnvelopeError] → [UiError]. Falls back to [UiErrorAction.Retry] when the
 * backend marks the error retryable (e.g. `rate_limited`, `internal`) or the
 * code is unknown; otherwise the error is permanent from the user's view.
 */
fun describeEnvelopeError(error: EnvelopeError): UiError {
    val retryable = error.retryable ?: (error.code == ErrorCodes.INTERNAL || error.code == ErrorCodes.RATE_LIMITED)
    return when (error.code) {
        ErrorCodes.UNAUTHORIZED -> UiError(
            title = "Not authorized",
            message = error.message,
            hint = error.hint ?: "Open Settings → Edit credentials and re-check the shared secret.",
            action = UiErrorAction.OpenSettings,
            showDiagnostics = false
        )
        ErrorCodes.PROJECT_NOT_FOUND -> UiError(
            title = "Project not found",
            message = error.message,
            hint = error.hint ?: "Check the project path on your claude-email service and resend.",
            action = UiErrorAction.EditCommand,
            showDiagnostics = false
        )
        ErrorCodes.NOT_IMPLEMENTED -> UiError(
            title = "Not available yet",
            message = error.message,
            hint = error.hint,
            action = UiErrorAction.Dismiss,
            showDiagnostics = false
        )
        ErrorCodes.INVALID_STATE -> UiError(
            title = "Can't run right now",
            message = error.message,
            hint = error.hint,
            action = UiErrorAction.Dismiss,
            showDiagnostics = false
        )
        ErrorCodes.RATE_LIMITED -> UiError(
            title = "Rate limited",
            message = error.message,
            hint = error.hint ?: error.retryAfterSeconds?.let { "Retry after ${it}s." },
            action = UiErrorAction.Retry,
            showDiagnostics = false
        )
        ErrorCodes.INTERNAL -> UiError(
            title = "Server hiccup",
            message = error.message,
            hint = error.hint,
            action = UiErrorAction.Retry,
            showDiagnostics = true
        )
        ErrorCodes.BAD_ENVELOPE, ErrorCodes.UNKNOWN_KIND, ErrorCodes.FORBIDDEN -> UiError(
            title = "Unexpected response",
            message = error.message,
            hint = error.hint,
            action = UiErrorAction.Dismiss,
            showDiagnostics = true
        )
        else -> UiError(
            title = "Server hiccup",
            message = error.message,
            hint = error.hint,
            action = if (retryable) UiErrorAction.Retry else UiErrorAction.Dismiss,
            showDiagnostics = true
        )
    }
}
