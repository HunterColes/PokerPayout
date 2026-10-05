#!/usr/bin/env bash
# Verify release signing without printing any secret: keystore.properties is private,
# its passwords open the keystore and key, and the certificate is the one F-Droid expects.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
props="$root/keystore.properties"
meta="$root/metadata/com.huntercoles.pokerpayout.yml"

if [[ ! -f $props ]]; then
  echo "  ✘ No keystore.properties at $props"
  exit 1
fi
perm="$(stat -c %a "$props")"
if [[ $perm == 600 ]]; then
  echo "  ✔ keystore.properties is private (600)"
else
  echo "  ! keystore.properties is mode $perm; fix with: chmod 600 $props"
fi

expected="$(sed -n 's/^AllowedAPKSigningKeys:[[:space:]]*//p' "$meta" | tr -d '[:space:]')"
if [[ -z $expected ]]; then
  echo "  ✘ No AllowedAPKSigningKeys in $meta"
  exit 1
fi

exec java "$here/VerifySigning.java" "$root" "$expected"
