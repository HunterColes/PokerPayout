#!/usr/bin/env bash
# One-time: create a new release signing key (the 2025 key's password was lost).
# A random password goes straight into keystore.properties; nothing secret is printed.
# Afterwards: back up the keystore + keystore.properties together, then F-Droid needs a
# merge request changing AllowedAPKSigningKeys to the fingerprint printed below.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
keystore="pokerpayout-release-2026.keystore"
props="keystore.properties"
meta="metadata/com.huntercoles.pokerpayout.yml"

if [[ -e $keystore ]]; then
  echo "  ✘ $keystore already exists; refusing to overwrite a signing key."
  exit 1
fi
if [[ -f $props ]] && grep -Eq '^[[:space:]]*storePassword[[:space:]]*=[[:space:]]*[^[:space:]]' "$props"; then
  echo "  ✘ $props already holds a password; refusing to overwrite it."
  exit 1
fi
if ! git check-ignore -q "$keystore" || ! git check-ignore -q "$props"; then
  echo "  ✘ $keystore or $props is not gitignored; refusing to create secrets that could be committed."
  exit 1
fi

umask 077
pass="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 40)"

KS_PASS="$pass" keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -alias pokerpayout \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Hunter Coles, O=Fatline, ST=AB" \
  -storepass:env KS_PASS -keypass:env KS_PASS >/dev/null 2>&1

sha256="$(KS_PASS="$pass" keytool -exportcert -rfc -keystore "$keystore" -alias pokerpayout -storepass:env KS_PASS 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr 'A-F' 'a-f')"

{
  echo "# Release signing for Poker Payout. Gitignored; keep it chmod 600."
  echo "# Key created $(date +%F) by scripts/release/new-signing-key.sh."
  echo "# BACK UP this file and $keystore together; one is useless without the other."
  echo "storeFile=$keystore"
  echo "storePassword=$pass"
  echo "keyAlias=pokerpayout"
  echo "keyPassword=$pass"
} > "$props"
unset pass
chmod 600 "$props" "$keystore"

sed -i "s/^AllowedAPKSigningKeys:.*/AllowedAPKSigningKeys: $sha256/" "$meta"
echo "  ✔ New key: $keystore (RSA 4096, valid ~27 years)"
echo "  ✔ Certificate SHA-256: $sha256"
echo "  ✔ Updated AllowedAPKSigningKeys in $meta"
"$root/scripts/release/verify-signing.sh"

cat <<EOF

BACK UP NOW. Copy both files somewhere safe (password manager and/or backup drive):
  $root/$keystore
  $root/$props
Losing either one means another key change.
EOF
