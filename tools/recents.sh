#!/bin/bash
# Leaving Raven through Recent Apps for another app, then tapping Raven there to come back: the page must come back,
# not a black screen and then a crash (the owner saw that on their phone). Every way Android can treat Raven while
# it's away: only stopped, told to free memory, its screen thrown away ("Don't keep activities"), or the whole app
# stopped to make room. And with the home page's live wallpaper, the floating tab, split screen, full screen for
# pages, and a fullscreen video (which shrinks into picture-in-picture on the way out).
set -u
source tools/lib.sh
log "---- recents"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null; $A shell settings put global always_finish_activities 0' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
f0=$(fatals)
has() { dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && echo yes || echo no; }
open_page() { go "$1"; sleep 6; try_tap "Open the unsecure site anyway" && sleep 5; }
mark() { $A shell log -t RavenTest "$1"; }
# How bright the middle of the screen is: 0 is black, 255 white. The test page is light (over 200).
bright() { $A exec-out screencap > $OUT/screen.raw && python3 tools/bright.py $OUT/screen.raw; }
infront() { case "$($A shell dumpsys window | grep -m1 mCurrentFocus)" in *$APP*) echo yes;; *) echo no;; esac; }
pid() { $A shell pidof $APP | tr -d '\r' | awk '{print $1}'; }

# Recent Apps, then another app (Android's Settings), the way the owner leaves.
away() {
  mark "away $1"
  $A shell input keyevent KEYCODE_APP_SWITCH; sleep 3; shot "$1_a_recents"
  $A shell am start -a android.settings.SETTINGS > /dev/null; sleep 4
}
# Recent Apps again, and a tap on Raven's card there (cards are named after their apps). Raven's card sits beside
# the app in front; if the launcher doesn't name its cards, a quick switch (Recent Apps twice) goes back to Raven.
come_back() {
  mark "back $1"
  $A shell input keyevent KEYCODE_APP_SWITCH; sleep 3
  dump
  if xy=$(python3 tools/find.py "$OUT/ui.xml" "=Raven"); then
    $A shell input tap $xy; log "tapped Raven's card in Recent Apps at $xy"
  else
    $A shell input swipe $((W / 4)) $((H / 2)) $((W * 3 / 4)) $((H / 2)) 300; sleep 2; dump
    if xy=$(python3 tools/find.py "$OUT/ui.xml" "=Raven"); then
      $A shell input tap $xy; log "tapped Raven's card in Recent Apps at $xy (after a swipe)"
    else
      cp "$OUT/ui.xml" "$OUT/$1_recents_ui.xml"
      log "Raven's card not named in Recent Apps: quick switch instead"
      $A shell input keyevent KEYCODE_APP_SWITCH; sleep 3
    fi
  fi
}
# What came back: Raven in front, the same app process or a new one, the page's brightness at once and later.
check() {
  local name=$1 expect=$2 before=$3
  sleep 1; local b1; b1=$(bright); shot "$name"_b_back
  sleep 7; local b2; b2=$(bright); shot "$name"_c_later
  local now; now=$(pid)
  log "$name: in front $(infront) | app process ${now:-none} (was $before) | middle of the screen right away $b1, after 8 s $b2 ($expect) | crashes so far $(( $(fatals) - f0 ))"
}

# 1. A page, only stopped meanwhile (the everyday case).
newtab; sleep 3; open_page "$PAGES/long.html"
$A shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 40 / 100)) 300; sleep 2
p=$(pid); away r01; come_back r01; check r01 "expect over 200" "$p"

# 2. Three more tabs, and while away Android asks Raven to free memory: the tabs out of sight sleep.
for i in 1 2 3; do newtab; sleep 3; open_page "$PAGES/jar.html"; done
newtab; sleep 3; open_page "$PAGES/long.html"
p=$(pid); away r02
$A shell am send-trim-memory $APP RUNNING_CRITICAL; sleep 1
$A shell am send-trim-memory $APP COMPLETE; sleep 2
come_back r02; check r02 "expect over 200" "$p"
tap "open tabs"; sleep 3; shot r02_d_tabs; $A shell input keyevent 4; sleep 2

# 3. Android throws Raven's screen away while it's away ("Don't keep activities"), and builds a new one on return.
$A shell settings put global always_finish_activities 1
p=$(pid); away r03; come_back r03; check r03 "expect over 200" "$p"
# Twice more, the second time from the Tabs screen.
p=$(pid); away r04; come_back r04; check r04 "expect over 200" "$p"
tap "open tabs"; sleep 3
p=$(pid); away r05; come_back r05; check r05 "the Tabs screen" "$p"
$A shell input keyevent 4; sleep 2
$A shell settings put global always_finish_activities 0

# 4. Android stops the whole app to make room while it's away; Recent Apps starts it again where it was.
p=$(pid); away r06
$A shell am kill $APP; sleep 3
log "after am kill: app process $(pid) (expect none)"
come_back r06; sleep 20; check r06 "expect over 200 once the page is back" "$p"

# 5. The home page, with a live wallpaper moving.
newtab; sleep 4
p=$(pid); away r07; come_back r07; check r07 "the home page, dark" "$p"

# 6. The floating tab over a page.
newtab; sleep 3; open_page "$PAGES/long.html"
menu "Float this tab"; sleep 4
newtab; sleep 3; open_page "$PAGES/jar.html"
p=$(pid); away r08; come_back r08; check r08 "a page with the floating tab" "$p"
log "floating tab still there: $(has "^Floating tab: ") (expect yes)"
$A shell settings put global always_finish_activities 1
p=$(pid); away r09; come_back r09; check r09 "a page with the floating tab, screen rebuilt" "$p"
$A shell settings put global always_finish_activities 0
log "floating tab still there: $(has "^Floating tab: ") (expect yes)"
if [ "$(has "^Floating tab: ")" = yes ]; then tap "Move the floating tab"; sleep 1; tap "=Close the floating tab"; sleep 2; fi

# 7. Split screen.
menu "=Split screen"; sleep 2; tap "Split with Raven long page"; sleep 6
p=$(pid); away r10; come_back r10; check r10 "split screen" "$p"
log "split still there: $(has "^Top half: ") $(has "^Bottom half: ") (expect yes yes)"
$A shell settings put global always_finish_activities 1
p=$(pid); away r11; come_back r11; check r11 "split screen, screen rebuilt" "$p"
$A shell settings put global always_finish_activities 0
menu "End split screen"; sleep 3

# 8. Full screen for pages.
newtab; sleep 3; open_page "$PAGES/long.html"
menu "Full screen for pages"; sleep 3
p=$(pid); away r12; come_back r12; check r12 "expect over 200, no bars" "$p"
$A shell input swipe $((W / 2)) 5 $((W / 2)) $((H / 3)) 300; sleep 1
menu "Full screen for pages"; sleep 3

# 9. A fullscreen video playing: leaving shrinks it into picture-in-picture; tapping Raven brings it back full.
newtab; sleep 3; open_page "$PAGES/video.html"
tap "=Play"; sleep 2; tap "=Fullscreen"; sleep 5; shot r13_a_fullscreen
p=$(pid); away r13; come_back r13; check r13 "the video, fullscreen or back in its page" "$p"
$A shell input keyevent 4; sleep 3

# 10. Five quick round trips in a row.
newtab; sleep 3; open_page "$PAGES/long.html"
for i in 1 2 3 4 5; do
  $A shell input keyevent KEYCODE_APP_SWITCH; sleep 1
  $A shell am start -a android.settings.SETTINGS > /dev/null; sleep 1
  $A shell input keyevent KEYCODE_APP_SWITCH; sleep 1
  dump; xy=$(python3 tools/find.py "$OUT/ui.xml" "=Raven") && $A shell input tap $xy || front
  sleep 2
done
p=$(pid); check r14 "expect over 200 after five quick trips" "$p"

log "recents: crashes $(( $(fatals) - f0 )) (expect 0)"
$A logcat -d > $OUT/logcat-recents.txt
crashes $OUT/logcat-recents.txt > $OUT/crashes-recents.txt
grep -E "Raven|GeckoView|GeckoSession|Compositor|syncResume|AndroidRuntime|ActivityManager: (Start|Kill|Process)" $OUT/logcat-recents.txt \
  | grep -v lookupManagedStorage > $OUT/n_recents_log.txt || true
log "---- recents done"
