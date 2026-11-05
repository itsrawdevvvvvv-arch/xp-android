package ai.xp.editor.core

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.xp.editor.design.Haptics
import ai.xp.editor.model.NodeEdit
import ai.xp.editor.model.Scene
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Screen { Home, Import, Editor, Export, Settings }

/** Everything the app remembers between screens. */
class XpState(val ctx: Context) {

    var screen by mutableStateOf(Screen.Home)

    var scene by mutableStateOf<Scene?>(null)
    var sceneError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var loadNote by mutableStateOf("")

    val edits = mutableStateMapOf<String, NodeEdit>()
    var selected by mutableStateOf<String?>(null)
    var editorTab by mutableStateOf(0)
    var sheetTab by mutableStateOf(0)

    var zoom by mutableStateOf(64f)
    var scroll by mutableStateOf(0f)
    var lastExport by mutableStateOf<String?>(null)
    var audioPath by mutableStateOf<String?>(null)

    // settings
    var previewQuality by mutableStateOf(1) // 0=540,1=720,2=1080 // 0=540,1=720,2=1080
    var hapticsOn by mutableStateOf(true)
    var loopOn by mutableStateOf(true)
    var showGuides by mutableStateOf(true)
    var exportWidth by mutableStateOf(1080)
    var exportFps by mutableStateOf(30)
    var exportQuality by mutableStateOf(0.72f)

    val engine = PreviewEngine(ctx)
    val isLowRam: Boolean = computeLowRam()

    init {
        engine.loop = true
        engine.targetFps = if (isLowRam) 24 else 30
        // 2 GB / Android 9 devices start at 540p rather than fight the CPU
        previewQuality = if (isLowRam) 0 else 1
        applyPreviewQuality()
    }

    private fun computeLowRam(): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        return (am?.isLowRamDevice ?: false) || Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    fun applyPreviewQuality() {
        val (w, h) = when (previewQuality) {
            0 -> 540 to 960
            2 -> 1080 to 1920
            else -> 720 to 1280
        }
        val lowRam = isLowRam
        val finalW = if (lowRam && previewQuality == 2) 720 else w
        val finalH = if (lowRam && previewQuality == 2) 1280 else h
        engine.setPreviewSize(finalW, finalH, finalW.toString())
    }

    fun editFor(id: String?): NodeEdit {
        val key = id ?: return NodeEdit()
        return edits.getOrPut(key) { NodeEdit() }
    }

    fun selectedNode() = scene?.node(selected)

    fun pushEdits() {
        XpPython.applyEdits(edits)
        engine.invalidate()
    }

    fun seek(t: Float) {
        engine.duration = scene?.duration ?: 8f
        engine.seek(t)
    }

    fun openScript(path: String) {
        loading = true
        loadNote = "Reading script"
        sceneError = null
    }

    fun onLoaded(scene: Scene?, error: String?, path: String) {
        loading = false
        if (scene == null) {
            sceneError = error ?: "Could not load the script"
            return
        }
        this.scene = scene
        edits.clear()
        loadSavedEdits(scene, path)
        selected = null
        engine.duration = scene.duration
        engine.sceneFps = scene.fps
        engine.seek(0f)
        engine.loop = loopOn
        XpPython.applyEdits(edits)
        zoom = 64f
        scroll = 0f
        screen = Screen.Editor
    }

    fun toggleHaptics() {
        hapticsOn = !hapticsOn
        Haptics.enabled = hapticsOn
    }

    // ------------------------------------------------------------- persistence
    private fun projectFile(scene: Scene): File {
        val dir = File(ctx.filesDir, "xp/projects").apply { mkdirs() }
        val name = scene.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(40)
        return File(dir, "$name.json")
    }

    fun saveEdits() {
        val scene = scene ?: return
        try {
            val root = JSONObject()
            edits.forEach { (id, e) -> if (!e.isDefault) root.put(id, e.toJson()) }
            projectFile(scene).writeText(root.toString())
        } catch (_: Throwable) {
        }
    }

    private fun loadSavedEdits(scene: Scene, path: String) {
        try {
            val f = projectFile(scene)
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            root.keys().forEach { id ->
                val o = root.getJSONObject(id)
                val e = NodeEdit()
                val p = o.optJSONObject("props")
                p?.let {
                    if (it.has("dx")) e.dx = it.optDouble("dx").toFloat()
                    if (it.has("dy")) e.dy = it.optDouble("dy").toFloat()
                    if (it.has("sx")) e.scale = it.optDouble("sx").toFloat()
                    if (it.has("rot")) e.rot = it.optDouble("rot").toFloat()
                    if (it.has("alpha")) e.opacity = it.optDouble("alpha").toFloat()
                    if (it.has("color")) e.color = it.optString("color")
                    if (it.has("text")) e.text = it.optString("text")
                    if (it.has("t0")) e.t0 = it.optDouble("t0").toFloat()
                    if (it.has("t1")) e.t1 = it.optDouble("t1").toFloat()
                }
                edits[id] = e
            }
        } catch (_: Throwable) {
        }
    }

    fun editsJson(): String {
        val root = JSONObject()
        edits.forEach { (id, e) -> if (!e.isDefault) root.put(id, e.toJson()) }
        return root.toString()
    }

    fun cacheBytes(): Long = engine.scrub.size()

    fun clearCache() {
        engine.scrub.clear()
        engine.invalidate()
    }

    companion object {
        const val MAX_EDITS_JSON = 200_000
    }
}
