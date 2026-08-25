#!/usr/bin/env python3
"""Drive the real app on an emulator against the real claude-email backend.

One command boots the whole world and asserts on it from both ends:

  GreenMail (docker) ── TLS terminators ──┬── real main.py poller (host)
                                          └── real app on an emulator (10.0.2.2)

The emulator side is asserted by ``RealMailE2ETest`` on the device. This script
asserts the half the device cannot see: that the backend really executed the
command, exactly once, in a real CLI process. Both must hold for the run to be
green.

Run it directly, or through ``scripts/e2e/gate-dispatch.sh``. It needs the
claude-email backend checkout — set ``CLAUDE_EMAIL_REPO`` to point at it.
"""
from __future__ import annotations

import base64
import contextlib
import os
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import backend  # noqa: E402
import checkout  # noqa: E402
import emulator  # noqa: E402
import mailserver  # noqa: E402
import oracle  # noqa: E402

APP_REPO = Path(__file__).resolve().parents[2]
DEFAULT_TEST_CLASS = "com.cocode.claudeemailapp.e2e.RealMailE2ETest"
PROJECT_NAME = "e2e-app-project"

#: Every key app/build.gradle.kts bakes into the instrumentation runner. They
#: are overridden unconditionally, because the developer .env / .env.test they
#: otherwise come from hold REAL mailbox credentials — a gate run must never be
#: able to reach the operator's mailbox.
BAKED_KEYS = (
    "TEST_MAIL_IMAP_HOST", "TEST_MAIL_IMAP_PORT", "TEST_MAIL_SMTP_HOST",
    "TEST_MAIL_SMTP_PORT", "TEST_MAIL_SMTP_STARTTLS", "TEST_MAIL_EMAIL",
    "TEST_MAIL_PASSWORD", "TEST_MAIL_RECIPIENT", "SHARED_SECRET",
)


def gradle_env(app, agent, secret: str, imaps: int, smtps: int) -> dict:
    env = {k: v for k, v in os.environ.items() if k not in BAKED_KEYS}
    env.update({
        "TEST_MAIL_IMAP_HOST": "10.0.2.2", "TEST_MAIL_IMAP_PORT": str(imaps),
        "TEST_MAIL_SMTP_HOST": "10.0.2.2", "TEST_MAIL_SMTP_PORT": str(smtps),
        "TEST_MAIL_SMTP_STARTTLS": "false",
        "TEST_MAIL_EMAIL": app.address, "TEST_MAIL_PASSWORD": app.password,
        "TEST_MAIL_RECIPIENT": agent.address, "SHARED_SECRET": secret,
    })
    return env


def run_instrumentation(test_class: str, args: dict, env: dict, serial: str) -> int:
    argv = [str(APP_REPO / "gradlew"), ":app:connectedDebugAndroidTest", "--no-daemon",
            f"-Pandroid.testInstrumentationRunnerArguments.class={test_class}"]
    argv += [f"-Pandroid.testInstrumentationRunnerArguments.{k}={v}" for k, v in args.items()]
    env = {**env, "ANDROID_SERIAL": serial}
    print(f"[harness] running {test_class} on {serial}", flush=True)
    return subprocess.run(argv, cwd=str(APP_REPO), env=env, check=False).returncode


def assert_backend_executed(env: dict, nonce: str) -> None:
    """The real CLI ran, once, with the command the app typed.

    Read from the stub's append-only ledger — a file on the host the emulator
    has no access to and the device-side test never writes. If the app had
    rendered a reply without the backend executing anything, this fails.
    """
    entries = backend.ledger_entries(env)
    hits = [e for e in entries if nonce in e.get("prompt", "")]
    if len(hits) != 1:
        raise AssertionError(
            f"expected exactly one real CLI execution carrying {nonce!r}, "
            f"got {len(hits)} (ledger held {len(entries)} entries): {entries}"
        )
    print(f"[harness] backend oracle: one CLI execution for {nonce}", flush=True)


def main(test_class: str) -> int:
    repo = checkout.default_repo()
    if not (repo / "main.py").exists():
        print(f"[harness] claude-email checkout not found at {repo}; "
              f"set CLAUDE_EMAIL_REPO", file=sys.stderr)
        return 2
    stack = checkout.load_stack(repo)
    reason = stack.missing_tooling()
    if reason is not None:
        print(f"[harness] backend tooling unavailable — {reason}", file=sys.stderr)
        return 2

    app, agent = backend.make_accounts(mailserver.DOMAIN)
    secret, token = secrets.token_hex(24), "e2e-result-" + secrets.token_hex(8)
    nonce = "e2e-cmd-" + secrets.token_hex(8)

    with contextlib.ExitStack() as ctx:
        smtp_port, imap_port = ctx.enter_context(mailserver.running_mailserver())
        workdir = Path(ctx.enter_context(tempfile.TemporaryDirectory(prefix="app-e2e-")))
        for sub in ("logs", "projects", "home"):
            (workdir / sub).mkdir(parents=True, exist_ok=True)
        cert, key = backend.generate_cert(workdir)
        server = backend.Server(
            host=mailserver.HOST, smtp_port=smtp_port, imap_port=imap_port,
            domain=mailserver.DOMAIN,
            accounts={"sender": app, "recipient": agent},
        )
        imaps = stack.TlsTerminator((server.host, imap_port), str(cert), str(key))
        ctx.callback(imaps.close)
        smtps = stack.TlsTerminator((server.host, smtp_port), str(cert), str(key))
        ctx.callback(smtps.close)

        env = backend.build_env(
            stack, server, imaps_port=imaps.port, smtps_port=smtps.port,
            cafile=cert, workdir=workdir, app=app, agent=agent,
            secret=secret, token=token,
        )
        ctx.callback(stack.shutdown_gpg, workdir / "gnupg")
        backend.prepare_project(env, PROJECT_NAME)
        runroot = stack.stage_run_root(workdir / "run-root")
        poller = ctx.enter_context(backend.booted_poller(stack, env, workdir, runroot))
        print(f"[harness] poller pid {poller.pid}, imaps {imaps.port}, smtps {smtps.port}",
              flush=True)

        serial = ctx.enter_context(emulator.running_emulator(workdir / "logs"))
        code = run_instrumentation(
            test_class,
            {
                "e2e.imaps.port": imaps.port, "e2e.smtps.port": smtps.port,
                "e2e.ca.pem.b64": base64.b64encode(cert.read_bytes()).decode(),
                "e2e.app.email": app.address, "e2e.app.password": app.password,
                "e2e.service.address": agent.address,
                "e2e.shared.secret": secret, "e2e.project": PROJECT_NAME,
                "e2e.command.nonce": nonce,
            },
            gradle_env(app, agent, secret, imaps.port, smtps.port),
            serial,
        )
        if code != 0:
            print("[harness] --- poller output ---\n" + poller.output(), file=sys.stderr)
            return code
        assert_backend_executed(env, nonce)
        delivered = oracle.result_envelopes(mailserver.HOST, imap_port, app.address)
        result = oracle.assert_real_cli_output_returned(delivered, token)
        print(f"[harness] mailbox oracle: result envelope for task "
              f"{result.get('task_id')} carried the CLI's own output", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TEST_CLASS))
