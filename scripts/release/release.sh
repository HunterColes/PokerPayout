#!/usr/bin/env bash
# Poker Payout release pipeline: preflight -> tests -> device tour -> version bump ->
# signed release build -> APK checks -> reproducibility check -> (publish) release PR,
# green CI, merge, tag, GitHub release. F-Droid then picks the tag up on its own (see
# docs/RELEASING.md). master is protected, so the release commit always lands via a PR.
#
#   scripts/release/release.sh                      # dry run (the default): changes nothing
#   scripts/release/release.sh --publish --notes-file build/notes/1.2.0.md --bump minor
#
# Options
#   --dry-run              default. Builds the last commit in a throwaway clone with the
#                          debug key; never commits, tags or pushes
#   --publish              ship it. Must run from the main checkout (keystore lives there)
#   --bump patch|minor|major   next versionName (default patch); versionCode is always +1
#   --version X.Y.Z        explicit versionName instead of --bump
#   --notes-file FILE      Markdown release notes. Optional first line "# Title"
#   --title TEXT           release title (default: the notes' "# " heading)
#   --fdroid-notes FILE    plain-text F-Droid "What's New" (default: derived from the notes)
#   --skip-tests           skip unit tests
#   --skip-tour            skip scripts/device/tour.sh (emulator smoke tour)
#   --repro-check          also rebuild F-Droid style in a pristine clone and compare
#                          (default on for --publish, off for --dry-run)
#   --no-repro-check       skip that
#   --require-release-key  dry run: fail when the APK is not signed with the pinned key
#   --ignore-fdroid-jdk    publish even if F-Droid's buildserver lacks the JDK this needs
#   --preflight-only       run the preflight checks and stop
#   --keep-work            keep the temporary clones
#
# Output: one line per stage on stdout; full logs and artifacts in build/release/<tag>/.
# Exit codes: 0 ok, 1 failure, 3 signer is not the pinned release key.
set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$HERE/lib/common.sh"
META_PY=(python3 "$HERE/lib/metadata.py")

MODE=dry-run BUMP=patch VERSION="" NOTES_FILE="" TITLE="" FDROID_NOTES=""
SKIP_TESTS=0 SKIP_TOUR=0 REPRO="" REQUIRE_RELEASE_KEY=0 IGNORE_FDROID_JDK=0
PREFLIGHT_ONLY=0 KEEP_WORK=0

usage() { sed -n '2,29p' "$0" | sed -E 's/^# ?//'; }
while (($#)); do
  case "$1" in
    --dry-run) MODE=dry-run ;;
    --publish) MODE=publish ;;
    --bump) BUMP="${2:?--bump needs patch|minor|major}"; shift ;;
    --version) VERSION="${2:?--version needs X.Y.Z}"; shift ;;
    --notes-file) NOTES_FILE="${2:?--notes-file needs a path}"; shift ;;
    --title) TITLE="${2:?--title needs text}"; shift ;;
    --fdroid-notes) FDROID_NOTES="${2:?--fdroid-notes needs a path}"; shift ;;
    --skip-tests) SKIP_TESTS=1 ;;
    --skip-tour) SKIP_TOUR=1 ;;
    --repro-check) REPRO=1 ;;
    --no-repro-check) REPRO=0 ;;
    --require-release-key) REQUIRE_RELEASE_KEY=1 ;;
    --ignore-fdroid-jdk) IGNORE_FDROID_JDK=1 ;;
    --preflight-only) PREFLIGHT_ONLY=1 ;;
    --keep-work) KEEP_WORK=1 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1 (see --help)" ;;
  esac
  shift
done
if [[ -z $REPRO ]]; then [[ $MODE == publish ]] && REPRO=1 || REPRO=0; fi
[[ -z $NOTES_FILE || -f $NOTES_FILE ]] || die "notes file not found: $NOTES_FILE"
[[ -z $FDROID_NOTES || -f $FDROID_NOTES ]] || die "F-Droid notes file not found: $FDROID_NOTES"
[[ -n $NOTES_FILE ]] && NOTES_FILE="$(readlink -f "$NOTES_FILE")"
[[ -n $FDROID_NOTES ]] && FDROID_NOTES="$(readlink -f "$FDROID_NOTES")"

CHECKOUT="$(checkout_root "$HERE")"
MAIN="$(main_root "$HERE")"
GH_REPO="$(github_repo "$CHECKOUT")"
cd "$CHECKOUT"

# ---------------------------------------------------------------- versions
read -r CUR_NAME CUR_CODE < <("${META_PY[@]}" get-version app/build.gradle.kts)
if [[ -n $VERSION ]]; then
  [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "--version must look like X.Y.Z"
  NEW_NAME=$VERSION
else
  NEW_NAME="$(bump_version "$CUR_NAME" "$BUMP")" || die "--bump must be patch, minor or major"
fi
NEW_CODE=$((CUR_CODE + 1))
TAG="v$NEW_NAME"
APK_NAME="$APK_PREFIX-v$NEW_NAME-release.apk"
LAST_TAG="$(git describe --tags --abbrev=0 --match 'v[0-9]*' HEAD 2>/dev/null || true)"

OUT="$CHECKOUT/build/release/$TAG"
[[ $MODE == dry-run ]] && OUT+="-dry-run"
LOG_DIR="$OUT/logs"
rm -rf "$OUT"
mkdir -p "$LOG_DIR"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/pokerpayout-release.XXXXXX")"
exec > >(tee "$OUT/summary.txt") 2>&1

SRC="" PRE_SHA="" NEW_SHA="" COMMITTED=0 TAGGED=0 PUSHED=0 CHANGELOG_EXISTED=0
REL_BRANCH="release/$TAG" START_BRANCH="" BRANCHED=0
on_exit() {
  local rc=$?
  # Before anything is pushed, a failed publish leaves no trace: the release edits live only
  # on the local release branch (the tree was clean at preflight), so drop that branch.
  if [[ $MODE == publish && $BRANCHED == 1 && $PUSHED == 0 && $rc != 0 ]]; then
    [[ $TAGGED == 1 ]] && git -C "$SRC" tag -d "$TAG" >/dev/null 2>&1 || true
    git -C "$SRC" switch -q -f "$START_BRANCH"
    git -C "$SRC" branch -q -D "$REL_BRANCH" 2>/dev/null || true
    [[ $CHANGELOG_EXISTED == 1 ]] || rm -f "$SRC/$CHANGELOG_DIR_REL/$NEW_CODE.txt"
    note "rolled back: local $REL_BRANCH deleted, back on $START_BRANCH at ${PRE_SHA:0:9}"
  fi
  # Keep the clones for debugging when something failed after they were made.
  if [[ $KEEP_WORK == 1 || ($rc != 0 && (-d $WORK/src || -d $WORK/repro)) ]]; then
    note "work dir kept: $WORK"
  else
    rm -rf "$WORK"
  fi
  exit "$rc"
}
trap on_exit EXIT

STEP=0
newlog() { STEP=$((STEP + 1)); log="$(printf '%s/%02d-%s.log' "$LOG_DIR" "$STEP" "$1")"; }
fail_step() { line_bad "$1" "$2"; [[ -n ${3:-} ]] && show_log_tail "$3"; exit "${4:-1}"; }

printf 'Poker Payout %s (%s)  [%s]\n' "$TAG" "$NEW_CODE" "$MODE"
note "from $CUR_NAME ($CUR_CODE) · repo $GH_REPO · logs $LOG_DIR"

# ---------------------------------------------------------------- preflight
PF_ERRORS=0
# pf <always|publish> <0 = ok> <name> <message>
pf() {
  if [[ $2 == 0 ]]; then line_ok "$3" "$4"
  elif [[ $1 == always || $MODE == publish ]]; then line_bad "$3" "$4"; PF_ERRORS=$((PF_ERRORS + 1))
  else line_warn "$3" "$4 (blocks --publish)"; fi
}

missing=()
for t in python3 curl gh unzip; do command -v "$t" >/dev/null || missing+=("$t"); done
APKSIGNER="$(latest_build_tool apksigner || true)"; [[ -n $APKSIGNER ]] || missing+=(apksigner)
AAPT2="$(latest_build_tool aapt2 || true)"; [[ -n $AAPT2 ]] || missing+=(aapt2)
pf always "${#missing[@]}" tools "${missing[*]:-python3 curl gh unzip apksigner aapt2 present}${missing[*]:+ missing}"

# Modules without jvmToolchain build with the JDK that runs Gradle; on F-Droid that is $FDROID_JDK.
REQUIRED_JDK="$(required_jdk "$CHECKOUT" 2>"$WORK/jdk.err")" || die "$(cat "$WORK/jdk.err")"
REQUIRED_JDK="${REQUIRED_JDK:-$FDROID_JDK}"
RELEASE_JDK="$(find_jdk "$REQUIRED_JDK" || true)"
if [[ -z $RELEASE_JDK && $REQUIRED_JDK == 17 && -x $CHECKOUT/scripts/device/ensure-jdk17.sh ]]; then
  newlog ensure-jdk17
  RELEASE_JDK="$("$CHECKOUT/scripts/device/ensure-jdk17.sh" 2>"$log" || true)"
fi
if [[ -n $RELEASE_JDK ]]; then
  pf always 0 jdk "$REQUIRED_JDK: $(jdk_vendor "$RELEASE_JDK") $(jdk_version "$RELEASE_JDK") ($RELEASE_JDK)"
else
  pf always 1 jdk "no JDK ${REQUIRED_JDK:-?} found (jvmToolchain). Install: sudo apt install openjdk-${REQUIRED_JDK:-21}-jdk-headless, or set PP_JDK_HOME"
fi

# Upstream F-Droid metadata (truth for what F-Droid will do), with the local mirror as fallback.
UPSTREAM="$WORK/upstream.yml"
if fetch "$FDROIDDATA_RAW" "$UPSTREAM" 30 && grep -q '^Builds:' "$UPSTREAM"; then HAVE_UPSTREAM=1; else HAVE_UPSTREAM=0; fi
MIRROR_KEY="$("${META_PY[@]}" field "$META_REL" AllowedAPKSigningKeys)"
if [[ $HAVE_UPSTREAM == 1 ]]; then
  FDROID_KEY="$("${META_PY[@]}" field "$UPSTREAM" AllowedAPKSigningKeys)"
  FDROID_CODE="$("${META_PY[@]}" field "$UPSTREAM" CurrentVersionCode)"
elif [[ -n $LAST_TAG ]]; then
  git show "$LAST_TAG:$META_REL" >"$WORK/last-tag.yml" 2>/dev/null || true
  FDROID_KEY="$("${META_PY[@]}" field "$WORK/last-tag.yml" AllowedAPKSigningKeys 2>/dev/null || true)"
  FDROID_CODE=0
else
  FDROID_KEY="$MIRROR_KEY" FDROID_CODE=0
fi
ROTATION=0
[[ -n $FDROID_KEY && $FDROID_KEY != "$MIRROR_KEY" ]] && ROTATION=1

pf publish "$([[ $HAVE_UPSTREAM == 1 ]]; echo $?)" fdroiddata \
  "$([[ $HAVE_UPSTREAM == 1 ]] && echo "upstream metadata fetched (CurrentVersionCode $FDROID_CODE)" || echo "could not fetch upstream metadata; using the in-repo mirror")"
if [[ $ROTATION == 1 ]]; then
  line_warn "signing key" "rotation: F-Droid pins ${FDROID_KEY:0:12}…, this release pins ${MIRROR_KEY:0:12}… → fdroiddata MR needed (fdroid-mr.sh)"
else
  pf always "$([[ ${#MIRROR_KEY} == 64 ]]; echo $?)" "signing key" "pinned ${MIRROR_KEY:0:12}… (AllowedAPKSigningKeys)"
fi

# F-Droid's buildserver has only JDK $FDROID_JDK unless the recipe installs another via sudo:.
if [[ -n $REQUIRED_JDK ]]; then
  if [[ $REQUIRED_JDK == "$FDROID_JDK" ]]; then
    pf publish 0 "fdroid jdk" "jvmToolchain($REQUIRED_JDK) matches F-Droid's buildserver JDK"
  elif [[ $HAVE_UPSTREAM == 1 ]] && grep -q "openjdk-$REQUIRED_JDK-jdk" "$UPSTREAM"; then
    pf publish 0 "fdroid jdk" "recipe installs openjdk-$REQUIRED_JDK via sudo:"
  else
    pf publish "$((1 - IGNORE_FDROID_JDK))" "fdroid jdk" \
      "jvmToolchain($REQUIRED_JDK) but F-Droid's Debian 13 buildserver only has JDK $FDROID_JDK: its build would fail (docs/RELEASING.md#jdk)"
  fi
fi

BINARIES="$("${META_PY[@]}" field "$META_REL" Binaries)"
EXPECTED_URL="https://github.com/$GH_REPO/releases/download/$TAG/$APK_NAME"
BIN_URL="${BINARIES//%v/$NEW_NAME}"; BIN_URL="${BIN_URL//%c/$NEW_CODE}"
pf always "$([[ $BIN_URL == "$EXPECTED_URL" ]]; echo $?)" binaries "${BIN_URL:-no Binaries: in $META_REL}"

pf publish "$([[ $CHECKOUT == "$MAIN" ]]; echo $?)" checkout \
  "$([[ $CHECKOUT == "$MAIN" ]] && echo "main checkout $MAIN" || echo "$CHECKOUT is a linked worktree; publish from $MAIN (keystore lives there)")"

BRANCH="$(git symbolic-ref --short -q HEAD || echo detached)"
pf publish "$([[ $BRANCH == master ]]; echo $?)" branch "$BRANCH"

# A clean tree, except a pending key rotation in the mirror (new-signing-key.sh edits it).
DIRTY="$(git status --porcelain --untracked-files=normal)"
PENDING_KEY=0
if [[ $DIRTY == " M $META_REL" ]] && [[ "$(git diff --numstat -- "$META_REL")" == $'1\t1\t'* ]] \
   && git diff -U0 -- "$META_REL" | grep -q '^+AllowedAPKSigningKeys:'; then
  PENDING_KEY=1
  pf publish 0 "clean tree" "clean except the new AllowedAPKSigningKeys (goes into the release commit)"
else
  pf publish "$([[ -z $DIRTY ]]; echo $?)" "clean tree" \
    "$([[ -z $DIRTY ]] && echo clean || echo "$(wc -l <<<"$DIRTY") uncommitted/untracked paths (git status)")"
fi

newlog fetch
if git fetch --quiet origin master --tags 2>"$log"; then
  read -r BEHIND AHEAD < <(git rev-list --left-right --count origin/master...HEAD)
  if ((BEHIND > 0)); then pf publish 1 origin "$BEHIND commit(s) behind origin/master; pull first"
  elif ((AHEAD > 0)); then line_warn origin "$AHEAD local commit(s) not on origin yet; --publish pushes them"
  else pf publish 0 origin "in sync with origin/master"; fi
else
  pf publish 1 origin "git fetch failed"
fi

newlog gh-auth
if gh auth status >"$log" 2>&1; then pf publish 0 github "gh authenticated"
else pf publish 1 github "gh not authenticated (gh auth login)"; fi

TAG_TAKEN=""
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && TAG_TAKEN+=" local-tag"
[[ -n "$(git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null)" ]] && TAG_TAKEN+=" remote-tag"
gh release view "$TAG" --repo "$GH_REPO" >/dev/null 2>&1 && TAG_TAKEN+=" github-release"
pf publish "$([[ -z $TAG_TAKEN ]]; echo $?)" tag "$TAG ${TAG_TAKEN:+already taken:$TAG_TAKEN}${TAG_TAKEN:-is free}"

VERSION_OK=0
semver_gt "$NEW_NAME" "$CUR_NAME" || VERSION_OK=1
((NEW_CODE > ${FDROID_CODE:-0})) || VERSION_OK=1
pf always "$VERSION_OK" version "$CUR_NAME ($CUR_CODE) -> $NEW_NAME ($NEW_CODE); F-Droid has ${FDROID_CODE:-?}"

if [[ $MODE == publish ]]; then
  newlog verify-signing
  if "$HERE/verify-signing.sh" >"$log" 2>&1; then pf publish 0 signing "keystore.properties opens the key; certificate matches the pin"
  else pf publish 1 signing "$(grep -m1 '✘' "$log" | sed 's/^ *✘ *//' || echo "verify-signing.sh failed")"; fi
elif [[ -f $MAIN/keystore.properties ]]; then
  line_ok signing "keystore.properties present in $MAIN (opened only by --publish)"
else
  line_warn signing "no keystore.properties in $MAIN (needed for --publish)"
fi

if ((PF_ERRORS > 0)); then
  line_bad preflight "$PF_ERRORS problem(s); nothing was changed"
  exit 1
fi
[[ $PREFLIGHT_ONLY == 1 ]] && { line_ok preflight "passed (--preflight-only)"; exit 0; }

# ---------------------------------------------------------------- source tree
if [[ $MODE == publish ]]; then
  SRC="$CHECKOUT"
else
  SRC="$WORK/src"
  git clone --quiet --no-hardlinks "$CHECKOUT" "$SRC"
  git -C "$SRC" checkout --quiet --detach "$(git rev-parse HEAD)"
  [[ $PENDING_KEY == 1 ]] && cp "$CHECKOUT/$META_REL" "$SRC/$META_REL"
  unset ORG_GRADLE_PROJECT_RELEASE_STORE_FILE ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD \
        ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD
fi
PRE_SHA="$(git -C "$SRC" rev-parse HEAD)"

# ---------------------------------------------------------------- gate: tests + tour
if [[ $SKIP_TESTS == 1 ]]; then
  line_skip "unit tests" "skipped (--skip-tests)"
else
  t0=$(date +%s); newlog unit-tests
  # shellcheck disable=SC2086
  gradle_run "$SRC" ./gradlew ${PP_TEST_TASKS:-testDebugUnitTest} >"$log" 2>&1 \
    || fail_step "unit tests" "failed" "$log"
  line_ok "unit tests" "${PP_TEST_TASKS:-testDebugUnitTest} passed ($(elapsed "$t0"))"
fi

TOUR="$SRC/scripts/device/tour.sh"
if [[ $SKIP_TOUR == 1 ]]; then
  line_skip "device tour" "skipped (--skip-tour)"
elif [[ ! -x $TOUR ]]; then
  line_skip "device tour" "skipped (scripts/device/tour.sh not in this tree)"
else
  t0=$(date +%s); newlog device-tour
  # One emulator per machine: wait for any agent's tour to finish, and shut it down after.
  flock /tmp/pokerpayout-emulator.lock "$TOUR" --stop >"$log" 2>&1 \
    || fail_step "device tour" "failed (report: build/device-reports/latest)" "$log"
  line_ok "device tour" "passed ($(elapsed "$t0"))"
fi

# ---------------------------------------------------------------- release notes
if [[ -n $NOTES_FILE ]]; then
  [[ -z $TITLE ]] && TITLE="$("${META_PY[@]}" notes-title "$NOTES_FILE")"
  "${META_PY[@]}" notes-body "$NOTES_FILE" >"$OUT/notes-body.md"
  NOTES_SRC="$NOTES_FILE"
else
  {
    echo "# ${TITLE:-Maintenance update}"
    echo
    git -C "$SRC" log --no-merges --format='- %s' "${LAST_TAG:+$LAST_TAG..}HEAD" \
      | grep -vE '^- v[0-9]+\.[0-9]+\.[0-9]+( |$)' || true
  } >"$OUT/notes.draft.md"
  NOTES_SRC="$OUT/notes.draft.md"
  "${META_PY[@]}" notes-body "$NOTES_SRC" >"$OUT/notes-body.md"
fi
TITLE="${TITLE:-Maintenance update}"
[[ -s $OUT/notes-body.md ]] || fail_step notes "release notes are empty"

ROTATION_MD="**One-time reinstall needed.** This release is signed with a new signing key (the old key was lost), so Android cannot install it over an older Poker Payout. Uninstall Poker Payout, then install this version. Uninstalling deletes the app's saved data. Later updates install normally."
ROTATION_TXT="One-time reinstall needed: this version is signed with a new key (the old key was lost), so it cannot update the installed app. Uninstall Poker Payout, then install this version. Uninstalling deletes saved data. Later updates install normally."

if [[ -n $FDROID_NOTES ]]; then
  sed -e 's/[[:space:]]*$//' "$FDROID_NOTES" >"$OUT/fdroid-changelog.txt"
else
  limit=$FDROID_WHATSNEW_LIMIT
  [[ $ROTATION == 1 ]] && limit=$((FDROID_WHATSNEW_LIMIT - ${#ROTATION_TXT} - 2))
  "${META_PY[@]}" fdroid-changelog "$NOTES_SRC" "$limit" >"$OUT/fdroid-changelog.txt"
fi
if [[ $ROTATION == 1 ]] && ! head -c 300 "$OUT/fdroid-changelog.txt" | grep -qi reinstall; then
  { echo "$ROTATION_TXT"; echo; cat "$OUT/fdroid-changelog.txt"; } >"$WORK/cl" && mv "$WORK/cl" "$OUT/fdroid-changelog.txt"
fi
CL_LEN="$(python3 -c 'import sys; print(len(open(sys.argv[1], encoding="utf-8").read().rstrip("\n")))' "$OUT/fdroid-changelog.txt")"
[[ -s $OUT/fdroid-changelog.txt ]] || fail_step notes "F-Droid changelog is empty"
((CL_LEN <= FDROID_WHATSNEW_LIMIT)) \
  || fail_step notes "F-Droid changelog is $CL_LEN chars; F-Droid cuts at $FDROID_WHATSNEW_LIMIT (shorten $FDROID_NOTES)"
line_ok notes "\"$TITLE\"; F-Droid What's New $CL_LEN/$FDROID_WHATSNEW_LIMIT chars$([[ $ROTATION == 1 ]] && echo ", with reinstall notice")"

# ---------------------------------------------------------------- bump
mkdir -p "$WORK/backup"
cp "$SRC/app/build.gradle.kts" "$WORK/backup/build.gradle.kts"
cp "$SRC/$META_REL" "$WORK/backup/mirror.yml"
CHANGELOG="$SRC/$CHANGELOG_DIR_REL/$NEW_CODE.txt"
[[ -e $CHANGELOG ]] && CHANGELOG_EXISTED=1

"${META_PY[@]}" set-version "$SRC/app/build.gradle.kts" "$NEW_NAME" "$NEW_CODE"
if [[ $HAVE_UPSTREAM == 1 ]]; then cp "$UPSTREAM" "$SRC/$META_REL"; fi
if [[ $ROTATION == 1 ]]; then
  "${META_PY[@]}" rotate-key "$SRC/$META_REL" "$MIRROR_KEY" "$NEW_CODE" \
    "signing key lost; superseded by $NEW_NAME, signed with a new key"
fi
"${META_PY[@]}" add-build "$SRC/$META_REL" "$NEW_NAME" "$NEW_CODE" "$TAG"
"${META_PY[@]}" check-yml "$SRC/$META_REL" "$NEW_NAME" "$NEW_CODE" \
  || fail_step bump "metadata mirror failed validation"
mkdir -p "$(dirname "$CHANGELOG")"
cp "$OUT/fdroid-changelog.txt" "$CHANGELOG"
cp "$SRC/$META_REL" "$OUT/fdroiddata.yml"

GIT_ID=()
[[ $MODE == dry-run ]] && GIT_ID=(-c user.name="release dry run" -c user.email=dry-run@localhost)
if [[ $MODE == publish ]]; then
  START_BRANCH="$(git -C "$SRC" symbolic-ref --short HEAD)"
  git -C "$SRC" switch -q -c "$REL_BRANCH"
  BRANCHED=1
fi
git -C "$SRC" add app/build.gradle.kts "$META_REL" "$CHANGELOG_DIR_REL/$NEW_CODE.txt"
git -C "$SRC" "${GIT_ID[@]}" commit -q -m "$TAG $TITLE"
COMMITTED=1
NEW_SHA="$(git -C "$SRC" rev-parse HEAD)"
git -C "$SRC" show --stat --format='%H %s' HEAD >"$OUT/release-commit.txt"
git -C "$SRC" show --format= HEAD >"$OUT/release-commit.diff"
line_ok bump "$CUR_NAME ($CUR_CODE) -> $NEW_NAME ($NEW_CODE), mirror + $CHANGELOG_DIR_REL/$NEW_CODE.txt, commit ${NEW_SHA:0:9}"

# ---------------------------------------------------------------- build
t0=$(date +%s); newlog build-release
write_local_properties "$SRC"
rm -f "$SRC"/app/build/outputs/apk/release/*.apk
gradle_run "$SRC" ./gradlew clean :app:assembleRelease >"$log" 2>&1 || fail_step build "assembleRelease failed" "$log"
BUILT="$SRC/app/build/outputs/apk/release/$APK_NAME"
[[ -f $BUILT ]] || fail_step build "expected $APK_NAME, got: $(ls "$SRC"/app/build/outputs/apk/release/ 2>/dev/null | tr '\n' ' ')" "$log"
APK="$OUT/$APK_NAME"
cp "$BUILT" "$APK"
APK_SHA256="$(sha256sum "$APK" | cut -d' ' -f1)"
echo "$APK_SHA256  $APK_NAME" >"$OUT/SHA256SUMS"
line_ok build "$APK_NAME $(du -h "$APK" | cut -f1) sha256 ${APK_SHA256:0:16}… ($(elapsed "$t0"))"

# ---------------------------------------------------------------- APK checks
newlog apk-checks
"$APKSIGNER" verify --verbose --print-certs "$APK" >"$log" 2>&1 || fail_step apk "apksigner verify failed" "$log"
# apksigner prints "Signer #1 certificate …" (older) or "V2 Signer: certificate …" (37+).
SIGNERS="$(sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' "$log" | sort -u)"
SIGNER="$(head -n1 <<<"$SIGNERS")"
[[ -n $SIGNER && $(wc -l <<<"$SIGNERS") == 1 ]] || fail_step apk "could not read exactly one signing certificate" "$log"
grep -q 'Number of signers: 1' "$log" || fail_step apk "expected exactly one signer" "$log"
grep -q 'APK Signature Scheme v2): true' "$log" || fail_step apk "no v2 signature (F-Droid needs v2+)" "$log"
"$AAPT2" dump badging "$APK" >>"$log" 2>&1 || fail_step apk "aapt2 dump badging failed" "$log"
grep -q "^package: name='$APP_ID' versionCode='$NEW_CODE' versionName='$NEW_NAME'" "$log" \
  || fail_step apk "manifest is not $APP_ID $NEW_NAME ($NEW_CODE)" "$log"
grep -q 'application-debuggable' "$log" && fail_step apk "APK is debuggable" "$log"
unzip -l "$APK" >"$WORK/apk-listing.txt"
grep -q 'baseline\.prof' "$WORK/apk-listing.txt" && fail_step apk "baseline profile present (breaks reproducibility)"
VCS_REV="$(unzip -p "$APK" META-INF/version-control-info.textproto 2>/dev/null | sed -n 's/.*revision: "\([0-9a-f]*\)".*/\1/p')"
[[ -z $VCS_REV || $VCS_REV == "$NEW_SHA" ]] || fail_step apk "APK embeds commit $VCS_REV, expected $NEW_SHA"
line_ok apk "$APP_ID $NEW_NAME ($NEW_CODE), v2-signed, not debuggable, built from ${NEW_SHA:0:9}"

if [[ -n $SIGNER && ",$MIRROR_KEY," == *",$SIGNER,"* ]]; then
  line_ok signer "release key ${SIGNER:0:12}… = AllowedAPKSigningKeys"
elif [[ $MODE == publish || $REQUIRE_RELEASE_KEY == 1 ]]; then
  fail_step signer "signed by ${SIGNER:0:12}…, but AllowedAPKSigningKeys pins ${MIRROR_KEY:0:12}…" "" 3
else
  line_warn signer "signed by ${SIGNER:0:12}… (debug key, expected in a dry run); release key is ${MIRROR_KEY:0:12}…"
fi

# ---------------------------------------------------------------- reproducibility
if [[ $REPRO == 1 ]]; then
  t0=$(date +%s); newlog repro-build
  REPRO_SRC="$WORK/repro"
  {
    git clone --quiet --no-hardlinks "$SRC" "$REPRO_SRC"
    git -C "$REPRO_SRC" checkout --quiet --detach "$NEW_SHA"
    # What F-Droid does: strip signing configs, local.properties, `gradle clean`, then
    # `gradle assembleRelease`, both from the `subdir: app` directory.
    python3 "$HERE/lib/fdroid_strip_signing.py" "$REPRO_SRC"
    write_local_properties "$REPRO_SRC"; write_local_properties "$REPRO_SRC/app"
    gradle_run "$REPRO_SRC/app" ../gradlew clean
    gradle_run "$REPRO_SRC/app" ../gradlew assembleRelease
  } >"$log" 2>&1 || fail_step repro "F-Droid-style build failed" "$log"
  REPRO_APK="$(ls "$REPRO_SRC"/app/build/outputs/apk/release/*.apk 2>/dev/null | head -n1)"
  [[ -n $REPRO_APK ]] || fail_step repro "F-Droid-style build produced no APK" "$log"
  cp "$REPRO_APK" "$OUT/fdroid-style-unsigned.apk"
  newlog repro-compare
  python3 "$HERE/lib/apkdiff.py" "$APK" "$REPRO_APK" >"$log" 2>&1 \
    || fail_step repro "differs from the F-Droid-style build; F-Droid would reject it" "$log"
  if command -v apksigcopier >/dev/null; then
    apksigcopier compare "$APK" --unsigned "$REPRO_APK" >>"$log" 2>&1 \
      || fail_step repro "apksigcopier compare failed" "$log"
  fi
  line_ok repro "F-Droid-style rebuild is identical outside the signature ($(elapsed "$t0"))"
else
  line_skip repro "skipped (--repro-check to enable)"
fi

# ---------------------------------------------------------------- GitHub notes
# The release page reads: our blurb, then GitHub's generated "What's Changed" (merged PRs
# since the last tag, added once the tag exists), then the APK/signing footer.
{
  [[ $ROTATION == 1 ]] && ! grep -qi reinstall "$OUT/notes-body.md" && { echo "> $ROTATION_MD"; echo; }
  cat "$OUT/notes-body.md"
} >"$OUT/notes-head.md"
{
  echo "---"
  echo "\`$APK_NAME\` · SHA-256 \`$APK_SHA256\`  "
  echo "Signing certificate SHA-256 \`$SIGNER\`  "
  echo "Also on F-Droid: https://f-droid.org/packages/$APP_ID/"
} >"$OUT/notes-foot.md"
{ cat "$OUT/notes-head.md"; echo; cat "$OUT/notes-foot.md"; } >"$OUT/release-notes.md"

if [[ $MODE == dry-run ]]; then
  line_ok "dry run" "nothing committed, tagged, pushed or released"
  note "publish would: commit \"$TAG $TITLE\" on $REL_BRANCH, open a PR, wait for green CI, merge it,"
  note "               tag the release commit $TAG, gh release \"$TAG $TITLE\" with $APK_NAME -> $EXPECTED_URL"
  note "artifacts: $OUT (release-notes.md, fdroid-changelog.txt, fdroiddata.yml, release-commit.diff)"
  [[ $ROTATION == 1 ]] && note "key rotation: after publishing, run scripts/release/fdroid-mr.sh --publish"
  exit 0
fi

# ---------------------------------------------------------------- publish
# master only accepts PRs with green CI, so the release commit goes in through a PR. The tag
# points at the release commit itself (exactly what was built and verified above); merging
# with a merge commit puts that commit on master.

# Waits for every check on the PR to finish; fails on the first failed or cancelled one.
wait_for_checks() {
  local url=$1 deadline=$(($(date +%s) + 45 * 60)) counts total pending failed
  while (($(date +%s) < deadline)); do
    counts="$(gh pr view "$url" --repo "$GH_REPO" --json statusCheckRollup --jq '
      [.statusCheckRollup[] | (.conclusion // .state // "") | ascii_upcase] as $c
      | "\($c | length) \([$c[] | select(. == "" or . == "PENDING" or . == "EXPECTED")] | length) \([$c[] | select(. == "FAILURE" or . == "CANCELLED" or . == "TIMED_OUT" or . == "ERROR" or . == "ACTION_REQUIRED" or . == "STARTUP_FAILURE")] | length)"' 2>/dev/null || echo "0 0 0")"
    read -r total pending failed <<<"$counts"
    echo "$(date +%T) checks: $total, pending $pending, failed $failed"
    ((failed > 0)) && return 1
    ((total > 0 && pending == 0)) && return 0
    sleep 30
  done
  echo "timed out after 45 min"
  return 1
}

newlog push
git -C "$SRC" push -q origin "refs/heads/$REL_BRANCH" >"$log" 2>&1 \
  || fail_step push "could not push $REL_BRANCH (nothing is public yet)" "$log"
PUSHED=1
newlog pr
PR_URL="$(gh pr create --repo "$GH_REPO" --base master --head "$REL_BRANCH" \
  --title "$TAG $TITLE" --body-file "$OUT/release-notes.md" 2>"$log")" \
  || fail_step pr "could not open the release PR ($REL_BRANCH is pushed; nothing released)" "$log"
line_ok pr "$PR_URL"

t0=$(date +%s); newlog ci
sleep 20 # let GitHub register the checks
wait_for_checks "$PR_URL" >"$log" 2>&1 \
  || fail_step ci "CI failed or timed out on $PR_URL; nothing released. Fix on $REL_BRANCH, then merge, tag and release by hand" "$log"
line_ok ci "all checks green ($(elapsed "$t0"))"

newlog merge
# Run outside the checkout so gh only deletes the remote branch and leaves the local tree alone.
(cd "$WORK" && gh pr merge "$PR_URL" --repo "$GH_REPO" --merge --delete-branch \
  --subject "$TAG $TITLE (#${PR_URL##*/})") >"$log" 2>&1 \
  || fail_step merge "could not merge $PR_URL; nothing released" "$log"
line_ok merge "release commit ${NEW_SHA:0:9} is on master"

git -C "$SRC" tag -a "$TAG" "$NEW_SHA" -F - <<<"$TAG $TITLE

$(cat "$OUT/notes-body.md")"
TAGGED=1
newlog tag
git -C "$SRC" push -q origin "refs/tags/$TAG" >"$log" 2>&1 \
  || fail_step tag "could not push $TAG (master has the release commit; retry: git push origin $TAG)" "$log"
line_ok tag "$TAG -> ${NEW_SHA:0:9}"

newlog notes-generated
GENERATED="$(gh api "repos/$GH_REPO/releases/generate-notes" -f tag_name="$TAG" \
  ${LAST_TAG:+-f previous_tag_name="$LAST_TAG"} --jq .body 2>"$log" || true)"
{
  cat "$OUT/notes-head.md"
  [[ -n $GENERATED ]] && { echo; echo "$GENERATED"; }
  echo
  cat "$OUT/notes-foot.md"
} >"$OUT/release-notes.md"

newlog gh-release
if ! gh release create "$TAG" "$APK" --repo "$GH_REPO" --title "$TAG $TITLE" \
     --notes-file "$OUT/release-notes.md" --verify-tag --latest >"$log" 2>&1; then
  line_bad release "gh release create failed; the tag is already public. Retry with:"
  note "gh release create $TAG '$APK' --repo $GH_REPO --title '$TAG $TITLE' --notes-file '$OUT/release-notes.md' --verify-tag --latest"
  show_log_tail "$log"
  exit 1
fi
line_ok release "https://github.com/$GH_REPO/releases/tag/$TAG"

ok_dl=1
for _ in 1 2 3 4 5 6; do
  if fetch "$BIN_URL" "$WORK/dl.apk" 120 && [[ "$(sha256sum "$WORK/dl.apk" | cut -d' ' -f1)" == "$APK_SHA256" ]]; then ok_dl=0; break; fi
  sleep 10
done
if [[ $ok_dl == 0 ]]; then line_ok binaries "F-Droid's Binaries URL serves this exact APK"
else line_bad binaries "could not download $BIN_URL with the expected sha256; check the release asset"; fi

if git -C "$SRC" switch -q "$START_BRANCH" && git -C "$SRC" pull -q --ff-only origin "$START_BRANCH" \
   && git -C "$SRC" branch -q -D "$REL_BRANCH"; then
  line_ok local "back on $START_BRANCH at $(git -C "$SRC" rev-parse --short HEAD)"
else
  line_warn local "couldn't return to an up-to-date $START_BRANCH; run: git switch $START_BRANCH && git pull --ff-only"
fi

echo
if [[ $ROTATION == 1 ]]; then
  echo "F-Droid: this release changes the signing key, so F-Droid will NOT publish it on its own."
  echo "  Its checkupdates bot will still add the build from the tag, but the build fails the signer"
  echo "  check until fdroiddata pins the new key. Open the merge request now:"
  echo "    scripts/release/fdroid-mr.sh --publish        (dry run first: scripts/release/fdroid-mr.sh)"
else
  echo "F-Droid (nothing to do; v1.1.12 took ~16 h from tag to built, published the same day):"
  echo "  1. checkupdates bot sees tag $TAG and commits 'Update Poker Payout to $NEW_CODE' to fdroiddata (hours)"
  echo "  2. buildserver builds $TAG from source, downloads $APK_NAME, checks it is identical (hours to ~2 days)"
  echo "  3. next index publish ships this signed APK in the F-Droid client (usually within a day after)"
fi
echo "  Track it: scripts/release/fdroid-status.sh $NEW_NAME"
