# Mark-Seen-on-View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user opens a `ConversationScreen`, mark every still-unread message in that conversation as `\Seen` on the IMAP server so the unread badge clears and stays cleared.

**Architecture:** App currently opens IMAP `INBOX` in `Folder.READ_ONLY` (`ImapMailFetcher.kt:38`) and only *reads* the `\Seen` flag — there is no write path. The local `unreadCount` is derived purely from server flags (`ConversationGrouper.kt:75`), so anything not seen by another client stays unread forever. Fix is App-side only: add `MailFetcher.markSeen(creds, messageIds)`, open INBOX `READ_WRITE` for the write, search by `MessageIDTerm`, batch `setFlags(SEEN, true)`. ViewModel does an optimistic local state patch first so the UI clears immediately, then fires the IMAP write in the background. Backend / envelope contract untouched — peer agent has been notified separately.

**Tech Stack:** Kotlin 2.2.10, Jakarta Mail (`jakarta.mail.search.MessageIDTerm`, `jakarta.mail.search.OrTerm`), Compose `LaunchedEffect`, mockk + JUnit4 + kotlinx-coroutines-test for tests.

---

## Pre-flight

- [ ] **Branch off `master`**

```bash
git checkout master && git pull
git checkout -b fix/mark-seen-on-view
```

- [ ] **Confirm baseline is green**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL.

---

### Task 1: Extend `MailFetcher` interface with `markSeen`

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/mail/MailFetcher.kt`
- Test: `app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt`

- [ ] **Step 1.1: Add the failing ViewModel-level test**

Append to `AppViewModelTest.kt` (inside the class, after the existing tests):

```kotlin
@Test
fun markConversationRead_callsFetcherWithUnreadMessageIds() = runTest {
    val fetcher = mockk<MailFetcher>(relaxed = true)
    coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
    coEvery { fetcher.markSeen(any(), any()) } returns Unit

    val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
    advanceUntilIdle()

    // Seed inbox with two unread + one already-seen message in the same thread.
    val thread = listOf(
        FetchedMessage(
            messageId = "<a@x>", from = "agent@x", fromName = null, to = listOf("me@ex.com"),
            subject = "Subject", body = "1", sentAt = Date(1), receivedAt = Date(1),
            inReplyTo = null, references = emptyList(), isSeen = false,
            contentType = "text/plain", envelope = null
        ),
        FetchedMessage(
            messageId = "<b@x>", from = "agent@x", fromName = null, to = listOf("me@ex.com"),
            subject = "Re: Subject", body = "2", sentAt = Date(2), receivedAt = Date(2),
            inReplyTo = "<a@x>", references = listOf("<a@x>"), isSeen = false,
            contentType = "text/plain", envelope = null
        ),
        FetchedMessage(
            messageId = "<c@x>", from = "agent@x", fromName = null, to = listOf("me@ex.com"),
            subject = "Re: Subject", body = "3", sentAt = Date(3), receivedAt = Date(3),
            inReplyTo = "<b@x>", references = listOf("<a@x>", "<b@x>"), isSeen = true,
            contentType = "text/plain", envelope = null
        )
    )
    coEvery { fetcher.fetchRecent(any(), any()) } returns thread
    vm.refreshInbox()
    advanceUntilIdle()

    val convId = vm.conversations.value.first().id
    vm.markConversationRead(convId)
    advanceUntilIdle()

    val captured = slot<List<String>>()
    coVerify { fetcher.markSeen(any(), capture(captured)) }
    assertEquals(setOf("<a@x>", "<b@x>"), captured.captured.toSet())
    assertEquals(0, vm.conversations.value.first().unreadCount)
}
```

- [ ] **Step 1.2: Run test, confirm it fails (compile error: unresolved `markSeen`)**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppViewModelTest.markConversationRead_callsFetcherWithUnreadMessageIds" --no-daemon`
Expected: COMPILE FAIL — `markSeen` not in `MailFetcher`, `markConversationRead` not on `AppViewModel`.

- [ ] **Step 1.3: Add `markSeen` to the interface**

Replace the contents of `MailFetcher.kt` with:

```kotlin
package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials

interface MailFetcher {
    suspend fun fetchRecent(credentials: MailCredentials, count: Int = 50): List<FetchedMessage>

    /**
     * Mark messages with the given RFC-5322 Message-IDs as `\Seen` on the IMAP server.
     * Unknown ids are silently skipped (the server may have purged or never had them).
     * Implementations must not throw on partial-match failures — read-state is best-effort.
     */
    suspend fun markSeen(credentials: MailCredentials, messageIds: List<String>)
}
```

- [ ] **Step 1.4: Add a temporary stub on `ImapMailFetcher` to keep compilation alive**

Add inside `class ImapMailFetcher(...) : MailFetcher { ... }`, alongside `fetchRecent`:

```kotlin
override suspend fun markSeen(credentials: MailCredentials, messageIds: List<String>) {
    TODO("Implemented in Task 2")
}
```

- [ ] **Step 1.5: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/mail/MailFetcher.kt \
        app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt \
        app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt
git commit -m "test(app): pin markConversationRead contract against MailFetcher.markSeen"
```

---

### Task 2: Implement `ImapMailFetcher.markSeen`

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt`
- Test: `app/src/test/java/com/cocode/claudeemailapp/mail/ImapMailFetcherTest.kt`

- [ ] **Step 2.1: Write the failing test for the success path**

Append to `ImapMailFetcherTest.kt`:

```kotlin
@Test
fun markSeen_opensReadWriteAndSetsSeenFlagOnMatchingMessages() = runTest {
    val match1 = mockk<Message>(relaxed = true)
    val match2 = mockk<Message>(relaxed = true)

    val inbox = mockk<Folder>(relaxed = true)
    every { inbox.isOpen } returns true
    every { inbox.search(any<jakarta.mail.search.SearchTerm>()) } returns arrayOf(match1, match2)

    val store = mockk<Store>(relaxed = true)
    every { store.getFolder("INBOX") } returns inbox
    every { store.isConnected } returns true

    val fetcher = ImapMailFetcher(
        sessionFactory = { Session.getInstance(Properties()) },
        storeConnector = { _, _ -> store }
    )
    fetcher.markSeen(creds(), listOf("<a@x>", "<b@x>"))

    verify { inbox.open(Folder.READ_WRITE) }
    verify {
        inbox.setFlags(arrayOf(match1, match2), match { it.contains(Flags.Flag.SEEN) }, true)
    }
    verify { inbox.close(false) }
    verify { store.close() }
}

@Test
fun markSeen_emptyIdsList_isNoOp() = runTest {
    val store = mockk<Store>(relaxed = true)
    val fetcher = ImapMailFetcher(
        sessionFactory = { Session.getInstance(Properties()) },
        storeConnector = { _, _ -> store }
    )
    fetcher.markSeen(creds(), emptyList())
    verify(exactly = 0) { store.getFolder(any<String>()) }
}

@Test
fun markSeen_swallowsConnectFailure() = runTest {
    val fetcher = ImapMailFetcher(
        sessionFactory = { Session.getInstance(Properties()) },
        storeConnector = { _, _ -> error("auth no") }
    )
    // Best-effort contract: must not throw.
    fetcher.markSeen(creds(), listOf("<a@x>"))
}
```

- [ ] **Step 2.2: Run, confirm fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ImapMailFetcherTest.markSeen_*" --no-daemon`
Expected: 3 failures — TODO from stub propagates as `NotImplementedError`.

- [ ] **Step 2.3: Replace the stub with the real implementation**

In `ImapMailFetcher.kt`, add this import block alongside the existing mail imports:

```kotlin
import jakarta.mail.search.MessageIDTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.SearchTerm
```

Replace the `markSeen` stub from Task 1 with:

```kotlin
override suspend fun markSeen(
    credentials: MailCredentials,
    messageIds: List<String>
) = withContext(Dispatchers.IO) {
    if (messageIds.isEmpty()) return@withContext
    val session = sessionFactory(imapProperties(credentials))
    val store = try {
        storeConnector(session, credentials)
    } catch (_: Throwable) {
        return@withContext
    }
    try {
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_WRITE)
        try {
            val term: SearchTerm = if (messageIds.size == 1) {
                MessageIDTerm(messageIds.single())
            } else {
                OrTerm(messageIds.map { MessageIDTerm(it) }.toTypedArray())
            }
            val matched = inbox.search(term)
            if (matched.isNotEmpty()) {
                inbox.setFlags(matched, Flags(Flags.Flag.SEEN), true)
            }
        } finally {
            if (inbox.isOpen) inbox.close(false)
        }
    } catch (_: Throwable) {
        // Best-effort: read-state is non-fatal. Next refresh will reflect server truth.
    } finally {
        try { if (store.isConnected) store.close() } catch (_: Throwable) {}
    }
}
```

- [ ] **Step 2.4: Run, confirm pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*ImapMailFetcherTest*" --no-daemon`
Expected: PASS, all existing tests still pass.

- [ ] **Step 2.5: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt \
        app/src/test/java/com/cocode/claudeemailapp/mail/ImapMailFetcherTest.kt
git commit -m "feat(mail): ImapMailFetcher.markSeen — batch \\Seen write via MessageIDTerm"
```

---

### Task 3: `AppViewModel.markConversationRead` with optimistic update

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt`

- [ ] **Step 3.1: Implement `markConversationRead`**

Add as a public method on `AppViewModel` (place near `setConversationArchived`, around line 193):

```kotlin
fun markConversationRead(conversationId: String) {
    val creds = _credentials.value ?: return
    val current = _inbox.value
    val convMessageIds = conversations.value
        .firstOrNull { it.id == conversationId }
        ?.messages
        ?.filter { !it.isSeen && it.messageId.isNotBlank() }
        ?.map { it.messageId }
        .orEmpty()
    if (convMessageIds.isEmpty()) return

    // 1) Optimistic local state — clear the unread badge immediately.
    val targets = convMessageIds.toSet()
    _inbox.value = current.copy(
        messages = current.messages.map { m ->
            if (m.messageId in targets && !m.isSeen) m.copy(isSeen = true) else m
        }
    )

    // 2) Fire the IMAP write in the background; failures are non-fatal.
    viewModelScope.launch {
        runCatching { mailFetcher.markSeen(creds, convMessageIds) }
    }
}
```

- [ ] **Step 3.2: Run the Task 1 ViewModel test, confirm pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppViewModelTest.markConversationRead_*" --no-daemon`
Expected: PASS — assertion on `unreadCount == 0` and on captured ids `{<a@x>, <b@x>}`.

- [ ] **Step 3.3: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt
git commit -m "feat(app): markConversationRead with optimistic unread clearing"
```

---

### Task 4: Wire UI — fire on conversation open

**Files:**
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/ConversationScreen.kt`
- Modify: `app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt`

- [ ] **Step 4.1: Add `onMarkRead` parameter to `ConversationScreen`**

In `ConversationScreen.kt`, add to the parameter list (default `{}` so test composables stay happy):

```kotlin
onMarkRead: () -> Unit = {},
```

Then, immediately after the `var reply by rememberSaveable...` line (~line 59), add:

```kotlin
LaunchedEffect(conversation.id) { onMarkRead() }
```

- [ ] **Step 4.2: Wire it in `AppRoot.kt`**

In the `Screen.Conversation` branch (~line 352), add inside the `ConversationScreen(...)` call:

```kotlin
onMarkRead = { viewModel.markConversationRead(conversation.id) },
```

- [ ] **Step 4.3: Run unit + lint suite**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.4: Commit**

```bash
git add app/src/main/java/com/cocode/claudeemailapp/app/ConversationScreen.kt \
        app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt
git commit -m "feat(app): mark conversation read on open"
```

---

### Task 5: Smoke + device verification

- [ ] **Step 5.1: Full smoke**

Run: `./gradlew buildSmoke --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.2: Run /simplify on the diff**

Per project hygiene rule: invoke `/simplify` on the changed files before final commit; fold any reductions into a `refactor:` follow-up commit if it produces real reductions, otherwise note "no simplifications" in the PR description.

- [ ] **Step 5.3: Manual verification on connected device**

Install the debug APK and verify the golden path:

```bash
./gradlew :app:installDebug --no-daemon
```

Then on device:
1. Send a fresh command from a peer / second client so a new unread message lands.
2. Confirm Home shows the conversation in **bold** with the "N unread" chip.
3. Tap the conversation → ConversationScreen opens.
4. Tap back → Home now shows the same conversation **non-bold**, no unread chip.
5. Pull-to-refresh on Home — conversation must stay non-bold (proves server-side `\Seen` was set, not just local).
6. Open the inbox in another IMAP client (Gmail web, Thunderbird) — those messages must show as read there too.

If step 5 fails (badge returns after refresh), the IMAP write didn't land — investigate `markSeen` (auth scope, server permissions, `MessageIDTerm` matching).

- [ ] **Step 5.4: Open PR**

```bash
git push -u origin fix/mark-seen-on-view
gh pr create --title "fix(app): mark conversation read on open (set \\Seen)" --body "$(cat <<'EOF'
## Summary
- Adds `MailFetcher.markSeen` (IMAP READ_WRITE + `MessageIDTerm` batch).
- ViewModel optimistically clears local unread state on conversation open, then writes `\Seen` to server.
- Fixes long-standing UX bug: messages stayed unread after viewing because the App opened INBOX READ_ONLY and never wrote the flag back.

## Test plan
- [x] Unit tests cover ViewModel, fetcher success/empty/connect-failure paths.
- [x] Manual device check: open conversation → unread chip clears → pull-to-refresh keeps it cleared → second IMAP client sees the same.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

- **Spec coverage:** Single requirement ("messages stay unread after viewing") → Task 1 (interface + ViewModel test), Task 2 (IMAP write), Task 3 (ViewModel logic + optimistic update), Task 4 (UI trigger), Task 5 (verify on device). No gaps.
- **Placeholders:** None — every step has full code or a concrete command.
- **Type consistency:** `MailFetcher.markSeen(MailCredentials, List<String>): Unit` (suspend) referenced identically in interface, impl, ViewModel, and tests. `markConversationRead(conversationId: String)` referenced identically in ViewModel, AppRoot wire-up, and test.
- **Backward-compat / API surface:** `MailFetcher` is an internal interface; only `ImapMailFetcher` and test mocks implement it. Adding a method requires every test mock that *isn't* `relaxed = true` to stub it — `AppViewModelTest.buildVm` already uses `mockk<MailFetcher>()` with explicit `coEvery` so add `coEvery { markSeen(any(), any()) } returns Unit` to the default in any test that exercises markConversationRead. Existing tests using `relaxed = true` need no change.
