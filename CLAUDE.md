# Notes for Claude: read this first

Raven is a personal Android browser built for one person, the owner of this repository, who tests it on
their own phone (an Infinix phone, XOS 16.3). These notes carry over what earlier sessions learned and
agreed, so a new session can continue without the old conversation. The README lists Raven's features,
how to build it, and what each emulator test script covers.

## How the owner likes to work

- **Discuss first, build only on "go".** When they say "don't create anything", or are just asking or
  discussing, only answer. Don't start building, pushing or testing until they say so.
- **Tell them before you start anything**, in a few words, and report what happened when it's done.
- **Plain, simple English.** Short sentences, no jargon. When they don't understand something, explain it
  again with an everyday example instead of more detail. They read on a phone.
- **Make the APK only when they ask for it.** Starting `Build Raven` publishes a release; once a build ran
  before they wanted it and they had to say "No. Fix all mistakes and glitches first". Fix and test first.
- **Never ask them to paste a key, token or password into the chat.** If one is needed, guide them to put
  it straight into GitHub's settings (Settings → Secrets and variables → Actions).
- **Give a recommendation**, not only options. They often answer "you decide the best".
- When they're away or asleep and have said to keep going: test → fix → retest until clean, then report
  everything that happened when they're back.

## Repositories

- **mohsen111-ai/RavenBrowser** (this one, public): Raven's code from 1.0.36 on. Releases are here.
- **mohsen111-ai/NewKepler** (private): the archive. It holds the full history (Kepler → Noir → Raven) with
  the reasons for each change in its commit messages. Look there with `git log` when you need to know why
  something is the way it is. Its Actions cache keeps a backup of the signing key (`raven-ci-key-v1`),
  refreshed by `Keep the signing key` every 3 days.

## Branches

Earlier sessions committed straight to `main`. New sessions start from `main`, so work left only on another
branch is invisible to the next session. When the owner allows pushing to `main` (they may say so in their
first message), work there. Otherwise work on your session's branch, run the tests on it (start the
workflows with that branch as the ref), and before you finish, ask the owner whether to merge it into
`main`.

## Rules for this public repository

- **Commit as** `git -c user.name="mohsen111-ai" -c user.email="337842191+mohsen111-ai@users.noreply.github.com"`.
  Never put the owner's real name or email in commits, code or files here.
- **Commit messages** say what changed and why, in plain words, plus the attribution lines your session
  asks for. No model names anywhere in the repository.
- **Signing:** builds sign only with the `RAVEN_KEYSTORE_BASE64`, `RAVEN_KEYSTORE_PASSWORD` and
  `RAVEN_KEY_ALIAS` secrets. Its certificate SHA-256 is `060ea0b57169bd6bda99478371fb3e6bb4b661c8d251103a253bb8c5e3b8c0a8`
  (`build.yml` prints it; check that it still matches). Never make, cache or commit a key in this
  repository. A different key means the phone refuses the update and Raven must be reinstalled, losing data.
- **Versions** are `1.0.N` with N = the build run number + 36 (1.0.36 was the last NewKepler build).
- **No workflow may run on `pull_request`, `pull_request_target` or similar triggers.** Strangers can then
  never run anything here or reach the secrets. Keep every workflow on `workflow_dispatch` (or `schedule`).
- **VPN location files** (WireGuard configs from the owner's Proton account) contain private keys. They
  stay in Raven's private storage on the phone and are never shown, logged, sent or committed. The VPN test
  makes up a dummy one each run.

## The code, briefly

Kotlin + Jetpack Compose on GeckoView 157 (`app/build.gradle.kts`). Package `app.raven.browser`.

- `engine/`: `Engine.kt` (Gecko runtime, prefs, content blocking, `warmUp` connections while typing),
  `TabManager.kt` (tabs, floating tab, split screen, media "one at a time" via `pauseOthers`),
  `Helper.kt` + `assets/helper/media.js` (Raven's own page helper extension: pause, mute, video only),
  `RavenVpn.kt` (WireGuard), `UrlInput.kt`, `MediaControls.kt`.
- `ui/RavenRoot.kt`: screens, fullscreen video, screen turning (`RotationHold.kt`), picture-in-picture.
- `ui/browser/`: `BrowserScreen.kt` (bar, suggestions), `Float.kt` (floating tab), `NewTabPage.kt` (home,
  greeting, Continue, home sites), `Greetings.kt` (122 greetings by time of day, shuffle bag).
- `ui/screens/SettingsScreen.kt`, `WallpaperPicker.kt`; `ui/sky/Sky.kt` (wallpapers); `data/Settings.kt`.
- uBlock Origin ("Shield") installs from addons.mozilla.org on first run; it isn't bundled.
- Deliberate choices: `media.audioFocus.management=false` (Gecko's own rule paused split-screen halves);
  "Close all" on the Tabs screen only closes tabs, Clean slate (erase everything) lives only in the menu;
  Continue shows only the last-used tab that's still open.

## Testing

- **Unit tests and screen pictures:** `./gradlew :app:testDebugUnitTest` (pictures in `app/build/shots`).
- **Emulator:** `Smoke test (Android 16 emulator)`, started by hand. Input `scripts`, default
  `smoke media video tabs back deep raven more`. Results (screenshots, `steps.txt`, logs, crash and ANR
  reports from `final.sh`) are pushed to a `smoke-N` branch. Read them there.
- **Minutes are free here** (public repository) and the runners have 4 cores and 16 GB, against 2 and 8 in
  the old private one. Planned: split the scripts over several jobs that run at once, and give the
  emulator more cores and memory. A full run used to take 2 to 2½ hours.
- After starting a run, set a backup reminder for yourself, so a finished run is never left unread.
- **Lessons the tests taught:**
  - uiautomator needs a still screen: a test page must not keep changing its text, or dumps fail.
  - Tab cards overlap: tap a tab by its title (`tap "!=Title"`), not the middle of its card.
  - Helpers in `tools/lib.sh`: `tap`, `try_tap`, `hold`, `shot`, `log`, `menu`, `go`, `front`, `newtab`.
  - The emulator sometimes loses Android's core (system_server) on heavy pages, and Gecko sometimes stalls
    on resume (`syncResumeResizeCompositor`) because the emulator draws without a graphics chip. Neither
    is a Raven bug; anything else in `final.sh` is.
  - Things only the owner can test: a real Proton VPN connection and the fingerprint lock.

## Known bugs to fix

1. **Split screen:** the address bar only works for the top half; the bottom half can't be refreshed or
   sent to another address.
2. **Video full screen in split screen** doesn't work.
3. **Video full screen in the floating tab:** the window turns wide for a split second, then the page comes
   back. `Float.kt` shows the page's fullscreen as "video only" in the window, and something exits
   fullscreen at once.
   **Fix for 2 and 3:** the video fills the whole phone screen (sideways for a wide video), and Back
   returns it to its half or its floating window.

## The next update: agreed, waiting for "go"

1. **Undo after closing a tab:** a "Tab closed · Undo" bar for a few seconds, also after Close all.
2. **VPN per site:** the owner picks a country for each site; other sites follow the normal VPN setting.
3. **Cookie popups:** rejected automatically; popups with no Reject button are hidden. (GeckoView has
   cookie banner handling in `ContentBlocking.Settings`.)
4. **Split screen bars:** each half gets its own small bar (address, refresh, menu). Both hide when
   scrolling down and come back when scrolling up. A full-screen switch hides both.
5. **Full screen for any page:** hides every bar. A swipe down from the top shows them for a moment. It
   stays on for all sites and tabs until turned off (decided).
6. **Profiles** (Gecko containers, `contextId`): separate "people" with their own logins, cookies, history
   and tabs, for example two Instagram accounts at once. A coloured mark on each tab, switching on the Tabs
   screen, and "Open in another profile" on a long-pressed link. Settings, home sites, Shield, VPN and
   downloads are shared.
7. **Backup file:** Backup and Restore buttons. One encrypted file (tabs, history, bookmarks, home sites,
   settings) the owner keeps, for example in Proton Drive. Logins to websites aren't included.
8. **Lock for all of Raven:** optional, off by default, with "Lock after": immediately, 1, 5 or 30
   minutes; default 5 minutes (decided). Private tabs keep their own lock.
9. **Wallpapers: 10 new still ones and 10 new live ones.** Draw all 20 and show them to the owner first,
   so they can swap any. Raven's style: sketchy and cozy but dark, manly, night, ravens and moons.
   - Still ideas so far: lighthouse on a cliff, castle ruins with a raven, cabin in snowy woods, a wolf on
     a ridge under the moon, desert dunes under stars, forest path with a lantern.
   - Live ideas so far: shooting stars, a raven flying across the moon, rain with rare lightning,
     fireflies, a campfire with sparks.
   - Live ones are drawn in code (no video files), move only while the home screen is visible, and stand
     still with Reduce motion or Battery Saver. The "Moving sky" switch becomes "Live wallpapers".
   - Not answered yet: whether the current Snow, Aurora, Clouds, Still water and Feather should also move.
   - The "Plain night" tile looks empty in the picker: give it a name and a few stars.
   - The current 11: Moonrise, Pines, Feather, Rooftops, Aurora, Still water, Snow, Corvus, Clouds, Night
     flight, Crescent (plus Plain night, and Eclipse for private tabs).
10. **Settings, reorganized like Firefox's** (the owner compared the two):
    - A short main page; each line opens its own page and shows its current value on the right. Raven's
      explanations move inside those pages. The wallpaper grid leaves the main page.
    - Top: a Profiles card, and "Make Raven your default browser" only while it isn't.
    - General: Search engine, Home and wallpapers, Tabs, Look, Downloads.
    - Privacy and security: Shield, VPN, Tracking protection, Cookie popups, HTTPS-only, Encrypted DNS,
      Site isolation, Site permissions (new: camera, location, notifications per site), Lock, Erasing.
    - Advanced: Picture-in-picture, Add-ons, Backup and restore. Then About.
    - Also a search box in Settings and "Open links in apps".
    - Leave out Firefox's Passwords, Autofill, AI controls, Page summaries, Data collection and Labs.
11. The name stays **Raven**.

## Already decided against (don't suggest again)

Slow internet mode; swipe gestures for back and forward (they clash with Android's back gesture); a paid
Raven account or sync server; Firefox account sync; a speed test against Firefox; reader view; a bottom
address bar; a password manager; VPN extensions; selling or publishing Raven.

## Later, a separate project

A theme with live wallpapers, icons and widgets in Raven's style (dark, night, ravens), with a new live
wallpaper each time the phone is opened. It would get its own new public repository. The owner's phone
can't import outside themes, so how it would be installed is still open. Not started; the owner said
"let's go back to the browser" first.
