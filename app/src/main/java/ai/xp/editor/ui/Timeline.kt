package ai.xp.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ai.xp.editor.core.XpState
import ai.xp.editor.design.Haptics
import ai.xp.editor.design.Xp
import ai.xp.editor.model.SceneNode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MODE_NONE = 0
private const val MODE_SCRUB = 1
private const val MODE_MOVE = 2
private const val MODE_TRIM_L = 3
private const val MODE_TRIM_R = 4

private class Hit(val mode: Int, val node: String?)

private fun trackColor(track: String): Color = when (track) {
    "VIDEO" -> Xp.ink
    "MEDIA" -> Xp.sky
    "TEXT" -> Xp.mint
    "SHAPES" -> Xp.sand
    "AUDIO" -> Xp.rose
    else -> Xp.mint
}

/**
 * CapCut-style multi-track timeline: ruler, stacked tracks, clips, playhead.
 * Fling/pinch to zoom, drag to scrub, drag a clip to move it, drag its edge to
 * trim, long-press to jump, tap to select.
 */
@Composable
fun TimelineView(state: XpState, modifier: Modifier = Modifier) {
    val scene = state.scene ?: return
    val tracks = scene.tracks
    val rowCount = tracks.size.coerceAtLeast(1)

    var dragMode by remember { mutableIntStateOf(MODE_NONE) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var anchorT0 by remember { mutableFloatStateOf(0f) }
    var anchorT1 by remember { mutableFloatStateOf(0f) }
    var acc by remember { mutableFloatStateOf(0f) }
    var pinchBase by remember { mutableFloatStateOf(0f) }

    fun eff(node: SceneNode): Pair<Float, Float> {
        val e = state.edits[node.id]
        return Pair(e?.t0 ?: node.t0, e?.t1 ?: node.t1)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height((22 + 30 * rowCount).dp)
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height((22 + 30 * rowCount).dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { off ->
                            val hit = hitTest(
                                off.x, off.y, 52.dp.toPx(), 22.dp.toPx(), 30.dp.toPx(),
                                tracks, scene, state.zoom, state.scroll, ::eff
                            )
                            if (hit.mode == MODE_MOVE && hit.node != null) {
                                state.selected = hit.node
                                Haptics.select()
                            } else if (off.y < 22.dp.toPx()) {
                                val t = ((off.x - 52.dp.toPx() + state.scroll) / state.zoom)
                                state.engine.seek(t.coerceIn(0f, scene.duration))
                                Haptics.tick()
                            } else {
                                state.selected = null
                            }
                        },
                        onLongPress = { off ->
                            val hit = hitTest(
                                off.x, off.y, 52.dp.toPx(), 22.dp.toPx(), 30.dp.toPx(),
                                tracks, scene, state.zoom, state.scroll, ::eff
                            )
                            if (hit.node != null) {
                                val n = scene.node(hit.node)
                                if (n != null) {
                                    state.selected = n.id
                                    state.engine.seek(eff(n).first)
                                    Haptics.thud()
                                }
                            } else {
                                val t = ((off.x - 52.dp.toPx() + state.scroll) / state.zoom)
                                state.engine.seek(t.coerceIn(0f, scene.duration))
                                Haptics.thud()
                            }
                        },
                        onDoubleTap = {
                            state.zoom = 64f
                            state.scroll = 0f
                            Haptics.tap()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val newZoom = (state.zoom * zoomChange).coerceIn(16f, 460f)
                        state.zoom = newZoom
                        state.scroll = (state.scroll - pan.x).coerceIn(0f, maxScroll(state, scene.duration))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off ->
                            val hit = hitTest(
                                off.x, off.y, 52.dp.toPx(), 22.dp.toPx(), 30.dp.toPx(),
                                tracks, scene, state.zoom, state.scroll, ::eff
                            )
                            dragMode = hit.mode
                            dragId = hit.node
                            acc = 0f
                            val n = hit.node?.let { scene.node(it) }
                            if (n != null) {
                                val (a, b) = eff(n)
                                anchorT0 = a
                                anchorT1 = b
                                if (hit.mode != MODE_SCRUB) state.selected = n.id
                            }
                        },
                        onDragEnd = {
                            if (dragMode == MODE_MOVE || dragMode == MODE_TRIM_L || dragMode == MODE_TRIM_R) {
                                state.pushEdits()
                                state.saveEdits()
                                Haptics.snap()
                            }
                            dragMode = MODE_NONE
                            dragId = null
                        },
                        onDragCancel = {
                            dragMode = MODE_NONE
                            dragId = null
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            acc += delta.x
                            val dt = acc / state.zoom
                            when (dragMode) {
                                MODE_SCRUB -> {
                                    val t = ((change.position.x - 52.dp.toPx() + state.scroll) / state.zoom)
                                    state.engine.seek(t.coerceIn(0f, scene.duration))
                                }
                                MODE_MOVE -> {
                                    val id = dragId ?: return@detectDragGestures
                                    val len = (anchorT1 - anchorT0).coerceAtLeast(0.12f)
                                    val nt0 = (anchorT0 + dt).coerceIn(0f, (scene.duration - len).coerceAtLeast(0f))
                                    val e = state.editFor(id)
                                    e.t0 = nt0
                                    e.t1 = nt0 + len
                                }
                                MODE_TRIM_L -> {
                                    val id = dragId ?: return@detectDragGestures
                                    val nt0 = (anchorT0 + dt).coerceIn(0f, anchorT1 - 0.12f)
                                    val e = state.editFor(id)
                                    e.t0 = nt0
                                    e.t1 = anchorT1
                                }
                                MODE_TRIM_R -> {
                                    val id = dragId ?: return@detectDragGestures
                                    val nt1 = (anchorT1 + dt).coerceIn(anchorT0 + 0.12f, scene.duration)
                                    val e = state.editFor(id)
                                    e.t0 = anchorT0
                                    e.t1 = nt1
                                }
                            }
                        }
                    )
                }
        ) {
            val labelPx = 52.dp.toPx()
            val rulerPx = 22.dp.toPx()
            val rowPx = 30.dp.toPx()
            val zoom = state.zoom
            val scroll = state.scroll
            val w = size.width
            val h = size.height

            // ruler
            drawRect(Xp.creamDeep, size = Size(w, rulerPx))
            val tStart = scroll / zoom
            val tEnd = (scroll + (w - labelPx)) / zoom
            val step = when {
                zoom > 220 -> 0.25f
                zoom > 110 -> 0.5f
                zoom > 46 -> 1f
                else -> 2f
            }
            var s = floor(tStart / step) * step
            while (s <= tEnd + step) {
                val x = labelPx + s * zoom - scroll
                if (x >= labelPx) {
                    drawLine(
                        Xp.ink.copy(alpha = 0.22f),
                        Offset(x, rulerPx - 6.dp.toPx()),
                        Offset(x, rulerPx),
                        strokeWidth = 1f
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        fmtTime(s), x + 3.dp.toPx(), rulerPx - 7.dp.toPx(), textPaint(9, Xp.inkMute)
                    )
                }
                s += step
            }

            // track rows
            tracks.forEachIndexed { i, tr ->
                val y = rulerPx + i * rowPx
                drawRect(
                    if (i % 2 == 0) Xp.paper else Xp.creamDeep.copy(alpha = 0.75f),
                    topLeft = Offset(0f, y), size = Size(w, rowPx)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    tr, 8.dp.toPx(), y + rowPx * 0.62f, textPaint(8, Xp.inkMute.copy(alpha = 0.8f))
                )
                drawLine(
                    Xp.ink.copy(alpha = 0.06f), Offset(0f, y + rowPx), Offset(w, y + rowPx),
                    strokeWidth = 1f
                )
            }

            // clips
            tracks.forEachIndexed { i, tr ->
                val y = rulerPx + i * rowPx + 4.dp.toPx()
                val clipH = rowPx - 8.dp.toPx()
                scene.nodesOn(tr).forEach { n ->
                    val (a, b) = eff(n)
                    val x0 = labelPx + a * zoom - scroll
                    val x1 = labelPx + b * zoom - scroll
                    if (x1 < labelPx || x0 > w) return@forEach
                    val left = x0.coerceAtLeast(labelPx)
                    val right = x1.coerceAtMost(w)
                    val isSel = state.selected == n.id
                    val col = trackColor(tr)
                    drawRoundRect(
                        color = col.copy(alpha = if (isSel) 0.95f else 0.8f),
                        topLeft = Offset(left, y),
                        size = Size((right - left).coerceAtLeast(2f), clipH),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    if (isSel) {
                        drawRoundRect(
                            color = Xp.ink,
                            topLeft = Offset(left, y),
                            size = Size((right - left).coerceAtLeast(2f), clipH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                        drawRoundRect(
                            color = Xp.ink,
                            topLeft = Offset(left, y),
                            size = Size(3.dp.toPx(), clipH),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                        )
                        drawRoundRect(
                            color = Xp.ink,
                            topLeft = Offset(right - 3.dp.toPx(), y),
                            size = Size(3.dp.toPx(), clipH),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                        )
                    }
                    if (right - left > 34.dp.toPx()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            n.label.take(18),
                            left + 6.dp.toPx(),
                            y + clipH * 0.66f,
                            textPaint(9, if (tr == "VIDEO") Xp.cream else Xp.ink)
                        )
                    }
                }
            }

            // playhead
            val px = labelPx + state.engine.playhead.value * zoom - scroll
            if (px >= labelPx - 2 && px <= w + 2) {
                drawLine(Xp.ink, Offset(px, 0f), Offset(px, h), strokeWidth = 2.dp.toPx())
                drawCircle(Xp.ink, radius = 5.dp.toPx(), center = Offset(px, rulerPx - 4.dp.toPx()))
                drawCircle(Xp.cream, radius = 2.dp.toPx(), center = Offset(px, rulerPx - 4.dp.toPx()))
            }

            // left gutter cover
            drawRect(Xp.cream, size = Size(labelPx, h))
            drawRect(Xp.creamDeep, size = Size(labelPx, rulerPx))
            tracks.forEachIndexed { i, tr ->
                val y = rulerPx + i * rowPx
                drawContext.canvas.nativeCanvas.drawText(
                    tr, 8.dp.toPx(), y + rowPx * 0.62f, textPaint(8, Xp.inkMute)
                )
            }
            drawLine(
                Xp.ink.copy(alpha = 0.1f), Offset(labelPx, 0f), Offset(labelPx, h),
                strokeWidth = 1f
            )
        }
    }
}

private fun maxScroll(state: XpState, duration: Float): Float =
    (duration * state.zoom).coerceAtLeast(0f)

private fun fmtTime(t: Float): String {
    val total = t.coerceAtLeast(0f)
    val s = total.toInt()
    val f = ((total - s) * 100).roundToInt()
    return "%d:%02d".format(s / 60, s % 60) + if (f > 0) ".%02d".format(f) else ""
}

private fun textPaint(sizeSp: Int, color: Color): android.graphics.Paint {
    val p = android.graphics.Paint()
    p.isAntiAlias = true
    p.textSize = sizeSp * 3.1f
    p.color = color.toArgb()
    p.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    return p
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt().coerceIn(0, 255),
    (red * 255).roundToInt().coerceIn(0, 255),
    (green * 255).roundToInt().coerceIn(0, 255),
    (blue * 255).roundToInt().coerceIn(0, 255)
)

private fun hitTest(
    x: Float, y: Float, labelPx: Float, rulerPx: Float, rowPx: Float,
    tracks: List<String>, scene: ai.xp.editor.model.Scene, zoom: Float, scroll: Float,
    eff: (SceneNode) -> Pair<Float, Float>
): Hit {
    if (y < rulerPx) return Hit(MODE_SCRUB, null)
    val row = ((y - rulerPx) / rowPx).toInt()
    if (row < 0 || row >= tracks.size) return Hit(MODE_SCRUB, null)
    val tr = tracks[row]
    val t = (x - labelPx + scroll) / zoom
    val tolerance = 12f / zoom
    var best: SceneNode? = null
    var bestMode = MODE_NONE
    scene.nodesOn(tr).forEach { n ->
        val (a, b) = eff(n)
        if (t >= a - tolerance && t <= b + tolerance) {
            best = n
            bestMode = when {
                kotlin.math.abs(t - a) < tolerance -> MODE_TRIM_L
                kotlin.math.abs(t - b) < tolerance -> MODE_TRIM_R
                else -> MODE_MOVE
            }
        }
    }
    val n = best
    return if (n != null) Hit(bestMode, n.id) else Hit(MODE_SCRUB, null)
}
