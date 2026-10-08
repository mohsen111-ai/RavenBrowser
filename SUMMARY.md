# Raven: where things stand

Branch `ccr-37e88ee5-lnlhpw`, last commit `55ab07a`. Everything below is committed and pushed. No APK has been
built (the owner hasn't asked for one).

## Done

**The update after 1.0.36 (all 11 agreed items), built and committed:**
Undo after closing tabs, VPN per site, cookie popups rejected, split screen bars per half, full screen for any
page, profiles, encrypted backup file, lock for all of Raven, 20 new wallpapers (10 still, 10 live), Settings
laid out like Firefox's, the name stays Raven. The three old known bugs (split screen's bottom bar, video
fullscreen from a split half or the floating tab) are fixed.

**The owner's two new requests:**
- **Black screen, then a crash, after Recent Apps.** Likely cause: Android lowers the priority of Gecko's helper
  processes as soon as Raven leaves the screen, so a strict phone (Infinix XOS) stops them while Raven itself runs
  on. Fixes: every page on screen reopens when Raven comes back (not only the front tab), never in the background;
  a stuck fullscreen state is cleared; the floating tab's texture pauses while away; leaving no longer puts all
  tabs to sleep; tabs are saved safely on leaving; a rebuilt screen starts in the right state; a tiny-window crash
  in the floating tab is fixed. Raven's crash report (Settings, About Raven, "Copy crash report") now also holds
  Android's own record of why Raven or its engine stopped, with thread stacks for a freeze.
- **Pull down to refresh.** `engine/PullGesture.kt` and `ui/browser/Pull.kt`. Raven asks the engine, as the finger
  comes down, whether the page is at its top and isn't using the touch itself (Firefox's rule). A circle follows
  the finger, with a light tick at the reload point. Works in split halves and the floating tab.

**Fixes from a review of the last update:** VPN per site for pages opened in a new tab, no fighting another VPN
app, no switching when tapping between split halves; Undo can't bring tabs back after Clean slate or a removed
profile; flocks of the same name in two profiles stay apart; typed addresses that redirect stay in Raven;
swiping away a permission question no longer blocks the site; safer cookie popup helper; the lock covers menus
and dialogs, and picture-in-picture waits for it; live wallpapers stand still behind other screens and follow
Battery Saver; a split half on its home page keeps its bar; Settings search finds pages by name; downloads stop
cleanly at Android's 6-hour limit with their own notification.

**Tests:** 85 unit tests pass (including new pull gesture tests and a picture of the pull circle). The emulator
workflow now runs groups side by side; new scripts `update.sh`, `recents.sh`, `pull.sh`, `setup.sh`.

## Test results so far

- Emulator run 1 (code before the fixes): 14 trips through Recent Apps, no crash, no black screen. The emulator
  never stops Gecko's helpers on its own, which is why `recents.sh` now stops them on purpose (r15 to r20).
- Run 2 (helpers stopped on purpose, code before the fixes) was started; its results branch `smoke-2` was not
  found when this summary was written. Check the Actions run.
- In run 1, `update.sh` stopped early because of Android's one-time "Viewing full screen" note (a test problem,
  now fixed in `setup.sh`), and its Undo check looked too late (fixed).
- The new code (commit `55ab07a`) has **not** been run on the emulator yet.

## Remaining bugs and open questions

- **The Recent Apps crash is not confirmed fixed** on the real phone. If it happens again, ask the owner for
  "Copy crash report" from Settings, About Raven.
- A last review of the newest fixes (crash fixes, pull to refresh, review fixes, test scripts) was stopped before
  it finished. Its findings are unknown.
- Pull to refresh, known limit: a page that takes the touch only late in the gesture (some drawers, a site's own
  pull) can lose it to Raven. Firefox behaves the same.
- VPN per site: the floating tab's pages are never routed by site; in split screen one VPN serves both halves.
- The private tabs lock still lets a fullscreen video go into picture-in-picture (older issue).
- Owner hasn't answered: which of the 20 new wallpapers to swap, and whether Snow, Aurora, Clouds, Still water
  and Feather should move too.
- Only the owner can test: a real Proton VPN connection, the fingerprint lock, how the pull feels and its tick.

## Next steps

1. Start `Smoke test (Android 16 emulator)` with the default groups (now includes `recents` and `pull`) on the
   branch; set a backup reminder; read the `smoke-N` branch; fix and rerun until clean.
2. Finish the review of the newest fixes and fix anything real it confirms.
3. Then tell the owner it's ready, briefly. Build the APK only when the owner asks (`Build Raven` publishes a
   release, signed only with the repository's secrets).
4. After the owner tries it: get the crash report if Recent Apps still fails, and their wallpaper choices.
5. Update the README for the new features and tests.
