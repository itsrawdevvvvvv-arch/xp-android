"""xp_runtime — the bridge between the editor and a user's .py video script.

Kotlin calls:  load_script(path) -> JSON scene graph
               apply_edits(json)
               render_frame(t, w, h) -> ARGB byte array

The script is never edited. We exec it once, wrap its draw calls
(blit / rrect / ptag) so the user's timeline edits are injected at the call
site, and read its AUDIT / ob() hooks for the scene graph.
"""
import importlib.util
import inspect
import json
import os
import sys
import traceback

import numpy as np
from PIL import Image

import xp_api
import xp_canvas
import xp_shim_cv2
import xp_shim_ffmpeg
from xp_canvas import Canvas

STATE = {
    "module": None,
    "fn": None,
    "path": None,
    "name": "Untitled",
    "duration": 8.0,
    "fps": 30.0,
    "size": (1080, 1920),
    "edits": {},
    "audit": {},
    "error": None,
    "baked": [],
    "fonts_dir": "",
    "cache_dir": "",
}

SHIMMED = False


def set_paths(fonts_dir="", cache_dir=""):
    STATE["fonts_dir"] = fonts_dir or ""
    STATE["cache_dir"] = cache_dir or ""
    xp_canvas.set_fonts_dir(fonts_dir)


def install_shims():
    """Make `import cv2` and `import ffmpeg` resolve to the XP shims."""
    global SHIMMED
    if SHIMMED:
        return
    sys.modules["cv2"] = xp_shim_cv2
    sys.modules["ffmpeg"] = xp_shim_ffmpeg
    SHIMMED = True


def _lib_versions():
    out = {}
    for name, mod in (("numpy", np), ("pillow", Image)):
        try:
            out[name] = getattr(mod, "__version__", "?")
        except Exception:
            out[name] = "?"
    try:
        import scipy
        out["scipy"] = getattr(scipy, "__version__", "?")
    except Exception:
        out["scipy"] = None
    out["cv2"] = xp_shim_cv2.__version__
    out["ffmpeg"] = xp_shim_ffmpeg.__version__
    out["python"] = "%d.%d.%d" % sys.version_info[:3]
    return out


def info():
    return json.dumps({"ok": True, "libs": _lib_versions(), "fonts": bool(STATE["fonts_dir"])})


# --------------------------------------------------------------------- loading
def _reset():
    STATE.update({"module": None, "fn": None, "path": None, "name": "Untitled",
                  "duration": 8.0, "fps": 30.0, "size": (1080, 1920),
                  "edits": {}, "audit": {}, "error": None, "baked": []})
    xp_api.AUDIT.clear()


def _load_module(path):
    spec = importlib.util.spec_from_file_location("xp_user_script", path)
    if spec is None or spec.loader is None:
        raise ImportError("cannot load script: %s" % path)
    module = importlib.util.module_from_spec(spec)
    sys.modules["xp_user_script"] = module
    d = os.path.dirname(os.path.abspath(path))
    if d not in sys.path:
        sys.path.insert(0, d)
    spec.loader.exec_module(module)
    return module


def _find_render_fn(module):
    for name in ("render_frame", "render", "draw", "frame", "make_frame"):
        fn = getattr(module, name, None)
        if callable(fn):
            return fn
    return None


def _arity(fn):
    try:
        sig = inspect.signature(fn)
        n = 0
        for p in sig.parameters.values():
            if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD):
                n += 1
        if any(p.kind == p.VAR_POSITIONAL for p in sig.parameters.values()):
            return 2
        return n
    except Exception:
        return 2


def _patch_module(module):
    """Monkey-patch the script's own draw helpers so edits still inject."""
    if module is None:
        return []
    patched = []
    for name in ("blit", "rrect", "ptag", "ptext"):
        fn = getattr(module, name, None)
        if not callable(fn) or getattr(fn, "__xp_wrapped__", False):
            continue
        original = fn

        def make(orig, nm):
            def wrapper(*args, **kw):
                kw.setdefault("name", kw.get("name") or "script:%s:%s" % (nm, kw.get("label", "")))
                return orig(*args, **kw)
            wrapper.__xp_wrapped__ = True
            wrapper.__name__ = nm
            return wrapper

        setattr(module, name, make(original, name))
        patched.append(name)
    return patched


def _call_render(fn, t, canvas, arity):
    if arity >= 2:
        out = fn(float(t), canvas)
    else:
        out = fn(float(t))
    if out is not None and not isinstance(out, Canvas):
        try:
            canvas.blit(out, 0, 0, 1, 1, fit="stretch", name="__return__")
        except Exception:
            pass
    return canvas


# ---------------------------------------------------------------------- audit
def _audit(duration, samples=64):
    fn = STATE["fn"]
    module = STATE["module"]
    arity = _arity(fn)
    found = {}
    errors = []
    dt = duration / max(1, samples - 1)
    for i in range(samples):
        t = round(i * dt, 4)
        try:
            c = Canvas(216, 384, t=t, duration=duration, edits={}, record=True)
            _call_render(fn, t, c, arity)
            for n in c.nodes:
                key = n["id"]
                cur = found.get(key)
                if cur is None:
                    found[key] = {
                        "id": key, "kind": n["kind"], "label": n["label"],
                        "color": n["color"], "text": n["text"],
                        "t0": t, "t1": t, "bbox": list(n["bbox"]), "hits": 1,
                    }
                else:
                    cur["t1"] = t
                    cur["hits"] += 1
                    if n.get("text"):
                        cur["text"] = n["text"]
                    if n.get("color"):
                        cur["color"] = n["color"]
                    b = n["bbox"]
                    cur["bbox"] = [min(cur["bbox"][0], b[0]), min(cur["bbox"][1], b[1]),
                                   max(cur["bbox"][2], b[2]), max(cur["bbox"][3], b[3])]
        except Exception:
            if len(errors) < 3:
                errors.append(traceback.format_exc(limit=3).strip().split("\n")[-1])
    # declared intent wins for labels / kinds
    for key, meta in list(xp_api.AUDIT.items()):
        if key == "__scene__":
            continue
        if key in found:
            found[key]["label"] = meta.get("label") or found[key]["label"]
            found[key]["kind"] = meta.get("kind") or found[key]["kind"]
            if meta.get("color"):
                found[key]["color"] = meta["color"]
        elif meta.get("t0") is not None:
            found[key] = {"id": key, "kind": meta.get("kind", "shape"),
                          "label": meta.get("label", key), "color": meta.get("color"),
                          "text": None, "t0": float(meta["t0"]),
                          "t1": float(meta.get("t1", duration)),
                          "bbox": [0.1, 0.1, 0.8, 0.1], "hits": 1}
    nodes = []
    for n in found.values():
        n.pop("hits", None)
        kind = n.get("kind") or "shape"
        track = {"text": "TEXT", "image": "MEDIA"}.get(kind, "SHAPES")
        if n["id"].startswith("background"):
            track = "VIDEO"
        n["track"] = track
        n["t0"] = round(float(n["t0"]), 3)
        n["t1"] = round(max(float(n["t1"]), float(n["t0"]) + dt), 3)
        n["label"] = (n.get("label") or n["id"])[:40]
        nodes.append(n)
    nodes.sort(key=lambda x: (x["track"], x["t0"]))
    if len(nodes) > 140:
        nodes = nodes[:140]
    return nodes, errors


def load_script(path):
    _reset()
    install_shims()
    try:
        if not os.path.exists(path):
            raise FileNotFoundError(path)
        module = _load_module(path)
        fn = _find_render_fn(module)
        if fn is None:
            raise AttributeError("no render_frame(t, canvas) function found in the script")
        duration = float(getattr(module, "DURATION", getattr(module, "duration", 8.0)) or 8.0)
        fps = float(getattr(module, "FPS", getattr(module, "fps", 30.0)) or 30.0)
        size = getattr(module, "SIZE", getattr(module, "size", (1080, 1920))) or (1080, 1920)
        duration = max(0.5, min(300.0, duration))
        STATE.update({
            "module": module, "fn": fn, "path": path,
            "name": getattr(module, "TITLE", os.path.basename(path).rsplit(".", 1)[0]),
            "duration": duration, "fps": max(12.0, min(60.0, fps)),
            "size": (int(size[0]), int(size[1])),
            "audit": dict(xp_api.AUDIT),
        })
        patched = _patch_module(module)
        nodes, errors = _audit(duration, samples=min(96, int(duration * 8) + 2))
        if not nodes and errors:
            raise RuntimeError("the script drew nothing: " + errors[0])
        return json.dumps({
            "ok": True, "name": STATE["name"], "path": path,
            "duration": duration, "fps": STATE["fps"],
            "width": STATE["size"][0], "height": STATE["size"][1],
            "nodes": nodes, "nodeCount": len(nodes),
            "patched": patched, "errors": errors,
            "libs": _lib_versions(),
            "auditKeys": list(xp_api.AUDIT.keys()),
        })
    except Exception:
        STATE["error"] = traceback.format_exc(limit=6)
        return json.dumps({"ok": False, "error": STATE["error"], "path": path})


def apply_edits(edits_json):
    try:
        STATE["edits"] = json.loads(edits_json) if edits_json else {}
    except Exception:
        STATE["edits"] = {}
    return json.dumps({"ok": True, "count": len(STATE["edits"])})


# --------------------------------------------------------------------- render
def _argb(pil_img):
    """RGBA bytes, ready for Bitmap.copyPixelsFromBuffer (ARGB_8888 is RGBA in memory)."""
    if pil_img.mode != "RGBA":
        pil_img = pil_img.convert("RGBA")
    return pil_img.tobytes()


def _fallback(w, h, msg):
    img = Image.new("RGBA", (w, h), (242, 240, 220, 255))
    try:
        from PIL import ImageDraw
        d = ImageDraw.Draw(img)
        f = xp_canvas.font("poppins", "semibold", max(12, int(h * 0.03)))
        d.multiline_text((w * 0.08, h * 0.4), "script error\n" + str(msg)[:160], font=f,
                         fill=(26, 32, 44, 255), spacing=8)
    except Exception:
        pass
    return img


def render_frame(t, w, h):
    install_shims()
    fn = STATE["fn"]
    if fn is None:
        return _argb(_fallback(int(w), int(h), STATE.get("error") or "no script loaded"))
    try:
        c = Canvas(int(w), int(h), t=float(t), duration=STATE["duration"],
                   edits=STATE["edits"], record=False)
        _call_render(fn, t, c, _arity(fn))
        return _argb(c.result())
    except Exception:
        STATE["error"] = traceback.format_exc(limit=6)
        return _argb(_fallback(int(w), int(h), STATE["error"].strip().split("\n")[-1]))


def render_png(t, w, h, path):
    try:
        fn = STATE["fn"]
        if fn is None:
            return ""
        c = Canvas(int(w), int(h), t=float(t), duration=STATE["duration"],
                   edits=STATE["edits"], record=False)
        _call_render(fn, t, c, _arity(fn))
        c.result().convert("RGB").save(path, quality=90)
        return path
    except Exception:
        return ""


def scene_json():
    return json.dumps({"duration": STATE["duration"], "fps": STATE["fps"],
                       "name": STATE["name"], "error": STATE["error"]})


# ------------------------------------------------------- baked / ffmpeg hooks
def register_baked_frames(frames, fps=30.0):
    STATE["baked"] = list(frames or [])
    return len(STATE["baked"])


def run_ffmpeg_job(stream):
    """input('%05d.jpg').output('out.mp4', r=30).run() -> register a bake job."""
    src = stream.parent.spec if stream.parent is not None else stream.spec
    fps = float(stream.kwargs.get("r", 30) or 30)
    register_baked_frames([str(src)], fps)
    return xp_shim_ffmpeg.Result(0, "", "")


def run_ffmpeg_args(args):
    fps = 30.0
    for i, a in enumerate(args):
        if a == "-r" and i + 1 < len(args):
            try:
                fps = float(args[i + 1])
            except Exception:
                pass
    register_baked_frames([str(a) for a in args if str(a).endswith((".jpg", ".png"))], fps)
    return xp_shim_ffmpeg.Result(0, "", "")
