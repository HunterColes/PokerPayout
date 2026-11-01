#!/usr/bin/env bash
# Open (or find) the forge PR for the current card branch: merge the base in, run the gate, push, open the PR.
#   tools/pr.sh "<title>" --body-file <file>
# The base is FORGE_BASE (or COMPANY_BASE / CREW_BASE): the Company's day train day/YYYY-MM-DD[-N], or master. The base
# is merged into the branch (never a rebase), the gate (tools/check.sh) runs, the branch is pushed to the forge without
# force (tools/forge.sh git push: never to GitHub) and the PR goes into the base. GATE_SKIP=1 skips the gate (a docs-only
# branch); pr-merge.sh runs it again on the head anyway. Tests stub FORGE_SH and GATE_CMD.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
base="${FORGE_BASE:-${COMPANY_BASE:-${CREW_BASE:-master}}}"
[[ $base == master || $base =~ ^day/[0-9]{4}-[0-9]{2}-[0-9]{2}(-[0-9]{1,2})?$ ]] ||
  { echo "refusing: bad FORGE_BASE '$base' (master or day/YYYY-MM-DD[-N])" >&2; exit 2; }
F="${FORGE_SH:-tools/forge.sh}"; GATE="${GATE_CMD:-tools/check.sh}"
title="${1:?title}"; [[ "${2:-}" == --body-file ]] || { echo "usage: tools/pr.sh <title> --body-file <file>" >&2; exit 2; }
file="${3:?body file}"
branch="$(git branch --show-current)"
[[ "$branch" != master && "$branch" != day/* && -n "$branch" ]] || { echo "pr.sh: not on a card branch" >&2; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo "pr.sh: uncommitted or untracked files; commit or remove them" >&2; exit 2; }
"$F" git fetch -q --no-tags forge "+refs/heads/$base:refs/remotes/forge/$base"
git merge -q --no-edit "forge/$base"
[[ ${GATE_SKIP:-} == 1 ]] || "$GATE"
"$F" git push -q forge "HEAD:refs/heads/$branch"
n="$("$F" pr-find "$branch")"
if [[ -n "$n" ]]; then echo "PR #$n exists: $("$F" url "$n")"
else n="$("$F" pr-open "$branch" "$title" "$file" "$base")"; echo "opened PR #$n: $("$F" url "$n")"; fi
