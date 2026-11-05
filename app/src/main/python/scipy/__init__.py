"""scipy shim for XP.

Chaquopy has no scipy wheel for CPython 3.11 on arm64, and a source build
needs meson + a Fortran toolchain that cannot run on-device. XP ships this
NumPy-backed package instead so `import scipy` and the common calls keep
working. Version string says so plainly.
"""
__version__ = "0.1.0-xp-shim"

from . import ndimage, signal, interpolate, spatial  # noqa: F401
