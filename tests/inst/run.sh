#!/usr/bin/env bash
# T-INST-1..10 and the G7 / update-survival measurements, on the device `adb` sees (default: the running emulator).
# Usage: tests/inst/run.sh [id ...]      e.g. tests/inst/run.sh t1 t7 g7      (no argument: everything)
# Needs: the debug APK installed (./gradlew :app:assembleDebug; adb install -r app/build/outputs/apk/debug/app-debug.apk).
# A test prints PASS only when it saw the check hold. Anything it cannot do here prints NOT RUN with the reason.
set -u
cd "$(dirname "$0")/../.."
. tests/inst/lib.sh

APK=app/build/outputs/apk/debug/app-debug.apk
ZONE_BEFORE=$(sh_ getprop persist.sys.timezone | tr -d '\r')

t1() {
  reminders Adhan
  local expected actual
  expected=$(last_registered); actual=$(queued_count)
  local now max out_of_window=0 when
  now=$(device_now_ms); max=$((now + 48 * 3600 * 1000))
  for when in $(queued); do { [ "$when" -gt "$now" ] && [ "$when" -le "$max" ]; } || out_of_window=$((out_of_window + 1)); done
  # 5 prayers x about 2 days + the replan alarm: far more than 5.
  if [ "$actual" = "$expected" ] && [ "$actual" -gt 5 ] && [ "$out_of_window" = 0 ]; then
    result T-INST-1 PASS "dumpsys alarm holds $actual alarms = the $expected the replan registered, all within 48 h"
  else
    result T-INST-1 FAIL "dumpsys has $actual, replan registered $expected, $out_of_window outside the window"
  fi
}

t2() {
  reminders Adhan; open_app
  local expected; expected=$(last_registered)
  adb_ reboot; adb_ wait-for-device
  wait_until 180 bash -c "[ \"\$($ADB shell getprop sys.boot_completed | tr -d '\r')\" = 1 ]" || { result T-INST-2 FAIL "no boot"; return; }
  local booted; booted=$(date +%s)
  # Not opened again: only the boot receiver can have put the alarms back.
  if wait_until 90 bash -c "[ \"\$($ADB shell dumpsys alarm | tr -d '\r' | grep -cE '#[0-9]+: Alarm.* $P\}')\" = $expected ]"; then
    result T-INST-2 PASS "after reboot, $expected alarms back $(( $(date +%s) - booted )) s after boot_completed, app never opened (target 60 s)"
  else
    result T-INST-2 FAIL "after reboot the queue did not return to $expected within 90 s; has $(queued_count)"
  fi
}

t3() {
  reminders Adhan; local before after
  before=$(queued | tr '\n' ' '); log_clear
  sh_ cmd alarm set-timezone Asia/Tokyo >/dev/null; sleep 4
  after=$(queued | tr '\n' ' ')
  local event; event=$(count_log "system event: android.intent.action.TIMEZONE_CHANGED")
  sh_ cmd alarm set-timezone "$ZONE_BEFORE" >/dev/null; sleep 3
  if [ "$event" -ge 1 ] && [ "$before" = "$after" ] && [ -n "$after" ]; then
    result T-INST-3 PASS "zone Asia/Dhaka -> Asia/Tokyo: replan ran ($event event), the same $(queued_count) instants stay queued"
  else
    result T-INST-3 FAIL "events=$event; before=[$before] after=[$after]"
  fi
}

t4() {
  reminders Adhan; log_clear
  local real new out_of_window=0 when now max registered
  real=$(device_now_ms); new=$((real + 3 * 3600 * 1000))
  sh_ cmd alarm set-time "$new" >/dev/null; sleep 4
  now=$(device_now_ms); max=$((now + 48 * 3600 * 1000)); registered=$(last_registered)
  for when in $(queued); do { [ "$when" -gt "$now" ] && [ "$when" -le "$max" ]; } || out_of_window=$((out_of_window + 1)); done
  local events actual; events=$(count_log "system event: android.intent.action.TIME_SET"); actual=$(queued_count)
  sh_ cmd alarm set-time "$(($(date +%s) * 1000))" >/dev/null; sleep 3
  if [ "$events" -ge 1 ] && [ "$actual" = "$registered" ] && [ "$out_of_window" = 0 ]; then
    result T-INST-4 PASS "clock +3 h: replan ran, $actual alarms queued = $registered registered, all inside the new 48 h window"
  else
    result T-INST-4 FAIL "events=$events queued=$actual registered=$registered out_of_window=$out_of_window"
  fi
}

t5() {
  reminders Adhan; local expected; expected=$(last_registered)
  [ -f "$APK" ] || { result T-INST-5 "NOT RUN" "no debug APK at $APK"; return; }
  adb_ install -r "$APK" >/dev/null
  if wait_until 60 bash -c "[ \"\$($ADB shell dumpsys alarm | tr -d '\r' | grep -cE '#[0-9]+: Alarm.* $P\}')\" = $expected ]"; then
    result T-INST-5 PASS "adb install -r: $expected alarms queued again, app not opened"
  else
    result T-INST-5 FAIL "after install -r the queue has $(queued_count), expected $expected"
  fi
}

# Measure: does a pending alarm survive `adb install -r`? A Test Adhan lives outside the replan's id space, so nothing
# the update's replan does can cancel or re-create it. If it is still queued afterwards, the system kept it.
survive() {
  reminders Off; stop_playback; log_clear
  debug TEST_ADHAN --ei seconds 60; sleep 2
  local planned; planned=$(queued | tail -1)
  adb_ install -r "$APK" >/dev/null; sleep 4
  if queued | grep -qx "$planned"; then
    result SURVIVE "ANSWER" "a pending alarm (instant $planned) SURVIVED adb install -r"
  else
    result SURVIVE "ANSWER" "a pending alarm (instant $planned) did NOT survive adb install -r; queue now: $(queued | tr '\n' ' ')"
  fi
}

t6() {
  reminders Adhan; log_clear
  sh_ appops set "$P" SCHEDULE_EXACT_ALARM deny >/dev/null 2>&1; sleep 2; debug REPLAN; sleep 2
  local denied; denied=$(count_log "exact alarms not allowed")
  sh_ appops set "$P" SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1; sleep 4
  local api; api=$(sh_ getprop ro.build.version.sdk | tr -d '\r')
  if [ "$denied" -ge 1 ]; then
    if [ "$(queued_count)" -gt 0 ]; then result T-INST-6 PASS "denied: 'not allowed' logged; allowed again: $(queued_count) alarms back"
    else result T-INST-6 FAIL "denied logged but the queue is empty after allow"; fi
  else
    result T-INST-6 "NOT RUN" "API $api: the app holds USE_EXACT_ALARM, so canScheduleExactAlarms() stays true when the SCHEDULE_EXACT_ALARM appop is denied; needs an API 31 or 32 device"
  fi
}

t7() {
  reminders Adhan; log_clear
  local before; before=$(queued | tr '\n' ' ')
  sh_ 'i=0; while [ $i -lt 100 ]; do am broadcast -n '"$P"'/.alarm.DebugReceiver -a '"$D"'.REPLAN >/dev/null; i=$((i+1)); done'
  sleep 3
  local after runs; after=$(queued | tr '\n' ' '); runs=$(count_log "replan: registered=")
  if [ "$before" = "$after" ] && [ "$runs" -ge 100 ] && [ -n "$after" ]; then
    result T-INST-7 PASS "$runs replans: the queue holds the same $(queued_count) alarms, same instants"
  else
    result T-INST-7 FAIL "replans=$runs before=[$before] after=[$after]"
  fi
}

t8() {
  reminders Off; stop_playback; log_clear
  debug TEST_ADHAN --ei seconds 5
  wait_until 15 bash -c "$ADB logcat -d -s Mumins | grep -q 'playback: start'" || { result T-INST-8 FAIL "playback never started"; return; }
  debug REPLAN; sleep 1
  debug FIRE --es kind Adhan --es salah Fajr --ei late 0; sleep 2
  debug REPLAN; sleep 1
  local starts ignored; starts=$(count_log "playback: start"); ignored=$(count_log "second start ignored")
  stop_playback
  if [ "$starts" = 1 ] && [ "$ignored" -ge 1 ]; then
    result T-INST-8 PASS "two replans and a second Adhan trigger during playback: $starts playback started, $ignored second start ignored"
  else
    result T-INST-8 FAIL "playbacks started=$starts ignored=$ignored"
  fi
}

t9() {
  reminders Off; stop_playback; sql "delete from alarm_log"; log_clear
  debug FIRE --es kind Adhan --es salah Fajr --ei late 300; sleep 3
  local rang; rang=$(count_log "playback: start"); stop_playback
  debug FIRE --es kind Adhan --es salah Dhuhr --ei late 900; sleep 3
  local total; total=$(count_log "playback: start"); stop_playback
  local rows; rows=$(sql "select prayer || ':' || outcome from alarm_log order by prayer" | tr -d '\r' | tr '\n' ' ')
  if [ "$rang" = 1 ] && [ "$total" = 1 ] && [ "$rows" = "Dhuhr:SilentNotification Fajr:Rang " ]; then
    result T-INST-9 PASS "5 min late: Adhan played; 15 min late: no audio, silent notification; log rows: $rows"
  else
    result T-INST-9 FAIL "after 5 min late playbacks=$rang, after 15 min late playbacks=$total, rows=[$rows]"
  fi
}

t10() {
  reminders Off; stop_playback; sql "delete from alarm_log"; log_clear
  debug SCHEDULE_PRAYER --es salah Asr --es mode Tone --ei seconds 15; sleep 2
  local planned_row; planned_row=$(sql "select outcome from alarm_log" | tr -d '\r')
  sleep 18
  local row; row=$(sql "select outcome || '|' || (delivered_utc - planned_utc) from alarm_log" | tr -d '\r')
  local outcome=${row%%|*} delay=${row##*|}
  if [ "$planned_row" = Planned ] && [ "$outcome" = Rang ] && [ "$delay" -ge 0 ] && [ "$delay" -lt 3000 ]; then
    result T-INST-10 PASS "row written as Planned on schedule, then Rang, delivered ${delay} ms after the planned instant"
  else
    result T-INST-10 FAIL "before=[$planned_row] after=[$row]"
  fi
  stop_playback
}

# G7: may each alarm method start the mediaPlayback service from the background, and does audio play with
# notifications denied? Read from the platform's own log lines and the audio service.
g7() {
  reminders Off; stop_playback; open_app
  local method out=""
  for method in clock idle; do
    log_clear; adb_ logcat -c
    debug TEST_ADHAN --ei seconds 6 --es method "$method"; sleep 10
    local allowed refused
    allowed=$(sh_ logcat -d | tr -d '\r' | grep "Background started FGS: Allowed" | grep -o "code:[A-Z_]*" | head -1)
    refused=$(sh_ logcat -d | tr -d '\r' | grep -c "ForegroundServiceStartNotAllowedException")
    out="$out [$method: ${allowed:-no 'allowed' line}; refused=$refused; playback_started=$(count_log 'playback: start')]"
    stop_playback; sleep 1
  done
  result G7-METHODS ANSWER "$out"

  sh_ pm revoke "$P" android.permission.POST_NOTIFICATIONS; sleep 2
  local granted; granted=$(sh_ dumpsys package "$P" | tr -d '\r' | grep "POST_NOTIFICATIONS: granted" | head -1)
  log_clear; adb_ logcat -c
  debug TEST_ADHAN --ei seconds 6; sleep 9
  local track; track=$(sh_ dumpsys audio | tr -d '\r' | grep "u/pid:" | grep "AudioTrack" | grep -c "state:started.*usage=USAGE_ALARM")
  local started; started=$(count_log "playback: start")
  result G7-NOTIF ANSWER "POST_NOTIFICATIONS revoked (granted-line='${granted:-none}'): playback_started=$started, started alarm-usage AudioTrack in dumpsys audio=$track"
  stop_playback
  sh_ pm grant "$P" android.permission.POST_NOTIFICATIONS
}

open_app
ids=("$@"); [ "${#ids[@]}" = 0 ] && ids=(t1 t3 t4 t7 t8 t9 t10 t5 survive t6 g7 t2)
for id in "${ids[@]}"; do "$id"; done
echo; echo "summary:"; for r in "${RESULTS[@]}"; do echo "  $r"; done
