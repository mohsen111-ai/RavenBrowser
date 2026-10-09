#!/bin/bash
# Videos: playing, fullscreen turning the screen to landscape for a wide video, leaving the app in the middle of
# a fullscreen video (Android throws the screen away meanwhile) and coming back to it, and Back leaving fullscreen.
set -u
source tools/lib.sh
log "---- video"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
# Width x height of a screenshot: wider than tall means the screen turned to landscape.
dims() { python3 -c "import struct,sys; print('%dx%d' % struct.unpack('>II', open(sys.argv[1],'rb').read(24)[16:24]))" "$OUT/$1.png"; }
bar() { dump; python3 tools/find.py "$OUT/ui.xml" "=Menu" > /dev/null && echo "bar shown" || echo "no bar"; }
page() { dump; python3 - "$OUT/ui.xml" <<'EOF'
import sys, xml.etree.ElementTree as ET
# Page text can come as a node's text or its description, and long lines may be split.
texts = [t for x in ET.parse(sys.argv[1]).iter("node") for t in (x.get("text", ""), x.get("content-desc", "")) if t]
print(next((t for t in texts if t.startswith(("state: ", "play failed", "fullscreen failed"))), "state: (not on screen)"))
EOF
}
# uiautomator waits for the screen to settle before it reads it, and a playing video never settles: the
# video is paused for every check that reads the screen (smoke-6 read an empty screen while it played).
pause() { $A shell cmd media_session dispatch pause; sleep 2; }
play() { $A shell cmd media_session dispatch play; sleep 3; }
f0=$(fatals)

newtab; sleep 3; go "$PAGES/video.html"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
tap "=Play"; sleep 4; shot v01_playing; pause
log "video: $(page)"
tap "=Fullscreen"; sleep 4
# The first fullscreen ever, Android shows "Viewing full screen ... Got it" over everything until it's tapped.
try_tap "=Got it" && sleep 2
play; shot v02_fullscreen; pause
log "fullscreen: screen $(dims v02_fullscreen) (expect wider than tall) | $(bar) (expect no bar)"

# Away in the middle of the fullscreen video, with Android throwing Raven's screen away meanwhile.
$A shell settings put global always_finish_activities 1
play
$A shell input keyevent 3; sleep 4
$A shell am start -a android.settings.SETTINGS > /dev/null 2>&1; sleep 3
$A shell input keyevent 3; sleep 2
front; sleep 6; shot v03_back_to_fullscreen; pause
log "back in the app: screen $(dims v03_back_to_fullscreen) (expect taller than wide: the video stepped back to its page for the trip) | $(bar) (expect bar shown)"
$A shell settings put global always_finish_activities 0

# Raven brought the video back to its page for the trip, so the bar is there and the screen is upright already.
sleep 2; shot v04_after_back
# What Android itself says about the screen's direction and the rotation settings, to tell an app request from a setting.
{ echo "--- after Back"; $A shell settings get system accelerometer_rotation; $A shell settings get system user_rotation
  $A shell dumpsys window displays | grep -E -m6 "mUserRotation|mCurrentRotation|mRotation=|mLastOrientation|mUserRotationMode"
  $A shell dumpsys activity activities | grep -E -m4 "requestedOrientation|mRequestedOrientation"; } > $OUT/v_rotation.txt 2>&1
log "after return: screen $(dims v04_after_back) (expect taller than wide) | $(bar) (expect bar shown) | $(page)"

# Fullscreen again, and out again with Back (Back leaves fullscreen: the bar comes back and the screen turns upright).
tap "=Fullscreen"; sleep 4; try_tap "=Got it" && sleep 2; shot v05_fullscreen_again
$A shell input keyevent 4; sleep 3; shot v06_out_again
log "second fullscreen: $(dims v05_fullscreen_again) then $(dims v06_out_again) | $(bar) (expect bar shown)"
# Whatever happened above, leave Raven upright with its bar for the scripts that follow.
try_tap "=Got it"; [ "$(bar)" = "no bar" ] && { $A shell input keyevent 4; sleep 3; }
$A shell settings put system user_rotation 0
log "video: new crashes: $(( $(fatals) - f0 ))"
$A logcat -d > $OUT/logcat-video.txt
crashes $OUT/logcat-video.txt > $OUT/crashes-video.txt
log "---- video done"
