"""scipy.spatial.distance subset on NumPy."""
import numpy as np


def euclidean(u, v):
    return float(np.linalg.norm(np.asarray(u, dtype=np.float64) - np.asarray(v, dtype=np.float64)))


def cdist(A, B, metric="euclidean", **kw):
    A = np.atleast_2d(np.asarray(A, dtype=np.float64))
    B = np.atleast_2d(np.asarray(B, dtype=np.float64))
    if metric == "cityblock" or metric == "manhattan":
        return np.abs(A[:, None, :] - B[None, :, :]).sum(axis=2)
    if metric == "cosine":
        na = np.linalg.norm(A, axis=1)[:, None]
        nb = np.linalg.norm(B, axis=1)[None, :]
        return 1.0 - (A @ B.T) / np.maximum(na * nb, 1e-12)
    d = A[:, None, :] - B[None, :, :]
    return np.sqrt((d * d).sum(axis=2))


def pdist(A, metric="euclidean", **kw):
    A = np.atleast_2d(np.asarray(A, dtype=np.float64))
    out = []
    for i in range(len(A)):
        for j in range(i + 1, len(A)):
            out.append(euclidean(A[i], A[j]))
    return np.array(out)
