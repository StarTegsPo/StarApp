import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { connect } from 'node:net';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createKompas } from '../server/app.js';

let kompas;
let base;
let port;
const dir = mkdtempSync(join(tmpdir(), 'kompas-'));

before(async () => {
  kompas = createKompas({ dbPath: ':memory:', filesDir: join(dir, 'files'), authLimit: 1000 });
  port = await kompas.listen(0, '127.0.0.1');
  base = `http://127.0.0.1:${port}`;
});
after(async () => {
  await kompas.close();
  rmSync(dir, { recursive: true, force: true });
});

async function call(method, path, body, token) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, data: await res.json().catch(() => null) };
}

async function signup(username, name = username) {
  const { status, data } = await call('POST', '/api/register', { username, name, password: 'secret123' });
  assert.equal(status, 200, JSON.stringify(data));
  return data;
}

/** Простейший WebSocket-клиент на сыром TCP — проверяет и наш серверный протокол. */
function wsClient(token) {
  return new Promise((resolve, reject) => {
    const sock = connect(port, '127.0.0.1');
    const events = [];
    const waiters = [];
    let buf = Buffer.alloc(0);
    let upgraded = false;
    sock.on('connect', () => {
      sock.write(`GET /ws?token=${encodeURIComponent(token)} HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ${randomBytes(16).toString('base64')}\r\nSec-WebSocket-Version: 13\r\n\r\n`);
    });
    sock.on('error', reject);
    sock.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      if (!upgraded) {
        const i = buf.indexOf('\r\n\r\n');
        if (i < 0) return;
        const head = buf.subarray(0, i).toString();
        if (!head.startsWith('HTTP/1.1 101')) return reject(new Error(head));
        upgraded = true;
        buf = buf.subarray(i + 4);
        resolve(client);
      }
      while (buf.length >= 2) {
        let len = buf[1] & 0x7f; let off = 2;
        if (len === 126) { len = buf.readUInt16BE(2); off = 4; } else if (len === 127) { len = Number(buf.readBigUInt64BE(2)); off = 10; }
        if (buf.length < off + len) return;
        const opcode = buf[0] & 0x0f;
        const payload = buf.subarray(off, off + len).toString();
        buf = buf.subarray(off + len);
        if (opcode !== 1) continue;
        const ev = JSON.parse(payload);
        events.push(ev);
        for (const w of [...waiters]) if (w.pred(ev)) { waiters.splice(waiters.indexOf(w), 1); w.ok(ev); }
      }
    });
    const client = {
      events,
      send(obj) {
        const payload = Buffer.from(JSON.stringify(obj));
        const mask = randomBytes(4);
        const masked = Buffer.from(payload.map((b, i) => b ^ mask[i & 3]));
        const header = payload.length < 126 ? Buffer.from([0x81, 0x80 | payload.length])
          : Buffer.from([0x81, 0x80 | 126, payload.length >> 8, payload.length & 0xff]);
        sock.write(Buffer.concat([header, mask, masked]));
      },
      wait(pred, ms = 2000) {
        const found = events.find(pred);
        if (found) return Promise.resolve(found);
        return new Promise((ok, fail) => {
          const w = { pred, ok };
          waiters.push(w);
          setTimeout(() => fail(new Error("timeout waiting for event; got " + JSON.stringify(events))), ms);
        });
      },
      close() { sock.destroy(); },
    };
  });
}

test('регистрация, вход и валидация', async () => {
  const a = await signup('anna', 'Анна');
  assert.ok(a.token);
  assert.equal(a.user.name, 'Анна');

  assert.equal((await call('POST', '/api/register', { username: 'ANNA', password: 'secret123' })).status, 409);
  assert.equal((await call('POST', '/api/register', { username: '1bad', password: 'secret123' })).status, 400);
  assert.equal((await call('POST', '/api/register', { username: 'shortpw', password: '123' })).status, 400);

  assert.equal((await call('POST', '/api/login', { username: 'anna', password: 'nope' })).status, 401);
  const login = await call('POST', '/api/login', { username: 'Anna', password: 'secret123' });
  assert.equal(login.status, 200);
  assert.equal(login.data.user.username, 'anna');

  assert.equal((await call('GET', '/api/me')).status, 401);
  const me = await call('GET', '/api/me', undefined, login.data.token);
  assert.equal(me.data.id, a.user.id);

  await call('POST', '/api/logout', {}, login.data.token);
  assert.equal((await call('GET', '/api/me', undefined, login.data.token)).status, 401);
});

test('личный чат: сообщения, ответы, правка, удаление, прочтение', async () => {
  const b = await signup('boris', 'Борис');
  const v = await signup('vera', 'Вера');

  const found = await call('GET', '/api/users/search?q=ver', undefined, b.token);
  assert.deepEqual(found.data.map((u) => u.username), ['vera']);

  const chat = (await call('POST', '/api/chats/direct', { userId: v.user.id }, b.token)).data;
  const again = (await call('POST', '/api/chats/direct', { userId: b.user.id }, v.token)).data;
  assert.equal(chat.id, again.id, 'один и тот же личный чат для обоих');
  assert.equal(chat.type, 'direct');

  const m1 = (await call('POST', `/api/chats/${chat.id}/messages`, { text: 'Привет!' }, b.token)).data;
  const m2 = (await call('POST', `/api/chats/${chat.id}/messages`, { text: 'Привет, Борис', replyTo: m1.id }, v.token)).data;
  assert.equal(m2.replyTo.id, m1.id);
  assert.equal(m2.replyTo.text, 'Привет!');

  let list = (await call('GET', '/api/chats', undefined, b.token)).data;
  assert.equal(list[0].unread, 1);
  assert.equal(list[0].lastMessage.id, m2.id);

  await call('POST', `/api/chats/${chat.id}/read`, { messageId: m2.id }, b.token);
  list = (await call('GET', '/api/chats', undefined, b.token)).data;
  assert.equal(list[0].unread, 0);
  const vChat = (await call('GET', `/api/chats/${chat.id}`, undefined, v.token)).data;
  assert.equal(vChat.readUpTo, m2.id, 'Вера видит, что Борис прочитал');

  assert.equal((await call('PATCH', `/api/messages/${m1.id}`, { text: 'взлом' }, v.token)).status, 403);
  const edited = (await call('PATCH', `/api/messages/${m1.id}`, { text: 'Привет всем!' }, b.token)).data;
  assert.equal(edited.text, 'Привет всем!');
  assert.ok(edited.editedAt);

  assert.equal((await call('DELETE', `/api/messages/${m1.id}`, undefined, b.token)).status, 200);
  const msgs = (await call('GET', `/api/chats/${chat.id}/messages`, undefined, v.token)).data;
  assert.equal(msgs[0].deleted, true);
  assert.equal(msgs[0].text, '');
  assert.equal(msgs[1].replyTo.deleted, true);

  // посторонний не видит чат
  const g = await signup('gleb');
  assert.equal((await call('GET', `/api/chats/${chat.id}/messages`, undefined, g.token)).status, 404);
  assert.equal((await call('POST', `/api/chats/${chat.id}/messages`, { text: 'x' }, g.token)).status, 404);
  assert.equal((await call('POST', `/api/chats/${chat.id}/messages`, { text: '   ' }, b.token)).status, 400);
});

test('группы: создание, добавление, переименование, выход', async () => {
  const d = await signup('dasha');
  const e = await signup('egor');
  const z = await signup('zhenya');
  const group = (await call('POST', '/api/chats/group', { title: 'Походы', members: [e.user.id] }, d.token)).data;
  assert.equal(group.members.length, 2);
  assert.equal(group.lastMessage.kind, 'system');

  const added = (await call('POST', `/api/chats/${group.id}/members`, { userIds: [z.user.id] }, e.token)).data;
  assert.equal(added.members.length, 3);
  const renamed = (await call('PATCH', `/api/chats/${group.id}`, { title: 'Горы' }, z.token)).data;
  assert.equal(renamed.title, 'Горы');

  await call('POST', `/api/chats/${group.id}/leave`, {}, d.token);
  const after = (await call('GET', `/api/chats/${group.id}`, undefined, e.token)).data;
  assert.equal(after.members.length, 2);
  assert.equal(after.members.find((m) => m.id === e.user.id).role, 'owner', 'роль создателя переходит дальше');
  assert.equal((await call('GET', `/api/chats/${group.id}`, undefined, d.token)).status, 404);
});

test('WebSocket: новые сообщения, набор текста и статус в сети', async () => {
  const k = await signup('kira');
  const l = await signup('lev');
  const chat = (await call('POST', '/api/chats/direct', { userId: l.user.id }, k.token)).data;

  const wsL = await wsClient(l.token);
  await wsL.wait((ev) => ev.type === 'hello');
  const wsK = await wsClient(k.token);
  await wsL.wait((ev) => ev.type === 'presence' && ev.userId === k.user.id && ev.online);

  wsK.send({ type: 'typing', chatId: chat.id });
  await wsL.wait((ev) => ev.type === 'typing' && ev.userId === k.user.id);

  const sent = (await call('POST', `/api/chats/${chat.id}/messages`, { text: 'Ты где?' }, k.token)).data;
  const got = await wsL.wait((ev) => ev.type === 'message' && ev.message.id === sent.id);
  assert.equal(got.message.text, 'Ты где?');

  await call('POST', `/api/chats/${chat.id}/read`, {}, l.token);
  await wsK.wait((ev) => ev.type === 'read' && ev.userId === l.user.id && ev.messageId === sent.id);

  wsK.close();
  await wsL.wait((ev) => ev.type === 'presence' && ev.userId === k.user.id && !ev.online);
  wsL.close();
});

test('фото: загрузка и доступ только участникам', async () => {
  const p = await signup('petr');
  const r = await signup('roma');
  const s = await signup('sasha');
  const chat = (await call('POST', '/api/chats/direct', { userId: r.user.id }, p.token)).data;

  const png = Buffer.from('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d4944415478da63f8ffff3f0005fe02fea7d6a4440000000049454e44ae426082', 'hex');
  const up = await fetch(base + '/api/upload', { method: 'POST', headers: { Authorization: 'Bearer ' + p.token, 'Content-Type': 'image/png' }, body: png });
  assert.equal(up.status, 200);
  const { file } = await up.json();
  assert.match(file, /^[a-f0-9]{32}\.png$/);

  const bad = await fetch(base + '/api/upload', { method: 'POST', headers: { Authorization: 'Bearer ' + p.token, 'Content-Type': 'text/html' }, body: '<script>' });
  assert.equal(bad.status, 415);

  const msg = (await call('POST', `/api/chats/${chat.id}/messages`, { file, text: 'Вид с вершины' }, p.token)).data;
  assert.equal(msg.kind, 'image');

  assert.equal((await fetch(`${base}/files/${file}?t=${r.token}`)).status, 200);
  assert.equal((await fetch(`${base}/files/${file}?t=${s.token}`)).status, 404, 'посторонний не видит фото');
  assert.equal((await fetch(`${base}/files/${file}`)).status, 404);
});

test('веб-клиент и CORS для мобильных приложений', async () => {
  const res = await fetch(base + '/');
  assert.equal(res.status, 200);
  assert.match(await res.text(), /<title>Компас<\/title>/);
  const pre = await fetch(base + '/api/me', { method: 'OPTIONS' });
  assert.equal(pre.status, 204);
  assert.equal(pre.headers.get('access-control-allow-origin'), '*');
  const traversal = await fetch(base + '/..%2f..%2fpackage.json');
  assert.doesNotMatch(await traversal.text(), /"name"/);
});

test('Избранное создаётся автоматически и сообщения в нём сразу прочитаны', async () => {
  const u = await signup('ulyana');
  const chats = (await call('GET', '/api/chats', undefined, u.token)).data;
  const saved = chats.find((c) => c.type === 'saved');
  assert.ok(saved, 'есть чат «Избранное»');
  const again = (await call('POST', '/api/chats/saved', {}, u.token)).data;
  assert.equal(again.id, saved.id);
  const self = (await call('POST', '/api/chats/direct', { userId: u.user.id }, u.token)).data;
  assert.equal(self.id, saved.id, 'чат с самим собой = Избранное');
  const m = (await call('POST', `/api/chats/${saved.id}/messages`, { text: 'заметка' }, u.token)).data;
  const view = (await call('GET', `/api/chats/${saved.id}`, undefined, u.token)).data;
  assert.ok(view.readUpTo >= m.id);
});

test('реакции, пересылка, закрепление', async () => {
  const a = await signup('fedor', 'Фёдор');
  const b = await signup('galya', 'Галя');
  const c = await signup('ivan', 'Иван');
  const ab = (await call('POST', '/api/chats/direct', { userId: b.user.id }, a.token)).data;
  const bc = (await call('POST', '/api/chats/direct', { userId: c.user.id }, b.token)).data;
  const m = (await call('POST', `/api/chats/${ab.id}/messages`, { text: 'Встречаемся в 7' }, a.token)).data;

  let r = (await call('POST', `/api/messages/${m.id}/react`, { emoji: '👍' }, b.token)).data;
  assert.deepEqual(r.reactions, [{ emoji: '👍', userIds: [b.user.id] }]);
  r = (await call('POST', `/api/messages/${m.id}/react`, { emoji: '❤️' }, b.token)).data;
  assert.deepEqual(r.reactions, [{ emoji: '❤️', userIds: [b.user.id] }], 'одна реакция на человека');
  r = (await call('POST', `/api/messages/${m.id}/react`, { emoji: '❤️' }, b.token)).data;
  assert.deepEqual(r.reactions, [], 'повторное нажатие снимает реакцию');
  assert.equal((await call('POST', `/api/messages/${m.id}/react`, { emoji: '🍔' }, b.token)).status, 400);
  assert.equal((await call('POST', `/api/messages/${m.id}/react`, { emoji: '👍' }, c.token)).status, 404);

  const fwd = (await call('POST', '/api/messages/forward', { messageIds: [m.id], chatIds: [bc.id] }, b.token)).data;
  assert.equal(fwd[0].forward.name, 'Фёдор');
  assert.equal(fwd[0].forward.userId, a.user.id);
  assert.equal(fwd[0].userId, b.user.id);
  assert.equal((await call('POST', '/api/messages/forward', { messageIds: [m.id], chatIds: [bc.id] }, c.token)).status, 404,
    'нельзя переслать то, чего не видишь');
  assert.equal((await call('PATCH', `/api/messages/${fwd[0].id}`, { text: 'x' }, b.token)).status, 403, 'пересланное не редактируется');

  const pinned = (await call('POST', `/api/chats/${ab.id}/pin`, { messageId: m.id }, b.token)).data;
  assert.equal(pinned.pinnedMessage.id, m.id);
  await call('DELETE', `/api/messages/${m.id}`, undefined, a.token);
  assert.equal((await call('GET', `/api/chats/${ab.id}`, undefined, b.token)).data.pinnedMessage, null, 'удалённое открепляется');

  const s = (await call('POST', `/api/chats/${ab.id}/settings`, { pinned: true, muted: true }, a.token)).data;
  assert.equal(s.pinned, true);
  assert.equal(s.muted, true);
  const list = (await call('GET', '/api/chats', undefined, a.token)).data;
  assert.equal(list[0].id, ab.id, 'закреплённый чат наверху');
});

test('каналы: публикуют только админы, подписка по поиску', async () => {
  const owner = await signup('kanal_owner', 'Автор');
  const reader = await signup('reader1', 'Читатель');
  assert.equal((await call('POST', '/api/chats/channel', { title: 'Новости', handle: 'reader1' }, owner.token)).status, 409,
    'ссылка канала не может совпадать с username');
  const ch = (await call('POST', '/api/chats/channel', { title: 'Новости Компаса', description: 'Всё о Компасе', handle: 'kompas_news' }, owner.token)).data;
  assert.equal(ch.type, 'channel');
  assert.equal(ch.canPost, true);
  const post = (await call('POST', `/api/chats/${ch.id}/messages`, { text: 'Первый пост' }, owner.token)).data;

  const found = (await call('GET', '/api/search?q=kompas_n', undefined, reader.token)).data;
  assert.equal(found.channels[0].id, ch.id);
  assert.equal(found.channels[0].joined, false);
  const preview = (await call('GET', `/api/chats/${ch.id}/messages`, undefined, reader.token)).data;
  assert.equal(preview.at(-1).text, 'Первый пост', 'публичный канал можно читать без подписки');
  assert.equal((await call('POST', `/api/messages/${post.id}/react`, { emoji: '🔥' }, reader.token)).status, 403);

  const joined = (await call('POST', `/api/chats/${ch.id}/join`, {}, reader.token)).data;
  assert.equal(joined.joined, true);
  assert.equal(joined.canPost, false);
  assert.equal(joined.unread, 0, 'старые посты не считаются непрочитанными');
  assert.equal(joined.memberCount, 2);
  assert.equal((await call('POST', `/api/chats/${ch.id}/messages`, { text: 'спам' }, reader.token)).status, 403);
  assert.equal((await call('POST', `/api/messages/${post.id}/react`, { emoji: '🔥' }, reader.token)).status, 200);

  await call('POST', `/api/chats/${ch.id}/messages`, { text: 'Второй пост' }, owner.token);
  const list = (await call('GET', '/api/chats', undefined, reader.token)).data;
  assert.equal(list.find((c) => c.id === ch.id).unread, 1);

  const fwd = (await call('POST', '/api/messages/forward', { messageIds: [post.id], chatIds: [list.find((c) => c.type === 'saved').id] }, reader.token)).data;
  assert.equal(fwd[0].forward.chatId, ch.id);
  assert.equal(fwd[0].forward.name, 'Новости Компаса');

  await call('POST', `/api/chats/${ch.id}/leave`, {}, reader.token);
  assert.equal((await call('GET', '/api/chats', undefined, reader.token)).data.some((c) => c.id === ch.id), false);
});

test('голосовые и аватарки', async () => {
  const v = await signup('voice_user');
  const w = await signup('voice_peer');
  const chat = (await call('POST', '/api/chats/direct', { userId: w.user.id }, v.token)).data;
  const audio = Buffer.alloc(3000, 7);
  const up = await (await fetch(base + '/api/upload', {
    method: 'POST', headers: { Authorization: 'Bearer ' + v.token, 'Content-Type': 'audio/webm;codecs=opus' }, body: audio,
  })).json();
  assert.match(up.file, /\.webm$/);
  const msg = (await call('POST', `/api/chats/${chat.id}/messages`, { file: up.file, duration: 3.4 }, v.token)).data;
  assert.equal(msg.kind, 'voice');
  assert.equal(msg.duration, 3);

  const part = await fetch(`${base}/files/${up.file}?t=${w.token}`, { headers: { Range: 'bytes=0-99' } });
  assert.equal(part.status, 206);
  assert.equal(part.headers.get('content-range'), 'bytes 0-99/3000');
  assert.equal((await part.arrayBuffer()).byteLength, 100);

  const png = Buffer.from('89504e470d0a1a0a', 'hex');
  const av = await (await fetch(base + '/api/upload', { method: 'POST', headers: { Authorization: 'Bearer ' + v.token, 'Content-Type': 'image/png' }, body: png })).json();
  const me = (await call('PATCH', '/api/me', { avatar: av.file }, v.token)).data;
  assert.equal(me.avatar, av.file);
  const stranger = await signup('stranger1');
  assert.equal((await fetch(`${base}/files/${av.file}?t=${stranger.token}`)).status, 200, 'аватарки видны всем');
});

test('миграция базы первой версии', async () => {
  const { DatabaseSync } = await import('node:sqlite');
  const path = join(dir, 'old.db');
  const old = new DatabaseSync(path);
  old.exec(`CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, name TEXT NOT NULL,
      bio TEXT NOT NULL DEFAULT '', color INTEGER NOT NULL DEFAULT 0, pass_hash TEXT NOT NULL, created_at INTEGER NOT NULL, last_seen INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL CHECK (type IN ('direct', 'group')), title TEXT NOT NULL DEFAULT '',
      direct_key TEXT UNIQUE, created_by INTEGER NOT NULL REFERENCES users(id), created_at INTEGER NOT NULL);
    CREATE TABLE members (chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      role TEXT NOT NULL DEFAULT 'member', last_read_id INTEGER NOT NULL DEFAULT 0, joined_at INTEGER NOT NULL, PRIMARY KEY (chat_id, user_id));
    CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
      user_id INTEGER NOT NULL REFERENCES users(id), kind TEXT NOT NULL DEFAULT 'text', text TEXT NOT NULL DEFAULT '', file TEXT, reply_to INTEGER,
      created_at INTEGER NOT NULL, edited_at INTEGER, deleted INTEGER NOT NULL DEFAULT 0);
    INSERT INTO users (username, name, pass_hash, created_at) VALUES ('old', 'Old', 'x:y', 1);
    INSERT INTO chats (type, title, created_by, created_at) VALUES ('group', 'Старая', 1, 1);
    INSERT INTO members (chat_id, user_id, joined_at) VALUES (1, 1, 1);`);
  old.close();
  const { openDb } = await import('../server/db.js');
  const db = openDb(path);
  db.prepare("INSERT INTO chats (type, title, created_by, created_at) VALUES ('channel', 'Новый', 1, 2)").run();
  assert.equal(db.prepare('SELECT title FROM chats WHERE id = 1').get().title, 'Старая');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM members').get().n, 1);
  db.close();
});
