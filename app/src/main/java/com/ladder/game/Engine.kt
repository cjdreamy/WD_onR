package com.ladder.game

import kotlin.random.Random

class Lexicon(raw: Collection<String>) {
    val words: Set<String> = raw.map { it.trim().lowercase() }
        .filter { it.length in 3..6 && it.all { c -> c in 'a'..'z' } }.toHashSet()

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
        while (true) {
            val s = pool.random()
            val far = distances(s).filterValues { it in minSteps..maxSteps }.keys
            if (far.isNotEmpty()) return s to far.random()
        }
    }
}

class Player(val name: String, val bot: Boolean = false, val skill: Float = 0.75f) {
    var score = 0
}

class Game(val lex: Lexicon, val start: String, val target: String, val players: List<Player>) {
    val chain = mutableListOf(start)
    val owners = mutableListOf<Player?>(null)
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

    fun play(w: String): String? {
        if (over) return "Game over"
        check(w)?.let { return it }
        val before = lex.path(current, target)?.size ?: Int.MAX_VALUE
        val after = lex.path(w, target)?.size ?: Int.MAX_VALUE
        chain += w; owners += player; passes = 0
        player.score += if (after < before) 2 else 1
        if (w == target) { player.score += 10; winner = player; over = true } else advance()
        return null
    }

    fun pass() {
        if (over) return
        player.score -= 1; passes++
        if (passes >= players.size) over = true else advance()
    }

    private fun advance() { turn = (turn + 1) % players.size }

    fun botMove(): String? {
        val used = chain.toSet()
        val route = lex.path(current, target, used - current)
        val options = lex.neighbors(current, used)
        if (options.isEmpty()) return null
        if (route != null && route.size > 1 && Random.nextFloat() < player.skill) return route[1]
        return if (Random.nextFloat() < 0.15f) null else options.random()
    }
}
