# Claude Email

![CI](https://github.com/cocodedk/Claude-Email-App/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

Claude Email lets you send commands to AI agents and read their replies on Android. It uses your own email account, and it needs a running [claude-email](https://github.com/cocodedk/claude-email) service. The service is the program that receives your commands, has an agent (a helper program) do the work in the project you name, and replies when it is done.

## Website

- English: [cocodedk.github.io/Claude-Email-App](https://cocodedk.github.io/Claude-Email-App/)
- Danish (Dansk): [cocodedk.github.io/Claude-Email-App/da](https://cocodedk.github.io/Claude-Email-App/da/)
- Persian (فارسی): [cocodedk.github.io/Claude-Email-App/fa](https://cocodedk.github.io/Claude-Email-App/fa/)

## Download

<!-- cocode-apps:install:start -->
- Coming to F-Droid
- [Download the Android installation file (APK) from GitHub](https://github.com/cocodedk/Claude-Email-App/releases/latest/download/Claude-Email-App.apk)
- [Add the app to Obtainium, an app that keeps it up to date](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cocodedk/Claude-Email-App)
<!-- cocode-apps:install:end -->

Requires Android 7.0 or newer.

## Features

- **Your own mailbox**: mail is received over IMAP and sent over SMTP, the standard ways a mail app talks to a mail provider (through Angus Mail). No relay of ours is involved.
- **Sign-in details stored encrypted**: your mail password and the shared secret are stored encrypted on the phone, with a key kept in the Android Keystore, never in plain preferences.
- **Commands and replies as conversations**: send a command, then read the agent's replies, grouped into conversations.
- **Shared secret**: commands carry the shared secret you set in the app, for the claude-email service to check.
- **Notifications**: a notification arrives when a new message comes in and the app is not open on screen. They are on by default. A background service keeps an IMAP IDLE connection open (a standard way for a mail server to announce new mail) so they work while the app is in the background. To stop all alerts at once, turn off this app's notifications in Android settings.
- **Quick-reply chips**: when an agent asks a question, the app shows suggested answers above the composer; one tap sends the answer.
- **Live progress**: progress messages from a task show as a label and, when the service reports how far along the task is, a progress bar.
- **Projects view**: the projects the claude-email service reports, with agent and task status; tap one to start a command for it.
- **Dark theme**: Material 3 with a dark palette.
- **English and Danish**: the app follows your phone's language.

## Privacy

Cocode receives nothing from this app: it has no account, no ads, no analytics and no crash reporting. Your mail password and the shared secret are stored encrypted on the phone, with a key kept in the Android Keystore. The app signs in to your own mail servers, and the password goes only to them. Your commands leave the phone as ordinary email to the service address you enter, with the shared secret if you set one. Notifications are on by default; to stop all alerts at once, turn off this app's notifications in Android settings. Android may include the app's data in its backups. Read the full [privacy policy](https://cocodedk.github.io/Claude-Email-App/privacy/).

## Build

Prerequisites:

- JDK 21 (Gradle is set up for it and downloads it when it is missing)
- Android SDK platform 36.1

Clone and build:

```bash
git clone https://github.com/cocodedk/Claude-Email-App.git
cd Claude-Email-App
./gradlew buildSmoke --no-daemon
```

Install a debug build on a connected device:

```bash
./gradlew :app:installDebug --no-daemon
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
