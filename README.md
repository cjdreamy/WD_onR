# Word Ladder

## 1. Supabase (leaderboard and match history)
1. Create a project at supabase.com.
2. SQL Editor > New query > paste `supabase/schema.sql` > Run.
3. Project Settings > API: copy the Project URL, the anon key and the service_role key.

## 2. Render (game server)
1. Push this whole folder to a GitHub repo.
2. Render > New > Blueprint > pick the repo (it reads `render.yaml`).
3. When asked, set `SUPABASE_URL` (Project URL) and `SUPABASE_SERVICE_KEY` (service_role key).
4. After deploy you get `https://NAME.onrender.com`. Free instances sleep, so the first connection can take ~50 s.

## 3. App
1. Open `app/src/main/java/com/ladder/game/Config.kt` and paste:
   - `SERVER_URL` = `wss://NAME.onrender.com`
   - `SUPABASE_URL` and `SUPABASE_ANON_KEY` (anon key only)
2. Open this folder in Android Studio, sync, and run `app`.

Dictionary: the app and the server both download the same word list on first run. To use your own, put `words.txt` in
`app/src/main/assets/` and in `server/`, or change `WORDS_URL` in Config.kt and on Render.
