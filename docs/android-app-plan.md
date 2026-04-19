# Claude Email Android App Plan

Status: draft  
Last updated: 2026-04-19

## 1. Goal

Build an Android app that replaces Outlook for `claude-email`.

The app should:

- register and use **one** mailbox account only
- send commands and replies through email
- receive and display replies from `claude-email`
- let the user follow multiple agents and tasks without confusion
- separate new conversations from old conversations cleanly, even with the same agent
- leave room for file attachments in chat-style conversations
- feel like a focused mobile product, not a generic email client
- preserve room for the stronger encrypted mobile protocol already being explored in `../claude-email/docs/mobile-app-plan.md`

## 2. Protocol Correction

Email transport in the app should be:

- **IMAP**: receive, sync, search, mark seen
- **SMTP**: send messages

The app should not be designed around "sending on IMAP". That is the wrong transport split and will create implementation problems immediately.

## 3. Recommendation

Recommended product shape for v1:

- one configured mailbox profile
- one primary `claude-email` contact per setup flow
- conversation-centric UI that feels like chat
- explicit separation between `agent`, `task`, and `conversation`
- no folder tree, no categories, no newsletter features, no generic enterprise mail UI

Why this direction:

- the app exists for one job: talking to `claude-email`
- Outlook is frustrating here because the UI is built for general email, not command/reply work
- a purpose-built client can hide email mechanics and surface the signal: commands, replies, agent messages, status, and sync state
- the raw backend model should not define the final UX, because one agent may handle different tasks across different days

## 4. Product Scope

### In scope for v1

- single mailbox account stored on device
- manual setup for IMAP + SMTP credentials
- optional presets for common providers later
- connect to one `claude-email` service address first
- display messaging-style conversations from that service
- send new commands
- reply to existing conversations
- start a **new conversation** intentionally instead of dumping everything into one endless history
- show which agent a conversation belongs to
- show a user-editable task title or subject label for each conversation
- show delivery, sync, and error state clearly
- local offline cache of recent messages
- notifications for new replies

### In scope for v1.1

- support multiple `claude-email` contacts while keeping one mailbox account
- quick command actions
- message search
- file attachments
- conversation archive/snooze/pin
- improved background sync tuning

### In scope for v2

- encrypted-envelope transport described in the sibling `claude-email` plan
- QR-based pairing and key storage
- richer setup verification and pairing health checks
- backend-native conversation/session identifiers instead of app-side reconstruction

### Out of scope

- multiple mailbox accounts
- generic inbox management
- calendar and contacts as primary features
- OAuth provider integrations in the first cut
- full Outlook parity

## 5. Grounding in the Existing `claude-email` Service

The current Python service already does the following:

- polls mail via IMAP
- sends replies via SMTP
- uses `In-Reply-To` and `References` for threading
- authorizes mail with GPG or a shared secret in the subject
- supports agent-style routed conversations through email

Important current limitation:

- the existing chat design effectively threads by **agent**
- that is not sufficient for a clean mobile UX when the same agent handles multiple tasks over time
- the Android app should treat conversation clarity as a product requirement, not something inherited from the raw backend model

That means the fastest practical mobile path is:

### Phase A: work with the current service

- app sends mail in the format the service already accepts
- app reads the mailbox and presents those messages in a focused mobile UX
- app uses the existing shared-secret path first, because mobile GPG is not the right first implementation
- app adds local conversation grouping rules so the UI stays understandable before the backend gains true session support

### Phase B: add stronger mobile-native security

- extend both the app and `claude-email` to support encrypted-envelope messaging
- keep the same UX model while changing the transport/authentication underneath

### Phase C: add backend-native session clarity

- extend `claude-email` so conversations have explicit session or workstream identity, not only agent identity
- allow "new conversation with this agent" without collapsing into the old one
- preserve attachment metadata cleanly once attachments are supported

This phased approach gets the user off Outlook faster and avoids blocking the app on protocol redesign.

## 6. Primary User Flows

### First-run setup

1. User opens the app.
2. User is told clearly that the app supports one mailbox account.
3. User enters:
   - display name
   - email address
   - password or app password
   - IMAP host and port
   - SMTP host and port
   - `claude-email` service address
   - shared secret for current-service mode
4. App validates IMAP login.
5. App validates SMTP login.
6. App sends a lightweight test email to the configured `claude-email` address.
7. User lands in the main conversation view only after setup succeeds.

### Send a new command

1. User opens Home and taps `New conversation`.
2. User chooses an agent or target context.
3. User optionally sets a task title.
4. User types a command in a command-first composer.
5. App makes it clear this starts a fresh conversation thread.
6. App sends through SMTP.
7. App shows immediate optimistic state: `Sending`.
8. App schedules a focused sync to look for the reply.
9. Reply arrives and renders as part of the same conversation.

### Continue an older conversation

1. User opens an existing conversation card.
2. The header clearly shows:
   - agent name
   - task title
   - last active time
   - whether the conversation is active or archived
3. User replies in context.
4. The app keeps this conversation visually separate from newer conversations involving the same agent.

### Attach a file to a message

1. User opens the composer.
2. User taps `Attach`.
3. User picks a file from the device.
4. The app shows a file chip or card before send.
5. On send, the app includes the attachment in the email.
6. If the backend cannot process the attachment type yet, the app warns before send instead of failing silently.

### Recover from failure

1. If SMTP fails, the draft remains visible with retry.
2. If IMAP auth fails, the app surfaces a blocking account-health banner.
3. If the shared secret is wrong, the user sees "sent but not accepted" guidance instead of a vague sync failure.
4. If an attachment is too large or unsupported, the app explains that before the final send action.

## 7. UX Product Shape

The app should feel like:

- a private command console
- a premium messenger
- a calm operations tool
- a messaging app for work with agents, not an email inbox

It should not feel like:

- a cloned email inbox
- a dense admin console
- a developer-only debug tool

Recommended top-level information architecture:

- `Home`: conversation list and account health
- `Conversation`: one session with one agent and one task or workstream
- `Command Composer`: bottom-sheet or inline composer
- `Settings`: account, sync, security mode, diagnostics

## 8. Conversation Model

The app should distinguish three different concepts:

- `Agent`: who is doing the work
- `Task` or `Workstream`: what the work is about
- `Conversation Session`: one bounded exchange over time

These must not collapse into a single ambiguous thread label.

### 8.1 Why this matters

Without this separation, the user will hit these failure modes:

- the same agent appears to have one endless thread covering unrelated work
- old context contaminates new work mentally
- the user cannot tell whether a reply belongs to the current task or last week's task
- notifications become noisy because they identify only the agent, not the actual conversation

### 8.2 Product rules

- starting fresh work should default to a **new conversation**
- continuing prior work should be an explicit user choice
- each conversation needs a visible title, even if initially generated from the first command
- each conversation should show its linked agent prominently but not rely on agent name as the sole identity
- old conversations should be easy to archive without hiding the agent itself

### 8.3 Backend implication

The current backend mostly maps one email thread per agent. That is acceptable only as a transitional compatibility layer.

Long-term requirement:

- add a backend conversation or session identifier
- persist session metadata separately from raw message rows
- preserve transport threading while letting the app show multiple sessions for one agent

This should be treated as a real backend milestone, not as UI polish.

## 9. Technical Architecture

### 9.1 App layers

- `ui`: Compose screens, navigation, state holders
- `domain`: use cases such as send command, sync inbox, retry send, mark thread read
- `data`: repositories coordinating local DB, IMAP sync, SMTP send, settings
- `mail`: transport adapters for IMAP and SMTP
- `storage`: Room DB, DataStore, secure credential handling

Keep this inside one app module initially. Split modules only when the codebase proves it needs them.

### 9.2 Mail transport

Recommended implementation approach:

- use a mail library compatible with Android for IMAP and SMTP
- wrap protocol details behind interfaces from day one
- keep send and sync operations on `Dispatchers.IO`
- support TLS-only configurations in v1

Suggested interfaces:

```kotlin
interface MailSender {
    suspend fun send(message: OutgoingMessage): SendResult
}

interface MailSyncer {
    suspend fun syncSince(cursor: SyncCursor?): SyncResult
}
```

This protects the rest of the app from low-level mail library churn.

### 9.3 Sync strategy

Recommended sync behavior:

- foreground: live refresh and optional IMAP IDLE if the library/device behavior is stable
- background: WorkManager periodic sync
- post-send: one-shot expedited sync sequence for a short period after the user sends a command

Reasoning:

- Android background limits make "always-on instant IMAP" unreliable
- after a send, the user most wants a quick reply, so that is where short-term aggressive sync earns its battery cost

Constraint to plan around:

- plain periodic WorkManager is not a true real-time channel and is typically too slow on its own for command/reply UX
- if background responsiveness must feel near-instant, we will eventually need either a foreground service strategy or push-assisted wakeups

### 9.4 Local storage

Use:

- Room for threads, messages, send queue, sync cursor, and lightweight contact metadata
- DataStore for non-secret preferences
- Android Keystore-backed secure storage for mailbox password or app password

Do not store raw credentials in Room or plain SharedPreferences.

### 9.5 Message model

Core entities:

- `Account`: exactly one row in v1
- `Agent`: display identity plus project metadata when available
- `Conversation`: app-level session with title, agent reference, state, and timestamps
- `Message`: inbound or outbound email mapped to app-level conversation items
- `Attachment`: local metadata plus email-part metadata
- `PendingSend`: outgoing items awaiting send or retry
- `SyncState`: mailbox cursor, last sync time, sync errors

Important normalization rule:

- preserve raw email headers for transport correctness
- convert them into app-native thread and message models before the UI sees them
- reconstruct app-level conversations cautiously while the backend still lacks native session IDs
- once backend-native session IDs exist, stop relying on heuristics for separation

### 9.6 Attachment model

Attachment support should be designed now even if it lands after the first usable send/reply milestone.

Required behavior:

- outgoing attachments selectable from Android document picker
- attachment chips visible in the composer before send
- inbound attachments shown as cards with filename, size, and action
- download/open/share actions
- clear warnings for unsupported file types or oversized payloads

Backend implication:

- current `claude-email` flow does not yet model attachment-aware chat behavior
- attachment support therefore requires coordinated changes in both the Android app and `../claude-email`

Recommended staged delivery:

- first ship attachment-ready UI and data model
- then add transport and backend handling in a dedicated milestone

### 9.7 Security modes

#### v1 security mode

- current `claude-email` shared-secret compatibility
- manual configuration in setup

#### future security mode

- encrypted-envelope mode from the sibling plan
- pairing flow and QR support

Design the settings and data model so both modes can coexist later without rewriting navigation or storage.

## 10. Suggested Screens

### Welcome / Setup

- one-account promise
- short explanation of IMAP + SMTP requirement
- manual connection fields
- test-connection flow

### Home

- recent conversations
- segmented filters such as `Active`, `Waiting`, `Archived`
- optional filters such as `All agents`, `This agent`, `Has attachments`
- unread badges
- sync status
- connection health banner
- floating primary action: `New command`

Conversation row content:

- conversation title
- agent badge
- latest message preview
- timestamp
- unread indicator
- state chip such as `Waiting`, `Needs reply`, `Sending`, `Failed`
- optional attachment icon

### Conversation

- message list rendered as bubbles/cards, not raw mail rows
- command blocks use monospace styling
- status events are visually quieter than message bodies
- reply composer anchored at bottom
- header shows agent, task title, and session state
- overflow menu includes `Rename conversation`, `Archive`, and `Start new conversation`
- attachments render inline as cards, not as raw MIME rows

### Settings

- account details
- server/service address
- security mode
- sync interval
- notification preferences
- export diagnostics
- reset account

### Diagnostics

- last sync time
- last SMTP send result
- IMAP connection result
- protocol mode in use
- copyable but sanitized technical details

## 11. UI/Engineering Constraints

- Compose-first UI
- support dark and light themes, but design the light theme first
- large-output messages must be readable with collapse/expand behavior
- rendering must remain smooth with long terminal-like replies
- pull-to-refresh must be instant and obvious
- every network state must map to a visible UI state
- conversation identity must remain understandable when the same agent appears in many conversations
- attachment rows must not break scrolling performance or message layout

## 12. Delivery Plan

### Milestone 1: Product shell

- create design system tokens
- create navigation shell
- build static screens for setup, home, conversation, settings
- validate agent/task/session separation in the UI before transport details get deep
- validate the UX before protocol work gets deep

### Milestone 2: Single-account setup

- persist one account
- connect to IMAP and SMTP
- validate credentials
- add diagnostics surface

### Milestone 3: Real mail flow

- send command email through SMTP
- fetch and render matching replies through IMAP
- preserve thread headers
- retry and failure handling
- add app-level conversation grouping that keeps old and new work visually distinct

### Milestone 4: `claude-email` specialization

- parse subjects and thread metadata for agent-style flows
- add better command composer and quick actions
- improve notifications
- add explicit `new conversation` and `continue conversation` behaviors
- prepare app data layer for backend-native session IDs

### Milestone 5: Attachments and richer transport

- add outgoing attachment picking and composer support
- add inbound attachment rendering
- update `claude-email` to process attachment-aware chat mail safely
- add attachment validation, size limits, and error handling

### Milestone 6: Security upgrade

- implement encrypted-envelope transport in both repos
- add QR pairing
- remove reliance on subject secret for mobile use

## 13. Key Decisions

### Decision: one account only in v1

Accepted.

Rationale:

- simplifies setup, storage, sync, settings, and UX
- makes the app feel intentional
- reduces error states and support load

### Decision: specialized client instead of generic email client

Accepted.

Rationale:

- the product exists to support `claude-email`
- a generic inbox would spend complexity on the wrong problems

### Decision: current service compatibility before encrypted mobile protocol

Accepted.

Rationale:

- fastest path off Outlook
- easiest way to dogfood the UX immediately
- lets the protocol evolve without blocking the app shell

### Decision: conversation sessions must be explicit

Accepted.

Rationale:

- agent-only threading is too ambiguous for long-running work
- clarity across days is a core product requirement, not a nice-to-have

### Decision: attachment support should be planned now, delivered after the basic mail flow

Accepted.

Rationale:

- the UI and local data model should not paint us into a corner
- the current backend does not yet support attachment-aware chat well enough to promise it in the first shipping cut

## 14. Risks

- Android background sync limits will affect perceived real-time behavior.
- Mail-library behavior on Android can be awkward; keep the transport layer isolated.
- Large command outputs can make the UI feel heavy if rendering is naive.
- If the existing `claude-email` threading is inconsistent across providers, the app will need defensive thread reconstruction logic.
- app-side conversation reconstruction can still mis-group messages until the backend has real session IDs
- attachment support increases transport complexity, storage needs, and failure cases

## 15. Open Questions

1. Should v1 support only one `claude-email` target address, or one mailbox with multiple target addresses?
2. Should the first shipped security model use only the subject secret, or also support a signed-message mode later?
3. Which attachment types should be supported first: images, text/code files, PDFs, or any document the device picker returns?
4. Should setup include provider presets for `one.com` immediately, since that appears likely to be the first real mailbox?
5. Should starting a new conversation with the same agent create a fresh email thread immediately, or can that remain app-local until backend session support lands?
