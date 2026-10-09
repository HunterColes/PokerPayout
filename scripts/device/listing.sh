#!/usr/bin/env bash
# The store listing's phone screenshots (PP-040), shot on the headless emulator:
# metadata/en-US/images/phoneScreenshots/, which F-Droid reads from the release tag.
#
#   scripts/device/listing.sh                  # boot if needed, build and install debug, shoot, copy
#   scripts/device/listing.sh --release        # the release (R8) build, as users get it
#   scripts/device/listing.sh --no-build       # install the last built APK as it is
#   scripts/device/listing.sh --stop           # shut the emulator down at the end
#   scripts/device/listing.sh --copy-from DIR  # no emulator: copy the pictures from a listing run's
#                                              # report (a CI artifact, say) into the listing
#   scripts/device/listing.sh --list           # the pictures and the steps that pose them, then exit
#
# Locally, hold the emulator lock like every tour: flock /tmp/pokerpayout-emulator.lock scripts/device/listing.sh --stop
# On GitHub: gh workflow run listing.yml --ref <branch> (commits the pictures to the branch).
#
# It plays one home game through the app with tour.sh: some of the tour's own steps and the opt-in
# listing steps in steps-listing.sh (nine named players, a running clock with rebuys and knockouts,
# the night played out to its settle-up, the tools). Each `listing-shot-*` step leaves a clean screen
# with the status bar in demo mode (12:00, full battery); its screenshot becomes one picture, under
# the stable name below. Only when every step passed are the pictures copied (as RGB and optimised,
# if Pillow is there), and then any other picture in phoneScreenshots/ (an old set) is removed.
#
# A failed run changes nothing. The tour's report (screenshots, UI dumps, logcat) goes to
# build/device-reports/listing-<timestamp>/.
set -euo pipefail

DEVICE_SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEVICE_SCRIPTS/../.." && pwd)"
DEST="$REPO_ROOT/metadata/en-US/images/phoneScreenshots"

# The game, in order: the tour's steps (tour.sh --list) and the listing's (steps-listing.sh)
STEPS=(
  launch listing-money blinds smallest-chip breaks ready-ticket
  listing-bank-names listing-buy-ins listing-start listing-midgame
  listing-shot-clock listing-shot-table-view listing-shot-bank listing-shot-payouts
  listing-finish listing-shot-settle-up listing-settle-close
  tools odds-empty odds-card-picker odds-hole-cards odds-flop odds-results listing-shot-odds
  chip-calc listing-shot-chip-set
  listing-shot-seat-draw
  app-alive
)
# step -> picture. The numbers set the order F-Droid shows them in; the table view is landscape.
SHOTS=(
  "listing-shot-clock       01_clock.png"
  "listing-shot-table-view  02_table_view.png"
  "listing-shot-bank        03_bank.png"
  "listing-shot-payouts     04_payouts.png"
  "listing-shot-settle-up   05_settle_up.png"
  "listing-shot-odds        06_odds.png"
  "listing-shot-chip-set    07_chip_set.png"
  "listing-shot-seat-draw   08_seat_draw.png"
)
LANDSCAPE=" listing-shot-table-view "

say() { printf '[listing] %s\n' "$*"; }
die() { printf '[listing] ERROR: %s\n' "$*" >&2; exit 1; }

TOUR_ARGS=(); COPY_FROM=""
while (( $# )); do
  case "$1" in
    --release|--no-build|--stop|--no-boot|--no-install) TOUR_ARGS+=("$1") ;;
    --copy-from) shift; COPY_FROM="${1:?--copy-from needs a report directory}" ;;
    --list)
      printf '%s\n' "${SHOTS[@]}" | awk '{ printf "%-24s %s\n", $2, $1 }'
      echo; echo "steps: ${STEPS[*]}"; exit 0 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done

if [[ -n "$COPY_FROM" ]]; then
  REPORT="$(cd "$COPY_FROM" 2>/dev/null && pwd)" || die "no report directory: $COPY_FROM"
else
  REPORT="${PP_REPORT_ROOT:-$REPO_ROOT/build/device-reports}/listing-$(date +%Y%m%d-%H%M%S)"
  say "shooting ${#SHOTS[@]} pictures in ${#STEPS[@]} steps; report: ${REPORT#"$REPO_ROOT"/}"
  if ! "$DEVICE_SCRIPTS/tour.sh" "${TOUR_ARGS[@]}" --out "$REPORT" --only "$(IFS=,; echo "${STEPS[*]}")"; then
    die "the tour failed, so the listing is unchanged; see ${REPORT#"$REPO_ROOT"/}/index.md"
  fi
fi

# A run copies only if it passed as a whole (a step that failed may have left a wrong screen behind)
grep -qx 'verdict=PASS' "$REPORT/summary.env" 2>/dev/null \
  || die "$REPORT has no passing run (summary.env says: $(tr '\n' ' ' < "$REPORT/summary.env" 2>/dev/null || echo nothing))"

# Check every picture (a PNG, the screen's size, the right way up), then copy them all, then drop the
# pictures that aren't in this set. Nothing is touched unless every picture is there.
mkdir -p "$DEST"
python3 - "$REPORT" "$DEST" "$LANDSCAPE" "${SHOTS[@]}" <<'PY'
import glob, os, struct, sys

report, dest, landscape = sys.argv[1], sys.argv[2], sys.argv[3].split()
shots = [arg.split() for arg in sys.argv[4:]]
if not shots or any(len(shot) != 2 for shot in shots):
    sys.exit("[listing] ERROR: no pictures to copy: %r" % sys.argv[4:])

def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", head[16:24])

sources = []
for step, name in shots:
    found = sorted(glob.glob(os.path.join(report, "[0-9][0-9]-%s.png" % step)))
    if len(found) != 1:
        sys.exit("[listing] ERROR: %d screenshots for %s in %s" % (len(found), step, report))
    size = png_size(found[0])
    if size is None:
        sys.exit("[listing] ERROR: %s isn't a PNG" % found[0])
    w, h = size
    wide = step in landscape
    if min(w, h) < 600 or (w > h) != wide:
        sys.exit("[listing] ERROR: %s is %dx%d; expected a %s screen" % (found[0], w, h, "landscape" if wide else "portrait"))
    sources.append((found[0], name, w, h))

try:
    from PIL import Image
except ImportError:
    Image = None
for src, name, w, h in sources:
    out = os.path.join(dest, name)
    if Image is not None:
        # RGB (the screen has no transparency) and optimised; Pillow writes no metadata chunks
        Image.open(src).convert("RGB").save(out, format="PNG", optimize=True)
    else:
        with open(src, "rb") as f, open(out, "wb") as g:
            g.write(f.read())
    print("[listing] %-22s %dx%d, %d KB" % (name, w, h, os.path.getsize(out) // 1024))

keep = {name for _, name, _, _ in sources}
for path in sorted(glob.glob(os.path.join(dest, "*"))):
    if os.path.isfile(path) and os.path.basename(path) not in keep and path.lower().endswith((".png", ".jpg", ".jpeg")):
        os.remove(path)
        print("[listing] removed %s (not in this set)" % os.path.basename(path))
if Image is None:
    print("[listing] (no Pillow: copied as the emulator took them, RGBA and unoptimised)")
PY
say "done: ${DEST#"$REPO_ROOT"/}"
