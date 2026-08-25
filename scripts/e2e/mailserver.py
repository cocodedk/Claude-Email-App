"""The private GreenMail for the on-emulator e2e run — start, wait, stop."""
from __future__ import annotations

import contextlib
import os
import shutil
import socket
import subprocess
import time
from pathlib import Path

COMPOSE_FILE = Path(__file__).parent / "docker-compose.yml"
PROJECT = "claude-email-app-e2e"
HOST = "127.0.0.1"
DOMAIN = "e2e.test"
STARTUP_TIMEOUT = 300.0


def port(name: str, default: int) -> int:
    return int(os.environ.get(f"CLAUDE_EMAIL_APP_E2E_{name}_PORT", default))


def unavailable_reason() -> str | None:
    if shutil.which("docker") is None:
        return "docker executable not found on PATH"
    probe = subprocess.run(
        ["docker", "compose", "version"],
        capture_output=True, text=True, timeout=60, check=False,
    )
    if probe.returncode != 0:
        return f"'docker compose' unavailable: {probe.stderr.strip()}"
    info = subprocess.run(
        ["docker", "info", "--format", "{{.ServerVersion}}"],
        capture_output=True, text=True, timeout=60, check=False,
    )
    if info.returncode != 0:
        return f"docker daemon not reachable: {info.stderr.strip()}"
    return None


def compose(*args: str, timeout: float) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "-p", PROJECT, *args],
        capture_output=True, text=True, timeout=timeout, check=False,
    )


def _banner(target_port: int) -> bytes:
    with socket.create_connection((HOST, target_port), timeout=5) as sock:
        sock.settimeout(5)
        with sock.makefile("rb") as stream:
            return stream.readline()


def await_serving(expected: dict[int, bytes], timeout: float = 120.0) -> None:
    """Block until each port answers with its protocol greeting.

    Docker's userland proxy accepts connections from the moment the container
    exists, long before the JVM inside has bound anything, so "the port is
    open" is not a readiness signal. The actual SMTP/IMAP greeting is.
    """
    deadline = time.monotonic() + timeout
    for target_port, prefix in expected.items():
        while True:
            try:
                greeting = _banner(target_port)
                if greeting.startswith(prefix):
                    break
                problem = f"greeting was {greeting!r}, expected {prefix!r}"
            except OSError as exc:
                problem = f"connection failed: {exc}"
            if time.monotonic() >= deadline:
                raise RuntimeError(f"mail server not serving on {HOST}:{target_port} — {problem}")
            time.sleep(0.5)


@contextlib.contextmanager
def running_mailserver():
    """Start the private GreenMail and tear it down with its mailboxes."""
    reason = unavailable_reason()
    if reason is not None:
        raise RuntimeError(f"the e2e mail server needs docker — {reason}")
    smtp, imap = port("SMTP", 14025), port("IMAP", 14143)
    try:
        up = compose("up", "-d", "--remove-orphans", timeout=STARTUP_TIMEOUT)
        if up.returncode != 0:
            raise RuntimeError(f"docker compose up failed:\n{up.stdout}\n{up.stderr}")
        await_serving({smtp: b"220", imap: b"* OK"})
        yield smtp, imap
    finally:
        compose("down", "-v", "--remove-orphans", timeout=120.0)
