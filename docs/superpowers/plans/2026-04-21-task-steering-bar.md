# Task-Steering Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the task-steering bar on `ConversationScreen` — four visual states (idle, sending, armed-cancel, awaiting_user) bound to `PendingCommand.status`, with two-tap cancel + undo and a template sheet for agent questions.

**Architecture:** A new `app/steering/` package holds the state model, a stateful controller (arming/undo timers via `StateFlow`), and the two Compose components. `AppViewModel` gains intent-dispatch methods that map steering intents to existing `Envelopes` builders. `ConversationScreen` consumes the matched `PendingCommand` and composes the bar above the existing composer. `PendingCommand` gains a `project` field so cancel/reset intents can be built. All user-facing strings live in `strings.xml`.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose + Material 3, Coroutines/StateFlow, JUnit 4 + Robolectric + MockK (unit), Compose UI Test (androidTest).

**Design spec:** [`docs/superpowers/specs/2026-04-21-task-steering-bar-design.md`](../specs/2026-04-21-task-steering-bar-design.md)

---

## File Structure

**Create:**
- `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBarState.kt` — pure state model + derivation from `PendingCommand`.
- `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringIntent.kt` — sealed set of intents the bar can emit.
- `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBarController.kt` — ephemeral UI state (armed/sending/undo) with a coroutine-driven clock.
- `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBar.kt` — `@Composable` chip row.
- `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringTemplateSheet.kt` — `@Composable` template sheet for awaiting_user.
- `app/src/test/java/com/cocode/claudeemailapp/app/steering/SteeringBarStateTest.kt`
- `app/src/test/java/com/cocode/claudeemailapp/app/steering/SteeringBarControllerTest.kt`
- `app/src/androidTest/java/com/cocode/claudeemailapp/SteeringBarTest.kt`

**Modify:**
- `app/src/main/res/values/strings.xml` — add steering string resources.
- `app/src/main/java/com/cocode/claudeemailapp/data/PendingCommand.kt` — add `project: String?`.
- `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt` — store project on send; add `dispatchSteering(intent, pending)`.
- `app/src/main/java/com/cocode/claudeemailapp/app/ConversationScreen.kt` — accept pending + controller; render steering bar and template sheet above composer.
- `app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt` — look up pending-by-messageId, wire controller and dispatch.

---

## Task 1: Add project to PendingCommand

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/data/PendingCommand.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt:194-205`
- Test: `app/src/test/java/com/cocode/claudeemailapp/data/PendingCommandStoreTest.kt`

Steering cancel/reset intents need the project name; it's currently discarded after send.

- [ ] **Step 1: Write a failing test** that sendCommand stores project on the pending record.

```kotlin
// In app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt (new test)
@Test fun `sendCommand stores project on pending`() = runTest {
    val vm = buildVm()
    vm.sendCommand(to = "svc@x", project = "proj-x", body = "hello")
    advanceUntilIdle()
    val pending = vm.pending.value.single()
    assertEquals("proj-x", pending.project)
}
```

- [ ] **Step 2: Run test — expect FAIL** (field doesn't exist).

Run: `./gradlew :app:testDebugUnitTest --tests "*AppViewModelTest.sendCommand stores project on pending" --no-daemon`
Expected: compile error or assertion failure.

- [ ] **Step 3: Add the field.**

```kotlin
// PendingCommand.kt — insert after askId line (serializable, safe default)
    val project: String? = null
```

- [ ] **Step 4: Populate on send.**

```kotlin
// AppViewModel.kt — inside sendCommand, when constructing PendingCommand:
val pending = PendingCommand(
    messageId = result.messageId,
    sentAt = result.sentAt.time,
    to = to,
    subject = subject,
    kind = envelope.kind,
    bodyPreview = body.take(200),
    project = project
)
```

- [ ] **Step 5: Run tests — expect PASS.**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/data/PendingCommand.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt
git commit -m "feat(data): store project on PendingCommand"
```

---

## Task 2: SteeringBarState model + derivation

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBarState.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/steering/SteeringBarStateTest.kt`

A pure function maps `PendingCommand?` → one of four `SteeringBarState` variants. The controller layers ephemeral state (armed/sending) on top.

- [ ] **Step 1: Write failing tests.**

```kotlin
package com.cocode.claudeemailapp.app.steering

import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteeringBarStateTest {
    private fun pending(status: String, askId: String? = null) = PendingCommand(
        messageId = "m1", sentAt = 0L, to = "svc@x", subject = "s",
        kind = "command", bodyPreview = "", status = status, askId = askId, project = "p"
    )

    @Test fun `null pending → Hidden`() { assertEquals(SteeringBarState.Hidden, SteeringBarState.from(null)) }
    @Test fun `running → Idle`() { assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.RUNNING))) }
    @Test fun `queued → Idle`() { assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.QUEUED))) }
    @Test fun `awaiting_ack → Idle`() { assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.AWAITING_ACK))) }
    @Test fun `awaiting_user with askId → AwaitingUser`() {
        val s = SteeringBarState.from(pending(PendingStatus.AWAITING_USER, askId = "7"))
        assertEquals(SteeringBarState.AwaitingUser("7"), s)
    }
    @Test fun `done → Hidden`() { assertEquals(SteeringBarState.Hidden, SteeringBarState.from(pending(PendingStatus.DONE))) }
    @Test fun `failed → Hidden`() { assertEquals(SteeringBarState.Hidden, SteeringBarState.from(pending(PendingStatus.FAILED))) }
    @Test fun `error → Hidden`() { assertEquals(SteeringBarState.Hidden, SteeringBarState.from(pending(PendingStatus.ERROR))) }
}
```

- [ ] **Step 2: Run — expect FAIL** (type doesn't exist).

Run: `./gradlew :app:testDebugUnitTest --tests "*SteeringBarStateTest" --no-daemon`

- [ ] **Step 3: Implement.**

```kotlin
package com.cocode.claudeemailapp.app.steering

import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus

sealed interface SteeringBarState {
    data object Hidden : SteeringBarState
    data object Idle : SteeringBarState
    data class AwaitingUser(val askId: String) : SteeringBarState

    companion object {
        fun from(pending: PendingCommand?): SteeringBarState {
            if (pending == null) return Hidden
            return when (pending.status) {
                PendingStatus.AWAITING_ACK,
                PendingStatus.QUEUED,
                PendingStatus.RUNNING -> Idle
                PendingStatus.AWAITING_USER -> pending.askId?.let(::AwaitingUser) ?: Idle
                else -> Hidden
            }
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBarState.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/steering/SteeringBarStateTest.kt
git commit -m "feat(steering): add SteeringBarState derivation"
```

---

## Task 3: SteeringIntent sealed type

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringIntent.kt`

No tests: it's a pure data-shape file consumed by tests in later tasks.

- [ ] **Step 1: Create the file.**

```kotlin
package com.cocode.claudeemailapp.app.steering

sealed interface SteeringIntent {
    data object Status : SteeringIntent
    data object Cancel : SteeringIntent
    data object CancelDrainQueue : SteeringIntent
    data object Reset : SteeringIntent
    data class Reply(val askId: String, val body: String) : SteeringIntent
}
```

- [ ] **Step 2: Verify the module still builds.**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: SUCCESS.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringIntent.kt
git commit -m "feat(steering): add SteeringIntent sealed type"
```

---

## Task 4: SteeringBarController (arming + undo timers)

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBarController.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/steering/SteeringBarControllerTest.kt`

Holds three bits of ephemeral state: `armed` (cancel flagged for 3s), `sending` (intent dispatched, awaiting ack), `undoWindow` (5s post-fire undo available). All timers driven by the passed `CoroutineScope` + injectable `nowMs: () -> Long` so tests can use `StandardTestDispatcher`.

- [ ] **Step 1: Write failing tests.**

```kotlin
package com.cocode.claudeemailapp.app.steering

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SteeringBarControllerTest {
    @Test fun `tap cancel once arms bar`() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val fired = mutableListOf<SteeringIntent>()
        ctrl.onIntent = { fired += it }
        ctrl.tapCancel()
        assertTrue(ctrl.uiState.value.armed)
        assertTrue(fired.isEmpty())
    }
    @Test fun `tap cancel twice within 3s fires intent`() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        val fired = mutableListOf<SteeringIntent>()
        ctrl.onIntent = { fired += it }
        ctrl.tapCancel(); advanceTimeBy(1_000); ctrl.tapCancel()
        assertEquals(listOf<SteeringIntent>(SteeringIntent.Cancel), fired)
    }
    @Test fun `arm times out after 3s`() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        ctrl.tapCancel(); advanceTimeBy(3_100)
        assertFalse(ctrl.uiState.value.armed)
    }
    @Test fun `fire opens 5s undo window`() = runTest {
        val ctrl = SteeringBarController(TestScope(StandardTestDispatcher(testScheduler)))
        ctrl.tapCancel(); ctrl.tapCancel()
        assertTrue(ctrl.uiState.value.undoAvailable)
        advanceTimeBy(5_100)
        assertFalse(ctrl.uiState.value.undoAvailable)
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement.**

```kotlin
package com.cocode.claudeemailapp.app.steering

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SteeringBarController(private val scope: CoroutineScope) {
    data class UiState(
        val armed: Boolean = false,
        val sending: SteeringIntent? = null,
        val undoAvailable: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    /** Caller wires this to the ViewModel dispatch function. */
    var onIntent: (SteeringIntent) -> Unit = {}
    /** Caller wires this to undo handling. */
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
        val fired = lastFired ?: return
        undoJob?.cancel()
        _ui.value = _ui.value.copy(undoAvailable = false)
        onUndo(fired)
        lastFired = null
    }

    /** Called when the ViewModel observes the ack round-trip completing. */
    fun onAcked() { _ui.value = _ui.value.copy(sending = null) }

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
        _ui.value = UiState(armed = false, sending = intent, undoAvailable = intent is SteeringIntent.Cancel)
        onIntent(intent)
        lastFired = intent
        if (intent is SteeringIntent.Cancel) {
            undoJob?.cancel()
            undoJob = scope.launch {
                delay(UNDO_WINDOW_MS)
                _ui.value = _ui.value.copy(undoAvailable = false)
                lastFired = null
            }
        }
    }

    companion object {
        const val ARM_WINDOW_MS = 3_000L
        const val UNDO_WINDOW_MS = 5_000L
    }
}
```

- [ ] **Step 4: Run — expect PASS.**

Run: `./gradlew :app:testDebugUnitTest --tests "*SteeringBarControllerTest" --no-daemon`

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBarController.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/steering/SteeringBarControllerTest.kt
git commit -m "feat(steering): add SteeringBarController with arm/undo timers"
```

---

## Task 5: Wire dispatchSteering into AppViewModel

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt`
- Modify: `app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt`

One public method, `dispatchSteering(pending, intent)`, translates to the right `Envelopes` builder and sends.

- [ ] **Step 1: Write a failing test for `Status` intent.**

```kotlin
@Test fun `dispatchSteering Status sends a status envelope`() = runTest {
    val sender = FakeMailSender()
    val vm = buildVm(sender = sender)
    val pending = PendingCommand(messageId = "m1", sentAt = 0L, to = "svc@x", subject = "s",
        kind = "command", bodyPreview = "", taskId = 42L, project = "p")
    vm.dispatchSteering(pending, SteeringIntent.Status); advanceUntilIdle()
    val body = sender.lastOutgoing?.body ?: error("no send")
    assertTrue(body.contains("\"kind\":\"status\""))
    assertTrue(body.contains("\"task_id\":42"))
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement.**

```kotlin
// In AppViewModel.kt — add import:
import com.cocode.claudeemailapp.app.steering.SteeringIntent

// And a new method:
fun dispatchSteering(pending: PendingCommand, intent: SteeringIntent) {
    val creds = _credentials.value ?: return
    val project = pending.project ?: return
    val envelope = when (intent) {
        SteeringIntent.Status -> Envelopes.status(
            taskId = pending.taskId, project = project,
            auth = creds.sharedSecret.takeIf(String::isNotBlank))
        SteeringIntent.Cancel -> Envelopes.cancel(
            project = project, drainQueue = false,
            auth = creds.sharedSecret.takeIf(String::isNotBlank))
        SteeringIntent.CancelDrainQueue -> Envelopes.cancel(
            project = project, drainQueue = true,
            auth = creds.sharedSecret.takeIf(String::isNotBlank))
        SteeringIntent.Reset -> Envelopes.reset(
            project = project, auth = creds.sharedSecret.takeIf(String::isNotBlank))
        is SteeringIntent.Reply -> Envelopes.reply(
            taskId = pending.taskId ?: return,
            body = intent.body,
            askId = intent.askId.toLongOrNull(),
            auth = creds.sharedSecret.takeIf(String::isNotBlank))
    }
    viewModelScope.launch {
        _send.value = SendState(sending = true)
        try {
            val outgoing = OutgoingMessage.envelope(
                to = pending.to, subject = "Re: ${pending.subject}",
                envelope = envelope,
                inReplyTo = pending.messageId.takeIf(String::isNotBlank))
            val result = mailSender.send(creds, outgoing)
            _send.value = SendState(sending = false, justSentMessageId = result.messageId)
            refreshInbox()
        } catch (e: MailException) { _send.value = SendState(sending = false, lastError = e.message) }
          catch (t: Throwable)      { _send.value = SendState(sending = false, lastError = t.message) }
    }
}
```

- [ ] **Step 4: Run — expect PASS.**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppViewModelTest.dispatchSteering*" --no-daemon`

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt
git commit -m "feat(steering): add dispatchSteering on AppViewModel"
```

---

## Task 6: Add strings to strings.xml

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add all steering strings.**

```xml
<resources>
    <string name="app_name">Claude Email App</string>

    <string name="steering_chip_status">Status</string>
    <string name="steering_chip_cancel">Cancel</string>
    <string name="steering_chip_cancel_confirm">Cancel · confirm</string>
    <string name="steering_chip_cancel_drain">Cancel + drain queue</string>
    <string name="steering_chip_reset">Reset</string>
    <string name="steering_chip_more">More</string>
    <string name="steering_chip_sending">Sending…</string>

    <string name="steering_armed_hint">Tap Cancel again to confirm · %1$ss</string>
    <string name="steering_undo_snackbar">Cancelling task #%1$d · Undo</string>

    <string name="steering_templates_heading">Reply templates</string>
    <string name="steering_advanced_heading">advanced</string>
    <string name="template_continue">Looks good, continue</string>
    <string name="template_abort">Abort and retry</string>
    <string name="template_explain">Explain the tradeoff in more detail</string>
</resources>
```

- [ ] **Step 2: Verify build.**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: SUCCESS.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(steering): add string resources"
```

---

## Task 7: SteeringBar composable

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBar.kt`
- Create: `app/src/androidTest/java/com/cocode/claudeemailapp/SteeringBarTest.kt`

- [ ] **Step 1: Write failing Compose UI test for idle chips visible.**

```kotlin
package com.cocode.claudeemailapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.cocode.claudeemailapp.app.steering.SteeringBar
import com.cocode.claudeemailapp.app.steering.SteeringBarController
import com.cocode.claudeemailapp.app.steering.SteeringBarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class SteeringBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun idle_shows_status_cancel_more_chips() {
        rule.setContent {
            SteeringBar(
                state = SteeringBarState.Idle,
                controller = SteeringBarController(CoroutineScope(Dispatchers.Main))
            )
        }
        rule.onNodeWithTag("steering_chip_status").assertIsDisplayed()
        rule.onNodeWithTag("steering_chip_cancel").assertIsDisplayed()
        rule.onNodeWithTag("steering_chip_more").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Implement the composable.**

```kotlin
package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

@Composable
fun SteeringBar(
    state: SteeringBarState,
    controller: SteeringBarController,
    modifier: Modifier = Modifier
) {
    if (state !is SteeringBarState.Idle) return
    val ui by controller.uiState.collectAsState()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { controller.tapStatus() },
            enabled = ui.sending == null,
            label = {
                Text(if (ui.sending == SteeringIntent.Status) stringResource(R.string.steering_chip_sending)
                     else stringResource(R.string.steering_chip_status))
            },
            modifier = Modifier.testTag("steering_chip_status")
        )
        AssistChip(
            onClick = { controller.tapCancel() },
            enabled = ui.sending == null,
            colors = AssistChipDefaults.assistChipColors(
                labelColor = if (ui.armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            ),
            label = {
                Text(if (ui.armed) stringResource(R.string.steering_chip_cancel_confirm)
                     else stringResource(R.string.steering_chip_cancel))
            },
            modifier = Modifier.testTag("steering_chip_cancel")
        )
        AssistChip(
            onClick = { /* More sheet deferred — see "Out of Scope" */ },
            enabled = ui.sending == null,
            label = { Text(stringResource(R.string.steering_chip_more)) },
            modifier = Modifier.testTag("steering_chip_more")
        )
    }
}
```

- [ ] **Step 3: Run — expect PASS.**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*SteeringBarTest.idle_shows_status_cancel_more_chips" --no-daemon`

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringBar.kt \
        app/src/androidTest/java/com/cocode/claudeemailapp/SteeringBarTest.kt
git commit -m "feat(steering): add SteeringBar composable with idle state"
```

---

## Task 8: SteeringTemplateSheet composable

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringTemplateSheet.kt`
- Modify: `app/src/androidTest/java/com/cocode/claudeemailapp/SteeringBarTest.kt`

- [ ] **Step 1: Write failing Compose UI test for awaiting_user.**

```kotlin
@Test fun awaiting_user_shows_three_templates() {
    rule.setContent {
        SteeringTemplateSheet(
            askId = "7",
            onTemplateTap = {}
        )
    }
    rule.onNodeWithTag("steering_template_continue").assertIsDisplayed()
    rule.onNodeWithTag("steering_template_abort").assertIsDisplayed()
    rule.onNodeWithTag("steering_template_explain").assertIsDisplayed()
}
```

- [ ] **Step 2: Implement.**

```kotlin
package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

@Composable
fun SteeringTemplateSheet(
    askId: String,
    onTemplateTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val continueText = stringResource(R.string.template_continue)
    val abortText = stringResource(R.string.template_abort)
    val explainText = stringResource(R.string.template_explain)
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.steering_templates_heading), style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { onTemplateTap(continueText) },
            modifier = Modifier.fillMaxWidth().testTag("steering_template_continue")) { Text(continueText) }
        OutlinedButton(onClick = { onTemplateTap(abortText) },
            modifier = Modifier.fillMaxWidth().testTag("steering_template_abort")) { Text(abortText) }
        OutlinedButton(onClick = { onTemplateTap(explainText) },
            modifier = Modifier.fillMaxWidth().testTag("steering_template_explain")) { Text(explainText) }
    }
}
```

- [ ] **Step 3: Run — expect PASS.**

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/steering/SteeringTemplateSheet.kt \
        app/src/androidTest/java/com/cocode/claudeemailapp/SteeringBarTest.kt
git commit -m "feat(steering): add SteeringTemplateSheet composable"
```

---

## Task 9: Wire the bar into ConversationScreen + AppRoot

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/ConversationScreen.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt`

ConversationScreen grows a new parameter: `pending: PendingCommand?`, plus the controller + dispatch callback. The bar renders above the composer when `SteeringBarState.from(pending) != Hidden`; otherwise the screen behaves as it does today.

Because `ConversationScreen.kt` is already at ~210 lines, extract `ReplyComposer` into a sibling file `ReplyComposer.kt` as part of this task to stay under the project's 200-line-per-file target.

- [ ] **Step 1: Move `ReplyComposer` into its own file.**

Create `app/src/main/java/com/cocode/claudeemailapp/app/ReplyComposer.kt` containing the existing private `ReplyComposer` composable (imports: `androidx.compose.foundation.layout.*`, `androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.material3.*`, `androidx.compose.runtime.Composable`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.text.input.KeyboardCapitalization`, `androidx.compose.ui.unit.dp`). Make it `internal` so `ConversationScreen` can call it.

Delete the corresponding block from `ConversationScreen.kt`.

- [ ] **Step 2: Update ConversationScreen signature and body.**

```kotlin
@Composable
fun ConversationScreen(
    message: FetchedMessage,
    pending: com.cocode.claudeemailapp.data.PendingCommand?,
    sending: Boolean,
    sendError: String?,
    onBack: () -> Unit,
    onSendReply: (body: String) -> Unit,
    onSteeringIntent: (com.cocode.claudeemailapp.app.steering.SteeringIntent) -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = remember { SteeringBarController(scope).also { it.onIntent = onSteeringIntent } }
    val steering = SteeringBarState.from(pending)

    var reply by rememberSaveable(message.messageId) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().imePadding().testTag("conversation_screen")) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") { HeaderCard(message = message, onBack = onBack) }
            item(key = "body") { BodyCard(message = message) }
            sendError?.let { item(key = "error") { ErrorCard(message = it) } }
        }
        when (steering) {
            SteeringBarState.Idle -> SteeringBar(state = steering, controller = controller)
            is SteeringBarState.AwaitingUser -> SteeringTemplateSheet(
                askId = steering.askId,
                onTemplateTap = { template -> reply = if (reply.isBlank()) template else "$reply\n$template" }
            )
            SteeringBarState.Hidden -> {}
        }
        ReplyComposer(
            reply = reply,
            onReplyChange = { reply = it },
            sending = sending,
            onSend = {
                val trimmed = reply.trim()
                if (trimmed.isNotBlank()) {
                    if (steering is SteeringBarState.AwaitingUser) {
                        onSteeringIntent(com.cocode.claudeemailapp.app.steering.SteeringIntent.Reply(steering.askId, trimmed))
                    } else {
                        onSendReply(trimmed)
                    }
                    reply = ""
                }
            }
        )
    }
}
```

Add the imports at the top: `com.cocode.claudeemailapp.app.steering.*`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.rememberCoroutineScope`.

- [ ] **Step 3: Update AppRoot Conversation branch.**

```kotlin
Screen.Conversation -> {
    val message = inbox.messages.firstOrNull { it.messageId == selectedMessageId }
    val matchedPending = pending.firstOrNull { it.messageId == selectedMessageId }
    if (message == null) {
        screen = Screen.Home
    } else {
        ConversationScreen(
            message = message,
            pending = matchedPending,
            sending = send.sending,
            sendError = send.lastError,
            onBack = { screen = Screen.Home },
            onSendReply = { body ->
                viewModel.sendMessage(
                    to = replyTo(message, credentials?.emailAddress),
                    subject = replySubject(message.subject, credentials?.sharedSecret),
                    body = body,
                    inReplyTo = message.messageId.takeIf(String::isNotBlank),
                    references = buildReferences(message)
                )
            },
            onSteeringIntent = { intent ->
                matchedPending?.let { viewModel.dispatchSteering(it, intent) }
            }
        )
    }
}
```

- [ ] **Step 4: Verify build + all tests.**

Run: `./gradlew buildSmoke --no-daemon`
Expected: SUCCESS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/ReplyComposer.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/ConversationScreen.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt
git commit -m "feat(steering): wire SteeringBar into ConversationScreen"
```

---

## Task 10: End-to-end flow test (Compose UI + fake VM)

**Files:**
- Create: `app/src/androidTest/java/com/cocode/claudeemailapp/SteeringFlowTest.kt`

Verify the full golden path in the UI: idle bar visible → tap Cancel twice → intent fired. Uses a fake `onSteeringIntent` lambda.

- [ ] **Step 1: Write the flow test.**

```kotlin
package com.cocode.claudeemailapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.cocode.claudeemailapp.app.ConversationScreen
import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.mail.FetchedMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SteeringFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun two_taps_on_cancel_fire_Cancel_intent() {
        val fired = mutableListOf<SteeringIntent>()
        val msg = FetchedMessage(messageId = "m1", from = "svc@x", fromName = null,
            to = listOf("me@x"), subject = "s", body = "b", receivedAt = 0L,
            references = emptyList(), inReplyTo = null, envelope = null)
        val pending = PendingCommand(messageId = "m1", sentAt = 0L, to = "svc@x", subject = "s",
            kind = "command", bodyPreview = "", taskId = 42L, status = PendingStatus.RUNNING, project = "p")

        rule.setContent {
            ConversationScreen(
                message = msg, pending = pending, sending = false, sendError = null,
                onBack = {}, onSendReply = {}, onSteeringIntent = { fired += it }
            )
        }
        rule.onNodeWithTag("steering_chip_cancel").performClick()
        rule.onNodeWithTag("steering_chip_cancel").performClick()
        assertEquals(listOf<SteeringIntent>(SteeringIntent.Cancel), fired)
    }
}
```

- [ ] **Step 2: Run the test.**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*SteeringFlowTest*" --no-daemon`
Expected: PASS.

- [ ] **Step 3: Final smoke.**

Run: `./gradlew buildSmoke --no-daemon`
Expected: SUCCESS.

- [ ] **Step 4: Commit.**

```bash
git add app/src/androidTest/java/com/cocode/claudeemailapp/SteeringFlowTest.kt
git commit -m "test(steering): end-to-end two-tap cancel flow"
```

---

## Out of Scope (deferred, already in spec)

- HomeScreen thread-collapse.
- Dashboard multi-task view.
- Command palette / keyboard shortcuts.
- Offline queueing of steering intents.
- Rich `More` sheet with per-template previews (the chip exists in Task 7 but opens nothing until a follow-up plan).
- Cancel-and-drain-queue confirm dialog (builder exists in ViewModel; no UI surface in v1).

## Self-Review Notes

Spec coverage: each of §3.1–§3.4 is covered by Tasks 2/7/8/9; §4 load-bearing decisions map to Tasks 2 (framing), 4 (two-tap + undo), 4+7 (three chip states via `sending`), 6+8 (templates in strings.xml). Error handling §6 is exercised via existing `SendState.lastError` wiring; dedicated snackbars are acceptable follow-ups. Tests §7 are covered by Tasks 2, 4, 7, 8, 10.

No placeholders, no "implement later." All type names consistent across tasks (`SteeringBarState`, `SteeringIntent`, `SteeringBarController`).
