package com.cocode.claudeemailapp.app.steering

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SteeringBarControllerTest {

    private fun controller() = SteeringBarController(TestScope(StandardTestDispatcher()))

    @Test
    fun tapCancel_once_armsBarWithoutFiring() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val fired = mutableListOf<SteeringIntent>()
        ctrl.onIntent = { fired += it }

        ctrl.tapCancel()

        assertTrue(ctrl.uiState.value.armed)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun tapCancel_twiceWithinArmWindow_firesCancelIntent() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val fired = mutableListOf<SteeringIntent>()
        ctrl.onIntent = { fired += it }

        ctrl.tapCancel()
        advanceTimeBy(1_000)
        ctrl.tapCancel()

        assertEquals(listOf<SteeringIntent>(SteeringIntent.Cancel), fired)
        assertFalse(ctrl.uiState.value.armed)
    }

    @Test
    fun armWindow_expiresAfter3s() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))

        ctrl.tapCancel()
        advanceTimeBy(3_100)

        assertFalse(ctrl.uiState.value.armed)
    }

    @Test
    fun fire_opensFiveSecondUndoWindow() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))

        ctrl.tapCancel()
        ctrl.tapCancel()

        assertTrue(ctrl.uiState.value.undoAvailable)

        advanceTimeBy(5_100)

        assertFalse(ctrl.uiState.value.undoAvailable)
    }

    @Test
    fun tapStatus_firesStatusIntentAndMarksSending() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val fired = mutableListOf<SteeringIntent>()
        ctrl.onIntent = { fired += it }

        ctrl.tapStatus()

        assertEquals(listOf<SteeringIntent>(SteeringIntent.Status), fired)
        assertEquals(SteeringIntent.Status, ctrl.uiState.value.sending)
    }

    @Test
    fun onAcked_clearsSending() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        ctrl.tapStatus()
        assertEquals(SteeringIntent.Status, ctrl.uiState.value.sending)

        ctrl.onAcked()

        assertNull(ctrl.uiState.value.sending)
    }

    @Test
    fun undo_withinWindow_callsOnUndoAndClearsFlag() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val undone = mutableListOf<SteeringIntent>()
        ctrl.onUndo = { undone += it }

        ctrl.tapCancel()
        ctrl.tapCancel()
        assertTrue(ctrl.uiState.value.undoAvailable)

        ctrl.undo()

        assertEquals(listOf<SteeringIntent>(SteeringIntent.Cancel), undone)
        assertFalse(ctrl.uiState.value.undoAvailable)
    }

    @Test
    fun undo_afterStatus_isIgnored() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val undone = mutableListOf<SteeringIntent>()
        ctrl.onUndo = { undone += it }

        ctrl.tapStatus()
        // Status is not undo-eligible, so undo() must be a no-op even if called.
        ctrl.undo()

        assertTrue(undone.isEmpty())
        assertFalse(ctrl.uiState.value.undoAvailable)
    }
}
