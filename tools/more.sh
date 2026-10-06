#!/bin/bash
# Third pass, after smoke.sh and deep.sh: everyday things not covered yet, then memory with many tabs.
set -u
source tools/lib.sh
log "---- more"
# Android's own reports so far, kept before the heavy sites (which have stopped Android itself on this emulator, and
# with it the reports at the end).
bash tools/final.sh before-more
front; sleep 3

# Search from the address bar: words go to DuckDuckGo, and the bar shows the words, not the long address.
newtab; sleep 3
go "raven%sbird%sfacts"; sleep 15; shot m01_search_results

# Save as PDF lands in Downloads.
go "example.com"; sleep 10
menu "Save as PDF"; sleep 8; shot m02_saved_pdf
menu "Downloads"; sleep 3; shot m03_downloads_with_pdf; back

# A site asks for the location: the permission sheet appears and can be refused.
go "https://permission.site/"; sleep 12; shot m04_permission_site
tap "Location" ; sleep 4; shot m05_location_prompt
tap "=Block"; sleep 3; shot m06_after_block

# A big download: its notification opens the Downloads screen, which shows the time left; then pause and cancel.
go "https://proof.ovh.net/files/1Gb.dat"; sleep 4
$A shell cmd statusbar expand-notifications; sleep 2; shot m07_download_notification
# uiautomator can't always see the shade on Android 16: then send the very intent the notification holds.
try_tap "1Gb.dat" || { $A shell cmd statusbar collapse; $A shell am start -n $MAIN -f 0x20000000 --ez show_downloads true > /dev/null; log "notification intent sent"; }
sleep 3; shot m07b_opened_from_notification
sleep 2; shot m07c_time_left
tap "Pause"; sleep 3; shot m07d_paused
tap "Cancel"; sleep 2; back

# Memory: the phone test's sites, each in its own tab. github.com and YouTube are left out on purpose:
# the emulator has no GPU, and drawing GitHub's WebGL globe in software took its page to 1.7 GB within
# 20 seconds and froze the whole emulator (smoke-20). They load fine on phones (the phone test included both).
# Free memory and Raven's processes are written down every 10 seconds meanwhile.
( for i in $(seq 1 60); do
    echo "--- $(date +%T) $(timeout 20 adb shell grep -E 'MemAvailable' /proc/meminfo 2>&1) procs=$(timeout 20 adb shell ps -A -o NAME 2>/dev/null | grep -c raven)" >> $OUT/m_memory_timeline.txt
    sleep 10
  done ) &
MEMLOG=$!
for url in "https://en.m.wikipedia.org/wiki/Android_(operating_system)" "https://www.reddit.com/" \
           "https://www.bbc.com/news" "https://edition.cnn.com/" "https://stackoverflow.com/questions" "https://www.justwatch.com/"; do
  newtab; sleep 2
  go "$url"; sleep 12
done
shot m08_after_6_sites
tap "open tabs"; sleep 3; shot m09_tabs_6_sites; back
$A shell ps -A -o RSS,NAME | grep -i raven > $OUT/m_processes_6_sites.txt 2>&1
for p in $($A shell ps -A -o NAME | grep -i raven); do
  echo "$p $($A shell dumpsys meminfo $p | grep -E 'TOTAL PSS' | head -1)" >> $OUT/m_pss_6_sites.txt
done
log "processes: $(wc -l < $OUT/m_processes_6_sites.txt)"

kill $MEMLOG 2>/dev/null
timeout 300 adb logcat -d > $OUT/logcat-more.txt
crashes $OUT/logcat-more.txt > $OUT/crashes-more.txt
log "more done"
