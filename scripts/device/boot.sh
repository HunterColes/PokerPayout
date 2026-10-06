#!/usr/bin/env bash
# Boot a headless, silent Android emulator for automated testing (idempotent).
#
#   scripts/device/boot.sh            # quick boot (reuses the AVD's snapshot if any)
#   scripts/device/boot.sh --cold     # ignore snapshots: cold boot, don't save one
#   scripts/device/boot.sh --wipe     # factory-reset the test AVD's data, then cold boot
#
# No window, no audio, no boot animation, software GPU (swiftshader_indirect works
# without a display). Uses a dedicated AVD (default "pokerpayout_test", API 34
# google_apis_playstore x86_64, 1080x2400 @ 420dpi, 3 GB, 4 cores) that this script creates on
# demand. Any other AVD named via PP_AVD is launched -read-only so it is never
# mutated. After boot it applies deterministic settings (animations off, en-US,
# 24h clock, UTC, touches hidden, screen always on, demo-mode status bar 12:00,
# Wi-Fi/data off, no display size/density override left by a killed device matrix).
#
# stdout: a few summary lines. Emulator output: build/device/emulator.log
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

COLD=0; WIPE=0
BOOT_TIMEOUT="${PP_BOOT_TIMEOUT:-300}"
for arg in "$@"; do
  case "$arg" in
    --cold) COLD=1 ;;
    --wipe) WIPE=1; COLD=1 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) die "unknown option: $arg" ;;
  esac
done

SYSIMG_REL="system-images/android-34/google_apis_playstore/x86_64/"
TEST_AVD="pokerpayout_test"

create_test_avd() {
  local avd_dir="$ANDROID_AVD_HOME/$TEST_AVD.avd"
  [[ -d "$ANDROID_HOME/$SYSIMG_REL" ]] || die "system image missing: $ANDROID_HOME/$SYSIMG_REL"
  mkdir -p "$avd_dir"
  cat > "$ANDROID_AVD_HOME/$TEST_AVD.ini" <<EOF
avd.ini.encoding=UTF-8
path=$avd_dir
path.rel=avd/$TEST_AVD.avd
target=android-34
EOF
  cat > "$avd_dir/config.ini" <<EOF
AvdId=$TEST_AVD
avd.ini.displayname=PokerPayout headless test (API 34)
avd.ini.encoding=UTF-8
PlayStore.enabled=true
abi.type=x86_64
image.sysdir.1=$SYSIMG_REL
tag.id=google_apis_playstore
tag.display=Google Play
target=android-34
disk.dataPartition.size=4G
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=3072
vm.heapSize=256
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.initialOrientation=portrait
hw.mainKeys=no
hw.keyboard=yes
hw.dPad=no
hw.trackBall=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
hw.gps=no
hw.sdCard=no
hw.battery=yes
hw.accelerometer=yes
hw.sensors.proximity=no
showDeviceFrame=no
EOF
  log "created AVD $TEST_AVD at $avd_dir"
}

apply_settings() {
  # A display size or density left over by a device-matrix run that was killed outright (the
  # matrix resets them itself on any normal end or signal; the emulator keeps them across reboots).
  if adb_ shell wm size 2>/dev/null | grep -q Override; then
    adb_ shell wm size reset >>"$STATE_DIR/settings.log" 2>&1 || true
    warn "reset a leftover display size override"
  fi
  if adb_ shell wm density 2>/dev/null | grep -q Override; then
    adb_ shell wm density reset >>"$STATE_DIR/settings.log" 2>&1 || true
    warn "reset a leftover display density override"
  fi
  # All best-effort: a failed tweak must not fail the boot.
  local s=(
    "settings put global window_animation_scale 0"
    "settings put global transition_animation_scale 0"
    "settings put global animator_duration_scale 0"
    "settings put system time_12_24 24"
    "settings put system show_touches 0"
    "settings put system pointer_location 0"
    "settings put system screen_off_timeout 2147483647"
    "settings put global stay_on_while_plugged_in 7"
    "svc power stayon true"
    "settings put system accelerometer_rotation 0"
    "settings put system user_rotation 0"
    "settings put system font_scale 1.0"
    "settings put secure show_ime_with_hard_keyboard 0"
    "settings put secure spell_checker_enabled 0"
    "settings put secure autofill_service null"
    "settings put secure immersive_mode_confirmations confirmed"
    "settings put global package_verifier_user_consent -1"
    "settings put global verifier_verify_adb_installs 0"
    "settings put global hidden_api_policy 1"
    "input keyevent KEYCODE_WAKEUP"
    "wm dismiss-keyguard"
    # Deterministic status bar for screenshots (SystemUI demo mode).
    "settings put global sysui_demo_allowed 1"
    "am broadcast -a com.android.systemui.demo -e command enter"
    "am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200"
    "am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false"
    "am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide"
    "am broadcast -a com.android.systemui.demo -e command notifications -e visible false"
  )
  local cmd
  for cmd in "${s[@]}"; do
    adb_ shell "$cmd" >>"$STATE_DIR/settings.log" 2>&1 || echo "failed: $cmd" >>"$STATE_DIR/settings.log"
  done
  # The app needs no network; turning it off keeps Play services quiet.
  if [[ "${PP_KEEP_NETWORK:-0}" != "1" ]]; then
    adb_ shell "svc wifi disable; svc data disable" >>"$STATE_DIR/settings.log" 2>&1 || true
  fi
}

# ---------------------------------------------------------------- already up?
if device_booted; then
  apply_settings
  log "already running: $ANDROID_SERIAL ($(adb_ shell getprop ro.build.version.release | tr -d '\r'), API $(adb_ shell getprop ro.build.version.sdk | tr -d '\r'))"
  exit 0
fi

[[ -x "$EMULATOR" ]] || die "emulator not found at $EMULATOR"
"$EMULATOR" -accel-check >/dev/null 2>&1 || warn "KVM acceleration check failed; boot will be very slow"

if [[ "$PP_AVD" == "$TEST_AVD" && ! -f "$ANDROID_AVD_HOME/$TEST_AVD.avd/config.ini" ]]; then
  create_test_avd
fi
[[ -f "$ANDROID_AVD_HOME/$PP_AVD.ini" ]] || die "AVD '$PP_AVD' not found in $ANDROID_AVD_HOME"

args=(
  -avd "$PP_AVD" -port "$PP_EMU_PORT"
  -no-window -no-audio -no-boot-anim
  -gpu "${PP_GPU:-swiftshader_indirect}"
  -memory "${PP_EMU_RAM:-3072}" -cores "${PP_EMU_CORES:-4}"
  -camera-back none -camera-front none
  -netdelay none -netspeed full
  -no-metrics
  -timezone "${PP_TIMEZONE:-Etc/UTC}"
  # Quick boot keeps guest RAM in a host file; it wrote hundreds of MB/s to the host disk
  # and made tours flaky under load. Snapshots still load; RAM just stays in memory.
  -feature -QuickbootFileBacked
)
if [[ "$PP_AVD" != "$TEST_AVD" ]]; then
  args+=(-read-only -no-snapshot-save)        # never mutate somebody else's AVD
fi
(( COLD )) && args+=(-no-snapshot-load -no-snapshot-save)
(( WIPE )) && args+=(-wipe-data)

start=$(date +%s)
mode="quick boot"; (( COLD )) && mode="cold boot"; (( WIPE )) && mode="wiped, cold boot"
log "booting $PP_AVD on $ANDROID_SERIAL (headless, $mode) ..."
# Without our fds: the emulator must not hold the caller's flock (see without_fds in lib.sh).
nohup setsid python3 -c 'import os, sys; os.closerange(3, 65536); os.execvp(sys.argv[1], sys.argv[1:])' \
  "$EMULATOR" "${args[@]}" >"$STATE_DIR/emulator.log" 2>&1 < /dev/null &
echo $! > "$STATE_DIR/emulator.pid"

# Wait for adb, then sys.boot_completed, then a responsive package manager.
deadline=$(( start + BOOT_TIMEOUT ))
until device_booted && adb_ shell pm path android >/dev/null 2>&1; do
  if ! kill -0 "$(cat "$STATE_DIR/emulator.pid")" 2>/dev/null; then
    tail -20 "$STATE_DIR/emulator.log" >&2
    die "emulator process exited during boot (see build/device/emulator.log)"
  fi
  (( $(date +%s) < deadline )) || die "boot timed out after ${BOOT_TIMEOUT}s (see build/device/emulator.log)"
  sleep 2
done
boot_secs=$(since "$start")

apply_settings
echo "$boot_secs" > "$STATE_DIR/last-boot-seconds"
# Locale: the API 34 image boots en-US (ro.product.locale) unless someone changed it;
# setting persist.sys.locale needs root, so verify instead of forcing.
locale="$(adb_ shell getprop persist.sys.locale | tr -d '\r')"
[[ -n "$locale" ]] || locale="$(adb_ shell getprop ro.product.locale | tr -d '\r')"
[[ "$locale" == "en-US" ]] || warn "device locale is '$locale', not en-US; text assertions may fail"
log "booted in ${boot_secs}s: $ANDROID_SERIAL, Android $(adb_ shell getprop ro.build.version.release | tr -d '\r') (API $(adb_ shell getprop ro.build.version.sdk | tr -d '\r')), $(adb_ shell wm size | tr -d '\r' | awk '{print $NF}') @ $(adb_ shell wm density | tr -d '\r' | awk '{print $NF}')dpi, $locale, TZ $(adb_ shell getprop persist.sys.timezone | tr -d '\r')"
