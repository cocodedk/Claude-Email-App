package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeErrorChipTest {

    @Test
    fun `null error falls back to generic agent-error label`() {
        assertEquals("agent error", envelopeErrorChipLabel(null))
    }

    @Test
    fun `project not found maps to short label`() {
        assertEquals("no project", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.PROJECT_NOT_FOUND, "x")))
    }

    @Test
    fun `unauthorized maps to short label`() {
        assertEquals("auth", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.UNAUTHORIZED, "x")))
    }

    @Test
    fun `rate limited maps to throttled`() {
        assertEquals("throttled", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.RATE_LIMITED, "x")))
    }

    @Test
    fun `not implemented maps to short label`() {
        assertEquals("not built", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.NOT_IMPLEMENTED, "x")))
    }

    @Test
    fun `internal maps to server`() {
        assertEquals("server", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.INTERNAL, "x")))
    }

    @Test
    fun `invalid state maps to short label`() {
        assertEquals("bad state", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.INVALID_STATE, "x")))
    }

    @Test
    fun `bad envelope maps to generic agent error`() {
        assertEquals("agent error", envelopeErrorChipLabel(EnvelopeError(ErrorCodes.BAD_ENVELOPE, "x")))
    }

    @Test
    fun `unknown code falls back to generic agent error`() {
        assertEquals("agent error", envelopeErrorChipLabel(EnvelopeError("brand_new_code", "x")))
    }
}
