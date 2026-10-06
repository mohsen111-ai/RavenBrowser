#!/bin/bash
# After smoke.sh: Home and Reload in the bar, no crash when you leave the app and come back,
# and media controls in the notification shade (and on the lock screen) like Chrome's.
set -u
source tools/lib.sh
log "---- media"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
pid() { $A shell pidof $APP | tr -d '\r'; }
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
state() { dump; python3 - "$OUT/ui.xml" <<'EOF'
import sys, xml.etree.ElementTree as ET
print(next((x.get("text") for x in ET.parse(sys.argv[1]).iter("node") if x.get("text", "").startswith("state: ")), "state: (not on screen)"))
EOF
}
# What Android knows about Raven's media session and its notification.
session() {
  $A shell dumpsys media_session > $OUT/n_media_session_$1.txt 2>&1
  $A shell dumpsys notification --noredact > $OUT/n_notifications_$1.txt 2>&1
  log "media [$1]: page $(state) | session: $(grep -A20 "package=$APP" $OUT/n_media_session_$1.txt | grep -m1 -oE 'state=PlaybackState \{state=[A-Z_]+|state=[0-9]+' ) | metadata: $(grep -m1 -oE 'description=Raven test tone[^,]*' $OUT/n_media_session_$1.txt) | notification: $(grep -c "pkg=$APP" $OUT/n_notifications_$1.txt) for Raven"
}

F_START=$(fatals)

# 1. Home: the button on the left of the bar shows the home page; Back returns to the page you were on.
newtab; sleep 3
go "example.com"; sleep 10; shot n01_page
tap "=Home"; sleep 3; shot n02_home
log "home: address after Home: $(addr)"
$A shell input keyevent 4; sleep 3; shot n03_back_from_home
log "home: address after Back (expect example.com): $(addr)"
# From Home, typing an address keeps the tab's history: Back goes to the page before.
tap "=Home"; sleep 2
go "example.org"; sleep 10
$A shell input keyevent 4; sleep 5; shot n04_back_after_typing_from_home
log "home: address after typing from Home then Back (expect example.com): $(addr)"

# 2. Refresh in the bar: Reload when the page is done, Stop while it is loading.
tap "=Reload"; sleep 5; shot n05_reloaded
go "$PAGES/slow"; sleep 4
try_tap "Open the unsecure site anyway" && sleep 4
shot n06_loading
tap "=Stop loading"; sleep 3; shot n07_stopped
log "refresh: after Stop the bar offers: $(dump; python3 tools/find.py $OUT/ui.xml '=Reload' > /dev/null && echo Reload || echo 'no Reload')"

# 3. Media: a page plays a tone, Android shows the controls, and the buttons reach the page.
go "$PAGES/media.html"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
shot n08_media_page
tap "=Play"; sleep 3
try_tap "=Allow" && sleep 2
sleep 4; shot n09_playing; session playing
$A shell cmd statusbar expand-notifications; sleep 3; shot n10_shade_controls
dump; cp $OUT/ui.xml $OUT/n_shade.xml
$A shell cmd statusbar collapse; sleep 2
# The system's media buttons, as the shade, the lock screen and headphones send them.
$A shell cmd media_session dispatch pause; sleep 3; session after_pause
$A shell cmd media_session dispatch play; sleep 3; session after_play
$A shell cmd media_session dispatch next; sleep 3; session after_next
$A shell cmd media_session dispatch previous; sleep 3; session after_previous
shot n11_after_buttons

# 4. Leaving the app: Android may destroy Raven's screen while it's away (here it always does). Coming back
# must not crash, and the music must keep playing meanwhile.
$A shell settings put global always_finish_activities 1
p0=$(pid); f0=$(fatals)
$A shell input keyevent 3; sleep 6; session in_background
$A shell cmd statusbar expand-notifications; sleep 3; shot n12_shade_from_home; $A shell cmd statusbar collapse; sleep 1
# The lock screen shows the same controls.
$A shell locksettings set-pin 1234 > /dev/null 2>&1
$A shell input keyevent 26; sleep 3; $A shell input keyevent 224; sleep 3; shot n13_lock_screen
$A shell input keyevent 82; sleep 1; $A shell input text 1234; $A shell input keyevent 66; sleep 3
$A shell locksettings clear --old 1234 > /dev/null 2>&1; $A shell wm dismiss-keyguard; sleep 2
front; sleep 6; shot n14_back_in_app; session back_in_app
log "leave and return with music: process $p0 -> $(pid), new crashes: $(( $(fatals) - f0 ))"
$A shell cmd media_session dispatch pause; sleep 2

# Leave and return from every main screen, with another app opened in between.
away() {
  $A shell input keyevent 3; sleep 2
  $A shell am start -a android.settings.SETTINGS > /dev/null 2>&1; sleep 4
  $A shell input keyevent 3; sleep 2
  front; sleep 5
}
f0=$(fatals); p0=$(pid)
away; shot n15_return_page
tap "open tabs"; sleep 3; away; shot n16_return_tabs; back
tap "Menu"; sleep 2; away; shot n17_return_menu; back
tap "=Home"; sleep 2; away; shot n18_return_home
$A shell input keyevent 4; sleep 2
privatetab; sleep 3; go "example.net"; sleep 8; away; shot n19_return_private
# The app switcher, and turning the phone while the screen is being rebuilt.
$A shell input keyevent KEYCODE_APP_SWITCH; sleep 3; shot n20_recents; front; sleep 4
$A shell settings put system accelerometer_rotation 0
$A shell settings put system user_rotation 1; sleep 4; away; shot n21_return_landscape
$A shell settings put system user_rotation 0; sleep 4
log "leave and return x6: process $p0 -> $(pid), new crashes: $(( $(fatals) - f0 ))"

# Low memory while away: Android asks Raven to free memory, then ends the process; tabs come back.
$A shell input keyevent 3; sleep 2
$A shell am send-trim-memory $APP RUNNING_CRITICAL > /dev/null 2>&1; sleep 2
$A shell am send-trim-memory $APP COMPLETE > /dev/null 2>&1; sleep 2
front; sleep 6; shot n22_after_trim_memory
$A shell input keyevent 3; sleep 2
$A shell am kill $APP; sleep 3
log "process after am kill: '$(pid)' (empty means it was ended)"
front; sleep 15; shot n23_after_process_ended
tap "open tabs"; sleep 3; shot n24_tabs_after_process_ended; back
$A shell settings put global always_finish_activities 0

# 5. More media: one tab plays at a time, a closed tab takes its controls with it, and a private tab
# keeps its title off the lock screen.
controls() { $A shell dumpsys notification --noredact | grep -c "pkg=$APP user=UserHandle{0} id=7001"; }
# From a clean bar, so both media tabs are on screen in the Tabs screen (Close all leaves one new tab).
tap "open tabs"; sleep 3; tap "^Tabs · "; sleep 1; tap "=Close all"; sleep 2; tap "!=Close all"; sleep 3; back
go "$PAGES/media.html?one"; sleep 6; tap "=Play"; sleep 4
newtab; sleep 3; go "$PAGES/media.html?two"; sleep 6; tap "=Play"; sleep 4; session two_playing
tap "open tabs"; sleep 3; shot n25_two_media_tabs
# Its title (the last element with that name: the first is the whole card, whose middle lies under the second tab's
# card, so runs 25 and 26 opened the second tab).
tap "!=Raven media test one"; sleep 3; shot n26_first_media_tab
log "tab on screen: $(dump; python3 - "$OUT/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
print(next((x.get("text") for x in ET.parse(sys.argv[1]).iter("node") if x.get("text", "").startswith("tab: ")), "-"))
PY
) (expect tab: one)"
log "one at a time: the first tab after the second started playing: $(state) (expect paused)"
tap "open tabs"; sleep 3
tap "^Close tab: Raven media test two"; sleep 2
tap "^Close tab: Raven media test one"; sleep 3; shot n27_media_tabs_closed; back
log "media tabs closed: media notifications left: $(controls) (expect 0)"
privatetab; sleep 3; go "$PAGES/media.html?private"; sleep 6; tap "=Play"; sleep 4; session private_playing
log "private: title for the lock screen: $(grep -m1 -oE 'description=[^,]*' $OUT/n_media_session_private_playing.txt) (expect Playing in a private tab)"
$A shell cmd statusbar expand-notifications; sleep 3; shot n28_private_controls; $A shell cmd statusbar collapse; sleep 1
tap "open tabs"; sleep 3; tap "=Close all"; sleep 2; tap "!=Close all"; sleep 3; back
log "private tabs closed: media notifications left: $(controls) (expect 0)"
log "media: new crashes in this whole test: $(( $(fatals) - F_START ))"

$A logcat -d > $OUT/logcat-media.txt
crashes $OUT/logcat-media.txt > $OUT/crashes-media.txt
grep -E "Raven|GeckoMediaSession|MediaSession" $OUT/logcat-media.txt | grep -v lookupManagedStorage > $OUT/n_media_log.txt || true
log "---- media done"
