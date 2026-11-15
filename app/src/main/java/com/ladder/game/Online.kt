package com.ladder.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private fun JSONArray.strings() = List(length()) { getString(it) }

class Online {
    var code by mutableStateOf(""); var host by mutableStateOf(false)
    var lobby by mutableStateOf(listOf<String>()); var ui by mutableStateOf<UiState?>(null)
    var you by mutableIntStateOf(-1); var error by mutableStateOf(""); var rejects by mutableIntStateOf(0)
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()

    fun join(first: JSONObject) {
        error = ""; ws?.close(1000, null)
        ws = client.newWebSocket(Request.Builder().url(Config.SERVER_URL).build(), object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) { w.send(first.toString()) }
            override fun onMessage(w: WebSocket, text: String) { handle(JSONObject(text)) }
            override fun onFailure(w: WebSocket, t: Throwable, r: Response?) { error = "Cannot reach server (it may be waking up, try again)" }
        })
    }
    fun send(type: String, vararg extra: Pair<String, Any>) { ws?.send(JSONObject(mapOf("type" to type, *extra)).toString()) }
    fun close() { ws?.close(1000, null) }

    private fun handle(j: JSONObject) {
        when (j.getString("type")) {
            "joined" -> { code = j.getString("code"); host = j.getBoolean("host") }
            "lobby" -> lobby = j.getJSONArray("players").strings()
            "rejected" -> { error = "Word not accepted"; rejects++ }
            "error" -> error = j.getString("why")
            "state" -> {
                val chain = j.getJSONArray("chain").strings(); val target = j.getString("target")
                val owners = j.getJSONArray("owners").strings().map { it.ifEmpty { null } }
                val ps = j.getJSONArray("players").let { a -> List(a.length()) { a.getJSONObject(it).let { o -> o.getString("name") to o.getInt("score") } } }
                val over = j.getBoolean("over"); you = j.getInt("you")
                val banner = if (!over) "" else if (chain.last() == target) "${owners.last()} reached ${target.uppercase()}" else "Everyone passed"
                ui = UiState(chain, owners, target, ps, j.getInt("turn"), over, banner)
            }
        }
    }
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(Pal.Panel, RoundedCornerShape(12.dp)).padding(16.dp)) {
        if (value.isEmpty()) Text(hint, color = Pal.Muted, fontSize = 17.sp)
        BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(Pal.Cream),
            textStyle = TextStyle(color = Pal.Cream, fontSize = 17.sp), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun OnlineFlow(lex: Lexicon, onExit: () -> Unit) {
    val o = remember { Online() }
    DisposableEffect(Unit) { onDispose { o.close() } }
    var name by remember { mutableStateOf("") }; var codeIn by remember { mutableStateOf("") }
    val ui = o.ui
    if (ui != null) {
        GameScreen(ui, myTurn = !ui.over && ui.turn == o.you, check = { legal(lex, ui.chain, it) },
            reject = o.rejects to o.error, onPlay = { o.send("play", "word" to it) }, onPass = { o.send("pass") }, onExit = onExit)
        return
    }
    val who = name.trim().ifEmpty { "Player" }
    Column(Modifier.fillMaxSize().background(Pal.Felt).statusBarsPadding().navigationBarsPadding().padding(24.dp), Arrangement.spacedBy(12.dp)) {
        Text("Play online", color = Pal.Cream, fontSize = 26.sp, fontFamily = FontFamily.Serif)
        if (o.code.isEmpty()) {
            Field(name, "Your name") { name = it.take(12) }
            Slab("Create room", Kind.Hot, 52.dp, Modifier.fillMaxWidth(), 17.sp, serif = false) { o.join(JSONObject(mapOf("type" to "create", "name" to who))) }
            Spacer(Modifier.height(10.dp))
            Field(codeIn, "Room code") { codeIn = it.uppercase().take(4) }
            Slab("Join room", Kind.Dark, 52.dp, Modifier.fillMaxWidth(), 17.sp, serif = false) { o.join(JSONObject(mapOf("type" to "join", "name" to who, "code" to codeIn))) }
        } else {
            Text("Share this code", color = Pal.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                o.code.forEach { Slab(it.toString(), Kind.Wood, 56.dp, points = true) }
            }
            Spacer(Modifier.height(8.dp))
            o.lobby.forEach { Text(it, color = Pal.Cream, modifier = Modifier.fillMaxWidth().background(Pal.Panel, RoundedCornerShape(10.dp)).padding(12.dp)) }
            Spacer(Modifier.weight(1f))
            if (o.host) Slab(if (o.lobby.size < 2) "Waiting for players" else "Start game", Kind.Hot, 52.dp, Modifier.fillMaxWidth(), 17.sp, serif = false) {
                if (o.lobby.size >= 2) o.send("start")
            } else Text("Waiting for the host to start", color = Pal.Muted)
        }
        if (o.error.isNotEmpty()) Text(o.error, color = Kind.Hot.face)
        Spacer(Modifier.weight(1f))
        Slab("Back", Kind.Dark, 46.dp, Modifier.fillMaxWidth(), 16.sp, serif = false) { onExit() }
    }
}
