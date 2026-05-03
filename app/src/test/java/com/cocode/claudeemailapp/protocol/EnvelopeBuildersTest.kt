package com.cocode.claudeemailapp.protocol

import com.cocode.claudeemailapp.mail.OutgoingMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeBuildersTest {

    @Test
    fun outgoingEnvelope_setsContentTypeAndHeader() {
        val env = Envelopes.command(body = "hello", project = "/p", sentAt = "2026-04-19T00:00:00Z")
        val msg = OutgoingMessage.envelope(
            to = "svc@ex",
            subject = "hello",
            envelope = env
        )
        assertEquals(ENVELOPE_CONTENT_TYPE, msg.contentType)
        assertEquals(CLIENT_ID, msg.extraHeaders[CLIENT_HEADER])
        assertTrue(msg.body.contains("\"kind\":\"command\""))
        assertTrue(msg.body.contains("\"project\":\"/p\""))
    }

    @Test
    fun outgoingEnvelope_preservesThreadingHeaders() {
        val env = Envelopes.reply(taskId = 9, body = "thanks", sentAt = "2026-04-19T00:00:00Z")
        val msg = OutgoingMessage.envelope(
            to = "svc@ex",
            subject = "Re: x",
            envelope = env,
            inReplyTo = "<orig@x>",
            references = listOf("<root@x>", "<mid@x>")
        )
        assertEquals("<orig@x>", msg.inReplyTo)
        assertEquals(listOf("<root@x>", "<mid@x>"), msg.references)
    }

    @Test
    fun outgoingEnvelope_mergesExtraHeaders() {
        val env = Envelopes.status(taskId = 1)
        val msg = OutgoingMessage.envelope(
            to = "svc@ex",
            subject = "status",
            envelope = env,
            extraHeaders = mapOf("X-Test" to "1")
        )
        assertEquals("1", msg.extraHeaders["X-Test"])
        assertEquals(CLIENT_ID, msg.extraHeaders[CLIENT_HEADER])
    }

    @Test
    fun commandBuilder_withoutOptionalFields() {
        val env = Envelopes.command(body = "x", project = "/p")
        assertEquals(Kinds.COMMAND, env.kind)
        assertEquals("/p", env.project)
        // priority and planFirst are null by default
        assertEquals(null, env.priority)
        assertEquals(null, env.planFirst)
    }

    @Test
    fun commandBuilder_preferLiveAgent_serializesIntoMeta() {
        val env = Envelopes.command(body = "go", project = "/p", preferLiveAgent = true)
        assertEquals(true, env.meta.preferLiveAgent)
        val msg = OutgoingMessage.envelope(to = "svc@ex", subject = "go", envelope = env)
        assertTrue(msg.body.contains("\"prefer_live_agent\":true"))
    }

    @Test
    fun commandBuilder_preferLiveAgent_defaultIsNullAndOmitsField() {
        val env = Envelopes.command(body = "go", project = "/p")
        assertEquals(null, env.meta.preferLiveAgent)
        val msg = OutgoingMessage.envelope(to = "svc@ex", subject = "go", envelope = env)
        assertTrue(!msg.body.contains("prefer_live_agent"))
    }

    @Test
    fun listProjectsBuilder_setsKindAndOmitsBody() {
        val env = Envelopes.listProjects(auth = "secret", sentAt = "2026-05-03T00:00:00Z")
        assertEquals(Kinds.LIST_PROJECTS, env.kind)
        assertEquals("", env.body)
        assertEquals(null, env.taskId)
        assertEquals(null, env.project)
        assertEquals("secret", env.meta.auth)
    }

    @Test
    fun listProjectsBuilder_serializesMinimalEnvelope() {
        val env = Envelopes.listProjects(auth = "secret", sentAt = "2026-05-03T00:00:00Z")
        val msg = OutgoingMessage.envelope(to = "svc@ex", subject = "list", envelope = env)
        assertTrue(msg.body.contains("\"kind\":\"list_projects\""))
        // No body field should appear since EnvelopeJson encodeDefaults=false and body=""
        assertTrue(!msg.body.contains("\"body\":\"\""))
    }
}
