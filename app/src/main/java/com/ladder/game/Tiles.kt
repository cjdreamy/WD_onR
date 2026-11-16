package com.ladder.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** Design tokens: walnut, antique gold, parchment, stone, forest green, rich red. */
class Theme(val name: String, val top: Color, val bottom: Color, val glow: Color, val leather: Color)
val Themes = listOf(
    Theme("Walnut", Color(0xFFC99556), Color(0xFFE3B97C), Color(0xFF6B4220), Color(0xFF52331E)),
    Theme("Midnight", Color(0xFF2A3A52), Color(0xFF0F1622), Color(0x2A7FB2FF), Color(0xFF34455F)),
    Theme("Forest", Color(0xFF234030), Color(0xFF0D1C14), Color(0x2AB7FF9A), Color(0xFF2F4D3B))
)

object Pal {
    val theme get() = Themes[Prefs.theme]
    val Felt get() = theme.bottom
    val Panel = Color(0xFF35200F); val Rack = Color(0xFF4A2D16); val RackDark = Color(0xFF2A180C)
    val Cream = Color(0xFFF6E9C8); val Muted = Color(0xFFCDB58A); val Ink = Color(0xFF3A2210)
    val InkSoft = Color(0xFF7A5A3A); val Gold = Color(0xFFE2AE3A)
}

enum class Kind(val face: Color, val hi: Color, val edge: Color, val rim: Color, val ink: Color, val grain: Boolean = false) {
    Tile(Color(0xFFC99556), Color(0xFFE3B97C), Color(0xFF6B4220), Color(0xFF4A2B12), Color(0xFF3A2210), true),   // carved oak
    Start(Color(0xFF2F7A45), Color(0xFF52A566), Color(0xFF174A28), Color(0xFF0F2F1A), Color(0xFFF6EFD8)),       // forest green
    Target(Color(0xFFE7B73A), Color(0xFFFFDC72), Color(0xFF9A6A12), Color(0xFF5A3A0A), Color(0xFF4A2B08)),     // gold
    Hot(Color(0xFFC73A2C), Color(0xFFE8604D), Color(0xFF7E1D14), Color(0xFF4A0F0A), Color(0xFFFFF1DC)),        // rich red
    Gold(Color(0xFFF2C23C), Color(0xFFFFE27A), Color(0xFFA9740F), Color(0xFF5A3A0A), Color(0xFF4A2B08)),       // gold button
    Stone(Color(0xFF9A968D), Color(0xFFBCB8AD), Color(0xFF4F4C47), Color(0xFF2F2D2A), Color(0xFF2A2825), true),  // keys
    Ivory(Color(0xFFEFE6CF), Color(0xFFFFF8E6), Color(0xFFA99A76), Color(0xFF6E5F40), Color(0xFF3A2A12)),
    Slate(Color(0xFF5C6878), Color(0xFF7E8B9C), Color(0xFF2E3640), Color(0xFF1A1F26), Color(0xFFEEF2F7))
}

/** Neutral tile looks the player can choose in Settings. */
val Looks = listOf(Kind.Tile, Kind.Ivory, Kind.Slate)

/** Chunky physical button/tile: cast shadow, extruded side, gradient face, gloss, grain, rim. Presses inward. */
@Composable
fun Slab(
    label: String, kind0: Kind, size: Dp, modifier: Modifier = Modifier.width(size),
    fontSize: TextUnit = (size.value * 0.46f).sp, serif: Boolean = false,
    scale: Float = 1f, onClick: (() -> Unit)? = null
) {
    val src = remember { MutableInteractionSource() }
    val kind = if (kind0 == Kind.Tile) Looks[Prefs.look] else kind0
    val pressed by src.collectIsPressedAsState()
    val depth = size * 0.14f
    val drop by animateDpAsState(if (pressed) depth * 0.8f else 0.dp, spring(Spring.DampingRatioMediumBouncy, 1000f), label = "drop")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) scale else 0.5f, spring(Spring.DampingRatioMediumBouncy, 500f), label = "pop")
    val shape = RoundedCornerShape(size * 0.22f)
    val light = kind.ink.luminance() > 0.5f
    Box(
        modifier.graphicsLayer { scaleX = pop; scaleY = pop }.height(size + depth + 5.dp)
            .then(if (onClick != null) Modifier.clickable(src, null) { onClick() } else Modifier)
    ) {
        Box(Modifier.offset(y = depth + 5.dp - drop / 2).fillMaxWidth().height(size).background(Color(0x77000000), shape))
        Box(Modifier.offset(y = depth).fillMaxWidth().height(size).background(kind.edge, shape).border(2.dp, kind.rim, shape))
        Box(
            Modifier.offset(y = drop).fillMaxWidth().height(size)
                .background(Brush.verticalGradient(listOf(kind.hi, kind.face)), shape)
                .drawBehind {
                    val inset = 3.dp.toPx()
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x00FFFFFF)), 0f, this.size.height * 0.5f),
                        Offset(inset, inset), Size(this.size.width - 2 * inset, this.size.height * 0.46f), CornerRadius(this.size.minDimension * 0.18f))
                    if (kind.grain) {
                        val s = label.hashCode()
                        for (k in 0..3) {
                            val y = this.size.height * (0.28f + 0.17f * k); val x0 = this.size.width * (((s + k * 37) and 15) / 40f + 0.08f)
                            drawLine(Color(0x24000000), Offset(x0, y), Offset(this.size.width - x0 * 0.7f, y + (k % 2) * 2f), 1.2.dp.toPx())
                        }
                    }
                }
                .border(2.dp, kind.rim, shape), Alignment.Center
        ) {
            Text(
                label, color = kind.ink, fontSize = fontSize, fontWeight = FontWeight.ExtraBold,
                fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
                style = TextStyle(shadow = Shadow(if (light) Color(0xAA000000) else Color(0x99FFFFFF), Offset(0f, if (light) 3f else 2f), if (light) 4f else 0f))
            )
        }
    }
}

/** Walnut plank wall with seams, grain, warm light and a dark vignette. */
fun Modifier.woodBg(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF402816), Color(0xFF22130A))))
    val ph = 110.dp.toPx(); var y = 0f; var i = 0
    while (y < size.height) {
        drawRect(Color(0x30000000), Offset(0f, y), Size(size.width, 3.dp.toPx()))
        drawRect(Color(0x12FFFFFF), Offset(0f, y + 3.dp.toPx()), Size(size.width, 1.dp.toPx()))
        for (k in 0..4) {
            val gy = y + ph * (0.16f + 0.17f * k); val x0 = size.width * (((i * 53 + k * 97) % 100) / 100f * 0.6f)
            drawLine(Color(0x18000000), Offset(x0, gy), Offset(x0 + size.width * 0.4f, gy + (k % 2) * 2f), 1.5.dp.toPx())
        }
        y += ph; i++
    }
    drawRect(Brush.radialGradient(listOf(Color(0x2AFFB347), Color.Transparent), Offset(size.width / 2, size.height * 0.3f), size.maxDimension * 0.6f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), Offset(size.width / 2, size.height / 2), size.maxDimension * 0.75f))
}

/** Stitched leather information panel. */
fun Modifier.leather(): Modifier {
    val s = RoundedCornerShape(14.dp)
    return background(Brush.verticalGradient(listOf(Pal.theme.leather, Pal.theme.bottom)), s)
        .drawBehind {
            val p = 6.dp.toPx()
            drawRoundRect(Color(0x66E3C48A), Offset(p, p), Size(size.width - 2 * p, size.height - 2 * p), CornerRadius(9.dp.toPx()),
                style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        .border(2.5.dp, Color(0xFF1C0F06), s)
}

/** Aged parchment with darkened edges. */
fun Modifier.parchment(): Modifier {
    val s = RoundedCornerShape(12.dp)
    return background(Brush.verticalGradient(listOf(Color(0xFFF0E2BC), Color(0xFFDCC590))), s)
        .drawBehind { drawRoundRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x40703A10)), center, size.maxDimension * 0.65f), cornerRadius = CornerRadius(12.dp.toPx())) }
        .border(3.dp, Color(0xFF7A4F24), s)
}

/** Wooden tray with a gold frame. */
fun Modifier.woodPanel(): Modifier {
    val s = RoundedCornerShape(16.dp)
    return background(Brush.verticalGradient(listOf(Color(0xFF714828), Color(0xFF4A2D16))), s)
        .drawBehind { for (k in 0..3) { val y = size.height * (0.2f + 0.2f * k); drawLine(Color(0x22000000), Offset(0f, y), Offset(size.width, y + 3f), 1.5.dp.toPx()) } }
        .border(3.dp, Pal.Gold, s)
}

@Composable
fun Title(text: String, size: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Pal.Gold, fontSize = size, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
        fontFamily = FontFamily.Serif, style = TextStyle(shadow = Shadow(Color(0xCC1C0F06), Offset(0f, 4f), 2f)))
}
