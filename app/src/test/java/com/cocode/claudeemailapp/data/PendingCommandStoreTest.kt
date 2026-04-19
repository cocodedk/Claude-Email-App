package com.cocode.claudeemailapp.data

import androidx.test.core.app.ApplicationProvider
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.Kinds
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingCommandStoreTest {

    private lateinit var store: SharedPrefsPendingCommandStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences("pcs_test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        store = SharedPrefsPendingCommandStore(prefs)
    }

    private fun pending(id: String = "<m1@x>", taskId: Long? = null, status: String = PendingStatus.AWAITING_ACK) =
        PendingCommand(
            messageId = id,
            sentAt = 1_000L,
            to = "svc@ex",
            subject = "sub",
            kind = Kinds.COMMAND,
            bodyPreview = "body",
            taskId = taskId,
            status = status
        )

    @Test
    fun addAndFindByMessageId_roundTrip() {
        store.add(pending())
        val found = store.findByMessageId("<m1@x>")
        assertNotNull(found)
        assertEquals(PendingStatus.AWAITING_ACK, found!!.status)
    }

    @Test
    fun findByMessageId_missing_returnsNull() {
        assertNull(store.findByMessageId("<nope>"))
    }

    @Test
    fun findByTaskId_scansAllEntries() {
        store.add(pending(id = "<a>", taskId = 1L))
        store.add(pending(id = "<b>", taskId = 2L))
        assertEquals("<b>", store.findByTaskId(2L)?.messageId)
    }

    @Test
    fun all_returnsEveryEntry() {
        store.add(pending(id = "<a>"))
        store.add(pending(id = "<b>"))
        assertEquals(2, store.all().size)
    }

    @Test
    fun clear_removesAll() {
        store.add(pending())
        store.clear()
        assertEquals(emptyList<PendingCommand>(), store.all())
    }

    @Test
    fun applyInbound_ack_promotesTaskIdAndStatus() {
        store.add(pending(id = "<m1@x>"))
        val env = Envelope(
            kind = Kinds.ACK,
            taskId = 42,
            body = "queued",
            data = buildJsonObject {
                put("branch", "claude/task-42-refactor")
                put("status", "queued")
            }
        )
        val updated = store.applyInbound(env, inReplyTo = "<m1@x>")
        assertNotNull(updated)
        assertEquals(42L, updated!!.taskId)
        assertEquals("queued", updated.status)
        assertEquals("claude/task-42-refactor", updated.branch)
    }

    @Test
    fun applyInbound_progress_setsRunning() {
        store.add(pending(id = "<m1@x>", taskId = 42L, status = PendingStatus.QUEUED))
        val env = Envelope(kind = Kinds.PROGRESS, taskId = 42, body = "working", data = buildJsonObject { put("status", "running") })
        val updated = store.applyInbound(env, "<m1@x>")
        assertEquals(PendingStatus.RUNNING, updated!!.status)
    }

    @Test
    fun applyInbound_question_setsAskId() {
        store.add(pending(id = "<m1@x>", taskId = 42L, status = PendingStatus.RUNNING))
        val env = Envelope(
            kind = Kinds.QUESTION,
            taskId = 42,
            body = "clarify please",
            data = buildJsonObject {
                put("ask_id", "ask-9")
                put("timeout_at", "2026-04-19T17:00:00Z")
            }
        )
        val updated = store.applyInbound(env, "<m1@x>")
        assertEquals(PendingStatus.AWAITING_USER, updated!!.status)
        assertEquals("ask-9", updated.askId)
    }

    @Test
    fun applyInbound_resultCompleted_setsDone() {
        store.add(pending(id = "<m1@x>", taskId = 42L, status = PendingStatus.RUNNING))
        val env = Envelope(kind = Kinds.RESULT, taskId = 42, body = "done", data = buildJsonObject { put("status", "completed") })
        val updated = store.applyInbound(env, "<m1@x>")
        assertEquals(PendingStatus.DONE, updated!!.status)
    }

    @Test
    fun applyInbound_resultFailed_setsFailed() {
        store.add(pending(id = "<m1@x>", taskId = 42L, status = PendingStatus.RUNNING))
        val env = Envelope(kind = Kinds.RESULT, taskId = 42, body = "boom", data = buildJsonObject {
            put("status", "failed")
            put("error", "segfault")
        })
        val updated = store.applyInbound(env, "<m1@x>")
        assertEquals(PendingStatus.FAILED, updated!!.status)
        assertEquals("segfault", updated.lastError)
    }

    @Test
    fun applyInbound_error_setsErrorStatusAndMessage() {
        store.add(pending(id = "<m1@x>"))
        val env = Envelope(
            kind = Kinds.ERROR,
            body = "bad",
            error = EnvelopeError(code = "unauthorized", message = "bad secret")
        )
        val updated = store.applyInbound(env, "<m1@x>")
        assertEquals(PendingStatus.ERROR, updated!!.status)
        assertEquals("unauthorized: bad secret", updated.lastError)
    }

    @Test
    fun applyInbound_matchByTaskIdWhenInReplyToMissing() {
        store.add(pending(id = "<m1@x>", taskId = 42L))
        val env = Envelope(kind = Kinds.PROGRESS, taskId = 42, body = "x", data = buildJsonObject { put("status", "running") })
        val updated = store.applyInbound(env, inReplyTo = null)
        assertEquals(PendingStatus.RUNNING, updated!!.status)
    }

    @Test
    fun applyInbound_unknownMatch_returnsNull() {
        val env = Envelope(kind = Kinds.ACK, taskId = 42, body = "x")
        val updated = store.applyInbound(env, inReplyTo = "<unknown>")
        assertNull(updated)
    }

    @Test
    fun applyInbound_unknownKind_returnsUnchanged() {
        store.add(pending(id = "<m1@x>", status = PendingStatus.QUEUED))
        val env = Envelope(kind = "mystery", taskId = 42, body = "x")
        val updated = store.applyInbound(env, "<m1@x>")
        assertEquals(PendingStatus.QUEUED, updated!!.status)
    }

    @Test
    fun defaultFactoryReturnsSharedPrefsImpl() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val s = PendingCommandStore(ctx)
        assert(s is SharedPrefsPendingCommandStore)
    }
}
