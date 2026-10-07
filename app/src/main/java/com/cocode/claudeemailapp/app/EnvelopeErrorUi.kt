package com.cocode.claudeemailapp.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.ErrorCodes

/**
 * Purely presentational classification of an [EnvelopeError] emitted by the
 * claude-email backend. Locked code enum lives in [ErrorCodes]; anything we
 * don't recognize collapses onto [UiErrorAction.Retry] per the contract
 * ("unknown codes → treat as `internal`").
 */
data class UiError(
    @StringRes val titleRes: Int,
    val message: String,
    val hint: UiHint?,
    val action: UiErrorAction,
    val showDiagnostics: Boolean
)

/** The line under an error: the service's own text, or one of the app's. */
sealed interface UiHint {
    data class FromService(val text: String) : UiHint
    data class FromApp(@StringRes val res: Int) : UiHint
    data class RetryAfter(val seconds: Int) : UiHint
}

@Composable
fun hintText(hint: UiHint): String = when (hint) {
    is UiHint.FromService -> hint.text
    is UiHint.FromApp -> stringResource(hint.res)
    is UiHint.RetryAfter -> pluralStringResource(R.plurals.error_hint_retry_after, hint.seconds, hint.seconds)
}

enum class UiErrorAction { Retry, OpenSettings, EditCommand, Dismiss }

/**
 * Short chip-friendly label (a string resource) for an envelope error. Reads as "the agent
 * replied with an error" rather than "transport failed" — paired with
 * [tertiary] coloring at the call site so it doesn't look like the red
 * "send failed" status the user sees on actual SMTP/IMAP failures.
 */
@StringRes
fun envelopeErrorChipLabel(error: EnvelopeError?): Int = when (error?.code) {
    ErrorCodes.PROJECT_NOT_FOUND -> R.string.error_chip_no_project
    ErrorCodes.UNAUTHORIZED -> R.string.error_chip_unauthorized
    ErrorCodes.RATE_LIMITED -> R.string.error_chip_rate_limited
    ErrorCodes.NOT_IMPLEMENTED -> R.string.error_chip_not_implemented
    ErrorCodes.INVALID_STATE -> R.string.error_chip_invalid_state
    ErrorCodes.INTERNAL -> R.string.error_chip_internal
    else -> R.string.error_chip_agent
}

private fun serviceHint(error: EnvelopeError): UiHint? = error.hint?.let(UiHint::FromService)

/**
 * Map [EnvelopeError] → [UiError]. Falls back to [UiErrorAction.Retry] when the
 * backend marks the error retryable (e.g. `rate_limited`, `internal`) or the
 * code is unknown; otherwise the error is permanent from the user's view.
 */
fun describeEnvelopeError(error: EnvelopeError): UiError {
    val retryable = error.retryable ?: (error.code == ErrorCodes.INTERNAL || error.code == ErrorCodes.RATE_LIMITED)
    return when (error.code) {
        ErrorCodes.UNAUTHORIZED -> UiError(
            titleRes = R.string.error_title_unauthorized,
            message = error.message,
            hint = serviceHint(error) ?: UiHint.FromApp(R.string.error_hint_unauthorized),
            action = UiErrorAction.OpenSettings,
            showDiagnostics = false
        )
        ErrorCodes.PROJECT_NOT_FOUND -> UiError(
            titleRes = R.string.error_title_project_not_found,
            message = error.message,
            hint = serviceHint(error) ?: UiHint.FromApp(R.string.error_hint_project_not_found),
            action = UiErrorAction.EditCommand,
            showDiagnostics = false
        )
        ErrorCodes.NOT_IMPLEMENTED -> UiError(
            titleRes = R.string.error_title_not_implemented,
            message = error.message,
            hint = serviceHint(error),
            action = UiErrorAction.Dismiss,
            showDiagnostics = false
        )
        ErrorCodes.INVALID_STATE -> UiError(
            titleRes = R.string.error_title_invalid_state,
            message = error.message,
            hint = serviceHint(error),
            action = UiErrorAction.Dismiss,
            showDiagnostics = false
        )
        ErrorCodes.RATE_LIMITED -> UiError(
            titleRes = R.string.error_title_rate_limited,
            message = error.message,
            hint = serviceHint(error) ?: error.retryAfterSeconds?.let(UiHint::RetryAfter),
            action = UiErrorAction.Retry,
            showDiagnostics = false
        )
        ErrorCodes.INTERNAL -> UiError(
            titleRes = R.string.error_title_service_problem,
            message = error.message,
            hint = serviceHint(error),
            action = UiErrorAction.Retry,
            showDiagnostics = true
        )
        ErrorCodes.BAD_ENVELOPE, ErrorCodes.UNKNOWN_KIND, ErrorCodes.FORBIDDEN -> UiError(
            titleRes = R.string.error_title_unexpected,
            message = error.message,
            hint = serviceHint(error),
            action = UiErrorAction.Dismiss,
            showDiagnostics = true
        )
        else -> UiError(
            titleRes = R.string.error_title_service_problem,
            message = error.message,
            hint = serviceHint(error),
            action = if (retryable) UiErrorAction.Retry else UiErrorAction.Dismiss,
            showDiagnostics = true
        )
    }
}
