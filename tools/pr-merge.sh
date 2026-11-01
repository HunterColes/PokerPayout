#!/usr/bin/env bash
# Land a forge PR into the Company's day train: refuse without "Reviewer: pass" in its body, rerun the gate
# (tools/check.sh) on exactly its head in a temporary worktree, squash-merge that head only. From PersonalSite's agent
# template; the Company runs it as its merge_cmd (company.toml, project poker) in its merge worktree.
#   tools/pr-merge.sh <n> [--no-review] [--gate-files a,b]
# FORGE_BASE names the base the PR must go into: a day train day/YYYY-MM-DD[-N] (the Company always sets it), or master
# (by hand only); anything else exits 2. The head must hold the base's tip (merged in, never rebased), checked before
# the gate and again right before the merge, with the wording the Company keys on: `refusing: PR #<n> is behind <base>`.
# When FORGE_BASE is set a change to a gate file (tools/gate-files.sh, judged with this checkout's copy) that
# --gate-files does not name is refused; without it the gate files are only listed.
# Every failure's line starts `refusing:`, `RED:` (the gate), `REFUSED:` (gate files) or `NOT MERGED:` (the forge
# refused the merge: `HTTP 405/409 ...`, which the Company sends to rework). The gate's own output is printed as its
# last 40 lines, each indented two spaces, so nothing a PR's tests print can pass for one of those lines (or for
# `MERGED`). Success's last line is exactly `MERGED #<n> -> <base> <short sha>`.
# Tests stub the forge and the gate: FORGE_SH (default tools/forge.sh) and GATE_CMD (default tools/check.sh, run inside
# the PR head's worktree).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
n="${1:-}"; shift || true
[[ $n =~ ^[1-9][0-9]{0,6}$ ]] || { echo "refusing: not a PR number: ${n:-none}"; exit 2; }
review=1; named=""
while (($#)); do case "$1" in
  --no-review) review=0; shift ;;
  --gate-files) named="${2-}"; shift 2 || { echo "refusing: --gate-files needs a list"; exit 2; } ;;
  *) echo "refusing: unknown argument $1"; exit 2 ;;
esac; done
enforce=0; [[ -z ${FORGE_BASE:-} ]] || enforce=1
BASE="${FORGE_BASE:-master}"
[[ $BASE == master || $BASE =~ ^day/[0-9]{4}-[0-9]{2}-[0-9]{2}(-[0-9]{1,2})?$ ]] ||
  { echo "refusing: bad FORGE_BASE '$BASE' (master or day/YYYY-MM-DD[-N])"; exit 2; }
F="${FORGE_SH:-$PWD/tools/forge.sh}"; GATE="${GATE_CMD:-tools/check.sh}"
trap 'echo "refusing: PR #$n: unexpected failure at tools/pr-merge.sh line $LINENO"' ERR

info="$("$F" pr "$n")" || { echo "refusing: PR #$n: forge.sh pr failed"; exit 1; }
read -r state merged sha ref base <<<"$info"
[[ $state == open && $merged == unmerged ]] || { echo "refusing: PR #$n is $state/$merged"; exit 1; }
[[ $base == "$BASE" ]] || { echo "refusing: PR #$n goes into $base, not $BASE"; exit 1; }
[[ ! $ref =~ ^day/ && $ref != master ]] || { echo "refusing: PR #$n is from $ref, not a card branch"; exit 1; }
if ((review)); then
  "$F" pr-body "$n" | grep -qx 'Reviewer: pass' || { echo "refusing: PR #$n has no 'Reviewer: pass' line"; exit 1; }
fi
"$F" git fetch -q --no-tags forge "+refs/heads/$BASE:refs/remotes/forge/$BASE" "+refs/pull/$n/head:refs/forge-pr/$n"
[[ "$(git rev-parse "refs/forge-pr/$n")" == "$sha" ]] ||
  { echo "refusing: the forge's refs/pull/$n/head is not the PR's head ($sha)"; exit 1; }
behind() {
  git merge-base --is-ancestor "forge/$BASE" "$sha" ||
    { echo "refusing: PR #$n is behind $BASE (its head lacks $(git rev-parse --short "forge/$BASE")): merge forge/$BASE into $ref, never rebase"; exit 1; }
}
behind

touched="$(git diff --raw --no-renames --no-abbrev -z "$(git merge-base "forge/$BASE" "$sha")" "$sha" | tools/gate-files.sh --raw)"
if [[ -n $touched ]]; then
  missing=""
  while IFS= read -r f; do [[ ",$named," == *",$f,"* ]] || missing+=" $f"; done <<<"$touched"
  if [[ -n $missing && $enforce == 1 ]]; then
    echo "REFUSED: PR #$n changes gate files not named in --gate-files:$missing"; exit 1
  fi
  echo "gate files changed: $(tr '\n' ' ' <<<"$touched")"
fi

wt="$(mktemp -d)"; log="$(mktemp)"
trap 'git worktree remove --force "$wt" >/dev/null 2>&1 || true; rm -rf "$wt" "$log"' EXIT
git worktree add -q --detach "$wt" "$sha"
# In a `||` list a red gate trips neither errexit nor the ERR trap.
rc=0
( cd "$wt" && "$GATE" ) >"$log" 2>&1 || rc=$?
echo "gate on ${sha:0:10}, last 40 lines:"
tail -n 40 "$log" | sed 's/^/  /'
((rc == 0)) || { echo "RED: PR #$n: gate red on ${sha:0:10}: $(grep -v '^[[:space:]]*$' "$log" | tail -1 | cut -c1-300)"; exit 1; }

"$F" git fetch -q --no-tags forge "+refs/heads/$BASE:refs/remotes/forge/$BASE"
behind
mout="$("$F" merge "$n" "$sha" 2>&1)" || { echo "NOT MERGED: PR #$n: $(tr '\n' ' ' <<<"$mout" | cut -c1-400)"; exit 1; }
echo "$mout"
"$F" git fetch -q --no-tags forge "+refs/heads/$BASE:refs/remotes/forge/$BASE"
echo "MERGED #$n -> $BASE $(git rev-parse --short "forge/$BASE")"
