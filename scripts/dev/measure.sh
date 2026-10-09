#!/usr/bin/env bash
# What the release APK is made of, for the code grade (and any "is it lighter?" question).
# Reads a build that already ran; it builds nothing. The Measure workflow
# (.github/workflows/device.yml, -f job=measure) runs it on GitHub after:
#
#   ./gradlew :app:assembleRelease -PcomposeReports
#   ./gradlew :app:dependencies --configuration releaseRuntimeClasspath > build/measure/deps-release.txt
#
# and writes build/measure/summary.md:
#   - the APK's bytes by kind (dex, resources.arsc, res/, native lib/, assets/, META-INF, other),
#     stored and compressed
#   - method references per dex, and the defined methods and dex bytes by library (apkanalyzer,
#     de-obfuscated with R8's mapping.txt)
#   - the runtime dependency tree's size (distinct artifacts) and its widest libraries
#   - Compose compiler metrics and the composable parameters it can't compare by value (-PcomposeReports)
#
#   scripts/dev/measure.sh [out-dir]     default build/measure
#
# Needs python3; apkanalyzer from the SDK's cmdline-tools for the dex part (skipped without it).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
out="${1:-build/measure}"
mkdir -p "$out"

apk="$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)"
[[ -n "$apk" ]] || { echo "measure: no release APK; run ./gradlew :app:assembleRelease first" >&2; exit 1; }
mapping="app/build/outputs/mapping/release/mapping.txt"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
analyzer=""
for candidate in "$sdk/cmdline-tools/latest/bin/apkanalyzer" "$(command -v apkanalyzer || true)"; do
  [[ -n "$candidate" && -x "$candidate" ]] && { analyzer="$candidate"; break; }
done

if [[ -n "$analyzer" ]]; then
  "$analyzer" dex references "$apk" > "$out/dex-references.txt" 2>/dev/null || true
  maps=()
  [[ -f "$mapping" ]] && maps=(--proguard-mappings "$mapping")
  "$analyzer" dex packages --defined-only "${maps[@]}" "$apk" > "$out/dex-packages.txt" 2>/dev/null || true
else
  echo "measure: apkanalyzer not found; skipping the dex breakdown" >&2
fi

python3 - "$apk" "$out" <<'PY'
import glob, json, os, re, sys, zipfile
from collections import defaultdict

apk, out = sys.argv[1], sys.argv[2]
lines = []
w = lines.append
kb = lambda n: f"{n / 1024:,.1f} KiB"

# 1. The APK by kind
def kind(name):
    if re.fullmatch(r"classes\d*\.dex", name): return "dex"
    if name == "resources.arsc": return "resources.arsc"
    if name.startswith("res/"): return "res/"
    if name.startswith("lib/"): return "lib/ (native)"
    if name.startswith("assets/"): return "assets/"
    if name.startswith("META-INF/"): return "META-INF/"
    if name.startswith("kotlin/"): return "kotlin/ (metadata)"
    return "other"
stored, packed, count = defaultdict(int), defaultdict(int), defaultdict(int)
biggest = []
with zipfile.ZipFile(apk) as z:
    for i in z.infolist():
        k = kind(i.filename)
        stored[k] += i.file_size; packed[k] += i.compress_size; count[k] += 1
        biggest.append((i.compress_size, i.file_size, i.filename))
size = os.path.getsize(apk)
w(f"# Release APK: {os.path.basename(apk)}\n")
w(f"**{size:,} bytes ({size / 1048576:.2f} MiB)**, {sum(count.values())} entries.\n")
w("| Kind | Entries | Stored | In the APK |")
w("|---|---:|---:|---:|")
for k in sorted(packed, key=lambda k: -packed[k]):
    w(f"| {k} | {count[k]} | {kb(stored[k])} | {kb(packed[k])} |")
w("\nLargest entries (in the APK):\n")
for c, s, n in sorted(biggest, reverse=True)[:12]:
    w(f"- `{n}`: {kb(c)} ({kb(s)} stored)")

# 2. Dex: method references and the libraries behind them
refs = os.path.join(out, "dex-references.txt")
if os.path.exists(refs) and os.path.getsize(refs):
    w("\n## Dex\n")
    total = 0
    for line in open(refs):
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            total += int(parts[1]); w(f"- `{parts[0]}`: {int(parts[1]):,} method references")
    w(f"- **Total: {total:,}** (the 64K limit is per dex)")
pk = os.path.join(out, "dex-packages.txt")
if os.path.exists(pk) and os.path.getsize(pk):
    def bucket(name):
        p = name.split(".")
        if name.startswith("com.huntercoles.pokerpayout"):
            return ".".join(p[:4]) if len(p) > 4 else "com.huntercoles.pokerpayout (app)"
        if name.startswith("androidx.compose.material.icons"): return "androidx.compose.material.icons"
        if name.startswith("androidx.compose."): return ".".join(p[:3])
        if name.startswith(("androidx.", "kotlinx.", "com.google.")): return ".".join(p[:2])
        if name.startswith(("dagger.", "hilt_aggregated_deps", "javax.inject")): return "dagger / hilt"
        if name.startswith("kotlin."): return "kotlin"
        return p[0] if len(p) > 1 else "(default package)"
    methods, nbytes = defaultdict(int), defaultdict(int)
    for line in open(pk):
        parts = line.split()
        # C d <defined methods> <referenced methods> <bytes> <class>
        if len(parts) >= 6 and parts[0] == "C" and parts[1] == "d":
            b = bucket(parts[5]); methods[b] += int(parts[2]); nbytes[b] += int(parts[4])
    allm, allb = sum(methods.values()), sum(nbytes.values())
    w(f"\nDefined in the dex: **{allm:,} methods, {kb(allb)}**. By library:\n")
    w("| Library | Methods | Dex bytes | Share |")
    w("|---|---:|---:|---:|")
    for b in sorted(nbytes, key=lambda b: -nbytes[b])[:20]:
        w(f"| {b} | {methods[b]:,} | {kb(nbytes[b])} | {100 * nbytes[b] / max(allb, 1):.1f}% |")

# 3. The runtime dependency tree
deps = os.path.join(out, "deps-release.txt")
if os.path.exists(deps):
    arts = set(); groups = defaultdict(set)
    for line in open(deps):
        m = re.search(r"[+\\]--- ([\w.\-]+):([\w.\-]+)(?::[\w.\-]+)?(?: -> ([\w.\-]+))?", line)
        if m:
            arts.add((m.group(1), m.group(2))); groups[m.group(1)].add(m.group(2))
    w("\n## Runtime dependencies (release)\n")
    w(f"**{len(arts)} distinct artifacts** in {len(groups)} groups. Widest groups:\n")
    for g in sorted(groups, key=lambda g: -len(groups[g]))[:10]:
        w(f"- `{g}`: {len(groups[g])}")

# 4. Compose compiler: metrics and unstable classes
mods = sorted(glob.glob("*/build/compose-metrics/*-module.json"))
if mods:
    w("\n## Compose compiler (release)\n")
    keys = ["restartableComposables", "skippableComposables", "restartGroups",
            "knownStableArguments", "knownUnstableArguments", "unknownStableArguments"]
    w("| Module | " + " | ".join(keys) + " |")
    w("|---|" + "---:|" * len(keys))
    for f in mods:
        d = json.load(open(f))
        w(f"| {f.split('/')[0]} | " + " | ".join(str(d.get(k, "")) for k in keys) + " |")
    # What costs recompositions: composable parameters Compose can't compare by value (it compares
    # them by identity, so an equal value built anew re-runs the composable)
    params = defaultdict(int)
    for f in sorted(glob.glob("*/build/compose-reports/*-composables.txt")):
        mod = f.split("/")[0]
        for m in re.finditer(r"^  unstable \w+: ([^=\n]+?)(?: = .*)?$", open(f).read(), re.M):
            params[(mod, m.group(1).strip())] += 1
    w(f"\nUnstable composable parameters: **{sum(params.values())}**. Most common:\n")
    for (mod, t), n in sorted(params.items(), key=lambda x: -x[1])[:15]:
        w(f"- {mod}: `{t}` x{n}")
    nonskip = []
    for f in sorted(glob.glob("*/build/compose-reports/*-composables.txt")):
        for m in re.finditer(r"^restartable (?!skippable)(?:\w+ )*fun (\w+)\(", open(f).read(), re.M):
            nonskip.append(f"{f.split('/')[0]}: {m.group(1)}")
    w(f"\nRestartable but not skippable composables: **{len(nonskip)}**\n")
    for n in nonskip[:40]:
        w(f"- {n}")

open(os.path.join(out, "summary.md"), "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
PY
