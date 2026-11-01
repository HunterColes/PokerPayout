#!/usr/bin/env bash
# Repro for the gate's lint crash (#38): "Unexpected failure during lint analysis of AdaptiveWidth.kt",
# NoClassDefFoundError androidx/compose/ui/lint/ComposedModifierDetector$visitMethodCall$1, red in one Gradle daemon
# for 7 builds in a row (04:47-05:15, ~/.gradle/daemon/8.11.1/daemon-416630.out.log), green in fresh ones.
#
# Mechanism: lint keeps a static, JVM-wide registry cache (lint-api 31.7.2 JarFileIssueRegistry.cache, keyed by the
# library's lint.jar in the shared Gradle transforms cache, never cleared in Gradle). The Compose lint jars need API
# migration, so the cached registry's class loader reads the migrated copy in whichever checkout linted FIRST
# (<checkout>/<module>/build/intermediates/lint-cache/*/migrated-jars). Every later lint in that JVM, in any checkout,
# runs those detectors from that loader, and a class that once fails to resolve stays failed for the JVM's life.
# The one-off trigger was not reproduced (deleting the first checkout alone does not break it on Linux); the leak is.
#
# Steps: two throwaway copies of this checkout's tracked files; lint bank-feature in A; delete A; lint core in B.
#   tools/lint-daemon-repro.sh               both in one private Gradle daemon: B writes no migrated Compose jar of
#                                            its own (it reuses A's loader, A long gone) -> "repro: LEAK", exit 1
#   tools/lint-daemon-repro.sh --no-daemon   each in its own single-use JVM, as tools/check.sh runs -> "repro: clean"
# The private daemon (a marker jvmarg keeps it apart from everyone else's) exits after 60 s idle. Takes the gate lock.
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
mode="${1:-}"
main_root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
for d in a b; do
  mkdir "$work/$d"
  git ls-files -z | tar --null -T - -cf - | tar -xf - -C "$work/$d"
  [[ -d "$main_root/build-cache" ]] && ln -s "$main_root/build-cache" "$work/$d/build-cache"
done
lint() { # $1: checkout, $2: module
  (cd "$1" && ./gradlew --console=plain ${mode:+"$mode"} -Dorg.gradle.daemon.idletimeout=60000 \
    "-Dorg.gradle.jvmargs=-Xmx2g -Dpokerpayout.lint-repro=1" -Pkotlin.daemon.jvmargs=-Xmx1g \
    ":$2:lintAnalyzeDebug" --rerun) > "$1.log" 2>&1
}
run() {
  lint "$work/a" bank-feature || { tail -20 "$work/a.log"; echo "repro: checkout A failed to lint (setup problem)"; exit 2; }
  rm -rf "$work/a"
  lint "$work/b" core || true
}
export -f lint run; export mode work
flock -w "${GATE_LOCK_WAIT:-3600}" /tmp/pokerpayout-gradle.lock bash -c run
grep -q 'BUILD SUCCESSFUL' "$work/b.log" || { grep -m3 'NoClassDefFoundError\|Message: ' "$work/b.log" || tail -20 "$work/b.log"; echo "repro: CRASH (B's lint failed)"; exit 1; }
if ! compgen -G "$work/b/core/build/intermediates/lint-cache/lintAnalyzeDebug/migrated-jars/androidx.compose.ui.lint.UiIssueRegistry-*" > /dev/null; then
  echo "repro: LEAK (B linted core with the Compose registry loaded from A's deleted checkout)"; exit 1
fi
echo "repro: clean (B loaded its own Compose lint registry)"
