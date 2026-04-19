#!/bin/sh
# setup-signing.sh
# Generates a release keystore and uploads all 4 GitHub Secrets to cocodedk/Claude-Email-App.
# Run once from the project root: ./scripts/setup-signing.sh
set -eu

restore_tty() { stty echo 2>/dev/null || true; }
trap restore_tty EXIT INT TERM

REPO="cocodedk/Claude-Email-App"
KEYSTORE="${KEYSTORE_FILE:-$HOME/release.keystore}"  # override: KEYSTORE_FILE=/path/to/key.jks ./scripts/setup-signing.sh
ALIAS="${KEYSTORE_ALIAS:-android}"                   # override: KEYSTORE_ALIAS=mykey ./scripts/setup-signing.sh

echo ""
echo "=== Claude-Email-App Release Signing Setup ==="
echo ""

# ── Prerequisites ────────────────────────────────────────────────────────────
command -v keytool >/dev/null 2>&1 || { echo "ERROR: keytool not found — install JDK 17+"; exit 1; }
command -v gh     >/dev/null 2>&1 || { echo "ERROR: gh not found — install GitHub CLI";    exit 1; }
command -v base64 >/dev/null 2>&1 || { echo "ERROR: base64 not found";                     exit 1; }

gh auth status >/dev/null 2>&1 || { echo "ERROR: gh not authenticated — run: gh auth login"; exit 1; }

# ── Keystore ─────────────────────────────────────────────────────────────────
if [ -f "$KEYSTORE" ]; then
    echo "Found existing keystore: $KEYSTORE"
    echo "Skipping generation — using existing file."
else
    echo "Generating release keystore..."
    echo "You will be prompted for a keystore password, a key password,"
    echo "and some name/org fields (those can be anything)."
    echo ""
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000
fi

# ── Read passwords securely ───────────────────────────────────────────────────
echo ""
printf "Keystore password: "
stty -echo 2>/dev/null || true
read -r KSPASS
restore_tty
case "$KSPASS" in
  *'"'*)
    echo 'ERROR: Password must not contain " (double quote).'
    exit 1
    ;;
esac
echo ""

printf "Key password (Enter = same as keystore password): "
stty -echo 2>/dev/null || true
read -r KEYPASS
restore_tty
case "$KEYPASS" in
  *'"'*)
    echo 'ERROR: Key password must not contain " (double quote).'
    exit 1
    ;;
esac
echo ""

[ -z "$KEYPASS" ] && KEYPASS="$KSPASS"

# ── Verify ───────────────────────────────────────────────────────────────────
echo "Verifying keystore..."
keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass "$KSPASS" -keypass "$KEYPASS" >/dev/null 2>&1 || {
    echo ""
    echo "ERROR: Wrong password or alias. Nothing was uploaded."
    exit 1
}
echo "✓ Keystore valid"

# ── Upload secrets ────────────────────────────────────────────────────────────
KEYSTORE_B64=$(base64 "$KEYSTORE" | tr -d '\n')

echo "Uploading secrets to $REPO..."
printf '%s' "$KEYSTORE_B64" | gh secret set KEYSTORE_BASE64  --repo "$REPO"
printf '%s' "$KSPASS"       | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS"        | gh secret set KEY_ALIAS         --repo "$REPO"
printf '%s' "$KEYPASS"      | gh secret set KEY_PASSWORD      --repo "$REPO"

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "✓ All 4 secrets uploaded:"
echo "    KEYSTORE_BASE64    ✓"
echo "    KEYSTORE_PASSWORD  ✓"
echo "    KEY_ALIAS          ✓  ($ALIAS)"
echo "    KEY_PASSWORD       ✓"
echo ""
echo "IMPORTANT: $KEYSTORE is stored outside the repo — back it up somewhere secure."
echo "           If you lose it, you cannot update the app on any store."
echo ""
echo "To trigger your first release: merge to master → release-apk.yml fires"
echo "→ Claude-Email-App.apk appears at:"
echo "  https://github.com/$REPO/releases/latest/download/Claude-Email-App.apk"
