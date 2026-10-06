#!/bin/bash
# Back like Chrome. An empty new tab, a search, a result, a page inside it: Back walks the same way home,
# page by page to the new tab page, and only then leaves the app. A tab a page opened goes back to its opener.
set -u
source tools/lib.sh
log "---- back"
front; sleep 3

# Writes down where Back left us: the address bar, the number of tabs, and the app in front.
where() {
  dump
  python3 - "$OUT/ui.xml" "$1" <<'EOF' | tee -a $OUT/steps.txt
import sys, xml.etree.ElementTree as ET
addr, tabs = "-", "-"
for n in ET.parse(sys.argv[1]).iter("node"):
    d = n.get("content-desc", "")
    if d.startswith("Address ") and addr == "-": addr = d.split(". Tap")[0][len("Address "):] or "(empty)"
    if d.endswith(" open tabs"): tabs = d.split()[0]
print(f"  [{sys.argv[2]}] address: {addr} | tabs: {tabs}")
EOF
  echo "  [$1] in front: $($A shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*{[^ ]* [^ ]* //; s/}//')" | tee -a $OUT/steps.txt
}
# The phone's Back key alone: no bringing Raven back afterwards.
press_back() { $A shell input keyevent 4; sleep 4; log "Back pressed"; }

# 1. Empty new tab → DuckDuckGo search → Wikipedia result → a page linked from it.
newtab; sleep 3; shot b01_new_tab; where start
go "wikipedia%sandroid%soperating%ssystem"; sleep 15; shot b02_search; where search
tap "Android (operating system)" || go "https://en.m.wikipedia.org/wiki/Android_(operating_system)"
sleep 15; shot b03_result; where result
try_tap "=Linux kernel" || go "https://en.m.wikipedia.org/wiki/Linux_kernel"
sleep 15; shot b04_inner_page; where inner

# 2. Back four times: the result, the search, the empty new tab page, then out of the app.
press_back; shot b05_back1; where "back 1 (expect the result)"
press_back; shot b06_back2; where "back 2 (expect the search)"
press_back; shot b07_back3; where "back 3 (expect the new tab page)"
press_back; shot b08_back4; where "back 4 (expect the home screen)"

# 3. Back in Raven on the new tab page: Forward in the menu returns to the search page.
front; sleep 2; shot b09_reopened; where reopened
menu "Forward"; sleep 4; shot b10_forward; where "forward (expect the search)"
press_back; where "back (expect the new tab page)"

# 4. Typing on that new tab page starts afresh: Back goes straight to the new tab page.
go "example.com"; sleep 10; shot b11_typed; where typed
press_back; shot b12_back_after_typed; where "back (expect the new tab page)"
front; sleep 2

# 5. A link that opens a new tab: Back on it closes that tab and shows the page it came from.
go "data:text/html,<a%shref=https://example.com/%starget=_blank%sstyle=font-size:120px>Raven%sopener</a>"; sleep 6
shot b13_opener; where opener
tap "=Raven opener"; sleep 10; shot b14_opened_tab; where "opened tab (expect one more tab)"
press_back; shot b15_back_to_opener; where "back (expect the link page, one tab fewer)"
front; sleep 2
log "---- back done"
