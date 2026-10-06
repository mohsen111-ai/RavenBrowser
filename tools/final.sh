#!/bin/bash
# Runs after all the scripts: Android's own reports of any crash or "isn't responding" (ANR) in this run,
# with what every thread was doing at that moment, so a hang can be traced to its cause.
set -u
source tools/lib.sh
# An optional name ($1) keeps an earlier collection apart from the last one (more.sh saves them before its heavy sites,
# which have stopped Android itself on this emulator).
S=${1:+-$1}
$A shell dumpsys dropbox --print data_app_anr > $OUT/anr-reports$S.txt 2>&1
$A shell dumpsys dropbox --print data_app_crash > $OUT/crash-reports$S.txt 2>&1
$A shell dumpsys dropbox --print data_app_native_crash > $OUT/native-crash-reports$S.txt 2>&1
log "reports${1:+ ($1)}: ANR $(grep -c '^Process: ' $OUT/anr-reports$S.txt), crash $(grep -c '^Process: ' $OUT/crash-reports$S.txt), native crash $(grep -c '^Process: ' $OUT/native-crash-reports$S.txt) (all apps)"
