#!/usr/bin/env bash
# Where is a release in F-Droid's pipeline? Read-only; needs no tokens.
#
#   scripts/release/fdroid-status.sh            # the version in app/build.gradle.kts
#   scripts/release/fdroid-status.sh 1.2.0      # a specific versionName (tag v1.2.0)
#
# Stages: GitHub release -> fdroiddata has the build (checkupdates bot or MR) ->
# buildserver built + verified it -> published in the F-Droid index.
set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$HERE/lib/common.sh"
META_PY=(python3 "$HERE/lib/metadata.py")
CHECKOUT="$(checkout_root "$HERE")"
GH_REPO="$(github_repo "$CHECKOUT")"
cd "$CHECKOUT"

case "${1:-}" in -h|--help) sed -n '2,9p' "$0" | sed -E 's/^# ?//'; exit 0 ;; esac
WORK="$(mktemp -d "${TMPDIR:-/tmp}/pokerpayout-fdroid-status.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
if [[ -n ${1:-} ]]; then
  NAME="${1#v}"
  if git rev-parse -q --verify "refs/tags/v$NAME" >/dev/null; then
    git show "v$NAME:app/build.gradle.kts" >"$WORK/tag.gradle.kts"
    read -r _ CODE < <("${META_PY[@]}" get-version "$WORK/tag.gradle.kts")
  else
    read -r cur CODE < <("${META_PY[@]}" get-version app/build.gradle.kts)
    [[ $cur == "$NAME" ]] || die "tag v$NAME not found locally; git fetch --tags, or run without a version"
  fi
else
  read -r NAME CODE < <("${META_PY[@]}" get-version app/build.gradle.kts)
fi
TAG="v$NAME"

printf 'F-Droid status for %s %s (versionCode %s)\n' "$APP_ID" "$TAG" "$CODE"
STAGE="not released"

# ---------------------------------------------------------------- GitHub
if [[ -n "$(git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null)" ]]; then
  line_ok tag "$TAG is on origin"
else
  line_bad tag "$TAG is not on origin (F-Droid's checkupdates only sees pushed tags)"
fi
APK_NAME="$APK_PREFIX-v$NAME-release.apk"
if gh release view "$TAG" --repo "$GH_REPO" --json assets >"$WORK/rel.json" 2>/dev/null \
   && python3 -c 'import json,sys; sys.exit(0 if any(a["name"]==sys.argv[2] for a in json.load(open(sys.argv[1]))["assets"]) else 1)' "$WORK/rel.json" "$APK_NAME"; then
  line_ok github "release $TAG has $APK_NAME"
  STAGE="released on GitHub"
else
  line_bad github "no GitHub release $TAG with $APK_NAME (F-Droid's Binaries download would fail)"
fi

# ---------------------------------------------------------------- fdroiddata
UP="$WORK/upstream.yml"
MIRROR_KEY="$("${META_PY[@]}" field "$META_REL" AllowedAPKSigningKeys)"
if fetch "$FDROIDDATA_RAW" "$UP" 30; then
  UP_CODE="$("${META_PY[@]}" field "$UP" CurrentVersionCode)"
  UP_KEY="$("${META_PY[@]}" field "$UP" AllowedAPKSigningKeys)"
  if "${META_PY[@]}" has-build "$UP" "$CODE"; then
    line_ok fdroiddata "has a build entry for $CODE (CurrentVersionCode $UP_CODE)"
    [[ $STAGE == "released on GitHub" ]] && STAGE="queued for F-Droid's buildserver"
  else
    line_warn fdroiddata "no build entry for $CODE yet (CurrentVersionCode $UP_CODE); checkupdates bot runs a few times a day"
  fi
  if [[ ",$UP_KEY," == *",${MIRROR_KEY%%,*},"* ]]; then
    line_ok "signing key" "fdroiddata pins ${UP_KEY:0:12}…, same as the in-repo mirror"
  else
    line_bad "signing key" "fdroiddata pins ${UP_KEY:0:12}…, mirror pins ${MIRROR_KEY:0:12}… → builds fail until an MR changes it (fdroid-mr.sh)"
  fi
else
  line_warn fdroiddata "could not fetch $FDROIDDATA_RAW"
fi
if fetch "$FDROIDDATA_API/repository/commits?path=metadata/$APP_ID.yml&per_page=3" "$WORK/commits.json" 30; then
  python3 - "$WORK/commits.json" <<'PY' | while IFS= read -r l; do note "$l"; done
import json, sys
for c in json.load(open(sys.argv[1])):
    print(f"fdroiddata {c['created_at'][:16].replace('T', ' ')}  {c['author_name']}: {c['title']}")
PY
fi
if fetch "$FDROIDDATA_API/merge_requests?state=opened&search=pokerpayout&in=title,description&per_page=5" "$WORK/mrs.json" 30; then
  python3 - "$WORK/mrs.json" <<'PY' | while IFS= read -r l; do note "$l"; done
import json, sys
for m in json.load(open(sys.argv[1])):
    print(f"open MR !{m['iid']}: {m['title']}  {m['web_url']}")
PY
fi

# ---------------------------------------------------------------- build
code=$(curl -s -o "$WORK/build.html" -w '%{http_code}' --max-time 30 \
  "https://monitor.f-droid.org/builds/log/$APP_ID/$CODE" || true)
if [[ $code == 200 ]]; then
  python3 - "$WORK/build.html" >"$WORK/build.txt" <<'PY'
import html, re, sys
print(html.unescape(re.sub(r"<[^>]+>", "\n", open(sys.argv[1], errors="replace").read())))
PY
  if grep -q 'compared built binary to supplied reference binary successfully' "$WORK/build.txt" \
     && grep -qE 'success: |1 build succeeded' "$WORK/build.txt"; then
    line_ok build "built and verified identical to the GitHub APK"
    STAGE="built; waiting for the next index publish"
  elif grep -qE 'Could not build app|build failed|BuildException' "$WORK/build.txt"; then
    line_bad build "build FAILED: https://monitor.f-droid.org/builds/log/$APP_ID/$CODE"
    grep -m3 -E 'ERROR|Could not build|What went wrong' "$WORK/build.txt" | sed 's/^/      /' || true
    STAGE="build failed"
  else
    line_warn build "log exists but no verdict yet: https://monitor.f-droid.org/builds/log/$APP_ID/$CODE"
  fi
else
  line_skip build "no build log yet (monitor.f-droid.org)"
fi

# ---------------------------------------------------------------- published
if fetch "https://f-droid.org/api/v1/packages/$APP_ID" "$WORK/pkg.json" 30; then
  read -r published suggested < <(python3 - "$WORK/pkg.json" "$CODE" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
codes = [p["versionCode"] for p in d.get("packages", [])]
print("yes" if int(sys.argv[2]) in codes else "no", d.get("suggestedVersionCode"))
PY
)
  if [[ $published == yes ]]; then
    line_ok published "versionCode $CODE is live on f-droid.org (suggested $suggested)"
    STAGE="published"
  else
    line_skip published "not in the F-Droid index yet (suggested $suggested)"
  fi
else
  line_warn published "could not reach the f-droid.org API"
fi

echo "Stage: $STAGE"
