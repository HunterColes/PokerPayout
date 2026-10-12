#!/usr/bin/env bash
# Does this change touch documentation only? Then CI takes the fast path: ci.yml's "Unit tests,
# lint, detekt" (the check master's branch protection requires) still reports, but passes in
# seconds without Gradle, and device.yml's tour and monkey.yml are skipped. Any other change runs
# everything, as before.
#
# Documentation, the one list (is_docs below): Markdown outside module sources, docs/, LICENSE,
# .github/ISSUE_TEMPLATE/, .github/FUNDING.yml, metadata/ (the store listing's text and pictures,
# the F-Droid metadata mirror) and crypto/ (the donation page and its pictures). Nothing builds,
# tests or ships these. Code, Gradle files, resources, tests, goldens, scripts, workflows and
# anything under a module's src/ (Markdown included) are not documentation.
#
# What it compares, in GitHub Actions:
#   pull_request    the merge commit GitHub tests, with its first parent: what the pull request
#                   changes on its base as it is now
#   push to master  what the push changed (the event's `before` to HEAD)
#   other pushes    the branch against master, from where they meet: what its pull request
#                   changes, so a branch's push run and its pull request run always agree
#   anything else   (workflow_dispatch: by hand, goldens.yml, listing.yml) runs everything
# When in doubt it says false, and everything runs: no common history, an unknown commit, nothing
# changed, a git error.
#
#   scripts/ci/docs-only.sh                  in Actions, the event's change; else origin/master...HEAD
#   scripts/ci/docs-only.sh <base> [<head>]  <base>...<head> as git diff reads it (head: HEAD)
#
# Prints what it compared, the changed files that aren't documentation (or all of them, if they all
# are) and docs_only=true|false. In Actions it also writes docs_only to $GITHUB_OUTPUT, and a line
# to the step summary on the fast path. Exits 0 unless the arguments are wrong. Needs the history of
# both commits: the workflows check out with fetch-depth 0 and filter blob:none (every commit and
# tree, but file contents only for the working tree).
set -uo pipefail

DEFAULT_BRANCH=master
ZERO=0000000000000000000000000000000000000000

is_docs() {
  case "$1" in
    # A module's sources, Markdown included: it could ship in the app or be read by a test.
    src/* | */src/*) return 1 ;;
    *.md | docs/* | LICENSE | LICENSE.* | metadata/* | crypto/*) return 0 ;;
    .github/ISSUE_TEMPLATE/* | .github/FUNDING.yml) return 0 ;;
    *) return 1 ;;
  esac
}

case "${1:-}" in
  -h | --help) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  -*) echo "docs-only: unknown option $1 (try --help)" >&2; exit 2 ;;
esac
if (($# > 2)); then echo "docs-only: at most two commits (try --help)" >&2; exit 2; fi

cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)" || exit 2

base="" head=HEAD what=""
if (($# >= 1)); then
  base=$1 head=${2:-HEAD} what="$base...$head"
elif [[ ${GITHUB_ACTIONS:-} == true ]]; then
  case "${GITHUB_EVENT_NAME:-}" in
    pull_request)
      # actions/checkout checks out refs/pull/<n>/merge: the pull request merged into its base.
      if git rev-parse -q --verify 'HEAD^2' >/dev/null; then
        base='HEAD^1' what="the pull request against its base (HEAD^1...HEAD)"
      fi
      ;;
    push)
      if [[ ${GITHUB_REF:-} == "refs/heads/$DEFAULT_BRANCH" ]]; then
        before="$(jq -r '.before // empty' "${GITHUB_EVENT_PATH:-/dev/null}" 2>/dev/null)"
        if [[ -n $before && $before != "$ZERO" ]]; then
          base=$before what="the push to $DEFAULT_BRANCH ($before...HEAD)"
        fi
      else
        base="origin/$DEFAULT_BRANCH"
        what="${GITHUB_REF#refs/heads/} against $DEFAULT_BRANCH ($base...HEAD)"
      fi
      ;;
  esac
else
  base="origin/$DEFAULT_BRANCH" what="origin/$DEFAULT_BRANCH...HEAD"
fi

docs_only=false
files="" others=() count=0
if [[ -z $base ]]; then
  why="nothing to compare with on a ${GITHUB_EVENT_NAME:-local} run"
elif ! files="$(git -c core.quotePath=false \
  diff --no-renames --name-only "$base...$head" -- 2>&1)"; then
  why="git diff $base...$head failed: $files"
  files=""
elif [[ -z $files ]]; then
  why="no file changed"
else
  while IFS= read -r file; do
    ((count += 1))
    is_docs "$file" || others+=("$file")
  done <<<"$files"
  if ((${#others[@]} == 0)); then
    docs_only=true why="all $count changed files are documentation"
  else
    why="${#others[@]} of $count changed files aren't documentation"
  fi
fi

echo "Compared: ${what:-nothing}"
echo "Verdict: $why"
if [[ $docs_only == true ]]; then
  mapfile -t shown <<<"$files"
  label="docs"
else
  shown=("${others[@]}")
  label="not docs"
fi
if ((${#shown[@]} > 0)); then
  printf "  $label  %s\n" "${shown[@]:0:25}"
  if ((${#shown[@]} > 25)); then echo "  ...and $((${#shown[@]} - 25)) more"; fi
fi
echo "docs_only=$docs_only"

if [[ -n ${GITHUB_OUTPUT:-} ]]; then
  echo "docs_only=$docs_only" >>"$GITHUB_OUTPUT"
fi
if [[ $docs_only == true && -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  echo "**Documentation only** ($why, scripts/ci/docs-only.sh): nothing to build, test or run." \
    >>"$GITHUB_STEP_SUMMARY"
fi
exit 0
