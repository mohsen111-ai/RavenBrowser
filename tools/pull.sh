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

# 3. Scrolled down, a pull only scrolls back up; once at the top, the next pull reloads.
$A shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 30 / 100)) 300; sleep 2
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

log "pull: crashes $(( $(fatals) - f0 )) (expect 0)"
$A logcat -d > $OUT/logcat-pull.txt
crashes $OUT/logcat-pull.txt > $OUT/crashes-pull.txt
log "---- pull done"
