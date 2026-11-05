package ai.xp.editor.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ai.xp.editor.R

/**
 * XP palette. Cream paper, mint accent, ink for type — matched to the
 * reference screenshot: dark phone cards on cream, mint pills, small stars.
 */
object Xp {
    val cream = Color(0xFFF2F0DC)
    val creamDeep = Color(0xFFE7E2C7)
    val paper = Color(0xFFFBF9EE)
    val mint = Color(0xFFCDE0A5)
    val mintDeep = Color(0xFFB4CD84)
    val ink = Color(0xFF1A202C)
    val inkSoft = Color(0xFF2A3242)
    val inkMute = Color(0xFF5B6478)
    val rose = Color(0xFFE8A0A0)
    val sky = Color(0xFFA9C7E8)
    val sand = Color(0xFFE4D9B8)
    val gold = Color(0xFFE8C878)
    val danger = Color(0xFFD9776B)

    val swatches = listOf(
        "#1A202C", "#CDE0A5", "#F2F0DC", "#E8A0A0",
        "#A9C7E8", "#E4D9B8", "#E8C878", "#FFFFFF"
    )
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

val Lora = FontFamily(
    Font(R.font.lora_variable, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.lora_italic_variable, FontWeight.Normal, FontStyle.Italic)
)

val LoraItalic = FontFamily(
    Font(R.font.lora_italic_variable, FontWeight.Normal, FontStyle.Italic)
)

/** Single text primitive — every string in XP goes through here. */
@Composable
fun XpText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color = Xp.ink,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Poppins,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: TextUnit = 0.sp,
    italic: Boolean = false,
    lineHeight: Int = 0
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = family,
            textAlign = align,
            letterSpacing = letterSpacing,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified
        )
    )
}

@Composable
fun Display(text: String, modifier: Modifier = Modifier, size: Int = 44, color: Color = Xp.ink) =
    XpText(text, modifier, size = size, color = color, weight = FontWeight.Bold, letterSpacing = (-1).sp)

@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: Int = 18, color: Color = Xp.ink) =
    XpText(text, modifier, size = size, color = color, weight = FontWeight.SemiBold)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, size: Int = 14, color: Color = Xp.inkMute) =
    XpText(text, modifier, size = size, color = color, weight = FontWeight.Normal)

@Composable
fun Accent(text: String, modifier: Modifier = Modifier, size: Int = 15, color: Color = Xp.inkMute) =
    XpText(text, modifier, size = size, color = color, family = LoraItalic, italic = true)

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, size: Int = 10, color: Color = Xp.inkMute) =
    XpText(text, modifier, size = size, color = color, weight = FontWeight.Bold, letterSpacing = 1.4.sp)
