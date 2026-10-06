#!/usr/bin/env bash
# Lists (or deletes) the committed screenshot goldens that no test records any more because their
# cell left the golden list (PP-090). Computed from the code, without running anything: it reads
# DeviceMatrix.kt in core's test fixtures,
#   `goldens`  the cells every golden is recorded on, and
#   `pinned`   named goldens recorded on their own cells only (Z1_clock_small, the *_font2x ones...).
# A golden <module>/src/test/screenshots/<group>/<Name>/<Name>_<cell>.png is retired when <Name> is
# pinned and <cell> isn't one of its cells, or <Name> isn't pinned and <cell> isn't in `goldens`.
#
# It can't see a golden whose test was renamed or removed (its cell may still be recorded); the
# summary of a goldens.yml run lists every golden the run didn't write, which covers both.
#
#   scripts/dev/retired-goldens.sh            the retired goldens, then a count and size (exit 0)
#   scripts/dev/retired-goldens.sh --check    the same, but exit 1 if there are any
#   scripts/dev/retired-goldens.sh --paths    only the paths, one a line (for other scripts)
#   scripts/dev/retired-goldens.sh --delete   git rm them (review and commit the result yourself)
#
# Needs git and python3 (stdlib). Runs from anywhere inside the repository.
set -euo pipefail

mode="${1:---list}"
case "$mode" in
  --list | --check | --paths | --delete) ;;
  -h | --help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) echo "retired-goldens: unknown option $mode (try --help)" >&2; exit 2 ;;
esac

cd "$(git rev-parse --show-toplevel)"

python3 - "$mode" <<'PY'
import os
import re
import subprocess
import sys

mode = sys.argv[1]
MATRIX = "core/src/testFixtures/kotlin/com/huntercoles/pokerpayout/core/testing/DeviceMatrix.kt"


def fail(message):
    print(f"retired-goldens: {message}", file=sys.stderr)
    sys.exit(2)


try:
    source = open(MATRIX, encoding="utf-8").read()
except OSError as error:
    fail(f"can't read {MATRIX}: {error}")

# enum class Device: SmallPhone("small", 320, 640),
devices = {
    name: (dev_id, int(width), int(height))
    for name, dev_id, width, height in re.findall(
        r'^\s*([A-Z]\w*)\("([^"]+)",\s*(\d+),\s*(\d+)\),?\s*$', source, re.MULTILINE
    )
}
if not devices:
    fail(f"no Device entries found in {MATRIX}")


def cells(text):
    """The cell ids of every ScreenConfig(Device.X, 1.0f) in text, as ScreenConfig.id spells them."""
    found = []
    for name, scale in re.findall(r"ScreenConfig\(\s*Device\.(\w+)\s*,\s*([0-9.]+)f?\s*\)", text):
        if name not in devices:
            fail(f"unknown device Device.{name} in {MATRIX}")
        dev_id, width, height = devices[name]
        found.append(f"{dev_id}-{width}x{height}_font{float(scale):.1f}")
    return found


def block(name, call):
    """The body of `val <name> ... = <call>(` up to its closing line `)`."""
    match = re.search(rf"\bval {name}\b[^=]*=\s*{call}\((.*?)\n\s*\)", source, re.DOTALL)
    if not match:
        fail(f"no `val {name} = {call}(...)` in {MATRIX}")
    return match.group(1)


recorded = set(cells(block("goldens", "listOf")))
if not recorded:
    fail(f"DeviceMatrix.goldens lists no cells in {MATRIX}")
pinned = {}
for line in block("pinned", "mapOf").splitlines():
    entry = re.match(r'\s*"(\w+)"\s+to\s+listOf\((.*)\),?\s*$', line)
    if entry:
        pinned[entry.group(1)] = set(cells(entry.group(2)))
    elif line.strip() and not line.strip().startswith("//"):
        fail(f"can't read this DeviceMatrix.pinned line (keep one entry a line): {line.strip()}")

listed = subprocess.run(
    ["git", "ls-files", "-z", "--", "*/src/test/screenshots/*.png"],
    check=True, capture_output=True,
).stdout.decode().split("\0")
golden_path = re.compile(r"^(?P<module>.+)/src/test/screenshots/(?P<group>[^/]+)/(?P<name>[^/]+)/(?P=name)_(?P<cell>.+)\.png$")

retired, odd = [], []
for path in filter(None, listed):
    match = golden_path.match(path)
    if not match:
        odd.append(path)
        continue
    name, cell = match.group("name"), match.group("cell")
    keep = cell in pinned[name] if name in pinned else cell in recorded
    if not keep:
        retired.append(path)

if mode == "--paths":
    for path in retired:
        print(path)
    sys.exit(0)

for path in retired:
    print(path)
size = sum(os.path.getsize(path) for path in retired if os.path.exists(path))
total = len(list(filter(None, listed)))
print(
    f"{len(retired)} of {total} committed goldens retired ({size / 1e6:.1f} MB); "
    f"recorded cells: {', '.join(sorted(recorded))}; {len(pinned)} pinned goldens",
    file=sys.stderr,
)
if odd:
    print(f"{len(odd)} files under src/test/screenshots don't follow <Name>/<Name>_<cell>.png:", file=sys.stderr)
    for path in odd:
        print(f"  {path}", file=sys.stderr)

if mode == "--delete" and retired:
    for start in range(0, len(retired), 200):
        subprocess.run(["git", "rm", "-q", "--", *retired[start:start + 200]], check=True)
    print(f"git rm'd {len(retired)} goldens; review with git status, then commit", file=sys.stderr)
if mode == "--check" and retired:
    sys.exit(1)
PY
