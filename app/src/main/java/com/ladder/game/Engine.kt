package com.ladder.game

import kotlin.math.roundToInt
import kotlin.random.Random

class Lexicon(raw: Collection<String>) {
    val words: Set<String> = raw.map { it.trim().lowercase() }
        .filter { it.length in 3..8 && it.all { c -> c in 'a'..'z' } }.toHashSet()

    fun neighbors(w: String, banned: Set<String> = emptySet()): List<String> {
        val out = ArrayList<String>()
        for (i in w.indices) for (c in 'a'..'z') if (c != w[i]) {
            val n = w.substring(0, i) + c + w.substring(i + 1)
            if (n in words && n !in banned) out += n
        }
        return out
    }

    /** Distance to every reachable word, via breadth-first search. */
    fun distances(from: String, banned: Set<String> = emptySet()): Map<String, Int> {
        val d = hashMapOf(from to 0); val q = ArrayDeque(listOf(from))
        while (q.isNotEmpty()) {
            val w = q.removeFirst()
            for (n in neighbors(w, banned)) if (n !in d) { d[n] = d.getValue(w) + 1; q.addLast(n) }
        }
        return d
    }

    fun path(from: String, to: String, banned: Set<String> = emptySet()): List<String>? {
        val prev = hashMapOf<String, String?>(from to null); val q = ArrayDeque(listOf(from))
        while (q.isNotEmpty()) {
            val w = q.removeFirst()
            if (w == to) return generateSequence(w) { prev[it] }.toList().reversed()
            for (n in neighbors(w, banned)) if (n !in prev) { prev[n] = w; q.addLast(n) }
        }
        return null
    }

    fun randomPair(len: Int = 4, minSteps: Int = 4, maxSteps: Int = 7): Pair<String, String> {
        val pool = words.filter { it.length == len }
        var n = 0
        while (true) {
            val s = pool.random()
            val lo = n++.let { if (it > 200) 1 else if (it > 40) 2 else minSteps }
            val far = distances(s).filterValues { it in lo..maxSteps }.keys
            if (far.isNotEmpty()) return s to far.random()
        }
    }
}

/** Faster plays earn more. */
fun speedMult(ms: Long) = when { ms <= 8000 -> 2.0f; ms <= 15000 -> 1.5f; ms <= 30000 -> 1.2f; else -> 1.0f }

class Player(val name: String, val bot: Boolean = false, val skill: Float = 0.75f) {
    var score = 0
}

class Game(val lex: Lexicon, val start: String, val target: String, val players: List<Player>) {
    val chain = mutableListOf(start)
    val owners = mutableListOf<Player?>(null)
    val points = mutableListOf(0)
    val dm = lex.distances(target)          // steps-to-target for every word, computed once
    private var best = dm[start] ?: 99
    private var turnStart = System.currentTimeMillis()
    var turn = 0; private set
    var passes = 0; private set
    var winner: Player? = null; private set
    var over = false; private set

    val current get() = chain.last()
    val player get() = players[turn]

    /** Returns null when the word is a legal move, otherwise a short reason. */
    fun check(w: String): String? {
        if (w.length != current.length) return "Wrong length"
        if (w == current) return "Change one letter"
        if (w.indices.count { w[it] != current[it] } != 1) return "Change only one letter"
        if (w in chain) return "Already used"
        if (w !in lex.words) return "Not in dictionary"
        return null
    }

    /** Base 10, +15 per step closer (max 3), +3..15 when within 5 steps, +20 for a new closest word, x speed, +100 at the target. Moving away costs nothing. */
    fun play(w: String, elapsedMs: Long = System.currentTimeMillis() - turnStart): String? {
        if (over) return "Game over"
        check(w)?.let { return it }
        val prev = dm[current] ?: 99; val now = dm[w] ?: 99
        var pts = 10
        if (now < prev) pts += 15 * minOf(3, prev - now)
        if (now in 1..5) pts += (6 - now) * 3
        if (now < best) { pts += 20; best = now }
        pts = (pts * speedMult(elapsedMs)).roundToInt()
        if (w == target) pts += 100
        chain += w; owners += player; points += pts; passes = 0; player.score += pts
        if (w == target) { winner = player; over = true } else advance()
        return null
    }

    fun pass() {
        if (over) return
        passes++   // passing is free
        if (passes >= players.size) over = true else advance()
    }

    private fun advance() { turn = (turn + 1) % players.size; turnStart = System.currentTimeMillis() }

    fun botMove(): String? {
        val used = chain.toSet()
        val route = lex.path(current, target, used - current)
        val options = lex.neighbors(current, used)
        if (options.isEmpty()) return null
        if (route != null && route.size > 1 && Random.nextFloat() < player.skill) return route[1]
        return if (Random.nextFloat() < 0.15f) null else options.random()
    }
}
