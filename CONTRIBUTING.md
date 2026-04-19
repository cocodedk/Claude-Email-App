# Contributing to Claude-Email-App

## Local Setup

1. Install Android Studio (Ladybug or newer) with SDK 36 and command-line tools.
2. Install JDK 17 (Temurin recommended).
3. Clone the repo and open it in Android Studio — Gradle will sync automatically.
4. (Optional, for integration tests) Copy `.env.example` to `.env` and fill in IMAP/SMTP credentials.

## Install Git Hooks

```
./scripts/install-hooks.sh
```

This wires up the pre-commit (runs `buildSmoke`) and commit-msg (enforces Conventional Commits) hooks.

## Recommended Local Git Config

Run these once after cloning:

```bash
git config pull.rebase true
git config core.autocrlf input        # on Windows: true
git config push.autoSetupRemote true
git config init.defaultBranch main
```

## Build and Test Commands

```bash
./gradlew buildSmoke --no-daemon                 # Full verification (CI runs this)
./gradlew :app:assembleDebug --no-daemon         # Debug APK
./gradlew :app:testDebugUnitTest --no-daemon     # Unit tests
./gradlew :app:lintDebug --no-daemon             # Lint
./gradlew :app:connectedDebugAndroidTest         # Instrumentation tests (device/emulator)
```

## Coding Style

- Kotlin official code style (default Android Studio settings)
- Files ≤ 200 lines where feasible — extract helpers, not god-files
- Composables are pure; side effects go in `LaunchedEffect` / `DisposableEffect`
- Immutable data models; use `.copy()` for mutations

## Branch Naming

Branch names use kebab-case. Prefix must match the Conventional Commit type of the PR.

| Prefix | Commit type | Example |
|---|---|---|
| `feature/` | `feat:` | `feature/add-draft-folder` |
| `fix/` | `fix:` | `fix/keyboard-overlap-on-setup` |
| `chore/` | `chore:` | `chore/update-dependencies` |
| `docs/` | `docs:` | `docs/update-readme` |
| `refactor/` | `refactor:` | `refactor/extract-envelope-builder` |
| `test/` | `test:` | `test/add-probe-coverage` |
| `ci/` | `ci:` | `ci/pin-action-shas` |

Never commit directly to `master` — always open a PR.

## PR Checklist

- [ ] `./gradlew buildSmoke --no-daemon` passes
- [ ] Manual test completed for the changed functionality on a real device or emulator
- [ ] No regressions in adjacent features (setup, home, compose, settings)
- [ ] Docs updated if behaviour changed
- [ ] Conventional Commits used for every commit in the branch
