# Inbox Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Buzz the device when a Claude reply arrives while the app is in recents (no foreground service, no FCM). Notifications stop when the user swipes the app away.

**Architecture:** A process-scoped IMAP IDLE listener runs alongside the existing 15s foreground poll. When the IMAP server pushes EXISTS, we fetch new UIDs, parse the existing `Envelope` JSON, skip ACK kind, and post a per-conversation notification (one ID per conversation, allocated from a persistent counter). The watcher reacts to credentials, settings toggle, network availability, and process lifecycle.

**Tech Stack:** Kotlin 2.3, Compose Material 3, Angus Mail 2.0.3 (jakarta.mail.IMAPFolder.idle()), `androidx.lifecycle:lifecycle-process`, `NotificationManagerCompat`, plain `SharedPreferences` (non-sensitive prefs). TDD with Robolectric + MockK.

**Spec:** `docs/superpowers/specs/2026-05-02-inbox-notifications-design.md`

**Naming notes for engineers:**
- The Composable named `ClaudeEmailApp` already exists in `app/AppRoot.kt`. The new `Application` subclass is therefore named **`ClaudeEmailApplication`** (file: `app/ClaudeEmailApplication.kt`).
- Credentials use `EncryptedSharedPreferences`, but notification prefs (toggle, UID water mark, ID allocator) are non-sensitive — they live in plain `SharedPreferences` named `"notification_prefs"`.

---

## Task 0: Confirm baseline green

**Files:** none

- [ ] **Step 1: Verify clean working tree on master**

```bash
git status --short
git rev-parse HEAD
```

Expected: empty working tree; HEAD is `89da34c` (the spec-revision commit) or newer.

- [ ] **Step 2: Run buildSmoke**

```bash
./gradlew buildSmoke --no-daemon
```

Expected: BUILD SUCCESSFUL. If anything fails, stop here and triage — do not proceed.

- [ ] **Step 3: Cut a feature branch**

```bash
git checkout -b feat/inbox-notifications
```

---

## Task 1: Add lifecycle-process dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library entries to version catalog**

In `gradle/libs.versions.toml`:

Under `[versions]` (after the existing `lifecycleRuntimeKtx` line — same version is reused):
no new line needed; lifecycle-process ships in the same BOM.

Under `[libraries]` (immediately after the existing `androidx-lifecycle-viewmodel-compose` entry):

```toml
androidx-lifecycle-process = { group = "androidx.lifecycle", name = "lifecycle-process", version.ref = "lifecycleRuntimeKtx" }
```

- [ ] **Step 2: Add dependency to `app/build.gradle.kts`**

In the `dependencies { ... }` block, immediately after the existing `implementation(libs.androidx.lifecycle.viewmodel.compose)` line:

```kotlin
    implementation(libs.androidx.lifecycle.process)
```

- [ ] **Step 3: Verify the build resolves the new dep**

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath --no-daemon | grep lifecycle-process
```

Expected: a line like `+--- androidx.lifecycle:lifecycle-process:2.10.0`.

- [ ] **Step 4: Compile**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add androidx.lifecycle:lifecycle-process for ProcessLifecycleOwner"
```

---

## Task 2: Extract `ImapSession` factory from `ImapMailFetcher`

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/mail/ImapSession.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/mail/ImapSessionTest.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt`

**Why:** `ImapIdleListener` (Task 4) needs the same IMAP session/properties setup. Extracting now avoids drift and keeps `ImapMailFetcher` under 200 LOC after the listener arrives.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/cocode/claudeemailapp/mail/ImapSessionTest.kt`:

```kotlin
package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class ImapSessionTest {

    private val creds = MailCredentials(
        displayName = "T",
        emailAddress = "u@example.com",
        password = "p",
        imapHost = "imap.example.com",
        imapPort = 1234,
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUseStartTls = false,
        serviceAddress = "claude@example.com",
        sharedSecret = "s"
    )

    @Test
    fun `imapProperties has expected keys`() {
        val p = ImapSession.imapProperties(creds)
        assertEquals("imaps", p.getProperty("mail.store.protocol"))
        assertEquals("imap.example.com", p.getProperty("mail.imaps.host"))
        assertEquals("1234", p.getProperty("mail.imaps.port"))
        assertEquals("true", p.getProperty("mail.imaps.ssl.enable"))
        assertEquals("true", p.getProperty("mail.imaps.ssl.checkserveridentity"))
        assertEquals(MailTimeouts.CONNECT_MS.toString(), p.getProperty("mail.imaps.connectiontimeout"))
        assertEquals(MailTimeouts.READ_MS.toString(), p.getProperty("mail.imaps.timeout"))
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails (no `ImapSession`)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.ImapSessionTest" --no-daemon
```

Expected: compilation error / unresolved reference `ImapSession`.

- [ ] **Step 3: Create `ImapSession.kt`**

Create `app/src/main/java/com/cocode/claudeemailapp/mail/ImapSession.kt`:

```kotlin
package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.Properties

/**
 * Shared IMAP session/store setup, extracted so ImapMailFetcher and ImapIdleListener
 * agree on properties (timeouts, TLS, identity check) without copy-paste drift.
 */
object ImapSession {

    fun imapProperties(credentials: MailCredentials): Properties = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", credentials.imapHost)
        put("mail.imaps.port", credentials.imapPort.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.ssl.checkserveridentity", "true")
        put("mail.imaps.connectiontimeout", MailTimeouts.CONNECT_MS.toString())
        put("mail.imaps.timeout", MailTimeouts.READ_MS.toString())
    }

    fun newSession(credentials: MailCredentials): Session =
        Session.getInstance(imapProperties(credentials))

    fun connect(session: Session, credentials: MailCredentials): Store =
        session.getStore("imaps").also {
            it.connect(credentials.imapHost, credentials.imapPort, credentials.emailAddress, credentials.password)
        }
}
```

- [ ] **Step 4: Refactor `ImapMailFetcher.kt` to delegate**

In `app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt`:

Delete the `internal fun imapProperties(...)` and the `DefaultStoreConnector` / `DefaultSessionFactory` helpers from the companion object, and replace the two factory parameters with delegation to `ImapSession`. Replace the file's `class ImapMailFetcher(...)` declaration:

```kotlin
class ImapMailFetcher(
    private val sessionFactory: (MailCredentials) -> Session = ImapSession::newSession,
    private val storeConnector: (Session, MailCredentials) -> Store = ImapSession::connect
) : MailFetcher {
```

Then in `fetchRecent`, change:

```kotlin
        val session = sessionFactory(imapProperties(credentials))
```

to:

```kotlin
        val session = sessionFactory(credentials)
```

Remove the now-unused `DefaultSessionFactory`, `DefaultStoreConnector`, and the inline `imapProperties` helper at the bottom of the companion. Keep all `toFetched()` / `tryParseEnvelope()` etc. as-is.

- [ ] **Step 5: Update existing `ImapMailFetcherTest` if it referenced `imapProperties`**

```bash
grep -n "imapProperties\|DefaultSessionFactory\|DefaultStoreConnector" app/src/test/java/com/cocode/claudeemailapp/mail/ImapMailFetcherTest.kt
```

If any hits: rewrite those tests to call `ImapSession.imapProperties(creds)` directly. If no hits, skip.

- [ ] **Step 6: Run all mail tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.*" --no-daemon
```

Expected: ALL PASS, including the new `ImapSessionTest` and the existing `ImapMailFetcherTest`.

- [ ] **Step 7: Verify file size budget**

```bash
wc -l app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt app/src/main/java/com/cocode/claudeemailapp/mail/ImapSession.kt
```

Expected: `ImapMailFetcher.kt` < 170 lines (down from 179); `ImapSession.kt` < 60.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/mail/ImapSession.kt \
        app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt \
        app/src/test/java/com/cocode/claudeemailapp/mail/ImapSessionTest.kt
git commit -m "refactor(mail): extract ImapSession factory shared by fetcher + future IDLE listener"
```

---

## Task 3: `InboxNotificationPrefs` data layer

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/data/InboxNotificationPrefs.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/data/InboxNotificationPrefsTest.kt`

**Responsibilities:**
1. `notificationsEnabled: StateFlow<Boolean>` — toggle (default ON).
2. `setNotificationsEnabled(enabled: Boolean)` — writer.
3. `waterMark(uidValidity: Long): Long?` — last-notified UID for the current UIDVALIDITY (`null` if validity changed or first run).
4. `setWaterMark(uidValidity: Long, uid: Long)` — writer; advances only the caller decides.
5. `notificationIdFor(conversationKey: String): Int` — atomic allocator; returns the same int for the same key forever (persisted).
6. `markFirstProgressSeen(conversationKey: String): Boolean` — returns `true` if this is the FIRST PROGRESS for the key (and records it); `false` on subsequent calls.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/cocode/claudeemailapp/data/InboxNotificationPrefsTest.kt`:

```kotlin
package com.cocode.claudeemailapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InboxNotificationPrefsTest {

    private lateinit var prefs: InboxNotificationPrefs

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteSharedPreferences(InboxNotificationPrefs.PREFS_NAME)
        prefs = InboxNotificationPrefs(ctx)
    }

    @Test
    fun `notifications enabled by default`() = runTest {
        assertTrue(prefs.notificationsEnabled.value)
    }

    @Test
    fun `toggle flips state`() = runTest {
        prefs.setNotificationsEnabled(false)
        assertFalse(prefs.notificationsEnabled.value)
        prefs.setNotificationsEnabled(true)
        assertTrue(prefs.notificationsEnabled.value)
    }

    @Test
    fun `water mark returns null on first run`() {
        assertNull(prefs.waterMark(uidValidity = 100L))
    }

    @Test
    fun `water mark roundtrips`() {
        prefs.setWaterMark(uidValidity = 100L, uid = 42L)
        assertEquals(42L, prefs.waterMark(uidValidity = 100L))
    }

    @Test
    fun `water mark resets on uidValidity change`() {
        prefs.setWaterMark(uidValidity = 100L, uid = 42L)
        // Now ask with a different validity — must be null (mailbox rebuilt)
        assertNull(prefs.waterMark(uidValidity = 200L))
    }

    @Test
    fun `notification id is stable per key`() {
        val a1 = prefs.notificationIdFor("conv-a")
        val a2 = prefs.notificationIdFor("conv-a")
        assertEquals(a1, a2)
    }

    @Test
    fun `notification id differs across keys`() {
        val a = prefs.notificationIdFor("conv-a")
        val b = prefs.notificationIdFor("conv-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `notification ids persist across instance recreation`() {
        val a = prefs.notificationIdFor("conv-a")
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs2 = InboxNotificationPrefs(ctx)
        assertEquals(a, prefs2.notificationIdFor("conv-a"))
    }

    @Test
    fun `markFirstProgressSeen returns true exactly once per key`() {
        assertTrue(prefs.markFirstProgressSeen("conv-a"))
        assertFalse(prefs.markFirstProgressSeen("conv-a"))
        assertTrue(prefs.markFirstProgressSeen("conv-b"))
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.data.InboxNotificationPrefsTest" --no-daemon
```

Expected: compilation failure (`InboxNotificationPrefs` not found).

- [ ] **Step 3: Implement `InboxNotificationPrefs.kt`**

Create `app/src/main/java/com/cocode/claudeemailapp/data/InboxNotificationPrefs.kt`:

```kotlin
package com.cocode.claudeemailapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-sensitive notification preferences:
 *   - master toggle ("Notify on replies"),
 *   - per-mailbox UID high-water mark (keyed by UIDVALIDITY),
 *   - stable conversation→Int notification ID allocator (atomic counter),
 *   - first-PROGRESS-seen set (for rate-limiting).
 *
 * Plain SharedPreferences — no encryption needed (no PII / no secrets here).
 */
class InboxNotificationPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun waterMark(uidValidity: Long): Long? {
        val storedValidity = sp.getLong(KEY_UIDVALIDITY, -1L)
        if (storedValidity != uidValidity) return null
        val stored = sp.getLong(KEY_LAST_UID, -1L)
        return if (stored < 0) null else stored
    }

    fun setWaterMark(uidValidity: Long, uid: Long) {
        sp.edit()
            .putLong(KEY_UIDVALIDITY, uidValidity)
            .putLong(KEY_LAST_UID, uid)
            .apply()
    }

    @Synchronized
    fun notificationIdFor(conversationKey: String): Int {
        val mapKey = KEY_NOTIF_ID_PREFIX + conversationKey
        val existing = sp.getInt(mapKey, -1)
        if (existing != -1) return existing
        val next = sp.getInt(KEY_NEXT_NOTIF_ID, FIRST_NOTIF_ID)
        sp.edit()
            .putInt(mapKey, next)
            .putInt(KEY_NEXT_NOTIF_ID, next + 1)
            .apply()
        return next
    }

    @Synchronized
    fun markFirstProgressSeen(conversationKey: String): Boolean {
        val key = KEY_PROGRESS_SEEN_PREFIX + conversationKey
        if (sp.getBoolean(key, false)) return false
        sp.edit().putBoolean(key, true).apply()
        return true
    }

    companion object {
        const val PREFS_NAME = "notification_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_UIDVALIDITY = "uid_validity"
        private const val KEY_LAST_UID = "last_uid"
        private const val KEY_NEXT_NOTIF_ID = "next_notif_id"
        private const val KEY_NOTIF_ID_PREFIX = "notif_id__"
        private const val KEY_PROGRESS_SEEN_PREFIX = "progress_seen__"
        // Start IDs from 1000 so we don't collide with any future hand-picked
        // notification IDs the app might use elsewhere (e.g. ongoing service).
        private const val FIRST_NOTIF_ID = 1000
    }
}
```

- [ ] **Step 4: Run the test, confirm pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.data.InboxNotificationPrefsTest" --no-daemon
```

Expected: 8 tests pass.

- [ ] **Step 5: Verify file size**

```bash
wc -l app/src/main/java/com/cocode/claudeemailapp/data/InboxNotificationPrefs.kt
```

Expected: < 100.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/data/InboxNotificationPrefs.kt \
        app/src/test/java/com/cocode/claudeemailapp/data/InboxNotificationPrefsTest.kt
git commit -m "feat(data): InboxNotificationPrefs (toggle, UID water mark, ID allocator, PROGRESS-seen)"
```

---

## Task 4: `ImapIdleListener` interface + capability check

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/mail/ImapIdleListenerTest.kt`

**Note on testing strategy:** `IMAPFolder.idle()` is a blocking JNI-style network call; we cannot easily exercise it in pure JVM tests. We test what we can in unit form (capability fallback, callback signature) and rely on the existing `MailIntegrationTest` harness for end-to-end IDLE in Task 7.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/cocode/claudeemailapp/mail/ImapIdleListenerTest.kt`:

```kotlin
package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Session
import jakarta.mail.Store
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.eclipse.angus.mail.imap.IMAPStore
import org.junit.Assert.assertFalse
import org.junit.Test

class ImapIdleListenerTest {

    private val creds = MailCredentials(
        displayName = "T", emailAddress = "u@example.com", password = "p",
        imapHost = "imap.example.com", imapPort = 993,
        smtpHost = "s", smtpPort = 465, smtpUseStartTls = false,
        serviceAddress = "claude@example.com", sharedSecret = "s"
    )

    @Test
    fun `listener exits cleanly when server lacks IDLE capability`() = runTest {
        val store = mockk<IMAPStore>(relaxed = true)
        every { store.hasCapability("IDLE") } returns false
        val session = mockk<Session>(relaxed = true)

        val listener = ImapIdleListener(
            sessionFactory = { _ -> session },
            storeConnector = { _, _ -> store as Store }
        )

        var calls = 0
        val supports = listener.supportsIdle(creds)
        if (supports) listener.listen(creds) { calls++ }

        assertFalse(supports)
        verify(exactly = 0) { store.getFolder(any<String>()) }
    }
}
```

- [ ] **Step 2: Run the test, confirm fail (`ImapIdleListener` not found)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.ImapIdleListenerTest" --no-daemon
```

Expected: compilation error.

- [ ] **Step 3: Create `ImapIdleListener.kt` (interface + capability stub)**

Create `app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt`:

```kotlin
package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Session
import jakarta.mail.Store
import org.eclipse.angus.mail.imap.IMAPStore

/**
 * Long-lived IMAP IDLE listener. Owns one IMAP connection per `listen()` call,
 * issues IDLE on the INBOX, and invokes [onArrival] every time the server pushes
 * EXISTS. The listener runs on Dispatchers.IO inside the caller's coroutine; cancel
 * the coroutine to stop — we close the folder/store in the finally block, which
 * is what unblocks the underlying blocking IDLE call.
 *
 * Capability fallback: callers SHOULD invoke [supportsIdle] before [listen]. If
 * the server does not advertise IDLE, [listen] returns immediately without error
 * (the existing 15s foreground poll continues to work).
 */
class ImapIdleListener(
    private val sessionFactory: (MailCredentials) -> Session = ImapSession::newSession,
    private val storeConnector: (Session, MailCredentials) -> Store = ImapSession::connect
) {

    /** Connect once and check IDLE capability; close immediately. */
    suspend fun supportsIdle(credentials: MailCredentials): Boolean {
        val session = sessionFactory(credentials)
        val store = storeConnector(session, credentials)
        return try {
            (store as? IMAPStore)?.hasCapability("IDLE") == true
        } finally {
            try { if (store.isConnected) store.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Connect, IDLE, emit on every arrival. Suspends until the coroutine is cancelled
     * or the connection drops (in which case it returns; the watcher does the backoff).
     *
     * @param onArrival called on the IO thread with `lastUidNext` (server-reported
     *                  UIDNEXT just after the EXISTS push) — caller fetches `> waterMark`.
     */
    suspend fun listen(
        credentials: MailCredentials,
        onArrival: suspend (lastUidNext: Long) -> Unit
    ) {
        // Implementation deferred to Task 5.
        TODO("Task 5: implement IDLE loop")
    }
}
```

- [ ] **Step 4: Run the test, confirm pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.ImapIdleListenerTest" --no-daemon
```

Expected: 1 test pass (the test only exercises `supportsIdle`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt \
        app/src/test/java/com/cocode/claudeemailapp/mail/ImapIdleListenerTest.kt
git commit -m "feat(mail): ImapIdleListener scaffold + IDLE capability check"
```

---

## Task 5: `ImapIdleListener` IDLE loop with Dispatchers.IO + close-to-break

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt`
- Modify: `app/src/test/java/com/cocode/claudeemailapp/mail/ImapIdleListenerTest.kt`

- [ ] **Step 1: Add the cancellation-closes-folder test**

Append to `ImapIdleListenerTest.kt`:

```kotlin
    @Test
    fun `cancelling the coroutine closes folder and store`() = runTest {
        val folder = mockk<org.eclipse.angus.mail.imap.IMAPFolder>(relaxed = true)
        every { folder.isOpen } returns true
        // idle() blocks indefinitely until folder is closed
        every { folder.idle() } answers {
            // Simulate the blocking nature: sleep until interrupted
            try { Thread.sleep(60_000) } catch (_: InterruptedException) {}
        }

        val store = mockk<IMAPStore>(relaxed = true) {
            every { hasCapability("IDLE") } returns true
            every { isConnected } returns true
            every { getFolder("INBOX") } returns folder
        }
        val session = mockk<Session>(relaxed = true)

        val listener = ImapIdleListener(
            sessionFactory = { _ -> session },
            storeConnector = { _, _ -> store as Store }
        )

        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            listener.listen(creds) { /* no-op */ }
        }
        // Let it actually enter idle()
        kotlinx.coroutines.delay(200)
        job.cancel()
        job.join()

        verify { folder.close(false) }
        verify { store.close() }
    }
```

(Add `import kotlinx.coroutines.launch` and `import kotlinx.coroutines.GlobalScope` etc. as needed. The test uses GlobalScope to detach from the test scheduler so the IO dispatcher actually runs.)

- [ ] **Step 2: Run the test, confirm fail (`TODO` triggers, or wrong behavior)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.ImapIdleListenerTest" --no-daemon
```

Expected: NotImplementedError from the `TODO`.

- [ ] **Step 3: Implement the IDLE loop**

Replace the `listen(...)` body in `ImapIdleListener.kt`:

```kotlin
    suspend fun listen(
        credentials: MailCredentials,
        onArrival: suspend (lastUidNext: Long) -> Unit
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val session = sessionFactory(credentials)
        val store = storeConnector(session, credentials)
        val imapStore = store as? IMAPStore
            ?: run { try { store.close() } catch (_: Throwable) {}; return@withContext }
        if (!imapStore.hasCapability("IDLE")) {
            try { store.close() } catch (_: Throwable) {}
            return@withContext
        }
        val folder = imapStore.getFolder("INBOX") as org.eclipse.angus.mail.imap.IMAPFolder
        try {
            folder.open(jakarta.mail.Folder.READ_ONLY)
            // EXISTS push handler — record arrival, but the actual fetch is the
            // caller's job after idle() returns.
            var sawArrival = false
            folder.addMessageCountListener(object : jakarta.mail.event.MessageCountAdapter() {
                override fun messagesAdded(e: jakarta.mail.event.MessageCountEvent?) { sawArrival = true }
            })
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                folder.idle()
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                if (sawArrival) {
                    sawArrival = false
                    val uidNext = folder.uidNext
                    onArrival(uidNext)
                }
            }
        } finally {
            try { if (folder.isOpen) folder.close(false) } catch (_: Throwable) {}
            try { if (store.isConnected) store.close() } catch (_: Throwable) {}
        }
    }
```

- [ ] **Step 4: Run all listener tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.ImapIdleListenerTest" --no-daemon
```

Expected: both tests pass.

- [ ] **Step 5: Verify file size**

```bash
wc -l app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt
```

Expected: < 120.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt \
        app/src/test/java/com/cocode/claudeemailapp/mail/ImapIdleListenerTest.kt
git commit -m "feat(mail): IDLE loop on Dispatchers.IO with close-to-break cancellation"
```

---

## Task 6: Notification channel registration

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/NotificationChannels.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/NotificationChannelsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/cocode/claudeemailapp/app/NotificationChannelsTest.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationChannelsTest {

    @Test
    fun `registers replies channel with default importance`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.register(ctx)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(NotificationChannels.REPLIES_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }
}
```

- [ ] **Step 2: Run the test, confirm fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.NotificationChannelsTest" --no-daemon
```

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/cocode/claudeemailapp/app/NotificationChannels.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * One channel for inbox-arrival notifications. IMPORTANCE_DEFAULT = sound + heads-up.
 * Users can tweak per-channel settings in Android system settings.
 */
object NotificationChannels {
    const val REPLIES_ID = "replies"
    private const val REPLIES_NAME = "Replies"
    private const val REPLIES_DESC = "Notifications when an agent replies to one of your commands."

    fun register(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(REPLIES_ID) != null) return
        val channel = NotificationChannel(REPLIES_ID, REPLIES_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = REPLIES_DESC
        }
        nm.createNotificationChannel(channel)
    }
}
```

- [ ] **Step 4: Verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.NotificationChannelsTest" --no-daemon
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/NotificationChannels.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/NotificationChannelsTest.kt
git commit -m "feat(app): register 'replies' notification channel"
```

---

## Task 7: `InboxNotifier` — kind-rule (fail-open) + suppression

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/InboxNotifierTest.kt`

**Responsibilities (this task):**
1. Decide whether to post for a given `FetchedMessage`:
   - skip if `notificationsEnabled` is false
   - skip if `envelope?.kind == Kinds.ACK`
   - **post otherwise** (including null envelope, unknown kind — fail-open)
   - skip if foreground (`ProcessLifecycleOwner.get().lifecycle.currentState >= STARTED`)
2. Defer the actual `notify()` call to Task 8.

We split the file: `InboxNotifier` (logic) is testable here; the actual `NotificationManagerCompat` calls go in Task 8.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/cocode/claudeemailapp/app/InboxNotifierTest.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.Kinds
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxNotifierDecisionTest {

    private fun msg(kind: String?): FetchedMessage = FetchedMessage(
        messageId = "<m@x>", from = "claude@example.com", fromName = null,
        to = listOf("u@example.com"), subject = "s", body = "b",
        sentAt = null, receivedAt = null, inReplyTo = null, references = emptyList(),
        isSeen = false, contentType = "application/json",
        envelope = if (kind == null) null else Envelope(kind = kind)
    )

    private fun decider(
        enabled: Boolean = true,
        foreground: Boolean = false
    ): InboxNotifier.Decider {
        val prefs = mockk<InboxNotificationPrefs> {
            every { notificationsEnabled } returns kotlinx.coroutines.flow.MutableStateFlow(enabled)
        }
        return InboxNotifier.Decider(prefs, isForeground = { foreground })
    }

    @Test
    fun `skip when toggle off`() {
        assertEquals(InboxNotifier.Action.Skip,
            decider(enabled = false).decide(msg(Kinds.RESULT), conversationKey = "c"))
    }

    @Test
    fun `skip ACK kind`() {
        assertEquals(InboxNotifier.Action.Skip,
            decider().decide(msg(Kinds.ACK), conversationKey = "c"))
    }

    @Test
    fun `post on PROGRESS QUESTION RESULT ERROR`() {
        for (k in listOf(Kinds.PROGRESS, Kinds.QUESTION, Kinds.RESULT, Kinds.ERROR)) {
            val a = decider().decide(msg(k), conversationKey = "c")
            assertEquals("kind=$k", InboxNotifier.Action.Post::class, a::class)
        }
    }

    @Test
    fun `fail open on null envelope`() {
        val a = decider().decide(msg(kind = null), conversationKey = "c")
        assertEquals(InboxNotifier.Action.Post::class, a::class)
    }

    @Test
    fun `fail open on unknown kind string`() {
        val a = decider().decide(msg(kind = "future_new_kind"), conversationKey = "c")
        assertEquals(InboxNotifier.Action.Post::class, a::class)
    }

    @Test
    fun `suppress when foreground`() {
        assertEquals(InboxNotifier.Action.Skip,
            decider(foreground = true).decide(msg(Kinds.RESULT), conversationKey = "c"))
    }
}
```

- [ ] **Step 2: Run the test, confirm fail**

- [ ] **Step 3: Implement decider scaffold**

Create `app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Kinds

/**
 * Decides whether to post a system notification for an inbound [FetchedMessage] and
 * (in Task 8) issues the actual NotificationCompat post.
 *
 * Kind-rule: skip ACK, post for everything else (including null/unknown — fail-open
 * so a future schema addition isn't silently swallowed).
 */
class InboxNotifier {

    sealed class Action {
        object Skip : Action()
        data class Post(val message: FetchedMessage, val conversationKey: String) : Action()
    }

    /** Pure decision logic — no Android dependencies, easily testable. */
    class Decider(
        private val prefs: InboxNotificationPrefs,
        private val isForeground: () -> Boolean
    ) {
        fun decide(message: FetchedMessage, conversationKey: String): Action {
            if (!prefs.notificationsEnabled.value) return Action.Skip
            if (message.envelope?.kind == Kinds.ACK) return Action.Skip
            if (isForeground()) return Action.Skip
            return Action.Post(message, conversationKey)
        }
    }
}
```

- [ ] **Step 4: Verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.InboxNotifierDecisionTest" --no-daemon
```

Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/InboxNotifierTest.kt
git commit -m "feat(app): InboxNotifier.Decider — kind-rule (skip ACK, fail-open) + foreground suppression"
```

---

## Task 8: `InboxNotifier.post()` — actual notification + PROGRESS rate-limit

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt`
- Modify: `app/src/test/java/com/cocode/claudeemailapp/app/InboxNotifierTest.kt`

- [ ] **Step 1: Add the rate-limit + post tests**

Append to `InboxNotifierTest.kt`:

```kotlin
class InboxNotifierPostTest {

    private fun msg(kind: String): com.cocode.claudeemailapp.mail.FetchedMessage =
        com.cocode.claudeemailapp.mail.FetchedMessage(
            messageId = "<m@x>", from = "claude@example.com", fromName = null,
            to = listOf("u@example.com"),
            subject = "Task #42 done", body = "...",
            sentAt = null, receivedAt = null, inReplyTo = null, references = emptyList(),
            isSeen = false, contentType = "application/json",
            envelope = com.cocode.claudeemailapp.protocol.Envelope(kind = kind)
        )

    @Test
    fun `first PROGRESS for a conversation alerts loudly second is silent`() {
        val ctx = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        ctx.deleteSharedPreferences(com.cocode.claudeemailapp.data.InboxNotificationPrefs.PREFS_NAME)
        val prefs = com.cocode.claudeemailapp.data.InboxNotificationPrefs(ctx)

        val first = InboxNotifier.shouldAlert(
            kind = com.cocode.claudeemailapp.protocol.Kinds.PROGRESS,
            conversationKey = "conv-a",
            prefs = prefs
        )
        val second = InboxNotifier.shouldAlert(
            kind = com.cocode.claudeemailapp.protocol.Kinds.PROGRESS,
            conversationKey = "conv-a",
            prefs = prefs
        )
        org.junit.Assert.assertTrue("first PROGRESS alerts", first)
        org.junit.Assert.assertFalse("second PROGRESS silent", second)
    }

    @Test
    fun `non-PROGRESS always alerts`() {
        val ctx = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        ctx.deleteSharedPreferences(com.cocode.claudeemailapp.data.InboxNotificationPrefs.PREFS_NAME)
        val prefs = com.cocode.claudeemailapp.data.InboxNotificationPrefs(ctx)

        for (k in listOf(
            com.cocode.claudeemailapp.protocol.Kinds.RESULT,
            com.cocode.claudeemailapp.protocol.Kinds.QUESTION,
            com.cocode.claudeemailapp.protocol.Kinds.ERROR,
            "unknown"
        )) {
            org.junit.Assert.assertTrue("kind=$k", InboxNotifier.shouldAlert(k, "c-$k", prefs))
            org.junit.Assert.assertTrue("kind=$k twice", InboxNotifier.shouldAlert(k, "c-$k", prefs))
        }
    }
}
```

(Add `@RunWith(org.robolectric.RobolectricTestRunner::class)` to the new class.)

- [ ] **Step 2: Run the new tests, confirm fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.InboxNotifierPostTest" --no-daemon
```

- [ ] **Step 3: Add `shouldAlert` + `post` to `InboxNotifier`**

Replace `InboxNotifier.kt` with:

```kotlin
package com.cocode.claudeemailapp.app

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

    private val decider = Decider(prefs, isForeground)

    fun handle(message: FetchedMessage, conversationKey: String) {
        when (val a = decider.decide(message, conversationKey)) {
            Action.Skip -> return
            is Action.Post -> postInternal(a.message, a.conversationKey)
        }
    }

    private fun postInternal(message: FetchedMessage, conversationKey: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return  // OS-level off; nothing to do
        val id = prefs.notificationIdFor(conversationKey)
        val kind = message.envelope?.kind ?: ""
        val alert = shouldAlert(kind, conversationKey, prefs)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
        }
        val pi = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = message.fromName?.takeIf { it.isNotBlank() } ?: message.from
        val notification = NotificationCompat.Builder(context, NotificationChannels.REPLIES_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.subject))
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try { nm.notify(id, notification) } catch (_: SecurityException) { /* POST_NOTIFICATIONS denied */ }
    }

    sealed class Action {
        object Skip : Action()
        data class Post(val message: FetchedMessage, val conversationKey: String) : Action()
    }

    class Decider(
        private val prefs: InboxNotificationPrefs,
        private val isForeground: () -> Boolean
    ) {
        fun decide(message: FetchedMessage, conversationKey: String): Action {
            if (!prefs.notificationsEnabled.value) return Action.Skip
            if (message.envelope?.kind == Kinds.ACK) return Action.Skip
            if (isForeground()) return Action.Skip
            return Action.Post(message, conversationKey)
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_KEY = "conversation_key"

        /**
         * PROGRESS alerts loudly only on the FIRST occurrence per conversation; subsequent
         * PROGRESS go in silently (setOnlyAlertOnce=true). All other kinds always alert.
         */
        fun shouldAlert(kind: String, conversationKey: String, prefs: InboxNotificationPrefs): Boolean {
            if (kind != Kinds.PROGRESS) return true
            return prefs.markFirstProgressSeen(conversationKey)
        }
    }
}
```

- [ ] **Step 4: Run all InboxNotifier tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.InboxNotifier*" --no-daemon
```

Expected: 8 tests pass.

- [ ] **Step 5: Verify file size**

```bash
wc -l app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt
```

Expected: < 110.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/InboxNotifier.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/InboxNotifierTest.kt
git commit -m "feat(app): InboxNotifier.post — deep-link, ID allocator, PROGRESS rate-limit"
```

---

## Task 9: `ClaudeEmailApplication` subclass + manifest registration

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/ClaudeEmailApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Note:** No unit test — the `Application.onCreate` path is exercised by Robolectric in any test that uses `ApplicationProvider`. We assert behavior indirectly in Task 10.

- [ ] **Step 1: Create the Application class**

Create `app/src/main/java/com/cocode/claudeemailapp/app/ClaudeEmailApplication.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Process-lifetime owner of:
 *  - the "replies" notification channel (registered once on cold start),
 *  - the [InboxWatcher] (started when credentials + toggle present; reactive thereafter).
 *
 * The watcher is intentionally process-scoped, not service-scoped: when the user
 * swipes the app from recents, the process dies, the watcher's coroutines are
 * cancelled, and the IMAP socket closes — no further notifications. This is the
 * explicit user-chosen behavior ("when the app is closed I am done").
 */
class ClaudeEmailApplication : Application() {

    private lateinit var watcher: InboxWatcher

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.register(this)
        watcher = InboxWatcher.create(this, ProcessLifecycleOwner.get())
        watcher.start()
    }

    override fun onTerminate() {
        if (::watcher.isInitialized) watcher.stop()
        super.onTerminate()
    }

    /**
     * Some OEMs (Samsung, Xiaomi) keep the process alive when the user swipes the
     * app from recents. Honor the user's "closed = done" intent on those devices
     * by explicitly stopping the watcher when the task is removed.
     */
    fun onTaskRemoved() {
        if (::watcher.isInitialized) watcher.stop()
    }

    companion object {
        // Hook for activities to call (Activity.onTaskRemoved isn't a thing; we wire
        // this from MainActivity.finish()/onDestroy() in Task 12.)
    }
}
```

- [ ] **Step 2: Stub `InboxWatcher` so the app compiles**

Create a minimal `app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt` (Task 10 fleshes it out):

```kotlin
package com.cocode.claudeemailapp.app

import android.content.Context
import androidx.lifecycle.LifecycleOwner

class InboxWatcher private constructor() {
    fun start() {}
    fun stop() {}

    companion object {
        fun create(context: Context, lifecycleOwner: LifecycleOwner): InboxWatcher = InboxWatcher()
    }
}
```

- [ ] **Step 3: Wire the Application class in the manifest**

In `app/src/main/AndroidManifest.xml`, change line 8 from:

```xml
    <application
```

to:

```xml
    <application
        android:name=".app.ClaudeEmailApplication"
```

(Add the `android:name` attribute; keep all the other attributes.)

- [ ] **Step 4: Compile**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the existing test suite (catches Application-class regressions)**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: ALL PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/ClaudeEmailApplication.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(app): ClaudeEmailApplication subclass + InboxWatcher stub + manifest wiring"
```

---

## Task 10: `InboxWatcher` — reactive on credentials, prefs, network

**Files:**
- Create: `app/src/main/java/com/cocode/claudeemailapp/data/CredentialsBus.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/data/CredentialsStore.kt` (notify bus on `save()`/`clear()`)
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/InboxWatcherTest.kt`

- [ ] **Step 0: Add `CredentialsBus` so the watcher hears about save/clear**

The existing `CredentialsStore` is a synchronous read/write store; nothing emits when `save()`/`clear()` is called. Add a process-singleton hot flow that the store updates on every mutation and that both `AppViewModel` and `InboxWatcher` subscribe to.

Create `app/src/main/java/com/cocode/claudeemailapp/data/CredentialsBus.kt`:

```kotlin
package com.cocode.claudeemailapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide hot flow of the current MailCredentials (or null if not configured).
 * CredentialsStore.save() / clear() must call into [emit] so subscribers (the inbox
 * watcher, the AppViewModel) see the change without a manual reload.
 */
object CredentialsBus {
    private val _state = MutableStateFlow<MailCredentials?>(null)
    val state: StateFlow<MailCredentials?> = _state.asStateFlow()

    /** Called once at process startup by `ClaudeEmailApplication.onCreate`. */
    fun seed(initial: MailCredentials?) { _state.value = initial }

    fun emit(credentials: MailCredentials?) { _state.value = credentials }

    /** Convenience for callers that want to bind a watcher to a freshly-loaded store. */
    fun flow(store: CredentialsStore): StateFlow<MailCredentials?> {
        if (_state.value == null) _state.value = store.load()
        return state
    }
}
```

In `CredentialsStore.kt`, modify `save()` and `clear()` to publish:

```kotlin
    override fun save(credentials: MailCredentials) {
        prefs.edit() /* ...existing puts... */ .apply()
        CredentialsBus.emit(credentials)
    }

    override fun clear() {
        prefs.edit().clear().apply()
        CredentialsBus.emit(null)
    }
```

(Add `import com.cocode.claudeemailapp.data.CredentialsBus` if needed — same package, so no import needed.)

In `ClaudeEmailApplication.onCreate` (Task 9 file), seed before the watcher starts:

```kotlin
        CredentialsBus.seed(CredentialsStore(this).load())
        watcher = InboxWatcher.create(this, ProcessLifecycleOwner.get())
        watcher.start()
```

- [ ] **Step 0b: Test the bus**

Add `app/src/test/java/com/cocode/claudeemailapp/data/CredentialsBusTest.kt`:

```kotlin
package com.cocode.claudeemailapp.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialsBusTest {
    private val sample = MailCredentials(
        displayName = "T", emailAddress = "u@x", password = "p",
        imapHost = "i", imapPort = 993, smtpHost = "s", smtpPort = 465,
        smtpUseStartTls = false, serviceAddress = "c@x", sharedSecret = "s"
    )

    @Test
    fun `seed and emit propagate`() = runTest {
        CredentialsBus.emit(null)  // reset
        CredentialsBus.seed(sample)
        assertEquals(sample, CredentialsBus.state.first())
        CredentialsBus.emit(null)
        assertNull(CredentialsBus.state.first())
    }
}
```

Run + verify pass before continuing.

- [ ] **Step 1: Write the failing reactivity test**

Create `app/src/test/java/com/cocode/claudeemailapp/app/InboxWatcherTest.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.data.MailCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxWatcherTest {

    private val creds = MailCredentials(
        displayName = "T", emailAddress = "u@example.com", password = "p",
        imapHost = "imap.example.com", imapPort = 993,
        smtpHost = "s", smtpPort = 465, smtpUseStartTls = false,
        serviceAddress = "claude@example.com", sharedSecret = "s"
    )

    @Test
    fun `starts listener when credentials present and toggle on`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefsFlow = MutableStateFlow(true)
        val prefs = mockk<InboxNotificationPrefs>(relaxed = true) {
            io.mockk.every { notificationsEnabled } returns prefsFlow
        }
        val credsFlow = MutableStateFlow<MailCredentials?>(creds)
        val listener = mockk<com.cocode.claudeemailapp.mail.ImapIdleListener>(relaxed = true) {
            coEvery { listen(any(), any()) } coAnswers { /* hang forever */ kotlinx.coroutines.awaitCancellation() }
        }

        val watcher = InboxWatcher.forTest(
            credentials = credsFlow,
            prefs = prefs,
            listener = listener,
            dispatcher = dispatcher
        )
        watcher.start()
        advanceUntilIdle()

        coVerify(timeout = 1_000) { listener.listen(eq(creds), any()) }
        watcher.stop()
    }

    @Test
    fun `cancels listener when toggle goes off`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefsFlow = MutableStateFlow(true)
        val prefs = mockk<InboxNotificationPrefs>(relaxed = true) {
            io.mockk.every { notificationsEnabled } returns prefsFlow
        }
        val credsFlow = MutableStateFlow<MailCredentials?>(creds)
        val listener = spyk<com.cocode.claudeemailapp.mail.ImapIdleListener>()
        coEvery { listener.listen(any(), any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }

        val watcher = InboxWatcher.forTest(credsFlow, prefs, listener, dispatcher)
        watcher.start()
        advanceUntilIdle()
        prefsFlow.value = false
        advanceUntilIdle()

        // After toggle off, the watcher's job should have been cancelled.
        org.junit.Assert.assertFalse(watcher.isActive())
        watcher.stop()
    }
}
```

- [ ] **Step 2: Run, confirm fail**

- [ ] **Step 3: Implement `InboxWatcher`**

Replace the stub `InboxWatcher.kt` with:

```kotlin
package com.cocode.claudeemailapp.app

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.ImapIdleListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Watches credentials + prefs + (later) network availability and runs an
 * [ImapIdleListener] whenever ALL of the conditions hold:
 *   - credentials present,
 *   - notifications toggle ON,
 *   - (network reachable — wired in Task 11).
 *
 * Cancels and restarts the listener when any of those flip.
 */
class InboxWatcher private constructor(
    private val credentials: Flow<MailCredentials?>,
    private val prefs: InboxNotificationPrefs,
    private val listener: ImapIdleListener,
    dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var listenJob: Job? = null

    fun start() {
        scope.launch {
            combine(credentials, prefs.notificationsEnabled) { c, enabled -> c.takeIf { enabled } }
                .distinctUntilChanged()
                .collect { effective ->
                    listenJob?.cancel()
                    listenJob = null
                    if (effective != null) {
                        listenJob = launch(dispatcher = Dispatchers.IO + CoroutineScope(SupervisorJob()).coroutineContext.minusKey(Job)) {
                            // Backoff loop: any throw / clean return → wait then retry.
                            var backoffMs = 5_000L
                            while (isActive) {
                                runCatching {
                                    listener.listen(effective) { /* arrival → Task 13 wires fetch+notify */ }
                                }
                                if (!isActive) break
                                delay(backoffMs)
                                backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
                            }
                        }
                    }
                }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        scope.cancel()
    }

    fun isActive(): Boolean = listenJob?.isActive == true

    companion object {
        fun create(context: Context, lifecycleOwner: LifecycleOwner): InboxWatcher {
            val store = CredentialsStore(context)
            val prefs = InboxNotificationPrefs(context)
            val listener = ImapIdleListener()
            return InboxWatcher(
                credentials = CredentialsBus.flow(store),  // hot StateFlow; updates on save (see CredentialsBus)
                prefs = prefs,
                listener = listener,
                dispatcher = Dispatchers.Default
            )
        }

        // Test-only entry point.
        internal fun forTest(
            credentials: Flow<MailCredentials?>,
            prefs: InboxNotificationPrefs,
            listener: ImapIdleListener,
            dispatcher: CoroutineDispatcher
        ): InboxWatcher = InboxWatcher(credentials, prefs, listener, dispatcher)
    }
}
```

> **Engineer note:** the `Dispatchers.IO + ...minusKey(Job)` ceremony is to make sure the inner job inherits the supervisor-scope correctly. If you find a cleaner formulation that the tests still pass with, prefer it. The intent: each restart spawns a fresh child job whose cancellation doesn't tear down the parent collector.

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.InboxWatcherTest" --no-daemon
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/InboxWatcherTest.kt
git commit -m "feat(app): InboxWatcher reactive on credentials + notifications toggle (with backoff)"
```

---

## Task 11: Network reactivity in `InboxWatcher`

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt`
- Create: `app/src/main/java/com/cocode/claudeemailapp/app/NetworkAvailability.kt`
- Create: `app/src/test/java/com/cocode/claudeemailapp/app/NetworkAvailabilityTest.kt`

- [ ] **Step 1: Write the test**

Create `app/src/test/java/com/cocode/claudeemailapp/app/NetworkAvailabilityTest.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkAvailabilityTest {
    @Test
    fun `flow emits initial value`() = kotlinx.coroutines.test.runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val flow = NetworkAvailability.flow(ctx)
        // In Robolectric the default is "no networks", but the flow must emit at least once.
        val first = kotlinx.coroutines.flow.first(flow)
        assertTrue("flow yields a Boolean", first is Boolean)
    }
}
```

- [ ] **Step 2: Run, confirm fail**

- [ ] **Step 3: Implement `NetworkAvailability`**

Create `app/src/main/java/com/cocode/claudeemailapp/app/NetworkAvailability.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Cold flow of "is the device currently online?" — emits on every transition. */
object NetworkAvailability {

    fun flow(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(currentlyOnline(cm)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(currentlyOnline(cm))
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun currentlyOnline(cm: ConnectivityManager): Boolean {
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

- [ ] **Step 4: Wire `NetworkAvailability` into `InboxWatcher`**

In `InboxWatcher.kt`:

Add a `network: Flow<Boolean>` constructor parameter and include it in the `combine(...)` so the listener restarts only when ALL three conditions (credentials, toggle, network) hold:

Modify the constructor signature:

```kotlin
class InboxWatcher private constructor(
    private val credentials: Flow<MailCredentials?>,
    private val prefs: InboxNotificationPrefs,
    private val network: Flow<Boolean>,
    private val listener: ImapIdleListener,
    dispatcher: CoroutineDispatcher
) {
```

Modify the `combine` in `start()`:

```kotlin
            combine(credentials, prefs.notificationsEnabled, network) { c, enabled, online ->
                c.takeIf { enabled && online }
            }
```

Update `create()`:

```kotlin
        fun create(context: Context, lifecycleOwner: LifecycleOwner): InboxWatcher {
            val store = CredentialsStore(context)
            val prefs = InboxNotificationPrefs(context)
            val listener = ImapIdleListener()
            return InboxWatcher(
                credentials = MutableStateFlow(store.load()),
                prefs = prefs,
                network = NetworkAvailability.flow(context),
                listener = listener,
                dispatcher = Dispatchers.Default
            )
        }
```

Update `forTest()` to also accept a `network` flow (default to `MutableStateFlow(true)` for the no-network-care path):

```kotlin
        internal fun forTest(
            credentials: Flow<MailCredentials?>,
            prefs: InboxNotificationPrefs,
            listener: ImapIdleListener,
            dispatcher: CoroutineDispatcher,
            network: Flow<Boolean> = MutableStateFlow(true)
        ): InboxWatcher = InboxWatcher(credentials, prefs, network, listener, dispatcher)
```

- [ ] **Step 5: Add a network-flap test in `InboxWatcherTest`**

Append:

```kotlin
    @Test
    fun `cancels listener when network goes offline restart when back online`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefs = mockk<InboxNotificationPrefs>(relaxed = true) {
            io.mockk.every { notificationsEnabled } returns MutableStateFlow(true)
        }
        val credsFlow = MutableStateFlow<MailCredentials?>(creds)
        val net = MutableStateFlow(true)
        val listener = mockk<com.cocode.claudeemailapp.mail.ImapIdleListener>(relaxed = true)
        coEvery { listener.listen(any(), any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }

        val watcher = InboxWatcher.forTest(credsFlow, prefs, listener, dispatcher, net)
        watcher.start()
        advanceUntilIdle()
        org.junit.Assert.assertTrue("started", watcher.isActive())

        net.value = false
        advanceUntilIdle()
        org.junit.Assert.assertFalse("offline → cancelled", watcher.isActive())

        net.value = true
        advanceUntilIdle()
        org.junit.Assert.assertTrue("online → restarted", watcher.isActive())

        watcher.stop()
    }
```

- [ ] **Step 6: Run all watcher tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.app.InboxWatcherTest" --tests "com.cocode.claudeemailapp.app.NetworkAvailabilityTest" --no-daemon
```

Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/NetworkAvailability.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/NetworkAvailabilityTest.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/InboxWatcherTest.kt
git commit -m "feat(app): InboxWatcher reactive on network availability"
```

---

## Task 12: Wire IMAP arrival → fetch → notify in the watcher

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt`

This task replaces the `/* arrival → Task 13 wires fetch+notify */` placeholder with the actual flow:

1. On arrival (`onArrival(uidNext)`): use `ImapMailFetcher` to fetch the most recent N (e.g., 50) messages, filter to `UID > waterMark`.
2. For each new message: derive the conversation key via `ConversationGrouper.groupKeyOf(message)` (existing helper — verify the exact name in `ConversationGrouper.kt`), call `InboxNotifier.handle(...)`.
3. Advance the water mark to the highest UID processed only AFTER the loop completes without throwing.

> **Engineer note:** `FetchedMessage` does not currently carry the IMAP UID. You must extend it (and `ImapMailFetcher.toFetched()`) to include `val uid: Long`. This is a small additive change. UID can be obtained via `(folder as IMAPFolder).getUID(message)` during `toFetched()`.

- [ ] **Step 1: Add `uid` to `FetchedMessage`**

In `app/src/main/java/com/cocode/claudeemailapp/mail/MailModels.kt`, add `val uid: Long = -1L,` to the `FetchedMessage` data class (default -1 keeps existing tests / call sites compiling).

- [ ] **Step 2: Populate it in `ImapMailFetcher.toFetched()`**

In `ImapMailFetcher.kt`, change `toFetched()` to take the folder and read the UID:

```kotlin
        internal fun Message.toFetched(folder: org.eclipse.angus.mail.imap.IMAPFolder): FetchedMessage {
            // ... existing body ...
            return FetchedMessage(
                uid = folder.getUID(this),
                messageId = messageId,
                // ... existing fields ...
            )
        }
```

And update the call site in `fetchRecent`:

```kotlin
                messages.reversed().map { it.toFetched(inbox as org.eclipse.angus.mail.imap.IMAPFolder) }
```

- [ ] **Step 3: Run existing mail tests, fix any breakage**

```bash
./gradlew :app:testDebugUnitTest --tests "com.cocode.claudeemailapp.mail.*" --no-daemon
```

If `ImapMailFetcherTest` constructs `FetchedMessage` directly, the default `uid = -1L` keeps it green. If anything else calls `toFetched()` directly, update those call sites too.

- [ ] **Step 4: Wire the arrival callback in `InboxWatcher`**

In `InboxWatcher.kt`, change the placeholder block. The watcher now needs additional collaborators (`fetcher`, `notifier`, `prefs`-as-water-mark-store, `grouper`-or-key-fn). Add them as constructor parameters with sensible production defaults, and update `create()` accordingly. Replace the empty `onArrival` lambda with a call to a new `processArrival(...)` method. Keep `processArrival` ≤ 30 LOC; if it grows, extract a `private val pipeline = InboxArrivalPipeline(...)` helper.

Concretely, in `InboxWatcher.start()`:

```kotlin
                            runCatching {
                                listener.listen(effective) { uidNext ->
                                    processArrival(effective, uidNext)
                                }
                            }
```

And add at class scope:

```kotlin
    private suspend fun processArrival(creds: MailCredentials, uidNext: Long) {
        val recent = runCatching { fetcher.fetchRecent(creds, count = 50) }.getOrNull() ?: return
        val uidValidity = uidValidityProvider() // see note below
        val waterMark = prefs.waterMark(uidValidity) ?: (uidNext - 1).coerceAtLeast(0)
        val newOnes = recent.filter { it.uid > waterMark }
        if (newOnes.isEmpty()) {
            prefs.setWaterMark(uidValidity, uidNext - 1)
            return
        }
        // Group adjacent messages into conversations using the existing grouper.
        for (msg in newOnes) {
            val convKey = groupKeyFor(msg)
            notifier.handle(msg, convKey)
        }
        prefs.setWaterMark(uidValidity, newOnes.maxOf { it.uid })
    }
```

> **Engineer note (UIDVALIDITY):** `IMAPFolder` exposes `uidValidity` only when the folder is open. The cheapest path: extend `ImapIdleListener.onArrival` to also pass `uidValidity` (it's already known to the listener since the folder is open). Update the lambda signature in Task 5 from `(Long) -> Unit` to `(uidNext: Long, uidValidity: Long) -> Unit` and adjust the test in Task 5. Then drop `uidValidityProvider` here in favor of the passed-in value.

> **Engineer note (groupKeyFor):** `ConversationGrouper.keyFor` is private (it's a local function inside `group()`). Replicate the same logic inline — it's two lines: `val key = msg.references.firstOrNull()?.takeIf { it.isNotBlank() } ?: msg.inReplyTo?.takeIf { it.isNotBlank() } ?: msg.messageId`. (This mirrors the grouper's "references chain or fall back to messageId" behavior. If the grouper changes its rule later, search for `references.firstOrNull` and update both call sites — leave a comment.)

- [ ] **Step 5: Add an integration test for `processArrival`**

(Optional but recommended — exercises the full path with mocked fetcher + spy notifier.)

- [ ] **Step 6: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/mail/MailModels.kt \
        app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt \
        app/src/main/java/com/cocode/claudeemailapp/mail/ImapIdleListener.kt \
        app/src/test/java/com/cocode/claudeemailapp/mail/ImapIdleListenerTest.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/InboxWatcher.kt
git commit -m "feat(app): wire IMAP arrival → fetch → InboxNotifier with UID water-mark advance"
```

---

## Task 13: Settings toggle UI

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt` (expose toggle)
- Modify: `app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt` (add toggle test)

- [ ] **Step 1: Read current `SettingsScreen.kt` to find where to add the toggle**

```bash
grep -n "Switch\|Sync\|interval" app/src/main/java/com/cocode/claudeemailapp/app/SettingsScreen.kt | head -20
```

Locate the existing `syncIntervalMs` setting block; the new toggle goes immediately above or below it.

- [ ] **Step 2: Add `notificationsEnabled` flow + `setNotificationsEnabled(Boolean)` to `AppViewModel`**

In `AppViewModel.kt`, add a backing `InboxNotificationPrefs` instance (constructor-injected via the existing factory pattern), expose `val notificationsEnabled: StateFlow<Boolean> = prefs.notificationsEnabled`, and add `fun setNotificationsEnabled(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)`.

(Inspect the existing `AppViewModel` to determine the injection pattern — it likely uses a `Factory` that takes `Application`. Add `InboxNotificationPrefs(application)` to that factory.)

- [ ] **Step 3: Add a test in `AppViewModelTest`**

```kotlin
@Test
fun `notifications toggle persists`() = runTest {
    val vm = createViewModel()  // existing test helper
    vm.setNotificationsEnabled(false)
    assertFalse(vm.notificationsEnabled.value)
    vm.setNotificationsEnabled(true)
    assertTrue(vm.notificationsEnabled.value)
}
```

- [ ] **Step 4: Add the Switch to `SettingsScreen.kt`**

Add after the existing sync-interval row:

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

(Adjust import block as needed.)

- [ ] **Step 5: Run all tests**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/SettingsScreen.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt
git commit -m "feat(ui): Settings 'Notify on replies' toggle"
```

---

## Task 14: `POST_NOTIFICATIONS` permission ask flow

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/MainActivity.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/ComposeMessageScreen.kt` (or wherever Send is dispatched)

- [ ] **Step 1: Add the permission to the manifest**

In `AndroidManifest.xml` after the existing `ACCESS_NETWORK_STATE` line:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Hold a `RequestPermission` launcher in `MainActivity`**

In `MainActivity.kt`, add:

```kotlin
class MainActivity : ComponentActivity() {

    private var pendingPermissionResult: ((Boolean) -> Unit)? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionResult?.invoke(granted)
            pendingPermissionResult = null
        }

    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            callback(true)  // < Android 13: notifications work without runtime permission
            return
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        pendingPermissionResult = callback
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

- [ ] **Step 3: Expose a `LocalNotificationPermissionRequester` to Composables**

In `AppRoot.kt`, near the top of the `ClaudeEmailApp()` Composable:

```kotlin
val activity = LocalContext.current as? MainActivity
val requestPerm: (suspend () -> Boolean) = remember(activity) {
    {
        if (activity == null) false
        else suspendCancellableCoroutine { cont -> activity.requestNotificationPermission { cont.resume(it) } }
    }
}
val permRequester = remember { NotificationPermissionRequester(requestPerm) }
CompositionLocalProvider(LocalNotificationPermissionRequester provides permRequester) { /* existing tree */ }
```

Add a tiny holder file (or co-locate) `app/NotificationPermissionRequester.kt`:

```kotlin
package com.cocode.claudeemailapp.app

import androidx.compose.runtime.compositionLocalOf

class NotificationPermissionRequester(val request: suspend () -> Boolean)

val LocalNotificationPermissionRequester = compositionLocalOf<NotificationPermissionRequester?> { null }
```

- [ ] **Step 4: Trigger the ask on Send**

Find the Send handler (likely in `ComposeMessageScreen.kt` or its ViewModel). After the dispatch succeeds (or just before — choose the spot where the user has clearly committed), insert:

```kotlin
// First-Send permission ask — only if toggle is ON and we haven't asked yet.
if (notificationsEnabled && !hasAskedNotificationPermission) {
    coroutineScope.launch {
        LocalNotificationPermissionRequester.current?.request?.invoke()
        viewModel.markPermissionAsked()  // persists to InboxNotificationPrefs (add a flag)
    }
}
```

You'll need to add `hasAskedNotificationPermission` and `markPermissionAsked()` to `InboxNotificationPrefs` (boolean key `KEY_PERMISSION_ASKED`). Apply the same TDD cycle as Task 3.

- [ ] **Step 5: Run all tests**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

- [ ] **Step 6: Commit**

```bash
git add -p && git commit -m "feat(app): just-in-time POST_NOTIFICATIONS permission ask on first Send"
```

---

## Task 15: Deep-link routing in `MainActivity` + `AppRoot`

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/MainActivity.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt`

Tap on a notification → activity opens with `Intent.EXTRA_CONVERSATION_KEY` extra → `AppRoot` reads it and pushes the matching conversation.

- [ ] **Step 1: Override `onNewIntent` in `MainActivity`**

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // so subsequent reads see the latest
    }
```

- [ ] **Step 2: Forward the intent extra to `AppRoot`**

Add a parameter to `ClaudeEmailApp(initialConversationKey: String? = null)` in `AppRoot.kt` and pass it from `MainActivity.onCreate`:

```kotlin
            val initialKey = intent?.getStringExtra(InboxNotifier.EXTRA_CONVERSATION_KEY)
            ClaudeEmailApp(initialConversationKey = initialKey)
```

- [ ] **Step 3: In `AppRoot.kt`, react to the initial key**

```kotlin
LaunchedEffect(initialConversationKey) {
    if (initialConversationKey != null) {
        viewModel.openConversation(initialConversationKey)
    }
}
```

(`openConversation(key: String)` — if it doesn't already exist on `AppViewModel`, add it; navigate to `ConversationScreen` for that key.)

- [ ] **Step 4: Compile + run all tests**

- [ ] **Step 5: Commit**

```bash
git add -p && git commit -m "feat(app): deep-link tap on inbox notification opens matching ConversationScreen"
```

---

## Task 16: `onTaskRemoved` cleanup

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/MainActivity.kt` (or `Application` subclass)

Android delivers `onTaskRemoved` to a Service, not an Activity, but for the no-service design we use `ComponentActivity.onDestroy()` combined with `isFinishing` and `isTaskRoot` to detect a recents-swipe.

- [ ] **Step 1: Override `onDestroy` in `MainActivity`**

```kotlin
    override fun onDestroy() {
        if (isFinishing && isTaskRoot) {
            (application as? com.cocode.claudeemailapp.app.ClaudeEmailApplication)?.onTaskRemoved()
        }
        super.onDestroy()
    }
```

(`onTaskRemoved()` was already declared on `ClaudeEmailApplication` in Task 9.)

- [ ] **Step 2: Manual verification**

This is hard to unit-test. Add a manual checklist entry to `docs/superpowers/specs/2026-05-02-inbox-notifications-design.md` under "Manual" — actually the spec already has "Send command → swipe app from recents → wait for reply → confirm no notification."

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/MainActivity.kt
git commit -m "feat(app): stop InboxWatcher on recents-swipe so OEM-keep-alive devices honor 'closed = done'"
```

---

## Task 17: One-time inline hint for permission denial

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/ConversationScreen.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/data/InboxNotificationPrefs.kt` (add `permissionDeniedHintDismissed` flag)

- [ ] **Step 1: Add a flag to `InboxNotificationPrefs`**

Add:

```kotlin
    fun isPermissionDeniedHintDismissed(): Boolean = sp.getBoolean(KEY_HINT_DISMISSED, false)
    fun dismissPermissionDeniedHint() { sp.edit().putBoolean(KEY_HINT_DISMISSED, true).apply() }
```

(Update tests in `InboxNotificationPrefsTest` to cover roundtrip.)

- [ ] **Step 2: Render the hint conditionally in `ConversationScreen`**

```kotlin
val ctx = LocalContext.current
val nm = remember(ctx) { NotificationManagerCompat.from(ctx) }
val hintDismissed by remember { mutableStateOf(prefs.isPermissionDeniedHintDismissed()) }
if (notificationsEnabled && !nm.areNotificationsEnabled() && !hintDismissed) {
    InfoBanner(
        text = "Enable notifications in system settings to get alerted on replies.",
        actionText = "Dismiss",
        onAction = { prefs.dismissPermissionDeniedHint() }
    )
}
```

(Use the existing banner component if there is one; otherwise inline a small Card.)

- [ ] **Step 3: Run all tests + commit**

```bash
git add -p && git commit -m "feat(app): one-time hint when notifications are denied at OS level"
```

---

## Task 18: buildSmoke + manual device verification

**Files:** none

- [ ] **Step 1: Run buildSmoke**

```bash
./gradlew buildSmoke --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install the debug APK on the connected device**

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Manual checklist (each ✓ a separate verification)**

- [ ] Open app, ensure already-configured. Toggle is ON in Settings.
- [ ] Send a test command (e.g., "what year is it"). Background the app (home button).
- [ ] Wait for reply. Confirm a notification appears.
- [ ] Tap the notification. Confirm app opens to the matching conversation.
- [ ] Repeat: send another command, swipe the app from recents instead of backgrounding. Confirm NO notification arrives.
- [ ] In Settings, toggle OFF. Send a new command, background. Confirm NO notification.
- [ ] Toggle back ON. Disable notifications at OS level (long-press app icon → notification settings → off). Send a command. Confirm no notification + the inline hint appears in the conversation.
- [ ] Re-enable OS notifications. Confirm next command's reply notifies again.
- [ ] During an IDLE session, switch wifi off → back on. Check logcat for reconnect (no error spam).

- [ ] **Step 4: Open the PR**

```bash
git push -u origin feat/inbox-notifications
gh pr create --title "feat: inbox notifications via process-lifetime IMAP IDLE" --body "$(cat <<'EOF'
## Summary
Buzz the device when an agent reply arrives while the app is in recents. No foreground service, no FCM. Closes silently when the user swipes the app away.

Implements the spec at `docs/superpowers/specs/2026-05-02-inbox-notifications-design.md`.

## Highlights
- Skips ACK kind; fail-open on null/unknown kinds so future schema additions aren't silently swallowed
- Persistent conversationKey→Int notification ID allocator (avoids hashCode collisions)
- PROGRESS rate-limited via setOnlyAlertOnce after the first per conversation; QUESTION/RESULT/ERROR always re-buzz
- Reactive watcher: restarts on credential / toggle / network changes
- Deep-link routing on tap

## Test plan
- [x] `./gradlew buildSmoke --no-daemon` — green
- [x] Manual checklist (see Task 18 of the plan)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Spec coverage check

Spec section → Implementing tasks:
- "Skip ACK; fail-open" → Task 7, 8
- "Notification ID strategy" → Task 3, 8
- "Watcher lifecycle reactive" → Task 10, 11
- "PROGRESS rate-limiting" → Task 8
- "Process-lifetime only (no Service)" → Task 9
- "Notification grouping (one per conversation)" → Task 8 (single ID per conv)
- "Tap target → ConversationScreen" → Task 8 + 15
- "Channel/importance" → Task 6
- "POST_NOTIFICATIONS just-in-time" → Task 14
- "Settings toggle" → Task 13
- "Foreground suppression" → Task 7
- "IDLE timeout / reconnect / network change / capability fallback" → Task 4, 5, 10, 11
- "High-water-mark UID + UIDVALIDITY reset" → Task 3, 12
- "Sign-out / credentials change" → Task 10 (credentials Flow)
- "Process death / OEM keep-alive (onTaskRemoved)" → Task 9, 16
- "Doze acknowledged" → documented in spec; no code
- "Permission denied inline hint" → Task 17
- "Migration: none, additive" → no task

All spec items have at least one implementing task. ✓
