# CLAUDE.md — Claude-Email-App

## Project Overview

Android email client for the claude-email backend service. Receives over IMAP, sends over SMTP, stores credentials encrypted with the Android Keystore, and presents a focused command-and-reply UX instead of a traditional inbox.

- **Language / Runtime**: Kotlin 2.2.10, JVM target 11, Android minSdk 24 / targetSdk 36
- **Framework**: Jetpack Compose + Material 3
- **Architecture**: Layered (app UI → data → mail/protocol), MVVM within the UI layer
- **Package / Namespace**: `com.cocode.claudeemailapp`

---

## Required Skills — ALWAYS Invoke These

| Situation | Skill |
|-----------|-------|
| Before any new feature or screen | `superpowers:brainstorming` |
| Planning multi-step changes | `superpowers:writing-plans` |
| Writing or fixing core logic | `superpowers:test-driven-development` |
| First sign of a bug or failure | `superpowers:systematic-debugging` |
| Before completing a feature branch | `superpowers:requesting-code-review` |
| Before claiming any task done | `superpowers:verification-before-completion` |
| Working on UI / Compose screens | `frontend-design:frontend-design` |
| After implementing — reviewing quality | `simplify` |

---

## Architecture

The codebase is organised under `app/src/main/java/com/cocode/claudeemailapp/`:

```
com/cocode/claudeemailapp/
├── MainActivity.kt      — Single activity host for the Compose app
├── app/                 — Compose screens, ViewModels, UI shell (AppRoot, navigation)
├── data/                — Encrypted credential storage, pending-command queue, models
├── mail/                — IMAP/SMTP adapters over Angus Mail
├── protocol/            — Envelope builders for the claude-email wire format
└── ui/theme/            — Color, typography, and shape tokens for Material 3
```

### Layer Rules
- `mail/` and `protocol/` must never depend on `app/` (UI layer)
- `data/` exposes immutable models; mutations happen via explicit copy/store operations
- Credentials never leave `data/` in plaintext — always via `EncryptedCredentialsStore`
- All screens under `app/` are Composables; ViewModels live alongside and expose `StateFlow`

---

## Coding Conventions

- [ ] All models are **immutable** — use `.copy()` for mutations
- [ ] Composables are **pure** — no hidden side effects; use `LaunchedEffect`/`DisposableEffect`
- [ ] State is a single source of truth per feature (`StateFlow` in the ViewModel)
- [ ] No hardcoded strings in UI — use `strings.xml`
- [ ] Explicit typing; no `Any` in public signatures

---

## Engineering Principles

### File Size
- **200-line maximum per file** — extract helpers/components when approaching the limit

### DRY · SOLID · KISS · YAGNI
- Extract shared logic into named utilities
- Single Responsibility: one class/function does one thing
- Don't add features not yet needed
- Delete dead code immediately

### TDD
- Write the failing test first, make it pass, then refactor
- Unit tests use Robolectric + MockK; instrumentation tests use Compose UI Test
- One assertion per test when feasible

### Commit hygiene
- Follow Conventional Commits: `feat: ...` / `fix: ...` / `chore: ...` / `docs: ...` / `refactor: ...` / `test: ...` / `ci: ...` / `build: ...` / `perf: ...` / `revert: ...`
- The `commit-msg` hook enforces this automatically after `./scripts/install-hooks.sh`

---

## Build Commands

```bash
./gradlew :app:assembleDebug --no-daemon       # Debug build
./gradlew :app:testDebugUnitTest --no-daemon   # Unit tests
./gradlew :app:lintDebug --no-daemon           # Lint
./gradlew buildSmoke --no-daemon               # Full smoke (build + tests + lint)
./gradlew :app:connectedDebugAndroidTest       # Instrumentation tests (needs device/emulator + .env)
```

Integration tests read real IMAP/SMTP credentials from `.env` (git-ignored). Copy `.env.example` to `.env` and fill in values to run them.

---

## Key Files

| File | Purpose |
|------|---------|
| `CLAUDE.md` | This file |
| `.env.example` | Template for integration-test credentials |
| `.github/workflows/ci.yml` | PR + branch verification |
| `.github/workflows/release-apk.yml` | Signed APK build + GitHub Release (workflow_dispatch) |
| `.github/workflows/deploy-pages.yml` | GitHub Pages deploy (on master push to `website/**`) |
| `.githooks/pre-commit` | Local buildSmoke gate |
| `.githooks/commit-msg` | Conventional Commits enforcement |
| `scripts/install-hooks.sh` | One-time hook installer |
| `scripts/setup-signing.sh` | Generates/uploads release keystore secrets |
| `scripts/setup-repo.sh` | Applies branch protection + merge settings |

---

## Starting a New Session

1. Read this file
2. Run `./gradlew buildSmoke --no-daemon` to confirm everything passes
3. Invoke `superpowers:brainstorming` before touching any feature
4. Follow the Required Skills table — every skill is mandatory, not optional
