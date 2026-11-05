package ai.xp.editor.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ai.xp.editor.R

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

val Lora = FontFamily(
    Font(R.font.lora_variable, FontWeight.Normal),
    Font(R.font.lora_italic_variable, FontWeight.Normal)
)

@Composable
fun XpText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color = Color(0xFF1A202C),
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Poppins,
    letterSpacing: TextUnit = 0.sp
) {
    Box(modifier) {
        BasicText(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = size.sp,
                fontWeight = weight,
                fontFamily = family,
                letterSpacing = letterSpacing
            )
        )
    }
}
