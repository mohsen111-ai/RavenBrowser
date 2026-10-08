#!/bin/bash
# Split screen and the floating tab: the address bar and the reload button work for the half you touched (the
# bottom one too), and a video going fullscreen in a half or in the floating tab fills the screen, Back brings it back.
set -u
source tools/lib.sh
log "---- split"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
f0=$(fatals)
has() { dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && echo yes || echo no; }
dims() { python3 -c "import struct,sys; print('%dx%d' % struct.unpack('>II', open(sys.argv[1],'rb').read(24)[16:24]))" "$OUT/$1.png"; }
bar() { dump; python3 tools/find.py "$OUT/ui.xml" "=Menu" > /dev/null && echo "bar shown" || echo "no bar"; }
pause() { $A shell cmd media_session dispatch pause; sleep 2; }

# Two tabs: media page "first" (goes below) and the video page (goes on top).
newtab; sleep 3; go "$PAGES/media.html?first"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
newtab; sleep 3; go "$PAGES/video.html"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
menu "=Split screen"; sleep 2
tap "Split with Raven media test first"; sleep 6; shot s01_split
log "split: top $(has "^Top half: ") bottom $(has "^Bottom half: ") (expect yes yes)"

# Touch the bottom half (an empty spot of its page): the bar must now belong to it.
$A shell input tap $((W / 2)) $((H * 88 / 100)); sleep 3; shot s02_bottom_touched
log "bar after touching the bottom half: $(addr) (expect the media test page, ?first)"
# Reload it: the tone page starts again (its state goes back to idle).
tap "!=Play"; sleep 3
log "bottom half playing before reload: $(has "state: playing") (expect yes)"
tap "=Reload"; sleep 6; shot s03_bottom_reloaded
log "bottom half after Reload: playing $(has "state: playing") (expect no)"
# Send it to another address with the bar.
go "$PAGES/media.html?other"; sleep 6; shot s04_bottom_other
log "bottom half after typing another address: $(has "^Bottom half: Raven media test other") (expect yes) | top untouched: $(has "^Top half: Raven video test") (expect yes)"
# And the top half again.
$A shell input tap $((W / 2)) $((H * 30 / 100)); sleep 3
log "bar after touching the top half: $(addr) (expect video.html)"

# Fullscreen video in the top half.
$A shell input tap $((W / 2)) $((H * 45 / 100)); sleep 1
tap "=Fullscreen"; sleep 4; try_tap "=Got it" && sleep 2
shot s05_fullscreen_in_half; pause
log "fullscreen in a half: screen $(dims s05_fullscreen_in_half) (expect wider than tall) | $(bar) (expect no bar) | state $(has "fullscreen: yes") (expect yes)"
$A shell input keyevent 4; sleep 4; shot s06_after_back
log "after Back: screen $(dims s06_after_back) (expect taller than wide) | split back: top $(has "^Top half: ") bottom $(has "^Bottom half: ") (expect yes yes)"
menu "End split screen"; sleep 3

# Fullscreen video in the floating tab.
go "$PAGES/video.html"; sleep 6
menu "=Float"; sleep 4; shot s07_floating
log "floating: $(has "^Floating tab: ") (expect yes)"
tap "Move the floating tab"; sleep 1
$A shell input tap $((W * 70 / 100)) $((H * 22 / 100)); sleep 1
tap "=Fullscreen"; sleep 4; try_tap "=Got it" && sleep 2
shot s08_fullscreen_in_float; pause
log "fullscreen in the floating tab: screen $(dims s08_fullscreen_in_float) (expect wider than tall) | $(bar) (expect no bar)"
$A shell input keyevent 4; sleep 4; shot s09_float_after_back
log "after Back: screen $(dims s09_float_after_back) (expect taller than wide) | floating again: $(has "^Floating tab: ") (expect yes)"
tap "Move the floating tab"; sleep 1; tap "=Close the floating tab"; sleep 2
$A shell settings put system user_rotation 0

log "split: new crashes: $(( $(fatals) - f0 ))"
$A logcat -d > $OUT/logcat-split.txt
crashes $OUT/logcat-split.txt > $OUT/crashes-split.txt
log "---- split done"
