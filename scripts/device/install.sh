#!/usr/bin/env bash
# Build the app and install it on the headless emulator.
#
#   scripts/device/install.sh             # ./gradlew :app:assembleDebug, then install
#   scripts/device/install.sh --release   # :app:assembleRelease (debug-signed unless the
#                                         # RELEASE_* signing properties are provided)
#   scripts/device/install.sh --no-build  # install the last built APK as-is
#   scripts/device/install.sh --clear     # also wipe app data after installing
#
# Gradle output: build/device/gradle-<variant>.log. stdout: one line per phase.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

VARIANT=debug; BUILD=1; CLEAR=0
for arg in "$@"; do
  case "$arg" in
    --release) VARIANT=release ;;
    --debug) VARIANT=debug ;;
    --no-build) BUILD=0 ;;
    --clear) CLEAR=1 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) die "unknown option: $arg" ;;
  esac
done
Variant="${VARIANT^}"

if (( BUILD )); then
  start=$(date +%s)
  glog="$STATE_DIR/gradle-$VARIANT.log"
  if ! gradle_ ":app:assemble$Variant" >"$glog" 2>&1; then
    grep -E "^e: |error:|What went wrong|FAILURE" -A3 "$glog" | head -40 >&2 || true
    die "gradle :app:assemble$Variant failed (full log: $glog)"
  fi
  log "built :app:assemble$Variant in $(since "$start")s"
fi

apk="$(ls -t "$REPO_ROOT"/app/build/outputs/apk/"$VARIANT"/*.apk 2>/dev/null | head -1 || true)"
[[ -n "$apk" ]] || die "no APK in app/build/outputs/apk/$VARIANT (build first)"

require_device
start=$(date +%s)
ilog="$STATE_DIR/install.log"
if ! adb_ install -r -t -d "$apk" >"$ilog" 2>&1; then
  if grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match" "$ilog"; then
    warn "signature mismatch with installed build; uninstalling first"
    adb_ uninstall "$APP_ID" >>"$ilog" 2>&1 || true
    adb_ install -r -t "$apk" >>"$ilog" 2>&1 || { cat "$ilog" >&2; die "install failed"; }
  else
    cat "$ilog" >&2; die "install failed"
  fi
fi
(( CLEAR )) && adb_ shell pm clear "$APP_ID" >/dev/null
log "installed $(basename "$apk") ($(du -h "$apk" | cut -f1)) in $(since "$start")s"
