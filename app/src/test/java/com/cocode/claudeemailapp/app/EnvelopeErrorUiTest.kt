package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeErrorUiTest {

    @Test
    fun unauthorized_primaryActionIsOpenSettings_hintUsesServerValue() {
        val ui = describeEnvelopeError(
            EnvelopeError(ErrorCodes.UNAUTHORIZED, "bad secret", retryable = false, hint = "Open Settings and check the shared secret.")
        )
        assertEquals(UiErrorAction.OpenSettings, ui.action)
        assertEquals("Open Settings and check the shared secret.", ui.hint)
    }

    @Test
    fun unauthorized_missingServerHint_fallsBackToClientCopy() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.UNAUTHORIZED, "bad secret", retryable = false))
        assertNotNull(ui.hint)
        assertTrue(ui.hint!!.contains("Settings"))
    }

    @Test
    fun projectNotFound_primaryActionIsEditCommand() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.PROJECT_NOT_FOUND, "no such project", retryable = false))
        assertEquals(UiErrorAction.EditCommand, ui.action)
    }

    @Test
    fun rateLimited_actionIsRetry_retryAfterSurfacesAsHint() {
        val ui = describeEnvelopeError(
            EnvelopeError(ErrorCodes.RATE_LIMITED, "slow down", retryable = true, retryAfterSeconds = 30)
        )
        assertEquals(UiErrorAction.Retry, ui.action)
        assertEquals("Retry after 30s.", ui.hint)
    }

    @Test
    fun internal_actionIsRetry_exposesDiagnostics() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.INTERNAL, "oops", retryable = true))
        assertEquals(UiErrorAction.Retry, ui.action)
        assertTrue(ui.showDiagnostics)
    }

    @Test
    fun notImplemented_actionIsDismiss_noRetryButton() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.NOT_IMPLEMENTED, "not wired", retryable = false))
        assertEquals(UiErrorAction.Dismiss, ui.action)
        assertFalse(ui.showDiagnostics)
    }

    @Test
    fun invalidState_actionIsDismiss() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.INVALID_STATE, "can't", retryable = false))
        assertEquals(UiErrorAction.Dismiss, ui.action)
    }

    @Test
    fun badEnvelope_surfacesAsGenericBug_withDiagnostics() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.BAD_ENVELOPE, "parse fail", retryable = false))
        assertEquals(UiErrorAction.Dismiss, ui.action)
        assertTrue(ui.showDiagnostics)
    }

    @Test
    fun unknownCode_retryableTrue_treatedAsRetryableInternal() {
        val ui = describeEnvelopeError(EnvelopeError("some_future_code", "hmm", retryable = true))
        assertEquals(UiErrorAction.Retry, ui.action)
        assertTrue(ui.showDiagnostics)
    }

    @Test
    fun unknownCode_retryableFalse_dismissWithDiagnostics() {
        val ui = describeEnvelopeError(EnvelopeError("some_future_code", "hmm", retryable = false))
        assertEquals(UiErrorAction.Dismiss, ui.action)
        assertTrue(ui.showDiagnostics)
    }

    @Test
    fun unknownCode_noRetryableHint_defaultsByCodeHeuristic() {
        // Missing retryable → infer from code. "some_future_code" is not INTERNAL/RATE_LIMITED, so not retryable.
        val ui = describeEnvelopeError(EnvelopeError("some_future_code", "hmm"))
        assertEquals(UiErrorAction.Dismiss, ui.action)
    }

    @Test
    fun internal_missingRetryableFlag_inferredRetryable() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.INTERNAL, "oops"))
        assertEquals(UiErrorAction.Retry, ui.action)
    }

    @Test
    fun missingHintAndRetryAfter_hintIsNull_forNotImplemented() {
        val ui = describeEnvelopeError(EnvelopeError(ErrorCodes.NOT_IMPLEMENTED, "x"))
        assertNull(ui.hint)
    }
}
