#!/bin/bash
# Pull down to refresh: pulling a page down from its top reloads it, like other browsers. A short pull doesn't, a
# pull on a page that's scrolled down only scrolls it back up, a page that takes touches for itself (like a map)
# keeps them, and a sideways swipe does nothing. In split screen a pull reloads only that half; in full screen for
# pages a swipe from the very top edge brings the bars back instead.
set -u
source tools/lib.sh
log "---- pull"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
f0=$(fatals)
has() { dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && echo yes || echo no; }
open_page() { go "$1"; sleep 6; try_tap "Open the unsecure site anyway" && sleep 5; }
# The first page text starting with $1, or "-".
text() { dump; python3 - "$OUT/ui.xml" "$1" <<'EOF'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    for t in (n.get("text", ""), n.get("content-desc", "")):
        if t.startswith(sys.argv[2]):
            print(t); sys.exit()
print("-")
EOF
}
# A finger pulling down from $1% to $2% of the screen's height, slowly enough to be a drag.
pull() { $A shell input swipe $((W / 2)) $((H * $1 / 100)) $((W / 2)) $((H * $2 / 100)) ${3:-700}; }

# 1. A full pull from the top of the page reloads it.
newtab; sleep 3; open_page "$PAGES/pull.html"
log "loaded: $(text "Loaded ") (expect 1 time)"
pull 30 75; sleep 1; shot p01_pulled; sleep 4
log "after a pull from the top: $(text "Loaded ") (expect 2 times)"

# 2. A short pull doesn't.
pull 30 36 300; sleep 4
log "after a short pull: $(text "Loaded ") (expect still 2 times)"

# 3. Scrolled down, a pull only scrolls back up; once at the top, the next pull reloads. (Slowly, so the page doesn't
# fling further than the finger went.)
$A shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 30 / 100)) 1200; sleep 2
pull 30 75; sleep 4; shot p02_scrolled_back
log "scrolled down, then a pull: $(text "Loaded ") (expect still 2 times: it only scrolled up) | top of the page in view: $(has "^Loaded ")"
pull 30 75; sleep 5
log "then a pull from the top: $(text "Loaded ") (expect 3 times)"

# 4. Sideways swipes do nothing.
$A shell input swipe $((W / 5)) $((H / 2)) $((W * 4 / 5)) $((H * 55 / 100)) 400; sleep 3
log "after a sideways swipe: $(text "Loaded ") (expect still 3 times)"

# 5. A page that takes every touch itself, like a map: the pull is the page's.
open_page "$PAGES/pull.html?grab=1"
before=$(text "Map: loaded ")
pull 30 75; sleep 5; shot p03_map
log "a map-like page: before '$before', after a pull '$(text "Map: loaded ")' (expect the same)"

# 5b. More kinds of page: one that listens to touches but leaves them (reloads), one that says no pull in its CSS
# (doesn't), one too short to scroll (reloads), and one that takes the touch late (noted, as Firefox behaves the same).
for mode in listen contain short late; do
  open_page "$PAGES/pull.html?$mode=1"; sleep 1
  b=$(text "Loaded "); pull 30 75; sleep 5; a=$(text "Loaded ")
  case $mode in contain) want="the same";; late) want="either";; *) want="one more";; esac
  log "pull on a '$mode' page: before '$b', after '$a' (expect $want)"
done
shot p03b_modes

# 5c. A pull, then straight to another tab: the other tab shows no turning circle, and a pull there works.
open_page "$PAGES/pull.html"
pull 30 75; sleep 0.3
newtab; sleep 2; open_page "$PAGES/pull.html?name=Other"; shot p03c_other_tab
b=$(text "Other: loaded"); pull 30 75; sleep 5
log "after going to another tab mid-reload: a pull there reloads: before '$b', after '$(text "Other: loaded")' (expect one more)"

# 6. Split screen: a pull in the bottom half reloads only that half.
newtab; sleep 3; open_page "$PAGES/pull.html"
newtab; sleep 3; open_page example.com
menu "=Split screen"; sleep 2; tap "Split with Raven pull test"; sleep 6; shot p04_split
b0=$(text "Loaded ")
pull 62 92; sleep 5; shot p05_split_pulled
log "split, bottom half pulled: before '$b0', after '$(text "Loaded ")' (expect one more) | top half: $(text "Top half: ")"
menu "End split screen"; sleep 3

# 7. Full screen for pages: a swipe from the very top edge shows the bars and doesn't reload; a pull lower down does.
newtab; sleep 3; open_page "$PAGES/pull.html"
menu "Full screen for pages"; sleep 3
f1=$(text "Loaded ")
$A shell input swipe $((W / 2)) 3 $((W / 2)) $((H * 40 / 100)) 500; sleep 1; shot p06_edge_swipe
log "full screen, swipe from the top edge: bars back $(has "^Address ") (expect yes) | before '$f1', after '$(sleep 3; text "Loaded ")' (expect the same)"
sleep 4
pull 30 75; sleep 5
log "full screen, a pull lower down: $(text "Loaded ") (expect one more than '$f1')"
menu "Full screen for pages"; sleep 3

# 8. The floating tab: a pull in its window reloads its page. A fullscreen video never reloads with a pull.
newtab; sleep 3; open_page "$PAGES/pull.html?name=Floating"
menu "Float this tab"; sleep 4
if xy=$(find_xy "^Floating tab: "); then
  set -- $xy
  fb=$(text "Floating: loaded")
  $A shell input swipe $1 $(( $2 - H / 10 )) $1 $(( $2 + H / 8 )) 700; sleep 5; shot p07_floating_pulled
  log "floating tab pulled: before '$fb', after '$(text "Floating: loaded")' (expect one more)"
  tap "Move the floating tab"; sleep 1; tap "=Close the floating tab"; sleep 2
fi
newtab; sleep 3; open_page "$PAGES/video.html"
tap "=Play"; sleep 2; tap "=Fullscreen"; sleep 4; $A shell cmd media_session dispatch pause; sleep 1
$A shell log -t RavenTest video-pull; pull 20 80; sleep 4; shot p08_video_pull
log "a pull on a fullscreen video: $(grep -c 'pull: refresh' <($A logcat -d | sed -n '/RavenTest: video-pull/,$p')) reloads after the mark (expect 0) | still fullscreen: $(text "state: ")"
$A shell input keyevent 4; sleep 3

log "pull: crashes $(( $(fatals) - f0 )) (expect 0)"
$A logcat -d > $OUT/logcat-pull.txt
crashes $OUT/logcat-pull.txt > $OUT/crashes-pull.txt
log "---- pull done"
