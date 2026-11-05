package ai.xp.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.xp.editor.core.Screen
import ai.xp.editor.core.XpState
import ai.xp.editor.design.Accent
import ai.xp.editor.design.Body
import ai.xp.editor.design.Chip
import ai.xp.editor.design.Haptics
import ai.xp.editor.design.HairLine
import ai.xp.editor.design.InkButton
import ai.xp.editor.design.Kicker
import ai.xp.editor.design.MintPill
import ai.xp.editor.design.Poppins
import ai.xp.editor.design.Segmented
import ai.xp.editor.design.Title
import ai.xp.editor.design.Xp
import ai.xp.editor.design.XpField
import ai.xp.editor.design.XpPanel
import ai.xp.editor.design.XpSlider
import ai.xp.editor.design.XpText
import ai.xp.editor.design.pressable
import ai.xp.editor.design.xpEnter
import ai.xp.editor.model.Keyframe
import ai.xp.editor.model.SceneNode
import kotlin.math.roundToInt

@Composable
fun EditorScreen(state: XpState) {
    val scene = state.scene
    if (scene == null) {
        LaunchedEffect(Unit) { state.screen = Screen.Home }
        return
    }

    val selectedNode = scene.node(state.selected)

    Column(Modifier.fillMaxSize()) {
        // ---- top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Xp.ink.copy(alpha = 0.06f))
                    .pressable({
                        state.saveEdits()
                        state.screen = Screen.Home
                    }),
                contentAlignment = Alignment.Center
            ) { XpText("‹", size = 20) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                XpText(scene.name, size = 16, weight = FontWeight.Bold, maxLines = 1)
                XpText(
                    "${scene.nodes.size} objects · ${"%.1f".format(scene.duration)}s",
                    size = 10, color = Xp.inkMute
                )
            }
            MintPill("Export", dark = true, onClick = {
                state.saveEdits()
                state.screen = Screen.Export
            })
        }

        // ---- preview
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            PreviewCanvas(state, scene.width, scene.height)
        }

        // ---- transport
        TransportBar(state)

        // ---- tabs
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Segmented(
                items = listOf("Video", "Text", "Shapes", "Audio"),
                selected = state.editorTab,
                onSelect = { state.editorTab = it; Haptics.select() }
            )
            Spacer(Modifier.weight(1f))
            RenderBadge(state)
        }

        // ---- timeline
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            TimelineView(state)
        }

        Spacer(Modifier.height(8.dp))

        // ---- property sheet
        if (selectedNode != null) {
            PropertySheet(state, selectedNode, Modifier.fillMaxWidth().heightIn(max = 290.dp))
        } else {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Accent("tap a clip to edit it · pinch to zoom · long-press to jump", size = 12)
            }
        }
    }
}

@Composable
fun PreviewCanvas(state: XpState, srcW: Int, srcH: Int) {
    val aspect = if (srcW > 0) srcH.toFloat() / srcW.toFloat() else 16f / 9f
    XpPanel(
        Modifier.fillMaxSize().aspectRatio(1f / aspect),
        color = Xp.ink,
        radius = 22.dp,
        shadow = 6.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            val frame = state.engine.frame.value
            Canvas(Modifier.fillMaxSize()) {
                if (frame != null) {
                    drawImage(
                        image = frame,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                } else {
                    drawRect(Xp.inkSoft)
                }
            }

            if (state.showGuides) {
                Canvas(Modifier.fillMaxSize()) {
                    val g = Xp.cream.copy(alpha = 0.14f)
                    drawRect(g, topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
                        size = Size(size.width * 0.9f, size.height * 0.9f),
                        style = Stroke(width = 1f))
                    drawLine(g, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f)
                    drawLine(g, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                }
            }

            // badges
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Xp.ink.copy(alpha = 0.55f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    XpText(
                        when (state.previewQuality) {
                            0 -> "540p"
                            2 -> "1080p"
                            else -> "720p"
                        },
                        size = 9, color = Xp.cream
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Xp.ink.copy(alpha = 0.55f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    XpText(
                        "${state.engine.previewH}p",
                        size = 9, color = Xp.cream
                    )
                }
            }

            // selection marquee of the selected node
            val node = state.scene?.node(state.selected)
            if (node != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val b = node.bbox
                    if (b.size >= 4) {
                        drawRect(
                            Xp.mint,
                            topLeft = Offset(b[0] * size.width, b[1] * size.height),
                            size = Size(
                                (b[2] * size.width).coerceAtLeast(2f),
                                (b[3] * size.height).coerceAtLeast(2f)
                            ),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransportBar(state: XpState) {
    val playing = state.engine.playing.value
    val t = state.engine.playhead.value
    val dur = state.scene?.duration ?: 8f

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Xp.ink)
                .pressable({
                    state.engine.togglePlay()
                    Haptics.tap()
                }),
            contentAlignment = Alignment.Center
        ) {
            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.width(3.dp).height(12.dp).background(Xp.mint))
                    Box(Modifier.width(3.dp).height(12.dp).background(Xp.mint))
                }
            } else {
                Canvas(Modifier.size(14.dp)) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(2f, 1f); lineTo(size.width - 1f, size.height / 2f); lineTo(2f, size.height - 1f)
                        close()
                    }
                    drawPath(p, Xp.mint)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            XpText("${fmt(t)} / ${fmt(dur)}", size = 13, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    .background(Xp.ink.copy(alpha = 0.1f))
            ) {
                Box(
                    Modifier.fillMaxWidth((t / dur).coerceIn(0f, 1f)).height(4.dp)
                        .clip(CircleShape).background(Xp.ink)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (state.loopOn) Xp.mint else Xp.ink.copy(alpha = 0.06f))
                .pressable({
                    state.loopOn = !state.loopOn
                    state.engine.loop = state.loopOn
                }),
            contentAlignment = Alignment.Center
        ) { XpText("↻", size = 15, color = Xp.ink) }
    }
}

/** Isolated so a new frame time does not recompose the whole editor. */
@Composable
private fun RenderBadge(state: XpState) {
    val ms = state.engine.renderMs.value
    val frames = state.engine.renderedFrames.value
    XpText("${ms}ms · ${frames}f", size = 10, color = Xp.inkMute)
}

private fun fmt(t: Float): String {
    val s = t.coerceAtLeast(0f)
    val m = (s / 60).toInt()
    val sec = s.toInt() % 60
    val cs = ((s - s.toInt()) * 100).toInt()
    return "%d:%02d.%02d".format(m, sec, cs)
}

@Composable
fun PropertySheet(state: XpState, node: SceneNode, modifier: Modifier = Modifier) {
    val edit = state.editFor(node.id)
    val scene = state.scene ?: return

    XpPanel(modifier.padding(horizontal = 12.dp), color = Xp.paper, radius = 22.dp, shadow = 4.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                        .background(parseHex(node.color) ?: Xp.mint)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    XpText(node.label, size = 14, weight = FontWeight.SemiBold, maxLines = 1)
                    XpText(
                        "${node.kind} · ${node.track.lowercase()} · ${node.id.take(22)}",
                        size = 10, color = Xp.inkMute, maxLines = 1
                    )
                }
                Chip("Reset", false, onClick = {
                    edit.reset()
                    state.pushEdits()
                    Haptics.thud()
                })
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Xp.ink.copy(alpha = 0.06f))
                        .pressable({ state.selected = null }),
                    contentAlignment = Alignment.Center
                ) { XpText("×", size = 14) }
            }

            Spacer(Modifier.height(10.dp))

            Segmented(
                items = listOf("Transform", "Style", "Timing", "Keys"),
                selected = state.sheetTab,
                onSelect = { state.sheetTab = it }
            )

            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
            ) {
                when (state.sheetTab) {
                    0 -> TransformTab(state, edit)
                    1 -> StyleTab(state, node, edit)
                    2 -> TimingTab(state, node, edit, scene.duration)
                    else -> KeysTab(state, edit)
                }
            }
        }
    }
}

@Composable
private fun TransformTab(state: XpState, edit: ai.xp.editor.model.NodeEdit) {
    Column {
        XpSlider(
            value = edit.dx, onValueChange = { edit.dx = it; state.pushEdits() },
            range = -0.5f..0.5f, label = "X", format = { "%.2f".format(it) }
        )
        Spacer(Modifier.height(8.dp))
        XpSlider(
            value = edit.dy, onValueChange = { edit.dy = it; state.pushEdits() },
            range = -0.5f..0.5f, label = "Y", format = { "%.2f".format(it) }
        )
        Spacer(Modifier.height(8.dp))
        XpSlider(
            value = edit.scale, onValueChange = { edit.scale = it; state.pushEdits() },
            range = 0.2f..3f, label = "Scale", format = { "%.2fx".format(it) }
        )
        Spacer(Modifier.height(8.dp))
        XpSlider(
            value = edit.rot, onValueChange = { edit.rot = it; state.pushEdits() },
            range = -180f..180f, label = "Rotation", format = { "%.0f°".format(it) }
        )
        Spacer(Modifier.height(8.dp))
        XpSlider(
            value = edit.opacity, onValueChange = { edit.opacity = it; state.pushEdits() },
            range = 0f..1f, label = "Opacity", format = { "${(it * 100).roundToInt()}%" }
        )
    }
}

@Composable
private fun StyleTab(state: XpState, node: SceneNode, edit: ai.xp.editor.model.NodeEdit) {
    Column {
        if (node.kind == "text") {
            Kicker("TEXT", size = 9)
            Spacer(Modifier.height(6.dp))
            XpField(
                value = edit.text ?: node.text ?: "",
                onValueChange = { edit.text = it; state.pushEdits() },
                hint = node.text ?: "type here"
            )
            Spacer(Modifier.height(12.dp))
        }
        Kicker("COLOUR", size = 9)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Xp.swatches.take(6).forEach { hex ->
                val on = (edit.color ?: node.color)?.equals(hex, true) == true
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(parseHex(hex) ?: Xp.mint)
                        .pressable({
                            edit.color = hex
                            state.pushEdits()
                            Haptics.select()
                        })
                ) {
                    if (on) {
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) { XpText("✓", size = 12, color = if (hex == "#F2F0DC") Xp.ink else Xp.cream) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Original", edit.color == null, onClick = { edit.color = null; state.pushEdits() })
            Chip("Copy hex", false, onClick = { Haptics.tap() })
        }
    }
}

@Composable
private fun TimingTab(
    state: XpState, node: SceneNode, edit: ai.xp.editor.model.NodeEdit, duration: Float
) {
    val t0 = edit.t0 ?: node.t0
    val t1 = edit.t1 ?: node.t1
    Column {
        XpSlider(
            value = t0, onValueChange = { edit.t0 = it.coerceAtMost(t1 - 0.1f); state.pushEdits() },
            range = 0f..duration, label = "In", format = { "%.2fs".format(it) }
        )
        Spacer(Modifier.height(8.dp))
        XpSlider(
            value = t1, onValueChange = { edit.t1 = it.coerceAtLeast(t0 + 0.1f); state.pushEdits() },
            range = 0f..duration, label = "Out", format = { "%.2fs".format(it) }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("In = playhead", false, onClick = {
                edit.t0 = state.engine.playhead.value
                state.pushEdits()
            })
            Chip("Out = playhead", false, onClick = {
                edit.t1 = state.engine.playhead.value
                state.pushEdits()
            })
        }
        Spacer(Modifier.height(8.dp))
        Body("Duration ${"%.2f".format((t1 - t0).coerceAtLeast(0f))}s", size = 11)
    }
}

@Composable
private fun KeysTab(state: XpState, edit: ai.xp.editor.model.NodeEdit) {
    val playhead = state.engine.playhead.value
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MintPill("Add key at ${"%.2f".format(playhead)}s", onClick = {
                edit.kf.add(
                    Keyframe(
                        playhead,
                        mapOf(
                            "dx" to edit.dx, "dy" to edit.dy,
                            "sx" to edit.scale, "sy" to edit.scale,
                            "rot" to edit.rot, "alpha" to edit.opacity
                        )
                    )
                )
                state.pushEdits()
                Haptics.success()
            })
        }
        Spacer(Modifier.height(10.dp))
        if (edit.kf.isEmpty()) {
            Accent("no keys — the transform above is constant", size = 12)
        } else {
            edit.kf.sortedBy { it.t }.forEach { k ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Xp.ink))
                    Spacer(Modifier.width(8.dp))
                    XpText("${"%.2f".format(k.t)}s", size = 12, weight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    XpText(
                        "x ${"%.2f".format(k.p["dx"] ?: 0f)} · s ${"%.2f".format(k.p["sx"] ?: 1f)}",
                        size = 11, color = Xp.inkMute
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(Xp.ink.copy(alpha = 0.06f))
                            .pressable({
                                edit.kf.remove(k)
                                state.pushEdits()
                            }),
                        contentAlignment = Alignment.Center
                    ) { XpText("×", size = 11) }
                }
            }
        }
    }
}

fun parseHex(hex: String?): Color? {
    if (hex == null) return null
    return try {
        val s = hex.removePrefix("#")
        when (s.length) {
            6 -> Color(android.graphics.Color.parseColor("#$s"))
            8 -> Color(android.graphics.Color.parseColor("#$s"))
            else -> null
        }
    } catch (t: Throwable) {
        null
    }
}
