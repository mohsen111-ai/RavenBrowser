#!/bin/bash
# Runs after smoke.sh on the same emulator: the things people do every day, beyond the first look.
set -u
source tools/lib.sh
log "---- deep test"
front; sleep 3

# Long-press a link, open it in a new tab, then follow it and come back with Back.
# A page that is one big link, so the press lands on it whatever the screen size.
newtab; sleep 3
go "data:text/html,<a%shref=https://example.com/%sstyle=font-size:160px>Hold%sme</a>"; sleep 6; shot d01_link_page
hold_at 100 150; sleep 3; shot d02_long_press
tap "Open in new tab"; sleep 3; shot d03_opened_in_new_tab
$A shell input tap 100 150; sleep 10; shot d04_followed_link
$A shell input keyevent 4; sleep 5; shot d05_back_to_link_page

# Address bar like Chrome: an empty field with the page's address underneath; the pencil puts it in the field.
go "example.com"; sleep 10
tap "Address"; sleep 2; shot d05b_address_editing
tap "Edit link"; sleep 2; shot d05c_address_in_field
$A shell input keyevent 4; sleep 1; $A shell input keyevent 4; sleep 2

# A double tap on page text must not bring up Copy / Select All; a long press still does.
# A page that is all big words at phone width, so both land on a word whatever the layout.
go "data:text/html,<meta%sname=viewport%scontent=width=device-width><p%sstyle=font-size:44px;line-height:1>$(printf 'Raven%%sflies%%s%.0s' $(seq 1 24))</p>"; sleep 6
# uiautomator can't see the floating Copy toolbar, so read Raven's log: Gecko's "show the toolbar" requests,
# Raven noticing a double tap, and Raven dropping the selection that followed it.
shows() { $A logcat -d | grep -c "ShowSelectionAction"; }
s0=$(shows)
# Each "input tap" starts its own small program, so two started together land in either order or overlap.
# Starting the second 0.15 s after the first gives two clean taps about that far apart: a double tap.
for i in 1 2 3; do $A shell "input tap 160 330 & sleep 0.15; input tap 160 330; wait"; sleep 2; $A shell input tap 300 600; sleep 1; done
shot d05d_after_double_tap
s1=$(shows)
log "double tap x3: seen as double tap $($A logcat -d | grep -cE 'Raven *: double tap') time(s), engine asked for the toolbar $((s1 - s0)) time(s), Raven dropped (expect the same number) $($A logcat -d | grep -c 'selection after a double tap dropped')"
hold_at 160 330; sleep 3; shot d05e_after_long_press
log "long press: toolbar requests $(( $(shows) - s1 )) (expect 1 or more; the screenshot shows the toolbar)"
$A shell input tap 300 600; sleep 1

# Desktop site on and off.
front
menu "Desktop site"; sleep 8; shot d06_desktop_on
menu "Desktop site"; sleep 6; shot d07_desktop_off

# Landscape.
front
$A shell settings put system accelerometer_rotation 0
$A shell settings put system user_rotation 1; sleep 5; shot d08_landscape
tap "open tabs"; sleep 3; shot d09_tabs_landscape; back
$A shell settings put system user_rotation 0; sleep 4

# Tabs come back after the app is stopped.
$A shell am force-stop $APP; sleep 3; front; sleep 15; shot d10_after_restart
tap "open tabs"; sleep 3; shot d11_tabs_after_restart; back

# A setting that needs a restart: site isolation for every site, then Restart.
setting "isolation" "Site isolation"; tap "Every site"; sleep 2; shot d12_restart_banner
tap "=Restart"; sleep 25; shot d13_after_settings_restart
$A shell ps -A -o RSS,NAME | grep -i raven > $OUT/d_processes_all_sites.txt 2>&1
setting "isolation" "Site isolation"; tap "Sites you log into"; sleep 2; tap "=Restart"; sleep 25

# Another add-on from addons.mozilla.org.
go "https://addons.mozilla.org/android/addon/darkreader/"; sleep 20; shot d14_amo_page
# The button sits at the bottom edge; scroll it into view first so the tap lands on it.
$A shell input swipe 160 450 160 250 400; sleep 3
tap "Add to Firefox" || tap "Add to"; sleep 10; shot d15_install_sheet
tap "=Add"; sleep 12; shot d16_after_install
menu "Add-ons"; sleep 3; shot d17_addons_list; back
menu "Dark Reader"; sleep 8; shot d17b_dark_reader_panel; back

# "Dark websites" is on by default: MDN follows the dark preference.
go "https://developer.mozilla.org/en-US/"; sleep 15; shot d21_dark_website

# Clean slate (in the menu): close every tab and erase, then history is empty.
menu "=Clean slate"; sleep 2; shot d18_clean_slate_sheet
tap "Erase everything"; sleep 8; shot d19_after_clean_slate
menu "History"; sleep 3; shot d20_history_after_clean_slate; back

$A logcat -d > $OUT/logcat-deep.txt
crashes $OUT/logcat-deep.txt > $OUT/crashes-deep.txt
log "deep done"
