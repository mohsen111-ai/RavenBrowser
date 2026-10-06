#!/bin/bash
# The Tabs screen's hand of cards: closing one with its X and throwing one away with a swipe, everyday and
# private tabs kept apart, and Close all on one side leaving the other side alone.
set -u
source tools/lib.sh
log "---- tabs"
# The emulator runs with animations off; switch them on here so the screenshots catch them moving.
anim() { for k in animator_duration_scale window_animation_scale transition_animation_scale; do $A shell settings put global $k $1; done; }
anim 1
front; sleep 3

# The counts on the Tabs | Private switch: "Tabs · N" and "Private · M".
count() { dump; python3 - "$OUT/ui.xml" "$1" <<'EOF' | tee -a $OUT/steps.txt
import re, sys, xml.etree.ElementTree as ET
tabs, private = "-", "-"
for x in ET.parse(sys.argv[1]).iter("node"):
    t = x.get("text", "")
    if m := re.match(r"Tabs · (\d+)", t): tabs = m.group(1)
    if m := re.match(r"Private · (\d+)", t): private = m.group(1)
print(f"  [{sys.argv[2]}] tabs: {tabs} | private: {private}")
EOF
}

# Start from a clean bar: Close all on the everyday side asks first, then leaves one new tab.
tap "open tabs"; sleep 3; tap "^Tabs · "; sleep 1
tap "=Close all"; sleep 2; tap "!=Close all"; sleep 3; shot t00_after_close_all; count "after Close all (expect tabs 1)"
back
# Four everyday tabs with pages: the first in the tab Close all left.
go example.com; sleep 6
for u in example.org "data:text/html,<title>Third</title><h1>Third</h1>" "data:text/html,<title>Fourth</title><h1>Fourth</h1>"; do
  newtab; sleep 3; go "$u"; sleep 6
done
tap "open tabs"; sleep 3; shot t01_bar; count "four pages"

# The X on a card: it spins away.
tap "^Close tab: Fourth"; sleep 0.15; shot t02_closing; sleep 2; shot t03_closed; count "after X (expect one fewer)"

# A quick swipe sideways throws a card away.
if xy=$(find_xy "^Third"); then
  set -- $xy
  $A shell input swipe $1 $2 $(( $1 + W * 6 / 10 )) $2 150; log "swipe on Third"
  sleep 0.1; shot t04_snapping; sleep 2; shot t05_snapped; count "after swipe (expect one fewer)"
fi
# A short drag springs back instead.
if xy=$(find_xy "^Example Domain"); then
  set -- $xy
  $A shell input swipe $1 $2 $(( $1 + W / 10 )) $2 600; log "short drag on Example Domain"
  sleep 2; shot t06_sprang_back; count "after short drag (expect no change)"
fi

# Private tabs live on their own side, in eclipse violet.
tap "^Private · "; sleep 2; shot t07_private_side_empty
tap "!=New private tab"; sleep 3; go "example.net"; sleep 8
tap "open tabs"; sleep 3; shot t08_private_side_one; count "one private (expect private 1)"
tap "^Tabs · "; sleep 2; shot t09_everyday_side; count "everyday side"
# Close all on the private side asks first, then leaves the everyday tabs alone.
tap "^Private · "; sleep 2
tap "=Close all"; sleep 2; shot t10_close_all_dialog
tap "!=Close all"; sleep 3; shot t11_private_closed; count "private closed (everyday unchanged)"

# Tapping a piece opens it.
tap "^Tabs · "; sleep 2
tap "^Example Domain"; sleep 3; shot t12_piece_opened
anim 0
log "---- tabs done"
