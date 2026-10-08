# The backgrounds of the ten live wallpapers. What moves is drawn over them in Raven's code (LiveSky.kt); the places
# it moves in (the fire, the candle, the moon...) are the same numbers there, in the same 390 x 844 frame.
import math
import sys
from art import *


def meteors():
    s = Scene(201)
    s.sky(top="#03050A", high="#060914", mid="#0B1124", low="#121A30")
    s.soft(195, 260, 90, 520, "#8E9BC2", 0.13, angle=-35)
    s.stars(230, 0, 560, bright=1.0, big=10)
    s.soft_hills(600, 40, FAR, waves=1.4, phase=0.8)
    s.soft_hills(650, 50, "#0A0F1C", waves=1.1, phase=2.2)
    c = "#05070D"
    s.fill_below([(-10, 700), (120, 680), (240, 650), (300, 640), (340, 648), (W + 10, 662)], c)
    s.pine(302, 646, 100, c)
    s.raven(258, 652, 0.4, color=c, left=True)
    return s


def ravenmoon():
    s = Scene(202)
    s.sky()
    moon = (195, 380, 112)
    s.stars(110, 0, 560, avoid=moon)
    s.moon(*moon, halo=2.2, halo_a=0.2)
    s.soft_hills(640, 34, FAR, waves=1.2, phase=1.4)
    c = "#05070D"
    s.fill_below([(-10, 690), (130, 672), (260, 684), (W + 10, 668)], c)
    # A dead branch reaching in from the left, below the moon.
    s.branch(-10, 600, -12, 120, 9, 4, c)
    s.branch(-10, 640, -28, 70, 6, 3, c)
    return s


def rain():
    s = Scene(203)
    s.sky(top="#05070C", high="#090C15", mid="#0E1320", low="#141A28")
    # Heavy cloud: overlapping soft lumps, a little lighter at their edges.
    for (x, y, rx, ry, a) in [(40, 110, 190, 110, 0.9), (210, 80, 220, 120, 0.85), (370, 130, 190, 110, 0.9),
                              (120, 220, 220, 90, 0.8), (300, 240, 210, 90, 0.8), (195, 310, 300, 60, 0.6)]:
        s.soft(x, y, rx, ry, "#1B2233", a)
    s.haze(480, 160, "#1A2132", 0.5)
    s.ridge(600, 30, "#0F1420", step=26)
    c = "#05070D"
    s.fill_below([(-10, 640), (150, 630), (W + 10, 636)], c)
    # A lone house with one warm window, and a bare tree by it.
    s.path("M222,630 L222,592 L254,568 L286,592 L286,630 Z", c)
    s.rect(270, 568, 7, 16, c)
    s.window_light(240, 600, 11, 12)
    s.branch(96, 632, -88, 70, 6, 4, c)
    return s


def fireflies():
    s = Scene(204)
    s.sky(low="#121B2E")
    moon = (300, 200, 22)
    s.stars(90, 0, 420, avoid=moon)
    s.crescent(*moon, phase=0.38, angle=200)
    s.haze(440, 160, "#1C2639", 0.5)
    s.forest(520, 20, 60, 110, "#131A2A", jitter=10)
    s.fill_below([(-10, 540), (W + 10, 536)], "#0C111D")
    c = "#05070D"
    # Big trees at either side, and tall grass in front.
    for (x, y, r) in [(-10, 300, 90), (30, 380, 80), (-20, 460, 90), (400, 330, 90), (360, 420, 80), (410, 480, 90)]:
        s.add(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{c}"/>')
    s.trunk(30, 760, 420, 26, c, 3)
    s.trunk(366, 760, 440, 28, c, -3)
    for i in range(120):
        x = s.rnd.uniform(-5, W + 5)
        h = s.rnd.uniform(40, 120)
        lean = s.rnd.uniform(-14, 14)
        s.add(f'<path d="M{x - 1.6:.1f},{H} Q{x + lean * 0.3:.1f},{H - h * 0.5 - 60:.1f} {x + lean:.1f},{H - h - 70:.1f} Q{x + lean * 0.3 + 1:.1f},{H - h * 0.5 - 60:.1f} {x + 1.6:.1f},{H} Z" fill="{c}"/>')
    s.fill_below([(-10, 800), (W + 10, 800)], c)
    return s


def campfire():
    s = Scene(205)
    s.sky()
    s.stars(130, 0, 470)
    s.forest(560, 18, 110, 190, "#0F1524", jitter=12)
    s.haze(470, 110, "#1A2236", 0.4)
    c = "#05070D"
    s.fill_below([(-10, 560), (W + 10, 556)], "#090C15")
    # Firelight on the ground around the fire (the flames and sparks are drawn live).
    s.add(s.glow(195, 590, 170, WARM, 0.12))
    # A tent to the right, lit on the side facing the fire.
    s.path("M250,600 L300,516 L350,600 Z", "#0B0F18")
    s.path("M250,600 L300,516 L290,600 Z", "#3A2C1E", 0.9)
    s.path("M296,600 L300,560 L306,600 Z", c)
    # The fire's stones and logs.
    for i in range(9):
        a = math.pi * (i / 8)
        s.add(f'<ellipse cx="{195 + math.cos(a) * 34:.1f}" cy="{592 + math.sin(a) * 6:.1f}" rx="8" ry="5" fill="#1A1410"/>')
    s.path("M168,592 L222,578 L224,584 L170,598 Z", "#1E1610")
    s.path("M170,580 L220,594 L218,599 L168,586 Z", "#24190F")
    # Dark ground in front, and a raven watching from a stump.
    s.fill_below([(-10, 660), (120, 650), (260, 656), (W + 10, 648)], c)
    s.rect(54, 610, 22, 44, c, rx=3)
    s.raven(65, 612, 0.5, color=c)
    return s


def sea():
    s = Scene(206)
    s.sky()
    moon = (195, 260, 46)
    s.stars(120, 0, 460, avoid=moon)
    s.moon(*moon)
    horizon = 470
    g = s.id("sea")
    s.defs.append(f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#141C31"/><stop offset="1" stop-color="{GROUND}"/></linearGradient>')
    s.add(f'<rect x="0" y="{horizon}" width="{W}" height="{H - horizon}" fill="url(#{g})"/>')
    s.haze(horizon - 26, 52, "#202A42", 0.55)
    # A rock out in the water, a raven on it.
    c = "#05070D"
    s.path("M286,604 C292,584 306,570 322,566 C340,564 356,574 364,592 L370,606 Z", c)
    s.raven(326, 568, 0.5, color=c)
    s.fill_below([(-10, 790), (W + 10, 790)], "#04060B")
    return s


def snowfall():
    s = Scene(207)
    s.sky(low="#131B2E")
    moon = (110, 210, 34)
    s.stars(70, 0, 420, avoid=moon)
    s.moon(*moon)
    s.soft(150, 240, 170, 30, "#1E2638", 0.9)
    s.soft(300, 170, 150, 22, "#1B2234", 0.8)
    s.forest(540, 24, 80, 140, "#151C2D", jitter=10)
    s.haze(470, 120, "#222B40", 0.45)
    g = s.id("snow")
    s.defs.append(f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2C354B"/><stop offset="0.6" stop-color="#1A2234"/><stop offset="1" stop-color="#0A0E18"/></linearGradient>')
    pts = [(x, 570 - 10 * math.sin(x / 60 + 1)) for x in range(-10, W + 12, 8)]
    s.add(f'<polygon points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)} {W},{H} 0,{H}" fill="url(#{g})"/>')
    c = "#05070D"
    for (x, base, h) in [(30, 650, 190), (88, 680, 150), (330, 660, 200), (380, 700, 160)]:
        s.pine(x, base, h, c)
    return s


def candle():
    s = Scene(208)
    # A dark room: the wall, and a window onto the night.
    s.rect(0, 0, W, H, "#06080E")
    g = s.id("wall")
    s.defs.append(f'<radialGradient id="{g}" cx="0.64" cy="0.57" r="0.6"><stop offset="0" stop-color="#2A1E14" stop-opacity="0.9"/><stop offset="1" stop-color="#06080E" stop-opacity="0"/></radialGradient>')
    s.add(f'<rect width="{W}" height="{H}" fill="url(#{g})"/>')
    wx, wy, ww, wh = 90, 150, 210, 330
    m = s.id("win")
    s.defs.append(f'<clipPath id="{m}"><path d="M{wx},{wy + wh} L{wx},{wy + ww / 2} A{ww / 2},{ww / 2} 0 0 1 {wx + ww},{wy + ww / 2} L{wx + ww},{wy + wh} Z"/></clipPath>')
    inner = Scene(209)
    inner.ids = 500
    inner.sky()
    inner.stars(60, 140, 470, avoid=(230, 250, 30))
    inner.moon(230, 250, 30)
    inner.ridge(470, 30, "#0F1524", step=14)
    for (x, w, h) in [(90, 40, 60), (128, 30, 90), (156, 46, 50), (200, 34, 80), (232, 44, 56), (274, 30, 74)]:
        inner.rect(x, 480 - h, w, h, "#0A0E19")
        inner.path(f"M{x - 2},{480 - h} L{x + w / 2},{480 - h - 14} L{x + w + 2},{480 - h} Z", "#0A0E19")
    inner.window_light(140, 410, 5, 7, 0.6)
    inner.window_light(240, 446, 5, 7, 0.5)
    s.defs.extend(inner.defs)
    s.add(f'<g clip-path="url(#{m})">{"".join(inner.parts)}</g>')
    # The frame: mullions, a deep sill catching the candlelight.
    fr = "#120D0A"
    s.add(f'<path d="M{wx},{wy + wh} L{wx},{wy + ww / 2} A{ww / 2},{ww / 2} 0 0 1 {wx + ww},{wy + ww / 2} L{wx + ww},{wy + wh}" fill="none" stroke="{fr}" stroke-width="10"/>')
    s.rect(wx + ww / 2 - 3, wy, 6, wh, fr)
    s.rect(wx, wy + 190, ww, 6, fr)
    s.rect(wx - 30, wy + wh, ww + 60, 16, "#2B1E14")
    s.rect(wx - 30, wy + wh + 16, ww + 60, 6, "#140E0A")
    # The candle on the sill (its flame is drawn live), in a small holder; a closed book beside it.
    cx = 250
    s.rect(cx - 9, 438, 18, 42, "#E4D6BC")
    s.add(s.glow(cx, 438, 14, "#FFFFFF", 0.3))
    s.path(f"M{cx - 20},480 L{cx + 20},480 L{cx + 16},486 L{cx - 16},486 Z", "#3A2A1C")
    s.rect(150, 462, 56, 18, "#24180F", rx=2)
    s.rect(152, 456, 52, 7, "#30200F", rx=2)
    s.raven(122, 480, 0.46, color="#0B0806")
    return s


def grass():
    s = Scene(210)
    s.sky()
    moon = (290, 250, 40)
    s.stars(110, 0, 480, avoid=moon)
    s.moon(*moon)
    s.soft(120, 330, 180, 26, "#1B2234", 0.9)
    s.soft(300, 380, 160, 20, "#192032", 0.7)
    s.soft_hills(560, 50, FAR, waves=1.3, phase=0.2)
    s.haze(500, 120, "#1C2438", 0.45)
    # A moonlit field rising to the hills: the grass (drawn live) stands dark against it.
    g = s.id("field")
    s.defs.append(f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1C2538"/><stop offset="1" stop-color="#0E1424"/></linearGradient>')
    pts = [(x, 600 - 22 * math.sin(x / W * math.pi * 0.9 + 1.8)) for x in range(-10, W + 12, 6)]
    s.add(f'<polygon points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)} {W},{H} 0,{H}" fill="url(#{g})"/>')
    return s


def circling():
    s = Scene(211)
    s.sky()
    moon = (195, 320, 74)
    s.stars(120, 0, 520, avoid=moon)
    s.moon(*moon, halo=2.6, halo_a=0.2)
    s.haze(520, 120, "#1C2438", 0.4)
    s.soft_hills(630, 40, FAR, waves=1.2, phase=0.9)
    c = "#05070D"
    s.soft_hills(690, 60, c, waves=0.9, phase=0.4)
    # A ruined bell tower on the hill: its open belfry against the moon, the top broken off.
    s.path("M170,660 L174,430 L216,430 L220,660 Z", c)
    s.path("M168,432 L222,432 L222,360 L214,352 L208,364 L200,346 L192,358 L184,344 L176,356 L168,350 Z", c)
    s.path("M182,420 L182,392 A13,13 0 0 1 208,392 L208,420 Z", "#1C2232")
    s.rect(165, 428, 60, 5, c)
    s.window_light(191, 500, 8, 12, 0.7)
    s.raven(214, 350, 0.4, color=c)
    return s


LIVES = {
    "meteors": ("Shooting stars", meteors), "ravenmoon": ("Raven and moon", ravenmoon), "rain": ("Storm", rain),
    "fireflies": ("Fireflies", fireflies), "campfire": ("Campfire", campfire), "sea": ("Moonlit sea", sea),
    "snowfall": ("Snowfall", snowfall), "candle": ("Candle", candle), "grass": ("Night wind", grass),
    "circling": ("Circling ravens", circling),
}

if __name__ == "__main__":
    for key in (sys.argv[1:] or LIVES):
        open(f"live_{key}.svg", "w").write(LIVES[key][1]().svg())
