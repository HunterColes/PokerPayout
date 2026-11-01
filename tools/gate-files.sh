#!/usr/bin/env bash
# Gate files (the Company's merge rule, from PersonalSite's agent template): paths that change how the gate judges a
# PR, what the desktop's shell runs outside Gradle's test sandbox, what F-Droid or GitHub build from, or what steers
# agent sessions. A PR that changes one merges only when its reviewer named it (tools/pr-merge.sh --gate-files a,b).
# Default-deny: a path is a gate file unless it sits under a module's src/main/, src/test/ or src/androidTest/ (Kotlin,
# resources, unit tests and their screenshot goldens), docs/, crypto/ or metadata/ (but not metadata/*.yml, F-Droid's
# build recipe), or is a root *.md or LICENSE*; and even there it is one when its mode is anything but a regular
# non-executable file (executable, symlink, submodule), any part of its path starts with a dot (.claude/, .github/,
# .editorconfig, ...), or its name is CLAUDE*, *.sh, *.gradle*, a baseline (lint-baseline.xml, detekt-baseline.xml)
# or a lockfile. So src/testFixtures/ (the layout-check kit and its thresholds), gradle/ (versions, detekt.yml),
# scripts/, tools/, the build files and every baseline always are.
#   tools/gate-files.sh --raw     stdin: `git diff --raw --no-renames --no-abbrev -z` records; prints the gate files
#   tools/gate-files.sh --paths   stdin: NUL-separated paths (mode taken as 100644); prints the gate files
# One path a line; nothing when there are none. A record it cannot read exits 2.
set -euo pipefail

# gate_file <path> [<old mode> <new mode>]: 0 when the path is a gate file.
gate_file() {
  local m
  for m in "${2:-100644}" "${3:-100644}"; do [[ $m == 100644 || $m == 000000 ]] || return 0; done
  case "/$1/" in */.*) return 0 ;; esac
  case "${1##*/}" in
    CLAUDE* | *.sh | *.gradle | *.gradle.kts | gradlew* | *.lock | *.lockfile | lint-baseline.xml | detekt-baseline.xml | \
    lint.xml | proguard-rules.pro | *.pro) return 0 ;;
  esac
  case "$1" in
    metadata/*.yml | metadata/*.yaml) return 0 ;;
    */src/main/* | */src/test/* | */src/androidTest/* | docs/* | crypto/* | metadata/*) return 1 ;;
    */*) return 0 ;;
    *.md | LICENSE*) return 1 ;;
  esac
  return 0
}

die() { echo "gate-files: $*" >&2; exit 2; }

case "${1:-}" in
  --raw)
    while IFS= read -r -d '' meta; do
      [[ $meta =~ ^:([0-7]{6})\ ([0-7]{6})\ [0-9a-f]{40,64}\ [0-9a-f]{40,64}\ [A-Z][0-9]{0,3}$ ]] || die "not a raw diff record"
      IFS= read -r -d '' f || die "a record without a path"
      if gate_file "$f" "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"; then printf '%s\n' "$f"; fi
    done ;;
  --paths)
    while IFS= read -r -d '' f; do if gate_file "$f"; then printf '%s\n' "$f"; fi; done ;;
  *) sed -n '2,14p' "$0"; exit 2 ;;
esac
