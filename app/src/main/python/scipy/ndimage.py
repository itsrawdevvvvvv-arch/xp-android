"""scipy.ndimage subset on NumPy + Pillow."""
import numpy as np
from PIL import Image, ImageFilter


def _pil(a):
    a = np.asarray(a)
    if a.dtype != np.uint8:
        a = np.clip(a, 0, 255).astype(np.uint8)
    if a.ndim == 2:
        return Image.fromarray(a, "L")
    if a.shape[2] == 4:
        return Image.fromarray(a, "RGBA")
    return Image.fromarray(a[:, :, :3], "RGB")


def gaussian_filter(arr, sigma, mode="reflect", **kw):
    a = np.asarray(arr)
    s = float(sigma) if np.isscalar(sigma) else float(np.max(sigma))
    out = np.asarray(_pil(a).filter(ImageFilter.GaussianBlur(max(0.1, s))))
    if a.dtype in (np.float32, np.float64):
        return out.astype(a.dtype) / 255.0 if a.max() <= 1.0 else out.astype(a.dtype)
    return out


def uniform_filter(arr, size=3, **kw):
    a = np.asarray(arr, dtype=np.float32)
    k = int(size) if np.isscalar(size) else int(np.max(size))
    if k < 2:
        return a
    ker = np.ones(k, np.float32) / k
    out = a
    for axis in range(a.ndim if a.ndim <= 2 else 2):
        out = np.apply_along_axis(lambda m: np.convolve(m, ker, mode="same"), axis, out)
    return out


def sobel(arr, axis=0, **kw):
    a = np.asarray(arr, dtype=np.float32)
    if a.ndim == 3:
        a = a.mean(axis=2)
    kx = np.array([[-1, 0, 1], [-2, 0, 2], [-1, 0, 1]], np.float32)
    if axis == 1:
        kx = kx.T
    from .signal import convolve2d
    return convolve2d(a, kx)


def zoom(arr, factor, order=1, **kw):
    a = np.asarray(arr)
    f = float(factor) if np.isscalar(factor) else float(factor[0])
    h = max(1, int(a.shape[0] * f))
    w = max(1, int(a.shape[1] * f))
    return np.asarray(_pil(a).resize((w, h), Image.BILINEAR))


def rotate(arr, angle, reshape=True, order=1, **kw):
    a = np.asarray(arr)
    return np.asarray(_pil(a).rotate(float(angle), resample=Image.BILINEAR, expand=bool(reshape)))
