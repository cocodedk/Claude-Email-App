"""Boot the real claude-email backend against the private e2e mail server.

Nothing here simulates a component of the system under test. It starts the
*actual* ``main.py`` as an OS process, pointed at a real GreenMail over a real
TLS socket, and hands it a real ``claude`` CLI stub that records what it was
asked to run.

The heavy lifting is imported from the backend checkout's own e2e harness
(``tests/e2e/_stack.py``) rather than reimplemented: the TLS terminator, the
run-root staging that stops a child inheriting the operator's ``.env``, and the
environment builder are all that file's, already reviewed in earlier slices.
Only two things differ and both are forced by the emulator:

* the certificate carries ``IP:10.0.2.2`` as well as ``IP:127.0.0.1``, because
  10.0.2.2 is the emulator's NAT alias for the host loopback and the app sets
  ``mail.smtp.ssl.checkserveridentity=true``;
* the poller runs with ``GPG_FINGERPRINT=""`` — the bearer-token deployment.
  ``is_authorized`` returns on the GPG branch whenever a fingerprint is set, so
  the shared-secret route the app actually uses is only reachable without one.
"""
from __future__ import annotations

import contextlib
import dataclasses
import subprocess
from pathlib import Path

#: Written by the stub for every CLI invocation — the host-side oracle for
#: "the backend really executed the command", independent of anything the
#: emulator observes.
LEDGER_ENV = "CLAUDE_EMAIL_APP_E2E_LEDGER"

STUB_SOURCE = """#!/usr/bin/env python3
import json, os, sys, time
argv = sys.argv[1:]
prompt = argv[-1] if argv else ""
with open(os.environ["{ledger}"], "a") as fh:
    fh.write(json.dumps({{"at": time.time(), "prompt": prompt, "argv": argv}}) + "\\n")
sys.stdout.write(os.environ["{token}"] + "\\n")
"""

TOKEN_ENV = "CLAUDE_EMAIL_APP_E2E_TOKEN"


@dataclasses.dataclass(frozen=True)
class Account:
    login: str
    password: str
    address: str


@dataclasses.dataclass(frozen=True)
class Server:
    """The shape ``_stack.build_stack_env`` expects of a mail server."""

    host: str
    smtp_port: int
    imap_port: int
    domain: str
    accounts: dict


def generate_cert(directory: Path) -> tuple[Path, Path]:
    """Self-signed cert whose SAN covers both sides of the emulator NAT."""
    cert, key = directory / "e2e-tls.crt", directory / "e2e-tls.key"
    subprocess.run(
        ["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
         "-keyout", str(key), "-out", str(cert), "-days", "2",
         "-subj", "/CN=claude-email-app-e2e",
         "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2"],
        capture_output=True, text=True, timeout=120, check=True,
    )
    key.chmod(0o600)
    return cert, key


def write_cli_stub(path: Path) -> Path:
    path.write_text(STUB_SOURCE.format(ledger=LEDGER_ENV, token=TOKEN_ENV))
    path.chmod(0o700)
    return path


def build_env(stack, server: Server, *, imaps_port: int, smtps_port: int,
              cafile: Path, workdir: Path, app: Account, agent: Account,
              secret: str, token: str) -> dict:
    """The poller's whole environment — constructed, never inherited."""
    gnupghome = workdir / "gnupg"
    gnupghome.mkdir(mode=0o700, exist_ok=True)
    env = stack.build_stack_env(
        server, imaps_port=imaps_port, smtps_port=smtps_port,
        chat_port=stack.free_port(), cafile=cafile, gnupghome=gnupghome,
        fingerprint="", workdir=workdir, shared_secret=secret,
    )
    ledger = workdir / "cli-ledger.jsonl"
    return {
        **env,
        "GPG_FINGERPRINT": "",
        # The mail server accepts any login and keys mailboxes by address (see
        # scripts/e2e/docker-compose.yml), so both sides use full addresses and
        # the From header the AUTHORIZED_SENDER check reads stays routable.
        "EMAIL_ADDRESS": agent.address,
        "EMAIL_PASSWORD": agent.password,
        "AUTHORIZED_SENDER": app.address,
        "EMAIL_DOMAIN": server.domain,
        "POLL_INTERVAL": "1",
        "CLAUDE_BIN": str(write_cli_stub(workdir / "e2e-recording-cli")),
        "CLAUDE_CWD": str(workdir / "projects"),
        "STATE_FILE": str(workdir / "processed_ids.json"),
        "LOG_FILE": str(workdir / "claude-email.log"),
        "CHAT_DB_PATH": str(workdir / "claude-chat-app-e2e.db"),
        LEDGER_ENV: str(ledger),
        TOKEN_ENV: token,
        "WORKER_IDLE_TIMEOUT": "60",
        "WORKER_TASK_TIMEOUT": "120",
    }


@contextlib.contextmanager
def booted_poller(stack, env: dict, workdir: Path, runroot: Path):
    """Start the real ``main.py`` and guarantee it is stopped again.

    A leaked poller would keep consuming a mailbox after the run ends, so
    teardown is unconditional.
    """
    logs = workdir / "logs"
    logs.mkdir(parents=True, exist_ok=True)
    child = stack.spawn("poller", "main.py", env, logs, runroot=runroot)
    try:
        child.wait_for_output(r"IMAP connected to \S+ as \S+")
        yield child
    finally:
        child.stop()


def ledger_entries(env: dict) -> list[dict]:
    """Every CLI invocation the stub recorded, oldest first."""
    import json  # noqa: PLC0415 — only needed on the assertion path
    path = Path(env[LEDGER_ENV])
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def make_accounts(domain: str) -> tuple[Account, Account]:
    """The app's mailbox and the backend's mailbox. No real address appears."""
    app = Account("app-user", "app-pw", f"app-user@{domain}")
    agent = Account("agent", "agent-pw", f"agent@{domain}")
    return app, agent


def prepare_project(env: dict, name: str) -> Path:
    """Create the project directory the worker will run the CLI in."""
    path = Path(env["CLAUDE_CWD"]) / name
    path.mkdir(parents=True, exist_ok=True)
    return path
