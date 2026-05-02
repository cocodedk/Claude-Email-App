# Inbox Notifications v0 — Implementation Plan

> v0 ships the slim path: reuse the existing 15s poll + diff against last snapshot + post a notification per new non-ACK message, suppressed when foregrounded. v1+ items (IMAP IDLE, watcher plumbing, deep-link routing, persistent water mark, etc.) are deferred — see the spec's "v0 in / v0 out" table.

**Goal:** Buzz the device when an agent reply arrives while the app is in recents. Stop when the user swipes the app away.

**Architecture:** A new `Application` subclass registers the notification channel. The existing `AppViewModel.refreshInbox()` gains a "diff vs. previous snapshot" step that hands new non-ACK messages to a tiny `InboxNotifier`, which posts via `NotificationManagerCompat`. Activity-scoped polling means swipe-from-recents stops everything for free.

**Spec:** `docs/superpowers/specs/2026-05-02-inbox-notifications-design.md` (see "v0 scope" header)

---

## Task 1 — branch + baseline green

- [ ] **Step 1:** `git status --short && ./gradlew buildSmoke --no-daemon` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `git checkout -b feat/inbox-notifications-v0`.

---

## Task 2 — `InboxNotificationPrefs` (toggle only)

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/data/InboxNotificationPrefs.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/data/InboxNotificationPrefsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.claudeemailapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InboxNotificationPrefsTest {
    private lateinit var prefs: InboxNotificationPrefs

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteSharedPreferences(InboxNotificationPrefs.PREFS_NAME)
        prefs = InboxNotificationPrefs(ctx)
    }

    @Test fun `enabled by default`() = assertTrue(prefs.notificationsEnabled.value)
    @Test fun `toggle persists`() {
        prefs.setNotificationsEnabled(false)
        assertFalse(prefs.notificationsEnabled.value)
        // New instance reads the persisted value
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(InboxNotificationPrefs(ctx).notificationsEnabled.value)
    }
}
```

- [ ] **Step 2: Run, confirm fail.** `./gradlew :app:testDebugUnitTest --tests 'com.cocode.claudeemailapp.data.InboxNotificationPrefsTest' --no-daemon`
- [ ] **Step 3: Implement**

```kotlin
package com.cocode.claudeemailapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InboxNotificationPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        const val PREFS_NAME = "notification_prefs"
        private const val KEY_ENABLED = "enabled"
    }
}
```

- [ ] **Step 4: Run, confirm pass.**
- [ ] **Step 5: Commit.** `git commit -am "feat(data): InboxNotificationPrefs with notificationsEnabled toggle"`

---

## Task 3 — `InboxNotifier` (decision + post)

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/InboxNotifierTest.kt`

`InboxNotifier` has two surfaces:
- pure `shouldPost(message, enabled, isForeground): Boolean` — testable without Android.
- `post(message)` — does the actual `NotificationManagerCompat.notify`.

- [ ] **Step 1: Write the decision test**

```kotlin
package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.Kinds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxNotifierDecisionTest {
    private fun msg(kind: String?): FetchedMessage = FetchedMessage(
        messageId = "<m@x>", from = "claude@example.com", fromName = null,
        to = listOf("u@example.com"), subject = "s", body = "b",
        sentAt = null, receivedAt = null, inReplyTo = null, references = emptyList(),
        isSeen = false, contentType = "application/json",
        envelope = if (kind == null) null else Envelope(kind = kind)
    )

    @Test fun `skip when disabled`() =
        assertFalse(InboxNotifier.shouldPost(msg(Kinds.RESULT), enabled = false, isForeground = false))
    @Test fun `skip when foreground`() =
        assertFalse(InboxNotifier.shouldPost(msg(Kinds.RESULT), enabled = true, isForeground = true))
    @Test fun `skip ACK`() =
        assertFalse(InboxNotifier.shouldPost(msg(Kinds.ACK), enabled = true, isForeground = false))
    @Test fun `post on PROGRESS QUESTION RESULT ERROR`() {
        for (k in listOf(Kinds.PROGRESS, Kinds.QUESTION, Kinds.RESULT, Kinds.ERROR)) {
            assertTrue("kind=$k", InboxNotifier.shouldPost(msg(k), enabled = true, isForeground = false))
        }
    }
    @Test fun `fail open on null envelope`() =
        assertTrue(InboxNotifier.shouldPost(msg(null), enabled = true, isForeground = false))
    @Test fun `fail open on unknown kind`() =
        assertTrue(InboxNotifier.shouldPost(msg("future_kind"), enabled = true, isForeground = false))
}
```

- [ ] **Step 2: Run, confirm fail.**
- [ ] **Step 3: Implement**

```kotlin
package com.cocode.claudeemailapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cocode.claudeemailapp.MainActivity
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Kinds

class InboxNotifier(
    private val context: Context,
    private val prefs: InboxNotificationPrefs,
    private val isForeground: () -> Boolean
) {

    fun handle(message: FetchedMessage) {
        if (!shouldPost(message, prefs.notificationsEnabled.value, isForeground())) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val id = (message.messageId.takeIf { it.isNotBlank() } ?: message.subject).hashCode()
        val tap = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = message.fromName?.takeIf { it.isNotBlank() } ?: message.from
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.subject))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try { nm.notify(id, notif) } catch (_: SecurityException) { /* POST_NOTIFICATIONS denied */ }
    }

    companion object {
        const val CHANNEL_ID = "replies"
        private const val CHANNEL_NAME = "Replies"
        private const val CHANNEL_DESC = "Notifications when an agent replies to one of your commands."

        fun registerChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = CHANNEL_DESC }
            )
        }

        fun shouldPost(message: FetchedMessage, enabled: Boolean, isForeground: Boolean): Boolean {
            if (!enabled) return false
            if (isForeground) return false
            if (message.envelope?.kind == Kinds.ACK) return false
            return true
        }
    }
}
```

- [ ] **Step 4: Run, confirm decision tests pass.**
- [ ] **Step 5: Commit.** `git commit -am "feat(app): InboxNotifier — decision rule + post + replies channel"`

---

## Task 4 — `ClaudeEmailApplication` + manifest

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/ClaudeEmailApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

> **Name:** the existing Composable in `AppRoot.kt` is named `ClaudeEmailApp` — the Application subclass is `ClaudeEmailApplication` to avoid a name clash.

- [ ] **Step 1: Create the Application class**

```kotlin
package com.cocode.claudeemailapp.app

import android.app.Application

class ClaudeEmailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        InboxNotifier.registerChannel(this)
    }
}
```

- [ ] **Step 2: Wire in manifest**

In `app/src/main/AndroidManifest.xml`, add `POST_NOTIFICATIONS` permission and the `android:name` attribute on `<application>`:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".app.ClaudeEmailApplication"
        android:allowBackup="true"
        ...
```

- [ ] **Step 3: Compile + run all tests**

`./gradlew :app:testDebugUnitTest --no-daemon` → ALL PASS (the channel registration is a no-op in JVM tests; just confirm nothing regressed).

- [ ] **Step 4: Commit.** `git commit -am "feat(app): ClaudeEmailApplication + POST_NOTIFICATIONS permission"`

---

## Task 5 — wire `InboxNotifier` into `AppViewModel.refreshInbox()`

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt`
- Modify: `app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt`

### Plan inside `refreshInbox()`

After `mailFetcher.fetchRecent` succeeds and BEFORE `_inbox.value = ...`:
1. Compute `previousIds = _inbox.value.messages.map { it.messageId }.toSet()`.
2. Compute `newOnes = messages.filter { it.messageId.isNotBlank() && it.messageId !in previousIds }`.
3. If `firstPollDone` is true, hand each `newOnes` to `inboxNotifier.handle(it)`.
4. Set `firstPollDone = true`.
5. Continue with the existing state update + `reconcilePending`.

`firstPollDone` is a `private var` on the ViewModel — initially false; flipping after the first successful fetch suppresses notifications for the historical backlog.

`inboxNotifier` and `notificationsEnabled` exposure:
- Add `private val inboxNotifier: InboxNotifier? = null` constructor parameter (defaults null so existing tests don't break).
- Add `private val notificationPrefs: InboxNotificationPrefs? = null` parameter.
- Expose `val notificationsEnabled: StateFlow<Boolean> = notificationPrefs?.notificationsEnabled ?: MutableStateFlow(true).asStateFlow()`.
- Expose `fun setNotificationsEnabled(b: Boolean) { notificationPrefs?.setNotificationsEnabled(b) }`.
- The factory used in production wires both; the test factory passes nulls.

- [ ] **Step 1: Inspect the ViewModel factory pattern**

```bash
grep -n "Factory\|companion object\|provideFactory\|Application" app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt | head -20
```

Add `inboxNotifier` + `notificationPrefs` to the factory's instantiation. In production use:

```kotlin
val prefs = InboxNotificationPrefs(application)
val notifier = InboxNotifier(
    context = application,
    prefs = prefs,
    isForeground = {
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    }
)
AppViewModel(/* existing args */, inboxNotifier = notifier, notificationPrefs = prefs)
```

(`androidx.lifecycle.ProcessLifecycleOwner` requires the `lifecycle-process` artifact. Add to `gradle/libs.versions.toml` + `app/build.gradle.kts` per the spec's deps row.)

- [ ] **Step 2: Add the lifecycle-process dependency**

In `gradle/libs.versions.toml` `[libraries]` (after `androidx-lifecycle-viewmodel-compose`):

```toml
androidx-lifecycle-process = { group = "androidx.lifecycle", name = "lifecycle-process", version.ref = "lifecycleRuntimeKtx" }
```

In `app/build.gradle.kts` `dependencies { ... }`:

```kotlin
    implementation(libs.androidx.lifecycle.process)
```

- [ ] **Step 3: Write a test for the diff + notify behavior**

In `AppViewModelTest.kt`, add a test that uses a spy `InboxNotifier` (mock via MockK) and verifies:

- First poll → no `notifier.handle` calls (suppressed).
- Second poll with one new message → exactly one `notifier.handle` call with that message.
- Second poll with no new messages → zero calls.

Use the existing test helpers / factory; pass a MockK-relaxed `InboxNotifier`.

```kotlin
@Test
fun `notifier suppressed on first poll then fires for new messages`() = runTest {
    val notifier = mockk<InboxNotifier>(relaxed = true)
    val vm = createViewModel(  // existing helper; thread the new params through
        inboxNotifier = notifier
    )
    // Configure the fake mailFetcher to return [m1] then [m1, m2]
    fakeFetcher.queueResult(listOf(m1))
    vm.refreshInbox()
    advanceUntilIdle()
    verify(exactly = 0) { notifier.handle(any()) }

    fakeFetcher.queueResult(listOf(m2, m1))
    vm.refreshInbox()
    advanceUntilIdle()
    verify(exactly = 1) { notifier.handle(eq(m2)) }
}
```

Adapt the test signature to whatever `createViewModel` helper already exists in `AppViewModelTest.kt`. If the test class has no fakeFetcher infrastructure, use `mockk<MailFetcher>().also { coEvery { it.fetchRecent(any(), any()) } returnsMany listOf(...) }`.

- [ ] **Step 4: Run, confirm fail.**
- [ ] **Step 5: Implement the diff + notify in `refreshInbox()`**

Modify `refreshInbox()` to:

```kotlin
    private var firstPollDone = false

    fun refreshInbox() {
        val creds = _credentials.value ?: return
        viewModelScope.launch {
            _inbox.value = _inbox.value.copy(loading = true, error = null)
            try {
                val messages = mailFetcher.fetchRecent(creds, count = 50)
                val previousIds = _inbox.value.messages.mapNotNull { it.messageId.takeIf { id -> id.isNotBlank() } }.toSet()
                val newOnes = messages.filter { it.messageId.isNotBlank() && it.messageId !in previousIds }
                _inbox.value = InboxState(
                    loading = false,
                    messages = messages,
                    lastFetchedAt = System.currentTimeMillis()
                )
                if (firstPollDone) newOnes.forEach { inboxNotifier?.handle(it) }
                firstPollDone = true
                reconcilePending(messages)
            } catch (e: MailException) {
                _inbox.value = _inbox.value.copy(loading = false, error = e.message)
            } catch (t: Throwable) {
                _inbox.value = _inbox.value.copy(loading = false, error = t.message)
            }
        }
    }
```

Add the new constructor params on `AppViewModel`:

```kotlin
class AppViewModel(
    private val credentialsStore: CredentialsStore,
    private val mailSender: MailSender,
    private val mailFetcher: MailFetcher,
    private val mailProbe: MailProbe,
    private val pendingStore: PendingCommandStore,
    private val conversationStateStore: ConversationStateStore,
    private val inboxNotifier: InboxNotifier? = null,
    private val notificationPrefs: InboxNotificationPrefs? = null
) : ViewModel() {
```

Add the toggle exposure (place near other StateFlow declarations):

```kotlin
    val notificationsEnabled: StateFlow<Boolean> =
        notificationPrefs?.notificationsEnabled
            ?: MutableStateFlow(true).asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPrefs?.setNotificationsEnabled(enabled)
    }
```

- [ ] **Step 6: Update the ViewModel factory** to wire production `InboxNotifier` + `InboxNotificationPrefs`. Find the factory in `AppViewModel.kt`'s companion (or wherever `viewModel { ... }` is built) and pass them in.

- [ ] **Step 7: Run all tests.** `./gradlew :app:testDebugUnitTest --no-daemon` → ALL PASS.

- [ ] **Step 8: Commit.** `git commit -am "feat(app): wire InboxNotifier into AppViewModel.refreshInbox with first-poll suppression"`

---

## Task 6 — Settings toggle + permission ask

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/MainActivity.kt`

- [ ] **Step 1: Add the Switch to Settings**

```bash
grep -n "Switch\|sync" app/src/main/java/com/cocode/claudeemailapp/app/SettingsScreen.kt | head -10
```

Locate the existing settings rows; add this row at the same nesting level:

```kotlin
val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column(Modifier.weight(1f)) {
        Text("Notify on replies", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Get a notification on the device when an agent reply arrives. Requires the app to remain in recents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Switch(
        checked = notificationsEnabled,
        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
    )
}
```

(Adjust imports for `Switch`, `collectAsState`, etc. as needed.)

- [ ] **Step 2: Request POST_NOTIFICATIONS in MainActivity**

In `MainActivity.kt`:

```kotlin
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* result is handled by the OS state; nothing to do here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeAskNotificationPermission()
        setContent { ClaudeEmailAppTheme { ClaudeEmailApp() } }
    }

    private fun maybeAskNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, perm)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        notificationPermissionLauncher.launch(perm)
    }
}
```

- [ ] **Step 3: Compile + run all tests + commit**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
git commit -am "feat(ui): Settings 'Notify on replies' toggle + POST_NOTIFICATIONS ask in MainActivity"
```

---

## Task 7 — buildSmoke, manual verify, PR

- [ ] **Step 1:** `./gradlew buildSmoke --no-daemon` → BUILD SUCCESSFUL.
- [ ] **Step 2:** Install: `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] **Step 3:** Manual checklist
  - [ ] Open app, accept POST_NOTIFICATIONS prompt.
  - [ ] Send a test command. Background the app (home button).
  - [ ] Wait ≤15s after the reply arrives. Confirm a notification appears.
  - [ ] Tap the notification. Confirm the app opens.
  - [ ] Send another command, swipe the app from recents instead of backgrounding. Confirm NO notification (activity died → polling stopped).
  - [ ] Toggle OFF in Settings. Send a command, background. Confirm NO notification.
  - [ ] Toggle ON. Send a command. Confirm notification fires again.
- [ ] **Step 4:** `git push -u origin feat/inbox-notifications-v0 && gh pr create --title "feat: inbox notifications v0 (poll-based)" --body "..."`

---

## Spec coverage check (v0)

| v0 spec requirement | Task |
|---|---|
| `replies` channel | 4 |
| `POST_NOTIFICATIONS` permission | 4 (manifest) + 6 (ask) |
| Notification on new non-ACK message | 3 + 5 |
| Foreground suppression | 3 (decision rule) |
| Settings toggle | 2 (prefs) + 5 (VM exposure) + 6 (UI) |
| First-poll suppression of historical backlog | 5 |
| `ClaudeEmailApplication` + manifest wiring | 4 |
| Tap → opens app | 3 (default `MainActivity` PendingIntent) |
| Stops on swipe-from-recents (free) | implicit; activity-scoped polling |

All v0 items have an implementing task. ✓
