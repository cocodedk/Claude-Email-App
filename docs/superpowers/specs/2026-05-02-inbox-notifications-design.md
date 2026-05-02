# Inbox notifications via process-lifetime IMAP IDLE

**Status:** v0 in progress (slimmed scope — see "v0 scope" below); v1+ follow-ups deferred
**Date:** 2026-05-02
**Author:** agent-Claude-Email-App (with peer agent-claude-email)

## v0 scope (what we're actually shipping first)

The original design below is the long-term target. v0 ships the minimum that delivers the user goal ("buzz my pocketed phone when a reply arrives, stop when I close the app") and defers everything reactive/IDLE-shaped to v1+.

**v0 reuses the existing 15s foreground poll** instead of adding IMAP IDLE. The activity-scoped polling job in `AppViewModel` runs while the activity is alive (which is "while the app is in recents" in practice). After each poll, we diff the new inbox snapshot against the previous one and post a notification per new non-ACK message, suppressed when the app is in foreground.

### v0 in / v0 out

| Item | v0? | Why |
|---|---|---|
| Notification on new non-ACK message | ✅ | Core goal |
| Skip if foreground (`STARTED+`) | ✅ | Avoids buzzing while user is looking at the app |
| Settings toggle (default ON) | ✅ | User must be able to disable |
| `POST_NOTIFICATIONS` permission ask | ✅ (in `MainActivity.onCreate`) | Required on Android 13+ |
| `replies` notification channel | ✅ | Required infra |
| Tap notification → opens app | ✅ (default `MainActivity` launch) | Don't deep-link to specific conversation in v0 |
| `ClaudeEmailApplication` subclass | ✅ | Channel registration + future hook |
| In-memory diff against last inbox snapshot | ✅ | Tracked in `AppViewModel`; bounds spam by suppressing the historical backlog on first poll |
| IMAP IDLE listener | ❌ defer | 15s poll is good enough; revisit only if latency complaints |
| `InboxWatcher` reactive plumbing | ❌ defer | Existing polling lifecycle is sufficient |
| `CredentialsBus` | ❌ defer | Single-account, infrequent change; existing flow handles it |
| Persistent UID water mark + UIDVALIDITY | ❌ defer | In-memory `seenIds` set + first-poll suppression replaces it |
| Persistent `conversationKey → Int` ID allocator | ❌ defer | Use `messageId.hashCode()`; collision risk on UUID@domain is negligible |
| PROGRESS rate-limit | ❌ defer | Most commands emit ≤3 messages; revisit if noisy |
| Network availability callback | ❌ defer | Polling already retries on next interval |
| Deep-link to specific conversation | ❌ defer | v0 just opens the app |
| `onTaskRemoved` cleanup | ❌ defer | Activity-scoped polling dies with the activity = "swipe = silence" for free |
| Inline "permission denied" hint | ❌ defer | If denied, toggle silently no-ops |
| `ImapSession` factory extraction | ❌ defer | Refactor only matters with a 2nd consumer |

### v0 file deltas

| File | Change |
|---|---|
| `app/ClaudeEmailApplication.kt` (new) | `Application` subclass; registers `replies` channel in `onCreate` |
| `app/InboxNotifier.kt` (new) | Single-shot `handle(message)`: skip if disabled / ACK / foreground; otherwise post |
| `data/InboxNotificationPrefs.kt` (new) | Just the `notificationsEnabled` toggle (StateFlow + setter) |
| `app/AppViewModel.kt` (modify) | Inject `InboxNotifier` + prev-snapshot diff in `refreshInbox`; expose `notificationsEnabled` |
| `app/SettingsScreen.kt` (modify) | "Notify on replies" Switch |
| `MainActivity.kt` (modify) | Request `POST_NOTIFICATIONS` on Android 13+ in `onCreate` |
| `AndroidManifest.xml` (modify) | Add `POST_NOTIFICATIONS` perm + `android:name=".app.ClaudeEmailApplication"` |

---

## (Below: original v1+ design, retained as roadmap)



## Goal

Buzz the device when a Claude reply arrives, so the user can pocket the phone after sending a command and get pinged. When the user dismisses the app from recents, notifications stop — the app being closed is the explicit "I'm done" signal.

## Decisions locked in

| Decision | Choice | Reason |
|---|---|---|
| What triggers a notification? | Skip `Envelope.kind == ACK` only; fire on every other kind (including unknown/null) | Each command emits ACK + N PROGRESS + RESULT (or ERROR / QUESTION). Buzzing on ACK is noise — the user just hit Send. **Fail-open** on unknown/missing kinds so future schema additions don't silently swallow important replies. |
| Notification ID strategy | Persistent `conversationKey → Int` map (atomic counter, stored in DataStore) | `String.hashCode()` collisions would overwrite the wrong conversation. Allocate a stable int ID on first sight of each conversation. |
| Watcher lifecycle | Reactive on credentials, prefs, permission, network — not one-shot | Restart on any of these signals changing, not just process startup. |
| PROGRESS rate-limiting | First PROGRESS per conversation buzzes; subsequent PROGRESS updates use `setOnlyAlertOnce(true)`. QUESTION / RESULT / ERROR always re-buzz | Avoids notification spam for chatty agents emitting many PROGRESS lines, while preserving urgency for terminal/blocking states. |
| When does the listener run? | Process-lifetime only (no Service) | User explicitly: "when the app is closed it means I am done." Process death = silence. |
| Notification grouping | One per conversation, updated in place | Skipping ACK keeps volume low; collapsing keeps the shade clean. |
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
| `app/InboxWatcher.kt` | ~140 | app | Owns process-scoped scope; reactive on credentials/prefs/network/permission changes; wires IDLE callbacks → notifier |
| `app/InboxNotifier.kt` | ~150 | app | Applies kind-rule, posts/updates grouped notifications, suppression check, PROGRESS rate-limit |
| `mail/ImapIdleListener.kt` | ~150 | mail | Long-lived IMAP connection issuing IDLE on `Dispatchers.IO`; emits arrival callbacks; cancellation closes folder/store to break IDLE |
| `mail/ImapSession.kt` | ~60 | mail | Shared session/properties factory (extracted from `ImapMailFetcher`) |
| `data/InboxNotificationPrefs.kt` | ~80 | data | Notification toggle + (UIDVALIDITY, lastNotifiedUid) + atomic `conversationKey → Int` ID allocator + first-PROGRESS-seen set |

### Files modified

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add `POST_NOTIFICATIONS` permission; set `android:name=".app.ClaudeEmailApp"` on `<application>` |
| `app/MainActivity.kt` | Handle deep-link extras in `onNewIntent` → navigate to ConversationScreen |
| `app/SettingsScreen.kt` | Add "Notify on replies" toggle |
| `app/AppRoot.kt` | Trigger `POST_NOTIFICATIONS` permission ask on first Send when toggle ON |
| `mail/ImapMailFetcher.kt` | Refactor to use new `ImapSession` factory (offsets size cost so file stays under 200 LOC) |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | Add `androidx.lifecycle:lifecycle-process`. Verify Angus Mail + Angus Activation versions are pinned and that R8/release-build still passes (Jakarta Mail 2.0.x has historically had Android packaging quirks) |

## Data flow

1. **Cold start.** `ClaudeEmailApp.onCreate` registers notification channel (`"replies"`, `IMPORTANCE_DEFAULT`) and a `ProcessLifecycleObserver`. On `ON_CREATE`, observer reads credentials from `CredentialsStore`. If present and notifications toggle is ON, it starts `InboxWatcher`.
2. **IDLE loop.** `InboxWatcher` launches a coroutine on a process-scoped scope. Connects via `ImapSession`, checks `CAPABILITY` for `IDLE`. If absent, logs once and exits silently (existing 15s foreground poll still works).
3. **Initial sync.** Reads stored `(UIDVALIDITY, lastNotifiedUid)`. If UIDVALIDITY changed (rare: mailbox rebuilt), updates pointer to current `UIDNEXT - 1` and skips notifying for the backlog. Otherwise records `UIDNEXT - 1` if no value yet (first run).
4. **Wait.** Issues `IDLE`. Server pushes `EXISTS` when new mail arrives.
5. **Fetch.** On push, sends `DONE`, fetches `UID > lastNotifiedUid`. Advances the high-water-mark pointer **only after** all post-processing for that batch succeeds (so a parse error or notification failure doesn't permanently skip the message — next loop will re-fetch).
6. **Filter + post.** For each message: read parsed `envelope?.kind`. Skip only if `kind == ACK`. For everything else (including null and unknown kinds): look up conversation key, hand to `InboxNotifier`. **Fail-open** so a future kind addition isn't silently swallowed.
7. **Suppress check.** `InboxNotifier` checks `ProcessLifecycleOwner.get().lifecycle.currentState`. If `STARTED+`, refresh inbox state but skip `notify()`.
8. **Post.** Allocate / look up the conversation's notification `Int` ID via `InboxNotificationPrefs` (atomic counter, persisted, never reused). Build `NotificationCompat.Builder`: title = project path; text = subject of latest message; `setOnlyAlertOnce(true)` for `PROGRESS` *after* the first one for that conversation, otherwise `false` (always alert on the first PROGRESS, plus every QUESTION / RESULT / ERROR). Deep-link extras: `conversationKey`.
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
| Process death mid-IDLE | Coroutine cancellation closes folder/store in `finally`, which is what breaks the blocking `IMAPFolder.idle()` call. IDLE runs on a dedicated `Dispatchers.IO` thread |
| OEM keeps process alive after recents-swipe | Some OEMs (Samsung, Xiaomi) don't kill the process on swipe. In that case notifications continue — acceptable since the app is still effectively "alive". `Application.onTaskRemoved` is overridden to explicitly cancel the watcher scope so the user-visible behavior matches expectations on those devices too |
| Doze / app standby | Acknowledged limitation: under deep Doze (device unplugged + screen off + idle for 1h+) network access is restricted. Notifications will resume when Doze releases. Documented as a known trade-off of the no-service approach |

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

- `InboxNotifier` kind-rule: `ACK` → no post; `PROGRESS` / `QUESTION` / `RESULT` / `ERROR` → post; **null envelope → POST (fail-open)**; **unknown kind string → POST (fail-open)**; toggle OFF → no post for any kind.
- `InboxNotifier` PROGRESS rate-limit: first PROGRESS in conversation → `setOnlyAlertOnce(false)`; second PROGRESS in same conversation → `setOnlyAlertOnce(true)`; subsequent QUESTION → always `false`.
- `InboxNotifier` foreground suppression: `STARTED` lifecycle state → no `notify()` call (verify via mocked `NotificationManagerCompat`).
- `InboxNotifier` grouping + ID allocation: two messages in same conversation → same `Int` ID (verify allocator returns same value); two messages in different conversations → distinct IDs; allocator is persisted across process restarts.
- `InboxNotificationPrefs` high-water-mark: UIDVALIDITY change resets pointer; UIDVALIDITY same advances pointer; **pointer only advances after the post step succeeds** (simulate post failure → next call re-fetches the same UID).
- `ImapIdleListener` capability fallback: mock CAPABILITY response without `IDLE` → listener exits cleanly without throwing.
- `ImapIdleListener` cancellation: cancel coroutine → `folder.close()` invoked in `finally` (verify via spy) → IDLE thread unblocks within 1s.
- `InboxWatcher` reactivity: credentials emission → restart; prefs toggle OFF emission → cancel; network `onLost` → cancel; network `onAvailable` → restart.

Integration (existing `.env` IMAP):

- `ImapIdleListener` against real one.com: connect → IDLE → trigger by sending an email from a sibling account (or from claude-email backend) → assert `onMessagesArrived` fires within 5s.
- Reconnect: kill the socket out-of-band → assert backoff loop reconnects.

Manual (device):

- Send command → background app → wait for reply → confirm notification fires + tap deep-links to conversation.
- Send command → swipe app from recents → wait for reply → confirm no notification.
- Toggle off in Settings → confirm no notification fires.
- Wifi → cellular handoff during IDLE → confirm reconnect (logcat).

## Known limitations

- **Reliability is best-effort, not guaranteed.** This is the explicit user-chosen trade-off of "no background service". Under aggressive OEM background restrictions, deep Doze, or process-killed-by-low-memory, notifications may be missed. If the user wants guaranteed delivery, the upgrade path is FCM push from the backend (see "Out of scope").
- **Foreground suppression is coarse.** Suppresses any time the activity is `STARTED+`, not specifically when viewing the matching conversation. Future iteration could thread the visible conversation key through to the notifier; not worth the state churn for v1.

## Out of scope (follow-ups)

- Actions on the notification (reply, archive) — would need full message-state mutation from a `BroadcastReceiver`. Defer.
- Quiet hours / schedule. Defer.
- Per-conversation mute. Defer.
- Background-when-closed via FCM — different feature; would require Firebase + backend coordination.

## Migration

None. New feature, additive. Existing 15s foreground poll continues to run alongside (it covers the case where IDLE isn't supported and serves as a belt-and-braces in foreground).
