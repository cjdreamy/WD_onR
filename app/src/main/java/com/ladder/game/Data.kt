package com.ladder.game

import android.content.Context
import android.content.SharedPreferences
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
    Column(Modifier.fillMaxSize().woodBg().statusBarsPadding().padding(20.dp)) {
        Title("Leaderboard")
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f), Arrangement.spacedBy(6.dp)) {
            val r = rows
            if (r == null) Text("Unavailable. Check the Supabase values in Config.kt.", color = Pal.Muted)
            else if (r.isEmpty()) Text("No games recorded yet.", color = Pal.Muted)
            else r.forEachIndexed { i, x ->
                Row(Modifier.fillMaxWidth().leather().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Pal.Muted, modifier = Modifier.width(28.dp))
                    Text(x.name, color = Pal.Cream, modifier = Modifier.weight(1f))
                    Text("${x.wins}W / ${x.games}G   ${x.points} pts", color = Pal.Muted, fontSize = 13.sp)
                }
            }
        }
        Slab("Back", Kind.Gold, 50.dp, Modifier.fillMaxWidth(), 17.sp, serif = false) { onBack() }
    }
}

/** Stored on the device only. */
object Prefs {
    private lateinit var sp: SharedPreferences
    var name by mutableStateOf(""); var theme by mutableIntStateOf(0)
    var look by mutableIntStateOf(0); var haptics by mutableStateOf(true)
    fun init(c: Context) {
        sp = c.getSharedPreferences("ladder", Context.MODE_PRIVATE)
        name = sp.getString("name", null) ?: "Player${(100..999).random()}".also { sp.edit().putString("name", it).apply() }
        theme = sp.getInt("theme", 0).coerceIn(0, Themes.size - 1); look = sp.getInt("look", 0).coerceIn(0, Looks.size - 1)
        haptics = sp.getBoolean("haptics", true)
    }
    fun saveName(n: String) { name = n.take(12); sp.edit().putString("name", name).apply() }
    fun saveTheme(i: Int) { theme = i; sp.edit().putInt("theme", i).apply() }
    fun saveLook(i: Int) { look = i; sp.edit().putInt("look", i).apply() }
    fun saveHaptics(b: Boolean) { haptics = b; sp.edit().putBoolean("haptics", b).apply() }
}
