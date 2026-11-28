#!/usr/bin/env bash
# F3 on an emulator, over adb and `uiautomator dump` (no test library: INV-P4). Debug build, onboarded, Bengali.
#   tests/inst/qibla.sh field    T-INST-12 (field part): good field -> trusted; out-of-range field -> warning within 2 s,
#                                greyed needle, calibration prompt. Needs `adb emu sensor set` (an emulator).
#   tests/inst/qibla.sh nomag    T-INST-13: run with ANDROID_SERIAL pointing at an AVD whose config has
#                                hw.sensors.magnetic_field=no (AVD `streambert_nomag`): number and hint, no needle.
# The needle is a custom view with a content-desc, so its presence is read from the dump. Screenshots go to $SHOTS.
# The emulator reports every reading as high accuracy, so "accuracy below high" cannot be injected here; that decision
# is covered on the JVM (CompassTest) and stays open for a phone (M-3).
set -u
cd "$(dirname "$0")/../.."
. tests/inst/lib.sh
SHOTS="${SHOTS:-}"
shot() { [ -z "$SHOTS" ] || adb_ exec-out screencap -p > "$SHOTS/$1.png"; }
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null; adb_ exec-out cat /sdcard/u.xml; }
# The screen's text including what is below the fold: a short screen (API 26's 1920 px) cuts the warning's later lines out
# of a dump, and the scroll view keeps them off it. Dump, scroll down, dump again, scroll back.
full() { dump; sh_ input swipe 500 1500 500 400 300; sleep 1; dump; sh_ input swipe 500 400 500 1500 300; sleep 1; }
open_qibla() {
  sh_ am force-stop $P; sh_ am start -n "$P/.MainActivity" >/dev/null; sleep 2
  local i xy
  for i in 1 2 3 4 5; do
    xy=$(dump | python3 -c '
import re,sys
for m in re.finditer(r"<node [^>]*>",sys.stdin.read()):
    n=m.group(0)
    if "text=\"কিবলা\"" in n and "clickable=\"true\"" in n:
        b=list(map(int,re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",n).groups()))
        # A zero-area node is a child scrolled out of view (bounds [0,0][0,0]): not tappable yet.
        if b[2]>b[0] and b[3]>b[1]: print((b[0]+b[2])//2,(b[1]+b[3])//2); break
')
    [ -n "$xy" ] && break; sh_ input swipe 500 1800 500 700; sleep 1
  done
  [ -n "$xy" ] && sh_ input tap $xy; sleep 3
}
has_needle() { echo "$1" | grep -q 'content-desc="কিবলার নির্দেশক'; }
case "${1:-}" in
field)
  adb_ emu sensor set magnetic-field 0:20:-45 >/dev/null; open_qibla; shot qibla-good; d=$(full)
  if has_needle "$d" && echo "$d" | grep -q 'কম্পাস ভালো আছে' && ! echo "$d" | grep -q 'কম্পাস ঠিক নেই'; then
    result T-INST-12.trusted PASS "good field (about 49 uT): needle, no warning"; else result T-INST-12.trusted FAIL "not trusted on a good field"; fi
  adb_ emu sensor set magnetic-field 0:0:200 >/dev/null; sleep 2; shot qibla-strong; d=$(full)
  if echo "$d" | grep -q 'কম্পাস ঠিক নেই' && echo "$d" | grep -q 'বাতাসে আট-এর' && echo "$d" | grep -q 'নির্ভরযোগ্য নয়'; then
    result T-INST-12.strong PASS "200 uT: warning, calibration prompt, needle described as not reliable (<= 2 s)"; else result T-INST-12.strong FAIL "no warning at 200 uT"; fi
  adb_ emu sensor set magnetic-field 0:0:5 >/dev/null; sleep 2; shot qibla-weak; d=$(full)
  echo "$d" | grep -q 'অস্বাভাবিক দুর্বল' && result T-INST-12.weak PASS "5 uT: weak-field warning" || result T-INST-12.weak FAIL "no warning at 5 uT"
  adb_ emu sensor set magnetic-field 0:20:-45 >/dev/null; sleep 2; d=$(full)
  has_needle "$d" && ! echo "$d" | grep -q 'কম্পাস ঠিক নেই' && result T-INST-12.recover PASS "good field again: warning gone" || result T-INST-12.recover FAIL "warning stuck"
  tests/inst/ui.sh | grep -E 'T-UNIT-9|T-UI-target|T-UI-label' | sed 's/^/  qibla: /'
  ;;
nomag)
  open_qibla; shot qibla-nomag; d=$(full)
  echo "$d" | grep -q 'content-desc="কিবলার দিক: ' && echo "$d" | grep -q 'কিবলার দিক: ' && result T-INST-13.number PASS "bearing number shown" || result T-INST-13.number FAIL "no number"
  echo "$d" | grep -q 'কম্পাস সেন্সর নেই' && echo "$d" | grep -q 'মিলিয়ে নিন' && result T-INST-13.hint PASS "no-sensor message and hint shown" || result T-INST-13.hint FAIL "no hint"
  has_needle "$d" && result T-INST-13.noneedle FAIL "a needle is described" || result T-INST-13.noneedle PASS "no needle in the view tree"
  tests/inst/ui.sh | grep -E 'T-UNIT-9|T-UI-target|T-UI-label' | sed 's/^/  qibla: /'
  ;;
*) echo "usage: $0 field|nomag"; exit 2;;
esac
