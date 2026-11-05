package ai.xp.editor.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Hand-tuned springs. XP uses no Material defaults: every transition here is
 * a damping/stiffness pair chosen for feel — snappy for taps, overshooting
 * for entrances, heavy for sheets.
 */
object XpSpring {
    val press: AnimationSpec<Float> = spring(dampingRatio = 0.42f, stiffness = 1200f)
    val snappy: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 700f)
    val gentle: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 220f)
    val overshoot: AnimationSpec<Float> = spring(dampingRatio = 0.38f, stiffness = 340f)
    val sheet: AnimationSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 300f)
    val thud: AnimationSpec<Float> = spring(dampingRatio = 0.95f, stiffness = 1500f)
}

/** Staggered entrance: rise + fade + slight scale, index drives the delay. */
@Composable
fun Modifier.xpEnter(index: Int = 0, rise: Float = 20f, from: Float = 0.965f, delayMs: Long = 40L): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs + index * 45L)
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "xpEnter"
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * rise
        scaleX = from + (1f - from) * p
        scaleY = from + (1f - from) * p
    }
}

/** A soft press-scale for anything tappable. */
@Composable
fun pressScale(pressed: Boolean, amount: Float = 0.955f): Float {
    val s by animateFloatAsState(
        targetValue = if (pressed) amount else 1f,
        animationSpec = XpSpring.press,
        label = "pressScale"
    )
    return s
}

// ---------------------------------------------------------------- OKLab lerp
// Interpolating in sRGB makes cream→ink go muddy. OKLab keeps the hue and the
// perceived lightness ramp clean, which is what the mint pills need.

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

fun Color.toOklab(): FloatArray {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
    val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
    val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
    val l_ = Math.cbrt(l.toDouble()).toFloat()
    val m_ = Math.cbrt(m.toDouble()).toFloat()
    val s_ = Math.cbrt(s.toDouble()).toFloat()
    return floatArrayOf(
        0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
        1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
        0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_,
        alpha
    )
}

fun oklabToColor(lab: FloatArray): Color {
    val l_ = lab[0] + 0.3963377774f * lab[1] + 0.2158037573f * lab[2]
    val m_ = lab[0] - 0.1055613458f * lab[1] - 0.0638541728f * lab[2]
    val s_ = lab[0] - 0.0894841775f * lab[1] - 1.2914855480f * lab[2]
    val l = l_ * l_ * l_
    val m = m_ * m_ * m_
    val s = s_ * s_ * s_
    val r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    val g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    val b = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
    return Color(
        red = linearToSrgb(r.coerceIn(0f, 1f)),
        green = linearToSrgb(g.coerceIn(0f, 1f)),
        blue = linearToSrgb(b.coerceIn(0f, 1f)),
        alpha = lab[3].coerceIn(0f, 1f)
    )
}

fun lerpOklab(a: Color, b: Color, t: Float): Color {
    if (t <= 0f) return a
    if (t >= 1f) return b
    val la = a.toOklab()
    val lb = b.toOklab()
    return oklabToColor(
        floatArrayOf(
            la[0] + (lb[0] - la[0]) * t,
            la[1] + (lb[1] - la[1]) * t,
            la[2] + (lb[2] - la[2]) * t,
            la[3] + (lb[3] - la[3]) * t
        )
    )
}

@Composable
fun animateXpColor(target: Color, spec: AnimationSpec<Float> = XpSpring.snappy): Color {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target != to) {
            from = lerpOklab(from, to, p.value)
            to = target
            p.snapTo(0f)
            p.animateTo(1f, spec)
        }
    }
    return lerpOklab(from, to, p.value)
}
