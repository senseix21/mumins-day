# Helpers for run.sh. Plain POSIX-ish bash; no test framework. Everything drives the debug build on one device through
# adb, because androidx.test is not on the dependency allowlist (INV-P4). Sourced, never run on its own.

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
P=com.senseix21.muminsday
D=com.senseix21.muminsday.debug
RESULTS=()

adb_() { "$ADB" "$@"; }
sh_() { "$ADB" shell "$@"; }

# Send one debug action to the app. Extra arguments are `--es k v` / `--ei k v`.
debug() { local action="$1"; shift; sh_ am broadcast -n "$P/.alarm.DebugReceiver" -a "$D.$action" "$@" >/dev/null; }

# SQL against the app's own databases, through run-as (debug builds are debuggable). One file each since G34.
sql() { sh_ "run-as $P sqlite3 databases/alarm-log.db \"$1\""; }
sql_deed() { sh_ "run-as $P sqlite3 databases/deed-days.db \"$1\""; }

# Device wall clock, epoch milliseconds.
device_now_ms() { echo "$(sh_ date +%s | tr -d '\r')000"; }

# (`#n:` keeps only the queue's own entries; the "Next alarm clock" summary repeats one of them.)
# The trigger time (epoch ms) of every alarm this app has queued, sorted. `origWhen` is the instant it was set for; API 26's
# dump has no `origWhen`, only `when`.
queued() {
  sh_ dumpsys alarm | tr -d '\r' \
    | grep -E "#[0-9]+: Alarm\{[0-9a-f]+ type [0-9] (origWhen|when) [0-9]+ .*$P\}" \
    | sed -E 's/.*(origWhen|when) ([0-9]+) .*/\2/' | sort -n
}
queued_count() { queued | grep -c . || true; }

# The number the last replan reported, read from the app's own log line.
last_registered() { sh_ logcat -d -s Mumins | tr -d '\r' | grep "replan: registered=" | tail -1 | sed -E 's/.*registered=([0-9]+).*/\1/'; }
log_clear() { sh_ logcat -c; }
log_lines() { sh_ logcat -d -s Mumins | tr -d '\r'; }
count_log() { log_lines | grep -c "$1" || true; }

wait_until() { # wait_until SECONDS COMMAND...  -> 0 if COMMAND succeeded within SECONDS
  local limit="$1"; shift; local i=0
  while [ "$i" -lt "$limit" ]; do "$@" && return 0; sleep 1; i=$((i + 1)); done
  return 1
}

stop_playback() { debug STOP; sleep 1; }
open_app() { sh_ am start -n "$P/.MainActivity" >/dev/null; sleep 1; sh_ input keyevent KEYCODE_HOME; }
reminders() { debug SET_REMINDERS --es mode "$1"; sleep 2; }

result() { # result ID PASS|FAIL|"NOT RUN" detail
  RESULTS+=("$1|$2|$3"); printf '%-10s %-8s %s\n' "$1" "$2" "$3"
}

# Zone and clock changes. `cmd alarm` exists from API 29 or so; before that (API 26) the same effect needs root, which
# the google_apis emulator images give: `service call alarm 3` is IAlarmManager.setTimeZone there, and `date -u` sets the
# wall clock (the kernel's clock-set event makes AlarmManager send TIME_SET). Transaction 3 is for the API 26 image only.
set_zone() {
  if sh_ cmd alarm set-timezone "$1" 2>&1 | grep -q "No shell command"; then
    adb_ root >/dev/null; adb_ wait-for-device; sh_ service call alarm 3 s16 "$1" >/dev/null
  fi
}
set_time_ms() { # set_time_ms EPOCH_MS
  if sh_ cmd alarm set-time "$1" 2>&1 | grep -q "No shell command"; then
    adb_ root >/dev/null; adb_ wait-for-device
    sh_ date -u "$(python3 -c "import datetime,sys;print(datetime.datetime.fromtimestamp($1/1000,datetime.timezone.utc).strftime('%m%d%H%M%Y.%S'))")" >/dev/null
  fi
}
