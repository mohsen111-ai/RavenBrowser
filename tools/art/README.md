# Raven's wallpapers

The second ten still wallpapers and the backgrounds of the ten live ones are drawn here as SVG, in the 390 × 844
frame all of Raven's wallpapers use, then rendered at 1080 × 2337 with Chromium (Playwright) and saved as WebP in
`app/src/main/res/drawable-nodpi/wall_<name>.webp`. What moves in a live wallpaper is drawn in the app itself
(`ui/sky/LiveSky.kt`), at the same places as in its background.

```
python3 stills.py            # or: python3 stills.py lighthouse wolf
python3 lives.py
node render.js still_*.svg live_*.svg
python3 sheet.py sheet.png still_*.png       # all of them on one picture, to look at
```

`./gradlew :app:testDebugUnitTest --tests '*LivePreview*'` draws frames of each live wallpaper with the app's own
code (in `app/build/live/`), to see how they move.

The first eleven (Moonrise to Crescent, and Eclipse) were drawn before this folder existed.
