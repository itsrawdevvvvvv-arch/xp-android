"""make_video.py — XP sample #1
A 6 second cream-and-mint title card. Every value here is a normalised 0..1
coordinate, so the same script renders at 540p, 720p or 1080p.

Edit anything in the timeline: the headline, the pill, the card colour, the
timing. XP injects your edits at the draw call — this file is never rewritten.
"""
import numpy as np

from xp_api import ob, AUDIT, ease, stage, lerp_color, stars

TITLE = "Make Something"
DURATION = 6.0
FPS = 30
SIZE = (1080, 1920)

CREAM = "#F2F0DC"
SAND = "#E4D9B8"
MINT = "#CDE0A5"
INK = "#1A202C"
PAPER = "#FBF9EE"

# --- declared intent: XP reads these to name timeline clips -----------------
ob("bg", kind="shape", label="Background", color=CREAM)
ob("stars", kind="shape", label="Star field", color=MINT)
ob("card", kind="shape", label="Mint card", color=MINT)
ob("pill", kind="shape", label="Pill", color=INK)
ob("pill_text", kind="text", label="Kicker", color=MINT)
ob("headline", kind="text", label="Headline", color=INK)
ob("rule", kind="shape", label="Rule", color=INK)
ob("caption", kind="text", label="Caption", color=INK)
ob("counter", kind="text", label="Counter", color=INK)
ob("bar", kind="shape", label="Progress", color=MINT)

STAR_FIELD = stars(26, seed=11)

_NOISE = {}

def _noise(w, h, seed=3):
    key = (w, h, seed)
    if key not in _NOISE:
        rng = np.random.default_rng(seed)
        base = rng.normal(0.5, 0.12, (h // 3, w // 3)).clip(0, 1)
        _NOISE[key] = np.dstack([base, base, base * 0.96, np.full_like(base, 0.09)])
    return _NOISE[key]


def render_frame(t, c):
    W, H = c.size
    intro = stage(t, 0.0, 1.1, "out")
    rise = stage(t, 0.05, 0.95, "back")

    # background: cream fading into sand, grain on top
    c.bg(CREAM, gradient=SAND, name="bg")
    c.blit(_noise(W, H, 3), 0, 0, 1, 1, fit="stretch", alpha=0.5, name="grain")

    # star field, twinkling
    for i, (sx, sy, sr) in enumerate(STAR_FIELD):
        tw = 0.35 + 0.65 * (0.5 + 0.5 * np.sin(t * 2.2 + i * 1.7))
        c.star(sx, sy * 0.92, r=sr * 1.6, points=4, fill=MINT, alpha=tw * intro,
               rot=t * 12 + i * 9, name="star_%02d" % i)

    # the card
    card_y = 0.30 - 0.06 * (1.0 - rise)
    c.rrect(0.075, card_y, 0.85, 0.42, r=0.055, fill=MINT, shadow=1.0,
            alpha=min(1.0, intro * 1.4), name="card")

    # kicker pill
    pill_in = stage(t, 0.28, 0.6, "back")
    px = 0.135 - 0.05 * (1.0 - pill_in)
    c.rrect(px, card_y + 0.055, 0.30, 0.052, r=0.5, fill=INK,
            alpha=pill_in, name="pill")
    c.ptag("XP STUDIO", px + 0.15, card_y + 0.081, size=0.021, color=MINT,
           weight="bold", tracking=0.28, alpha=pill_in, name="pill_text")

    # headline, two lines staggering up
    for i, line in enumerate(("MAKE", "SOMETHING")):
        p = stage(t, 0.34 + i * 0.10, 0.62, "back")
        c.ptag(line, 0.125, card_y + 0.175 + i * 0.083 - 0.035 * (1 - p),
               size=0.082, color=INK, weight="bold", align="left", anchor="ml", maxw=0.90,
               tracking=-0.02, alpha=min(1.0, p * 1.2), name="headline" if i == 0 else "headline2")

    # rule + caption
    rp = stage(t, 0.75, 0.5, "inout")
    c.rect(0.125, card_y + 0.345, 0.14 * rp, 0.004, fill=INK, alpha=0.85, name="rule")
    cp = stage(t, 0.85, 0.6)
    c.ptag("a python script, a timeline, a video",
           0.125 + 0.17, card_y + 0.338, size=0.0245, color=INK, family="lora",
           weight="italic", align="left", anchor="ml", maxw=0.92, alpha=cp * 0.85, name="caption")

    # counter + progress
    n = int(min(1.0, t / DURATION) * 100)
    c.ptag("%02d%%" % n, 0.845, 0.845, size=0.030, color=INK, weight="semibold",
           align="right", anchor="mr", alpha=stage(t, 1.0, 0.4), name="counter")
    c.progress(0.075, 0.865, 0.85, 0.008, value=t / DURATION, fill=INK,
               track="#DCD6B8", r=0.5, name="bar")

    # sign-off
    sp = stage(t, 1.35, 0.7)
    c.ptag("written in python  ·  edited in xp", 0.5, 0.925, size=0.020, color=INK,
           family="lora", weight="italic", alpha=sp * 0.6, name="footer")
