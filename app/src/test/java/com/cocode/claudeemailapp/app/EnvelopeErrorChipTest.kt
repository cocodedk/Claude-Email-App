package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeErrorChipTest {

    @Test
    fun `null error falls back to generic agent-error label`() {
        assertEquals(R.string.error_chip_agent, envelopeErrorChipLabel(null))
    }

    @Test
    fun `project not found maps to its label`() {
        assertEquals(R.string.error_chip_no_project, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.PROJECT_NOT_FOUND, "x")))
    }

    @Test
    fun `unauthorized maps to short label`() {
        assertEquals(R.string.error_chip_unauthorized, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.UNAUTHORIZED, "x")))
    }

    @Test
    fun `rate limited maps to throttled`() {
        assertEquals(R.string.error_chip_rate_limited, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.RATE_LIMITED, "x")))
    }

    @Test
    fun `not implemented maps to short label`() {
        assertEquals(R.string.error_chip_not_implemented, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.NOT_IMPLEMENTED, "x")))
    }

    @Test
    fun `internal maps to server`() {
        assertEquals(R.string.error_chip_internal, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.INTERNAL, "x")))
    }

    @Test
    fun `invalid state maps to short label`() {
        assertEquals(R.string.error_chip_invalid_state, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.INVALID_STATE, "x")))
    }

    @Test
    fun `bad envelope maps to generic agent error`() {
        assertEquals(R.string.error_chip_agent, envelopeErrorChipLabel(EnvelopeError(ErrorCodes.BAD_ENVELOPE, "x")))
    }

    @Test
    fun `unknown code falls back to generic agent error`() {
        assertEquals(R.string.error_chip_agent, envelopeErrorChipLabel(EnvelopeError("brand_new_code", "x")))
    }
}
