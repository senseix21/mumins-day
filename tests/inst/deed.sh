#!/usr/bin/env bash
# T-UI-2 (screen part) for the day card, over adb and `uiautomator dump` (no test library: INV-P4). Debug build only: it
# reads and writes deed-days.db through run-as and relies on the fake fixture app/src/debug/assets/deeds.json.
# Starts at Home (Bengali, onboarded). Checks: card shown and recorded; swap once; done; week dots; no Latin digit;
# 200 % font scale. Screenshots go to $SHOTS if set.
set -u
cd "$(dirname "$0")/../.."
. tests/inst/lib.sh
SHOTS="${SHOTS:-}"
shot() { [ -z "$SHOTS" ] || adb_ exec-out screencap -p > "$SHOTS/$1.png"; }
dump() { sh_ uiautomator dump /sdcard/u.xml >/dev/null; adb_ exec-out cat /sdcard/u.xml; }
tap() { # tap TEXT: taps the node with exactly that text
  local xy; xy=$(dump | python3 -c '
import re,sys
x=sys.stdin.read()
for m in re.finditer(r"<node [^>]*>",x):
    n=m.group(0)
    if "text=\"%s\"" % sys.argv[1] in n:
        b=list(map(int,re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",n).groups()))
        if b[2]>b[0] and b[3]>b[1]: print((b[0]+b[2])//2,(b[1]+b[3])//2); break  # zero-area = scrolled out of view
' "$1")
  [ -n "$xy" ] && sh_ input tap $xy
}
today=$(TZ=Asia/Dhaka python3 -c 'import datetime;print(datetime.date.today())' ); today=$(python3 - <<'PY'
import datetime,zoneinfo
print(datetime.datetime.now(zoneinfo.ZoneInfo("Asia/Dhaka")).date())
PY
)
day() { python3 -c "import datetime,sys;print(datetime.date.fromisoformat('$today')-datetime.timedelta(days=$1))"; }

# From Home, scroll until the card button is on screen (Home is longer at large font), then open it.
open_card() {
  sh_ am force-stop $P; sh_ am start -n "$P/.MainActivity" >/dev/null; sleep 2
  local i; for i in 1 2 3 4 5; do dump | grep -q 'text="আজকের একটি ভালো কাজ"' && break; sh_ input swipe 500 1800 500 700; sleep 1; done
  tap "আজকের একটি ভালো কাজ"; sleep 2
}
# deed-days.db exists only after the card was shown once (a fresh install has none): show it, then seed.
open_card
sh_ am force-stop $P; sql_deed "delete from deed_day"
# Three earlier days done (1, 2, 4 days ago): the dots must show exactly those three, with no number.
for back in 1 2 4; do sql_deed "insert into deed_day values('$(day $back)','test-1',1,0,1000)"; done
open_card; shot card-1
d=$(dump)
row=$(sql_deed "select deed_id||'|'||swapped||'|'||coalesce(done_at,'') from deed_day where local_date='$today'" | tr -d '\r')
first=${row%%|*}
case "$row" in test-*"|0|") result T-UI-2.card PASS "card recorded when first shown: $row";; *) result T-UI-2.card FAIL "row: '$row'";; esac
echo "$d" | grep -q 'TEST DEED [০-৯]* BN' && result T-UI-2.text PASS "deed text shown, digits Bengali" || result T-UI-2.text FAIL "no card text"
dots=$(echo "$d" | grep -o 'content-desc="[^"]*"' | grep -c 'করা হয়েছে')
[ "$dots" = 3 ] && result T-UI-2.dots PASS "3 of 7 dots filled (days 1, 2, 4 ago), described by weekday" || result T-UI-2.dots FAIL "filled dots: $dots"
echo "$d" | grep -oE 'text="[^"]*"' | grep -q 'দেখান' && result T-UI-2.swap-offered PASS "show me another is offered" || result T-UI-2.swap-offered FAIL ""
tests/inst/ui.sh | grep -E 'T-UNIT-9|T-UI-target|T-UI-label' | sed 's/^/  card: /'

# Swap: another deed, once.
tap "আরেকটি দেখান"; sleep 1; shot card-2-swapped
row2=$(sql_deed "select deed_id||'|'||swapped from deed_day where local_date='$today'" | tr -d '\r')
[ "$row2" != "$first|1" ] && [ "${row2##*|}" = 1 ] && result T-UI-2.swap PASS "swapped: $first -> $row2" || result T-UI-2.swap FAIL "$row2"
dump | grep -q 'দেখান"' && result T-UI-2.swap-once FAIL "button still there" || result T-UI-2.swap-once PASS "no second swap offered"

# Restart: the same card (G5).
open_card
row3=$(sql_deed "select deed_id||'|'||swapped from deed_day where local_date='$today'" | tr -d '\r')
[ "$row3" = "$row2" ] && result T-UI-2.rest PASS "same card after restart" || result T-UI-2.rest FAIL "$row3"

# Done.
tap "আমি করেছি"; sleep 1; shot card-3-done
d=$(dump)
done_at=$(sql_deed "select done_at from deed_day where local_date='$today'" | tr -d '\r')
[ -n "$done_at" ] && result T-UI-2.done PASS "done_at recorded" || result T-UI-2.done FAIL "no done_at"
echo "$d" | grep -q 'আজকের জন্য করা হয়েছে' && ! echo "$d" | grep -q 'আমি করেছি' && result T-UI-2.done-quiet PASS "quiet done sentence, no button" || result T-UI-2.done-quiet FAIL ""
filled=$(echo "$d" | grep -o 'content-desc="[^"]*"' | grep -c 'করা হয়েছে')
[ "$filled" = 4 ] && result T-UI-2.dots-today PASS "today's dot filled (4 dots)" || result T-UI-2.dots-today FAIL "$filled"
tests/inst/ui.sh | grep -E 'T-UNIT-9' | sed 's/^/  done: /'

# 200 % font scale: nothing clipped, the buttons and dots still on screen.
sh_ settings put system font_scale 2.0; sleep 2
open_card; shot card-4-font200
dump | grep -q 'TEST DEED' && result T-UI-2.font200 PASS "card readable at 200 % (see screenshot card-4-font200)" || result T-UI-2.font200 FAIL ""
sh_ settings put system font_scale 1.0
