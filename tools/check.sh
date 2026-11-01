#!/usr/bin/env bash
# The gate, as CI's "Unit tests, lint, detekt" runs it, on this checkout (a worktree is fine):
#   ./gradlew testDebugUnitTest lintDebug detekt
# testDebugUnitTest also verifies every screenshot golden (roborazzi.test.verify=true) and runs the layout checks.
# The device tour, monkey and mutation runs are not part of it (CLAUDE.md: Testing).
#   tools/check.sh            the whole gate
#   tools/check.sh <tasks>    other Gradle tasks under the same lock and caps (e.g. recordRoborazziDebug)
# One Gradle build at a time on this machine (CLAUDE.md: the owner runs Unity): every gate takes
# /tmp/pokerpayout-gradle.lock first (waits up to GATE_LOCK_WAIT seconds, default 3600), with the memory caps CLAUDE.md
# gives for builds that may overlap. A worktree has no local.properties: ANDROID_HOME defaults to ~/Android/Sdk, and a
# worktree's build-cache links to the main checkout's (Gradle locks the cache itself).
# A red gate ends with one line `gate: RED: <the last failure line>`; green ends `gate: green`.
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
main_root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
if [[ "$main_root" != "$PWD" && ! -e build-cache && -d "$main_root/build-cache" ]]; then
  ln -s "$main_root/build-cache" build-cache
fi
tasks=("$@"); ((${#tasks[@]})) || tasks=(testDebugUnitTest lintDebug detekt)
log="$(mktemp)"; trap 'rm -f "$log"' EXIT
rc=0
flock -w "${GATE_LOCK_WAIT:-3600}" /tmp/pokerpayout-gradle.lock \
  ./gradlew --console=plain -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx1g "${tasks[@]}" \
  > >(tee "$log") 2>&1 || rc=$?
wait
if ((rc)); then
  why="$(grep -E '^(> |e: |FAILED|.*Roborazzi: .* is changed|.*tests completed, [0-9]+ failed|Lint found|detekt .* weighted issues)' "$log" | tail -3 | paste -sd';' -)" || true
  printf '\ngate: RED: %s\n' "${why:-$(grep -v '^[[:space:]]*$' "$log" | tail -1)}"
  exit "$rc"
fi
echo "gate: green"
