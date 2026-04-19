#!/bin/sh
set -eu
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "Hooks installed — pre-commit (buildSmoke) and commit-msg (Conventional Commits) are active."
