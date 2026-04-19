package com.cocode.claudeemailapp.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MailModelsTest {

    @Test
    fun outgoingMessage_defaults() {
        val m = OutgoingMessage("to@x", "s", "b")
        assertNull(m.inReplyTo)
        assertEquals(emptyList<String>(), m.references)
    }

    @Test
    fun probeResultFailure_holdsAllFields() {
        val cause = RuntimeException("x")
        val f = ProbeResult.Failure(ProbeResult.Stage.SMTP, "nope", cause)
        assertEquals(ProbeResult.Stage.SMTP, f.stage)
        assertEquals("nope", f.message)
        assertEquals(cause, f.cause)
    }

    @Test
    fun probeResultFailure_defaultCauseIsNull() {
        val f = ProbeResult.Failure(ProbeResult.Stage.IMAP, "nope")
        assertNull(f.cause)
    }

    @Test
    fun probeResultStage_enumValues() {
        assertEquals(2, ProbeResult.Stage.values().size)
        assertEquals(ProbeResult.Stage.IMAP, ProbeResult.Stage.valueOf("IMAP"))
        assertEquals(ProbeResult.Stage.SMTP, ProbeResult.Stage.valueOf("SMTP"))
    }

    @Test
    fun mailException_preservesCause() {
        val cause = RuntimeException("root")
        val ex = MailException("top", cause)
        assertEquals("top", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun mailException_defaultCauseIsNull() {
        val ex = MailException("msg")
        assertNull(ex.cause)
    }
}
