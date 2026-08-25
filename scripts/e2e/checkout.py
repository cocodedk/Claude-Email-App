"""Locating the claude-email backend checkout and importing its e2e harness.

Split out of ``backend.py`` to keep both files under the repo's 200-line rule.
"""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def load_stack(repo: Path):
    """Import the backend checkout's e2e harness module (``tests/e2e/_stack.py``)."""
    sys.path.insert(0, str(repo / "tests" / "e2e"))
    sys.path.insert(0, str(repo))
    import _stack  # noqa: PLC0415 — path must be set first
    return _stack


#: The file that marks a checkout as carrying the backend's e2e harness.
STACK_MARKER = Path("tests") / "e2e" / "_stack.py"


def _worktrees(repo: Path) -> list[Path]:
    """Linked worktrees of ``repo``, newest-listed last. Read-only."""
    listed = subprocess.run(
        ["git", "-C", str(repo), "worktree", "list", "--porcelain"],
        capture_output=True, text=True, timeout=60, check=False,
    )
    if listed.returncode != 0:
        return []
    return [Path(line.split(" ", 1)[1].strip())
            for line in listed.stdout.splitlines() if line.startswith("worktree ")]


def default_repo() -> Path:
    """A claude-email checkout that actually carries the e2e harness.

    ``CLAUDE_EMAIL_REPO`` wins outright. Otherwise the main checkout is used if
    it has the harness — and when the e2e work is still on an unmerged branch it
    will not, so its linked worktrees are searched too. Without that, the gate
    would have to be told where the branch is checked out, which it cannot be:
    the gate's command list is frozen.
    """
    override = os.environ.get("CLAUDE_EMAIL_REPO", "").strip()
    if override:
        return Path(override)
    main = Path.home() / "0-projects" / "claude-email"
    if (main / STACK_MARKER).exists():
        return main
    for tree in _worktrees(main):
        if (tree / STACK_MARKER).exists() and (tree / "main.py").exists():
            return tree
    return main
