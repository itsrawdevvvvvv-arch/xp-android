package ai.xp.editor.core

import android.content.Context
import ai.xp.editor.model.NodeEdit
import ai.xp.editor.model.Scene
import ai.xp.editor.model.SceneJson
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The Chaquopy bridge. Every call into Python is serialised: the runtime holds
 * one loaded script, so preview and export must not interleave.
 */
object XpPython {

    private val lock = Any()
    @Volatile private var inited = false

    lateinit var scriptsDir: File
        private set
    lateinit var fontsDir: File
        private set
    lateinit var cacheDir: File
        private set

    private val runtime: PyObject
        get() = Python.getInstance().getModule("xp_runtime")

    fun ensureInit(ctx: Context) {
        if (inited) return
        synchronized(lock) {
            if (inited) return
            val base = File(ctx.filesDir, "xp")
            scriptsDir = File(base, "scripts").apply { mkdirs() }
            fontsDir = File(base, "fonts").apply { mkdirs() }
            cacheDir = File(ctx.cacheDir, "xp").apply { mkdirs() }

            copyAssets(ctx, "fonts", fontsDir)
            copyAssets(ctx, "samples", scriptsDir)

            runtime.callAttr("set_paths", fontsDir.absolutePath, cacheDir.absolutePath)
            runtime.callAttr("install_shims")
            inited = true
        }
    }

    private fun copyAssets(ctx: Context, assetDir: String, outDir: File) {
        try {
            val names = ctx.assets.list(assetDir) ?: return
            names.forEach { name ->
                val out = File(outDir, name)
                if (out.exists() && out.length() > 0) return@forEach
                try {
                    ctx.assets.open("$assetDir/$name").use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun info(): JSONObject = try {
        synchronized(lock) { JSONObject(runtime.callAttr("info").toString()) }
    } catch (t: Throwable) {
        JSONObject().put("ok", false).put("error", t.message ?: "python init failed")
    }

    /** Executes a user script and returns its scene graph, or null + message. */
    fun load(path: String): Pair<Scene?, String?> {
        synchronized(lock) {
            return try {
                val raw = runtime.callAttr("load_script", path).toString()
                val scene = SceneJson.parse(raw)
                Pair(scene, if (scene == null) SceneJson.errorOf(raw) else null)
            } catch (t: Throwable) {
                Pair(null, t.message ?: "python error")
            }
        }
    }

    fun applyEditsRaw(json: String) {
        try {
            synchronized(lock) { runtime.callAttr("apply_edits", json) }
        } catch (_: Throwable) {
        }
    }

    fun applyEdits(edits: Map<String, NodeEdit>) {
        val root = JSONObject()
        edits.forEach { (id, e) ->
            if (!e.isDefault) root.put(id, e.toJson())
        }
        try {
            synchronized(lock) { runtime.callAttr("apply_edits", root.toString()) }
        } catch (_: Throwable) {
        }
    }

    /** RGBA bytes for one frame at the requested size. */
    fun renderFrame(t: Float, w: Int, h: Int): ByteArray? {
        synchronized(lock) {
            return try {
                runtime.callAttr("render_frame", t.toDouble(), w, h)
                    .toJava(ByteArray::class.java)
            } catch (t2: Throwable) {
                null
            }
        }
    }

    /** Renders straight to a JPEG on disk — used by the scrub cache. */
    fun renderJpeg(t: Float, w: Int, h: Int, out: String): Boolean {
        synchronized(lock) {
            return try {
                val r = runtime.callAttr("render_png", t.toDouble(), w, h, out).toString()
                r.isNotEmpty()
            } catch (t2: Throwable) {
                false
            }
        }
    }

    fun scripts(): List<File> {
        val list = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".py") } ?: emptyArray()
        return list.sortedBy { it.name.lowercase() }
    }

    fun importScript(ctx: Context, name: String, bytes: ByteArray): File {
        val safe = name.replace(Regex("[^A-Za-z0-9 ._-]"), "_").takeLast(80)
        val f = File(scriptsDir, if (safe.endsWith(".py")) safe else "$safe.py")
        f.writeBytes(bytes)
        return f
    }

    fun bakedFrames(): JSONArray = try {
        JSONArray()
    } catch (t: Throwable) {
        JSONArray()
    }
}
