package ai.xp.editor.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** One object the user's script drew — a text run, a shape, an image. */
data class SceneNode(
    val id: String,
    val kind: String,
    val label: String,
    val t0: Float,
    val t1: Float,
    val track: String,
    val bbox: List<Float>,
    val color: String?,
    val text: String?
)

data class Scene(
    val name: String,
    val path: String,
    val duration: Float,
    val fps: Float,
    val width: Int,
    val height: Int,
    val nodes: List<SceneNode>,
    val errors: List<String>,
    val libs: Map<String, String>
) {
    val tracks: List<String>
        get() {
            val order = listOf("VIDEO", "MEDIA", "TEXT", "SHAPES", "AUDIO")
            val present = nodes.map { it.track }.toSet()
            return order.filter { present.contains(it) || it == "AUDIO" }
        }

    fun nodesOn(track: String) = nodes.filter { it.track == track }
    fun node(id: String?) = nodes.firstOrNull { it.id == id }
}

class Keyframe(val t: Float, val p: Map<String, Float>)

/**
 * A user's edit for one node. Serialised straight to the Python side, which
 * merges it (and any keyframes) at the draw call.
 */
class NodeEdit {
    var dx by mutableStateOf(0f)
    var dy by mutableStateOf(0f)
    var scale by mutableStateOf(1f)
    var rot by mutableStateOf(0f)
    var opacity by mutableStateOf(1f)
    var color by mutableStateOf<String?>(null)
    var text by mutableStateOf<String?>(null)
    var t0 by mutableStateOf<Float?>(null)
    var t1 by mutableStateOf<Float?>(null)
    val kf = mutableStateListOf<Keyframe>()

    val isDefault: Boolean
        get() = dx == 0f && dy == 0f && scale == 1f && rot == 0f && opacity == 1f &&
            color == null && text == null && t0 == null && t1 == null && kf.isEmpty()

    fun reset() {
        dx = 0f; dy = 0f; scale = 1f; rot = 0f; opacity = 1f
        color = null; text = null; t0 = null; t1 = null
        kf.clear()
    }

    fun toJson(): JSONObject {
        val props = JSONObject()
        if (dx != 0f) props.put("dx", dx.toDouble())
        if (dy != 0f) props.put("dy", dy.toDouble())
        if (scale != 1f) {
            props.put("sx", scale.toDouble())
            props.put("sy", scale.toDouble())
        }
        if (rot != 0f) props.put("rot", rot.toDouble())
        if (opacity != 1f) props.put("alpha", opacity.toDouble())
        color?.let { props.put("color", it) }
        text?.let { props.put("text", it) }
        t0?.let { props.put("t0", it.toDouble()) }
        t1?.let { props.put("t1", it.toDouble()) }
        val o = JSONObject()
        o.put("props", props)
        if (kf.isNotEmpty()) {
            val arr = JSONArray()
            kf.sortedBy { it.t }.forEach { k ->
                val p = JSONObject()
                k.p.forEach { (key, v) -> p.put(key, v.toDouble()) }
                arr.put(JSONObject().put("t", k.t.toDouble()).put("p", p).put("ease", "inout"))
            }
            o.put("kf", arr)
        }
        return o
    }
}

object SceneJson {
    fun parse(raw: String): Scene? {
        return try {
            val o = JSONObject(raw)
            if (!o.optBoolean("ok", false)) return null
            val arr = o.optJSONArray("nodes") ?: JSONArray()
            val nodes = ArrayList<SceneNode>(arr.length())
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                val bb = n.optJSONArray("bbox")
                val bbox = if (bb != null) (0 until bb.length()).map { bb.optDouble(it).toFloat() }
                else listOf(0f, 0f, 1f, 1f)
                nodes.add(
                    SceneNode(
                        id = n.optString("id"),
                        kind = n.optString("kind", "shape"),
                        label = n.optString("label", n.optString("id")),
                        t0 = n.optDouble("t0", 0.0).toFloat(),
                        t1 = n.optDouble("t1", 1.0).toFloat(),
                        track = n.optString("track", "SHAPES"),
                        bbox = bbox,
                        color = n.optString("color").ifEmpty { null },
                        text = n.optString("text").ifEmpty { null }
                    )
                )
            }
            val errs = o.optJSONArray("errors")?.let { e ->
                (0 until e.length()).map { e.optString(it) }
            } ?: emptyList()
            val libsObj = o.optJSONObject("libs")
            val libs = LinkedHashMap<String, String>()
            libsObj?.keys()?.forEach { k ->
                val v = libsObj.optString(k)
                if (v.isNotEmpty() && v != "null") libs[k] = v
            }
            Scene(
                name = o.optString("name", "Untitled"),
                path = o.optString("path", ""),
                duration = o.optDouble("duration", 8.0).toFloat(),
                fps = o.optDouble("fps", 30.0).toFloat(),
                width = o.optInt("width", 1080),
                height = o.optInt("height", 1920),
                nodes = nodes,
                errors = errs,
                libs = libs
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun errorOf(raw: String): String? = try {
        val o = JSONObject(raw)
        if (o.optBoolean("ok", false)) null else o.optString("error").take(600)
    } catch (t: Throwable) {
        raw.take(600)
    }
}
