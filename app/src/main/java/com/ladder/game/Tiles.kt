package com.ladder.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

object Pal {
    val Felt = Color(0xFF17352D); val Panel = Color(0xFF10261F); val Rack = Color(0xFF5A3A20)
    val RackDark = Color(0xFF3E2814); val Cream = Color(0xFFF3E9D2); val Muted = Color(0xFF8FA89E)
}

enum class Kind(val face: Color, val edge: Color, val ink: Color) {
    Wood(Color(0xFFE8CC94), Color(0xFFB38B4D), Color(0xFF2A1C0D)),
    Start(Color(0xFF2F7A73), Color(0xFF1B4744), Color(0xFFEAF4F2)),
    Target(Color(0xFFA6402F), Color(0xFF692419), Color(0xFFFBEDE9)),
    Hot(Color(0xFFF3BA4C), Color(0xFFB77F1E), Color(0xFF2A1C0D)),
    Dark(Color(0xFF2C4A40), Color(0xFF0C1C17), Color(0xFFE6EFEA))
}

private val pts = mapOf(
    "aeioulnstr" to 1, "dg" to 2, "bcmp" to 3, "fhvwy" to 4, "k" to 5, "jx" to 8, "qz" to 10
).flatMap { (k, v) -> k.map { it to v } }.toMap()

/** A physical-feeling slab: cast shadow, side edge, face, top highlight. Presses down on touch. */
@Composable
fun Slab(
    label: String, kind: Kind, size: Dp, modifier: Modifier = Modifier,
    fontSize: TextUnit = (size.value * 0.52f).sp, points: Boolean = false,
    serif: Boolean = true, scale: Float = 1f, onClick: (() -> Unit)? = null
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val depth = size * 0.12f
    val drop by animateDpAsState(if (pressed) depth * 0.85f else 0.dp, spring(Spring.DampingRatioMediumBouncy, 1000f), label = "drop")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) scale else 0.5f, spring(Spring.DampingRatioMediumBouncy, 500f), label = "pop")
    val shape = RoundedCornerShape(size * 0.14f)
    Box(
        modifier.graphicsLayer { scaleX = pop; scaleY = pop }.height(size + depth + 3.dp)
            .then(if (onClick != null) Modifier.clickable(src, null) { onClick() } else Modifier)
    ) {
        Box(Modifier.offset(y = depth + 3.dp).fillMaxWidth().height(size).background(Color(0x66000000), shape))
        Box(Modifier.offset(y = depth).fillMaxWidth().height(size).background(kind.edge, shape))
        Box(Modifier.offset(y = drop).fillMaxWidth().height(size).background(kind.face, shape)
            .border(1.dp, Color(0x2BFFFFFF), shape), Alignment.Center) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 2.dp).fillMaxWidth(0.72f).height(2.dp)
                .background(Color(0x55FFFFFF), CircleShape))
            Text(label, color = kind.ink, fontSize = fontSize, fontWeight = FontWeight.Bold,
                fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif)
            if (points && label.length == 1)
                Text("${pts[label[0].lowercaseChar()] ?: 0}", color = kind.ink.copy(alpha = 0.75f),
                    fontSize = (size.value * 0.2f).sp, fontFamily = FontFamily.Serif,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = size * 0.1f, bottom = size * 0.06f))
        }
    }
}
