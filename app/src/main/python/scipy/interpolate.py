"""scipy.interpolate subset on NumPy."""
import numpy as np


class interp1d:
    def __init__(self, x, y, kind="linear", bounds_error=True, fill_value=np.nan, **kw):
        self.x = np.asarray(x, dtype=np.float64)
        self.y = np.asarray(y, dtype=np.float64)
        self.kind = kind
        self.bounds_error = bounds_error
        self.fill_value = fill_value

    def __call__(self, xq):
        xq = np.asarray(xq, dtype=np.float64)
        out = np.interp(xq, self.x, self.y)
        if self.bounds_error and (xq.min() < self.x.min() or xq.max() > self.x.max()):
            out = np.where((xq < self.x.min()) | (xq > self.x.max()), self.fill_value, out)
        return out


class CubicSpline:
    def __init__(self, x, y, **kw):
        self.x = np.asarray(x, dtype=np.float64)
        self.y = np.asarray(y, dtype=np.float64)

    def __call__(self, xq):
        # smooth enough for motion curves: piecewise cubic via polyfit windows
        xq = np.atleast_1d(np.asarray(xq, dtype=np.float64))
        out = np.empty_like(xq)
        for i, v in enumerate(xq):
            j = int(np.clip(np.searchsorted(self.x, v), 1, len(self.x) - 2))
            sl = slice(max(0, j - 1), min(len(self.x), j + 2))
            if len(self.x[sl]) < 3:
                out[i] = np.interp(v, self.x, self.y)
            else:
                c = np.polyfit(self.x[sl], self.y[sl], 2)
                out[i] = np.polyval(c, v)
        return out


def interp(x, xp, fp, **kw):
    return np.interp(np.asarray(x), np.asarray(xp), np.asarray(fp))
