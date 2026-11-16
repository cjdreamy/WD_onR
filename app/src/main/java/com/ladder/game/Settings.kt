package com.ladder.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.*

@Composable private fun Label(t: String) { Text(t.uppercase(), color = Pal.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp) }

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit) {
    val v = LocalView.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), Arrangement.spacedBy(12.dp)) {
        Title("Settings")
        Label("Username"); Field(Prefs.name, "Your name") { Prefs.saveName(it) }
        Label("Theme")
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Themes.forEachIndexed { i, t -> Slab(t.name, if (Prefs.theme == i) Kind.Hot else Kind.Gold, 44.dp, Modifier.weight(1f), 13.sp) { v.tap(); Prefs.saveTheme(i) } }
        }
        Label("Tile look")
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            listOf("Oak", "Ivory", "Slate").forEachIndexed { i, n -> Slab(n, if (Prefs.look == i) Kind.Hot else Kind.Gold, 44.dp, Modifier.weight(1f), 13.sp) { v.tap(); Prefs.saveLook(i) } }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) { "WORD".forEach { Slab(it.toString(), Kind.Tile, 44.dp) } }
        Label("Feedback")
        Slab(if (Prefs.haptics) "Haptics: on" else "Haptics: off", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 15.sp) { Prefs.saveHaptics(!Prefs.haptics); v.tap() }
        Slab("Privacy policy", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 15.sp) { v.tap(); onPrivacy() }
        Slab("Back", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 16.sp) { onBack() }
    }
}

@Composable
private fun TextPage(title: String, body: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), Arrangement.spacedBy(14.dp)) {
        Title(title)
        Column(Modifier.weight(1f).fillMaxWidth().parchment().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(body, color = Pal.Ink, fontSize = 15.sp, lineHeight = 22.sp)
        }
        Slab("Back", Kind.Gold, 46.dp, Modifier.fillMaxWidth(), 16.sp) { onBack() }
    }
}

@Composable fun HelpScreen(onBack: () -> Unit) = TextPage("How to play", """
You get a start word and a target word. Take turns changing exactly one letter to make a new real word, until someone reaches the target. A word can be used once per game.

There is no single right route. The shortest ladder may be 4 steps, but a game can run to 40 or more. Wandering off is part of the fun.

SCORING
Every valid word: 10 points.
Stepping closer to the target: +15 per step closer (up to 3).
Within 5 steps of the target: +3 to +15, more the closer you are.
Closest word so far this game: +20.
Speed multiplier: x2 within 8 s, x1.5 within 15 s, x1.2 within 30 s.
Reaching the target: +100.
Passing is free, and moving away from the target never loses points. The game ends when someone reaches the target or everyone passes in a row.

PLAYING
Tap a tile in your word, then tap a letter. The line under the ladder tells you if your word is valid before you play it. Words can have 3 to 8 letters.

ONLINE
Find a match pairs you with another waiting player. You can also invite anyone listed as online, or share a room code.
""".trimIndent(), onBack)

@Composable fun PrivacyScreen(onBack: () -> Unit) = TextPage("Privacy policy", """
Word Ladder does not use accounts, emails, phone numbers, contacts or location.

What is stored on your device: your username, theme, tile look and haptics setting.

What is sent to our server when you play online: your username, the words you play and your scores, so that rooms, matchmaking and the leaderboard work. Finished online games (words, winner and scores) are saved with the username you used.

Offline games against bots send nothing.

We do not sell your data or show ads. Do not use your real name as a username if you want to stay anonymous.

To ask for your game records to be deleted, contact: wordstair@gmail.com

This policy may change; the latest version is always inside the app.
""".trimIndent(), onBack)
