# Inbox notifications via process-lifetime IMAP IDLE

**Status:** design — pending review
**Date:** 2026-05-02
**Author:** agent-Claude-Email-App (with peer agent-claude-email)

## Goal

Buzz the device when a Claude reply arrives, so the user can pocket the phone after sending a command and get pinged. When the user dismisses the app from recents, notifications stop — the app being closed is the explicit "I'm done" signal.

## Decisions locked in

| Decision | Choice | Reason |
|---|---|---|
| What triggers a notification? | Skip `Envelope.kind == ACK`; fire on `PROGRESS`, `QUESTION`, `RESULT`, `ERROR` | Each command emits ACK + N PROGRESS + RESULT (or ERROR / QUESTION). Buzzing on ACK is noise — the user just hit Send. The other kinds all need attention; QUESTION especially since the agent is blocked waiting. |
| When does the listener run? | Process-lifetime only (no Service) | User explicitly: "when the app is closed it means I am done." Process death = silence. |
| Notification grouping | One per conversation, updated in place | C-rule keeps volume low; collapsing keeps the shade clean. |
| Tap target | Deep-link to that `ConversationScreen` | Obvious. |
| Channel/importance | Single `"replies"` channel, `IMPORTANCE_DEFAULT` (heads-up + sound) | One knob. Users can tweak per-channel in system settings. |
| `POST_NOTIFICATIONS` ask | Just-in-time, on first Send when toggle is ON | No upfront permission scare. |
| Settings toggle | "Notify on replies", default ON, single switch | Same DataStore as `syncIntervalMs`. |
| Foreground suppression | Suppress posts while activity is `STARTED+` | You're already looking at the app. |

## Non-goals

- No `Service` (foreground or otherwise).
- No FCM, no Firebase, no backend changes.
- No `WorkManager` / periodic background poll.
- No notification actions (reply / archive from shade) in v1.
- No multi-account fanout (app is single-account).

## Architecture

### Components

```
Application (ClaudeEmailApp)
 └─ ProcessLifecycleObserver (ON_CREATE)
     └─ InboxWatcher (process-scoped CoroutineScope)
         ├─ ImapIdleListener  (mail/, transport)         ── emits onMessagesArrived(uids)
         ├─ ImapMailFetcher   (mail/, existing)           ── fetch new messages by UID; already parses envelope
         ├─ ConversationGrouper (data/, existing)         ── group fetched into conversations
         └─ InboxNotifier    (app/, posts notifications)  ── apply kind-rule, post grouped

(Envelope JSON parsing already lives in `mail/ImapMailFetcher.tryParseEnvelope` and emits
 `MailMessage.envelope: Envelope?`, so the listener path reuses it without a new parser.)
```

### Files added

| File | Lines (est) | Layer | Responsibility |
|---|---|---|---|
| `app/ClaudeEmailApp.kt` | ~50 | app | `Application` subclass: register channel + lifecycle observer |
| `app/InboxWatcher.kt` | ~120 | app | Owns process-scoped scope; wires IDLE callbacks → notifier |
| `app/InboxNotifier.kt` | ~140 | app | Applies kind-rule, posts/updates grouped notifications, suppression check |
| `mail/ImapIdleListener.kt` | ~150 | mail | Long-lived IMAP connection issuing IDLE; emits arrival callbacks |
| `mail/ImapSession.kt` | ~60 | mail | Shared session/properties factory (extracted from `ImapMailFetcher`) |
| `data/InboxNotificationPrefs.kt` | ~50 | data | Notification toggle + last-notified UID per (UIDVALIDITY) |

### Files modified

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add `POST_NOTIFICATIONS` permission; set `android:name=".app.ClaudeEmailApp"` on `<application>` |
| `app/MainActivity.kt` | Handle deep-link extras in `onNewIntent` → navigate to ConversationScreen |
| `app/SettingsScreen.kt` | Add "Notify on replies" toggle |
| `app/AppRoot.kt` | Trigger `POST_NOTIFICATIONS` permission ask on first Send when toggle ON |
| `mail/ImapMailFetcher.kt` | Refactor to use new `ImapSession` factory (offsets size cost so file stays under 200 LOC) |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | Add `androidx.lifecycle:lifecycle-process` |

## Data flow

1. **Cold start.** `ClaudeEmailApp.onCreate` registers notification channel (`"replies"`, `IMPORTANCE_DEFAULT`) and a `ProcessLifecycleObserver`. On `ON_CREATE`, observer reads credentials from `CredentialsStore`. If present and notifications toggle is ON, it starts `InboxWatcher`.
2. **IDLE loop.** `InboxWatcher` launches a coroutine on a process-scoped scope. Connects via `ImapSession`, checks `CAPABILITY` for `IDLE`. If absent, logs once and exits silently (existing 15s foreground poll still works).
3. **Initial sync.** Reads stored `(UIDVALIDITY, lastNotifiedUid)`. If UIDVALIDITY changed (rare: mailbox rebuilt), updates pointer to current `UIDNEXT - 1` and skips notifying for the backlog. Otherwise records `UIDNEXT - 1` if no value yet (first run).
4. **Wait.** Issues `IDLE`. Server pushes `EXISTS` when new mail arrives.
5. **Fetch.** On push, sends `DONE`, fetches `UID > lastNotifiedUid`, advances pointer.
6. **Filter + post.** For each message: read parsed `envelope?.kind`. Skip if `ACK` or null (not a JSON envelope). For `PROGRESS`, `QUESTION`, `RESULT`, `ERROR`: look up conversation key, hand to `InboxNotifier`.
7. **Suppress check.** `InboxNotifier` checks `ProcessLifecycleOwner.get().lifecycle.currentState`. If `STARTED+`, refresh inbox state but skip `notify()`.
8. **Post.** Otherwise build `NotificationCompat.Builder` with conversation ID as notification ID. Title = project path; text = subject of latest message; `setOnlyAlertOnce(false)` so subsequent updates re-buzz; deep-link extras: `conversationKey`.
9. **Re-IDLE.** Loop back to step 4.
10. **Process death.** User swipes app from recents → process killed → coroutine cancelled → `finally` closes IMAP socket → no further notifications.

## Reliability

| Risk | Mitigation |
|---|---|
| IDLE timeout (servers drop ~30 min) | Reissue `DONE` + `IDLE` every 25 min |
| Socket failure / exception | Reconnect with backoff: 5s → 10s → 30s → 60s, cap at 60s. Reset on successful EXISTS push |
| Network change (wifi ↔ cellular) | `ConnectivityManager.NetworkCallback`: on `onLost` cancel IDLE; on `onAvailable` restart immediately |
| Server lacks IDLE | Detect via `CAPABILITY` on connect, log once, exit listener cleanly. Foreground 15s poll continues |
| Notification flood on first run | High-water-mark UID stored per UIDVALIDITY in `InboxNotificationPrefs` |
| Sign-out / credentials change | Observe `CredentialsStore` flow → cancel + relaunch listener |
| Permission denied | One-time inline hint on `ConversationScreen` ("Enable notifications in system settings") — non-blocking, dismissible |
| Process death mid-IDLE | Coroutine cancellation closes socket via `try/finally` — no leak |

## Permission flow

1. User toggles "Notify on replies" ON (default) in onboarding or Settings.
2. User taps Send on a command.
3. If Android 13+ and permission not yet granted/denied, `MainActivity` invokes the `RequestPermission` contract for `POST_NOTIFICATIONS` immediately after dispatch succeeds.
4. If denied: Android tracks the denial natively. Show a one-time inline hint on `ConversationScreen` for that conversation. We don't re-prompt automatically (rely on `shouldShowRequestPermissionRationale` to know whether asking again is allowed).
5. If granted: subsequent notifications post normally.
6. Permission state is checked via `NotificationManagerCompat.areNotificationsEnabled()` before each post — handles user toggling at the OS level later.

## Settings

Single switch added to `SettingsScreen`:

> **Notify on replies** _(default: on)_
> Get a notification on the device when an agent reply arrives. Requires the app to remain in recents.

Stored in the same DataStore as `syncIntervalMs`.

## Testing

Unit (Robolectric + MockK):

- `InboxNotifier` kind-rule: `ACK` input → no post; `PROGRESS` / `QUESTION` / `RESULT` / `ERROR` → post; null envelope → no post; toggle OFF → no post for any kind.
- `InboxNotifier` foreground suppression: `STARTED` lifecycle state → no `notify()` call (verify via mocked `NotificationManagerCompat`).
- `InboxNotifier` grouping: two messages in same conversation → single `notify()` call with same ID, second overwrites.
- `InboxNotificationPrefs` high-water-mark: UIDVALIDITY change resets pointer; UIDVALIDITY same advances pointer.
- `ImapIdleListener` capability fallback: mock CAPABILITY response without `IDLE` → listener exits cleanly without throwing.

Integration (existing `.env` IMAP):

- `ImapIdleListener` against real one.com: connect → IDLE → trigger by sending an email from a sibling account (or from claude-email backend) → assert `onMessagesArrived` fires within 5s.
- Reconnect: kill the socket out-of-band → assert backoff loop reconnects.

Manual (device):

- Send command → background app → wait for reply → confirm notification fires + tap deep-links to conversation.
- Send command → swipe app from recents → wait for reply → confirm no notification.
- Toggle off in Settings → confirm no notification fires.
- Wifi → cellular handoff during IDLE → confirm reconnect (logcat).

## Out of scope (follow-ups)

- Actions on the notification (reply, archive) — would need full message-state mutation from a `BroadcastReceiver`. Defer.
- Quiet hours / schedule. Defer.
- Per-conversation mute. Defer.
- Background-when-closed via FCM — different feature; would require Firebase + backend coordination.

## Migration

None. New feature, additive. Existing 15s foreground poll continues to run alongside (it covers the case where IDLE isn't supported and serves as a belt-and-braces in foreground).
