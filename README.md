# Claude Email

![CI](https://github.com/cocodedk/Claude-Email-App/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

Android email client for the `claude-email` backend. Claude Email pairs a standard IMAP/SMTP transport with Android Keystore credential storage and a command-and-reply interaction model built for operators who send structured requests and wait for structured answers. It is not a general-purpose inbox: it targets focused workflows where envelopes flow between trusted peers and the UI stays out of the way.

## Website

- English: [cocodedk.github.io/Claude-Email-App](https://cocodedk.github.io/Claude-Email-App/)
- Persian (فارسی): [cocodedk.github.io/Claude-Email-App/fa](https://cocodedk.github.io/Claude-Email-App/fa/)

## Download

<!-- cocode-apps:install:start -->
- Coming to F-Droid
- [Download the APK from GitHub](https://github.com/cocodedk/Claude-Email-App/releases/latest/download/Claude-Email-App.apk)
- [Auto-update the GitHub APK with Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cocodedk/Claude-Email-App)
<!-- cocode-apps:install:end -->

Install on any Android device running API 24 or newer.

## Features

- **IMAP/SMTP transport** — standards-based mail delivery powered by Angus Mail, no proprietary relay required.
- **Android Keystore credentials** — account secrets are encrypted at rest by the platform keystore, never in plain preferences.
- **Command-and-reply UX** — the UI is built around sending a command and reading the agent's replies, grouped into conversations.
- **Shared-secret auth** — commands carry the shared secret you set in the app, for the claude-email service to check.
- **Inbox notifications** — device notifications for incoming replies, on by default and switchable under Settings. A foreground IMAP IDLE service keeps a connection open so they work in the background.
- **Quick-reply chips** — when an agent asks a question, the app surfaces backend-suggested chips above the composer; one tap auto-sends the answer.
- **Live progress** — `kind=progress` envelopes render in the conversation as a label and, when the task reports a fraction, a progress bar.
- **Projects view** — the projects the claude-email service reports, with agent and task status; tap one to start a command for it.
- **Dark-first UI** — Material 3 with a dark palette and typography tuned for focused operator work.

## Privacy

Your mail password and the shared secret are stored encrypted on the device (Android Keystore), never in plain preferences. The password is sent only to your own mail server, to sign in. The shared secret is included in the commands you send to the claude-email service. Reply notifications are on by default and can be switched off in Settings.

## Build

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
├── data/        Encrypted credential storage + tracking of sent commands
├── mail/        IMAP/SMTP (Angus Mail)
├── protocol/    claude-email envelope builders
└── ui/theme/    Colour + typography tokens
```

| Area            | Choice                                        |
| --------------- | --------------------------------------------- |
| Language        | Kotlin 2.3.21                                 |
| UI              | Jetpack Compose + Material 3                  |
| Mail transport  | Angus Mail (IMAP + SMTP)                      |
| Storage         | Android Keystore + EncryptedSharedPreferences |
| Testing         | JUnit + AndroidX instrumented tests           |

## Contributing

- [`CLAUDE.md`](CLAUDE.md) — project guidance for Claude Code sessions.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contribution workflow, branch and commit conventions.
- Install the local git hooks with `./scripts/install-hooks.sh`.

## Author

**Babak Bandpey** — [cocode.dk](https://cocode.dk) | [LinkedIn](https://linkedin.com/in/babakbandpey) | [GitHub](https://github.com/cocodedk)

## License

Apache-2.0 | © 2026 [Cocode](https://cocode.dk) | Created by [Babak Bandpey](https://linkedin.com/in/babakbandpey)
