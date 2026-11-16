-- Run once in Supabase: SQL Editor > New query > paste > Run.
create table if not exists matches (
  id uuid primary key default gen_random_uuid(),
  room_code text, start_word text, target_word text,
  chain jsonb, winner text, players jsonb,
  created_at timestamptz default now()
);
create table if not exists player_stats (
  name text primary key,
  games int default 0, wins int default 0, points int default 0,
  updated_at timestamptz default now()
);

create or replace function record_match(
  p_room text, p_start text, p_target text, p_chain jsonb, p_winner text, p_players jsonb
) returns void language plpgsql security definer as $$
declare pl jsonb;
begin
  insert into matches(room_code, start_word, target_word, chain, winner, players)
  values (p_room, p_start, p_target, p_chain, p_winner, p_players);
  for pl in select * from jsonb_array_elements(p_players) loop
    insert into player_stats(name, games, wins, points)
    values (pl->>'name', 1, case when pl->>'name' = p_winner then 1 else 0 end, (pl->>'score')::int)
    on conflict (name) do update set
      games = player_stats.games + 1,
      wins = player_stats.wins + excluded.wins,
      points = player_stats.points + excluded.points,
      updated_at = now();
  end loop;
end $$;

create or replace view leaderboard as
  select name, games, wins, points from player_stats order by wins desc, points desc limit 50;

alter table matches enable row level security;
alter table player_stats enable row level security;
create policy "public read stats" on player_stats for select to anon using (true);
create policy "public read matches" on matches for select to anon using (true);
grant select on leaderboard to anon;

-- Only the server (service_role key) may write results.
revoke execute on function record_match(text,text,text,jsonb,text,jsonb) from public, anon, authenticated;
grant execute on function record_match(text,text,text,jsonb,text,jsonb) to service_role;
