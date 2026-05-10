#!/bin/sh
set -eu
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "Hooks installed — pre-commit (buildSmoke), commit-msg (Conventional Commits), and pre-push (cocodedk-only remote) are active."
