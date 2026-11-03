#!/usr/bin/env bash
# The few Forgejo API calls the Company's agents and merge jobs need (the forge is `git config company.forge`, set in
# each checkout and never committed: this repo is public; repo read from the `forge` remote's URL), with curl, as the machine user `agent` (write access, no admin). Adapted from PersonalSite's
# agent template for this repo: the default branch is master, and the forge needs a login even to read, so git goes
# through `forge.sh git` too.
# The token is never an argument, a URL or a log line: curl reads its header line (`Authorization: token ...`) from a
# 0600 file outside the repo, $FORGE_HEADER (default ~/.config/company/forge.header), and git gets the same line in
# its environment only (GIT_CONFIG_*), scoped to the forge's URL, so it never goes to GitHub (`origin`).
# Replies are parsed with python3's json (stdlib only).
#   forge.sh pr-list                         open PRs: #n head -> base, head sha, title
#   forge.sh pr-find <branch>                the open PR's number from <branch>, or nothing
#   forge.sh pr-open <branch> <title> <body-file> [base]   open a PR (base: master or a day train), print its number
#   forge.sh pr <n>                          state merged|unmerged head-sha head-branch base-branch
#   forge.sh pr-body <n>                     the PR's body
#   forge.sh pr-set-body <n> <file>          replace the PR's body
#   forge.sh pr-title <n> <title>            rename the PR (the squash commit's subject)
#   forge.sh pr-diff <n> [--stat|--name-only]   diff of the PR head against its merge base with the PR's own base
#                                            (master or a day/* train; fetched, never checked out)
#   forge.sh gate-files <n>                  the PR's gate files (tools/gate-files.sh) over that diff, one a line
#   forge.sh comment <n> <file>              post <file> as a review comment on the PR (event COMMENT)
#   forge.sh comments <n>                    the PR's reviews
#   forge.sh merge <n> <sha>                 squash-merge only while the head is still <sha>, delete the branch; a
#                                            day/* PR into master is refused (Hunter merges those); a refusal prints
#                                            `forge.sh: merge #<n>: HTTP <code> <reply>`, exit 1
#   forge.sh url <n>                         the PR's web address
#   forge.sh git fetch|push|ls-remote ARGS...   git against the forge as agent (never a GitHub remote)
set -euo pipefail
HDR="${FORGE_HEADER:-$HOME/.config/company/forge.header}"
[[ -r "$HDR" ]] || HDR="$HOME/.config/crew/forge.header"
[[ -r "$HDR" ]] || { echo "forge.sh: no token header file (FORGE_HEADER or ~/.config/company/forge.header)" >&2; exit 2; }
REMOTE_URL="$(git remote get-url forge)"
BASE_URL="${REMOTE_URL%%://*}://$(echo "${REMOTE_URL#*://}" | cut -d/ -f1)"
FORGE="${FORGE_URL:-$(git config --get company.forge || true)}"
[[ -n $FORGE ]] || { echo "forge.sh: no forge URL: git config company.forge <https://host> (or FORGE_URL)" >&2; exit 2; }
[[ $BASE_URL == "$FORGE" ]] || { echo "forge.sh: the forge remote is not $FORGE" >&2; exit 2; }
SLUG="$(echo "${REMOTE_URL#*://}" | cut -d/ -f2-3)"; SLUG="${SLUG%.git}"
API="$BASE_URL/api/v1/repos/$SLUG"
TRAIN='^day/[0-9]{4}-[0-9]{2}-[0-9]{2}(-[0-9]{1,2})?$'

api() { # method path [json-on-stdin]
  local m="$1" p="$2"
  if [[ "$m" == GET ]]; then curl -sSf --proto '=https' -H @"$HDR" "$API$p"
  else curl -sSf --proto '=https' -H @"$HDR" -H 'Content-Type: application/json' -X "$m" --data-binary @- "$API$p"; fi
}
py() { python3 -c "$1" "${@:2}"; }
body_json() { # file key -> {"key": contents}
  local f="$1" k="$2"
  [[ -f "$f" && $(wc -c <"$f") -le 65536 ]] || { echo "forge.sh: $f missing or over 64 KiB" >&2; exit 2; }
  py 'import json,sys; print(json.dumps({sys.argv[2]: open(sys.argv[1], encoding="utf-8").read()}))' "$f" "$k"
}
num() { [[ "$1" =~ ^[0-9]+$ ]] || { echo "forge.sh: bad PR number $1" >&2; exit 2; }; }
# fgit <fetch|push|ls-remote> ARGS...: the header reaches git through its environment only, keyed to the forge's URL,
# redirects refused, no prompt.
fgit() (
  case "${1:-}" in fetch | push | ls-remote) ;; *) echo "forge.sh git: fetch, push or ls-remote only" >&2; exit 2 ;; esac
  export GIT_TERMINAL_PROMPT=0 GIT_CONFIG_COUNT=2 GIT_CONFIG_KEY_0="http.$BASE_URL/.extraHeader" \
    GIT_CONFIG_KEY_1="http.$BASE_URL/.followRedirects" GIT_CONFIG_VALUE_1=false
  GIT_CONFIG_VALUE_0="$(head -n 1 "$HDR")"; export GIT_CONFIG_VALUE_0
  git "$@"
)
# pr_fetch <n>: fetch the PR's own base (master or day/YYYY-MM-DD[-N], checked) into refs/remotes/forge/<base> and
# its head into refs/forge-pr/<n>; print their merge base.
pr_fetch() {
  local base
  base="$(api GET "/pulls/$1" | py 'import json,sys; print(json.load(sys.stdin)["base"]["ref"])')"
  [[ $base == master || $base =~ $TRAIN ]] || { echo "forge.sh: PR #$1 goes into an unexpected base" >&2; exit 2; }
  fgit fetch -q --no-tags forge "+refs/heads/$base:refs/remotes/forge/$base" "+refs/pull/$1/head:refs/forge-pr/$1"
  git merge-base "refs/remotes/forge/$base" "refs/forge-pr/$1"
}

cmd="${1:-}"; shift || true
case "$cmd" in
  pr-list) api GET "/pulls?state=open&limit=50" | py '
import json,sys
for p in json.load(sys.stdin):
    print("#{} {} -> {} {} {}".format(p["number"], p["head"]["ref"], p["base"]["ref"], p["head"]["sha"][:10], p["title"]))' ;;
  pr-find) api GET "/pulls?state=open&limit=50" | py '
import json,sys
for p in json.load(sys.stdin):
    if p["head"]["ref"] == sys.argv[1]: print(p["number"]); break' "$1" ;;
  pr-open)
    branch="$1" title="$2" file="$3" base="${4:-master}"
    [[ $base == master || $base =~ $TRAIN ]] || { echo "forge.sh: bad base $base" >&2; exit 2; }
    py 'import json,sys; b=json.loads(sys.argv[1]); b.update(head=sys.argv[2], base=sys.argv[3], title=sys.argv[4]); print(json.dumps(b))' \
      "$(body_json "$file" body)" "$branch" "$base" "$title" | api POST "/pulls" | py 'import json,sys; print(json.load(sys.stdin)["number"])' ;;
  pr) num "$1"; api GET "/pulls/$1" | py '
import json,sys
p=json.load(sys.stdin); print(p["state"], "merged" if p["merged"] else "unmerged", p["head"]["sha"], p["head"]["ref"], p["base"]["ref"])' ;;
  pr-body) num "$1"; api GET "/pulls/$1" | py 'import json,sys; print(json.load(sys.stdin)["body"] or "")' ;;
  pr-title) num "$1"; py 'import json,sys; print(json.dumps({"title": sys.argv[1]}))' "${2:?title}" | api PATCH "/pulls/$1" >/dev/null && echo "title set on #$1" ;;
  pr-set-body) num "$1"; body_json "$2" body | api PATCH "/pulls/$1" >/dev/null && echo "body set on #$1" ;;
  pr-diff) num "$1"
    mb="$(pr_fetch "$1")"
    git diff --no-ext-diff --no-textconv ${2:-} "$mb" "refs/forge-pr/$1" ;;
  gate-files) num "$1"
    mb="$(pr_fetch "$1")"
    git diff --raw --no-renames --no-abbrev -z "$mb" "refs/forge-pr/$1" | "$(dirname "$0")/gate-files.sh" --raw ;;
  comment) num "$1"; py 'import json,sys; b=json.loads(sys.argv[1]); b["event"]="COMMENT"; print(json.dumps(b))' "$(body_json "$2" body)" \
      | api POST "/pulls/$1/reviews" >/dev/null && echo "commented on #$1" ;;
  comments) num "$1"; api GET "/pulls/$1/reviews" | py '
import json,sys
for c in json.load(sys.stdin): print("--- {} {} {}\n{}".format(c["user"]["login"], c["state"], c["submitted_at"], c["body"]))' ;;
  merge) num "$1"; [[ "$2" =~ ^[0-9a-f]{40}$ ]] || { echo "forge.sh: bad sha" >&2; exit 2; }
    pr_json="$(api GET "/pulls/$1")"
    read -r head base <<<"$(py 'import json,sys; p=json.loads(sys.argv[1]); print(p["head"]["ref"], p["base"]["ref"])' "$pr_json")"
    [[ ! ($head =~ $TRAIN && $base == master) ]] || { echo "forge.sh: merge #$1: a day PR; Hunter merges those" >&2; exit 1; }
    out="$(mktemp)"; trap 'rm -f "$out"' EXIT
    # Our own title and message: the forge's default adds a Reviewed-on: line with the PR's URL, and GitHub mirrors
    # master. The PR body goes in without any line naming the forge, and ends with Hunter as co-author so GitHub
    # credits him beside the agent (the author).
    code="$(py '
import json,sys
p=json.loads(sys.argv[1]); base=sys.argv[3]
co="Co-authored-by: Hunter Coles <hunter.colesw@gmail.com>"
body="\n".join(l for l in (p.get("body") or "").splitlines() if base not in l and l.strip()!=co).strip()
body=(body+"\n\n" if body else "")+co
print(json.dumps({"Do":"squash","head_commit_id":sys.argv[2],"delete_branch_after_merge":True,
  "MergeTitleField":"{} (#{})".format(p["title"], p["number"]),"MergeMessageField":body}))' "$pr_json" "$2" "$BASE_URL" \
      | curl -sS --proto '=https' -H @"$HDR" -H 'Content-Type: application/json' -X POST --data-binary @- \
          -o "$out" -w '%{http_code}' "$API/pulls/$1/merge")" || code="${code:-000}"
    [[ $code == 2?? ]] || { echo "forge.sh: merge #$1: HTTP $code $(head -c 300 "$out" | tr -d '\000-\037')" >&2; exit 1; }
    echo "merged #$1 at ${2:0:10}" ;;
  url) num "$1"; echo "$BASE_URL/$SLUG/pulls/$1" ;;
  git) fgit "$@" ;;
  *) sed -n '2,/^set -euo/p' "$0" | sed '$d'; exit 2 ;;
esac
