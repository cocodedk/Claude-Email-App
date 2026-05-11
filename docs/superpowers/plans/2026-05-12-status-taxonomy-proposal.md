# Status Taxonomy Proposal — Agent View Alignment

**Branch:** `docs/agent-view-positioning` (continuation; same docs branch).
**Peer ask:** From `agent-claude-email`'s top-5 (#2): align Agent View's task-state vocabulary (`waiting | working | completed`) with the backend's process-state vocabulary (`running | idle | disconnected | deregistered`). Proposal-only — backend spec follows.

## Diagnosis: two axes, not one

The current wire collapses two distinct things into a single `agent_status` field:

| Axis | Question it answers | Current wire | Source of truth |
|------|---------------------|--------------|-----------------|
| Process state | "Is anyone home on the bus?" | `agent_status: connected \| disconnected \| absent` | claude-chat presence |
| Task state | "What is the running task doing?" | derived client-side from `running_task_id` + `queue_depth` | claude-email tasks table |

`ProjectStatePill` (`app/src/main/java/.../app/ProjectsScreen.kt:159`) renders one pill by priority: agent connected → running task #N → queued N → idle. Readable, but it hides whichever signal lost the priority race.

Agent View's `waiting | working | completed` is a pure task-state vocabulary. It has no process-state axis.

## Proposal: keep them separate

### Process-state vocabulary (replaces `AgentStatusValues`)

Per project, per agent — matches the dashboard's existing ghost-filter so liveness reads identically across surfaces.

| Value | Meaning | Replaces |
|-------|---------|----------|
| `online` | Bus presence is fresh (`last_seen_at` within heartbeat window). | `connected` |
| `stale` | Bus presence is older than heartbeat window but younger than ghost threshold; PreCompact gaps and bus restarts land here briefly. UI renders neutrally, **no alert**. | (new) |
| `offline` | Beyond ghost threshold, or `deregistered`. UI renders dim, no alert. | `disconnected` + `absent` |

Three buckets, all set server-side. Client never computes presence — it just renders. Matches peer's #5 ("don't fire a stopped alert").

### Task-state vocabulary (new; aligns to Agent View)

Per running or just-finished task. Echoes Agent View one-for-one.

| Value | Meaning |
|-------|---------|
| `waiting` | Queued behind another task, **or** blocked on a question (`chat_ask`). |
| `working` | Active; an agent is processing it. |
| `completed` | Finished — terminal. Visible briefly on the projects list, persisted in conversation history. |

### Wire shape

Two minimal additions to `ProjectSummary` (`data/ProjectSummary.kt`):

```kotlin
@Serializable
data class ProjectSummary(
    val name: String,
    val path: String,
    @SerialName("running_task_id") val runningTaskId: Long? = null,
    @SerialName("queue_depth") val queueDepth: Int = 0,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("agent_status") val agentStatus: String? = null,   // now: online | stale | offline
    @SerialName("task_state")   val taskState: String? = null,     // NEW: waiting | working | completed | null
)
```

`task_state` is nullable: `null` means no task is currently of interest (clean idle). Backend sets it to the state of the most relevant task — typically the one referenced by `running_task_id`, or the most recent `completed` within a short fade window.

Gated on envelope `v >= 2` so plain-text consumers keep working.

### UI rendering

`ProjectStatePill` becomes two pills side-by-side, both server-driven:

```
┌──────────────┐  ┌──────────────────┐
│ ● online     │  │ working · #42    │
└──────────────┘  └──────────────────┘
```

Color tokens:
- `online` → tertiary (positive); `stale` → outline (neutral); `offline` → outlineVariant (dim).
- `working` → primary; `waiting` → secondary; `completed` → tertiary container (fades after a few seconds).

Sort order on the projects list: `working` first, then `waiting`, then `online & no task`, then `stale`, then `offline`. Mirrors what a user scanning the list cares about.

## Files this proposal would change later (not now)

1. `app/src/main/java/com/cocode/claudeemailapp/protocol/Envelope.kt` — extend `AgentStatusValues`, add `TaskStateValues` object.
2. `app/src/main/java/com/cocode/claudeemailapp/data/ProjectSummary.kt` — add `taskState` field.
3. `app/src/main/java/com/cocode/claudeemailapp/app/ProjectsScreen.kt` — split `ProjectStatePill` into two pills, update sort/order.
4. `app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt` — update the `agentStatus == CONNECTED` sort hint at line 303 to `agentStatus == ONLINE`, similarly for the `routedVia` check at line 381.

Behind the `v >= 2` flag at the parser level, so `v: 1` responses keep working.

## Open questions for the peer

1. Heartbeat window vs ghost threshold values — what does the dashboard currently use? Mirror those.
2. For `completed` — how long does it stay visible on `task_state` before flipping to `null`? (Suggest: 30 s, configurable server-side.)
3. Is there a use case for `error` as a fourth task-state value, or should errors stay on the conversation envelope and out of the projects-list shape?

## Out of scope

- Animated transitions between states (UI polish, defer).
- Per-agent multi-task visibility (Agent View shows a column per session; the Android list collapses to one per project — fine for the small-screen form factor).

## Implementation order, once spec is locked

1. Backend ships `task_state` on `list_projects` ack at `v: 2`.
2. App bumps `Envelope.v` parser tolerance, adds `TaskStateValues`, surfaces both pills.
3. Dashboard adopts the same shared vocabulary so all three surfaces (dashboard, app, Agent View) read the same.
