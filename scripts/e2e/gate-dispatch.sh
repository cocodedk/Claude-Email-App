#!/usr/bin/env bash
# Translate the e2e chain's frozen gate commands into this repo's build system.
#
# The chain runs one hash-frozen command list (state/gate-cmds.sh) against every
# slice's repo. It was written for the python backend and invokes
# `.venv/bin/pytest`; this repo is a Gradle Android project with no python test
# suite at all. The gate file cannot be edited — it is hashed, and editing it is
# an automatic rejection — so the translation has to live here.
#
# `.venv/bin/pytest` is a symlink to this script. It is a real dispatcher, not a
# stub that exits 0:
#
#   tests/                       → the JVM unit suite (:app:testDebugUnitTest)
#   com.cocode.…SomeTest         → that instrumentation test on an emulator,
#                                  through scripts/e2e/harness.py, which boots
#                                  the mail server and the backend it needs
#
# Anything else is an error, so a future gate command cannot pass silently.
#
# On the unit-suite path the test.mail.* keys are exported EMPTY on purpose.
# app/build.gradle.kts otherwise bakes the developer's .env / .env.test — real
# one.com credentials — into the test JVM, and MailIntegrationTest then sends
# real mail to the operator's real mailbox on every gate run. Those tests are
# opt-in by their own design ("Skipped when test credentials are absent"); the
# gate declines to opt in. Every other unit test runs normally.
set -euo pipefail
cd "$(dirname "$0")/../.."

target="${1:-}"
for arg in "$@"; do
  case "$arg" in -*) continue ;; esac
  target="$arg"
  break
done

case "$target" in
  tests|tests/|tests/*)
    export TEST_MAIL_IMAP_HOST="" TEST_MAIL_IMAP_PORT="" TEST_MAIL_SMTP_HOST="" \
           TEST_MAIL_SMTP_PORT="" TEST_MAIL_SMTP_STARTTLS="" TEST_MAIL_EMAIL="" \
           TEST_MAIL_PASSWORD="" TEST_MAIL_RECIPIENT="" SHARED_SECRET=""
    exec ./gradlew :app:testDebugUnitTest --no-daemon
    ;;
  com.cocode.*)
    # The harness imports the backend's own e2e helpers, which need that
    # checkout's dependencies — so run it on that checkout's interpreter.
    repo="${CLAUDE_EMAIL_REPO:-$HOME/0-projects/claude-email}"
    python="$repo/.venv/bin/python"
    [ -x "$python" ] || python="$(command -v python3)"
    exec "$python" scripts/e2e/harness.py "$target"
    ;;
  *)
    echo "gate-dispatch: don't know how to run '$target'" >&2
    exit 2
    ;;
esac
