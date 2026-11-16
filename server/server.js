// Word Ladder server: authoritative rooms, matchmaking and invites over WebSocket; results saved to Supabase.
const http = require('http'), fs = require('fs'), { WebSocketServer } = require('ws');
const SB_URL = process.env.SUPABASE_URL, SB_KEY = process.env.SUPABASE_SERVICE_KEY;
const WORDS_URL = process.env.WORDS_URL || 'https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt';
let words = new Set(), queue = [], nextId = 1; const rooms = new Map(), peers = new Map();

async function loadWords() {
  let t; if (fs.existsSync('words.txt')) t = fs.readFileSync('words.txt', 'utf8');
  else { t = await (await fetch(WORDS_URL)).text(); fs.writeFileSync('words.txt', t); }
  words = new Set(t.split(/\s+/).map(w => w.toLowerCase()).filter(w => /^[a-z]{3,8}$/.test(w)));
  console.log('words loaded:', words.size);
}
const nbrs = w => { const o = []; for (let i = 0; i < w.length; i++) for (const c of 'abcdefghijklmnopqrstuvwxyz') if (c !== w[i]) { const n = w.slice(0, i) + c + w.slice(i + 1); if (words.has(n)) o.push(n); } return o; };
function bfs(a) { const d = new Map([[a, 0]]), q = [a]; for (let i = 0; i < q.length; i++) { const w = q[i]; for (const n of nbrs(w)) if (!d.has(n)) { d.set(n, d.get(w) + 1); q.push(n); } } return d; }
function randomPair(len = 4) {
  const pool = [...words].filter(w => w.length === len);
  for (let n = 0; n < 400; n++) {
    const s = pool[Math.random() * pool.length | 0], lo = n > 200 ? 1 : n > 40 ? 2 : 4;
    const d = [...bfs(s)].filter(([, v]) => v >= lo && v <= 7); if (d.length) return [s, d[Math.random() * d.length | 0][0]];
  }
}

// Scoring (same rules as the app): base 10, +15 per step closer (max 3), +3..15 within 5 steps, +20 new closest, x speed, +100 at target.
const mult = ms => ms <= 8000 ? 2 : ms <= 15000 ? 1.5 : ms <= 30000 ? 1.2 : 1;
function scoreMove(r, w) {
  const prev = r.dm.get(r.chain.at(-1)) ?? 99, now = r.dm.get(w) ?? 99; let pts = 10;
  if (now < prev) pts += 15 * Math.min(3, prev - now);
  if (now >= 1 && now <= 5) pts += (6 - now) * 3;
  if (now < r.best) { pts += 20; r.best = now; }
  pts = Math.round(pts * mult(Date.now() - r.turnStart));
  return w === r.target ? pts + 100 : pts;
}

const send = (ws, m) => ws.readyState === 1 && ws.send(JSON.stringify(m));
const castLobby = r => r.players.forEach(p => send(p.ws, { type: 'lobby', players: r.players.map(q => q.name) }));
const castState = r => r.players.forEach((p, i) => send(p.ws, { type: 'state', chain: r.chain, owners: r.owners, points: r.points, target: r.target,
  turn: r.turn, over: r.over, you: i, players: r.players.map(q => ({ name: q.name, score: q.score })) }));
function presence() {
  const idle = [...peers.values()].filter(p => !p.room);
  idle.forEach(p => send(p.ws, { type: 'online', peers: idle.filter(q => q !== p).map(q => ({ id: q.id, name: q.name })) }));
}
function newRoom() {
  let code; do code = Math.random().toString(36).slice(2, 6).toUpperCase(); while (rooms.has(code));
  const r = { code, players: [], chain: [], owners: [], points: [], turn: 0, passes: 0, over: true }; rooms.set(code, r); return r;
}
function enter(r, p) { r.players.push(p); p.room = r; p.score = 0; send(p.ws, { type: 'joined', code: r.code, host: r.players[0] === p }); }
function begin(r, s, t) {
  const dm = bfs(t);
  Object.assign(r, { chain: [s], owners: [''], points: [0], target: t, dm, best: dm.get(s) ?? 99, turn: 0, passes: 0, over: false, saved: false, turnStart: Date.now() });
  r.players.forEach(p => p.score = 0); castState(r);
}
function quick(a, b) {                      // matchmaking / accepted invite: instant game with a random ladder
  queue = queue.filter(p => p !== a && p !== b);
  const r = newRoom(); enter(r, a); enter(r, b);
  [a, b].forEach(p => send(p.ws, { type: 'queued', on: false }));
  const [s, t] = randomPair(4 + (Math.random() * 3 | 0)); begin(r, s, t); presence();
}
async function finish(r) {
  r.over = true; castState(r);
  if (!SB_URL || !SB_KEY || r.saved) return; r.saved = true;
  try {
    await fetch(`${SB_URL}/rest/v1/rpc/record_match`, { method: 'POST',
      headers: { apikey: SB_KEY, Authorization: `Bearer ${SB_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ p_room: r.code, p_start: r.chain[0], p_target: r.target, p_chain: r.chain,
        p_winner: r.chain.at(-1) === r.target ? r.owners.at(-1) : null, p_players: r.players.map(p => ({ name: p.name, score: p.score })) }) });
  } catch (e) { console.error('supabase', e.message); }
}

const server = http.createServer((q, s) => { s.writeHead(200); s.end('ok'); });
const wss = new WebSocketServer({ server });
wss.on('connection', ws => {
  let me = null;
  ws.on('message', raw => {
    let m; try { m = JSON.parse(raw); } catch { return; }
    if (m.type === 'hello') {
      if (me) return;
      me = { id: 'p' + nextId++, ws, name: String(m.name || 'Player').slice(0, 12), score: 0, room: null };
      peers.set(me.id, me); send(ws, { type: 'welcome', id: me.id }); return presence();
    }
    if (!me) return;
    const r = me.room;
    if (!r) {
      if (m.type === 'create') { const nr = newRoom(); enter(nr, me); castLobby(nr); return presence(); }
      if (m.type === 'join') {
        const jr = rooms.get(String(m.code || '').toUpperCase());
        if (!jr) return send(ws, { type: 'error', why: 'Room not found' });
        if (jr.players.length >= 6) return send(ws, { type: 'error', why: 'Room is full' });
        if (!jr.over) return send(ws, { type: 'error', why: 'Game already started' });
        enter(jr, me); castLobby(jr); return presence();
      }
      if (m.type === 'queue') {
        queue = queue.filter(p => p !== me); if (m.on) queue.push(me);
        send(ws, { type: 'queued', on: !!m.on });
        while (queue.length >= 2) quick(queue[0], queue[1]); return;
      }
      if (m.type === 'invite') { const t = peers.get(m.to); if (t && !t.room) send(t.ws, { type: 'invited', id: me.id, name: me.name }); return; }
      if (m.type === 'accept') { const a = peers.get(m.from); if (a && !a.room) quick(a, me); return; }
      return;
    }
    if (m.type === 'start' && r.players[0] === me && r.over && r.players.length >= 2) {
      const s = String(m.start || '').toLowerCase(), t = String(m.target || '').toLowerCase();
      if (!s || !t) return send(ws, { type: 'error', why: 'Choose a start and target word' });
      if (s.length !== t.length || s === t || !words.has(s) || !words.has(t) || !bfs(t).has(s)) return send(ws, { type: 'error', why: 'No ladder connects those words' });
      return begin(r, s, t);
    }
    if (r.over || r.players[r.turn] !== me) return;
    const next = () => { r.turn = (r.turn + 1) % r.players.length; r.turnStart = Date.now(); castState(r); };
    if (m.type === 'pass') return ++r.passes >= r.players.length ? finish(r) : next();   // passing is free
    if (m.type === 'play') {
      const w = String(m.word).toLowerCase(), cur = r.chain.at(-1);
      const why = w.length !== cur.length || [...w].filter((c, i) => c !== cur[i]).length !== 1 ? 'Change exactly one letter'
        : r.chain.includes(w) ? 'Already used' : !words.has(w) ? 'Not in dictionary' : null;
      if (why) { console.log('rejected', w, why); return send(ws, { type: 'rejected', why }); }
      const pts = scoreMove(r, w);
      r.chain.push(w); r.owners.push(me.name); r.points.push(pts); r.passes = 0; me.score += pts;
      return w === r.target ? finish(r) : next();
    }
  });
  ws.on('close', () => {
    if (!me) return;
    peers.delete(me.id); queue = queue.filter(p => p !== me);
    const r = me.room;
    if (r) {
      r.players = r.players.filter(p => p !== me);
      if (!r.players.length) rooms.delete(r.code);
      else { r.turn %= r.players.length; if (!r.over && r.players.length < 2) finish(r); else r.over ? castLobby(r) : castState(r); }
    }
    presence();
  });
});
loadWords().then(() => server.listen(process.env.PORT || 8080, () => console.log('listening')));
