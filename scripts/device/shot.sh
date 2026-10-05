#!/usr/bin/env bash
# Capture a PNG screenshot from the headless emulator.
#
#   scripts/device/shot.sh <name> [outdir]        # -> <outdir>/<name>.png (default outdir: build/device/shots)
#   scripts/device/shot.sh <name> [outdir] --ui   # also dump the UI tree to <outdir>/<name>.xml
#
# Prints the PNG path on stdout.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[[ $# -ge 1 ]] || die "usage: shot.sh <name> [outdir] [--ui]"
name="$1"; shift
outdir="$STATE_DIR/shots"; UI=0
for arg in "$@"; do
  case "$arg" in
    --ui) UI=1 ;;
    *) outdir="$arg" ;;
  esac
done
mkdir -p "$outdir"
require_device

png="$outdir/$name.png"
adb_ exec-out screencap -p > "$png"
# Validate the PNG signature; a booting/locked device can return garbage.
if [[ "$(head -c 8 "$png" | od -An -tx1 | tr -d ' \n')" != "89504e470d0a1a0a" ]]; then
  die "screencap did not return a PNG ($png)"
fi
if (( UI )); then
  python3 "$DEVICE_SCRIPTS/ui.py" dump --out "$outdir/$name.xml" >/dev/null
fi
echo "$png"
