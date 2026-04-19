#!/bin/sh
# Android signing keystore generator + GitHub Actions secret uploader.
#
# Generates (or reuses) a release keystore, verifies it, and uploads the four
# secrets required by the release workflow (KEYSTORE_BASE64, KEYSTORE_PASSWORD,
# KEY_ALIAS, KEY_PASSWORD) to the current GitHub repository.
#
# Requirements: gh (authenticated), keytool, base64.

set -eu

# --- Preflight -------------------------------------------------------------

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: 'gh' CLI is not installed." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "ERROR: 'gh' is not authenticated. Run 'gh auth login' first." >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: 'keytool' not found. Install a JDK (e.g. OpenJDK 17+)." >&2
  exit 1
fi

if ! command -v base64 >/dev/null 2>&1; then
  echo "ERROR: 'base64' not found." >&2
  exit 1
fi

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)
if [ -z "${REPO:-}" ]; then
  echo "ERROR: Unable to determine GitHub repo. Is a remote configured?" >&2
  exit 1
fi

echo ""
echo "=== Android signing setup for $REPO ==="
echo ""

# --- Keystore path ---------------------------------------------------------

DEFAULT_KEYSTORE="./release.keystore"
printf "Keystore path [%s]: " "$DEFAULT_KEYSTORE"
read -r KEYSTORE_PATH
if [ -z "${KEYSTORE_PATH:-}" ]; then
  KEYSTORE_PATH="$DEFAULT_KEYSTORE"
fi

DEFAULT_ALIAS="claude-email-release"
printf "Key alias [%s]: " "$DEFAULT_ALIAS"
read -r KEY_ALIAS
if [ -z "${KEY_ALIAS:-}" ]; then
  KEY_ALIAS="$DEFAULT_ALIAS"
fi

# --- Passwords -------------------------------------------------------------

prompt_password() {
  # $1 = prompt label, sets PASSWORD_OUT
  _label="$1"
  while :; do
    printf "%s: " "$_label"
    stty -echo 2>/dev/null || true
    read -r _p1
    stty echo 2>/dev/null || true
    printf "\n"

    printf "%s (confirm): " "$_label"
    stty -echo 2>/dev/null || true
    read -r _p2
    stty echo 2>/dev/null || true
    printf "\n"

    if [ "$_p1" != "$_p2" ]; then
      echo "Passwords do not match — try again." >&2
      continue
    fi
    if [ -z "$_p1" ]; then
      echo "Password cannot be empty — try again." >&2
      continue
    fi
    case "$_p1" in
      *\"*)
        echo "Password must not contain a double-quote character — try again." >&2
        continue
        ;;
    esac
    if [ ${#_p1} -lt 6 ]; then
      echo "Password must be at least 6 characters — try again." >&2
      continue
    fi
    PASSWORD_OUT="$_p1"
    break
  done
}

# --- Generate or reuse keystore --------------------------------------------

if [ -f "$KEYSTORE_PATH" ]; then
  echo "Found existing keystore at: $KEYSTORE_PATH"
  printf "Reuse it? [Y/n]: "
  read -r REUSE
  case "${REUSE:-Y}" in
    n|N|no|NO)
      echo "ERROR: Refusing to overwrite existing keystore. Move or delete it first." >&2
      exit 1
      ;;
  esac

  prompt_password "KEYSTORE_PASSWORD"
  KEYSTORE_PASSWORD="$PASSWORD_OUT"

  prompt_password "KEY_PASSWORD"
  KEY_PASSWORD="$PASSWORD_OUT"
else
  echo "No keystore at '$KEYSTORE_PATH' — generating a new one."
  echo ""
  echo "Distinguished name fields (press Enter for defaults):"

  printf "  CN (common name) [Cocode]: "
  read -r DN_CN
  [ -z "${DN_CN:-}" ] && DN_CN="Cocode"

  printf "  O (organization) [Cocode]: "
  read -r DN_O
  [ -z "${DN_O:-}" ] && DN_O="Cocode"

  printf "  L (city) [Copenhagen]: "
  read -r DN_L
  [ -z "${DN_L:-}" ] && DN_L="Copenhagen"

  printf "  C (country code) [DK]: "
  read -r DN_C
  [ -z "${DN_C:-}" ] && DN_C="DK"

  DNAME="CN=$DN_CN, O=$DN_O, L=$DN_L, C=$DN_C"

  prompt_password "KEYSTORE_PASSWORD"
  KEYSTORE_PASSWORD="$PASSWORD_OUT"

  prompt_password "KEY_PASSWORD"
  KEY_PASSWORD="$PASSWORD_OUT"

  echo ""
  echo "Generating keystore (this can take a few seconds) ..."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "$DNAME" >/dev/null

  echo "Keystore generated at: $KEYSTORE_PATH"
fi

# --- Verify keystore -------------------------------------------------------

echo ""
echo "Verifying keystore ..."
if ! keytool -list -v \
       -keystore "$KEYSTORE_PATH" \
       -storepass "$KEYSTORE_PASSWORD" >/dev/null 2>&1; then
  echo "ERROR: Keystore verification failed (wrong password, or file is corrupt)." >&2
  exit 1
fi
echo "Keystore verified."

# --- Upload secrets --------------------------------------------------------

echo ""
echo "Uploading secrets to $REPO ..."

KEYSTORE_BASE64=$(base64 -w 0 "$KEYSTORE_PATH")

printf '%s' "$KEYSTORE_BASE64"    | gh secret set KEYSTORE_BASE64    --repo "$REPO" --body -
printf '%s' "$KEYSTORE_PASSWORD"  | gh secret set KEYSTORE_PASSWORD  --repo "$REPO" --body -
printf '%s' "$KEY_ALIAS"          | gh secret set KEY_ALIAS          --repo "$REPO" --body -
printf '%s' "$KEY_PASSWORD"       | gh secret set KEY_PASSWORD       --repo "$REPO" --body -

# --- Summary ---------------------------------------------------------------

echo ""
echo "=== Done ==="
echo "Repo:     $REPO"
echo "Keystore: $KEYSTORE_PATH"
echo "Alias:    $KEY_ALIAS"
echo ""
echo "Uploaded secrets:"
echo "  - KEYSTORE_BASE64"
echo "  - KEYSTORE_PASSWORD"
echo "  - KEY_ALIAS"
echo "  - KEY_PASSWORD"
echo ""
echo "Keep '$KEYSTORE_PATH' out of git (already in .gitignore)."
