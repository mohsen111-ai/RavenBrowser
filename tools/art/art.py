# Raven's night wallpapers, drawn as SVG in the 390 x 844 frame the old ones use, then rendered at 1080 x 2337.
# Shared pieces: the sky, stars, moons and their glow, hills, pines, bare trees, ravens, haze.
import math
import random

W, H = 390, 844

# The old wallpapers' colours.
SKY_TOP = "#04060B"
SKY_HIGH = "#070A13"
SKY_MID = "#0A1020"
SKY_LOW = "#0E1528"
GROUND = "#05070D"
FAR = "#151C2E"
MID = "#0E1422"
NEAR = "#080B14"
MOON_LIGHT = "#E4E8EF"
MOON = "#CDD3DC"
MOON_EDGE = "#AEB6C4"
WARM = "#E8B26A"


class Scene:
    def __init__(self, seed):
        self.parts = []
        self.defs = []
        self.rnd = random.Random(seed)
        self.ids = 0

    def id(self, base):
        self.ids += 1
        return f"{base}{self.ids}"

    def add(self, s):
        self.parts.append(s)

    def svg(self):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
                f'<defs>{"".join(self.defs)}</defs>{"".join(self.parts)}</svg>')

    # ------------------------------------------------------------------ sky

    def sky(self, top=SKY_TOP, high=SKY_HIGH, mid=SKY_MID, low=SKY_LOW, horizon=0.62):
        g = self.id("sky")
        self.defs.append(
            f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0" stop-color="{top}"/><stop offset="0.28" stop-color="{high}"/>'
            f'<stop offset="{horizon * 0.85:.2f}" stop-color="{mid}"/><stop offset="{horizon:.2f}" stop-color="{low}"/>'
            f'<stop offset="1" stop-color="{GROUND}"/></linearGradient>')
        self.add(f'<rect width="{W}" height="{H}" fill="url(#{g})"/>')

    def stars(self, n=90, top=0, bottom=460, avoid=None, bright=0.9, big=6):
        """Stars of every brightness; [avoid]: (x, y, r) of the moon, kept clear."""
        out = []
        for i in range(n):
            x = self.rnd.uniform(4, W - 4)
            y = self.rnd.uniform(top, bottom) if self.rnd.random() < 0.8 else self.rnd.uniform(top, top + (bottom - top) * 0.45)
            if avoid and (x - avoid[0]) ** 2 + (y - avoid[1]) ** 2 < (avoid[2] * 1.7) ** 2:
                continue
            r = self.rnd.choice([0.45, 0.5, 0.6, 0.7, 0.8, 1.0])
            a = self.rnd.uniform(0.18, bright) * (1 - 0.35 * (y - top) / max(1, bottom - top))
            out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r}" fill="#FFFFFF" opacity="{a:.2f}"/>')
        for i in range(big):
            x = self.rnd.uniform(16, W - 16)
            y = self.rnd.uniform(top + 20, top + (bottom - top) * 0.7)
            if avoid and (x - avoid[0]) ** 2 + (y - avoid[1]) ** 2 < (avoid[2] * 2) ** 2:
                continue
            out.append(self.glow(x, y, 4.5, "#FFFFFF", 0.22) + f'<circle cx="{x:.1f}" cy="{y:.1f}" r="1.25" fill="#FFFFFF" opacity="0.95"/>')
        self.add("".join(out))

    def glow(self, x, y, r, color, a, inner=0.0):
        g = self.id("glow")
        self.defs.append(
            f'<radialGradient id="{g}"><stop offset="{inner}" stop-color="{color}" stop-opacity="{a}"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')
        return f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="url(#{g})"/>'

    def moon(self, x, y, r, halo=2.6, halo_a=0.16, craters=True):
        g = self.id("moon")
        self.defs.append(
            f'<radialGradient id="{g}" cx="0.42" cy="0.38" r="0.7"><stop offset="0" stop-color="{MOON_LIGHT}"/>'
            f'<stop offset="0.7" stop-color="{MOON}"/><stop offset="1" stop-color="{MOON_EDGE}"/></radialGradient>')
        s = self.glow(x, y, r * halo, "#C7CCD8", halo_a, inner=0.25)
        s += self.glow(x, y, r * 1.35, "#DDE2EA", 0.18, inner=0.6)
        s += f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#{g})"/>'
        if craters:
            rnd = random.Random(int(x * 7 + y))
            for (dx, dy, cr) in [(-0.35, -0.25, 0.17), (0.18, 0.25, 0.22), (0.3, -0.35, 0.1), (-0.15, 0.42, 0.09), (0.45, 0.1, 0.07)]:
                s += f'<circle cx="{x + dx * r:.1f}" cy="{y + dy * r:.1f}" r="{cr * r:.1f}" fill="#9EA6B5" opacity="{rnd.uniform(0.18, 0.28):.2f}"/>'
        self.add(s)

    def crescent(self, x, y, r, phase=0.42, angle=-25, halo_a=0.14):
        """A crescent: the moon with a darker disc taken out of it, [phase] of the radius over."""
        m = self.id("cres")
        cx = x + math.cos(math.radians(angle)) * r * phase * 2
        cy = y + math.sin(math.radians(angle)) * r * phase * 2
        self.defs.append(f'<mask id="{m}"><rect width="{W}" height="{H}" fill="#fff"/><circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r * 1.02:.1f}" fill="#000"/></mask>')
        g = self.id("moon")
        self.defs.append(
            f'<radialGradient id="{g}" cx="0.3" cy="0.6" r="0.8"><stop offset="0" stop-color="{MOON_LIGHT}"/>'
            f'<stop offset="1" stop-color="{MOON}"/></radialGradient>')
        s = self.glow(x, y, r * 2.4, "#C7CCD8", halo_a, inner=0.3)
        s += f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#{g})" mask="url(#{m})"/>'
        self.add(s)

    def haze(self, y, h, color="#1C2438", a=0.45):
        g = self.id("haze")
        self.defs.append(
            f'<linearGradient id="{g}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{color}" stop-opacity="0"/>'
            f'<stop offset="0.55" stop-color="{color}" stop-opacity="{a}"/><stop offset="1" stop-color="{color}" stop-opacity="0"/></linearGradient>')
        self.add(f'<rect x="0" y="{y}" width="{W}" height="{h}" fill="url(#{g})"/>')

    def fill_below(self, points, color, opacity=1.0):
        pts = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
        self.add(f'<polygon points="{pts} {W},{H} 0,{H}" fill="{color}" opacity="{opacity}"/>')

    def ridge(self, y, rough, color, step=18, seed_shift=0, start=-10, end=None, opacity=1.0):
        """A jagged line of hills at height [y], [rough] tall, filled down to the bottom."""
        end = W + 10 if end is None else end
        pts = []
        x = start
        while x <= end:
            pts.append((x, y - self.rnd.uniform(0, rough)))
            x += step * self.rnd.uniform(0.6, 1.4)
        pts.append((end, y))
        self.fill_below([(start, H)] + pts + [(end, H)], color, opacity)

    def soft_hills(self, y, amp, color, waves=2.0, phase=0.0, opacity=1.0):
        pts = [(x, y - amp * (0.5 + 0.5 * math.sin(x / W * math.pi * waves + phase))) for x in range(-10, W + 12, 6)]
        self.fill_below(pts, color, opacity)

    # ------------------------------------------------------------------ things

    def pine(self, x, base, h, color, w=None):
        w = w or h * 0.36
        tiers = max(3, int(h / 14))
        d = f"M{x - w * 0.06:.1f},{base} L{x - w * 0.06:.1f},{base - h * 0.08:.1f} "
        pts = []
        for i in range(tiers):
            t = i / tiers
            yy = base - h * 0.08 - (h * 0.92) * t
            ww = w * (1 - t) * 0.5 + 1.2
            pts.append((x - ww, yy))
            pts.append((x - ww * 0.45, yy - h * 0.92 / tiers * 0.55))
        left = " ".join(f"L{px:.1f},{py:.1f}" for px, py in pts)
        right = " ".join(f"L{2 * x - px:.1f},{py:.1f}" for px, py in reversed(pts))
        d += left + f" L{x:.1f},{base - h:.1f} " + right + f" L{x + w * 0.06:.1f},{base - h * 0.08:.1f} L{x + w * 0.06:.1f},{base} Z"
        self.add(f'<path d="{d}" fill="{color}"/>')

    def forest(self, base, n, hmin, hmax, color, x0=-10, x1=None, jitter=10):
        x1 = W + 10 if x1 is None else x1
        for i in range(n):
            x = x0 + (x1 - x0) * (i + self.rnd.uniform(0.1, 0.9)) / n
            self.pine(x, base + self.rnd.uniform(0, jitter), self.rnd.uniform(hmin, hmax), color)

    def branch(self, x, y, angle, length, width, depth, color, out=None, tips=None):
        """A bare tree's branch, splitting into smaller ones (drawn as tapering strokes)."""
        x2 = x + math.cos(math.radians(angle)) * length
        y2 = y + math.sin(math.radians(angle)) * length
        bend = self.rnd.uniform(-0.25, 0.25) * length
        mx = (x + x2) / 2 + math.cos(math.radians(angle + 90)) * bend
        my = (y + y2) / 2 + math.sin(math.radians(angle + 90)) * bend
        (out if out is not None else self.parts).append(
            f'<path d="M{x:.1f},{y:.1f} Q{mx:.1f},{my:.1f} {x2:.1f},{y2:.1f}" stroke="{color}" stroke-width="{width:.2f}" stroke-linecap="round" fill="none"/>')
        if depth <= 0:
            if tips is not None:
                tips.append((x2, y2, angle))
            return
        for d_ang in self.rnd.sample([-34, -22, -12, 14, 26, 38], self.rnd.choice([2, 2, 3])):
            self.branch(x2, y2, angle + d_ang + self.rnd.uniform(-6, 6), length * self.rnd.uniform(0.62, 0.8), width * 0.66, depth - 1, color, out, tips)

    # A raven sitting, facing right: heavy beak, shaggy throat, long wedge tail. About 46 x 52 at scale 1, its feet at
    # the bottom middle.
    PERCHED = ("M26,6 C30,3 35,3.5 38,6.6 L48.5,10.4 C45.5,12.4 41.5,13.2 38.4,13.6 L39,15.4 L37,15.8 L37.8,18 "
               "L35.6,18.4 L36.4,20.8 C36.8,27 34.2,32.2 29.6,35.6 L28.6,40.5 L30.4,45 L28.8,45 L26.8,41 L25.4,41.2 "
               "L26,45.2 L24.4,45.2 L23.2,40.6 L21,39.2 L9.5,47.4 L5,47.6 L7.6,43.8 L13.2,36 C10.8,28 13.4,16.8 20.5,10.6 "
               "C22,8.6 24,7.2 26,6 Z")
    PERCHED_SIZE = (50, 45)
    # A raven gliding, seen from below, pointing up: fingered wing tips and the wedge tail. 64 x 38.
    SOARING = ("M32,0 L33.6,3 C35.2,4 35.8,6.5 35.6,9 C40,9.5 46,9 52,8 L58,7.5 L62,8.5 L57.5,10 L63,11.2 L57.8,12.4 "
               "L62,14.2 L56.8,14.6 L59.5,17 L54,16.2 C48,17 42,18.5 37,20 L36.5,23 L40,33 L32,37.5 L24,33 L27.5,23 "
               "L27,20 C22,18.5 16,17 10,16.2 L4.5,17 L7.2,14.6 L2,14.2 L6.2,12.4 L1,11.2 L6.5,10 L2,8.5 L6,7.5 "
               "L12,8 C18,9 24,9.5 28.4,9 C28.2,6.5 28.8,4 30.4,3 Z")
    SOARING_SIZE = (64, 19)
    # A raven far away, wings raised: a few strokes.
    DISTANT = ("M0,4 C4,2 8,2.5 11,6 C12,6.5 13,6.5 14,6 C17,2.5 21,2 25,4 C21,4 17,5.5 14.4,8.4 L13.2,10 L12.2,8.4 "
               "C9,5.5 5,4 0,4 Z")
    DISTANT_SIZE = (25, 7)

    def raven(self, x, y, scale=1.0, color=NEAR, left=False, flying=False, far=False, angle=0):
        """Perched: (x, y) is where its feet are. Flying: its middle."""
        if far:
            d, (w, h) = self.DISTANT, self.DISTANT_SIZE
        elif flying:
            d, (w, h) = self.SOARING, self.SOARING_SIZE
        else:
            d, (w, h) = self.PERCHED, self.PERCHED_SIZE
        flip = -1 if left else 1
        self.add(f'<g transform="translate({x:.1f},{y:.1f}) rotate({angle}) scale({flip * scale:.3f},{scale:.3f}) translate({-w / 2:.1f},{-h:.1f})">'
                 f'<path d="{d}" fill="{color}"/></g>')

    def soft(self, cx, cy, rx, ry, color, a, angle=0):
        """A soft-edged patch (cloud, mist, the Milky Way): an ellipse fading out to its edge. No blur filter, which bands
        dark colours into purple and teal."""
        g = self.id("soft")
        self.defs.append(f'<radialGradient id="{g}"><stop offset="0" stop-color="{color}" stop-opacity="{a}"/>'
                         f'<stop offset="0.55" stop-color="{color}" stop-opacity="{a * 0.6:.3f}"/><stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')
        self.add(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="url(#{g})" transform="rotate({angle} {cx} {cy})"/>')

    def blur(self, sd):
        f = self.id("blur")
        self.defs.append(f'<filter id="{f}" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="{sd}"/></filter>')
        return f

    def trunk(self, x, base, top, w, color, lean=0.0):
        """A tree trunk: wider at the roots, a little crooked."""
        mid = (base + top) / 2
        self.add(f'<path d="M{x - w * 0.9:.1f},{base} C{x - w * 0.5:.1f},{base - 20} {x - w * 0.5 + lean * 0.5:.1f},{mid} {x - w * 0.42 + lean:.1f},{top} '
                 f'L{x + w * 0.42 + lean:.1f},{top} C{x + w * 0.5 + lean * 0.5:.1f},{mid} {x + w * 0.5:.1f},{base - 20} {x + w * 0.9:.1f},{base} Z" fill="{color}"/>')

    def rect(self, x, y, w, h, color, opacity=1.0, rx=0):
        self.add(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" fill="{color}" opacity="{opacity}"/>')

    def path(self, d, color, opacity=1.0, extra=""):
        self.add(f'<path d="{d}" fill="{color}" opacity="{opacity}" {extra}/>')

    def window_light(self, x, y, w, h, a=1.0):
        """A small warm window, with its glow."""
        self.add(self.glow(x + w / 2, y + h / 2, max(w, h) * 2.2, WARM, 0.28 * a))
        self.rect(x, y, w, h, "#F2C98A", a)
