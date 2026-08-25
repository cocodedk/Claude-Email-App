#!/usr/bin/env bash
# Enforce the repo's 200-line-per-file rule (CLAUDE.md).
#
# This script exists because the e2e gate for this repo runs the same frozen
# command list as the claude-email backend's gate, which invokes
# `scripts/check-line-limit.sh`. The backend had one; this repo did not. Rather
# than stub it, this is a real check.
#
# Ten files already exceeded the limit when it was written. They are listed
# below with the count they were at, so the rule is enforced on everything else
# and the existing debt is visible and cannot grow — a file in the baseline that
# gets *longer* fails, and any file not in the baseline fails at 201 lines.
set -euo pipefail
cd "$(dirname "$0")/.."

LIMIT=200

# path:lines-at-baseline. Shrink a file and it may be removed from this list.
BASELINE="
app/src/test/java/com/cocode/claudeemailapp/app/AppViewModelTest.kt:698
app/src/main/java/com/cocode/claudeemailapp/app/AppViewModel.kt:525
app/src/main/java/com/cocode/claudeemailapp/app/AppRoot.kt:460
app/src/test/java/com/cocode/claudeemailapp/mail/ImapMailFetcherTest.kt:400
app/src/androidTest/java/com/cocode/claudeemailapp/EndToEndEnvelopeFlowTest.kt:281
app/src/androidTest/java/com/cocode/claudeemailapp/SteeringLiveFlowTest.kt:273
app/src/test/java/com/cocode/claudeemailapp/data/PendingCommandStoreTest.kt:269
app/src/androidTest/java/com/cocode/claudeemailapp/AppRootTest.kt:253
app/src/main/java/com/cocode/claudeemailapp/mail/ImapMailFetcher.kt:215
app/src/test/java/com/cocode/claudeemailapp/protocol/EnvelopeTest.kt:211
"

baseline_for() {
  printf '%s\n' "$BASELINE" | awk -F: -v p="$1" '$1==p {print $2}'
}

failed=0
while IFS= read -r file; do
  lines=$(wc -l < "$file")
  allowed=$(baseline_for "$file")
  [ -n "$allowed" ] || allowed=$LIMIT
  if [ "$lines" -gt "$allowed" ]; then
    if [ "$allowed" = "$LIMIT" ]; then
      echo "line-limit: $file is $lines lines (limit $LIMIT)" >&2
    else
      echo "line-limit: $file grew to $lines lines (baseline $allowed; must not grow)" >&2
    fi
    failed=1
  fi
done < <(find app/src scripts -type f \( -name '*.kt' -o -name '*.py' -o -name '*.sh' \) | sort)

if [ "$failed" -ne 0 ]; then
  echo "line-limit: extract helpers until every file is at or under $LIMIT lines." >&2
  exit 1
fi
echo "line-limit: ok"
