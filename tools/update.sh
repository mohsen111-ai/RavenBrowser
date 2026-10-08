#!/bin/bash
# The update after 1.0.36, part by part: split screen's own bars, video fullscreen from a half and from the floating
# tab, Undo for closed tabs, cookie popups, full screen for pages, profiles, the lock for all of Raven, the backup
# file, VPN per site, the live wallpapers and the new Settings.
set -u
source tools/lib.sh
log "---- update"
front; sleep 3
python3 tools/pages/serve.py & SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
PAGES=http://10.0.2.2:8000
fatals() { $A logcat -d | grep -c "FATAL EXCEPTION"; }
f0=$(fatals)
has() { dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && echo yes || echo no; }
# How many elements on screen have a label starting with $1.
many() { dump; python3 - "$OUT/ui.xml" "$1" <<'EOF'
import sys, xml.etree.ElementTree as ET
print(sum(1 for n in ET.parse(sys.argv[1]).iter("node") if n.get("content-desc", "").startswith(sys.argv[2]) or n.get("text", "").startswith(sys.argv[2])))
EOF
}
# The first page text starting with $1 (page text can be a node's text or its description), or "-".
text() { dump; python3 - "$OUT/ui.xml" "$1" <<'EOF'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    for t in (n.get("text", ""), n.get("content-desc", "")):
        if t.startswith(sys.argv[2]):
            print(t); sys.exit()
print("-")
EOF
}
open_page() { go "$1"; sleep 6; try_tap "Open the unsecure site anyway" && sleep 5; }
# A video plays and changes the screen all the time; uiautomator only reads a still screen.
pause() { $A shell cmd media_session dispatch pause; sleep 2; }
dims() { python3 -c "import struct,sys; print('%dx%d' % struct.unpack('>II', open(sys.argv[1],'rb').read(24)[16:24]))" "$OUT/$1.png"; }
since() { $A logcat -d | sed -n "/RavenTest: $1/,\$p"; }
mark() { $A shell log -t RavenTest "$1"; }

# 1. Undo: a closed tab comes back with Undo, as it was; so do all of them after Close all.
newtab; sleep 3; open_page example.com
newtab; sleep 3; open_page example.org
tap "open tabs"; sleep 3; tap "^Tabs · "; sleep 1
log "tabs before closing: $(text "Tabs · ")"
tap "=Close tab: Example Domain"; sleep 1
# The Undo bar stays a few seconds: look for it first, then take the picture.
dump; xy=$(python3 tools/find.py "$OUT/ui.xml" "=Undo") && offered=yes || offered=no
[ $offered = yes ] && $A shell input tap $xy; sleep 3; shot u02_undone
log "after closing one: Undo offered: $offered (expect yes)"
log "after Undo: $(text "Tabs · ") (expect the count before) | Example Domain back: $(has "^Example Domain") (expect yes)"
tap "=Close all"; sleep 2; tap "!=Close all"; sleep 1
dump; closed=$(python3 tools/find.py "$OUT/ui.xml" "tabs closed" > /dev/null && echo yes || echo no)
xy=$(python3 tools/find.py "$OUT/ui.xml" "=Undo") && $A shell input tap $xy; sleep 3; shot u04_all_back
log "after Close all: 'N tabs closed' bar shown: $closed (expect yes)"
log "after Undo: $(text "Tabs · ") (expect the count before)"
back

# 2. Split screen: each half has its own bar. The bottom half's address bar takes it to another page, and its reload
# works; scrolling down a half's page tucks its bar away, scrolling up brings it back.
newtab; sleep 3; open_page "$PAGES/long.html"
newtab; sleep 3; open_page example.com
menu "=Split screen"; sleep 2; tap "Split with Raven long page"; sleep 6; shot u05_split_bars
log "split: bars $(many "Address ") (expect 2: one per half) | top $(text "Top half: ") | bottom $(text "Bottom half: ")"
if xy=$(find_xy "!^Address "); then
  set -- $xy; $A shell input tap $1 $2; sleep 2; shot u06_bottom_editing
  $A shell input text "example.org"; sleep 1; $A shell input keyevent 66; sleep 8; shot u07_bottom_moved
  log "bottom half after typing in its bar: $(text "Bottom half: ") (expect Example Domain, at example.org) | top: $(text "Top half: ") (expect Example Domain)"
  log "bottom half's address: $(text "Address example.org") (expect it)"
  $A shell input keyevent 4; sleep 6
fi
log "bottom half back on the long page: $(text "Bottom half: ") (expect Raven long page)"
tap "!=Reload"; sleep 4; log "bottom half reloaded: $(text "Bottom half: ") (expect Raven long page)"
# Scroll the bottom half's page down, then up.
$A shell input swipe $((W / 2)) $((H * 88 / 100)) $((W / 2)) $((H * 60 / 100)) 300; sleep 2; shot u08_scrolled_down
log "after scrolling the bottom half down: bars $(many "Address ") (expect 1: its bar stepped aside)"
$A shell input swipe $((W / 2)) $((H * 60 / 100)) $((W / 2)) $((H * 88 / 100)) 300; sleep 2; shot u09_scrolled_up
log "after scrolling it up: bars $(many "Address ") (expect 2)"
menu "End split screen"; sleep 3

# 3. Video fullscreen in a split half, with two videos playing: the video fills only its own half. The other half stays
# on screen (and keeps playing), the phone's bars stay, the screen doesn't turn; Back puts the half as it was.
newtab; sleep 3; open_page "$PAGES/video.html?other&once"
newtab; sleep 3; open_page "$PAGES/video.html?once"
menu "=Split screen"; sleep 2; tap "Split with Raven video test"; sleep 6
tap "=Play"; sleep 3; tap "!=Play"; sleep 3
mark split-fullscreen; tap "=Fullscreen"; sleep 5; shot u10_split_fullscreen
pause
log "split half fullscreen: screen $(dims u10_split_fullscreen) (expect taller than wide) | halves $(has "^Top half: ") $(has "^Bottom half: ") (expect yes yes: the other half is still there) | bars $(many "Address ") (expect 1: the full half's bar stepped aside) | page: $(text "state: ") (expect fullscreen: yes)"
log "raven: $(since split-fullscreen | grep -m1 -o 'fullscreen: [0-9a-f]* *(*[a-z ]*)*') (expect none: only the whole-phone kind is logged)"
$A shell input keyevent 4; sleep 4; shot u11_split_back
log "after Back: halves $(has "^Top half: ") $(has "^Bottom half: ") (expect yes yes) | bars $(many "Address ") (expect 2) | page: $(text "state: ") (expect fullscreen: no)"
# Both videos play once and stop by themselves (a playing video keeps the screen from settling for the test tool).
sleep 25
menu "End split screen"; sleep 3

# 4. Video fullscreen from the floating tab: the whole phone screen, then back into its window.
newtab; sleep 3; open_page "$PAGES/video.html"
tap "=Play"; sleep 2; pause
menu "Float this tab"; sleep 4; shot u12_floating_video
mark float-fullscreen; tap "!=Fullscreen"; sleep 5; shot u13_float_fullscreen
pause
log "floating tab fullscreen: screen $(dims u13_float_fullscreen) (expect wider than tall) | page: $(text "state: ") (expect fullscreen: yes)"
log "raven: $(since float-fullscreen | grep -m1 -o 'fullscreen: [0-9a-f]* *(*[a-z ]*)*') (expect '(floating tab)')"
log "fullscreen kept after 3 seconds: $(sleep 3; text "state: ") (expect fullscreen: yes: before, it left fullscreen at once)"
$A shell input keyevent 4; sleep 4; shot u14_float_back
log "after Back: in its window again $(has "^Floating tab: ") (expect yes)"
if [ "$(has "^Floating tab: ")" = yes ]; then tap "Move the floating tab"; sleep 1; tap "=Close the floating tab"; sleep 2; fi

# 5. Cookie popups: Raven presses the site's own "Reject All".
newtab; sleep 3; mark cookies; open_page "$PAGES/cookies.html"; sleep 4; shot u15_cookie_popup
log "cookie popup: $(text "state: ") (expect state: rejected) | raven: $(since cookies | grep -m1 -o 'cookie popup: .*')"

# 6. Full screen for pages: no bars; a swipe down from the top brings the bar back for a moment.
open_page example.com
menu "Full screen for pages"; sleep 3; shot u16_full_screen
log "full screen: address bar $(has "^Address ") (expect no)"
$A shell input swipe $((W / 2)) 4 $((W / 2)) $((H / 3)) 300; sleep 1; shot u17_bars_peek
log "after a swipe down from the top: address bar $(has "^Address ") (expect yes)"
sleep 6; log "a few seconds later: address bar $(has "^Address ") (expect no)"
$A shell input swipe $((W / 2)) 4 $((W / 2)) $((H / 3)) 300; sleep 1
menu "Full screen for pages"; sleep 3; shot u18_full_screen_off
log "full screen off: address bar $(has "^Address ") (expect yes)"

# 7. Profiles: a second person whose cookies are their own.
newtab; sleep 3; open_page "$PAGES/jar.html?set=personal"
log "personal tab: $(text "jar: ") (expect who=personal)"
setting "profile" "Profiles"; shot u19_profiles_page
tap "=Add a profile"; sleep 2; tap "=Name"; sleep 1; $A shell input text "Work"; sleep 1; tap "=Add"; sleep 2; shot u20_profile_added
log "profiles: Work added $(has "=Work") (expect yes)"
leave_settings
tap "open tabs"; sleep 3; shot u21_tabs_profiles
log "tabs screen: profile chips $(has "^Profile Personal") $(has "^Profile Work") (expect yes yes)"
tap "^Profile Work"; sleep 2; tap "!=New tab"; sleep 3
open_page "$PAGES/jar.html"; shot u22_work_jar
log "work tab: $(text "jar: ") (expect empty: a profile keeps its own cookies)"
# A long-pressed link offers the other profile.
if xy=$(find_xy "=Look in the jar"); then
  set -- $xy; hold_at $1 $2; sleep 3; shot u23_link_other_profile
  log "long press: Open in Personal offered $(has "=Open in Personal") (expect yes)"
  $A shell input keyevent 4; sleep 2; front
fi
tap "open tabs"; sleep 3; tap "^Profile Personal"; sleep 2; tap "=Done"; sleep 3
log "back in Personal: $(text "jar: ") (expect who=personal)"

# 8. The lock for all of Raven, with the emulator's PIN (a fingerprint is for the phone).
$A shell locksettings set-pin 1234 > /dev/null 2>&1
pin() { sleep 3; $A shell input text 1234; sleep 1; $A shell input keyevent 66; sleep 3; }
focus() { $A shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*{[^ ]* [^ ]* //; s/}//'; }
setting "lock" "Lock"; shot u24_lock_page
# The switch is the last thing called "Lock Raven" (its title comes first).
tap "!=Lock Raven"; sleep 3; shot u25_asks_pin; pin; shot u25b_lock_on
log "lock on: $(has "=Immediately") (expect yes: 'Lock after' shows once it's on)"
tap "=Immediately"; sleep 1; leave_settings
$A shell input keyevent 3; sleep 3; front; sleep 2; shot u26_locked
log "after leaving and coming back: in front $(focus) (expect the PIN screen over Raven's lock) | lock screen $(has "^Locked for the night") (expect yes, or the PIN screen over it)"
pin; shot u27_unlocked
log "after the PIN: locked $(has "^Locked for the night") (expect no) | address bar $(has "^Address ") (expect yes)"
setting "lock" "Lock"; tap "!=Lock Raven"; pin; leave_settings
$A shell locksettings clear --old 1234 > /dev/null 2>&1

# 9. The backup file: saved with a password, then put back (Raven restarts).
tap "open tabs"; sleep 3; before="$(text "Tabs · ")"; back
setting "backup" "Backup and restore"; shot u28_backup_page
tap "^Back up"; sleep 2
tap "Password (8 or more"; $A shell input text "night-owl-42"; sleep 1
tap "The same again"; $A shell input text "night-owl-42"; sleep 1; shot u29_backup_password
tap "=Choose where to save"; sleep 4; shot u30_save_picker
try_tap "=SAVE" || try_tap "=Save"; sleep 6; shot u31_backup_saved
log "backup file: $($A shell ls /sdcard/Download/ 2>/dev/null | grep -c "Raven backup") in Downloads (expect 1)"
tap "^Restore from a backup"; sleep 4
try_tap "Show roots" && sleep 2; try_tap "=Downloads" && sleep 3; try_tap "=List view" && sleep 2
tap "^Raven backup"; sleep 3
tap "=Password"; $A shell input text "night-owl-42"; sleep 1; tap "=Open"; sleep 6; shot u32_restore_question
log "restore: $(has "=Restore this backup?") (expect yes) | $(text "Made on")"
tap "=Restore and restart"; sleep 30; front; sleep 5; shot u33_after_restore
tap "open tabs"; sleep 3; log "tabs after restoring: $(text "Tabs · ") (expect as before: $before)"; back

# 10. VPN per site (with a made-up location: the emulator can't reach Proton, but the tunnel still comes up).
key() { head -c 32 /dev/urandom | base64; }
printf '[Interface]\nPrivateKey = %s\nAddress = 10.2.0.2/32\nDNS = 10.2.0.1\n\n[Peer]\nPublicKey = %s\nAllowedIPs = 0.0.0.0/0, ::/0\nEndpoint = 10.0.2.2:51820\n' \
  "$(key)" "$(key)" > /tmp/raven-NL-1.conf
# Kept out of the results folder: even a made-up location file isn't published with the screenshots.
$A push /tmp/raven-NL-1.conf /sdcard/Download/raven-NL-1.conf > /dev/null; rm -f /tmp/raven-NL-1.conf
newtab; sleep 3; open_page example.com
menu "VPN:"; sleep 2
tap "Add your first location"; sleep 4
try_tap "Show roots" && sleep 2; try_tap "=Downloads" && sleep 3; try_tap "=List view" && sleep 2
tap "raven-NL-1"; sleep 4; shot u34_vpn_sheet
mark vpn-site
tap "^This site: example.com"; sleep 2; shot u35_site_choice
tap "!Netherlands"; sleep 3; try_tap "=OK" && sleep 5; shot u36_site_country
log "site's own country: $(text "Netherlands") | raven: $(since vpn-site | grep -m1 -o 'vpn: .*') (expect on for a site with its own country)"
for i in 1 2; do $A shell input keyevent 4; sleep 2; done; front
mark vpn-other; open_page example.org; sleep 4
log "another site: raven: $(since vpn-other | grep -m1 -o 'vpn: .*') (expect off for the switch)"

# 11. A live wallpaper moves on the home screen: two screenshots a moment apart differ.
setting "wallpaper" "Wallpapers"
seek() { for i in 1 2 3 4 5 6 7 8; do dump; python3 tools/find.py "$OUT/ui.xml" "$1" > /dev/null && return 0; $A shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 35 / 100)) 400; sleep 1; done; log "NOT FOUND after scrolling: $1"; return 1; }
seek "New wallpaper each time"; tap "!=New wallpaper each time"; sleep 1
seek "=Campfire"; tap "=Campfire"; sleep 2; shot u37_campfire_chosen
leave_settings; newtab; sleep 5
$A exec-out screencap -p > $OUT/u38_live_a.png; sleep 1; $A exec-out screencap -p > $OUT/u38_live_b.png
# The middle of the screen only (the status bar's clock changes too).
moved=$(python3 tools/differ.py $OUT/u38_live_a.png $OUT/u38_live_b.png)
log "live wallpaper moving: $moved (expect yes)"
setting "wallpaper" "Wallpapers"; seek "New wallpaper each time"; tap "!=New wallpaper each time"; sleep 1; leave_settings

# 12. The new Settings: the short main page, its search box, and some of its pages.
menu "Settings"; sleep 3; shot u39_settings_main
$A shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 25 / 100)) 400; sleep 2; shot u40_settings_main_more
tap "Search settings"; sleep 1; $A shell input text "cookie"; sleep 2; $A shell input keyevent 4; sleep 1; shot u41_settings_search
tap "^Cookie popups"; sleep 2; shot u42_cookie_page; $A shell input keyevent 4; sleep 2
leave_settings
for q in "permissions:Site permissions" "links:Open links in apps" "look:Look" "about:About Raven"; do
  if setting "${q%%:*}" "${q#*:}"; then shot "u43_page_${q%%:*}"; leave_settings; else log "settings page not found: ${q#*:}"; fi
done

# 5. Downloads: tapping the "Downloading ..." pill opens the Downloads page; tapping a finished file opens it, or (when
# no app can) shows the Downloads folder in the file manager instead of doing nothing.
newtab; sleep 3
tap "Address"; sleep 2; $A shell input text "'$PAGES/sample.bin'"; sleep 1; $A shell input keyevent 66
dump; python3 tools/find.py "$OUT/ui.xml" "^Downloading " > /dev/null && { xy=$(python3 tools/find.py "$OUT/ui.xml" "^Downloading "); $A shell input tap $xy; log "tapped the Downloading pill at $xy"; } || log "the Downloading pill was already gone"
sleep 3; shot u44_downloads_from_pill
log "tapping the Downloading pill: Downloads page $(has "=Downloads") $(has "sample.bin") (expect yes yes)"
tap "^sample.bin"; sleep 4; shot u45_opened_file
log "tapping the finished file: front app $($A shell dumpsys window | grep -m1 mCurrentFocus | grep -c "$APP") (expect 0: another app, the file manager, came to the front)"
$A shell input keyevent 4; sleep 2; front; sleep 2

log "new crashes: $(( $(fatals) - f0 ))"
$A logcat -d > $OUT/logcat-update.txt
crashes $OUT/logcat-update.txt > $OUT/crashes-update.txt
log "---- update done"
