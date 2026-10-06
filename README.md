# Raven

A quiet, private Android browser for the night. Built on Firefox's Gecko engine (GeckoView), so real
Firefox add-ons work, starting with uBlock Origin, which comes installed.

## What's in it

- **Home:** a new night wallpaper each time Raven opens (eleven to choose from, or keep one), the
  greeting, what you were reading, a dock of your sites, and how many trackers Shield turned away today.
- **Tabs:** a hand of cards, everyday and private tabs kept apart, flocks (tab groups), and swiping the
  address bar sideways to move between tabs.
- **Private tabs:** they keep nothing, and lock with your fingerprint or screen lock when you leave.
- **Bookmarks:** the hoard, with folders, search, and bookmarks first in the address suggestions.
- **VPN:** Raven's own WireGuard VPN, for Raven only, with the location files from a Proton VPN account:
  on and off and the country, from the menu.
- **Translate:** whole pages, on the phone (Firefox's translation models; nothing is sent to a server).
- **Videos:** fullscreen turns to landscape for wide videos, picture-in-picture when you leave, and media
  controls in the notification and on the lock screen.
- **Speed and memory:** Gecko tuned for phones (one shared web process, fewer prelaunched processes,
  unused tabs put to sleep).
- **Blocking and privacy:** uBlock Origin kept up to date, strict tracking protection, HTTPS-only mode,
  encrypted DNS, site isolation for logged-in sites, erase-on-close, and Clean slate to erase everything.
- **Add-ons:** install any Android-compatible Firefox add-on from addons.mozilla.org or from a `.xpi` file;
  add-ons with a toolbar button open their panel from the menu.
- **Downloads:** up to 8 connections per file when the server allows it; servers that limit connections
  are waited out, so a download always finishes. Keeps going in the background; pause and resume.
- **Everyday tools:** history, find in page, desktop site, save as PDF, share, pin a site to Raven's
  home, page permissions.

## Getting the app

`Build Raven` (started by hand from the Actions tab) builds a phone APK (arm64) and publishes it under
**Releases** as `Raven 1.0.N`. Open the release on the phone, download the APK, and open it to install.

### Signing

Builds are signed with the key in these repository secrets:

| Secret | Value |
| --- | --- |
| `RAVEN_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `RAVEN_KEYSTORE_PASSWORD` | the keystore and key password |
| `RAVEN_KEY_ALIAS` | the key's alias |

Without them the build stops: this repository is public, so it never makes a key of its own or keeps one
in its Actions cache. Never commit a keystore or its password to the repository.

## Building locally

Needs JDK 21 and the Android SDK with `platforms;android-37.1` and `build-tools;37.0.0`.

```
./gradlew :app:assembleRelease                      # phone (arm64)
./gradlew :app:assembleRelease -PgeckoAbi=x86_64    # emulator
./gradlew :app:testDebugUnitTest                    # unit tests, and pictures of the screens in app/build/shots
```

## Emulator tests

`Smoke test (Android 16 emulator)` is started by hand from the Actions tab. It installs the x86_64 build on
an emulator and drives it like a person would, one script after another:

- `tools/smoke.sh`: first run, uBlock Origin, menu, tabs, private tabs, HTTPS-only, a 100 MB download,
  find in page, settings, add-ons, history, and the launcher icon.
- `tools/media.sh`: Home and Reload in the bar, leaving and coming back without a crash, and media controls.
- `tools/video.sh`: fullscreen video, landscape, leaving in the middle and coming back.
- `tools/tabs.sh`: the hand of cards, closing and throwing away tabs, Close all on each side.
- `tools/back.sh`: Back like Chrome, page by page, and a tab a page opened going back to its opener.
- `tools/deep.sh`: long-press link menu, desktop site, landscape, tabs after the app is killed, a setting
  that restarts the app, installing Dark Reader, dark websites, and Clean slate.
- `tools/more.sh`: search, save as PDF, permissions, a large download from its notification, and memory
  with many tabs.
- `tools/raven.sh`: the wallpaper changing, swiping the address bar, bookmarks, flocks, wallpaper settings,
  the menu, Translate, picture-in-picture, and the VPN.
- `tools/final.sh`: Android's own crash and "isn't responding" reports for the run.

Screenshots, memory use, logs and the R8 map are pushed to a `smoke-N` branch.

## Crash reports

After a crash, Settings > About has "Copy crash report". Release builds are minified; each CI build keeps
its R8 map as the `mapping-N` artifact, and `retrace --partition-map mapping.prt report.txt`
(Android cmdline-tools) turns the report back into file and line numbers.

## Layout

| Path | What it holds |
| --- | --- |
| `engine/` | Gecko runtime and settings, tabs, add-ons, page prompts, error pages, media controls, the VPN |
| `downloads/` | multi-connection downloader and its foreground service |
| `data/` | settings, history, bookmarks and download records (SQLite) |
| `ui/` | Compose screens: browser, tabs and flocks, bookmarks, downloads, history, settings, add-ons, welcome |
| `ui/sky/` | the night wallpapers and their rotation |
| `ui/theme/` | colors, fonts and the icon set |
