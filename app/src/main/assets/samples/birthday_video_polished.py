"""birthday_video_polished.py — XP sample #3
An 8 second birthday card: confetti, drifting balloons, a candle, and a name
that lands with a bounce. Everything is deterministic — no RNG state — so any
frame renders identically whether you scrub to it or export it.
"""
import numpy as np

from xp_api import ob, ease, stage, lerp_color

TITLE = "Birthday"
DURATION = 8.0
FPS = 30
SIZE = (1080, 1920)

CREAM = "#F2F0DC"
MINT = "#CDE0A5"
INK = "#1A202C"
PAPER = "#FBF9EE"
ROSE = "#E8A0A0"
SKY = "#A9C7E8"
SAND = "#E4D9B8"

NAME = "ARJUN"
CONFETTI_COLORS = [MINT, ROSE, SKY, INK, "#E8D9A0"]

ob("bg", kind="shape", label="Background")
ob("confetti", kind="shape", label="Confetti")
ob("balloons", kind="shape", label="Balloons")
ob("cake", kind="shape", label="Cake")
ob("greeting", kind="text", label="Greeting")
ob("name", kind="text", label="Name")
ob("wish", kind="text", label="Wish")

_N = 54


def _confetti():
    """Fixed particle table: (x0, speed, drift, phase, size, colour index)."""
    out = []
    s = 20240
    for i in range(_N):
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        x0 = (s % 1000) / 1000.0
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        speed = 0.05 + (s % 1000) / 1000.0 * 0.09
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        drift = 0.01 + (s % 1000) / 1000.0 * 0.03
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        phase = (s % 1000) / 1000.0 * 6.283
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        size = 0.006 + (s % 1000) / 1000.0 * 0.012
        out.append((x0, speed, drift, phase, size, i % len(CONFETTI_COLORS)))
    return out


PARTICLES = _confetti()


def render_frame(t, c):
    W, H = c.size
    intro = stage(t, 0.0, 0.8)

    c.bg(CREAM, gradient="#DCE8C4", name="bg")

    # confetti — falls, sways, wraps around
    for i, (x0, speed, drift, phase, size, ci) in enumerate(PARTICLES):
        y = (x0 * 0.6 + t * speed) % 1.12 - 0.06
        x = (x0 + np.sin(t * 0.9 + phase) * drift) % 1.0
        c.rect(x, y, size, size * 1.9, fill=CONFETTI_COLORS[ci], alpha=0.75 * intro,
               name="conf_%02d" % i)

    # balloons drifting up behind the card
    for i, (bx, col, ph) in enumerate(((0.14, ROSE, 0.0), (0.86, SKY, 1.4), (0.24, MINT, 2.6))):
        by = 0.62 - 0.10 * np.sin(t * 0.55 + ph) - 0.02 * t
        r = 0.055
        c.circle(bx, by, r, fill=col, alpha=0.95 * intro, name="balloon_%d" % i)
        c.poly([(bx - 0.012, by + r * 0.95), (bx + 0.012, by + r * 0.95), (bx, by + r * 1.25)],
               fill=col, alpha=intro, name="knot_%d" % i)
        c.line(bx, by + r * 1.2, bx + 0.02 * np.sin(t + ph), by + r * 2.4, color=INK,
               w=0.0018, alpha=0.35 * intro, name="string_%d" % i)

    # the cake
    cy = 0.735
    cake_in = stage(t, 0.35, 0.7, "back")
    c.rrect(0.30, cy + 0.028 * (1 - cake_in), 0.40, 0.075, r=0.02, fill=PAPER,
            stroke=INK, sw=0.0025, alpha=cake_in, shadow=0.8, name="cake")
    c.rrect(0.30, cy + 0.028 * (1 - cake_in), 0.40, 0.022, r=0.02, fill=MINT,
            alpha=cake_in, name="icing")
    c.rrect(0.425, cy - 0.028 + 0.028 * (1 - cake_in), 0.15, 0.060, r=0.014,
            fill=SAND, stroke=INK, sw=0.0022, alpha=cake_in, name="tier2")
    c.rrect(0.425, cy - 0.028 + 0.028 * (1 - cake_in), 0.15, 0.016, r=0.014,
            fill=MINT, alpha=cake_in, name="icing2")
    # candle + flame
    c.rect(0.4945, cy - 0.072 + 0.028 * (1 - cake_in), 0.011, 0.045, fill=ROSE,
           alpha=cake_in, name="candle")
    flick = 1.0 + 0.18 * np.sin(t * 12.0)
    c.circle(0.500, cy - 0.083 + 0.028 * (1 - cake_in), 0.0085 * flick,
             fill="#F6C86A", alpha=cake_in, name="flame")
    c.circle(0.500, cy - 0.086 + 0.028 * (1 - cake_in), 0.0042 * flick,
             fill=ROSE, alpha=cake_in, name="flame_core")

    # greeting, letter by letter
    letters = "HAPPY"
    for i, ch in enumerate(letters):
        p = stage(t, 0.25 + i * 0.055, 0.55, "back")
        c.ptag(ch, 0.5 + (i - 2) * 0.072, 0.215 - 0.02 * (1 - p), size=0.055,
               color=INK, weight="bold", alpha=min(1.0, p * 1.2), name="letter_%d" % i)
    p = stage(t, 0.62, 0.6, "back")
    c.ptag("BIRTHDAY", 0.5, 0.30, size=0.105, color=INK, weight="bold",
           tracking=0.04, maxw=0.88, alpha=min(1.0, p * 1.2), name="greeting")

    # name in a mint pill
    np_ = stage(t, 1.05, 0.75, "back")
    pill_w = 0.56 * ease(np_)
    c.rrect(0.5 - pill_w / 2, 0.395, max(0.02, pill_w), 0.062, r=0.5, fill=INK,
            alpha=min(1.0, np_ * 1.5), name="namepill")
    c.ptag(NAME, 0.5, 0.426, size=0.046, color=MINT, weight="bold", tracking=0.22,
           alpha=np_, name="name")

    # wish
    wp = stage(t, 1.7, 0.8)
    c.ptag("make a wish, %s" % NAME.title(), 0.5, 0.90, size=0.028, color=INK,
           family="lora", weight="italic", alpha=wp * 0.85, name="wish")

    # closing sparkle burst
    bt = t - 5.2
    if bt > 0:
        for i in range(10):
            ang = i * 0.628 + bt * 1.5
            rad = 0.10 + 0.22 * ease(min(1.0, bt / 1.6))
            sx = 0.5 + np.cos(ang) * rad
            sy = 0.30 + np.sin(ang) * rad * 0.7
            c.star(sx, sy, r=0.016, points=4, fill=CONFETTI_COLORS[i % 5],
                   rot=bt * 40, alpha=max(0.0, 1.0 - bt / 2.4), name="burst_%d" % i)
