# Shared helpers for the emulator test scripts: tap things by their label, type, take screenshots.
OUT=smoke
mkdir -p $OUT
A="timeout 90 adb"
APP=app.raven.browser
MAIN=$APP/app.raven.browser.MainActivity
# The screen size, for swipes in the middle of it.
read W H < <($A shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //; s/x/ /')
log() { echo "$(date +%T) $*" | tee -a $OUT/steps.txt; }
shot() { $A exec-out screencap -p > "$OUT/$1.png"; log "screenshot $1"; }
dump() { $A shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; $A pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1; }
# Prints the centre of the first element labelled $1 (a leading "=" means an exact match, "!" the last one).
find_xy() {
  for i in 1 2 3; do
    dump
    if xy=$(python3 tools/find.py "$OUT/ui.xml" "$1"); then echo "$xy"; return 0; fi
    sleep 3
  done
  log "NOT FOUND: '$1'"; cp "$OUT/ui.xml" "$OUT/missing-$(echo "$1" | tr -c 'a-zA-Z0-9' '_').xml"; return 1
}
tap() { xy=$(find_xy "$1") && $A shell input tap $xy && log "tap '$1' at $xy"; }
hold() { xy=$(find_xy "$1") && $A shell input swipe $xy $xy 1000 && log "hold '$1' at $xy"; }
# Back closes sheets and screens; on the first page of a tab it leaves the app, so bring it back.
front() { $A shell am start -n $MAIN > /dev/null 2>&1; sleep 2; }
back() { $A shell input keyevent 4; sleep 2; front; }
# Quoted for the device shell, so addresses may contain < > ; and the like ("%s" types a space).
go() { tap "Address" && sleep 2 && $A shell input text "'$1'" && sleep 1 && $A shell input keyevent 66; log "go $1"; }
hold_at() { $A shell input swipe $1 $2 $1 $2 1000; log "hold at $1 $2"; }
# The menu's lower tiles can sit below the screen's edge on a small phone: scroll the sheet up if so.
menu() {
  tap "Menu" && sleep 2 || return 1
  dump
  python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null || {
    $A shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 40 / 100)) 400; sleep 1; log "menu scrolled"; }
  tap "$1"
}
# Opens the Tabs screen on its everyday side and starts a fresh tab with the button at the bottom
# (a blank tab's piece is called "New tab" too).
newtab() { tap "open tabs" && sleep 3 && tap "^Tabs · " && sleep 1 && tap "!=New tab"; }
# A new private tab the quick way: hold the tabs button, then "New private tab".
privatetab() { hold "open tabs" && sleep 1 && tap "=New private tab"; }
crashes() { grep -E "FATAL EXCEPTION|ANR in $APP|Process: $APP" -A 30 "$1" || echo "no crashes"; }
# Taps $1 only if it's on screen right now (no waiting), e.g. a permission dialog that may not come.
try_tap() { dump; xy=$(python3 tools/find.py "$OUT/ui.xml" "$1") && $A shell input tap $xy && log "tap '$1' at $xy"; }
# The page address in Raven's bar, from its label "Address <url>. Tap to search or edit".
addr() { dump; python3 - "$OUT/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    d = n.get("content-desc", "")
    if d.startswith("Address "):
        print(d.split(". Tap")[0][len("Address "):] or "(empty)"); break
else:
    print("-")
PY
}
# Settings: most settings have a page of their own now, below the fold on a small phone; the search box finds them.
# Opens Settings, searches for $1 and opens the result starting with $2.
setting() {
  menu "Settings" && sleep 3 || return 1
  tap "Search settings" && sleep 1 && $A shell input text "'$1'" && sleep 2
  $A shell input keyevent 4; sleep 1
  # Nothing found: out of Settings again, so the next step starts from the browser.
  tap "^$2" || { $A shell input keyevent 4; sleep 1; front; return 1; }
  sleep 2
}
# Leaves Settings from one of its pages: Back to the main page, Back out, and Raven in front.
leave_settings() { $A shell input keyevent 4; sleep 1; $A shell input keyevent 4; sleep 2; front; }
