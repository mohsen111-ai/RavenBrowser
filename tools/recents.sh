#!/bin/bash
# Recent apps: open the list with the button next to Home, go to another app, come back to Raven from the list.
# Raven must come back showing its page, and nothing may crash (a black screen and a crash happened on a real phone).
set -u
source tools/lib.sh
log "---- recents"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
f0=$(fatals)
has() { dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && echo yes || echo no; }
alive() { $A shell pidof $APP | wc -w; }

newtab; sleep 3; go "$PAGES/media.html?recents"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
newtab; sleep 3; go "$PAGES/media.html?second"; sleep 6
pid0=$($A shell pidof $APP)
for round in 1 2 3; do
  $A shell input keyevent 187; sleep 3; shot rc${round}a_recents          # the recent apps list
  $A shell am start -a android.settings.SETTINGS > /dev/null 2>&1; sleep 4  # another app
  $A shell input keyevent 187; sleep 3; shot rc${round}b_recents_again
  # Raven's card is the one behind the front one: tap its middle (a swipe sideways would close it).
  $A shell input tap $((W / 2)) $((H / 2)); sleep 3
  front; sleep 6; shot rc${round}c_back_in_raven
  log "round $round: the page is back: $(has "tab: second") (expect yes) | Raven's process still the same: $([ "$($A shell pidof $APP)" = "$pid0" ] && echo yes || echo NO) | crashes so far $(( $(fatals) - f0 ))"
done
# Recent apps while Android throws Raven's screen away.
$A shell settings put global always_finish_activities 1
$A shell input keyevent 187; sleep 3
$A shell am start -a android.settings.SETTINGS > /dev/null 2>&1; sleep 4
front; sleep 8; shot rc4_after_destroy
log "after Android threw the screen away: the page is back: $(has "tab: second") (expect yes)"
$A shell settings put global always_finish_activities 0
# Memory pressure while away.
$A shell input keyevent 3; sleep 2
$A shell am send-trim-memory $APP RUNNING_CRITICAL > /dev/null 2>&1; sleep 2
$A shell am send-trim-memory $APP COMPLETE > /dev/null 2>&1; sleep 3
front; sleep 8; shot rc5_after_trim
log "after memory trimming: Raven shows something: $(has "open tabs") (expect yes) | crashes $(( $(fatals) - f0 ))"
$A shell run-as $APP cat files/last-exit.txt > $OUT/last-exit.txt 2>&1
log "recents: new crashes: $(( $(fatals) - f0 ))"
$A logcat -d > $OUT/logcat-recents.txt
crashes $OUT/logcat-recents.txt > $OUT/crashes-recents.txt
log "---- recents done"
