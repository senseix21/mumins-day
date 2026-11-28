#!/usr/bin/env bash
# Finish first-run onboarding on a fresh install of the debug build (Bengali, first district, Adhan, respect silent),
# by tapping the screens read from `uiautomator dump` (no test library: INV-P4). Run once per install; the other scripts
# assume an onboarded app. Usage: tests/inst/onboard.sh   (device from ANDROID_SERIAL, as for every script here)
# Stops with a message if a screen is not the one expected, so a changed flow fails loudly.
set -u
cd "$(dirname "$0")/../.."
. tests/inst/lib.sh
tap() { # tap TEXT: taps the node with exactly that text; returns 1 if it is not on screen
  local xy; xy=$(sh_ uiautomator dump /sdcard/u.xml >/dev/null; adb_ exec-out cat /sdcard/u.xml | python3 -c '
import re,sys
for m in re.finditer(r"<node [^>]*>",sys.stdin.read()):
    n=m.group(0)
    if "text=\"%s\"" % sys.argv[1] in n:
        b=list(map(int,re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",n).groups())); print((b[0]+b[2])//2,(b[1]+b[3])//2); break
' "$1")
  [ -n "$xy" ] || { echo "onboard: '$1' not on screen" >&2; return 1; }
  sh_ input tap $xy; sleep 2
}
sh_ am start -n "$P/.MainActivity" >/dev/null; sleep 3
tap "বাংলা" || exit 1
tap "TEST DISTRICT ১ BN" || exit 1
tap "এগিয়ে যান" || exit 1   # reminders: Adhan is preselected
tap "এগিয়ে যান" || exit 1   # silent mode: the recommended option is preselected
tap "শেষ করুন" || exit 1
sleep 2
sh_ dumpsys activity activities | tr -d '\r' | grep -m1 "mResumedActivity\|topResumedActivity" || true
echo "onboard: done"
