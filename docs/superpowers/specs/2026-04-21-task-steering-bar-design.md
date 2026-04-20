# Task-Steering Bar — Design Spec

Date: 2026-04-21
Status: draft (awaiting user review)
Scope: `ConversationScreen` only (v1). Follow-up specs cover HomeScreen thread-collapse; Dashboard is parked.
Extends: [§4.3 Conversation of `docs/ux-ui-spec.md`](../../ux-ui-spec.md). Visual language, typography, and global palette come from that document; this spec defines one component.

Companion mockup: `.superpowers/brainstorm/191947-1776721585/content/steering-bar-v1.html` (four-state walkthrough).

---

## 1. Purpose

Give the user one-tap control over the *running task* from the Conversation screen without hunting through menus or risking an accidental destructive action. The bar lives above the composer and is pinned while the thread scrolls above it.

## 2. Framing: task-steering, not message-action

The chips reflect `PendingCommand.status` (see `app/src/main/java/com/cocode/claudeemailapp/data/PendingCommand.kt`), **not** the `Envelope.kind` of the message currently on screen. Scrolling up in the thread does not change what **Cancel** cancels.

The current conversation maps to one `PendingCommand` (matched via `In-Reply-To`). If no pending command is associated (e.g. viewing a finished task or a non-task message), the steering bar is hidden and only the composer is shown.

## 3. Four states

All four are visible in the companion mockup. The chip row is always `[Status] [Cancel] [More]` except in the awaiting_user state.

### 3.1 Idle — task running
- Trigger: `PendingCommand.status ∈ {QUEUED, RUNNING}`.
- Chips: `Status`, `Cancel`, `More` — all enabled.
- Header status pill: green `running`, with monotonic elapsed time and progress summary (e.g. `2m 14s · 3/7 files`).

### 3.2 Sending — request in flight
- Trigger: user tapped a chip that dispatches a command (e.g. `Status`); SMTP+IMAP-ack round-trip can take up to 45s.
- Affordances: the tapped chip swaps its icon for a spinner and its label to `Sending…`. Other chips in the bar are visually disabled and do not respond to taps.
- Exit: on ack received (kind=ack matched via `In-Reply-To`) the chip briefly flashes "acked" (≤600ms), then returns to idle. On timeout or error, returns to idle with an inline error toast.

### 3.3 Armed — cancel confirm (two-tap destructive)
- Trigger: first tap on `Cancel` in idle state.
- Affordances: the `Cancel` chip flips to red `Cancel · confirm`; a hint snackbar appears above the composer ("Tap Cancel again to confirm · 3s…").
- Second tap within 3s → dispatch cancel intent (transitions to state 3.2 sending). Any other tap, or timeout, → disarm silently.
- Post-fire: a 5s undo snackbar follows ("Cancelling task #42 · Undo"). Undo within the window aborts the cancel request (if it has not yet been acked by the backend) and restores idle.
- The `More` sheet exposes a separate `Cancel + drain queue` control with its own confirm dialog; this is not reachable from the main bar.

### 3.4 Awaiting user — agent asked a question
- Trigger: `PendingCommand.status == AWAITING_USER` (set when backend sent `kind=question`; `askId` populated).
- The chip row is replaced by a template sheet pinned above the composer:
  - Heading: `Reply templates`
  - A list of 2–3 context-appropriate templates (tap → appends template text to the composer field with a leading newline if the composer is non-empty; user still presses `Send`).
  - Below, an `Advanced` divider with `Status`, `Cancel`, `Cancel + drain queue`, `Reset`.
- Sending a reply includes `meta.ask_id` (already supported by `PendingCommand.askId`) so the backend routes the answer back to the waiting question.

## 4. Load-bearing decisions

Each is visible in the mockup and must be preserved through implementation:

1. **Task-steering, not message-action.** Chips bind to `PendingCommand.status`, not to the envelope kind of any specific message.
2. **Cancel is two-tap plus undo.** Single-tap cancel is forbidden anywhere in the bar.
3. **Three chip states, always.** `idle` · `sending` (spinner, peers disabled) · `acked` (brief flash). The chip must never look unresponsive during the 45s round-trip window.
4. **Templates live in `strings.xml`.** No hardcoded user-facing strings in Compose (per project CLAUDE.md). Tap-template appends; it does not auto-send.

## 5. Data flow

```
PendingCommand (data/)
   ├── status ─────────────────▶ SteeringBar chip set (state 3.1 / 3.4)
   ├── askId ──────────────────▶ Template sheet visibility + meta.ask_id on reply
   └── taskId, branch ─────────▶ Header taskline display

Chip tap
   └── AppViewModel.dispatch(CommandIntent) ──▶ EnvelopeBuilder (protocol/)
       └── SmtpMailSender ──▶ (state 3.2 sending) ──▶ ack via IMAP poll
            └── updates PendingCommand.status ──▶ Compose recomposes
```

## 6. Error handling

| Situation | Behavior |
|-----------|----------|
| SMTP send fails | Chip reverts to idle; red inline error above composer with a `Retry` action. |
| No ack within 45s | Chip reverts to idle; amber "No ack yet" snackbar with `Retry` / `View status`. |
| Cancel arm times out | Chip silently disarms; no toast. |
| User taps `Cancel` on a task already in `DONE`/`FAILED`/`ERROR` | Steering bar should already be hidden; if reached via stale state, chip is disabled. |

## 7. Testing

- **Unit (Robolectric + MockK):** state machine mapping `PendingCommand.status` → `SteeringBarState`; two-tap arming logic with a controllable clock; template append behavior.
- **Compose UI test:** the four visual states render with the expected chips/labels; tap semantics fire the right intents; `Cancel` requires the second tap.
- **Integration:** wire the steering bar into `ConversationScreen` behind the existing `PendingCommandStore` flow; verify `meta.ask_id` is echoed on reply in the awaiting_user path.

## 8. Strings

All user-facing text sourced from `strings.xml`:
`steering_chip_status`, `steering_chip_cancel`, `steering_chip_more`, `steering_chip_sending`, `steering_cancel_confirm`, `steering_cancel_undo`, `steering_cancel_armed_hint`, `steering_templates_heading`, `steering_advanced_heading`, plus per-context template bodies (`template_task_continue`, `template_task_abort`, …).

## 9. Out of scope (deferred)

- HomeScreen thread-collapse (follow-up spec).
- Dashboard / multi-task overview (parked).
- Command palette / keyboard shortcuts.
- Offline queueing of steering intents (currently dispatched immediately; no local queue).
- Rich per-template previews in a bottom-sheet picker.

## 10. Open questions

None at write time. Any interpretation ambiguity should be resolved during `writing-plans`.
