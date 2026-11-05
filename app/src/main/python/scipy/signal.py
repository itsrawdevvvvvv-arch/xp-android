"""scipy.signal subset on NumPy."""
import numpy as np


def convolve(a, b, mode="full", **kw):
    return np.convolve(np.asarray(a).ravel(), np.asarray(b).ravel(), mode=mode)


def correlate(a, b, mode="full", **kw):
    return np.correlate(np.asarray(a).ravel(), np.asarray(b).ravel(), mode=mode)


def convolve2d(a, k, mode="same", **kw):
    a = np.asarray(a, dtype=np.float32)
    k = np.asarray(k, dtype=np.float32)
    kh, kw_ = k.shape
    pad_h, pad_w = kh // 2, kw_ // 2
    padded = np.pad(a, ((pad_h, pad_h), (pad_w, pad_w)), mode="edge")
    out = np.zeros_like(a)
    for i in range(kh):
        for j in range(kw_):
            out += k[i, j] * padded[i:i + a.shape[0], j:j + a.shape[1]]
    return out


def find_peaks(x, height=None, distance=1, **kw):
    x = np.asarray(x, dtype=np.float64)
    idx = []
    for i in range(1, len(x) - 1):
        if x[i] > x[i - 1] and x[i] >= x[i + 1]:
            if height is not None and x[i] < (height if np.isscalar(height) else height[0]):
                continue
            if idx and i - idx[-1] < distance:
                continue
            idx.append(i)
    return np.array(idx, dtype=np.int64), {}


def resample(x, num, **kw):
    x = np.asarray(x)
    return np.interp(np.linspace(0, len(x) - 1, int(num)), np.arange(len(x)), x)


def detrend(x, type="linear", **kw):
    x = np.asarray(x, dtype=np.float64)
    t = np.arange(len(x))
    if type == "constant":
        return x - x.mean()
    p = np.polyfit(t, x, 1)
    return x - np.polyval(p, t)
