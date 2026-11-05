package ai.xp.editor.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * XP's own widget kit. Nothing here is a Material component: surfaces are
 * rounded boxes with an offset shadow, controls are drawn on a Canvas, and
 * every press runs through the XP spring + haptic pair.
 */

fun Modifier.xpPress(onClick: () -> Unit, haptic: () -> Unit = { Haptics.tap() }): Modifier =
    this.pointerInput(onClick) {
        detectTapGestures(
            onPress = { haptic() },
            onTap = { onClick() }
        )
    }

@Composable
fun Modifier.pressable(onClick: () -> Unit, amount: Float = 0.955f, haptic: () -> Unit = { Haptics.tap() }): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s = pressScale(pressed, amount)
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = interaction, indication = null) {
            haptic()
            onClick()
        }
}

/** Flat offset shadow — the look from the reference, and free to render. */
fun Modifier.xpShadow(offset: Dp = 4.dp, radius: Dp = 18.dp, color: Color = Color(0x1F1A202C)): Modifier =
    this.drawBehind {
        if (offset <= 0.dp) return@drawBehind
        val d = offset.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(d, d),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
        )
    }

@Composable
fun XpPanel(
    modifier: Modifier = Modifier,
    color: Color = Xp.paper,
    radius: Dp = 20.dp,
    border: Color? = null,
    shadow: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .xpShadow(shadow, radius)
            .clip(RoundedCornerShape(radius))
            .background(color)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(radius)) else Modifier)
    ) { content() }
}

@Composable
fun MintPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    dark: Boolean = false,
    size: Int = 13,
    onClick: (() -> Unit)? = null
) {
    val bg = when {
        dark -> Xp.ink
        selected -> Xp.mint
        else -> Color.Transparent
    }
    val fg = when {
        dark -> Xp.mint
        selected -> Xp.ink
        else -> Xp.inkMute
    }
    val base = modifier
        .clip(CircleShape)
        .background(bg)
        .then(
            if (!selected && !dark) Modifier.border(1.dp, Xp.ink.copy(alpha = 0.14f), CircleShape)
            else Modifier
        )
        .padding(horizontal = 14.dp, vertical = 8.dp)
    Box(if (onClick != null) base.pressable(onClick, 0.93f) else base) {
        XpText(text, size = size, color = fg, weight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    }
}

@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Xp.ink,
    labelColor: Color = Xp.cream,
    onClick: () -> Unit
) {
    val bg = if (enabled) tint else tint.copy(alpha = 0.35f)
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick, 0.96f)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        XpText(text, size = 14, color = labelColor, weight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    }
}

@Composable
fun Segmented(
    items: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Xp.ink.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) Xp.ink else Color.Transparent)
                    .pressable({ onSelect(i) }, 0.95f, { Haptics.select() })
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                XpText(
                    label, size = 12,
                    color = if (on) Xp.cream else Xp.inkMute,
                    weight = if (on) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun XpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    tint: Color = Xp.ink,
    track: Color = Xp.ink.copy(alpha = 0.12f),
    label: String? = null,
    format: (Float) -> String = { "%.2f".format(it) }
) {
    var width by remember { mutableIntStateOf(1) }
    val span = (range.endInclusive - range.start).takeIf { it != 0f } ?: 1f
    val p = ((value - range.start) / span).coerceIn(0f, 1f)

    fun emit(x: Float) {
        val f = (x / width).coerceIn(0f, 1f)
        onValueChange(range.start + f * span)
    }

    Column(modifier) {
        if (label != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Kicker(label, size = 9, color = Xp.inkMute)
                XpText(format(value), size = 11, color = Xp.ink, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { width = it.width }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { off -> emit(off.x); Haptics.tick() })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { off -> emit(off.x) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            emit(change.position.x)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = size.height / 2f
                drawRoundRect(track, size = size, cornerRadius = CornerRadius(r, r))
                drawRoundRect(
                    tint, size = Size(size.width * p, size.height),
                    cornerRadius = CornerRadius(r, r)
                )
                val cx = (size.width * p).coerceIn(r, size.width - r)
                drawCircle(Xp.paper, radius = size.height * 1.15f, center = Offset(cx, size.height / 2f))
                drawCircle(tint, radius = size.height * 0.72f, center = Offset(cx, size.height / 2f))
            }
        }
    }
}

@Composable
fun XpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    color: Color = Xp.ink
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Xp.ink.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            XpText(hint, size = 13, color = Xp.inkMute.copy(alpha = 0.6f))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = color, fontSize = 13.sp, fontFamily = Poppins,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Xp.ink),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun XpToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, tint: Color = Xp.mint) {
    val knob by animateFloatAsStateCompat(if (checked) 1f else 0f)
    Box(
        modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (checked) tint else Xp.ink.copy(alpha = 0.15f))
            .pressable({ onChange(!checked) }, 0.96f),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = knob * 20f }
                .padding(start = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Xp.ink else Xp.paper)
        )
    }
}

@Composable
private fun animateFloatAsStateCompat(target: Float): androidx.compose.runtime.State<Float> =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = XpSpring.snappy,
        label = "knob"
    )

/** Deterministic star field — same scatter every launch, no RNG drift. */
@Composable
fun StarField(
    modifier: Modifier = Modifier,
    count: Int = 18,
    color: Color = Xp.mintDeep,
    seed: Int = 7,
    alpha: Float = 0.55f
) {
    Canvas(modifier) {
        var s = seed
        repeat(count) {
            s = (1103515245 * s + 12345) and 0x7FFFFFFF
            val x = (s % 1000) / 1000f * size.width
            s = (1103515245 * s + 12345) and 0x7FFFFFFF
            val y = (s % 1000) / 1000f * size.height
            s = (1103515245 * s + 12345) and 0x7FFFFFFF
            val r = 1.6f + (s % 1000) / 1000f * 3.4f
            drawStar(Offset(x, y), r, color.copy(alpha = alpha * (0.5f + (s % 100) / 200f)))
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset, radius: Float, color: Color, points: Int = 4, inner: Float = 0.28f
) {
    val path = androidx.compose.ui.graphics.Path()
    val n = points * 2
    for (i in 0 until n) {
        val ang = (-Math.PI / 2) + i * (2 * Math.PI / n)
        val rad = if (i % 2 == 0) radius else radius * inner
        val px = center.x + (Math.cos(ang) * rad).toFloat()
        val py = center.y + (Math.sin(ang) * rad).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}

@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = Xp.ink,
    track: Color = Xp.ink.copy(alpha = 0.12f),
    stroke: Dp = 8.dp,
    label: String? = null
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - sw, size.height - sw),
                style = Stroke(width = sw)
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - sw, size.height - sw),
                style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        if (label != null) {
            XpText(label, size = 13, color = Xp.ink, weight = FontWeight.Bold)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Xp.ink else Color.Transparent)
            .then(
                if (!selected) Modifier.border(1.dp, Xp.ink.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .pressable(onClick, 0.95f, { Haptics.select() })
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        XpText(text, size = 11, color = if (selected) Xp.cream else Xp.inkMute, weight = FontWeight.Medium)
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier, alpha: Float = 0.12f) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Xp.ink.copy(alpha = alpha)))
}

fun Int.toHexColor(): String = "#%06X".format(this and 0xFFFFFF)

fun percent(v: Float): String = "${(v * 100).roundToInt()}%"
