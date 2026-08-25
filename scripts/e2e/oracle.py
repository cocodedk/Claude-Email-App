"""Host-side assertions about what really happened, read off the wire.

The device-side test can only see what the app renders. These checks read the
other two records of the same round trip — the CLI stub's append-only ledger on
the host filesystem, and the actual reply sitting in the app's mailbox on the
mail server — neither of which the emulator can write.
"""
from __future__ import annotations

import email
import imaplib
import json


def result_envelopes(host: str, imap_port: int, address: str) -> list[dict]:
    """Every `kind=result` envelope delivered to ``address``.

    Read over plain IMAP straight from the server, deliberately not through the
    TLS terminator the app and the backend use: a second, independent view of
    the same mailbox.
    """
    conn = imaplib.IMAP4(host, imap_port)
    try:
        conn.login(address, "any-password")  # the test server accepts any login
        conn.select("INBOX")
        _, data = conn.search(None, "ALL")
        found = []
        for num in data[0].split():
            _, payload = conn.fetch(num, "(RFC822)")
            message = email.message_from_bytes(payload[0][1])
            if message.get_content_type() != "application/json":
                continue
            try:
                envelope = json.loads(message.get_payload(decode=True).decode())
            except (ValueError, UnicodeDecodeError):
                continue
            if envelope.get("kind") == "result":
                found.append(envelope)
        return found
    finally:
        try:
            conn.logout()
        except OSError:
            pass


def assert_real_cli_output_returned(envelopes: list[dict], token: str) -> dict:
    """The CLI's actual stdout came back to the app's mailbox, exactly once.

    ``token`` is generated on the host and handed only to the CLI stub. Its
    presence in a delivered result envelope proves the whole chain ran: the
    app's SMTP send, the backend's authentication and dispatch, a real CLI
    process, and the backend's SMTP reply.
    """
    hits = [
        env for env in envelopes
        if token in json.dumps(env.get("data") or {})
    ]
    if len(hits) != 1:
        raise AssertionError(
            f"expected exactly one delivered result envelope carrying the CLI's "
            f"output token {token!r}, got {len(hits)}: {envelopes}"
        )
    return hits[0]
