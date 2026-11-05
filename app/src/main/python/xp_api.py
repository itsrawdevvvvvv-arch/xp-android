"""xp_api — the small helper surface XP gives your scripts.

Scripts declare intent here (AUDIT / ob) and XP turns that into the scene
graph, the timeline labels and the property sheet. Nothing in here draws.
"""

AUDIT = {}


def ob(name, kind="shape", label=None, note="", color=None, track=None, **kw):
    """Declare a scene object. Call it once, at import time, or every frame."""
    AUDIT[str(name)] = {
        "kind": kind,
        "label": label or str(name).replace("_", " ").title(),
        "note": note,
        "color": color,
        "track": track,
    }
    return name


def scene(**kw):
    AUDIT.setdefault("__scene__", {}).update(kw)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def lerp(a, b, t):
    return a + (b - a) * t


def ease(t, mode="out"):
    t = clamp(t)
    if mode == "in":
        return t * t
    if mode == "inout":
        return t * t * (3 - 2 * t)
    if mode == "back":
        c = 1.70158
        return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2
    if mode == "linear":
        return t
    return 1 - (1 - t) ** 3


def stage(t, t0, dur, mode="out"):
    """0..1 progress of a sub-animation that starts at t0 and lasts dur."""
    if dur <= 0:
        return 1.0 if t >= t0 else 0.0
    return ease((t - t0) / dur, mode)


def lerp_color(a, b, t):
    """Hex colour interpolation in plain sRGB (XP's Kotlin side uses OKLab)."""
    t = clamp(t)
    a = a.lstrip("#")
    b = b.lstrip("#")
    out = []
    for i in (0, 2, 4):
        va = int(a[i:i + 2], 16)
        vb = int(b[i:i + 2], 16)
        out.append(int(round(va + (vb - va) * t)))
    return "#%02X%02X%02X" % tuple(out)


def stagger(i, n, t, start=0.0, step=0.06):
    return stage(t, start + i * step, 0.5)


def stars(n=18, seed=7, w=1.0, h=1.0):
    """Deterministic star field — same layout every run, no RNG drift."""
    s = seed
    out = []
    for _ in range(int(n)):
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        x = (s % 10000) / 10000.0
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        y = (s % 10000) / 10000.0
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        r = 0.006 + (s % 1000) / 1000.0 * 0.012
        out.append((x * w, y * h, r))
    return out


CREAM = "#F2F0DC"
MINT = "#CDE0A5"
INK = "#1A202C"
PAPER = "#FBF9EE"
ROSE = "#E8A0A0"
SKY = "#A9C7E8"
