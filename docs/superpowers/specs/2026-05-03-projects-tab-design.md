# Projects tab

**Status:** v1 in progress (Tier 1 only); Tier 2 + 3 deferred to follow-ups
**Date:** 2026-05-03
**Author:** agent-Claude-Email-App (with peer agent-claude-email)

## Goal

Surface a discoverable list of projects under the configured backend `allowed_base`, with per-project agent state. From this list the user can dispatch new commands or (in later tiers) inspect / control / git-operate on each project, without first having to remember the project path.

## Tier breakdown

The user described five capabilities. They split cleanly into three shippable tiers:

| Tier | Capability | Wire ops needed |
|---|---|---|
| **Tier 1 (v1)** | List projects + per-row "active agent" / queue badge; tap → conversation or new command | New: `list_projects` |
| Tier 2 (v2)    | Per-project Status + Cancel buttons | Reuse: existing `status` / `cancel` (project-scoped) |
| Tier 3 (v3)    | Per-project `git status` / `git commit` / `git push` / `make pr` | New backend kinds: `git_status`, `git_push`, `git_make_pr`; existing `commit` is currently a stub |

This document specifies all three so the wire format and UI shape stay consistent across phases. **Implementation in this PR covers Tier 1 only.**

## Tier 1 — Read-only Projects tab

### UX

A new bottom-of-the-app tab "Projects" sits alongside the existing Home/Settings affordances. (Existing nav is screen-based via `Screen` enum + `Crossfade` in `AppRoot.kt`; Projects becomes a new `Screen.Projects` value reachable from the Home screen's quick-action row, mirroring how `Settings` / `New command` / `Refresh` are already surfaced.)

**Row layout per project:**

```
┌─────────────────────────────────────────────────┐
│ [folder icon]  claude-email                     │
│                /home/cocodedk/.../claude-email  │
│                ● running task #42 · 2 queued    │
└─────────────────────────────────────────────────┘
```

- **Top line:** project name (basename of path), monospace.
- **Second line:** absolute path, dimmed, single-line ellipsized.
- **Status pill:** uses the same `ChipPill` primitive already in `UiPrimitives.kt`. States:
  - `idle` — outline color, no badge.
  - `queued N` — secondary color, where N = `queue_depth`.
  - `running task #N` — primary color, bold-ish; shows the running task id.
- **Last activity timestamp** in the top-right corner, formatted by the existing `formatTimestamp` helper.

**Tap behavior:**
- If a conversation tagged to that project exists locally → open `Screen.Conversation` for the most recent one.
- Else → open `Screen.Compose` with the project field pre-filled.

**Empty state:**
"No projects discovered yet. Send a command from Compose to get started." (One-line + button to Compose.)

**Refresh:** pull-to-refresh OR a Refresh icon in the top bar — pulls a fresh `list_projects` envelope.

### Data flow

1. App opens `Screen.Projects` for the first time → `ProjectsViewModel.refresh()` → `MailSender.sendCommand(kind=list_projects, …)`.
2. Backend replies with an `ack` envelope carrying `data.projects: [{ name, path, running_task_id, queue_depth, last_activity_at }]`.
3. App stores the parsed list as `StateFlow<List<ProjectSummary>>`.
4. The Projects screen `collectAsState`s and renders. Refresh re-fires the same kind.

### Wire format addition (Tier 1)

**Inbound (app → backend), kind=`list_projects`:**

```jsonc
{
  "v": 1,
  "kind": "list_projects",
  "meta": {
    "client": "cocode-android/1.0",
    "ask_id": "<uuid>",      // echoed back per existing convention
    "auth": "<shared_secret>"
  }
}
```

No `body` / `data` / `project` fields needed — it's a query, not an action.

**Outbound (backend → app), kind=`ack`:**

```jsonc
{
  "v": 1,
  "kind": "ack",
  "meta": { "ask_id": "<echoed>" },
  "data": {
    "projects": [
      {
        "name": "claude-email",
        "path": "/home/cocodedk/0-projects/claude-email",
        "running_task_id": 42,           // null when idle
        "queue_depth": 2,                // 0 when no queued tasks
        "last_activity_at": "2026-05-03T09:24:00Z"  // ISO 8601, nullable
      },
      // ...
    ]
  }
}
```

**Error path** (backend can't enumerate, e.g. `allowed_base` misconfigured):

```jsonc
{ "v": 1, "kind": "error", "meta": {...}, "error": { "code": "internal", "message": "…", "hint": "…" } }
```

App falls back to the existing `EnvelopeErrorBanner` rendering.

### Files added (Tier 1)

| File | Lines (est) | Layer | Responsibility |
|---|---|---|---|
| `data/ProjectSummary.kt`                                            | ~15  | data | Immutable model: `name, path, runningTaskId, queueDepth, lastActivityAt` |
| `protocol/ListProjectsResponse.kt` (or extend `Envelope.kt`)        | ~25  | protocol | `@Serializable` typed view of `data.projects` |
| `app/ProjectsViewModel.kt` (or methods on existing `AppViewModel`)  | ~80  | app | `refresh()`, `projects: StateFlow<List<ProjectSummary>>`, error/loading state |
| `app/ProjectsScreen.kt`                                             | ~150 | app | Compose surface: rows, status pills, tap-routing |
| `app/ProjectRow.kt`                                                 | ~80  | app | Single-row composable extracted to keep file size in check |

### Files modified (Tier 1)

| File | Change |
|---|---|
| `app/AppRoot.kt` | Add `Screen.Projects` enum value; route in `AppScreenContent`; add a "Projects" quick-action button in `HomeScreen`; pass `recentConversationByProject` map for tap-routing |
| `app/HomeScreen.kt` | Add a "Projects" button next to "New command" / "Settings" |
| `app/AppViewModel.kt` | Expose `projectsState: StateFlow<ProjectsState>` + `refreshProjects()` + tap-routing helper that resolves project → most recent conversation id |
| `protocol/EnvelopeBuilders.kt` | New builder for `kind=list_projects` envelope |
| `protocol/Envelope.kt` (`Kinds` object) | Add `LIST_PROJECTS = "list_projects"` |

### Tests (Tier 1)

- `ProjectsViewModelTest`: refresh sets loading true → fetcher returns → state updates with parsed projects; error path sets error message.
- `ListProjectsEnvelopeBuilderTest`: produces correct `kind`, `meta.ask_id`, no spurious fields.
- `Envelope` deserialization test: a sample `ack` payload with `data.projects` deserializes into the expected `List<ProjectSummary>`.
- (No Compose UI tests for v1 — keep test surface tight.)

## Tier 2 (deferred) — Per-project Status + Cancel from row

Each row gains a small overflow menu (or long-press) with **Status** / **Cancel** actions. Both reuse the kinds your peer already shipped (commit `bf63bbc` / 11:26 CEST):
- `status` → ack with `data: {running, pending}` — render in a bottom sheet.
- `cancel` (with `drain_queue=true` opt-in) → ack with `data: {status, task_id, drained}` — toast "1 task cancelled, 3 drained".

No new envelope kinds. New UI: `ProjectActionsSheet` composable.

## Tier 3 (deferred) — Per-project git ops

Adds four new envelope kinds:

- `git_status` → returns `data: { branch, ahead, behind, dirty: bool, untracked: [str], modified: [str] }`
- `git_commit` (existing stub on backend; needs body to ship) → returns `data: { committed_sha, files_changed, lines_added, lines_removed }`
- `git_push` → returns `data: { remote_branch, commits_pushed, hash }`
- `git_make_pr` → returns `data: { pr_url, pr_number, base, head }`

UI: extends Tier 2's actions sheet with a "Git" subsection. Confirmations required for `commit` / `push` / `make_pr` (destructive-ish, surfaced via existing snackbar pattern).

## Error handling (all tiers)

Reuses the existing chain:

- Transport failure → `StatusCard` ("Send failed: <reason>") via `_send.lastError`.
- Envelope error (`kind=error`) → `EnvelopeErrorBanner` with `meta.error.{code,message,hint}` rendered verbatim, plus the discriminated `agent error`/`no project`/`auth`/etc chip from PR #40.
- No `kind=ack` reply within ~30s → existing `PendingCommand` stalled flow already handles this.

## Migration / impact

None. Additive. Existing screens unchanged in behavior; the new tab is opt-in via the new quick-action button.

## Out of scope

- Multi-account / multi-backend project lists (app is single-account).
- Inline file browser for each project.
- Diff viewer for `git status`.
- Background refresh of the projects list — pull-to-refresh is enough for v1.
- Notifications when a new project is discovered.
