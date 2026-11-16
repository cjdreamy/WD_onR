package com.ladder.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*

@Composable
fun SetupPanel(lex: Lexicon, cta: String, enabled: Boolean = true, onReady: (String, String) -> Unit) {
    val v = LocalView.current
    var custom by remember { mutableStateOf(false) }
    var len by remember { mutableIntStateOf(4) }
    var pair by remember { mutableStateOf(lex.randomPair(4)) }
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    val s = if (custom) a.lowercase() else pair.first
    val t = if (custom) b.lowercase() else pair.second
    val problem = when {
        s.isEmpty() || t.isEmpty() -> "Enter both words"
        s.length != t.length -> "Both words need the same number of letters"
        s == t -> "Pick two different words"
        s !in lex.words -> "${s.uppercase()} is not in the dictionary"
        t !in lex.words -> "${t.uppercase()} is not in the dictionary"
        else -> null
    }
    val steps = remember(s, t, problem) { if (problem == null) lex.path(s, t)?.size?.minus(1) else null }
    val note = problem ?: if (steps == null) "No ladder connects these words" else "Shortest ladder: $steps steps"
    val ready = steps != null && enabled

    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
        Title("Your Ladder")
        Column(Modifier.height(100.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.forEach { Slab(it.uppercase(), Kind.Start, 40.dp) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { t.forEach { Slab(it.uppercase(), Kind.Target, 40.dp) } }
        }
        Text(note, color = if (steps != null) Pal.Cream else Pal.Gold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            Slab("Random", if (!custom) Kind.Hot else Kind.Gold, 46.dp, Modifier.weight(1f), 15.sp) { v.tap(); custom = false }
            Slab("Choose words", if (custom) Kind.Hot else Kind.Gold, 46.dp, Modifier.weight(1f), 15.sp) { v.tap(); custom = true }
        }
        if (!custom) Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            listOf(3, 4, 5, 6, 7, 8).forEach { n ->
                Slab("$n", if (len == n) Kind.Start else Kind.Gold, 42.dp, Modifier.weight(1f), 15.sp) { v.tap(); len = n; pair = lex.randomPair(n) }
            }
            Slab("Shuffle", Kind.Gold, 42.dp, Modifier.weight(2f), 15.sp) { v.tap(); pair = lex.randomPair(len) }
        } else {
            Field(a, "Start word") { a = it.filter { c -> c.isLetter() }.take(8) }
            Field(b, "Target word") { b = it.filter { c -> c.isLetter() }.take(8) }
        }
        Slab(cta, Kind.Hot, 52.dp, Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.4f), 17.sp) { if (ready) { v.tap(); onReady(s, t) } }
    }
}

@Composable
fun SetupScreen(lex: Lexicon, bots: Int, onBots: (Int) -> Unit, onBack: () -> Unit, onReady: (String, String) -> Unit) {
    val v = LocalView.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3).forEach { n ->
                Slab(if (n == 1) "1 bot" else "$n bots", if (bots == n) Kind.Start else Kind.Gold, 42.dp, Modifier.weight(1f), 14.sp) { v.tap(); onBots(n) }
            }
        }
        SetupPanel(lex, "Start game", true, onReady)
        Slab("Back", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 16.sp) { onBack() }
    }
}
