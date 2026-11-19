// Бизнес-логика Компаса: пользователи, сессии, чаты, сообщения.
// Ошибки бросаются как HttpError — app.js превращает их в JSON-ответы.
import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export const LIMITS = {
  username: [3, 32],
  name: [1, 64],
  bio: 200,
  password: [6, 128],
  title: [1, 64],
  text: 4000,
  groupMembers: 200,
  pageSize: 50,
};

const USERNAME_RE = /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/;
const COLORS = 8;

function hashPassword(password, salt = randomBytes(16).toString('hex')) {
  const hash = scryptSync(password, salt, 32).toString('hex');
  return `${salt}:${hash}`;
}

function checkPassword(password, stored) {
  const [salt, hash] = stored.split(':');
  const candidate = scryptSync(password, salt, 32);
  return timingSafeEqual(candidate, Buffer.from(hash, 'hex'));
}

function str(v) {
  return typeof v === 'string' ? v.trim() : '';
}

export class Messenger {
  /** @param {import('node:sqlite').DatabaseSync} db */
  constructor(db, { now = () => Date.now() } = {}) {
    this.db = db;
    this.now = now;
    this.online = new Map(); // userId -> Set<WsConnection>
  }

  // ---------- realtime ----------

  connect(userId, conn) {
    let set = this.online.get(userId);
    const wasOffline = !set;
    if (!set) this.online.set(userId, (set = new Set()));
    set.add(conn);
    if (wasOffline) this.#presence(userId, true);
  }

  disconnect(userId, conn) {
    const set = this.online.get(userId);
    if (!set) return;
    set.delete(conn);
    if (set.size === 0) {
      this.online.delete(userId);
      this.db.prepare('UPDATE users SET last_seen = ? WHERE id = ?').run(this.now(), userId);
      this.#presence(userId, false);
    }
  }

  emit(userIds, event) {
    const data = JSON.stringify(event);
    for (const id of new Set(userIds)) {
      for (const conn of this.online.get(id) ?? []) conn.send(data);
    }
  }

  #presence(userId, online) {
    // Сообщаем о статусе всем, с кем у пользователя есть общие чаты
    const peers = this.db.prepare(`
      SELECT DISTINCT m2.user_id AS id FROM members m1
      JOIN members m2 ON m2.chat_id = m1.chat_id
      WHERE m1.user_id = ? AND m2.user_id != ?`).all(userId, userId).map((r) => r.id);
    this.emit(peers, { type: 'presence', userId, online, lastSeen: online ? null : this.now() });
  }

  typing(userId, chatId) {
    if (!this.#isMember(chatId, userId)) return;
    this.emit(this.#memberIds(chatId).filter((id) => id !== userId), { type: 'typing', chatId, userId });
  }

  // ---------- пользователи и сессии ----------

  publicUser(row) {
    if (!row) return null;
    return {
      id: row.id,
      username: row.username,
      name: row.name,
      bio: row.bio,
      color: row.color,
      online: this.online.has(row.id),
      lastSeen: row.last_seen || null,
    };
  }

  register({ username, name, password }, device = '') {
    username = str(username);
    name = str(name) || username;
    password = typeof password === 'string' ? password : '';
    if (!USERNAME_RE.test(username)) {
      throw new HttpError(400, 'Имя пользователя: 3–32 символа, латиница, цифры и _, начинается с буквы');
    }
    if (name.length > LIMITS.name[1]) throw new HttpError(400, 'Имя слишком длинное');
    if (password.length < LIMITS.password[0] || password.length > LIMITS.password[1]) {
      throw new HttpError(400, 'Пароль: от 6 до 128 символов');
    }
    if (this.db.prepare('SELECT 1 FROM users WHERE username = ?').get(username)) {
      throw new HttpError(409, 'Это имя пользователя уже занято');
    }
    const color = Math.floor(Math.random() * COLORS);
    const { lastInsertRowid } = this.db.prepare(
      'INSERT INTO users (username, name, color, pass_hash, created_at) VALUES (?, ?, ?, ?, ?)',
    ).run(username, name, color, hashPassword(password), this.now());
    return this.#newSession(Number(lastInsertRowid), device);
  }

  login({ username, password }, device = '') {
    const row = this.db.prepare('SELECT * FROM users WHERE username = ?').get(str(username));
    if (!row || typeof password !== 'string' || !checkPassword(password, row.pass_hash)) {
      throw new HttpError(401, 'Неверное имя пользователя или пароль');
    }
    return this.#newSession(row.id, device);
  }

  #newSession(userId, device) {
    const token = randomBytes(32).toString('base64url');
    this.db.prepare('INSERT INTO sessions (token, user_id, device, created_at) VALUES (?, ?, ?, ?)')
      .run(token, userId, String(device).slice(0, 200), this.now());
    return { token, user: this.me(userId) };
  }

  logout(token) {
    this.db.prepare('DELETE FROM sessions WHERE token = ?').run(token);
  }

  userByToken(token) {
    if (!token) return null;
    const row = this.db.prepare('SELECT user_id FROM sessions WHERE token = ?').get(token);
    return row ? row.user_id : null;
  }

  me(userId) {
    const row = this.db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
    return this.publicUser(row);
  }

  updateMe(userId, { name, bio }) {
    const cur = this.db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
    const newName = name === undefined ? cur.name : str(name);
    const newBio = bio === undefined ? cur.bio : str(bio);
    if (newName.length < LIMITS.name[0] || newName.length > LIMITS.name[1]) {
      throw new HttpError(400, 'Имя: от 1 до 64 символов');
    }
    if (newBio.length > LIMITS.bio) throw new HttpError(400, 'О себе: до 200 символов');
    this.db.prepare('UPDATE users SET name = ?, bio = ? WHERE id = ?').run(newName, newBio, userId);
    const user = this.me(userId);
    const peers = this.db.prepare(`
      SELECT DISTINCT m2.user_id AS id FROM members m1
      JOIN members m2 ON m2.chat_id = m1.chat_id WHERE m1.user_id = ?`).all(userId).map((r) => r.id);
    this.emit([userId, ...peers], { type: 'user', user });
    return user;
  }

  changePassword(userId, { oldPassword, newPassword }) {
    const row = this.db.prepare('SELECT pass_hash FROM users WHERE id = ?').get(userId);
    if (typeof oldPassword !== 'string' || !checkPassword(oldPassword, row.pass_hash)) {
      throw new HttpError(403, 'Текущий пароль неверный');
    }
    if (typeof newPassword !== 'string' || newPassword.length < 6 || newPassword.length > 128) {
      throw new HttpError(400, 'Пароль: от 6 до 128 символов');
    }
    this.db.prepare('UPDATE users SET pass_hash = ? WHERE id = ?').run(hashPassword(newPassword), userId);
  }

  getUser(id) {
    const user = this.publicUser(this.db.prepare('SELECT * FROM users WHERE id = ?').get(Number(id)));
    if (!user) throw new HttpError(404, 'Пользователь не найден');
    return user;
  }

  searchUsers(userId, q) {
    q = str(q).replace(/^@/, '');
    if (!q) return [];
    const like = `%${q.replace(/[\\%_]/g, (c) => '\\' + c)}%`;
    return this.db.prepare(`
      SELECT * FROM users
      WHERE id != ? AND (username LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\')
      ORDER BY (username = ? COLLATE NOCASE) DESC, username LIMIT 20`)
      .all(userId, like, like, q).map((r) => this.publicUser(r));
  }

  // ---------- чаты ----------

  #isMember(chatId, userId) {
    return !!this.db.prepare('SELECT 1 FROM members WHERE chat_id = ? AND user_id = ?').get(chatId, userId);
  }

  #memberIds(chatId) {
    return this.db.prepare('SELECT user_id FROM members WHERE chat_id = ?').all(chatId).map((r) => r.user_id);
  }

  #requireMember(chatId, userId) {
    chatId = Number(chatId);
    const chat = this.db.prepare('SELECT * FROM chats WHERE id = ?').get(chatId);
    if (!chat || !this.#isMember(chatId, userId)) throw new HttpError(404, 'Чат не найден');
    return chat;
  }

  chatView(chat, userId) {
    const members = this.db.prepare(`
      SELECT u.*, m.role, m.last_read_id FROM members m JOIN users u ON u.id = m.user_id
      WHERE m.chat_id = ? ORDER BY m.joined_at`).all(chat.id);
    const mine = members.find((m) => m.id === userId);
    const lastReadId = mine?.last_read_id ?? 0;
    const othersRead = Math.max(0, ...members.filter((m) => m.id !== userId).map((m) => m.last_read_id));
    const last = this.db.prepare(
      'SELECT * FROM messages WHERE chat_id = ? AND deleted = 0 ORDER BY id DESC LIMIT 1',
    ).get(chat.id);
    const { n } = this.db.prepare(
      'SELECT COUNT(*) AS n FROM messages WHERE chat_id = ? AND id > ? AND user_id != ? AND deleted = 0',
    ).get(chat.id, lastReadId, userId);
    return {
      id: chat.id,
      type: chat.type,
      title: chat.title,
      createdBy: chat.created_by,
      createdAt: chat.created_at,
      members: members.map((m) => ({ ...this.publicUser(m), role: m.role })),
      lastMessage: last ? this.messageView(last) : null,
      unread: n,
      lastReadId,
      readUpTo: othersRead,
    };
  }

  listChats(userId) {
    const chats = this.db.prepare(`
      SELECT c.*, COALESCE((SELECT MAX(id) FROM messages WHERE chat_id = c.id), 0) AS last_id
      FROM chats c JOIN members m ON m.chat_id = c.id
      WHERE m.user_id = ?
      ORDER BY CASE WHEN last_id = 0 THEN c.created_at ELSE (SELECT created_at FROM messages WHERE id = last_id) END DESC`)
      .all(userId);
    return chats.map((c) => this.chatView(c, userId));
  }

  getChat(userId, chatId) {
    return this.chatView(this.#requireMember(chatId, userId), userId);
  }

  openDirect(userId, peerId) {
    peerId = Number(peerId);
    if (peerId === userId) throw new HttpError(400, 'Нельзя создать чат с самим собой');
    if (!this.db.prepare('SELECT 1 FROM users WHERE id = ?').get(peerId)) {
      throw new HttpError(404, 'Пользователь не найден');
    }
    const key = [userId, peerId].sort((a, b) => a - b).join(':');
    let chat = this.db.prepare('SELECT * FROM chats WHERE direct_key = ?').get(key);
    if (!chat) {
      const t = this.now();
      const { lastInsertRowid } = this.db.prepare(
        "INSERT INTO chats (type, direct_key, created_by, created_at) VALUES ('direct', ?, ?, ?)",
      ).run(key, userId, t);
      const add = this.db.prepare('INSERT INTO members (chat_id, user_id, joined_at) VALUES (?, ?, ?)');
      add.run(lastInsertRowid, userId, t);
      add.run(lastInsertRowid, peerId, t);
      chat = this.db.prepare('SELECT * FROM chats WHERE id = ?').get(lastInsertRowid);
      this.emit([peerId], { type: 'chat', chat: this.chatView(chat, peerId) });
    }
    return this.chatView(chat, userId);
  }

  createGroup(userId, { title, members }) {
    title = str(title);
    if (title.length < LIMITS.title[0] || title.length > LIMITS.title[1]) {
      throw new HttpError(400, 'Название группы: от 1 до 64 символов');
    }
    const ids = [...new Set((Array.isArray(members) ? members : []).map(Number))]
      .filter((id) => id !== userId);
    if (ids.length + 1 > LIMITS.groupMembers) throw new HttpError(400, 'Слишком много участников');
    const existing = ids.filter((id) => this.db.prepare('SELECT 1 FROM users WHERE id = ?').get(id));
    const t = this.now();
    const { lastInsertRowid } = this.db.prepare(
      "INSERT INTO chats (type, title, created_by, created_at) VALUES ('group', ?, ?, ?)",
    ).run(title, userId, t);
    const chatId = Number(lastInsertRowid);
    this.db.prepare("INSERT INTO members (chat_id, user_id, role, joined_at) VALUES (?, ?, 'owner', ?)")
      .run(chatId, userId, t);
    const add = this.db.prepare('INSERT INTO members (chat_id, user_id, joined_at) VALUES (?, ?, ?)');
    for (const id of existing) add.run(chatId, id, t);
    this.#system(chatId, userId, 'created');
    const chat = this.db.prepare('SELECT * FROM chats WHERE id = ?').get(chatId);
    for (const id of existing) this.emit([id], { type: 'chat', chat: this.chatView(chat, id) });
    return this.chatView(chat, userId);
  }

  renameGroup(userId, chatId, title) {
    const chat = this.#requireMember(chatId, userId);
    if (chat.type !== 'group') throw new HttpError(400, 'Переименовать можно только группу');
    title = str(title);
    if (title.length < 1 || title.length > 64) throw new HttpError(400, 'Название группы: от 1 до 64 символов');
    this.db.prepare('UPDATE chats SET title = ? WHERE id = ?').run(title, chat.id);
    this.#system(chat.id, userId, `renamed:${title}`);
    this.#broadcastChat(chat.id);
    return this.getChat(userId, chat.id);
  }

  addMembers(userId, chatId, userIds) {
    const chat = this.#requireMember(chatId, userId);
    if (chat.type !== 'group') throw new HttpError(400, 'Добавлять участников можно только в группу');
    const current = new Set(this.#memberIds(chat.id));
    const ids = [...new Set((Array.isArray(userIds) ? userIds : []).map(Number))]
      .filter((id) => !current.has(id) && this.db.prepare('SELECT 1 FROM users WHERE id = ?').get(id));
    if (current.size + ids.length > LIMITS.groupMembers) throw new HttpError(400, 'Слишком много участников');
    const add = this.db.prepare('INSERT INTO members (chat_id, user_id, joined_at) VALUES (?, ?, ?)');
    for (const id of ids) {
      add.run(chat.id, id, this.now());
      this.#system(chat.id, userId, `added:${id}`);
    }
    this.#broadcastChat(chat.id);
    return this.getChat(userId, chat.id);
  }

  leaveChat(userId, chatId) {
    const chat = this.#requireMember(chatId, userId);
    if (chat.type !== 'group') throw new HttpError(400, 'Из личного чата выйти нельзя');
    this.#system(chat.id, userId, 'left');
    this.db.prepare('DELETE FROM members WHERE chat_id = ? AND user_id = ?').run(chat.id, userId);
    const rest = this.db.prepare('SELECT user_id, role FROM members WHERE chat_id = ? ORDER BY joined_at').all(chat.id);
    if (rest.length === 0) {
      this.db.prepare('DELETE FROM chats WHERE id = ?').run(chat.id);
    } else if (!rest.some((m) => m.role === 'owner')) {
      this.db.prepare("UPDATE members SET role = 'owner' WHERE chat_id = ? AND user_id = ?").run(chat.id, rest[0].user_id);
    }
    this.emit([userId], { type: 'chat_removed', chatId: chat.id });
    if (rest.length) this.#broadcastChat(chat.id);
  }

  #broadcastChat(chatId) {
    const chat = this.db.prepare('SELECT * FROM chats WHERE id = ?').get(chatId);
    for (const id of this.#memberIds(chatId)) this.emit([id], { type: 'chat', chat: this.chatView(chat, id) });
  }

  // ---------- сообщения ----------

  messageView(m) {
    let reply = null;
    if (m.reply_to) {
      const r = this.db.prepare('SELECT id, user_id, kind, text, deleted FROM messages WHERE id = ?').get(m.reply_to);
      if (r) reply = { id: r.id, userId: r.user_id, kind: r.kind, text: r.deleted ? '' : r.text.slice(0, 200), deleted: !!r.deleted };
    }
    return {
      id: m.id,
      chatId: m.chat_id,
      userId: m.user_id,
      kind: m.kind,
      text: m.deleted ? '' : m.text,
      file: m.deleted ? null : m.file,
      replyTo: reply,
      createdAt: m.created_at,
      editedAt: m.edited_at,
      deleted: !!m.deleted,
    };
  }

  #system(chatId, userId, text) {
    const { lastInsertRowid } = this.db.prepare(
      "INSERT INTO messages (chat_id, user_id, kind, text, created_at) VALUES (?, ?, 'system', ?, ?)",
    ).run(chatId, userId, text, this.now());
    const msg = this.messageView(this.db.prepare('SELECT * FROM messages WHERE id = ?').get(lastInsertRowid));
    this.emit(this.#memberIds(chatId), { type: 'message', message: msg });
  }

  listMessages(userId, chatId, { before, limit } = {}) {
    const chat = this.#requireMember(chatId, userId);
    const n = Math.min(Math.max(Number(limit) || LIMITS.pageSize, 1), 100);
    const rows = before
      ? this.db.prepare('SELECT * FROM messages WHERE chat_id = ? AND id < ? ORDER BY id DESC LIMIT ?').all(chat.id, Number(before), n)
      : this.db.prepare('SELECT * FROM messages WHERE chat_id = ? ORDER BY id DESC LIMIT ?').all(chat.id, n);
    return rows.reverse().map((m) => this.messageView(m));
  }

  sendMessage(userId, chatId, { text, replyTo, file }) {
    const chat = this.#requireMember(chatId, userId);
    text = typeof text === 'string' ? text.trim() : '';
    file = typeof file === 'string' && /^[a-f0-9]{32}\.(jpg|png|gif|webp)$/.test(file) ? file : null;
    if (!text && !file) throw new HttpError(400, 'Пустое сообщение');
    if (text.length > LIMITS.text) throw new HttpError(400, `Сообщение длиннее ${LIMITS.text} символов`);
    let reply = null;
    if (replyTo) {
      reply = this.db.prepare('SELECT id FROM messages WHERE id = ? AND chat_id = ?').get(Number(replyTo), chat.id);
      if (!reply) throw new HttpError(400, 'Сообщение для ответа не найдено');
    }
    const { lastInsertRowid } = this.db.prepare(
      'INSERT INTO messages (chat_id, user_id, kind, text, file, reply_to, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    ).run(chat.id, userId, file ? 'image' : 'text', text, file, reply?.id ?? null, this.now());
    const id = Number(lastInsertRowid);
    this.db.prepare('UPDATE members SET last_read_id = ? WHERE chat_id = ? AND user_id = ?').run(id, chat.id, userId);
    const msg = this.messageView(this.db.prepare('SELECT * FROM messages WHERE id = ?').get(id));
    const members = this.#memberIds(chat.id);
    // Отправитель заодно прочитал всё, что было до его сообщения — сообщаем, чтобы у собеседника появились ✓✓
    this.emit(members, { type: 'read', chatId: chat.id, userId, messageId: id });
    this.emit(members, { type: 'message', message: msg });
    return msg;
  }

  #ownMessage(userId, messageId) {
    const m = this.db.prepare('SELECT * FROM messages WHERE id = ?').get(Number(messageId));
    if (!m || !this.#isMember(m.chat_id, userId)) throw new HttpError(404, 'Сообщение не найдено');
    if (m.user_id !== userId || m.kind === 'system') throw new HttpError(403, 'Можно менять только свои сообщения');
    if (m.deleted) throw new HttpError(410, 'Сообщение удалено');
    return m;
  }

  editMessage(userId, messageId, text) {
    const m = this.#ownMessage(userId, messageId);
    text = typeof text === 'string' ? text.trim() : '';
    if (!text && !m.file) throw new HttpError(400, 'Пустое сообщение');
    if (text.length > LIMITS.text) throw new HttpError(400, `Сообщение длиннее ${LIMITS.text} символов`);
    this.db.prepare('UPDATE messages SET text = ?, edited_at = ? WHERE id = ?').run(text, this.now(), m.id);
    const msg = this.messageView(this.db.prepare('SELECT * FROM messages WHERE id = ?').get(m.id));
    this.emit(this.#memberIds(m.chat_id), { type: 'message_edit', message: msg });
    return msg;
  }

  deleteMessage(userId, messageId) {
    const m = this.#ownMessage(userId, messageId);
    this.db.prepare("UPDATE messages SET deleted = 1, text = '', file = NULL WHERE id = ?").run(m.id);
    this.emit(this.#memberIds(m.chat_id), { type: 'message_delete', chatId: m.chat_id, messageId: m.id });
    return m.file; // имя файла, чтобы app.js удалил его с диска
  }

  markRead(userId, chatId, messageId) {
    const chat = this.#requireMember(chatId, userId);
    const { max } = this.db.prepare('SELECT COALESCE(MAX(id), 0) AS max FROM messages WHERE chat_id = ?').get(chat.id);
    const id = Math.min(Number(messageId) || max, max);
    const res = this.db.prepare(
      'UPDATE members SET last_read_id = ? WHERE chat_id = ? AND user_id = ? AND last_read_id < ?',
    ).run(id, chat.id, userId, id);
    if (res.changes) this.emit(this.#memberIds(chat.id), { type: 'read', chatId: chat.id, userId, messageId: id });
    return { lastReadId: id };
  }

  canSeeFile(userId, file) {
    return !!this.db.prepare(`
      SELECT 1 FROM messages m JOIN members mb ON mb.chat_id = m.chat_id
      WHERE m.file = ? AND mb.user_id = ? LIMIT 1`).get(file, userId);
  }
}
