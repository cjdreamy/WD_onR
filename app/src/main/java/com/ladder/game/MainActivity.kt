package com.ladder.game

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) { super.onCreate(s); setContent { Root() } }
}

private fun View.tap() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
private fun View.ok() = performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
private fun View.no() = performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

fun legal(lex: Lexicon, chain: List<String>, w: String): String? {
    val c = chain.last()
    return when {
        w.length != c.length || w.indices.count { w[it] != c[it] } != 1 -> "Change exactly one letter"
        w in chain -> "Already used"
        w !in lex.words -> "Not in dictionary"
        else -> null
    }
}

class UiState(
    val chain: List<String>, val owners: List<String?>, val target: String,
    val players: List<Pair<String, Int>>, val turn: Int, val over: Boolean, val banner: String
)

@Composable
fun Root() {
    val ctx = LocalContext.current
    val lex by produceState<Lexicon?>(null) { value = withContext(Dispatchers.IO) { runCatching { Lexicon(loadWords(ctx)) }.getOrNull() } }
    var screen by remember { mutableStateOf("menu") }
    var game by remember { mutableStateOf<Game?>(null) }
    val l = lex
    Box(Modifier.fillMaxSize().background(Pal.Felt)) {
        if (l == null) Text("Loading dictionary. The first run needs internet unless words.txt is bundled.", color = Pal.Cream,
            modifier = Modifier.align(Alignment.Center).padding(32.dp))
        else when (screen) {
            "local" -> LocalGame(game!!) { screen = "menu" }
            "online" -> OnlineFlow(l) { screen = "menu" }
            "board" -> LeaderboardScreen { screen = "menu" }
            else -> Menu(
                onBots = { n ->
                    val (a, b) = l.randomPair()
                    game = Game(l, a, b, listOf(Player("You")) + List(n) { Player("Bot ${it + 1}", true, 0.55f + it * 0.2f) }); screen = "local"
                },
                onOnline = { screen = "online" }, onBoard = { screen = "board" })
        }
    }
}

@Composable
fun Menu(onBots: (Int) -> Unit, onOnline: () -> Unit, onBoard: () -> Unit) {
    val v = LocalView.current
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { "WORD".forEach { Slab(it.toString(), Kind.Wood, 56.dp, points = true) } }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { "LADDER".forEach { Slab(it.toString(), Kind.Wood, 56.dp, points = true) } }
        Spacer(Modifier.height(48.dp))
        listOf<Pair<String, () -> Unit>>(
            "Play online" to onOnline, "Versus 1 bot" to { onBots(1) }, "Versus 3 bots" to { onBots(3) }, "Leaderboard" to onBoard
        ).forEachIndexed { i, (t, f) ->
            Slab(t, if (i == 0) Kind.Hot else Kind.Dark, 54.dp, Modifier.fillMaxWidth(), 18.sp, serif = false) { v.tap(); f() }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun LocalGame(game: Game, onExit: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observe = tick
    LaunchedEffect(tick) {
        if (!game.over && game.player.bot) {
            delay(1100)
            val w = game.botMove(); if (w == null) game.pass() else game.play(w); tick++
        }
    }
    val banner = if (!game.over) "" else game.winner?.let { "${it.name} reached ${game.target.uppercase()}" } ?: "Everyone passed"
    val ui = UiState(game.chain.toList(), game.owners.map { it?.name }, game.target, game.players.map { it.name to it.score }, game.turn, game.over, banner)
    GameScreen(ui, myTurn = !game.over && !game.player.bot, check = { game.check(it) }, reject = 0 to "",
        onPlay = { game.play(it); tick++ }, onPass = { game.pass(); tick++ }, onExit = onExit)
}

@Composable
fun GameScreen(
    ui: UiState, myTurn: Boolean, check: (String) -> String?, reject: Pair<Int, String>,
    onPlay: (String) -> Unit, onPass: () -> Unit, onExit: () -> Unit
) {
    val v = LocalView.current
    val scope = rememberCoroutineScope()
    val cur = ui.chain.last()
    var draft by remember(cur) { mutableStateOf(cur) }
    var sel by remember(cur) { mutableIntStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val list = rememberLazyListState()

    suspend fun bad(why: String) {
        msg = why; v.no()
        repeat(3) { shake.animateTo(12f, tween(40)); shake.animateTo(-12f, tween(40)) }
        shake.animateTo(0f, tween(40))
    }
    LaunchedEffect(reject.first) { if (reject.first > 0) bad(reject.second) }
    LaunchedEffect(ui.chain.size) { msg = ""; list.animateScrollToItem(ui.chain.size) }
    LaunchedEffect(ui.turn, ui.over) { if (myTurn) v.tap() }

    Column(Modifier.fillMaxSize().background(Pal.Felt).statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            ui.players.forEachIndexed { i, (n, s) ->
                val on = i == ui.turn && !ui.over
                Column(Modifier.weight(1f).background(if (on) Pal.Rack else Pal.Panel, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text(n, color = if (on) Pal.Cream else Pal.Muted, fontSize = 13.sp, maxLines = 1)
                    Text("$s", color = Pal.Cream, fontSize = 24.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), list, verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            itemsIndexed(ui.chain) { i, w ->
                val prev = ui.chain.getOrNull(i - 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        w.forEachIndexed { k, c ->
                            Slab(c.uppercase(), if (i == 0) Kind.Start else if (prev != null && prev[k] != c) Kind.Hot else Kind.Wood, 36.dp, points = true)
                        }
                    }
                    Text(ui.owners.getOrNull(i) ?: "start", color = Pal.Muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(60.dp).padding(start = 8.dp))
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { ui.target.forEach { Slab(it.uppercase(), Kind.Target, 36.dp, points = true) } }
            }
        }
        Text(if (ui.over) ui.banner else msg, color = Kind.Hot.face, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).offset(x = shake.value.dp).background(Pal.Rack, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(10.dp).background(Pal.RackDark, RoundedCornerShape(5.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.forEachIndexed { i, c ->
                    Slab(c.uppercase(), if (i == sel) Kind.Hot else Kind.Wood, 56.dp, Modifier.width(56.dp), points = true, scale = if (i == sel) 1.08f else 1f) {
                        if (myTurn) { v.tap(); sel = i }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.alpha(if (myTurn) 1f else 0.4f).align(Alignment.CenterHorizontally), Arrangement.spacedBy(5.dp), Alignment.CenterHorizontally) {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c ->
                        Slab(c.uppercase(), Kind.Dark, 38.dp, Modifier.width(32.dp), 15.sp, serif = false) {
                            if (myTurn && sel >= 0) { v.tap(); draft = draft.replaceRange(sel, sel + 1, c.toString()) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.over) Slab("Back to menu", Kind.Hot, 50.dp, Modifier.weight(1f), 17.sp, serif = false) { onExit() }
            else {
                Slab("Pass", Kind.Dark, 50.dp, Modifier.weight(1f), 17.sp, serif = false) { if (myTurn) { v.tap(); onPass() } }
                Slab("Play", Kind.Hot, 50.dp, Modifier.weight(2f), 17.sp, serif = false) {
                    if (myTurn) { val why = check(draft); if (why == null) { v.ok(); onPlay(draft) } else scope.launch { bad(why) } }
                }
            }
        }
    }
}
