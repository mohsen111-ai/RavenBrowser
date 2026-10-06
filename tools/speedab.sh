#!/bin/bash
# Why Raven is slower than Kepler on bad internet for a site's first visit (speed run 5: 4.1 s against 2.7 s): Raven
# against two copies of itself, one without its page helper and one without the page-language check, on the same
# slow line, each site's first visit. Whichever copy is faster shows what costs the time.
set -u
source tools/lib.sh
APPS="app.raven.browser app.raven.browser.nohelper app.raven.browser.notranslate"
ACT=app.raven.browser.MainActivity
name() { case "$1" in *.nohelper) echo NoHelper;; *.notranslate) echo NoTranslate;; *) echo Raven;; esac; }
log "---- speed A/B: Raven, without its helper, without the language check"
for f in raven-x86_64.apk raven-nohelper-x86_64.apk raven-notranslate-x86_64.apk; do $A install -r $f | tee -a $OUT/steps.txt; done

# First launch of each: past the welcome, then time for Shield (uBlock Origin) to install and fetch its lists.
for pkg in $APPS; do
  $A shell am start -n $pkg/$ACT; sleep 25
  try_tap "Not now"; sleep 5; try_tap "=Allow"; sleep 2; shot "a01_$(name $pkg)_home"
done
sleep 60

Q="timeout 20 adb"
android_ok() { $Q shell service check activity 2>/dev/null | grep -q ": found"; }
recover() {
  android_ok && return 0
  log "Android's core stopped answering: restarting the emulator" >&2
  timeout 60 adb reboot > /dev/null 2>&1
  timeout 300 adb wait-for-device
  for i in $(seq 1 60); do [ "$($Q shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 5; done
  sleep 30
  log "emulator back: $(android_ok && echo yes || echo no)" >&2
}
# Prints "<until it starts> <until it finished>" for $2 opened in app $1, or "- timeout"; the other copies are closed
# first, so they never compete for the phone's memory.
load() {
  local pkg=$1 url=$2 host pid i r p
  recover
  host=$(python3 -c "import sys,urllib.parse as u; h=u.urlparse(sys.argv[1]).hostname; print(h[4:] if h.startswith('www.') else h[2:] if h.startswith('m.') else h)" "$url")
  for p in $APPS; do [ "$p" = "$pkg" ] || $Q shell am force-stop $p > /dev/null 2>&1; done
  $Q shell am start -n $pkg/$ACT > /dev/null 2>&1; sleep 30
  pid=$($Q shell pidof $pkg | tr -d '\r' | awk '{print $1}')
  $Q logcat -c
  $Q shell log -t RavenSpeed go
  $Q shell am start -a android.intent.action.VIEW -d "'$url'" -n $pkg/$ACT > /dev/null 2>&1
  for i in $(seq 1 45); do
    sleep 2
    if r=$($Q logcat -d -v epoch | python3 tools/speedparse.py "$pid" "$host"); then echo "$r"; return; fi
  done
  echo "- timeout"
}

SITES="https://en.m.wikipedia.org/wiki/Crow https://www.theguardian.com/international https://stackoverflow.com/questions https://www.imdb.com/ https://www.aljazeera.com/ https://www.npr.org/ https://www.bbc.com/sport https://www.mozilla.org/en-US/"
# Bad internet, as in the Kepler comparison: 3G speed with the slow, uneven replies of a weak signal.
log "slow line: $($A emu network speed umts 2>&1 | tr -d '\r' | tr '\n' ' ') $($A emu network delay edge 2>&1 | tr -d '\r' | tr '\n' ' ')"
set -- $APPS
i=0
for url in $SITES; do
  # Each copy goes first in turn.
  case $((i % 3)) in 0) order="$1 $2 $3";; 1) order="$2 $3 $1";; *) order="$3 $1 $2";; esac
  for pkg in $order; do
    r=$(load $pkg "$url")
    echo "slow-first $(name $pkg) $url $r" >> $OUT/speed.txt
    log "slow-first $(name $pkg) $url: $r"
  done
  i=$((i + 1))
done
$A emu network speed full > /dev/null 2>&1; $A emu network delay none > /dev/null 2>&1

python3 tools/speedparse.py --summary < $OUT/speed.txt | tee $OUT/speed-summary.txt
log "speed A/B done"
