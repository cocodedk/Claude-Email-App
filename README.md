# Claude Email

![CI](https://github.com/cocodedk/Claude-Email-App/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

Android email client for the `claude-email` backend. Claude Email pairs a standard IMAP/SMTP transport with Android Keystore credential storage and a command-and-reply interaction model built for operators who send structured requests and wait for structured answers. It is not a general-purpose inbox: it targets focused workflows where envelopes flow between trusted peers, credentials stay on-device, and the UI stays out of the way.

## Website

- English: [cocodedk.github.io/Claude-Email-App](https://cocodedk.github.io/Claude-Email-App/)
- Persian (فارسی): [cocodedk.github.io/Claude-Email-App/fa](https://cocodedk.github.io/Claude-Email-App/fa/)

## Download

**[Download the latest APK](https://github.com/cocodedk/Claude-Email-App/releases/latest/download/Claude-Email-App.apk)**

Install on any Android device running API 24 or newer.

## Features

- **IMAP/SMTP transport** — standards-based mail delivery powered by Angus Mail, no proprietary relay required.
- **Android Keystore credentials** — account secrets are encrypted at rest by the platform keystore, never in plain preferences.
- **Command-and-reply UX** — the UI is built around sending an envelope and reading the structured response, not browsing threads.
- **Shared-secret auth** — envelopes are authenticated end-to-end with a shared secret in addition to transport security.
- **Offline queue** — outbound envelopes are persisted and retried when connectivity returns.
- **Dark-first UI** — Material 3 with a dark-first palette and typography tuned for focused operator work.

## Build from Source

Prerequisites:

- JDK 17
- Android SDK, platform 36

Clone and build:

```bash
git clone https://github.com/cocodedk/Claude-Email-App.git
cd Claude-Email-App
./gradlew buildSmoke --no-daemon
```

Install a debug build on a connected device:

```bash
./gradlew :app:assembleDebug --no-daemon
```

For integration tests that hit a real IMAP/SMTP account, copy `.env.example` to `.env` and fill in the values before running the instrumented suite.

## Architecture

```text
app/src/main/java/com/cocode/claudeemailapp/
├── MainActivity.kt
├── app/         Compose screens + ViewModels
├── data/        Encrypted credential storage + pending queue
├── mail/        IMAP/SMTP (Angus Mail)
├── protocol/    claude-email envelope builders
└── ui/theme/    Colour + typography tokens
```

| Area            | Choice                                  |
| --------------- | --------------------------------------- |
| Language        | Kotlin 2.2.10                           |
| UI              | Jetpack Compose + Material 3            |
| Mail transport  | Angus Mail (IMAP + SMTP)                |
| Storage         | Android Keystore + encrypted datastore  |
| Testing         | JUnit + AndroidX instrumented tests     |

## Development

- [`CLAUDE.md`](CLAUDE.md) — project guidance for Claude Code sessions.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contribution workflow, branch and commit conventions.
- Install the local git hooks with `./scripts/install-hooks.sh`.

## Author

**Babak Bandpey** — [cocode.dk](https://cocode.dk) | [LinkedIn](https://linkedin.com/in/babakbandpey) | [GitHub](https://github.com/cocodedk)

## License

Apache-2.0 | © 2026 [Cocode](https://cocode.dk) | Created by [Babak Bandpey](https://linkedin.com/in/babakbandpey)
