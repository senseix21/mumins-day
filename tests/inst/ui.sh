#!/usr/bin/env bash
# Screen checks on the screen that is open now, read from `uiautomator dump` (no test library: INV-P4).
# Usage: open the screen (in Bengali for the digit check), then tests/inst/ui.sh
#   T-UNIT-9 (screen part): no Latin digit 0-9 in any text or content-desc.
#   Touch targets: every clickable view that is fully on screen is at least 48 dp in both directions.
#   TalkBack: every clickable view has text or a content-desc.
set -u
cd "$(dirname "$0")/../.."
. tests/inst/lib.sh
dump=$(mktemp); trap 'rm -f "$dump"' EXIT
sh_ uiautomator dump /sdcard/u.xml >/dev/null; adb_ exec-out cat /sdcard/u.xml > "$dump"
density=$(sh_ wm density | tr -d '\r' | grep -o '[0-9]*$' | tail -1); min=$(( 48 * density / 160 ))
height=$(sh_ wm size | tr -d '\r' | grep -o '[0-9]*$' | tail -1)

latin=$(grep -oE '(text|content-desc)="[^"]*[0-9][^"]*"' "$dump" || true)
if [ -z "$latin" ]; then result T-UNIT-9 PASS "no Latin digit in any text or content-desc on this screen"; else result T-UNIT-9 FAIL "$latin"; fi

small=0; unlabeled=0
while IFS='|' read -r text desc l t r b; do
  w=$((r - l)); h=$((b - t))
  [ "$t" -le 0 ] || [ "$b" -ge "$height" ] || { [ "$w" -ge "$min" ] && [ "$h" -ge "$min" ]; } || { small=$((small + 1)); echo "  small: '$text$desc' ${w}x${h} px (min $min)"; }
  [ -n "$text$desc" ] || unlabeled=$((unlabeled + 1))
done < <(python3 - "$dump" <<'PY'
import re,sys
x=open(sys.argv[1],encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    if 'clickable="true"' not in n: continue
    # A Spinner is named by the caption that points at it with labelFor, which a dump does not show.
    spinner='class="android.widget.Spinner"' in n
    g=lambda k:(re.search(k+r'="([^"]*)"',n) or [None,''])[1]
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n).groups()
    print('|'.join([g('text'),g('content-desc') or ('labelFor' if spinner else ''),*b]))
PY
)
if [ "$small" = 0 ]; then result T-UI-target PASS "every fully visible clickable is >= 48 dp ($min px)"; else result T-UI-target FAIL "$small too small"; fi
if [ "$unlabeled" = 0 ]; then result T-UI-label PASS "every clickable has text or a content-desc"; else result T-UI-label FAIL "$unlabeled without a label"; fi
