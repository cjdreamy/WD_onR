package com.ladder.game

/** Paste your own links here, then rebuild. */
object Config {
    // Render gives you https://NAME.onrender.com  ->  use wss:// plus the same host
    const val SERVER_URL = "wss://word-ladder-server.onrender.com"

    // Supabase: Project Settings > API. Use the anon (public) key here, never the service_role key.
    const val SUPABASE_URL = "https://zmkgzmzlgwnuvfnqcjoh.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_0ZgOuiiJmKRGm1JcXOJXNg_mcUvhOx3"

    // Used only if app/src/main/assets/words.txt is not bundled. Keep identical to the server's WORDS_URL.
    const val WORDS_URL = "https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt"
}