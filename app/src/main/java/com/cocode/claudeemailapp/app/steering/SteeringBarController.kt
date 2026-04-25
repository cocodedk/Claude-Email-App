package com.cocode.claudeemailapp.app.steering

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ephemeral UI state for the steering bar — arming the Cancel chip,
 * tracking the in-flight intent, and the 5s undo window after a cancel.
 *
 * The caller wires [onIntent] to the ViewModel dispatch method and must
 * call [onAcked] when the corresponding backend ack lands so the "sending"
 * state clears.
 */
class SteeringBarController(private val scope: CoroutineScope) {

    data class UiState(
        val armed: Boolean = false,
        val sending: SteeringIntent? = null,
        val undoAvailable: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    var onIntent: (SteeringIntent) -> Unit = {}
    var onUndo: (SteeringIntent) -> Unit = {}

    private var armJob: Job? = null
    private var undoJob: Job? = null
    private var lastFired: SteeringIntent? = null

    fun tapCancel() {
        if (_ui.value.armed) fire(SteeringIntent.Cancel) else arm()
    }

    fun tapStatus() = fire(SteeringIntent.Status)

    fun tapReply(askId: String, body: String) = fire(SteeringIntent.Reply(askId, body))

    fun undo() {
        if (!_ui.value.undoAvailable) return
        val fired = lastFired ?: return
        undoJob?.cancel()
        _ui.value = _ui.value.copy(undoAvailable = false)
        onUndo(fired)
        lastFired = null
    }

    /** Called when the ViewModel observes the ack round-trip completing. */
    fun onAcked() {
        _ui.value = _ui.value.copy(sending = null)
    }

    private fun arm() {
        _ui.value = _ui.value.copy(armed = true)
        armJob?.cancel()
        armJob = scope.launch {
            delay(ARM_WINDOW_MS)
            _ui.value = _ui.value.copy(armed = false)
        }
    }

    private fun fire(intent: SteeringIntent) {
        armJob?.cancel()
        val undoEligible = intent is SteeringIntent.Cancel
        _ui.value = UiState(armed = false, sending = intent, undoAvailable = undoEligible)
        onIntent(intent)
        if (undoEligible) {
            lastFired = intent
            undoJob?.cancel()
            undoJob = scope.launch {
                delay(UNDO_WINDOW_MS)
                _ui.value = _ui.value.copy(undoAvailable = false)
                lastFired = null
            }
        } else {
            lastFired = null
        }
    }

    companion object {
        const val ARM_WINDOW_MS = 3_000L
        const val UNDO_WINDOW_MS = 5_000L
    }
}
