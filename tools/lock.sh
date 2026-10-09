#!/bin/bash
# The lock for all of Raven on its own: why the "Lock Raven" switch asks for the screen lock (or doesn't) on the emulator.
set -u
source tools/lib.sh
log "---- lock"
front; sleep 3
$A logcat -c
log "set-pin: $($A shell locksettings set-pin 1234 2>&1 | tr '\n' ' ')"
log "verify: $($A shell locksettings verify --old 1234 2>&1 | tr '\n' ' ')"
log "keyguard secure: $($A shell dumpsys window | grep -m2 -i -E 'isKeyguardSecure|mKeyguardSecure|keyguardSecure' | tr '\n' ' ')"
setting "lock" "Lock"; shot l01_lock_page
dump; cp "$OUT/ui.xml" "$OUT/lock-page.xml"
for i in 1 2 3; do
  y=$(find_xy "^Lock Raven" | cut -d' ' -f2)
  $A shell input tap $((W * 84 / 100)) ${y:-238}; log "tapped the switch ($i) at ${y:-238}"
  sleep 2; shot l02_after_tap_$i
  sleep 2; shot l03_after_wait_$i
  dump; cp "$OUT/ui.xml" "$OUT/lock-after-$i.xml"
  log "after tap $i: prompt $(python3 tools/find.py "$OUT/ui.xml" "Lock Raven" > /dev/null && echo 'Lock Raven label on screen' || echo none) | PIN field $(python3 tools/find.py "$OUT/ui.xml" "pin" > /dev/null && echo yes || echo no)"
  $A shell input text 1234; sleep 1; $A shell input keyevent 66; sleep 3; shot l04_after_pin_$i
done
$A logcat -d | grep -i -E "biometric|BiometricPrompt|ConfirmDeviceCredential|KeyguardManager|Toast|app.raven" | grep -v -E "GeckoConsole|ViewRootImpl|Choreographer" > $OUT/lock-log.txt
log "log lines kept: $(wc -l < $OUT/lock-log.txt)"
$A shell locksettings clear --old 1234 > /dev/null 2>&1
log "---- lock done"
