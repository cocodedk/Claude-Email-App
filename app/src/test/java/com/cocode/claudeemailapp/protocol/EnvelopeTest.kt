package com.cocode.claudeemailapp.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeTest {

    @Test
    fun roundTrip_command() {
        val env = Envelopes.command(
            body = "do a thing",
            project = "/p",
            priority = 5,
            planFirst = true,
            auth = "s",
            sentAt = "2026-04-19T16:00:00Z"
        )
        val json = EnvelopeJson.encodeToString(env)
        val decoded = EnvelopeJson.decodeFromString(Envelope.serializer(), json)
        assertEquals(env, decoded)
    }

    @Test
    fun roundTrip_replyWithAskId() {
        val env = Envelopes.reply(
            taskId = 42,
            body = "answer",
            askId = 7L,
            auth = "s",
            sentAt = "2026-04-19T16:00:00Z"
        )
        val json = EnvelopeJson.encodeToString(env)
        val decoded = EnvelopeJson.decodeFromString(Envelope.serializer(), json)
        assertEquals(env, decoded)
        assertEquals(42L, decoded.taskId)
        assertEquals(7L, decoded.meta.askId)
    }

    @Test
    fun roundTrip_allBuilders() {
        val builders = listOf(
            Envelopes.status(taskId = 1),
            Envelopes.cancel(project = "/p", drainQueue = true),
            Envelopes.retry(taskId = 2, newBody = "redo"),
            Envelopes.commit(project = "/p", body = "commit msg"),
            Envelopes.reset(project = "/p"),
            Envelopes.confirmReset(project = "/p", token = "tok-abc")
        )
        for (env in builders) {
            val json = EnvelopeJson.encodeToString(env)
            val back = EnvelopeJson.decodeFromString(Envelope.serializer(), json)
            assertEquals("round trip failed for kind=${env.kind}", env, back)
        }
    }

    @Test
    fun ignoreUnknownKeys_surviveForwardCompatibility() {
        val json = """{"v":1,"kind":"ack","task_id":42,"body":"x","meta":{},"future_field":"oops"}"""
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), json)
        assertEquals("ack", env.kind)
        assertEquals(42L, env.taskId)
    }

    @Test
    fun nullableFields_dropWhenEmpty_viaEncodeDefaults() {
        val env = Envelope(kind = "command", body = "x")
        val json = EnvelopeJson.encodeToString(env)
        assertFalse("task_id should not appear when null", json.contains("task_id"))
    }

    @Test
    fun errorEnvelope_parsesCodeAndMessage() {
        val json = """{"v":1,"kind":"error","body":"nope","error":{"code":"unauthorized","message":"bad secret"}}"""
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), json)
        assertEquals("error", env.kind)
        assertNotNull(env.error)
        assertEquals(ErrorCodes.UNAUTHORIZED, env.error!!.code)
        assertEquals("bad secret", env.error!!.message)
    }

    @Test
    fun dataField_roundTripsAsJsonObject() {
        val env = Envelope(
            kind = Kinds.ACK,
            taskId = 42,
            body = "queued",
            data = buildJsonObject {
                put("branch", "claude/task-42-slug")
                put("status", "queued")
            }
        )
        val json = EnvelopeJson.encodeToString(env)
        val back = EnvelopeJson.decodeFromString(Envelope.serializer(), json)
        assertEquals(env, back)
    }

    @Test
    fun kindsConstants_covered() {
        // Forces the compiler to keep these constants alive and tests string values.
        val all = listOf(
            Kinds.COMMAND, Kinds.REPLY, Kinds.STATUS, Kinds.CANCEL, Kinds.RETRY,
            Kinds.COMMIT, Kinds.RESET, Kinds.CONFIRM_RESET,
            Kinds.ACK, Kinds.PROGRESS, Kinds.QUESTION, Kinds.RESULT, Kinds.ERROR
        )
        assertEquals(13, all.toSet().size)
    }

    @Test
    fun errorCodes_lockedEnum_isNineDistinctCodes() {
        val codes = listOf(
            ErrorCodes.BAD_ENVELOPE, ErrorCodes.UNKNOWN_KIND, ErrorCodes.UNAUTHORIZED,
            ErrorCodes.FORBIDDEN, ErrorCodes.PROJECT_NOT_FOUND, ErrorCodes.INVALID_STATE,
            ErrorCodes.NOT_IMPLEMENTED, ErrorCodes.RATE_LIMITED, ErrorCodes.INTERNAL
        )
        assertEquals(9, codes.toSet().size)
    }

    @Test
    fun constants_areNonBlank() {
        assertTrue(ENVELOPE_CONTENT_TYPE.isNotBlank())
        assertTrue(CLIENT_ID.isNotBlank())
        assertTrue(CLIENT_HEADER.isNotBlank())
    }

    @Test
    fun envelopeMeta_defaultsAreAllNull() {
        val meta = EnvelopeMeta()
        assertNull(meta.client)
        assertNull(meta.sentAt)
        assertNull(meta.auth)
        assertNull(meta.askId)
    }

    @Test
    fun error_defaultCodeMessage() {
        val e = EnvelopeError("x", "y")
        assertEquals("x", e.code)
        assertEquals("y", e.message)
    }

    @Test
    fun envelopeMeta_progress_parsesAllFields() {
        val raw = """
            {"v":1,"kind":"progress","body":"running tests","meta":{"progress":{"current":3,"total":7,"percent":42.8,"label":"tests passed"}}}
        """.trimIndent()
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), raw)
        val p = env.meta.progress
        assertNotNull(p)
        assertEquals(3, p!!.current)
        assertEquals(7, p.total)
        assertEquals(42.8, p.percent!!, 0.001)
        assertEquals("tests passed", p.label)
    }

    @Test
    fun envelopeMeta_progress_allFieldsOptional() {
        val raw = """{"v":1,"kind":"progress","body":"x","meta":{"progress":{}}}"""
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), raw)
        val p = env.meta.progress
        assertNotNull(p)
        assertNull(p!!.current)
        assertNull(p.total)
        assertNull(p.percent)
        assertNull(p.label)
    }

    @Test
    fun envelopeMeta_progress_absentWhenMissing() {
        val raw = """{"v":1,"kind":"progress","body":"x","meta":{}}"""
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), raw)
        assertNull(env.meta.progress)
    }

    @Test
    fun envelopeMeta_suggestedReplies_parsesArray() {
        val raw = """{"v":1,"kind":"question","body":"ok?","meta":{"suggested_replies":["yes","no","edit first"]}}"""
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), raw)
        assertEquals(listOf("yes", "no", "edit first"), env.meta.suggestedReplies)
    }

    @Test
    fun envelopeMeta_suggestedReplies_absentWhenMissing() {
        val raw = """{"v":1,"kind":"question","body":"ok?","meta":{}}"""
        val env = EnvelopeJson.decodeFromString(Envelope.serializer(), raw)
        assertNull(env.meta.suggestedReplies)
    }
}
