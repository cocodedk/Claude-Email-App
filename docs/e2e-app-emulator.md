# The real app on an emulator, against the real backend

`app/src/androidTest/java/com/cocode/claudeemailapp/e2e/RealMailE2ETest` drives
the shipped app on an emulator against a real mail server and a real
claude-email backend. Nothing in the mail path is mocked, injected or faked.

```
   emulator                       host (127.0.0.1)
  ┌──────────┐   TLS via 10.0.2.2  ┌──────────────┐      ┌───────────────┐
  │ the app  │────────────────────▶│ TLS terminat.│─────▶│ GreenMail     │
  │ Angus    │◀────────────────────│ (byte pump)  │◀─────│ (docker)      │
  │ Mail     │                     └──────────────┘      └───────┬───────┘
  └──────────┘                                                   │
                                                       real main.py poller
                                                       → real worker → real CLI
```

## Running it

```bash
scripts/e2e/harness.py                 # boots everything, runs the test, asserts
CLAUDE_EMAIL_REPO=/path/to/claude-email scripts/e2e/harness.py
```

The harness needs docker, `openssl`, `gpg`, an Android SDK, and a claude-email
checkout that carries `tests/e2e/_stack.py`. It reuses a running emulator and
leaves it running; if none is up it starts one `-read-only` and stops it again.

## What is real, and what is merely configured

Real: the app's `SmtpMailSender` and `ImapMailFetcher` (Angus Mail), the TLS
handshake and hostname check, every byte of SMTP and IMAP, the MIME and envelope
serialisers, the backend's authentication, its task queue, the worker process,
and the CLI process it spawns.

Configured, and only this:

* **The trust anchor.** The harness generates a certificate with `IP:10.0.2.2`
  in its SAN and the test installs it as the process's default `SSLContext`.
  `E2eTrust.assertUntrustedBySystem` first proves the platform rejects that
  certificate, which bounds what the install means: the handshake succeeds only
  *after* the install, so the app resolves `SSLSocketFactory.getDefault()` and
  did not pin the system trust store. It does **not** prove the app verifies
  anything — an app-side trust-all regression (a permissive `TrustManager`, a
  custom `SSLSocketFactory`, `mail.imaps.ssl.trust` / `mail.smtp.ssl.trust`)
  would leave that control green, because it builds its own `SSLContext` and
  never touches the app's mail stack. That the verification is armed is
  established by inspection: `SmtpMailSender.kt:69`, `ImapMailFetcher.kt:102`
  and `MailProbe.kt:60,79` set `ssl.checkserveridentity=true`, and no file under
  `app/src/main` sets `ssl.trust`, a socket factory, a `TrustManager` or a
  `HostnameVerifier`.
* **The mail server accepts any login.** GreenMail authenticates on the bare
  local part and rejects a full address; the app derives its login from the
  address it sends From. See the comment in `scripts/e2e/docker-compose.yml`.
* **The CLI is a stub.** The `claude` binary is outside the system under test.
  The stub records every invocation to an append-only ledger and prints a token.

## Three independent oracles

1. **On screen** — the ack card renders `Queued as task #N` with N minted by the
   backend's queue; the test reads N off the screen and requires the result card
   to render `Task #N done`. The test cannot supply N.
2. **On the host filesystem** — `scripts/e2e/harness.py` asserts the CLI ledger
   holds exactly one execution carrying the command the app typed.
3. **In the mailbox** — `scripts/e2e/oracle.py` reads the delivered result
   envelope over a separate plain-IMAP connection and asserts it carries the
   CLI's own stdout token. That token exists only on the host; it is never
   passed to the device, so no device-side assertion could fabricate it.

## Why it fails if the implementation is reverted

* Break the SMTP sender, the MIME/envelope serialiser or the `meta.auth` field
  and the backend never authenticates: oracle 2 finds zero executions.
* Break the IMAP fetcher, the envelope parser or the conversation rendering and
  no `Task #N done` reaches the screen: oracle 1 times out.
* Remove the harness trust anchor, or make the app pin the system trust store
  instead of resolving `SSLSocketFactory.getDefault()`, and the handshake fails:
  nothing is delivered and every oracle goes red. The converse is **not**
  covered — a trust-all regression inside the app would not be caught by this
  test; see *Configured, and only this* above.
* Run the test without the harness and it fails immediately on a missing runner
  argument — it never skips, because a skip reports as a pass.

## The gate adapter

The e2e chain runs one hash-frozen command list against every slice's repo and
it was written for the python backend: `scripts/check-line-limit.sh` and
`.venv/bin/pytest`. Both now exist here for real —
`scripts/check-line-limit.sh` enforces the 200-line rule (with the ten
pre-existing offenders recorded as a non-growable baseline), and
`.venv/bin/pytest` is a symlink to `scripts/e2e/gate-dispatch.sh`, which maps
`tests/` to the JVM unit suite and a `com.cocode.…` class to this harness.

The dispatcher blanks the `test.mail.*` keys on the unit-test path. Without
that, `app/build.gradle.kts` bakes the developer's `.env` — real credentials —
into the test JVM and `MailIntegrationTest` sends real mail to a real mailbox on
every gate run.
