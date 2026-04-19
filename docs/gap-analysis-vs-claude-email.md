# Gap Analysis: Android App vs. `claude-email` Backend

Status: draft
Last updated: 2026-04-19
Scope: compares the Android client at `/home/cocodedk/0-projects/Claude-Email-App` against the `claude-email` service at `/home/cocodedk/0-projects/claude-email` (commit state on disk).

## 1. Executive Summary

The Android app is a **Compose UI prototype with zero transport, persistence, or backend integration**. The `claude-email` service is a fully operational email-routed task-queue + MCP control plane with 14 MCP tools, SQLite-backed task/agent/message/event tables, per-task git branching, a two-step reset flow, and a cross-project dashboard.

- **App delivers**: 5 static screens (Home, Conversation, New conversation, Settings, Diagnostics), in-memory conversation model, attachment picker UI, a dark theme. ~1,645 lines of Kotlin, all mock data.
- **App does not deliver**: IMAP, SMTP, MCP, HTTP, Room, DataStore, Keystore, WorkManager, notifications, auth, settings persistence, setup flow, background sync, or any network I/O.
- **Bottom line**: the app surfaces **none** of the backend's capabilities end-to-end today. The UI scaffolding is aligned with Phase A of `docs/android-app-plan.md`, but no feature is functional.

## 2. Backend Capability Inventory (summary)

| Area | Backend entry points |
|------|----------------------|
| Email transport | IMAP polling (`src/poller.py`), SMTP send (`src/mailer.py`), Message-ID dedupe (`processed_ids.json`) |
| Email auth | Envelope From + Return-Path check + GPG signature or `AUTH:<secret>` (`src/security.py`) + chat-thread Message-ID mode |
| Task queue | Per-project FIFO with priority 0-10, states pending/running/done/failed/cancelled (`src/task_queue.py`, `src/chat_schema.py`) |
| Workers | One per canonical project path, auto-respawn, idle-exit (`src/worker_manager.py`, `src/project_worker.py`) |
| Branch strategy | `claude/task-<id>-<slug>`, clean-checkout gate, branch recorded in DB (`src/project_worker.py`, `src/git_ops.py`) |
| Reset flow | Two-step token (5-min TTL): `chat_reset_project` → `chat_confirm_reset` (`src/reset_control.py`, `chat/project_tools.py`) |
| Commit escape hatch | `chat_commit_project` runs `git add -A && git commit -m` (`chat/project_tools.py:116`) |
| Dashboard | `chat_where_am_i` returns per-project worker PID, running task, pending count, last activity (`chat/project_tools.py:131`) |
| Agents | register / list / deregister / spawn (`chat/tools.py`), auto-reap dead PIDs (`src/chat_db.py:69`) |
| Chat relay | `chat_ask` (blocking up to 1h), `chat_notify` (fire-and-forget), `chat_check_messages` (ACK) |
| Cleanup | 30-day purge of delivered/failed messages and events |
| Transport surface | MCP SSE on `127.0.0.1:8420`; no REST, no web UI, no push |
| Security | Localhost-only MCP; TLS-verified IMAP/SMTP; sender envelope + GPG/secret; Message-ID idempotency |
| Storage | SQLite WAL: `agents`, `messages`, `events`, `tasks` |
| Experimental | `LLM_ROUTER=1` for natural-language email routing; mobile-app encrypted envelope (design only) |

## 3. App Inventory (summary)

- `MainActivity.kt` (20 lines) — Compose host
- `prototype/PrototypeApp.kt` (~1,240 lines) — all screens, navigation, attachment picker
- `prototype/PrototypeModels.kt` (229 lines) — `AgentProfile`, `ConversationItem`, `MessageItem`, `AttachmentItem`, enums, sample data
- `prototype/PrototypeState.kt` (176 lines) — enum-based screen routing, in-memory conversation list
- `ui/theme/` — dark theme, serif headlines, monospace for user commands
- `AndroidManifest.xml` — single activity, **no permissions** (no `INTERNET`, `POST_NOTIFICATIONS`, etc.)
- `build.gradle.kts` — Compose BOM 2026.02.01 only; **no** Retrofit/OkHttp/JavaMail/Room/DataStore/WorkManager/Firebase/coroutines/MCP SDK

TODOs visible in code (`PrototypeState.kt:167`, `PrototypeApp.kt:1035`, `:1038`) explicitly flag that SMTP/IMAP and sync state are not wired.

## 4. Gap Analysis

Legend: ✅ full · 🟡 partial · ⬜ missing.

### 4.1 Transport & auth

| Backend feature | App status | Notes |
|---|---|---|
| IMAP polling of mailbox | ⬜ missing | No mail library in `app/build.gradle.kts`; `Diagnostics` screen shows hardcoded "IMAP healthy" (`PrototypeApp.kt:1022-1068`). |
| SMTP send with threading (`In-Reply-To`/`References`) | ⬜ missing | Composer sets `statusLabel = "Queued for send"` only (`PrototypeState.kt:167`). |
| Message-ID idempotency | ⬜ missing | No persistence at all. |
| Envelope check (From + Return-Path) | ⬜ missing | No incoming pipeline. |
| Shared-secret auth (`AUTH:<secret>` in Subject/body) | ⬜ missing | No credential or secret storage; Settings screen is informational (`PrototypeApp.kt:965-1020`). |
| GPG signature auth | ⬜ missing | Plan defers to v2 (`docs/android-app-plan.md:111`). |
| Chat-thread Message-ID auth | ⬜ missing | No outbound Message-ID tracking. |
| TLS-verified connections | ⬜ missing | No network layer. |

### 4.2 Task queue (MCP: `chat_enqueue_task`, `chat_cancel_task`, `chat_queue_status`)

| Backend feature | App status | Notes |
|---|---|---|
| Enqueue task against a project | ⬜ missing | No UI concept of "project" or "task body"; `New conversation` picks an **agent**, not a project (`PrototypeApp.kt:847-962`). |
| Priority 0-10 | ⬜ missing | Not modelled. |
| Cancel running / drain pending | ⬜ missing | No cancel UI. |
| Queue status (running task, pending list) | ⬜ missing | Diagnostics screen hardcodes "Queued" / "Healthy" strings. |
| Per-task branch name preview (`planned_branch`) | ⬜ missing | No branch surface. |
| Clean-checkout error surfacing | ⬜ missing | Error body from `git status --porcelain` never reaches the app. |

### 4.3 Git branch strategy & commit escape hatch

| Backend feature | App status | Notes |
|---|---|---|
| Per-task branch naming `claude/task-<id>-<slug>` | ⬜ missing | Not displayed anywhere. |
| Branch recorded on task (`branch_name` column) | ⬜ missing | No task entities. |
| `chat_commit_project(message)` escape hatch | ⬜ missing | No UI to unblock a dirty repo. |

### 4.4 Reset flow (`chat_reset_project` → `chat_confirm_reset`)

| Backend feature | App status | Notes |
|---|---|---|
| Issue confirm token | ⬜ missing | Not wired. |
| 5-minute TTL handling | ⬜ missing | No token storage. |
| Confirm + cancel + drain + `git reset --hard && git clean -fd` | ⬜ missing | Destructive flow not surfaced at all. |

### 4.5 Cross-project dashboard (`chat_where_am_i`)

| Backend feature | App status | Notes |
|---|---|---|
| Per-project worker PID | ⬜ missing | Home screen shows hero card with static "1 account" pill (`PrototypeApp.kt:271-319`). |
| Running task + pending count | ⬜ missing | No project roll-up anywhere. |
| Last activity timestamp | ⬜ missing | Only per-conversation `lastActiveLabel`, all mocked. |

### 4.6 Agents (`chat_register`, `chat_list_agents`, `chat_deregister`, `chat_spawn_agent`)

| Backend feature | App status | Notes |
|---|---|---|
| List agents with status (running/idle/disconnected/deregistered) | 🟡 partial | UI shows `AgentBadge` + `statusLabel` string per agent, but agents are hardcoded (`PrototypeModels.kt` sample data). Status strings are display-only, not mapped to backend states. |
| Spawn agent (project path + instruction) | ⬜ missing | No spawn UI. |
| Deregister | ⬜ missing | No action. |
| Liveness / reap signal | ⬜ missing | Not surfaced. |

### 4.7 Chat relay (`chat_ask`, `chat_notify`, `chat_check_messages`)

| Backend feature | App status | Notes |
|---|---|---|
| Render agent `notify` messages | 🟡 partial | UI can render `MessageRole.Agent`/`System` bubbles; no transport to receive them. |
| Render blocking `ask` with reply field | ⬜ missing | No ask/notify distinction in models. User replies use the generic composer. |
| Auto-ACK on read (`chat_check_messages`) | ⬜ missing | No read state sync. |
| Reply-threaded routing to the right agent | ⬜ missing | Thread model is local-only; no `In-Reply-To` parsing. |

### 4.8 Email routing semantics

| Backend feature | App status | Notes |
|---|---|---|
| `@agent-name` subject routing | ⬜ missing | Composer has no subject field (title is local). |
| Meta-commands (`status`, `spawn`, `restart`) | ⬜ missing | No command palette. |
| CLI fallback routing | ⬜ missing | N/A without transport. |
| Quote-stripping of email replies | ⬜ missing | No inbound parser. |
| 50KB output truncation handling | ⬜ missing | No "[truncated]" rendering. |

### 4.9 Storage & persistence

| Backend feature | App status | Notes |
|---|---|---|
| SQLite task/agent/message/event persistence (backend side) | ⬜ missing (on device) | App has **no Room, no DataStore, no Keystore**; conversation list lives in `mutableStateListOf` and dies with the process (`PrototypeState.kt`). |
| Credential storage | ⬜ missing | Settings screen has no inputs. |
| Sync cursor / last-seen | ⬜ missing | Diagnostics hardcodes "Today 10:44". |
| Attachment cache | ⬜ missing | Attachments are `Uri` references only (`PrototypeApp.kt:1186-1224`). |

### 4.10 Background, notifications, lifecycle

| Backend feature | App status | Notes |
|---|---|---|
| Background poll (IMAP) | ⬜ missing | No WorkManager, no foreground service, no `INTERNET` permission in manifest. |
| Notifications for new replies | ⬜ missing | No channels, no FCM, no `POST_NOTIFICATIONS`. |
| Service restart hooks (`restart self`, `restart chat`) | ⬜ missing | Out of scope for a mobile client; noted for awareness only. |

### 4.11 MCP surface summary

| MCP tool | App coverage |
|---|---|
| `chat_register` | ⬜ |
| `chat_ask` | ⬜ |
| `chat_notify` | 🟡 (rendering only, no receive) |
| `chat_check_messages` | ⬜ |
| `chat_list_agents` | 🟡 (hardcoded sample) |
| `chat_deregister` | ⬜ |
| `chat_spawn_agent` | ⬜ |
| `chat_enqueue_task` | ⬜ |
| `chat_cancel_task` | ⬜ |
| `chat_queue_status` | ⬜ |
| `chat_reset_project` | ⬜ |
| `chat_confirm_reset` | ⬜ |
| `chat_commit_project` | ⬜ |
| `chat_where_am_i` | ⬜ |

Net: **0 of 14 MCP tools are wired**.

## 5. Things the App Has That the Backend Does Not Need

- **Client-side conversation grouping** (`ConversationState.WaitingOnAgent`, `WaitingOnUser`, `Archived`, `Failed`, `isPinned`) — the backend models tasks and messages, not conversations. This is the app-side reconstruction the plan calls out in `docs/android-app-plan.md:103` and Phase C. Not a gap against the backend; a product decision that needs backend session IDs later.
- **Filter chips** (All/Active/Waiting/Archived) — purely presentational.
- **Composer "mode" toggle** (Reply vs. Start new) — enforces intentional new conversations the backend does not model.
- **Monospace user-message styling** — cosmetic reinforcement of "commands".

## 6. Things the Backend Offers That the App Does Not Surface

Ranked by user-visible leverage:

1. **Task queue control** — enqueue / cancel / drain / priority / queue status across projects. The backend's core value.
2. **Cross-project "where am I" dashboard** — single-tap visibility of every worker, running task, pending count, last activity.
3. **Reset flow with confirm token** — destructive operation that genuinely needs a mobile-quality two-step UI.
4. **Commit escape hatch** — dirty-repo unblock with a plain-language commit message field.
5. **Per-task branch surface** — planned branch name + clean-checkout errors shown on the conversation so the user understands why a task is blocked.
6. **Agent lifecycle** — spawn a new agent against a project path; see running/idle/disconnected; deregister.
7. **Blocking `chat_ask` UX** — inbound agent questions should render differently from `notify` and offer a first-class reply affordance.
8. **Email auth plumbing** — shared-secret entry in Settings, GPG later; today the Settings screen has no inputs at all.
9. **Message-ID / threading fidelity** — the backend threads by `In-Reply-To`; the app has no thread reconstruction.
10. **50KB truncation + quoted-reply handling** — mobile should render "[truncated]" cleanly and strip quoted history from inbound mail.

## 7. Recommended Next Steps (not prescriptive)

These follow the phase plan in `docs/android-app-plan.md` and close the gaps above.

1. Add `INTERNET` + `POST_NOTIFICATIONS` to `AndroidManifest.xml`.
2. Add dependencies: JavaMail (or Jakarta Mail), Room, DataStore, Keystore helpers, WorkManager, coroutines.
3. Build the **setup flow** (§4.1 of the plan) so IMAP/SMTP/service-address/shared-secret are captured and validated.
4. Wire **SMTP send** + **IMAP sync** behind interfaces, with WorkManager expedited sync on send.
5. Introduce a **Projects** layer alongside Agents; back the queue/reset/commit/where-am-i flows.
6. Surface **task state** (pending/running/done/failed/cancelled + branch name) inside conversation cards and the conversation header.
7. Render **`ask` vs. `notify`** distinctly; add a "Answer" affordance for `ask`.
8. Implement **reset + confirm-reset** as a two-screen destructive flow with the 5-minute token countdown visible.
9. Add **commit escape hatch** as a menu item inside a conversation/project view.
10. Implement **Diagnostics** off real last-sync timestamps, IMAP/SMTP probe results, and `chat_where_am_i` roll-up.

## 8. Key File References

Backend:
- `src/poller.py`, `src/mailer.py`, `src/security.py`, `src/chat_router.py`
- `src/task_queue.py`, `src/project_worker.py`, `src/worker_manager.py`, `src/git_ops.py`
- `src/reset_control.py`, `src/chat_schema.py`, `src/chat_handlers.py`
- `chat/tools.py`, `chat/tool_definitions.py`, `chat/project_tools.py`, `chat/project_tool_defs.py`, `chat/server.py`

App:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/cocode/claudeemailapp/MainActivity.kt`
- `app/src/main/java/com/cocode/claudeemailapp/prototype/PrototypeApp.kt`
- `app/src/main/java/com/cocode/claudeemailapp/prototype/PrototypeModels.kt`
- `app/src/main/java/com/cocode/claudeemailapp/prototype/PrototypeState.kt`
- `app/build.gradle.kts`
- `docs/README.md`, `docs/android-app-plan.md`, `docs/ux-ui-spec.md`
