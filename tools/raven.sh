#!/bin/bash
# Raven's new parts: a new sky each time Raven opens, swiping the address bar between tabs, bookmarks,
# flocks (tab groups), the wallpaper settings and picture-in-picture.
set -u
source tools/lib.sh
log "---- raven"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
f0=$(fatals)
# The first label on screen that starts with $1 (without it), or "-".
desc() { dump; python3 - "$OUT/ui.xml" "$1" <<'EOF'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    for t in (n.get("content-desc", ""), n.get("text", "")):
        if t.startswith(sys.argv[2]):
            print(t[len(sys.argv[2]):]); sys.exit()
print("-")
EOF
}
has() { dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && echo yes || echo no; }
# Scrolls the screen up until $1 shows (Settings is long).
seek() { for i in 1 2 3 4 5 6 7 8; do dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && return 0; $A shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 35 / 100)) 400; sleep 1; done; log "NOT FOUND after scrolling: $1"; return 1; }

# 1. The sky: each time Raven comes back from the background, the next wallpaper's turn.
newtab; sleep 4; shot r01_home
skies="$(desc "Wallpaper: ")"
for i in 1 2 3 4; do
  $A shell input keyevent 3; sleep 2; front; sleep 3
  skies="$skies, $(desc "Wallpaper: ")"
done
shot r02_home_again
log "sky after each return: $skies (expect a different one each time, never the same twice running)"

# 2. Swiping the address bar sideways moves between tabs.
go example.com; sleep 6
newtab; sleep 3; go example.org; sleep 6
log "address before swiping: $(addr) (expect example.org)"
# A short swipe (W/7 each way from the middle), so on a narrow phone it starts on the address, not the Home button.
if xy=$(find_xy "^Address "); then
  set -- $xy
  $A shell input swipe $(( $1 - W / 7 )) $2 $(( $1 + W / 7 )) $2 250; sleep 3; shot r03_swiped_back
  log "after swiping right: $(addr) (expect example.com)"
  $A shell input swipe $(( $1 + W / 7 )) $2 $(( $1 - W / 7 )) $2 250; sleep 3; shot r04_swiped_forward
  log "after swiping left: $(addr) (expect example.org)"
  $A shell input swipe $(( $1 + W / 7 )) $2 $(( $1 - W / 7 )) $2 250; sleep 3
  log "swiping left past the last tab: $(addr) (expect example.org still: it only stretches)"
fi

# 3. Bookmarks: save the page, find it in Bookmarks and in the address suggestions, remove it and undo.
menu "Bookmark this page"; sleep 1; shot r05_saved_snackbar; sleep 4
tap "Menu"; sleep 2; log "menu shows the page saved: $(has "Remove bookmark") (expect yes)"; shot r06_menu_saved; back
menu "=Bookmarks"; sleep 3; shot r07_bookmarks
log "Example Domain in bookmarks: $(has "^Example Domain") (expect yes)"
tap "More for Example Domain"; sleep 2; shot r08_bookmark_actions
tap "=Remove"; sleep 1; shot r09_removed
tap "=Undo"; sleep 2; shot r10_undone
log "after Undo: $(has "^Example Domain") (expect yes)"
back
tap "Address"; sleep 2; $A shell input text "exam"; sleep 3; shot r11_suggestions
log "bookmark first in suggestions: $(has "=Bookmark") (expect yes)"
$A shell input keyevent 4; sleep 1; $A shell input keyevent 4; sleep 2; front

# 4. Flocks: hold a tab card, start a flock with it, see it on the Flocks side.
tap "open tabs"; sleep 3; tap "^Tabs · "; sleep 2
hold "^Example Domain"; sleep 2; shot r12_card_held
tap "New flock with this tab"; sleep 2
# The keyboard's ✓ key creates it (on a small phone the keyboard covers the Create button).
$A shell input text "Work"; sleep 1; shot r13_new_flock
$A shell input keyevent 66; sleep 3; shot r14_flock_open
log "flocks: $(desc "Flocks · ") (expect 1) | open flock shows Example Domain: $(has "^Example Domain") (expect yes)"
tap "=All flocks"; sleep 2; shot r15_flocks
log "Work's tile: $(desc "Flock Work") (expect ', 1 tab')"
tap "^Tabs · "; sleep 1; back

# 5. Wallpapers in Settings: turn the rotation off and pick Snow; the home screen shows it.
setting "wallpaper" "Wallpapers"
seek "New wallpaper each time"; sleep 1; shot r16_wallpaper_settings
tap "!=New wallpaper each time"; sleep 1
seek "=Snow"; tap "=Snow"; sleep 2; shot r17_snow_chosen
leave_settings; newtab; sleep 4; shot r18_home_snow
log "home after choosing Snow: $(desc "Wallpaper: ") (expect Snow)"
$A shell input keyevent 3; sleep 2; front; sleep 3
log "and after coming back: $(desc "Wallpaper: ") (expect Snow: the rotation is off)"
setting "wallpaper" "Wallpapers"; seek "New wallpaper each time"; tap "!=New wallpaper each time"; sleep 1; leave_settings

# 6. The menu's squares, the VPN square (no VPN on the emulator), and Translate on a French page.
tap "Menu"; sleep 2; shot r21_menu
log "menu: VPN square says $(desc "VPN: ") (expect off) | Private gone from the grid: $(has "=Private tab") (expect no)"
$A shell input keyevent 4; sleep 2; front
newtab; sleep 3; go "$PAGES/french.html"; sleep 8
try_tap "Open the unsecure site anyway" && sleep 5
shot r22_french
log "translate offered in the bar: $(has "=Translate this page") (expect yes on phones 360 dp or wider; on this narrow one it's in the menu)"
tap "=Translate this page" || menu "Translate page"; sleep 3; shot r23_translate_sheet
tap "!=Translate"; sleep 45; shot r24_translated
log "page after translating: $(has "raven") (expect yes: corbeau became raven)"
# Its options again: from the bar when it has room, otherwise from the menu.
try_tap "Translated, tap for options" || menu "Translate page"; sleep 2; shot r24b_translate_options
tap "=Show original"; sleep 5; shot r25_original
log "after Show original: $(has "corbeau") (expect yes)"

# 7. Picture-in-picture: leaving Raven during a fullscreen video shrinks it into a window.
newtab; sleep 3; go "$PAGES/video.html"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
tap "=Play"; sleep 3; tap "=Fullscreen"; sleep 4
try_tap "=Got it" && sleep 2
$A shell input keyevent 3; sleep 4; shot r19_pip
log "picture-in-picture: $($A shell dumpsys activity activities | grep -c -iE "mode=pinned|pinned stack|windowingMode=pinned") pinned window(s) (expect 1 or more)"
log "still playing: $($A shell dumpsys media_session | grep -m1 -oE 'state=PlaybackState \{state=[A-Z_]+|state=[0-9]+')"
front; sleep 4; shot r20_back_from_pip
$A shell cmd media_session dispatch pause; sleep 2
$A shell input keyevent 4; sleep 2; front

# 8. Raven's VPN: add a location file (a dummy one: the emulator can't reach Proton), switch it on (Android asks
# once), see the menu show the country, switch it off. Checks the VPN engine loads and runs in the real build.
key() { head -c 32 /dev/urandom | base64; }
printf '[Interface]\nPrivateKey = %s\nAddress = 10.2.0.2/32\nDNS = 10.2.0.1\n\n[Peer]\nPublicKey = %s\nAllowedIPs = 0.0.0.0/0, ::/0\nEndpoint = 10.0.2.2:51820\n' \
  "$(key)" "$(key)" > $OUT/raven-NL-1.conf
$A push $OUT/raven-NL-1.conf /sdcard/Download/raven-NL-1.conf > /dev/null
menu "VPN:"; sleep 2; shot r26_vpn_sheet
tap "Add your first location"; sleep 4; shot r27_picker
# The file picker: open its list of places, then Downloads, then the file.
try_tap "Show roots" && sleep 2; try_tap "=Downloads" && sleep 3
# The picker may show files as tiles, where a tap on the name did nothing (runs 13 and 21): a list instead.
try_tap "=List view" && sleep 2
tap "raven-NL-1"; sleep 4; shot r28_location_added
log "location added: $(has "=Netherlands") (expect yes)"
tap "=Netherlands"; sleep 3; shot r29_vpn_permission
try_tap "=OK" && sleep 6
shot r30_vpn_on
log "VPN sheet: $(desc "Raven is browsing from ") (expect the Netherlands) | Android's VPN networks: $($A shell dumpsys connectivity | grep -c -E "VPN CONNECTED|NetworkAgentInfo.*VPN")"
$A shell input keyevent 4; sleep 2; front
tap "Menu"; sleep 2; log "menu square: $(desc "VPN: ") (expect browsing from the Netherlands)"; $A shell input keyevent 4; sleep 2; front
menu "VPN:"; sleep 2; tap "Raven's VPN"; sleep 4; shot r31_vpn_off
log "after switching off: $(desc "Raven is browsing from ") (expect -)"
# Whatever was left open above (the file picker, the VPN sheet), the next part starts from Raven's browser: in run 21
# the sheet stayed open and every step after it failed.
for i in 1 2 3; do $A shell input keyevent 4; sleep 2; done; front; sleep 2

# 9. The floating tab: a tab in a small window over the others. Moved by its bar, pushed against the edge it parks
# as an icon and its sound pauses; tapped, it opens again; Full size brings it back as the tab on screen.
count() { dump; grep -o "$1" $OUT/ui.xml | wc -l; }
newtab; sleep 3; go "$PAGES/media.html?float"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
tap "=Play"; sleep 3
menu "Float this tab"; sleep 4; shot r32_floating
log "floating: $(desc "Floating tab: ") (expect Raven media test float) | under it: $(addr) (expect another tab)"
log "the floating page still plays: $(has "state: playing") (expect yes)"
# Bigger from a corner: drag the bottom-left corner out, to the left and down.
fw() { dump; python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("content-desc", "").startswith("Floating tab: "):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds"))); print(x2 - x1); break
else:
    print(0)
PY
}
w0=$(fw)
if xy=$(find_xy "Resize from the left corner"); then
  set -- $xy
  $A shell input swipe $1 $2 $(($1 - 60)) $(($2 + 30)) 600; sleep 2; shot r32b_resized
fi
log "resized from its corner: width $w0 -> $(fw) (expect wider)"
if xy=$(find_xy "Move the floating tab"); then
  set -- $xy
  $A shell input swipe $1 $2 $((W - 2)) $2 800; sleep 3; shot r33_parked
  log "pushed against the edge: $(has "^Floating tab parked") (expect yes)"
fi
tap "^Floating tab parked"; sleep 4; shot r34_opened_again
log "opened again: $(has "^Floating tab: ") (expect yes) | its sound: $(has "state: paused") (expect yes: paused while it was an icon)"
# The buttons show for a few seconds: tap Full size straight away (the screenshot waits until after).
tap "Move the floating tab"; sleep 1; tap "=Full size"; sleep 4; shot r36_full_size
log "full size: $(addr) (expect the media test page) | still floating: $(has "^Floating tab: ") (expect no)"
# Nothing floating over the split screen test (in run 22 its taps landed on a floating window left open).
if [ "$(has "^Floating tab: ")" = yes ]; then tap "Move the floating tab"; sleep 1; tap "=Close the floating tab"; sleep 2; fi

# 10. Split screen: the second tab starting to play pauses the first (one tab at a time); split, both play at once;
# the speaker picks whose sound you hear (the other keeps playing, muted); End split ends it.
newtab; sleep 3; go "$PAGES/media.html?first"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
tap "=Play"; sleep 3
newtab; sleep 3; go "$PAGES/media.html?second"; sleep 6
try_tap "Open the unsecure site anyway" && sleep 5
tap "=Play"; sleep 4
menu "=Split screen"; sleep 2; shot r37_split_pick
tap "Split with Raven media test first"; sleep 6; shot r38_split
log "split: top $(has "^Top half: ") bottom $(has "^Bottom half: ") (expect yes yes)"
log "one at a time: the first tab after the second started: $(count "state: paused") paused, $(count "state: playing") playing (expect 1 and 1)"
tap "!=Play"; sleep 4; shot r39_both_playing
log "both halves play at once: $(count "state: playing") playing (expect 2)"
tap "^Sound: on"; sleep 2; shot r40_sound_choice
tap "=Top only"; sleep 3; shot r41_top_only
log "top only: $(count "state: playing (muted)") muted and still playing (expect 1) | a half's speaker shows muted: $(has "^Sound: muted") (expect yes)"
menu "End split screen"; sleep 4; shot r42_split_ended
log "split ended: $(has "^Top half: ") (expect no)"

# 11. The home page: a new greeting each time Raven opens; Continue is a tab that's still open (gone once that tab
# is closed); the Tabs screen's Close all only closes tabs (Clean slate, which erases, is only in the menu).
greeting() { dump; python3 - "$OUT/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    t = n.get("text", "")
    if "\n" in t:
        print(t.replace("\n", " ")); break
else:
    print("-")
PY
}
newtab; sleep 3; $A shell log -t RavenTest typing-example; go "https://example.com/"; sleep 8
# Typing it, Raven had already started connecting to the site before Go was pressed.
log "connected ahead while typing: $($A logcat -d | sed -n '/RavenTest: typing-example/,$p' | grep -c "warm up: example.com") time(s) (expect 1 or more)"
newtab; sleep 3; shot r43_home_continue
log "continue: $(has "=Example Domain") (expect yes: its tab is open)"
g1="$(greeting)"
$A shell input keyevent 3; sleep 3; front; sleep 3
g2="$(greeting)"
$A shell input keyevent 3; sleep 3; front; sleep 3
g3="$(greeting)"
log "greetings: '$g1' / '$g2' / '$g3' (expect three different ones)"
tap "open tabs"; sleep 3; shot r44_tabs_buttons
log "tabs screen: Close all $(has "=Close all") (expect yes) | Clean slate $(has "^Clean slate") (expect no: it's in the menu)"
# Its card's close button shows when it's the tab in front: open it, then close it from the Tabs screen.
tap "^Example Domain"; sleep 3; tap "open tabs"; sleep 3
tap "=Close tab: Example Domain"; sleep 3
tap "^Tabs · " && sleep 1; tap "!=New tab"; sleep 3; shot r45_home_after_closing
log "continue after closing that tab: $(has "=Example Domain") (expect no)"

log "new crashes: $(( $(fatals) - f0 ))"
