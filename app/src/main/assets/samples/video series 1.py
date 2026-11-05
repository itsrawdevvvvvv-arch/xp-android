"""video series 1.py — XP sample #2
A 10 second three-chapter opener for a video series. Shows the cv2 shim doing
real work: the scrolling ticker strip at the bottom is built with
cv2.putText / cv2.resize on a NumPy canvas, then blitted into the frame.
"""
import numpy as np

from xp_api import ob, ease, stage, lerp_color

try:
    import cv2
except Exception:
    cv2 = None

TITLE = "Series One"
DURATION = 10.0
FPS = 30
SIZE = (1080, 1920)

CREAM = "#F2F0DC"
MINT = "#CDE0A5"
INK = "#1A202C"
PAPER = "#FBF9EE"
ROSE = "#E8A0A0"

CHAPTERS = [
    (0.0, 3.2, "01", "THE SETUP", "one script, one idea", ROSE),
    (3.2, 6.4, "02", "THE SHOOT", "render it at any size", MINT),
    (6.4, 10.0, "03", "THE CUT", "edit without the code", "#A9C7E8"),
]

ob("bg", kind="shape", label="Background")
ob("card", kind="shape", label="Chapter card")
ob("numeral", kind="text", label="Chapter number")
ob("chapter", kind="text", label="Chapter title")
ob("body", kind="text", label="Chapter line")
ob("ticker", kind="image", label="Ticker strip")
ob("bar", kind="shape", label="Progress")
ob("series", kind="text", label="Series label")

_TICKER = {}


def _ticker_strip(w, h, label, color):
    key = (w, h, label, color)
    if key in _TICKER:
        return _TICKER[key]
    if cv2 is None:
        arr = np.full((h, w, 3), 26, np.uint8)
    else:
        arr = np.full((h, w, 3), 26, np.uint8)
        txt = "  ".join([label] * 6)
        arr = cv2.putText(arr, txt, (8, int(h * 0.72)), 0, h / 34.0, (165, 224, 205), 2, 16)
        # a second pass in mint keeps the strip legible at small sizes
        arr = cv2.resize(arr, (w, h), interpolation=cv2.INTER_LINEAR)
    _TICKER[key] = arr
    return arr


def _chapter_at(t):
    for (t0, t1, num, name, body, accent) in CHAPTERS:
        if t0 <= t < t1:
            return (t0, t1, num, name, body, accent)
    return CHAPTERS[-1]


def render_frame(t, c):
    W, H = c.size
    t0, t1, num, name, body, accent = _chapter_at(t)
    local = (t - t0) / max(0.001, (t1 - t0))
    enter = stage(t, t0, 0.55, "out")
    leave = 1.0 - stage(t, t1 - 0.42, 0.42, "in")

    c.bg(CREAM, gradient="#E9E5CC", name="bg")

    # series label, top left
    c.ptag("XP  /  SERIES 01", 0.08, 0.075, size=0.022, color=INK, weight="bold",
           align="left", anchor="ml", tracking=0.18, alpha=0.75, name="series")

    # the dark card slides in from the right each chapter
    dx = 0.10 * (1.0 - enter) + 0.06 * (1.0 - leave)
    card_y = 0.17
    c.rrect(0.07 + dx, card_y, 0.86, 0.46, r=0.05, fill=INK, shadow=1.0,
            alpha=min(1.0, enter * 1.6), name="card")

    # chapter number, huge, accent colour
    c.ptag(num, 0.125 + dx, card_y + 0.115, size=0.145, color=accent, weight="bold",
           align="left", anchor="ml", alpha=enter, name="numeral")

    # chapter title
    c.ptag(name, 0.125 + dx, card_y + 0.255, size=0.052, color=PAPER, weight="semibold",
           align="left", anchor="ml", tracking=0.06, maxw=0.92, alpha=enter, name="chapter")

    # body line
    bp = stage(t, t0 + 0.18, 0.5)
    c.ptag(body, 0.125 + dx, card_y + 0.325, size=0.027, color="#A8B0BF", family="lora",
           weight="italic", align="left", anchor="ml", maxw=0.9, alpha=bp, name="body")

    # accent rule that fills as the chapter plays
    c.rect(0.125 + dx, card_y + 0.375, 0.30 * ease(local), 0.005, fill=accent, name="rule")

    # ticker strip (cv2)
    strip = _ticker_strip(int(W * 1.2), int(H * 0.045), "  %s  " % name, accent)
    off = (t * 0.06) % 0.25
    c.blit(strip, -off, 0.70, 1.25, 0.045, fit="stretch", name="ticker")

    # chapter ticks + progress
    for i, (ct0, ct1, cn, _, _, ca) in enumerate(CHAPTERS):
        x = 0.08 + i * 0.285
        on = 1.0 if t >= ct0 else 0.35
        c.rrect(x, 0.83, 0.26, 0.012, r=0.5, fill=ca if t >= ct0 else "#D8D3B7",
                alpha=on, name="tick_%d" % i)
        c.ptag(cn, x, 0.80, size=0.018, color=INK, weight="semibold", align="left",
               anchor="ml", alpha=on * 0.8, name="ticklabel_%d" % i)

    c.progress(0.08, 0.885, 0.84, 0.007, value=t / DURATION, fill=INK, track="#DCD6B8",
               r=0.5, name="bar")
    c.ptag("%.1fs" % t, 0.92, 0.795, size=0.018, color=INK, weight="medium",
           align="right", anchor="mr", alpha=0.55, name="clock")
