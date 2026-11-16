package com.ladder.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private fun JSONArray.strings() = List(length()) { getString(it) }
class Peer(val id: String, val name: String)

class Online {
    var connected by mutableStateOf(false); var code by mutableStateOf(""); var host by mutableStateOf(false)
    var lobby by mutableStateOf(listOf<String>()); var ui by mutableStateOf<UiState?>(null)
    var peers by mutableStateOf(listOf<Peer>()); var invite by mutableStateOf<Peer?>(null); var queued by mutableStateOf(false)
    var you by mutableIntStateOf(-1); var error by mutableStateOf(""); var rejects by mutableIntStateOf(0)
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()

    fun connect(name: String) {
        error = ""; connected = false; ws?.close(1000, null)
        ws = client.newWebSocket(Request.Builder().url(Config.SERVER_URL).build(), object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) { w.send(JSONObject(mapOf("type" to "hello", "name" to name)).toString()) }
            override fun onMessage(w: WebSocket, text: String) { handle(JSONObject(text)) }
            override fun onFailure(w: WebSocket, t: Throwable, r: Response?) { connected = false; error = "Cannot reach server (it may be waking up)" }
        })
    }
    fun send(type: String, vararg extra: Pair<String, Any>) { ws?.send(JSONObject(mapOf("type" to type, *extra)).toString()) }
    fun close() { ws?.close(1000, null) }

    private fun handle(j: JSONObject) {
        when (j.getString("type")) {
            "welcome" -> connected = true
            "online" -> peers = j.getJSONArray("peers").let { a -> List(a.length()) { a.getJSONObject(it).let { o -> Peer(o.getString("id"), o.getString("name")) } } }
            "invited" -> invite = Peer(j.getString("id"), j.getString("name"))
            "queued" -> queued = j.getBoolean("on")
            "joined" -> { code = j.getString("code"); host = j.getBoolean("host"); queued = false; invite = null }
            "lobby" -> lobby = j.getJSONArray("players").strings()
            "rejected" -> { error = j.optString("why", "Word not accepted"); rejects++ }
            "error" -> error = j.getString("why")
            "state" -> {
                val chain = j.getJSONArray("chain").strings(); val target = j.getString("target")
                val owners = j.getJSONArray("owners").strings().map { it.ifEmpty { null } }
                val pts = j.getJSONArray("points").let { a -> List(a.length()) { a.getInt(it) } }
                val ps = j.getJSONArray("players").let { a -> List(a.length()) { a.getJSONObject(it).let { o -> o.getString("name") to o.getInt("score") } } }
                val over = j.getBoolean("over"); you = j.getInt("you"); error = ""
                val banner = if (!over) "" else if (chain.last() == target) "${owners.last()} reached ${target.uppercase()}" else "Everyone passed"
                ui = UiState(chain, owners, target, ps, j.getInt("turn"), over, banner, pts)
            }
        }
    }
}

@Composable
fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().parchment().padding(16.dp)) {
        if (value.isEmpty()) Text(hint, color = Pal.InkSoft, fontSize = 17.sp)
        BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(Pal.Ink),
            textStyle = TextStyle(color = Pal.Ink, fontSize = 17.sp), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun OnlineFlow(lex: Lexicon, onExit: () -> Unit) {
    val o = remember { Online() }
    val v = LocalView.current
    DisposableEffect(Unit) { o.connect(Prefs.name); onDispose { o.close() } }
    var codeIn by remember { mutableStateOf("") }
    val ui = o.ui
    if (ui != null) {
        GameScreen(ui, myTurn = !ui.over && ui.turn == o.you, check = { legal(lex, ui.chain, it) },
            reject = o.rejects to o.error, onPlay = { o.send("play", "word" to it) }, onPass = { o.send("pass") }, onExit = onExit)
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), Arrangement.spacedBy(12.dp)) {
        Title("Play online")
        Text("Playing as ${Prefs.name}", color = Pal.Muted)
        when {
            !o.connected -> {
                Text("Connecting. A sleeping server can take about a minute.", color = Pal.Cream)
                Slab("Retry", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 16.sp) { o.connect(Prefs.name) }
            }
            o.code.isEmpty() -> {
                o.invite?.let { p ->
                    Column(Modifier.fillMaxWidth().parchment().padding(14.dp), Arrangement.spacedBy(10.dp)) {
                        Text("${p.name} invited you to play", color = Pal.Ink, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Slab("Accept", Kind.Hot, 44.dp, Modifier.weight(1f), 15.sp) { o.send("accept", "from" to p.id); o.invite = null }
                            Slab("Decline", Kind.Gold, 44.dp, Modifier.weight(1f), 15.sp) { o.invite = null }
                        }
                    }
                }
                Slab(if (o.queued) "Searching... tap to cancel" else "Find a match", Kind.Hot, 52.dp, Modifier.fillMaxWidth(), 17.sp) { v.tap(); o.send("queue", "on" to !o.queued) }
                Slab("Create room", Kind.Gold, 50.dp, Modifier.fillMaxWidth(), 16.sp) { o.send("create") }
                Field(codeIn, "Room code") { codeIn = it.uppercase().take(4) }
                Slab("Join room", Kind.Gold, 50.dp, Modifier.fillMaxWidth(), 16.sp) { o.send("join", "code" to codeIn) }
                Text("PLAYERS ONLINE", color = Pal.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
                if (o.peers.isEmpty()) Text("Nobody else is online right now", color = Pal.Muted)
                o.peers.forEach { p ->
                    Row(Modifier.fillMaxWidth().leather().padding(10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(p.name, color = Pal.Cream)
                        Slab("Invite", Kind.Gold, 34.dp, Modifier.width(88.dp), 13.sp) { o.send("invite", "to" to p.id) }
                    }
                }
            }
            else -> {
                Text("Share this code", color = Pal.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) { o.code.forEach { Slab(it.toString(), Kind.Tile, 56.dp) } }
                o.lobby.forEach { Text(it, color = Pal.Cream, modifier = Modifier.fillMaxWidth().leather().padding(12.dp)) }
                if (o.host) SetupPanel(lex, if (o.lobby.size < 2) "Waiting for players" else "Start game", o.lobby.size >= 2) { s, t -> o.send("start", "start" to s, "target" to t) }
                else Text("Waiting for the host to choose the ladder and start", color = Pal.Muted)
            }
        }
        if (o.error.isNotEmpty()) Text(o.error, color = Pal.Gold)
        Slab("Back", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 16.sp) { onExit() }
    }
}
