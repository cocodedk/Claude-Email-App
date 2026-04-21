package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.MailException
import com.cocode.claudeemailapp.mail.MailMutator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns per-message delete/archive scheduling with a 5-second undo window.
 * Scheduling a mutation stages it: the message is added to [State.pendingIds]
 * (so the UI can hide it optimistically) and an Undo snackbar is shown via
 * [State.lastScheduled]. The underlying IMAP call only fires after the
 * window elapses — [undo] within it cancels cleanly and restores the UI.
 */
class MessageMutationController(
    private val scope: CoroutineScope,
    private val credentials: StateFlow<MailCredentials?>,
    private val mutator: MailMutator,
    private val onAfterMutation: () -> Unit,
    private val undoWindowMs: Long = DEFAULT_UNDO_WINDOW_MS
) {
    enum class Action { DELETE, ARCHIVE }

    data class Scheduled(val messageId: String, val action: Action)

    data class State(
        val pendingIds: Set<String> = emptySet(),
        val lastScheduled: Scheduled? = null,
        val lastError: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun scheduleDelete(messageId: String) = schedule(messageId, Action.DELETE) { creds ->
        mutator.delete(creds, messageId)
    }

    fun scheduleArchive(messageId: String) = schedule(messageId, Action.ARCHIVE) { creds ->
        mutator.archive(creds, messageId)
    }

    fun undo(messageId: String) {
        jobs.remove(messageId)?.cancel()
        _state.value = _state.value.copy(
            pendingIds = _state.value.pendingIds - messageId,
            lastScheduled = _state.value.lastScheduled?.takeIf { it.messageId != messageId }
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }

    /** Clear the undo snackbar after it has been shown. */
    fun consumeLastScheduled() {
        _state.value = _state.value.copy(lastScheduled = null)
    }

    private fun schedule(
        messageId: String,
        action: Action,
        op: suspend (MailCredentials) -> Unit
    ) {
        val creds = credentials.value ?: return
        jobs.remove(messageId)?.cancel()
        val scheduled = Scheduled(messageId, action)
        _state.value = State(
            pendingIds = _state.value.pendingIds + messageId,
            lastScheduled = scheduled,
            lastError = null
        )
        jobs[messageId] = scope.launch {
            try {
                delay(undoWindowMs)
                op(creds)
                _state.value = _state.value.copy(
                    pendingIds = _state.value.pendingIds - messageId,
                    lastScheduled = _state.value.lastScheduled?.takeIf { it != scheduled }
                )
                onAfterMutation()
            } catch (t: CancellationException) {
                throw t
            } catch (e: MailException) {
                failWith(messageId, e.message ?: "IMAP mutation failed")
            } catch (t: Throwable) {
                failWith(messageId, t.message ?: "mutation failed")
            } finally {
                jobs.remove(messageId)
            }
        }
    }

    private fun failWith(messageId: String, reason: String) {
        _state.value = _state.value.copy(
            pendingIds = _state.value.pendingIds - messageId,
            lastScheduled = null,
            lastError = reason
        )
    }

    companion object {
        const val DEFAULT_UNDO_WINDOW_MS = 5_000L
    }
}
