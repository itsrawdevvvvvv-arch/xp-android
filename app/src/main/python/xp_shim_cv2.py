"""cv2 shim — a real subset of OpenCV built on NumPy + Pillow.

XP ships this instead of real OpenCV: OpenCV's Android native libs add ~60 MB
and duplicate what Pillow already does. The functions below are the ones video
scripts actually use. Anything missing raises a clear error naming the call.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from xp_canvas import font

# ---- constants (values copied from OpenCV so code comparing them works) ----
COLOR_BGR2RGB = 4
COLOR_RGB2BGR = 4
COLOR_BGR2GRAY = 6
COLOR_RGB2GRAY = 7
COLOR_GRAY2RGB = 8
COLOR_GRAY2BGR = 8
COLOR_BGRA2BGR = 1
INTER_NEAREST = 0
INTER_LINEAR = 1
INTER_CUBIC = 2
INTER_AREA = 3
INTER_LANCZOS4 = 4
FONT_HERSHEY_SIMPLEX = 0
FONT_HERSHEY_DUPLEX = 1
FONT_HERSHEY_PLAIN = 2
LINE_AA = 16
LINE_8 = 8
FILLED = -1
BORDER_CONSTANT = 0
BORDER_REFLECT = 4

_RESAMPLE = {INTER_NEAREST: Image.NEAREST, INTER_LINEAR: Image.BILINEAR,
             INTER_CUBIC: Image.BICUBIC, INTER_AREA: Image.BOX, INTER_LANCZOS4: Image.LANCZOS}

__version__ = "4.9.0-xp"
XP_SHIM = True


def _pil(img):
    if isinstance(img, Image.Image):
        return img
    a = np.asarray(img)
    if a.dtype != np.uint8:
        a = np.clip(a, 0, 255).astype(np.uint8)
    if a.ndim == 2:
        return Image.fromarray(a, "L")
    if a.shape[2] == 4:
        return Image.fromarray(a, "RGBA")
    return Image.fromarray(a[:, :, :3], "RGB")


def _arr(img, channels=3):
    a = np.asarray(img)
    if channels == 4 and a.ndim == 3 and a.shape[2] == 3:
        a = np.dstack([a, np.full(a.shape[:2], 255, np.uint8)])
    return a


def _bgr(color):
    """OpenCV colours are BGR tuples; convert to RGB for Pillow."""
    if color is None:
        return (0, 0, 0)
    c = tuple(int(v) for v in color)
    if len(c) == 3:
        return (c[2], c[1], c[0])
    return c


def resize(img, dsize, fx=0, fy=0, interpolation=INTER_LINEAR):
    pil = _pil(img)
    w, h = dsize if (dsize and dsize[0]) else (0, 0)
    if not w or not h:
        w = max(1, int(pil.width * (fx or 1)))
        h = max(1, int(pil.height * (fy or 1)))
    return _arr(pil.resize((int(w), int(h)), _RESAMPLE.get(interpolation, Image.BILINEAR)),
                4 if pil.mode == "RGBA" else 3)


def cvtColor(img, code):
    pil = _pil(img)
    if code in (COLOR_BGR2GRAY, COLOR_RGB2GRAY):
        return np.asarray(pil.convert("L"))
    if code in (COLOR_GRAY2RGB, COLOR_GRAY2BGR):
        return np.asarray(pil.convert("RGB"))
    if code == COLOR_BGRA2BGR:
        return np.asarray(pil.convert("RGB"))
    return np.asarray(pil.convert("RGB"))


def putText(img, text, org, fontFace=0, fontScale=1.0, color=(255, 255, 255),
            thickness=2, lineType=LINE_AA, bottomLeftOrigin=False):
    pil = _pil(img).convert("RGBA")
    d = ImageDraw.Draw(pil)
    size = max(8, int(30 * float(fontScale)))
    f = font("poppins", "semibold", size)
    x, y = int(org[0]), int(org[1])
    bb = d.textbbox((0, 0), str(text), font=f)
    d.text((x, y - (bb[3] - bb[1])), str(text), font=f, fill=_bgr(color) + (255,))
    if isinstance(img, Image.Image):
        return pil
    return np.asarray(pil.convert("RGB") if img.ndim == 3 and img.shape[2] == 3 else pil)


def getTextSize(text, fontFace=0, fontScale=1.0, thickness=2):
    size = max(8, int(30 * float(fontScale)))
    f = font("poppins", "semibold", size)
    tmp = ImageDraw.Draw(Image.new("RGBA", (8, 8)))
    bb = tmp.textbbox((0, 0), str(text), font=f)
    return (bb[2] - bb[0], bb[3] - bb[1]), max(1, int(size * 0.25))


def rectangle(img, pt1, pt2, color=(255, 255, 255), thickness=1, lineType=LINE_8):
    pil = _pil(img).convert("RGBA")
    d = ImageDraw.Draw(pil)
    box = [int(pt1[0]), int(pt1[1]), int(pt2[0]), int(pt2[1])]
    if thickness == FILLED or thickness < 0:
        d.rectangle(box, fill=_bgr(color) + (255,))
    else:
        d.rectangle(box, outline=_bgr(color) + (255,), width=int(thickness))
    return pil if isinstance(img, Image.Image) else np.asarray(pil.convert("RGB"))


def circle(img, center, radius, color=(255, 255, 255), thickness=1, lineType=LINE_AA):
    pil = _pil(img).convert("RGBA")
    d = ImageDraw.Draw(pil)
    cx, cy, r = int(center[0]), int(center[1]), int(radius)
    box = [cx - r, cy - r, cx + r, cy + r]
    if thickness == FILLED or thickness < 0:
        d.ellipse(box, fill=_bgr(color) + (255,))
    else:
        d.ellipse(box, outline=_bgr(color) + (255,), width=int(thickness))
    return pil if isinstance(img, Image.Image) else np.asarray(pil.convert("RGB"))


def line(img, pt1, pt2, color=(255, 255, 255), thickness=1, lineType=LINE_AA):
    pil = _pil(img).convert("RGBA")
    ImageDraw.Draw(pil).line([int(pt1[0]), int(pt1[1]), int(pt2[0]), int(pt2[1])],
                             fill=_bgr(color) + (255,), width=int(thickness))
    return pil if isinstance(img, Image.Image) else np.asarray(pil.convert("RGB"))


def GaussianBlur(img, ksize, sigmaX=0, sigmaY=0):
    pil = _pil(img)
    s = float(sigmaX) if sigmaX else max(0.6, (ksize[0] if isinstance(ksize, (tuple, list)) else 3) / 3.0)
    return np.asarray(pil.filter(ImageFilter.GaussianBlur(s)))


def blur(img, ksize):
    return GaussianBlur(img, ksize, max(0.6, (ksize[0] if isinstance(ksize, (tuple, list)) else 3) / 3.0))


def addWeighted(a, alpha, b, beta, gamma=0):
    out = np.asarray(a, np.float32) * float(alpha) + np.asarray(b, np.float32) * float(beta) + float(gamma)
    return np.clip(out, 0, 255).astype(np.uint8)


def getRotationMatrix2D(center, angle, scale):
    return np.array([[float(angle), 0.0, float(center[0])],
                     [0.0, float(scale), float(center[1])]], np.float32)


def warpAffine(img, M, dsize, flags=INTER_LINEAR, borderMode=0, borderValue=(0, 0, 0)):
    pil = _pil(img)
    angle = float(np.asarray(M)[0][0])
    out = pil.rotate(-angle, resample=Image.BICUBIC, expand=False)
    if dsize and dsize[0]:
        out = out.resize((int(dsize[0]), int(dsize[1])), Image.BILINEAR)
    return np.asarray(out)


def imwrite(path, img):
    pil = _pil(img)
    if pil.mode == "RGB" and str(path).lower().endswith((".jpg", ".jpeg")):
        pil.save(path, quality=92)
    else:
        pil.save(path)
    return True


def imread(path, flags=1):
    if not os.path.exists(str(path)):
        return None
    pil = Image.open(str(path))
    if flags == 0:
        return np.asarray(pil.convert("L"))
    return np.asarray(pil.convert("RGB"))


def hconcat(imgs):
    return np.hstack([np.asarray(i) for i in imgs])


def vconcat(imgs):
    return np.vstack([np.asarray(i) for i in imgs])


class VideoWriter:
    """Collects frames to disk so XP can encode them; no codec of its own."""

    def __init__(self, path="", fourcc=None, fps=30, frameSize=None, isColor=True):
        self.path = str(path) or "xp_out"
        self.fps = float(fps) or 30.0
        self.frameSize = frameSize
        self.frames = []
        self.dir = os.path.dirname(self.path) or "."
        try:
            os.makedirs(self.dir, exist_ok=True)
        except Exception:
            pass
        self._i = 0

    def isOpened(self):
        return True

    def write(self, frame):
        try:
            p = os.path.join(self.dir, "_seq_%05d.jpg" % self._i)
            imwrite(p, frame)
            self.frames.append(p)
        except Exception:
            pass
        self._i += 1

    def release(self):
        try:
            import xp_runtime
            xp_runtime.register_baked_frames(self.frames, self.fps)
        except Exception:
            pass
        return self.frames


class VideoCapture:
    def __init__(self, *a, **k):
        self._ok = False

    def isOpened(self):
        return False

    def read(self):
        return False, None

    def release(self):
        return None


def __getattr__(name):
    raise AttributeError(
        "cv2.%s is not implemented in XP's cv2 shim (NumPy + Pillow). "
        "Supported: resize, cvtColor, putText, getTextSize, rectangle, circle, "
        "line, GaussianBlur, blur, addWeighted, warpAffine, imread, imwrite, "
        "hconcat, vconcat, VideoWriter, VideoCapture." % name)
