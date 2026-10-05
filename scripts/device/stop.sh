#!/usr/bin/env bash
# Stop the headless test emulator started by boot.sh (no-op if it is not running).
#
#   scripts/device/stop.sh            # graceful: `adb emu kill` (saves a quick-boot snapshot)
#   scripts/device/stop.sh --force    # SIGKILL the emulator process
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

pidfile="$STATE_DIR/emulator.pid"
pid="$(cat "$pidfile" 2>/dev/null || true)"
alive() { [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; }

if ! device_online && ! alive; then
  rm -f "$pidfile"
  log "not running ($ANDROID_SERIAL)"
  exit 0
fi

if (( FORCE )); then
  alive && kill -9 "$pid" 2>/dev/null || true
else
  adb_ emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 60); do
    alive || break
    sleep 1
  done
  if alive; then
    warn "emulator did not exit after 60s; sending SIGTERM"
    kill "$pid" 2>/dev/null || true
    sleep 3
    alive && kill -9 "$pid" 2>/dev/null || true
  fi
fi
rm -f "$pidfile"
log "stopped $ANDROID_SERIAL"
