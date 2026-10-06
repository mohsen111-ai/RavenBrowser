#!/bin/bash
# Drives Raven on the emulator like a person would, taking a screenshot after each step.
set -u
source tools/lib.sh

$A install -r raven-x86_64.apk | tee -a $OUT/steps.txt
$A logcat -c
$A shell am start -n $MAIN
sleep 25
# Every step after this needs Raven in front; say so loudly if it isn't (smoke-1 ran an hour on the home screen).
focus=$($A shell dumpsys window | grep -m1 mCurrentFocus)
case "$focus" in *$APP*) log "Raven is in front";; *) log "RAVEN DID NOT OPEN: $focus";; esac
shot 01_welcome
# One welcome page: "Not now" leaves the default-browser question for later.
tap "Not now"; sleep 5; shot 03_new_tab
sleep 30; shot 04_new_tab_after_shield_install
go "example.com"; sleep 15; shot 05_example_com
menu "Shield"; sleep 12; shot 06_shield_panel; back
go "https://www.bbc.com/news"; sleep 25; shot 07_bbc
menu "Shield"; sleep 12; shot 08_shield_panel_bbc; back
tap "Menu"; sleep 3; shot 09_menu; back
tap "open tabs"; sleep 4; shot 10_tabs; back
privatetab; sleep 4; shot 12_private_new_tab
go "duckduckgo.com"; sleep 15; shot 13_private_page
# The Tabs screen opens on the private side for a private tab; the everyday side holds the rest.
tap "open tabs"; sleep 4; shot 14_tabs_private_side
tap "^Tabs · "; sleep 2; shot 14b_tabs_everyday_side
tap "^Private · "; sleep 2; back
go "http://http.badssl.com/"; sleep 20; shot 15_https_only
go "https://proof.ovh.net/files/100Mb.dat"; sleep 6; shot 16_download_started
tap "=Allow"; sleep 3; shot 16b_after_permission
menu "Downloads"; sleep 3; shot 17_downloads; sleep 20; shot 18_downloads_later; back
# The file put together from pieces must match the server's file byte for byte.
sleep 20
$A shell ls -l /sdcard/Download/ > $OUT/downloads-ls.txt 2>&1
$A shell md5sum /sdcard/Download/100Mb.dat > $OUT/download-md5.txt 2>&1
curl -s --retry 3 https://proof.ovh.net/files/100Mb.dat | md5sum > $OUT/download-md5-expected.txt
log "download md5: $(cut -d' ' -f1 $OUT/download-md5.txt) expected: $(cut -d' ' -f1 $OUT/download-md5-expected.txt)"
menu "Find in page"; sleep 2; $A shell input text "the"; sleep 2; shot 19_find; $A shell input keyevent 4; sleep 1; back
menu "Settings"; sleep 3; shot 20_settings
$A shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 25 / 100)) 400; sleep 2; shot 21_settings_more; back
menu "Add-ons"; sleep 3; shot 22_addons; back
menu "History"; sleep 3; shot 23_history; back
# The launcher icon, from the home screen's app list.
$A shell input keyevent 3; sleep 2; $A shell input swipe $((W / 2)) $((H * 90 / 100)) $((W / 2)) $((H * 20 / 100)) 300; sleep 3; shot 24_app_list
$A shell dumpsys meminfo $APP > $OUT/meminfo.txt 2>&1
$A shell ps -A -o RSS,NAME | grep -i raven > $OUT/processes.txt 2>&1
$A shell run-as $APP cat files/last-crash.txt > $OUT/last-crash.txt 2>&1
$A logcat -d > $OUT/logcat.txt
crashes $OUT/logcat.txt > $OUT/crashes.txt
grep -E "GeckoConsole.*(Error|TypeError)|Raven" $OUT/logcat.txt | grep -v "lookupManagedStorage" > $OUT/engine-errors.txt || true
log "done"
