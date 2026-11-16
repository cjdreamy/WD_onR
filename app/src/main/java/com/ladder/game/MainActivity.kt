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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
    override fun onCreate(s: Bundle?) { super.onCreate(s); Prefs.init(this); setContent { Root() } }
}

fun View.tap() { if (Prefs.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
private fun View.ok() { if (Prefs.haptics) performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS) }
private fun View.no() { if (Prefs.haptics) performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS) }

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
    val players: List<Pair<String, Int>>, val turn: Int, val over: Boolean, val banner: String,
    val points: List<Int> = emptyList()
)

@Composable
fun Root() {
    val ctx = LocalContext.current
    val lex by produceState<Lexicon?>(null) { value = withContext(Dispatchers.IO) { runCatching { Lexicon(loadWords(ctx)) }.getOrNull() } }
    var screen by remember { mutableStateOf("menu") }
    var game by remember { mutableStateOf<Game?>(null) }
    var bots by remember { mutableIntStateOf(1) }
    val back = { screen = "menu" }
    val l = lex
    Box(Modifier.fillMaxSize().woodBg()) {
        when (screen) {
            "settings" -> SettingsScreen(back) { screen = "privacy" }
            "privacy" -> PrivacyScreen { screen = "settings" }
            "help" -> HelpScreen(back)
            else -> if (l == null) Text("Loading dictionary. The first run needs internet unless words.txt is bundled.", color = Pal.Cream,
                modifier = Modifier.align(Alignment.Center).padding(32.dp))
            else when (screen) {
                "setup" -> SetupScreen(l, bots, { bots = it }, back) { a, b ->
                    game = Game(l, a, b, listOf(Player(Prefs.name.ifBlank { "You" })) + List(bots) { Player("Bot ${it + 1}", true, 0.55f + it * 0.2f) }); screen = "local"
                }
                "local" -> LocalGame(game!!, back)
                "online" -> OnlineFlow(l, back)
                "board" -> LeaderboardScreen(back)
                else -> Menu { screen = it }
            }
        }
    }
}

@Composable
fun Menu(go: (String) -> Unit) {
    val v = LocalView.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { "WORD".forEach { Slab(it.toString(), Kind.Tile, 56.dp) } }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { "LADDER".forEach { Slab(it.toString(), Kind.Tile, 56.dp) } }
        Spacer(Modifier.height(36.dp))
        listOf("Play online" to "online", "Play offline" to "setup", "Leaderboard" to "board", "Settings" to "settings", "Help" to "help")
            .forEachIndexed { i, (t, s) ->
                Slab(t, if (i == 0) Kind.Hot else Kind.Gold, 50.dp, Modifier.fillMaxWidth(), 18.sp) { v.tap(); go(s) }
                Spacer(Modifier.height(10.dp))
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
            val w = game.botMove(); if (w == null) game.pass() else game.play(w, (5000L..25000L).random()); tick++
        }
    }
    val banner = if (!game.over) "" else game.winner?.let { "${it.name} reached ${game.target.uppercase()}" } ?: "Everyone passed"
    val ui = UiState(game.chain.toList(), game.owners.map { it?.name }, game.target, game.players.map { it.name to it.score }, game.turn, game.over, banner, game.points.toList())
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
    val cur = ui.chain.last(); val n = cur.length
    val tile = when { n >= 8 -> 23; n == 7 -> 26; n == 6 -> 30; else -> 36 }.dp
    val rk = when { n >= 8 -> 32; n == 7 -> 40; n == 6 -> 48; else -> 56 }.dp
    var draft by remember(cur) { mutableStateOf(cur) }
    var sel by remember(cur) { mutableIntStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    var elapsed by remember(ui.chain.size, ui.turn) { mutableLongStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val list = rememberLazyListState()

    suspend fun bad(why: String) {
        msg = why; v.no()
        repeat(3) { shake.animateTo(12f, tween(40)); shake.animateTo(-12f, tween(40)) }
        shake.animateTo(0f, tween(40))
    }
    LaunchedEffect(reject.first) { if (reject.first > 0) bad(reject.second) }
    LaunchedEffect(ui.chain.size) { msg = ""; list.animateScrollToItem(ui.chain.size) }
    LaunchedEffect(draft) { msg = "" }
    LaunchedEffect(ui.chain.size, ui.turn, ui.over) {
        val t0 = System.currentTimeMillis()
        while (!ui.over) { elapsed = System.currentTimeMillis() - t0; delay(200) }
    }
    LaunchedEffect(ui.turn, ui.over) { if (myTurn) v.tap() }

    // Live look-up: is the word being built a legal move?
    val live = if (myTurn && draft != cur) check(draft) else null
    val valid = myTurn && draft != cur && live == null
    val line = when {
        ui.over -> ui.banner
        msg.isNotEmpty() -> msg
        myTurn && draft != cur -> live ?: "Valid word"
        myTurn -> "Your turn"
        else -> "${ui.players.getOrNull(ui.turn)?.first ?: ""} is thinking"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            ui.players.forEachIndexed { i, (name, s) ->
                val on = i == ui.turn && !ui.over
                Column(Modifier.weight(1f).then(if (on) Modifier.parchment() else Modifier.leather()).padding(10.dp)) {
                    Text(name, color = if (on) Pal.InkSoft else Pal.Muted, fontSize = 13.sp, maxLines = 1)
                    Text("$s", color = if (on) Pal.Ink else Pal.Cream, fontSize = 24.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().parchment().padding(10.dp), list, verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            itemsIndexed(ui.chain) { i, w ->
                val prev = ui.chain.getOrNull(i - 1)
                val who = ui.owners.getOrNull(i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        w.forEachIndexed { k, c ->
                            Slab(c.uppercase(), if (i == 0) Kind.Start else if (prev != null && prev[k] != c) Kind.Hot else Kind.Tile, tile)
                        }
                    }
                    Text(if (who == null) "start" else "$who +${ui.points.getOrElse(i) { 0 }}", color = Pal.InkSoft, fontSize = 11.sp, maxLines = 1,
                        modifier = Modifier.width(70.dp).padding(start = 8.dp))
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { ui.target.forEach { Slab(it.uppercase(), Kind.Target, tile) } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween) {
            Text(line, color = if (valid && msg.isEmpty() && !ui.over) Color(0xFF9BD98F) else Pal.Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (myTurn && !ui.over) Text("Speed x${"%.1f".format(speedMult(elapsed))}", color = Pal.Cream, fontSize = 13.sp)
        }
        Box(Modifier.align(Alignment.CenterHorizontally).offset(x = shake.value.dp).woodPanel().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(10.dp).background(Pal.RackDark, RoundedCornerShape(5.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(if (n >= 7) 4.dp else 8.dp)) {
                draft.forEachIndexed { i, c ->
                    Slab(c.uppercase(), if (i == sel) Kind.Hot else Kind.Tile, rk, scale = if (i == sel) 1.08f else 1f) { if (myTurn) { v.tap(); sel = i } }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.alpha(if (myTurn) 1f else 0.4f).align(Alignment.CenterHorizontally), Arrangement.spacedBy(5.dp), Alignment.CenterHorizontally) {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c ->
                        Slab(c.uppercase(), Kind.Stone, 38.dp, Modifier.width(32.dp), 15.sp) {
                            if (myTurn && sel >= 0) { v.tap(); draft = draft.replaceRange(sel, sel + 1, c.toString()) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.over) Slab("Back to menu", Kind.Hot, 50.dp, Modifier.weight(1f), 17.sp) { onExit() }
            else {
                Slab("Pass", Kind.Gold, 50.dp, Modifier.weight(1f), 17.sp) { if (myTurn) { v.tap(); onPass() } }
                Slab("Play", Kind.Hot, 50.dp, Modifier.weight(2f).alpha(if (valid) 1f else 0.55f), 17.sp) {
                    if (myTurn) { val why = check(draft); if (why == null) { v.ok(); onPlay(draft) } else scope.launch { bad(why) } }
                }
            }
        }
    }
}
