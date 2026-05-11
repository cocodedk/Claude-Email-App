# Agent View Positioning — Android Mirror

**Branch:** `docs/agent-view-positioning`
**Peer counterpart:** `claude-email` repo, same branch name, same date.
**Goal:** Mirror the peer's positioning of `claude-email` against Claude Code's newly announced built-in [Agent View](https://claude.com/blog/agent-view-in-claude-code), but framed from the **Android client's** point of view — so users understand what the phone app gives them that the terminal Agent View does not.

## Framing (three bullets, lockstep with peer)

- **Remote-first.** The phone is the cockpit when you are away from the laptop. Agent View is local to one machine; an email thread is not.
- **Inter-agent bus.** The app sends commands that route through `claude-email` to agents that talk to *each other* (MCP chat bus, `chat_message_agent`). Agent View has no agent-to-agent channel.
- **Persistent, multi-surface state.** Conversations, task history, and liveness survive reboots and bus restarts. The Android app, the dashboard, and the original email thread are three windows onto the same state.

One-liner: *Agent View is the cockpit when you're at the laptop; this app is the radio when you're not.*

## Out of scope (deferred, peer-coordinated)

- Dashboard status-vocab alignment (`waiting | working | completed`) — peer parked it; we wait.
- Any code change to the protocol, transport, or UI flow.

## Files touched (docs-only)

| File | Change |
|------|--------|
| `README.md` | New `## Compared to Claude Code's Agent View` section between the lede paragraph and `## Website`. |
| `website/index.html` | New `<section class="section" id="positioning">` between Features and Install. |
| `website/fa/index.html` | Persian mirror of the same section, RTL-compatible. |
| `app/src/main/res/values/strings.xml` | Tighten onboarding panel 1 (Remote-first emphasis) and panel 2 (multi-surface emphasis). Three-panel structure unchanged. |

No new files. No CSS additions — reuse `.section`, `.container`, `.section-head`, `.eyebrow`, `.section-title`, `.section-lede`, `.features`/`.feature` classes already styled.

## Verification

- `./gradlew :app:lintDebug --no-daemon` (strings.xml well-formed, no missing refs)
- Open both `website/index.html` and `website/fa/index.html` locally; visually confirm the new section sits between Features and Install and renders in both LTR and RTL.
- Re-read README top-to-bottom; confirm the new section reads as continuous prose with the surrounding lede + Website + Download blocks.

## Cadence (peer agreement)

After each commit: run `/simplify` on the diff, ping `agent-claude-email` with a short status line, then `/compact`. Same on the peer side.
