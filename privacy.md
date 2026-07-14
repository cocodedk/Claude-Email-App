# Privacy Policy — Claude Email App

**App:** Claude Email App (`com.cocode.claudeemailapp`)
**Developer:** CoCode.dk — Babak Bandpey
**Last updated:** 14 July 2026

> The canonical, always-current version of this policy is published at
> **https://cocodedk.github.io/Claude-Email-App/privacy.html**

**Claude Email App is an Android email client. It runs on your device, stores your account
credentials locally in encrypted form, and connects directly to the email servers you configure.
There is no server operated by the developer as part of this app. The commands and messages you send
are transmitted as ordinary email to a `claude-email` agent backend whose address you choose — and
because that backend is built on Anthropic's Claude, the content you send may be processed by
Anthropic and is subject to Anthropic's privacy policy.**

## What the app is

Claude Email App is a focused IMAP/SMTP email client. You point it at an email account you already
control, plus the email address of a `claude-email` agent backend. You then send structured
"commands" to that backend and read its replies inside the app. The app does not create an account
for you, and it does not route your mail through any infrastructure operated by the developer.

## Information you provide, and where it is stored

During setup you enter the details needed to reach your mailbox and the backend. This includes:

- Your display name and email address.
- Your email account **password** (used to log in to your IMAP and SMTP servers).
- Your IMAP and SMTP server host names, ports, and TLS settings.
- The **service address** — the email address of the claude-email backend you want to reach.
- An optional **shared secret** used to authenticate the commands you send to that backend.

These values are stored **only on your device**, in encrypted storage (Android
`EncryptedSharedPreferences`) protected by a hardware-backed key held in the Android Keystore. They
are never sent to the developer. If the device-bound key becomes unavailable, the stored values
cannot be decrypted and you are simply asked to set up the app again.

## Access to your email account

To do its job the app connects **directly from your device** to the email servers you configured,
over an encrypted (TLS) connection:

- It reads messages in your **inbox** in order to find and display replies from the backend agent.
  Messages that are not claude-email envelopes are ignored, but the app does read your inbox to look
  for relevant messages, and it may mark messages it has processed as read.
- It sends outgoing email through your SMTP server to the service address you configured.

The developer has no access to your mailbox, your credentials, or the contents of your email.

## What is sent to the backend and to Anthropic (Claude)

When you issue a command, the app builds a structured message (a JSON "envelope") and sends it **as an
email**, through your own SMTP server, to the service address you configured. That message contains
the **text of your command** and related details such as the target project name, priorities, and —
if you set one — your shared secret (which the app also places in the email subject line for
authentication). Replies from the backend arrive in your inbox and are displayed in the app.

The `claude-email` backend is an AI agent system built on **Anthropic's Claude**. This means the
content you send — your commands and any text in them — and the replies you receive are **processed
by that backend and may be handled by Anthropic's Claude models**. That processing is therefore
subject to [Anthropic's Privacy Policy](https://www.anthropic.com/legal/privacy), and to the
practices of whoever operates the backend you have chosen to use. Do not send information through the
app that you are not comfortable having processed this way.

Importantly, **the app itself never contacts Anthropic directly**. It holds no Anthropic API key and
makes no request to Anthropic's servers; all AI processing happens on the backend you send email to,
not inside the app.

## Notifications and background service

If you enable notifications, the app runs a foreground service that keeps a live connection to your
mailbox (IMAP IDLE) so that new replies can notify you promptly, without repeated polling. This
service connects only to the mail server you configured and shows a notification while it is active,
as Android requires. Notifications are optional and can be turned off in the app or in your device
settings.

## No analytics, no tracking, no ads

- The app contains **no analytics, no crash reporting, and no advertising**.
- There are **no third-party tracking SDKs, no cookies, and no advertising identifiers**.
- The only network connections the app makes are to the mail servers and backend address **you**
  configure.

## Device backup

If you have enabled Android Auto Backup or Google account backup, the operating system may include
this app's local data in your own personal Google backup. This is controlled entirely by you and
Google — the developer has no access to it. Your encrypted credentials are protected by a key held in
the Android Keystore that is bound to your device and is not included in backups, so those
credentials cannot be decrypted on another device. See
[Google's Privacy Policy](https://policies.google.com/privacy) for details.

## External links

The app and the website link to the developer's website ([cocode.dk](https://cocode.dk)), a GitHub
repository, and a LinkedIn profile. Opening them leaves the app; those sites are governed by their
own privacy policies.

## Children

The app is a technical tool for operators and is not directed at children. It does not knowingly
collect data from anyone, including children.

## Changes

If this policy changes, the updated version will be posted here and on the website with a new
"last updated" date.

## Contact

Questions about this policy can be sent to **bb@cocode.dk** (CoCode.dk, developer: Babak Bandpey).
