"""XP canvas — a resolution-independent drawing surface with edit injection.

Every primitive takes normalised coordinates (0..1 of the frame), so a script
authored once renders at 540p, 720p or 1080p.  Every call also records a scene
node (kind, bbox, colour, text, time range) which the editor turns into a
timeline.  Edits are injected here, at the draw call, so the user's .py source
is never modified.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

CREAM = "#F2F0DC"
MINT = "#CDE0A5"
INK = "#1A202C"
PAPER = "#FBF9EE"
ROSE = "#E8A0A0"
SKY = "#A9C7E8"
SAND = "#E4D9B8"

NAMED = {
    "cream": CREAM, "mint": MINT, "ink": INK, "paper": PAPER,
    "rose": ROSE, "sky": SKY, "sand": SAND, "white": "#FFFFFF",
    "black": "#000000", "transparent": "#00000000",
}

_FONT_FILES = {
    ("poppins", "regular"): "poppins_regular.ttf",
    ("poppins", "medium"): "poppins_medium.ttf",
    ("poppins", "semibold"): "poppins_semibold.ttf",
    ("poppins", "bold"): "poppins_bold.ttf",
    ("lora", "regular"): "lora_variable.ttf",
    ("lora", "italic"): "lora_italic_variable.ttf",
}

_font_cache = {}
FONTS_DIR = os.environ.get("XP_FONTS_DIR", "")


def set_fonts_dir(path):
    global FONTS_DIR
    FONTS_DIR = path or ""


def font(family="poppins", weight="semibold", size=48):
    key = (str(family).lower(), str(weight).lower(), int(size))
    if key in _font_cache:
        return _font_cache[key]
    fam = key[0]
    if fam not in ("poppins", "lora"):
        fam = "poppins"
    weight = key[1]
    if fam == "lora":
        weight = "italic" if "italic" in weight else "regular"
    elif weight not in ("regular", "medium", "semibold", "bold"):
        weight = "semibold"
    fname = _FONT_FILES.get((fam, weight), "poppins_semibold.ttf")
    path = os.path.join(FONTS_DIR, fname) if FONTS_DIR else ""
    try:
        f = ImageFont.truetype(path, int(size)) if path and os.path.exists(path) else ImageFont.load_default()
    except Exception:
        f = ImageFont.load_default()
    _font_cache[key] = f
    return f


def parse_color(c, alpha=1.0):
    """Accept '#rgb', '#rrggbb', '#rrggbbaa', a name, or an (r,g,b[,a]) tuple."""
    if c is None:
        c = INK
    if isinstance(c, (tuple, list)):
        vals = list(c)
        if len(vals) == 4:
            alpha = float(vals[3]) if alpha == 1.0 else alpha
        vals = vals[:3]
        if all(isinstance(v, (int, np.integer)) and v > 1 for v in vals):
            r, g, b = [int(v) for v in vals]
        else:
            r, g, b = [int(round(float(v) * 255)) for v in vals]
        return (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)),
                max(0, min(255, int(round(alpha * 255)))))
    s = str(c).strip()
    low = s.lower()
    if low in NAMED:
        s = NAMED[low]
    s = s.lstrip("#")
    if len(s) == 3:
        s = "".join(ch * 2 for ch in s)
    if len(s) == 8:
        a = int(s[6:8], 16) / 255.0
        s = s[:6]
        alpha = alpha * a
    if len(s) != 6:
        s = "1A202C"
    r = int(s[0:2], 16)
    g = int(s[2:4], 16)
    b = int(s[4:6], 16)
    return (r, g, b, max(0, min(255, int(round(alpha * 255)))))


def hex_of(color):
    r, g, b = color[0], color[1], color[2]
    return "#%02X%02X%02X" % (int(r), int(g), int(b))


def to_pil(img):
    if isinstance(img, Image.Image):
        return img.convert("RGBA")
    if isinstance(img, np.ndarray):
        a = img
        if a.dtype != np.uint8:
            m = float(np.max(a)) if a.size else 0.0
            a = (np.clip(a, 0, 1) * 255).astype(np.uint8) if m <= 1.5 else np.clip(a, 0, 255).astype(np.uint8)
        if a.ndim == 2:
            return Image.fromarray(a, "L").convert("RGBA")
        if a.shape[2] == 4:
            return Image.fromarray(a, "RGBA")
        return Image.fromarray(a[:, :, :3], "RGB").convert("RGBA")
    if isinstance(img, str) and os.path.exists(img):
        return Image.open(img).convert("RGBA")
    raise TypeError("blit() wants a numpy array, a PIL image or a file path")


_CACHE = {}
_CACHE_MAX = 48


def _cached(key, build):
    hit = _CACHE.get(key)
    if hit is not None:
        return hit
    val = build()
    if len(_CACHE) > _CACHE_MAX:
        _CACHE.clear()
    _CACHE[key] = val
    return val


def _resample_for(src, dst):
    """Cheap resampling: exact-size copies skip work, big downscales use BOX."""
    sw, sh = src
    dw, dh = dst
    ratio = (dw * dh) / float(max(1, sw * sh))
    if ratio < 0.45:
        return Image.BOX
    if ratio < 1.0:
        return Image.BILINEAR
    return Image.BICUBIC


def _lerp(a, b, t):
    return a + (b - a) * t


def _ease(t, mode="out"):
    t = max(0.0, min(1.0, t))
    if mode == "in":
        return t * t
    if mode == "inout":
        return t * t * (3 - 2 * t)
    if mode == "linear":
        return t
    if mode == "back":
        c = 1.70158
        return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2
    return 1 - (1 - t) ** 3


def _fit(pil, tw, th, fit, radius):
    try:
        if fit == "stretch":
            out = pil.copy() if pil.size == (tw, th) else pil.resize((tw, th), _resample_for(pil.size, (tw, th)))
        else:
            sw_, sh_ = pil.size
            s = max(tw / sw_, th / sh_) if fit == "cover" else min(tw / sw_, th / sh_)
            nw, nh = max(1, int(sw_ * s)), max(1, int(sh_ * s))
            resized = pil if pil.size == (nw, nh) else pil.resize((nw, nh), _resample_for(pil.size, (nw, nh)))
            out = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
            out.paste(resized, ((tw - nw) // 2, (th - nh) // 2), resized)
        if radius and radius > 0:
            mask = Image.new("L", (tw, th), 0)
            ImageDraw.Draw(mask).rounded_rectangle([0, 0, tw - 1, th - 1],
                                                   radius=int(min(tw, th) * float(radius)), fill=255)
            out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", (tw, th), 0), mask))
        return out
    except Exception:
        return None


def _shadow(w, h, rad, off, blur_r, alpha_i):
    pad = int(blur_r * 2.5) + 6
    sh = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([pad, pad + off, pad + w - 1, pad + h - 1 + off],
                                         radius=rad, fill=(26, 32, 44, alpha_i))
    return sh.filter(ImageFilter.GaussianBlur(blur_r))


def _gradient(w, h, top, bottom):
    g = np.linspace(0, 1, h, dtype=np.float32)[:, None]
    arr = np.zeros((h, w, 4), dtype=np.uint8)
    for i in range(3):
        arr[:, :, i] = (top[i] + (bottom[i] - top[i]) * g).astype(np.uint8)
    arr[:, :, 3] = 255
    return Image.fromarray(arr, "RGBA")


class Canvas:
    """One frame of video."""

    DEFAULT_PROPS = {
        "dx": 0.0, "dy": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0,
        "alpha": 1.0, "color": None, "text": None, "t0": None, "t1": None,
        "visible": True,
    }

    def __init__(self, width, height, t=0.0, duration=1.0, edits=None, record=True):
        self.W = int(width)
        self.H = int(height)
        self.t = float(t)
        self.duration = float(duration) if duration else 1.0
        self.img = Image.new("RGBA", (self.W, self.H), parse_color(CREAM))
        self.edits = edits or {}
        self.record = record
        self.nodes = []
        self._seq = 0
        self._bg_drawn = False

    # ---------------------------------------------------------------- geometry
    @property
    def size(self):
        return (self.W, self.H)

    def px(self, v, axis="x"):
        return float(v) * (self.W if axis == "x" else self.H)

    # ------------------------------------------------------------------- edits
    def _key(self, name, kind, text=None, box=None):
        if name:
            return str(name)
        self._seq += 1
        if text:
            return "%s~%s" % (kind, str(text)[:28])
        if box is not None:
            return "%s~%d,%d,%dx%d" % (kind, round(box[0] * 100), round(box[1] * 100),
                                       round(box[2] * 100), round(box[3] * 100))
        return "%s~%d" % (kind, self._seq)

    def _props(self, key):
        """Merge the user's static edits with any keyframes, at the current time."""
        base = dict(self.DEFAULT_PROPS)
        e = self.edits.get(key)
        if not e:
            return base
        for k, v in (e.get("props") or {}).items():
            if k in base and v is not None:
                base[k] = v
        kfs = e.get("kf") or []
        if kfs:
            kfs = sorted(kfs, key=lambda f: float(f.get("t", 0.0)))
            t = self.t
            if t <= float(kfs[0].get("t", 0.0)):
                cur = kfs[0].get("p") or {}
            elif t >= float(kfs[-1].get("t", 0.0)):
                cur = kfs[-1].get("p") or {}
            else:
                cur = {}
                for i in range(len(kfs) - 1):
                    a, b = kfs[i], kfs[i + 1]
                    ta, tb = float(a.get("t", 0.0)), float(b.get("t", 0.0))
                    if ta <= t <= tb and tb > ta:
                        u = _ease((t - ta) / (tb - ta), b.get("ease", "inout"))
                        pa, pb = a.get("p") or {}, b.get("p") or {}
                        for k in set(list(pa.keys()) + list(pb.keys())):
                            va, vb = pa.get(k), pb.get(k)
                            if va is None:
                                va = vb
                            if vb is None:
                                vb = va
                            if isinstance(va, (int, float)) and isinstance(vb, (int, float)):
                                cur[k] = _lerp(float(va), float(vb), u)
                            else:
                                cur[k] = vb if u > 0.5 else va
                        break
            for k, v in cur.items():
                if k in base and v is not None:
                    base[k] = v
        if base.get("t0") is not None and self.t < float(base["t0"]) - 1e-6:
            base["visible"] = False
        if base.get("t1") is not None and self.t > float(base["t1"]) + 1e-6:
            base["visible"] = False
        return base

    def _place(self, box, p):
        x, y, w, h = box
        cx = x + w / 2.0
        cy = y + h / 2.0
        w = max(1.0, w * float(p["sx"]))
        h = max(1.0, h * float(p["sy"]))
        x = cx - w / 2.0 + float(p["dx"]) * self.W
        y = cy - h / 2.0 + float(p["dy"]) * self.H
        return x, y, w, h

    def _put(self, layer, x, y, p, rot=None):
        a = float(p["alpha"])
        if a < 0.999:
            al = layer.getchannel("A").point(lambda v: int(v * a))
            layer.putalpha(al)
        rot = float(p["rot"]) if rot is None else rot
        if abs(rot) > 0.01:
            bw, bh = layer.width, layer.height
            layer = layer.rotate(rot, resample=Image.BICUBIC, expand=True)
            x -= (layer.width - bw) / 2.0
            y -= (layer.height - bh) / 2.0
        self.img.paste(layer, (int(round(x)), int(round(y))), layer)

    def _record(self, key, kind, label, box_n, color, text=None):
        if not self.record:
            return
        self.nodes.append({
            "id": key, "kind": kind, "label": label,
            "bbox": [round(float(v), 4) for v in box_n],
            "color": hex_of(color) if color else None,
            "text": text, "t": round(self.t, 3),
        })

    def _norm_box(self, x, y, w, h):
        return [x / self.W, y / self.H, w / self.W, h / self.H]

    # -------------------------------------------------------------- primitives
    def bg(self, color=CREAM, gradient=None, name="background"):
        key = self._key(name, "shape")
        p = self._props(key)
        if not p["visible"]:
            return
        col = parse_color(p["color"] or color)
        if gradient:
            top = parse_color(gradient)
            self.img = _cached(("grad", self.W, self.H, top, col),
                               lambda: _gradient(self.W, self.H, top, col)).copy()
        else:
            if not self._bg_drawn or self.img.size != (self.W, self.H):
                self.img = Image.new("RGBA", (self.W, self.H), col)
            else:
                self.img.paste(col, (0, 0, self.W, self.H))
        self._bg_drawn = True
        self._record(key, "shape", "background", [0, 0, 1, 1], col)

    def fill(self, color=CREAM):
        self.img.paste(Image.new("RGBA", (self.W, self.H), parse_color(color)), (0, 0))

    def rrect(self, x, y, w, h, r=0.04, fill=MINT, stroke=None, sw=0.003,
              alpha=1.0, shadow=0.0, name=None, rot=0.0, **kw):
        box = (self.px(x), self.px(y, "y"), self.px(w), self.px(h, "y"))
        key = self._key(name, "shape", box=box)
        p = self._props(key)
        p["rot"] = rot if p["rot"] == 0.0 else p["rot"]
        if not p["visible"]:
            return
        x, y, w, h = self._place(box, p)
        p["alpha"] = float(p["alpha"]) * float(alpha)
        col = parse_color(p["color"] or fill)
        rad = int(min(w, h) * max(0.0, min(0.5, float(r))))
        layer = Image.new("RGBA", (max(1, int(w)), max(1, int(h))), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.rounded_rectangle([0, 0, layer.width - 1, layer.height - 1], radius=rad, fill=col)
        if stroke:
            d.rounded_rectangle([0, 0, layer.width - 1, layer.height - 1], radius=rad,
                                outline=parse_color(stroke), width=max(1, int(self.px(sw))))
        if shadow and shadow > 0:
            off = int(self.px(0.012, "y"))
            blur_r = self.px(0.014, "y")
            alpha_i = int(60 * float(shadow))
            skey = ("shadow", layer.width, layer.height, rad, off, round(blur_r, 2), alpha_i)
            sh = _cached(skey, lambda: _shadow(layer.width, layer.height, rad, off, blur_r, alpha_i))
            pad = int(blur_r * 2.5) + 6
            self.img.paste(sh, (int(x) - pad, int(y) - pad), sh)
        self._put(layer, x, y, p)
        self._record(key, "shape", name or "card", self._norm_box(x, y, w, h), col)

    def rect(self, x, y, w, h, fill=MINT, **kw):
        return self.rrect(x, y, w, h, r=0, fill=fill, **kw)

    def circle(self, x, y, r, fill=MINT, alpha=1.0, name=None, **kw):
        rr = self.px(r, "y")
        box = (self.px(x) - rr, self.px(y, "y") - rr, rr * 2, rr * 2)
        key = self._key(name, "shape", box=box)
        p = self._props(key)
        if not p["visible"]:
            return
        bx, by, bw, bh = self._place(box, p)
        p["alpha"] = float(p["alpha"]) * float(alpha)
        col = parse_color(p["color"] or fill)
        layer = Image.new("RGBA", (max(1, int(bw)), max(1, int(bh))), (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse([0, 0, layer.width - 1, layer.height - 1], fill=col)
        self._put(layer, bx, by, p)
        self._record(key, "shape", name or "circle", self._norm_box(bx, by, bw, bh), col)

    def line(self, x1, y1, x2, y2, color=INK, w=0.004, alpha=1.0, name=None, **kw):
        key = self._key(name, "shape")
        p = self._props(key)
        if not p["visible"]:
            return
        col = parse_color(p["color"] or color, float(alpha) * float(p["alpha"]))
        lw = max(1, int(self.px(w, "y")))
        d = ImageDraw.Draw(self.img)
        d.line([self.px(x1), self.px(y1, "y"), self.px(x2), self.px(y2, "y")], fill=col, width=lw)
        xa, ya = min(self.px(x1), self.px(x2)), min(self.px(y1, "y"), self.px(y2, "y"))
        wa, ha = abs(self.px(x2) - self.px(x1)) + lw, abs(self.px(y2, "y") - self.px(y1, "y")) + lw
        self._record(key, "shape", name or "line", self._norm_box(xa, ya, wa, ha), col)

    def star(self, x, y, r=0.06, points=4, fill=MINT, rot=0.0, alpha=1.0, name=None, inner=0.22, **kw):
        cx, cy = self.px(x), self.px(y, "y")
        rr = self.px(r, "y")
        box = (cx - rr, cy - rr, rr * 2, rr * 2)
        key = self._key(name, "shape", box=box)
        p = self._props(key)
        p["rot"] = rot if p["rot"] == 0.0 else p["rot"]
        if not p["visible"]:
            return
        bx, by, bw, bh = self._place(box, p)
        p["alpha"] = float(p["alpha"]) * float(alpha)
        col = parse_color(p["color"] or fill)
        layer = Image.new("RGBA", (max(2, int(bw)), max(2, int(bh))), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        cxm, cym = layer.width / 2.0, layer.height / 2.0
        rad0 = min(layer.width, layer.height) / 2.0
        ang0 = math.radians(float(p["rot"]) - 90.0)
        pts = []
        n = int(points) * 2
        for i in range(n):
            ang = ang0 + i * (2 * math.pi / n)
            rad = rad0 if i % 2 == 0 else rad0 * float(inner)
            pts.append((cxm + math.cos(ang) * rad, cym + math.sin(ang) * rad))
        d.polygon(pts, fill=col)
        p["rot"] = 0.0
        self._put(layer, bx, by, p)
        self._record(key, "shape", name or "star", self._norm_box(bx, by, bw, bh), col)

    def poly(self, points, fill=MINT, alpha=1.0, name=None, **kw):
        pts = [(self.px(px_), self.px(py_, "y")) for px_, py_ in points]
        xs = [q[0] for q in pts]
        ys = [q[1] for q in pts]
        box = (min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys))
        key = self._key(name, "shape", box=box)
        p = self._props(key)
        if not p["visible"]:
            return
        bx, by, bw, bh = self._place(box, p)
        p["alpha"] = float(p["alpha"]) * float(alpha)
        col = parse_color(p["color"] or fill)
        layer = Image.new("RGBA", (max(1, int(bw)), max(1, int(bh))), (0, 0, 0, 0))
        sc = (bw / max(1e-6, box[2]), bh / max(1e-6, box[3]))
        ImageDraw.Draw(layer).polygon(
            [((q[0] - box[0]) * sc[0], (q[1] - box[1]) * sc[1]) for q in pts], fill=col)
        self._put(layer, bx, by, p)
        self._record(key, "shape", name or "poly", self._norm_box(bx, by, bw, bh), col)

    def progress(self, x, y, w, h, value=0.5, fill=MINT, track="#E4DFC6", r=0.5,
                 alpha=1.0, name=None, **kw):
        self.rrect(x, y, w, h, r=r, fill=track, alpha=alpha, name=(name or "bar_track") + ":t")
        v = max(0.0, min(1.0, float(value)))
        self.rrect(x, y, max(0.002, w * v), h, r=r, fill=fill, alpha=alpha,
                   name=(name or "bar") + ":f")

    def blit(self, img, x=0, y=0, w=1.0, h=1.0, alpha=1.0, radius=0.0, rot=0.0,
             fit="cover", name=None, shadow=0.0, **kw):
        pil = _cached(("pil", id(img), getattr(img, "shape", None)), lambda: to_pil(img))
        box = (self.px(x), self.px(y, "y"), self.px(w), self.px(h, "y"))
        key = self._key(name, "image", box=box)
        p = self._props(key)
        p["rot"] = rot if p["rot"] == 0.0 else p["rot"]
        if not p["visible"]:
            return
        bx, by, bw, bh = self._place(box, p)
        p["alpha"] = float(p["alpha"]) * float(alpha)
        tw, th = max(1, int(bw)), max(1, int(bh))
        out = _cached(("fit", id(pil), tw, th, fit, radius), lambda: _fit(pil, tw, th, fit, radius))
        if out is None:
            out = _fit(pil, tw, th, fit, radius)
        self._put(out, bx, by, p)
        self._record(key, "image", name or "image", self._norm_box(bx, by, bw, bh), None)
        return

    def _blit_slow(self, pil, bx, by, bw, bh, tw, th, fit, radius, p, key, name, box):
        if fit == "stretch":
            out = pil.copy() if pil.size == (tw, th) else pil.resize((tw, th), _resample_for(pil.size, (tw, th)))
        else:
            sw_, sh_ = pil.size
            s = max(tw / sw_, th / sh_) if fit == "cover" else min(tw / sw_, th / sh_)
            nw, nh = max(1, int(sw_ * s)), max(1, int(sh_ * s))
            resized = pil if pil.size == (nw, nh) else pil.resize((nw, nh), _resample_for(pil.size, (nw, nh)))
            out = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
            out.paste(resized, ((tw - nw) // 2, (th - nh) // 2), resized)
        if radius and radius > 0:
            mask = Image.new("L", (tw, th), 0)
            ImageDraw.Draw(mask).rounded_rectangle([0, 0, tw - 1, th - 1],
                                                   radius=int(min(tw, th) * float(radius)), fill=255)
            out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", (tw, th), 0), mask))
        self._put(out, bx, by, p)
        self._record(key, "image", name or "image", self._norm_box(bx, by, bw, bh), None)

    image = blit

    def ptag(self, text, x=0.5, y=0.5, size=0.06, color=INK, weight="semibold",
             family="poppins", align="center", anchor="mc", tracking=0.0,
             alpha=1.0, name=None, rot=0.0, line_height=1.15, shadow=0.0,
             maxw=0.9, **kw):
        text = str(text)
        key = self._key(name, "text", text=text)
        p = self._props(key)
        p["rot"] = rot if p["rot"] == 0.0 else p["rot"]
        if not p["visible"]:
            return
        if p["text"] is not None:
            text = str(p["text"])
        fsize = max(6, int(self.px(size, "y")))
        f = font(family, weight, fsize)
        col = parse_color(p["color"] or color)
        lines = text.split("\n")
        probe = Image.new("RGBA", (8, 8))
        pd = ImageDraw.Draw(probe)
        widths = []
        for ln in lines:
            bb = pd.textbbox((0, 0), ln, font=f)
            widths.append(bb[2] - bb[0])
        if maxw and max(widths or [0]) > 0:
            if anchor in ("ml", "tl", "bl") or anchor == "l":
                avail = max(0.1, (1.0 - float(x))) * self.W
            elif anchor in ("mr", "tr", "br") or anchor == "r":
                avail = max(0.1, float(x)) * self.W
            else:
                avail = self.W
            limit = avail * float(maxw)
            if max(widths) > limit:
                fsize = max(6, int(fsize * limit / float(max(widths))))
            f = font(family, weight, fsize)
            widths = [pd.textbbox((0, 0), ln, font=f)[2] - pd.textbbox((0, 0), ln, font=f)[0] for ln in lines]
        lh = int(fsize * float(line_height))
        tw = max(widths) if widths else 1
        th = lh * len(lines)
        pad = int(fsize * 0.45)
        layer = Image.new("RGBA", (max(1, tw + pad * 2), max(1, th + pad * 2)), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        for i, ln in enumerate(lines):
            bb = d.textbbox((0, 0), ln, font=f)
            lw = bb[2] - bb[0]
            if align == "left":
                lx = pad
            elif align == "right":
                lx = layer.width - pad - lw
            else:
                lx = (layer.width - lw) / 2.0
            d.text((lx - bb[0], pad + i * lh), ln, font=f, fill=col)
        ax = self.px(x)
        ay = self.px(y, "y")
        w, h = layer.width, layer.height
        if "l" in anchor[1:] or anchor in ("l", "ml", "tl", "bl"):
            left = ax
        elif "r" in anchor[1:] or anchor in ("r", "mr", "tr", "br"):
            left = ax - w
        else:
            left = ax - w / 2.0
        if anchor in ("tl", "tc", "tr", "t"):
            top = ay
        elif anchor in ("bl", "bc", "br", "b"):
            top = ay - h
        else:
            top = ay - h / 2.0
        p["alpha"] = float(p["alpha"]) * float(alpha)
        self._put(layer, left, top, p)
        self._record(key, "text", name or text[:24], self._norm_box(left, top, w, h), col, text)

    text = ptag
    ptext = ptag

    def label(self, *a, **k):
        return self.ptag(*a, **k)

    # ------------------------------------------------------------------ output
    def result(self):
        return self.img
