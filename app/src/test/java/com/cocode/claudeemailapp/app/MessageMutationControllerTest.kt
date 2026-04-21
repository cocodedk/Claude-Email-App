package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.MailException
import com.cocode.claudeemailapp.mail.MailMutator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageMutationControllerTest {

    private class RecordingMutator(
        private val deleteFails: Boolean = false
    ) : MailMutator {
        val deleted = mutableListOf<String>()
        val archived = mutableListOf<String>()
        override suspend fun delete(credentials: MailCredentials, messageId: String) {
            if (deleteFails) throw MailException("imap offline")
            deleted += messageId
        }
        override suspend fun archive(credentials: MailCredentials, messageId: String) {
            archived += messageId
        }
    }

    private fun creds() = MailCredentials(
        displayName = "d", emailAddress = "me@ex.com", password = "p",
        imapHost = "i", imapPort = 993, smtpHost = "s", smtpPort = 465,
        smtpUseStartTls = false, serviceAddress = "svc@ex", sharedSecret = "s"
    )

    private fun controller(
        mutator: MailMutator = RecordingMutator(),
        scope: TestScope
    ): Pair<MessageMutationController, MutableList<Unit>> {
        val refreshes = mutableListOf<Unit>()
        val ctrl = MessageMutationController(
            scope = scope,
            credentials = MutableStateFlow(creds()),
            mutator = mutator,
            onAfterMutation = { refreshes += Unit },
            undoWindowMs = 5_000L
        )
        return ctrl to refreshes
    }

    @Test
    fun scheduleDelete_marksPendingAndShowsUndoSnackbar() = runTest {
        val mutator = RecordingMutator()
        val (ctrl, _) = controller(mutator, this)

        ctrl.scheduleDelete("<m1@x>")

        assertTrue("<m1@x>" in ctrl.state.value.pendingIds)
        assertEquals(
            MessageMutationController.Scheduled("<m1@x>", MessageMutationController.Action.DELETE),
            ctrl.state.value.lastScheduled
        )
        assertTrue("delete must not fire before window", mutator.deleted.isEmpty())
    }

    @Test
    fun undoWithinWindow_cancelsWithoutFiring() = runTest {
        val mutator = RecordingMutator()
        val (ctrl, refreshes) = controller(mutator, this)

        ctrl.scheduleDelete("<m1@x>")
        advanceTimeBy(2_000)
        ctrl.undo("<m1@x>")
        advanceUntilIdle()

        assertTrue(mutator.deleted.isEmpty())
        assertTrue("<m1@x>" !in ctrl.state.value.pendingIds)
        assertNull(ctrl.state.value.lastScheduled)
        assertTrue(refreshes.isEmpty())
    }

    @Test
    fun afterUndoWindow_firesMutatorAndClearsState() = runTest {
        val mutator = RecordingMutator()
        val (ctrl, refreshes) = controller(mutator, this)

        ctrl.scheduleDelete("<m1@x>")
        advanceTimeBy(5_100)
        advanceUntilIdle()

        assertEquals(listOf("<m1@x>"), mutator.deleted)
        assertTrue(ctrl.state.value.pendingIds.isEmpty())
        assertEquals(1, refreshes.size)
    }

    @Test
    fun scheduleArchive_usesArchiveMutator() = runTest {
        val mutator = RecordingMutator()
        val (ctrl, _) = controller(mutator, this)

        ctrl.scheduleArchive("<m2@x>")
        advanceTimeBy(5_100)
        advanceUntilIdle()

        assertEquals(listOf("<m2@x>"), mutator.archived)
        assertTrue(mutator.deleted.isEmpty())
    }

    @Test
    fun multiplePending_eachHasOwnWindow() = runTest {
        val mutator = RecordingMutator()
        val (ctrl, _) = controller(mutator, this)

        ctrl.scheduleDelete("<m1@x>")
        advanceTimeBy(2_000)
        ctrl.scheduleArchive("<m2@x>")

        assertEquals(setOf("<m1@x>", "<m2@x>"), ctrl.state.value.pendingIds)

        // advanceTimeBy only runs tasks whose virtual deadline falls inside
        // the advanced window — don't call advanceUntilIdle here, it would
        // drain every queued coroutine regardless of deadline.
        advanceTimeBy(3_100) // virtual clock → 5100: m1 fires, m2 not yet.
        assertEquals(listOf("<m1@x>"), mutator.deleted)
        assertTrue(mutator.archived.isEmpty())

        advanceTimeBy(2_000) // → 7100: m2 fires.
        assertEquals(listOf("<m2@x>"), mutator.archived)
    }

    @Test
    fun mutatorError_clearsPendingAndSurfacesError() = runTest {
        val mutator = RecordingMutator(deleteFails = true)
        val (ctrl, refreshes) = controller(mutator, this)

        ctrl.scheduleDelete("<m1@x>")
        advanceTimeBy(5_100)
        advanceUntilIdle()

        assertTrue(ctrl.state.value.pendingIds.isEmpty())
        assertNull(ctrl.state.value.lastScheduled)
        assertNotNull(ctrl.state.value.lastError)
        assertTrue(refreshes.isEmpty())
    }
}
