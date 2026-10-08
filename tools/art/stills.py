# The ten new still wallpapers. Each draws into the 390 x 844 frame: the greeting sits in the top third and the
# dock at the bottom, so the scene's picture lives in the middle and the bottom stays dark.
import math
import sys
from art import *


def lighthouse():
    s = Scene(11)
    s.sky()
    moon = (92, 230, 40)
    s.stars(110, 0, 470, avoid=moon)
    s.moon(*moon)
    # The sea, and the moon's path on it.
    sea_y = 540
    g = s.id("sea")
    s.defs.append(f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#131B30"/><stop offset="1" stop-color="{GROUND}"/></linearGradient>')
    s.add(f'<rect x="0" y="{sea_y}" width="{W}" height="{H - sea_y}" fill="url(#{g})"/>')
    for i in range(24):
        y = sea_y + 5 + i * i * 0.5
        w = 8 + i * 2.4
        s.rect(moon[0] - w / 2 + s.rnd.uniform(-5, 5), y, w * s.rnd.uniform(0.5, 1), 1.1, MOON, 0.5 - i * 0.018, rx=0.6)
    for i in range(24):
        s.rect(s.rnd.uniform(0, 200), sea_y + s.rnd.uniform(10, 260), s.rnd.uniform(14, 40), 0.8, "#AEB6C4", 0.1)
    s.haze(sea_y - 34, 64, "#1E2840", 0.5)
    # The beam: a pale cone from the lantern out over the sea.
    lx, ly = 300, 360
    g = s.id("beam")
    s.defs.append(f'<linearGradient id="{g}" x1="1" y1="0" x2="0" y2="0"><stop offset="0" stop-color="#EDE4C6" stop-opacity="0.34"/><stop offset="1" stop-color="#EDE4C6" stop-opacity="0"/></linearGradient>')
    s.add(f'<polygon points="{lx},{ly - 4} -30,{ly - 90} -30,{ly + 50} {lx},{ly + 4}" fill="url(#{g})"/>')
    # The headland: rock falling to the sea on the left, the tower on its top.
    s.fill_below([(214, H), (222, 640), (236, 602), (250, 570), (258, 548), (266, 520), (284, 504), (320, 496), (360, 500), (W + 10, 494)], "#0A0E19")
    s.path("M226,620 L240,600 L252,606 L246,630 Z", "#121828")
    s.path("M250,566 L262,544 L270,552 L262,572 Z", "#121828")
    s.fill_below([(150, H), (180, 720), (220, 690), (260, 670), (W + 10, 650)], NEAR)
    for i in range(6):
        s.rect(196 - i * 10, 652 + i * 6, 18 + i * 4, 1, "#C7CCD8", 0.18)
    # The lighthouse: a tapering striped tower, its gallery, the lantern room and its light.
    c = "#0C111E"
    s.path("M282,500 L289,388 L311,388 L318,500 Z", c)
    for k in range(4):
        y = 404 + k * 24
        a, b = 289 - (y - 388) * 0.0625, 311 + (y - 388) * 0.0625
        s.path(f"M{a:.1f},{y} L{b:.1f},{y} L{b + 0.6:.1f},{y + 10} L{a - 0.6:.1f},{y + 10} Z", "#151C2E")
    s.rect(286, 476, 6, 10, "#F2C98A", 0.8)
    s.rect(282, 381, 36, 7, c)
    for k in range(6):
        s.rect(283.5 + k * 6.4, 373, 1.2, 8, c)
    s.rect(283, 372, 34, 1.4, c)
    s.add(s.glow(lx, ly, 56, "#F3E6C2", 0.5))
    s.rect(290, 348, 20, 24, "#F6EED4", 0.96)
    s.rect(299, 348, 2, 24, c)
    s.path("M286,349 L300,333 L314,349 Z", c)
    s.rect(299.2, 326, 1.6, 8, c)
    s.raven(316, 372, 0.4, color=c, left=True)
    s.raven(180, 300, 0.55, far=True, color="#0D1220")
    s.raven(152, 318, 0.4, far=True, color="#0D1220")
    return s


def ruins():
    s = Scene(23)
    s.sky()
    s.stars(120, 0, 520, avoid=(290, 190, 40))
    s.crescent(290, 190, 40, phase=0.36, angle=200)
    s.haze(470, 120, "#1A2236", 0.45)
    s.ridge(560, 40, FAR, step=26)
    s.soft_hills(640, 60, MID, waves=1.2, phase=1.0)
    c = "#070A12"
    # The castle on its hill: a curtain wall, two broken towers, arched holes the sky shows through.
    s.soft_hills(700, 70, c, waves=1.0, phase=0.6)
    s.add('<g transform="translate(-60,-120) scale(1.3)">')
    s.path("M70,640 L70,520 L76,520 L76,512 L84,512 L84,520 L92,520 L92,506 L100,498 L104,510 L110,500 L116,512 L116,640 Z", c)
    s.path("M116,640 L116,568 L124,568 L124,560 L132,560 L132,568 L140,568 L140,562 L148,562 L148,568 L156,568 L156,558 L164,566 L170,560 L176,572 L182,566 L190,574 L196,566 L204,574 L204,640 Z", c)
    s.path("M204,650 L204,470 L212,470 L212,460 L222,460 L222,470 L230,470 L230,452 L236,440 L242,456 L250,446 L254,470 L262,470 L262,650 Z", c)
    s.path("M262,650 L262,590 L300,590 L300,584 L308,584 L308,590 L320,590 L324,600 L330,594 L334,650 Z", c)
    for (x, y, w, h) in [(226, 500, 10, 18), (90, 548, 8, 14), (232, 560, 8, 14)]:
        s.path(f"M{x},{y + h} L{x},{y + w / 2} A{w / 2},{w / 2} 0 0 1 {x + w},{y + w / 2} L{x + w},{y + h} Z", "#121A2C")
    s.raven(246, 448, 0.55, color=c)
    s.add('</g>')
    s.raven(150, 400, 0.6, far=True, color="#0B0F1A")
    s.raven(118, 430, 0.42, far=True, color="#0B0F1A")
    return s


def cabin():
    s = Scene(37)
    s.sky(low="#121A2C")
    moon = (270, 210, 34)
    s.stars(90, 0, 450, avoid=moon)
    s.moon(*moon)
    # Snowy ground: pale in the moonlight, darker in front.
    g = s.id("snow")
    s.defs.append(f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2A3349"/><stop offset="0.5" stop-color="#1A2235"/><stop offset="1" stop-color="#0A0E18"/></linearGradient>')
    s.forest(560, 26, 70, 130, "#121928", jitter=8)
    pts = [(x, 590 - 14 * math.sin(x / 70)) for x in range(-10, W + 12, 8)]
    s.add(f'<polygon points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)} {W},{H} 0,{H}" fill="url(#{g})"/>')
    # The cabin, its warm window, and smoke from the chimney.
    c = "#080B14"
    s.path("M130,610 L130,560 L182,524 L234,560 L234,610 Z", c)
    s.path("M120,566 L182,518 L244,566 L238,568 L182,526 L126,568 Z", "#3A455E")
    s.rect(206, 522, 10, 26, c)
    for i in range(7):
        s.add(s.glow(212 + i * 7 + math.sin(i) * 5, 512 - i * 16, 9 + i * 3, "#9AA3B6", 0.16 - i * 0.017))
    s.window_light(150, 572, 14, 14)
    s.window_light(196, 572, 14, 14, 0.85)
    s.rect(156.3, 572, 1.4, 14, c)
    s.rect(150, 578.3, 14, 1.4, c)
    s.add(s.glow(170, 640, 70, WARM, 0.08))
    for x in [60, 300, 352]:
        s.pine(x, 640 + s.rnd.uniform(-10, 10), s.rnd.uniform(120, 170), "#05070D")
    for i in range(60):
        s.add(f'<circle cx="{s.rnd.uniform(0, W):.1f}" cy="{s.rnd.uniform(380, 760):.1f}" r="{s.rnd.choice([0.6, 0.8, 1.0, 1.3])}" fill="#E4E8EF" opacity="{s.rnd.uniform(0.2, 0.6):.2f}"/>')
    return s


def wolf():
    s = Scene(41)
    s.sky()
    moon = (195, 420, 92)
    s.stars(120, 0, 520, avoid=moon)
    s.moon(*moon, halo=2.2, halo_a=0.2)
    s.ridge(600, 50, FAR, step=24)
    # The ridge climbing from the left to a rock the wolf stands on.
    c = "#06080F"
    s.fill_below([(-10, 560), (40, 548), (90, 530), (130, 514), (160, 500), (200, 486), (226, 482), (246, 488), (268, 512), (300, 540), (340, 566), (W + 10, 590)], c)
    # The wolf, sitting, howling: head thrown back, nose to the moon, its tail on the rock.
    s.path("M204,486 C202,476 203,466 206,458 C208,450 212,446 216,442 C214,436 213,428 214,420 L216,412 L218,402 "
           "C219,396 222,390 226,386 L224,376 L230,382 L232,372 L236,384 C240,382 244,378 248,372 L252,366 L254,368 "
           "C252,376 248,384 244,390 C240,396 238,402 238,410 C240,422 240,432 238,442 C244,450 248,462 248,474 "
           "L250,486 L244,486 L242,476 L240,486 L234,486 L234,470 C230,474 226,476 222,476 L224,486 L218,486 "
           "L216,478 L214,486 Z", c)
    s.path("M204,484 C194,486 184,490 176,488 C182,484 190,480 198,478 Z", c)
    s.raven(110, 330, 0.5, far=True, color="#0B0F1A")
    return s


def dunes():
    s = Scene(53)
    s.sky(top="#03050A", high="#070914", mid="#0D1124", low="#161A30")
    # The Milky Way: a soft, blurred band of light with a crowd of small stars across it.
    s.soft(195, 280, 100, 560, "#8E9BC2", 0.16, angle=28)
    s.soft(205, 250, 44, 400, "#B9C3DE", 0.12, angle=28)
    for i in range(320):
        t = s.rnd.gauss(0, 0.45)
        along = s.rnd.uniform(-560, 560)
        x = 195 + along * math.sin(math.radians(28)) * -1 + t * 60
        y = 280 + along * math.cos(math.radians(28)) + t * 30
        if 0 < x < W and 0 < y < 560:
            s.add(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{s.rnd.choice([0.35, 0.45, 0.55])}" fill="#FFFFFF" opacity="{s.rnd.uniform(0.15, 0.6):.2f}"/>')
    s.stars(100, 0, 560)
    s.crescent(310, 470, 20, phase=0.4, angle=150, halo_a=0.12)
    # Dunes: smooth curves, lit faces a little lighter.
    def dune(y, amp, color, phase, length):
        pts = [(x, y - amp * math.sin((x + phase) / length * math.pi) ** 2) for x in range(-10, W + 12, 5)]
        s.fill_below(pts, color)
    dune(590, 40, "#151A2C", 40, 260)
    dune(630, 60, "#10142A", 180, 330)
    dune(690, 70, "#0B0E1C", 90, 300)
    dune(760, 50, "#06080F", 250, 420)
    s.raven(80, 708, 0.36, color="#06080F")
    return s


def lantern():
    s = Scene(67)
    s.sky(low="#121A2C")
    s.stars(70, 0, 300)
    s.crescent(250, 150, 22, phase=0.4, angle=-30, halo_a=0.12)
    # Far pines, pale in the mist; nearer ones darker.
    s.forest(470, 22, 90, 150, "#1A2234", jitter=10)
    s.haze(400, 160, "#232C42", 0.6)
    s.forest(520, 16, 120, 190, "#111726", jitter=16)
    s.haze(470, 120, "#1C2438", 0.5)
    s.fill_below([(-10, 560), (W + 10, 556)], "#090C16")
    # The path, winding away into the wood.
    g = s.id("path")
    s.defs.append(f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#151B2A"/><stop offset="1" stop-color="#0A0D17"/></linearGradient>')
    s.add(f'<path d="M196,556 C186,580 230,600 222,630 C214,662 150,690 140,{H} L290,{H} C280,700 300,664 280,630 C266,604 214,584 206,556 Z" fill="url(#{g})"/>')
    # Near trees framing it: crooked trunks and bare branches.
    c = "#05070D"
    for (x, w, top, lean) in [(18, 34, -20, 6), (92, 18, 40, -4), (322, 22, 20, 4), (374, 38, -20, -6)]:
        s.trunk(x, 760, top, w, c, lean)
    for (x, y, ang, ln, wd) in [(28, 160, -30, 70, 5), (16, 300, -20, 60, 4), (96, 200, -150, 40, 3), (368, 180, -150, 70, 5), (322, 240, -40, 50, 3.5), (380, 340, -160, 56, 4)]:
        s.branch(x, y, ang, ln, wd, 3, c)
    s.fill_below([(-10, 740), (120, 730), (300, 736), (W + 10, 728)], c)
    # The lantern on its post, glowing warm, with light on the path.
    lx = 118
    s.add(s.glow(lx, 600, 130, WARM, 0.15))
    s.add(s.glow(lx, 574, 42, "#F6D9A0", 0.5))
    s.rect(lx - 1.6, 574, 3.2, 170, c)
    s.rect(lx - 8, 562, 16, 2, c)
    s.path(f"M{lx - 9},564 L{lx + 9},564 L{lx + 6},586 L{lx - 6},586 Z", "#F6D9A0", 0.92)
    s.rect(lx - 0.6, 564, 1.2, 22, c)
    s.path(f"M{lx - 10},564 L{lx},553 L{lx + 10},564 Z", c)
    s.rect(lx - 7, 586, 14, 3, c)
    s.raven(lx, 553, 0.36, color=c, left=True)
    return s


def peaks():
    s = Scene(79)
    s.sky()
    moon = (120, 200, 30)
    s.stars(110, 0, 440, avoid=moon)
    s.moon(*moon)
    # Peaks with moonlit snow on their right faces, mirrored in the lake.
    def peak(x, top, w, base, dark, lit):
        s.path(f"M{x - w},{base} L{x},{top} L{x + w},{base} Z", dark)
        s.path(f"M{x},{top} L{x + w},{base} L{x + w * 0.35},{base} L{x + w * 0.2},{top + (base - top) * 0.55} L{x + w * 0.05},{top + (base - top) * 0.3} Z", lit)
    lake = 560
    for (x, top, w, dark, lit) in [(80, 380, 120, "#121828", "#2A3448"), (230, 330, 150, "#101626", "#33405A"), (340, 410, 110, "#121828", "#252E44")]:
        peak(x, top, w, lake, dark, lit)
    s.ridge(lake, 30, "#0B101C", step=14)
    s.rect(0, lake, W, H - lake, "#080C17")
    # Their reflection: the same peaks upside down, dim, under a little haze.
    for (x, top, w, dark, lit) in [(80, 380, 120, "#0E1322", "#1A2234"), (230, 330, 150, "#0D1220", "#1E2740"), (340, 410, 110, "#0E1322", "#182032")]:
        bottom = lake + (lake - top) * 0.8
        s.path(f"M{x - w},{lake} L{x},{bottom:.1f} L{x + w},{lake} Z", dark, 0.9)
        s.path(f"M{x},{bottom:.1f} L{x + w},{lake} L{x + w * 0.35},{lake} L{x + w * 0.2},{lake + (bottom - lake) * 0.45:.1f} Z", lit, 0.7)
    s.haze(lake - 10, 120, "#1A2234", 0.35)
    for i in range(18):
        y = lake + 40 + i * i * 0.7
        s.rect(moon[0] - 12 - i, y, 24 + i * 2, 1, MOON, 0.35 - i * 0.017, rx=0.5)
    for i in range(14):
        s.rect(s.rnd.uniform(0, W - 60), lake + s.rnd.uniform(10, 220), s.rnd.uniform(20, 60), 0.8, "#AEB6C4", 0.12)
    s.fill_below([(-10, 720), (60, 700), (120, 712), (180, 730), (W + 10, 740)], "#05070D")
    s.forest(724, 9, 50, 90, "#05070D", x0=230, x1=W + 10, jitter=6)
    return s


def oak():
    s = Scene(97)
    s.sky()
    moon = (220, 330, 86)
    s.stars(110, 0, 520, avoid=moon)
    s.moon(*moon, halo=2.3, halo_a=0.18)
    s.soft_hills(640, 40, FAR, waves=1.3, phase=2.0)
    c = "#06080F"
    s.soft_hills(690, 70, c, waves=0.9, phase=0.9)
    # The old oak: a thick crooked trunk and a crown of bare, twisting branches.
    s.path("M168,660 C174,610 168,560 178,520 C182,500 196,488 206,480 L214,486 C204,500 198,520 198,560 C198,600 206,630 214,664 Z", c)
    tips = []
    for (ang, ln, wd) in [(-150, 80, 9), (-120, 76, 9), (-80, 70, 9), (-45, 86, 9), (-20, 70, 7), (-170, 60, 6)]:
        s.branch(190 + s.rnd.uniform(-6, 6), 496, ang, ln, wd, 4, c, tips=tips)
    for (x, y, sc, left) in [(118, 0, 0.5, False), (270, 0, 0.46, True), (150, 0, 0.42, True)]:
        t = min(tips, key=lambda p: abs(p[0] - x) + abs(p[1] - 420) * 0.5)
        s.raven(t[0], t[1] + 2, sc, color=c, left=left)
    s.raven(300, 250, 0.6, flying=True, angle=-70, color="#0B0F1A")
    return s


def stones():
    s = Scene(103)
    s.sky()
    moon = (195, 250, 36)
    s.stars(120, 0, 520, avoid=moon)
    # A ring around the moon.
    s.add(f'<circle cx="{moon[0]}" cy="{moon[1]}" r="100" fill="none" stroke="#C7CCD8" stroke-opacity="0.11" stroke-width="12"/>')
    s.moon(*moon)
    s.haze(540, 120, "#1C2438", 0.45)
    s.soft_hills(600, 30, FAR, waves=1.5, phase=0.4)
    s.fill_below([(-10, 620), (W + 10, 612)], "#0B0F1A")

    def stone(x, base, w, h, color, lean=0.0):
        """A rough standing stone: bulging sides, a slanted, broken top."""
        r = s.rnd
        tl = base - h * r.uniform(0.86, 0.98)
        tr = base - h * r.uniform(0.9, 1.0)
        mid = base - h * r.uniform(0.94, 1.02)
        s.path(f"M{x - w / 2:.1f},{base} L{x - w * 0.56 + lean * 0.5:.1f},{base - h * 0.45:.1f} L{x - w * 0.44 + lean:.1f},{tl:.1f} "
               f"L{x - w * 0.1 + lean:.1f},{mid:.1f} L{x + w * 0.2 + lean:.1f},{mid + h * 0.05:.1f} L{x + w * 0.46 + lean:.1f},{tr:.1f} "
               f"L{x + w * 0.54 + lean * 0.5:.1f},{base - h * 0.5:.1f} L{x + w / 2:.1f},{base} Z", color)
    # The far side of the ring, then the great trilithon in the middle, then the near stones, big and black.
    far = "#131A2A"
    for (x, w, h) in [(46, 18, 54), (92, 16, 46), (300, 16, 48), (346, 18, 56)]:
        stone(x, 640, w, h, far, lean=s.rnd.uniform(-2, 2))
    stone(160, 650, 24, 96, "#10162A")
    stone(232, 650, 24, 96, "#10162A")
    s.path("M140,560 L252,554 L254,570 L142,574 Z", "#10162A")
    s.fill_below([(-10, 700), (100, 690), (200, 696), (300, 688), (W + 10, 698)], "#06080F")
    for (x, w, h, lean) in [(26, 48, 230, -5), (362, 52, 250, 5)]:
        stone(x, 790, w, h, "#06080F", lean=lean)
    s.raven(364, 548, 0.62, color="#06080F", left=True)
    s.raven(110, 340, 0.5, far=True, color="#0B0F1A")
    return s


def train():
    s = Scene(131)
    s.sky()
    moon = (300, 210, 40)
    s.stars(120, 0, 470, avoid=moon)
    s.moon(*moon)
    s.ridge(560, 46, FAR, step=24)
    s.haze(520, 140, "#1C2438", 0.45)
    s.soft_hills(650, 50, MID, waves=1.6, phase=2.3)
    # The viaduct: a deck on tall arches across the valley.
    c = "#070A12"
    deck = 560
    s.rect(-10, deck, W + 20, 10, c)
    for i in range(7):
        x = -20 + i * 64
        s.path(f"M{x},{H} L{x},{deck + 10} L{x + 64},{deck + 10} L{x + 64},{H} L{x + 54},{H} L{x + 54},{deck + 44} "
               f"A22,22 0 0 0 {x + 10},{deck + 44} L{x + 10},{H} Z", c)
    # The train: an engine and carriages with warm windows, its smoke trailing back.
    tx = 250
    s.path(f"M{tx},{deck} L{tx},{deck - 26} L{tx + 36},{deck - 26} L{tx + 36},{deck - 40} L{tx + 50},{deck - 40} L{tx + 50},{deck - 26} "
           f"L{tx + 64},{deck - 24} C{tx + 70},{deck - 20} {tx + 72},{deck - 8} {tx + 74},{deck} Z", c)
    s.rect(tx + 54, deck - 38, 6, 12, c)
    s.window_light(tx + 39, deck - 36, 8, 7, 0.85)
    for k in range(4):
        x = tx - 6 - (k + 1) * 58
        s.rect(x, deck - 28, 54, 28, c, rx=2)
        for j in range(5):
            s.window_light(x + 5 + j * 9.8, deck - 22, 6, 9, 0.9)
    for i in range(10):
        s.add(s.glow(tx + 57 - i * 26, deck - 54 - i * 5 - math.sin(i) * 4, 14 + i * 2.6, "#9AA3B6", 0.16 - i * 0.013))
    s.add(s.glow(tx - 120, deck + 8, 160, WARM, 0.05))
    s.fill_below([(-10, 760), (W + 10, 750)], "#05070D")
    return s


SCENES = {
    "lighthouse": ("Lighthouse", lighthouse), "ruins": ("Castle ruins", ruins), "cabin": ("Snowy cabin", cabin),
    "wolf": ("Wolf", wolf), "dunes": ("Dunes", dunes), "lantern": ("Lantern path", lantern),
    "peaks": ("Peaks", peaks), "oak": ("Old oak", oak), "stones": ("Standing stones", stones), "train": ("Night train", train),
}

if __name__ == "__main__":
    for key in (sys.argv[1:] or SCENES):
        open(f"still_{key}.svg", "w").write(SCENES[key][1]().svg())
