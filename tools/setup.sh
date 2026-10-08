#!/bin/bash
# Gets a fresh emulator ready for any test script but smoke.sh (which checks these first steps itself): installs
# Raven, gets past the welcome page, gives Shield time to install, and allows notifications up front (on one
# emulator smoke.sh used to do that for all the scripts after it; now each group of scripts has its own emulator).
set -u
source tools/lib.sh
log "---- setup"
$A install -r raven-x86_64.apk | tee -a $OUT/steps.txt
$A shell pm grant $APP android.permission.POST_NOTIFICATIONS > /dev/null 2>&1
$A logcat -c
$A shell am start -n $MAIN > /dev/null
sleep 25
tap "Not now"; sleep 30
focus=$($A shell dumpsys window | grep -m1 mCurrentFocus)
case "$focus" in *$APP*) log "Raven is in front";; *) log "RAVEN DID NOT OPEN: $focus";; esac
shot 00_setup
log "---- setup done"
