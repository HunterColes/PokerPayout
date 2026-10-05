#!/usr/bin/env bash
# Open the fdroiddata merge request a signing-key change (or a missing build entry) needs,
# through the GitLab REST API: no fdroiddata clone.
#
#   scripts/release/fdroid-mr.sh                # dry run (default): print the YAML diff + MR text
#   scripts/release/fdroid-mr.sh --publish      # fork if needed, commit on a branch, open the MR
#
# The proposed metadata/com.huntercoles.pokerpayout.yml starts from fdroiddata's current
# file and:
#   * pins only the in-repo mirror's AllowedAPKSigningKeys (the new key), when it differs;
#   * adds `disable:` to every older build, so F-Droid drops the old-key APKs from its repo
#     and never tries to rebuild them (users cannot install old versions any more);
#   * adds the Builds entry + CurrentVersion(Code) for the release, unless the checkupdates
#     bot already did.
#
# Options
#   --version X.Y.Z     release to add (default: versionName in app/build.gradle.kts)
#   --key SHA256        certificate to pin (default: AllowedAPKSigningKeys in the mirror)
#   --keep-old-builds   do not disable older builds (they then vanish only via the key pin)
#   --token-file PATH   GitLab token (default: gitlab.token in the main checkout, chmod 600)
#   --force             open a new MR even if one for this app is already open
#
# GitLab token: a personal access token with the `api` scope (fork, commit, open MR),
# saved alone in <main checkout>/gitlab.token (gitignored), chmod 600. It is never printed.
set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$HERE/lib/common.sh"
META_PY=(python3 "$HERE/lib/metadata.py")
CHECKOUT="$(checkout_root "$HERE")"
MAIN="$(main_root "$HERE")"
GH_REPO="$(github_repo "$CHECKOUT")"
cd "$CHECKOUT"

MODE=dry-run NAME="" KEY="" DISABLE_OLD=1 TOKEN_FILE="$MAIN/gitlab.token" FORCE=0
while (($#)); do
  case "$1" in
    --dry-run) MODE=dry-run ;;
    --publish) MODE=publish ;;
    --version) NAME="${2:?}"; NAME="${NAME#v}"; shift ;;
    --key) KEY="${2:?}"; shift ;;
    --keep-old-builds) DISABLE_OLD=0 ;;
    --token-file) TOKEN_FILE="${2:?}"; shift ;;
    --force) FORCE=1 ;;
    -h|--help) sed -n '2,27p' "$0" | sed -E 's/^# ?//'; exit 0 ;;
    *) die "unknown option: $1 (see --help)" ;;
  esac
  shift
done

WORK="$(mktemp -d "${TMPDIR:-/tmp}/pokerpayout-fdroid-mr.XXXXXX")"
chmod 700 "$WORK"
trap 'rm -rf "$WORK"' EXIT

# ---------------------------------------------------------------- what to propose
read -r CUR_NAME CUR_CODE < <("${META_PY[@]}" get-version app/build.gradle.kts)
NAME="${NAME:-$CUR_NAME}"
TAG="v$NAME"
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  git show "$TAG:app/build.gradle.kts" >"$WORK/tag.gradle.kts"
  read -r _ CODE < <("${META_PY[@]}" get-version "$WORK/tag.gradle.kts")
  COMMIT="$(git rev-parse "$TAG^{commit}")"   # fdroiddata's bot records the full hash
  git show "$TAG:$META_REL" >"$WORK/mirror.yml"
else
  [[ $NAME == "$CUR_NAME" ]] || die "tag $TAG not found locally (git fetch --tags)"
  CODE=$CUR_CODE COMMIT=$TAG
  cp "$META_REL" "$WORK/mirror.yml"
  [[ $MODE == publish ]] && die "tag $TAG does not exist yet; publish the release first (release.sh --publish)"
fi
KEY="${KEY:-$("${META_PY[@]}" field "$WORK/mirror.yml" AllowedAPKSigningKeys)}"
[[ $KEY =~ ^[0-9a-f]{64}$ ]] || die "not a SHA-256 certificate fingerprint: '$KEY'"

fetch "$FDROIDDATA_RAW" "$WORK/upstream.yml" 30 || die "could not download $FDROIDDATA_RAW"
cp "$WORK/upstream.yml" "$WORK/proposed.yml"
UP_KEY="$("${META_PY[@]}" field "$WORK/upstream.yml" AllowedAPKSigningKeys)"
CHANGES=()
if [[ $UP_KEY != "$KEY" ]]; then
  if [[ $DISABLE_OLD == 1 ]]; then
    "${META_PY[@]}" rotate-key "$WORK/proposed.yml" "$KEY" "$CODE" \
      "signing key lost; superseded by $NAME, signed with a new key"
    CHANGES+=("pin the new signing certificate and disable the builds signed with the lost key")
  else
    "${META_PY[@]}" rotate-key "$WORK/proposed.yml" "$KEY" 0 unused
    CHANGES+=("pin the new signing certificate")
  fi
fi
if ! "${META_PY[@]}" has-build "$WORK/proposed.yml" "$CODE"; then
  "${META_PY[@]}" add-build "$WORK/proposed.yml" "$NAME" "$CODE" "$COMMIT"
  CHANGES+=("add $NAME ($CODE)")
fi
if ((${#CHANGES[@]} == 0)); then
  line_ok fdroiddata "already pins ${KEY:0:12}… and has $NAME ($CODE); no merge request needed"
  exit 0
fi
"${META_PY[@]}" check-yml "$WORK/proposed.yml" "$("${META_PY[@]}" field "$WORK/proposed.yml" CurrentVersion)" \
  "$("${META_PY[@]}" field "$WORK/proposed.yml" CurrentVersionCode)"

ROTATION=0; [[ $UP_KEY != "$KEY" ]] && ROTATION=1
ADDED=0; [[ ${CHANGES[-1]} == add* ]] && ADDED=1
case "$ROTATION$ADDED" in
  11) TITLE="Poker Payout: new signing key, update to $NAME" ;;
  10) TITLE="Poker Payout: new signing key from $NAME" ;;
  *)  TITLE="Poker Payout: update to $NAME" ;;
esac
RELEASE_URL="https://github.com/$GH_REPO/releases/tag/$TAG"
{
  echo "Upstream developer here (${GH_REPO%%/*}, author of $APP_ID)."
  echo
  if [[ $ROTATION == 1 ]]; then
    echo "The password of the release key that signed 1.1.12 and earlier is lost, so $NAME and later"
    echo "are signed with a new key. This MR:"
  else
    echo "This MR:"
  fi
  for c in "${CHANGES[@]}"; do echo "- ${c^}"; done
  echo
  if [[ $ROTATION == 1 ]]; then
    echo "Old certificate SHA-256: \`$UP_KEY\`"
    echo "New certificate SHA-256: \`$KEY\`"
    echo
    echo "The same fingerprint is pinned in the app's own repo"
    echo "([metadata/$APP_ID.yml](https://github.com/$GH_REPO/blob/$TAG/$META_REL)) and printed in the"
    echo "[$TAG release notes]($RELEASE_URL), which also tell users to uninstall and reinstall once."
    echo "Older builds are disabled on purpose: we don't want anyone installing the old-key versions."
    echo
  fi
  echo "Reproducible build: \`Binaries:\` is unchanged; the $TAG APK on GitHub is built from tag $TAG"
  echo "with JDK $FDROID_JDK and matches an F-Droid-style unsigned rebuild byte for byte outside the signature."
} >"$WORK/mr.md"

OUT="$CHECKOUT/build/fdroid-mr/$TAG"
mkdir -p "$OUT"
cp "$WORK/proposed.yml" "$OUT/$APP_ID.yml"
cp "$WORK/mr.md" "$OUT/mr.md"
echo "$TITLE" >"$OUT/mr-title.txt"

printf 'fdroiddata merge request for %s %s (%s)  [%s]\n' "$APP_ID" "$TAG" "$CODE" "$MODE"
echo
diff -u --label "a/metadata/$APP_ID.yml" --label "b/metadata/$APP_ID.yml" \
  "$WORK/upstream.yml" "$WORK/proposed.yml" || true
echo
echo "Title: $TITLE"
echo
sed 's/^/  /' "$WORK/mr.md"
echo

if [[ $MODE == dry-run ]]; then
  line_ok "dry run" "nothing sent. Files for the manual route: $OUT"
  note "publish: scripts/release/fdroid-mr.sh --publish   (needs $TOKEN_FILE, scope api)"
  exit 0
fi

# ---------------------------------------------------------------- GitLab API
[[ -f $TOKEN_FILE ]] || die "no GitLab token at $TOKEN_FILE (see docs/RELEASING.md#gitlab-token)"
[[ "$(stat -c %a "$TOKEN_FILE")" == 600 ]] || die "$TOKEN_FILE must be private: chmod 600 $TOKEN_FILE"
if [[ $TOKEN_FILE == "$MAIN"/* ]] && ! git -C "$MAIN" check-ignore -q "$TOKEN_FILE"; then
  die "$TOKEN_FILE is not gitignored; refusing to use a token that could be committed"
fi
# The token goes into a private curl config file, never onto a command line.
umask 077
{ printf 'header = "PRIVATE-TOKEN: '; tr -d '[:space:]' <"$TOKEN_FILE"; printf '"\n'; } >"$WORK/curl.cfg"
API="https://gitlab.com/api/v4"
UPSTREAM_PATH="fdroid%2Ffdroiddata"

# api <METHOD> <path> [json file] -> body on stdout, fails on HTTP >= 400
api() {
  local method=$1 path=$2 data=${3:-} code
  local -a extra=()
  [[ -n $data ]] && extra=(-H 'Content-Type: application/json' --data-binary "@$data")
  code=$(curl -sS -K "$WORK/curl.cfg" -X "$method" "${extra[@]}" -o "$WORK/resp.json" \
    -w '%{http_code}' --max-time 120 "$API$path") || return 1
  cat "$WORK/resp.json"
  [[ $code -lt 400 ]] || { echo "HTTP $code for $method $path" >&2; return 1; }
}
jget() { python3 -c 'import json,sys; v=json.load(sys.stdin).get(sys.argv[1]); print("" if v is None else v)' "$1"; }

USER_JSON="$(api GET /user)" || die "GitLab rejected the token (needs scope api, not expired)"
GL_USER="$(jget username <<<"$USER_JSON")"
line_ok gitlab "token works for @$GL_USER"
UP_ID="$(api GET "/projects/$UPSTREAM_PATH" | jget id)"

if [[ $FORCE == 0 ]]; then
  open="$(api GET "/projects/$UP_ID/merge_requests?state=opened&author_username=$GL_USER&search=Poker%20Payout&in=title" \
    | python3 -c 'import json,sys; print(" ".join(m["web_url"] for m in json.load(sys.stdin)))')"
  [[ -z $open ]] || die "you already have an open MR for Poker Payout: $open (use --force for another)"
fi

# Reuse the user's fork of fdroiddata, or create one (GitLab copies it server side).
FORK_ID="$(api GET "/projects/$UP_ID/forks?owned=true&per_page=20" \
  | python3 -c 'import json,sys; f=json.load(sys.stdin); print(f[0]["id"] if f else "")')"
if [[ -z $FORK_ID ]]; then
  note "creating your fork of fdroid/fdroiddata (one time; can take a few minutes)"
  FORK_ID="$(api POST "/projects/$UP_ID/fork" | jget id)"
  for _ in $(seq 1 60); do
    status="$(api GET "/projects/$FORK_ID" | jget import_status)"
    [[ $status == finished || $status == none ]] && break
    sleep 10
  done
  [[ $status == finished || $status == none ]] || die "fork is still being created ($status); rerun later"
fi
FORK_PATH="$(api GET "/projects/$FORK_ID" | jget path_with_namespace)"
line_ok fork "$FORK_PATH"

BRANCH="$APP_ID-$TAG"
if api GET "/projects/$FORK_ID/repository/branches/$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$BRANCH")" >/dev/null 2>&1; then
  BRANCH="$BRANCH-$(date +%Y%m%d%H%M%S)"
fi

# One commit on a new branch of the fork, started from fdroiddata's current master.
python3 - "$WORK/commit.json" "$BRANCH" "$UP_ID" "$TITLE" "metadata/$APP_ID.yml" "$WORK/proposed.yml" <<'PY'
import json, sys
out, branch, up_id, title, path, content = sys.argv[1:]
json.dump({
    "branch": branch, "start_branch": "master", "start_project": int(up_id),
    "commit_message": title,
    "actions": [{"action": "update", "file_path": path, "content": open(content).read()}],
}, open(out, "w"))
PY
api POST "/projects/$FORK_ID/repository/commits" "$WORK/commit.json" >/dev/null \
  || die "could not commit to $FORK_PATH:$BRANCH"
line_ok commit "$FORK_PATH:$BRANCH"

python3 - "$WORK/mr.json" "$BRANCH" "$UP_ID" "$TITLE" "$WORK/mr.md" <<'PY'
import json, sys
out, branch, up_id, title, body = sys.argv[1:]
json.dump({
    "source_branch": branch, "target_branch": "master", "target_project_id": int(up_id),
    "title": title, "description": open(body).read(),
    "remove_source_branch": True, "allow_collaboration": True,
}, open(out, "w"))
PY
MR_URL="$(api POST "/projects/$FORK_ID/merge_requests" "$WORK/mr.json" | jget web_url)" \
  || die "could not open the merge request (branch $BRANCH is in your fork; open it in the web UI)"
line_ok "merge request" "$MR_URL"
note "F-Droid reviewers usually answer within days; watch it with scripts/release/fdroid-status.sh $NAME"
