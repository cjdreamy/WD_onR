package com.ladder.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File

/** Bundled assets/words.txt wins; otherwise downloaded once and cached. */
fun loadWords(ctx: Context): List<String> {
    runCatching { return ctx.assets.open("words.txt").bufferedReader().readLines() }
    val f = File(ctx.filesDir, "words.txt")
    if (!f.exists()) {
        val r = OkHttpClient().newCall(Request.Builder().url(Config.WORDS_URL).build()).execute()
        f.writeText(r.body!!.string())
    }
    return f.readLines()
}

class Row(val name: String, val games: Int, val wins: Int, val points: Int)

suspend fun fetchLeaders(): List<Row>? = withContext(Dispatchers.IO) {
    if ("YOUR-" in Config.SUPABASE_URL) return@withContext null
    runCatching {
        val req = Request.Builder().url("${Config.SUPABASE_URL}/rest/v1/leaderboard?select=*&limit=20")
            .header("apikey", Config.SUPABASE_ANON_KEY).header("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}").build()
        val a = JSONArray(OkHttpClient().newCall(req).execute().body!!.string())
        List(a.length()) { a.getJSONObject(it).let { o -> Row(o.getString("name"), o.getInt("games"), o.getInt("wins"), o.getInt("points")) } }
    }.getOrNull()
}

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val rows by produceState<List<Row>?>(null) { value = fetchLeaders() }
    Column(Modifier.fillMaxSize().background(Pal.Felt).statusBarsPadding().padding(20.dp)) {
        Text("Leaderboard", color = Pal.Cream, fontSize = 26.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f), Arrangement.spacedBy(6.dp)) {
            val r = rows
            if (r == null) Text("Unavailable. Check the Supabase values in Config.kt.", color = Pal.Muted)
            else if (r.isEmpty()) Text("No games recorded yet.", color = Pal.Muted)
            else r.forEachIndexed { i, x ->
                Row(Modifier.fillMaxWidth().background(Pal.Panel, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Pal.Muted, modifier = Modifier.width(28.dp))
                    Text(x.name, color = Pal.Cream, modifier = Modifier.weight(1f))
                    Text("${x.wins}W / ${x.games}G   ${x.points} pts", color = Pal.Muted, fontSize = 13.sp)
                }
            }
        }
        Slab("Back", Kind.Dark, 50.dp, Modifier.fillMaxWidth(), 17.sp, serif = false) { onBack() }
    }
}
