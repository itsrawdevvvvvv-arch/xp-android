"""ffmpeg shim — the ffmpeg-python surface, mapped onto XP's own encoder.

XP does not ship an ffmpeg binary. Encoding an MP4 is done by MediaCodec
(H.264) inside the app, which is smaller, hardware accelerated and gives the
same result for the jobs video scripts actually ask for. This module accepts
the usual ffmpeg-python calls and turns them into a job the app runs.
"""
import os

XP_SHIM = True
__version__ = "0.2.0-xp"


class Error(Exception):
    pass


class Result:
    def __init__(self, returncode=0, stdout="", stderr=""):
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr

    def __repr__(self):
        return "<ffmpeg.Result rc=%d>" % self.returncode


class Stream:
    def __init__(self, spec, kind="input", parent=None, kwargs=None):
        self.spec = spec
        self.kind = kind
        self.parent = parent
        self.kwargs = kwargs or {}
        self.filters = []

    def filter(self, name, *a, **kw):
        s = Stream(self.spec, self.kind, self, dict(self.kwargs))
        s.filters = list(self.filters) + [(name, a, kw)]
        return s

    def vflip(self):
        return self.filter("vflip")

    def hflip(self):
        return self.filter("hflip")

    def scale(self, w=None, h=None):
        return self.filter("scale", w, h)

    def setpts(self, expr):
        return self.filter("setpts", expr)

    def output(self, path, **kw):
        s = Stream(path, "output", self, kw)
        s.filters = list(self.filters)
        return s

    def overwrite_output(self):
        self.kwargs["y"] = True
        return self

    def run(self, **kw):
        try:
            import xp_runtime
            return xp_runtime.run_ffmpeg_job(self)
        except ImportError:
            raise Error("ffmpeg jobs can only run inside the XP app")

    def compile(self):
        return ["ffmpeg", "-i", str(self.spec), str(self.spec)]


def input(path, **kw):
    return Stream(path, "input", None, kw)


def probe(path, **kw):
    try:
        from PIL import Image
        im = Image.open(str(path))
        return {"streams": [{"codec_type": "video", "width": im.width,
                             "height": im.height, "duration": "0"}], "format": {"duration": "0"}}
    except Exception:
        return {"streams": [], "format": {}}


def run(args, **kw):
    try:
        import xp_runtime
        return xp_runtime.run_ffmpeg_args(list(args))
    except ImportError:
        raise Error("ffmpeg jobs can only run inside the XP app")


def compile(args, **kw):
    return ["ffmpeg"] + [str(a) for a in args]


def __getattr__(name):
    raise AttributeError("ffmpeg.%s is not implemented in XP's ffmpeg shim. "
                         "Use input(...).output(...).run() for image-sequence to MP4." % name)
