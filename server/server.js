// Word Ladder server: authoritative rooms over WebSocket, results saved to Supabase.
const http = require('http'), fs = require('fs'), { WebSocketServer } = require('ws');
const SB_URL = process.env.SUPABASE_URL, SB_KEY = process.env.SUPABASE_SERVICE_KEY;
const WORDS_URL = process.env.WORDS_URL || 'https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt';
let words = new Set(); const rooms = new Map();

async function loadWords() {
  let t; if (fs.existsSync('words.txt')) t = fs.readFileSync('words.txt', 'utf8');
  else { t = await (await fetch(WORDS_URL)).text(); fs.writeFileSync('words.txt', t); }
  words = new Set(t.split(/\s+/).map(w => w.toLowerCase()).filter(w => /^[a-z]{3,6}$/.test(w)));
  console.log('words loaded:', words.size);
}
const nbrs = w => { const o = []; for (let i = 0; i < w.length; i++) for (const c of 'abcdefghijklmnopqrstuvwxyz') if (c !== w[i]) { const n = w.slice(0, i) + c + w.slice(i + 1); if (words.has(n)) o.push(n); } return o; };
const dist = (a, b, banned = new Set()) => { const d = new Map([[a, 0]]), q = [a]; while (q.length) { const w = q.shift(); if (w === b) return d.get(w); for (const n of nbrs(w)) if (!d.has(n) && !banned.has(n)) { d.set(n, d.get(w) + 1); q.push(n); } } return Infinity; };
const send = (ws, m) => ws.readyState === 1 && ws.send(JSON.stringify(m));
const names = r => r.players.map(p => p.name);
const castLobby = r => r.players.forEach(p => send(p.ws, { type: 'lobby', players: names(r) }));
const castState = r => r.players.forEach((p, i) => send(p.ws, { type: 'state', chain: r.chain, owners: r.owners, target: r.target,
  turn: r.turn, over: r.over, you: i, players: r.players.map(q => ({ name: q.name, score: q.score })) }));

function randomPair(len = 4) {
  const pool = [...words].filter(w => w.length === len);
  for (;;) { const s = pool[Math.random() * pool.length | 0], d = [...bfs(s)].filter(([, v]) => v >= 4 && v <= 7); if (d.length) return [s, d[Math.random() * d.length | 0][0]]; }
}
function bfs(a) { const d = new Map([[a, 0]]), q = [a]; while (q.length) { const w = q.shift(); for (const n of nbrs(w)) if (!d.has(n)) { d.set(n, d.get(w) + 1); q.push(n); } } return d; }

async function finish(r) {
  r.over = true; castState(r);
  if (!SB_URL || !SB_KEY || r.saved) return; r.saved = true;
  const winner = r.chain.at(-1) === r.target ? r.owners.at(-1) : null;
  try {
    await fetch(`${SB_URL}/rest/v1/rpc/record_match`, { method: 'POST',
      headers: { apikey: SB_KEY, Authorization: `Bearer ${SB_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ p_room: r.code, p_start: r.chain[0], p_target: r.target, p_chain: r.chain, p_winner: winner,
        p_players: r.players.map(p => ({ name: p.name, score: p.score })) }) });
  } catch (e) { console.error('supabase', e.message); }
}

const server = http.createServer((q, s) => { s.writeHead(200); s.end('ok'); });
const wss = new WebSocketServer({ server });
wss.on('connection', ws => {
  let room = null, me = null;
  ws.on('message', raw => {
    let m; try { m = JSON.parse(raw); } catch { return; }
    if (m.type === 'create' || m.type === 'join') {
      if (room) return;
      let code = String(m.code || '').toUpperCase();
      if (m.type === 'create') { do code = Math.random().toString(36).slice(2, 6).toUpperCase(); while (rooms.has(code));
        rooms.set(code, { code, players: [], chain: [], owners: [], turn: 0, passes: 0, over: true }); }
      const r = rooms.get(code);
      if (!r) return send(ws, { type: 'error', why: 'Room not found' });
      if (r.players.length >= 6) return send(ws, { type: 'error', why: 'Room is full' });
      if (!r.over) return send(ws, { type: 'error', why: 'Game already started' });
      room = r; me = { ws, name: String(m.name || 'Player').slice(0, 12), score: 0 }; r.players.push(me);
      send(ws, { type: 'joined', code, host: r.players[0] === me }); return castLobby(r);
    }
    if (!room) return;
    if (m.type === 'start' && room.players[0] === me && room.over && room.players.length >= 2) {
      const [s, t] = randomPair();
      Object.assign(room, { chain: [s], owners: [''], target: t, turn: 0, passes: 0, over: false, saved: false });
      room.players.forEach(p => p.score = 0); return castState(room);
    }
    if (room.over || room.players[room.turn] !== me) return;
    const next = () => { room.turn = (room.turn + 1) % room.players.length; castState(room); };
    if (m.type === 'pass') { me.score -= 1; return ++room.passes >= room.players.length ? finish(room) : next(); }
    if (m.type === 'play') {
      const w = String(m.word).toLowerCase(), cur = room.chain.at(-1);
      if (w.length !== cur.length || [...w].filter((c, i) => c !== cur[i]).length !== 1 || room.chain.includes(w) || !words.has(w))
        return send(ws, { type: 'rejected', word: w });
      const used = new Set(room.chain), better = dist(w, room.target, used) < dist(cur, room.target, used);
      room.chain.push(w); room.owners.push(me.name); room.passes = 0; me.score += better ? 2 : 1;
      if (w === room.target) { me.score += 10; return finish(room); }
      next();
    }
  });
  ws.on('close', () => {
    if (!room) return;
    room.players = room.players.filter(p => p !== me);
    if (!room.players.length) return rooms.delete(room.code);
    room.turn %= room.players.length;
    if (!room.over && room.players.length < 2) return finish(room);
    room.over ? castLobby(room) : castState(room);
  });
});
loadWords().then(() => server.listen(process.env.PORT || 8080, () => console.log('listening')));
