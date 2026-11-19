// Бизнес-логика Компаса: пользователи, сессии, чаты, каналы, сообщения.
// Ошибки бросаются как HttpError — app.js превращает их в JSON-ответы.
import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export const LIMITS = {
  name: 64,
  bio: 200,
  title: 64,
  description: 255,
  text: 4000,
  groupMembers: 200,
  pageSize: 50,
  forwardTargets: 20,
};

export const REACTIONS = ['👍', '❤️', '🔥', '😂', '😮', '😢', '🙏', '👏', '🎉', '🤔', '👎', '💯'];

const HANDLE_RE = /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/;
const IMAGE_FILE = /^[a-f0-9]{32}\.(jpg|png|gif|webp)$/;
const VOICE_FILE = /^[a-f0-9]{32}\.(webm|ogg|m4a|mp3|aac)$/;
const COLORS = 8;

function hashPassword(password, salt = randomBytes(16).toString('hex')) {
  return `${salt}:${scryptSync(password, salt, 32).toString('hex')}`;
}

function checkPassword(password, stored) {
  const [salt, hash] = stored.split(':');
  return timingSafeEqual(scryptSync(password, salt, 32), Buffer.from(hash, 'hex'));
}

const str = (v) => (typeof v === 'string' ? v.trim() : '');

export class Messenger {
  /** @param {import('node:sqlite').DatabaseSync} db */
  constructor(db, { now = () => Date.now() } = {}) {
    this.db = db;
    this.now = now;
    this.online = new Map(); // userId -> Set<WsConnection>
  }

  #one(sql, ...args) { return this.db.prepare(sql).get(...args); }
  #all(sql, ...args) { return this.db.prepare(sql).all(...args); }
  #run(sql, ...args) { return this.db.prepare(sql).run(...args); }

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
      this.#run('UPDATE users SET last_seen = ? WHERE id = ?', this.now(), userId);
      this.#presence(userId, false);
    }
  }

  emit(userIds, event) {
    const data = JSON.stringify(event);
    for (const id of new Set(userIds)) {
      for (const conn of this.online.get(id) ?? []) conn.send(data);
    }
  }

  /** Люди, с которыми у пользователя есть личный чат или общая группа (не каналы). */
  #contacts(userId) {
    return this.#all(`
      SELECT DISTINCT m2.user_id AS id FROM members m1
      JOIN chats c ON c.id = m1.chat_id AND c.type IN ('direct', 'group')
      JOIN members m2 ON m2.chat_id = m1.chat_id
      WHERE m1.user_id = ? AND m2.user_id != ?`, userId, userId).map((r) => r.id);
  }

  #presence(userId, online) {
    this.emit(this.#contacts(userId), { type: 'presence', userId, online, lastSeen: online ? null : this.now() });
  }

  typing(userId, chatId) {
    const chat = this.#one('SELECT * FROM chats WHERE id = ?', chatId);
    if (!chat || chat.type === 'channel' || chat.type === 'saved' || !this.#member(chatId, userId)) return;
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
      avatar: row.avatar || null,
      online: this.online.has(row.id),
      lastSeen: row.last_seen || null,
    };
  }

  #handleTaken(handle, exceptChatId = 0) {
    return !!this.#one('SELECT 1 FROM users WHERE username = ?', handle)
      || !!this.#one('SELECT 1 FROM chats WHERE handle = ? AND id != ?', handle, exceptChatId);
  }

  register({ username, name, password }, device = '') {
    username = str(username);
    name = str(name) || username;
    password = typeof password === 'string' ? password : '';
    if (!HANDLE_RE.test(username)) {
      throw new HttpError(400, 'Имя пользователя: 3–32 символа, латиница, цифры и _, начинается с буквы');
    }
    if (name.length > LIMITS.name) throw new HttpError(400, 'Имя слишком длинное');
    if (password.length < 6 || password.length > 128) throw new HttpError(400, 'Пароль: от 6 до 128 символов');
    if (this.#handleTaken(username)) throw new HttpError(409, 'Это имя пользователя уже занято');
    const { lastInsertRowid } = this.#run(
      'INSERT INTO users (username, name, color, pass_hash, created_at) VALUES (?, ?, ?, ?, ?)',
      username, name, Math.floor(Math.random() * COLORS), hashPassword(password), this.now(),
    );
    const userId = Number(lastInsertRowid);
    this.savedChat(userId);
    return this.#newSession(userId, device);
  }

  login({ username, password }, device = '') {
    const row = this.#one('SELECT * FROM users WHERE username = ?', str(username).replace(/^@/, ''));
    if (!row || typeof password !== 'string' || !checkPassword(password, row.pass_hash)) {
      throw new HttpError(401, 'Неверное имя пользователя или пароль');
    }
    return this.#newSession(row.id, device);
  }

  #newSession(userId, device) {
    const token = randomBytes(32).toString('base64url');
    this.#run('INSERT INTO sessions (token, user_id, device, created_at) VALUES (?, ?, ?, ?)',
      token, userId, String(device || '').slice(0, 200), this.now());
    return { token, user: this.me(userId) };
  }

  logout(token) {
    this.#run('DELETE FROM sessions WHERE token = ?', token);
  }

  userByToken(token) {
    if (!token) return null;
    return this.#one('SELECT user_id FROM sessions WHERE token = ?', token)?.user_id ?? null;
  }

  me(userId) {
    return this.publicUser(this.#one('SELECT * FROM users WHERE id = ?', userId));
  }

  updateMe(userId, { name, bio, avatar, username }) {
    const cur = this.#one('SELECT * FROM users WHERE id = ?', userId);
    const newName = name === undefined ? cur.name : str(name);
    const newBio = bio === undefined ? cur.bio : str(bio);
    let newAvatar = cur.avatar;
    if (avatar !== undefined) {
      if (avatar !== null && !IMAGE_FILE.test(avatar)) throw new HttpError(400, 'Некорректное фото');
      newAvatar = avatar;
    }
    let newUsername = cur.username;
    if (username !== undefined && str(username).replace(/^@/, '') !== cur.username) {
      newUsername = str(username).replace(/^@/, '');
      if (!HANDLE_RE.test(newUsername)) throw new HttpError(400, 'Имя пользователя: 3–32 символа, латиница, цифры и _');
      if (newUsername.toLowerCase() !== cur.username.toLowerCase() && this.#handleTaken(newUsername)) {
        throw new HttpError(409, 'Это имя пользователя уже занято');
      }
    }
    if (newName.length < 1 || newName.length > LIMITS.name) throw new HttpError(400, 'Имя: от 1 до 64 символов');
    if (newBio.length > LIMITS.bio) throw new HttpError(400, 'О себе: до 200 символов');
    this.#run('UPDATE users SET name = ?, bio = ?, avatar = ?, username = ? WHERE id = ?',
      newName, newBio, newAvatar, newUsername, userId);
    const user = this.me(userId);
    this.emit([userId, ...this.#contacts(userId)], { type: 'user', user });
    return user;
  }

  changePassword(userId, { oldPassword, newPassword }) {
    const row = this.#one('SELECT pass_hash FROM users WHERE id = ?', userId);
    if (typeof oldPassword !== 'string' || !checkPassword(oldPassword, row.pass_hash)) {
      throw new HttpError(403, 'Текущий пароль неверный');
    }
    if (typeof newPassword !== 'string' || newPassword.length < 6 || newPassword.length > 128) {
      throw new HttpError(400, 'Пароль: от 6 до 128 символов');
    }
    this.#run('UPDATE users SET pass_hash = ? WHERE id = ?', hashPassword(newPassword), userId);
  }

  getUser(id) {
    const user = this.publicUser(this.#one('SELECT * FROM users WHERE id = ?', Number(id)));
    if (!user) throw new HttpError(404, 'Пользователь не найден');
    return user;
  }

  #like(q) {
    return `%${q.replace(/[\\%_]/g, (c) => '\\' + c)}%`;
  }

  searchUsers(userId, q) {
    q = str(q).replace(/^@/, '');
    if (!q) return [];
    const like = this.#like(q);
    return this.#all(`
      SELECT * FROM users
      WHERE id != ? AND (username LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\')
      ORDER BY (username = ? COLLATE NOCASE) DESC, username LIMIT 20`, userId, like, like, q)
      .map((r) => this.publicUser(r));
  }

  /** Глобальный поиск: люди и публичные каналы. */
  search(userId, q) {
    const users = this.searchUsers(userId, q);
    q = str(q).replace(/^@/, '');
    if (!q) return { users, channels: [] };
    const like = this.#like(q);
    const channels = this.#all(`
      SELECT * FROM chats WHERE type = 'channel' AND handle IS NOT NULL
        AND (handle LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\')
      ORDER BY (handle = ? COLLATE NOCASE) DESC, title LIMIT 20`, like, like, q)
      .map((c) => this.chatView(c, userId));
    return { users, channels };
  }

  // ---------- чаты ----------

  #member(chatId, userId) {
    return this.#one('SELECT * FROM members WHERE chat_id = ? AND user_id = ?', chatId, userId);
  }

  #memberIds(chatId) {
    return this.#all('SELECT user_id FROM members WHERE chat_id = ?', chatId).map((r) => r.user_id);
  }

  /** Чат, который пользователь может читать (участник или публичный канал). */
  #readable(chatId, userId) {
    const chat = this.#one('SELECT * FROM chats WHERE id = ?', Number(chatId));
    if (!chat) throw new HttpError(404, 'Чат не найден');
    if (this.#member(chat.id, userId)) return chat;
    if (chat.type === 'channel' && chat.handle) return chat;
    throw new HttpError(404, 'Чат не найден');
  }

  #requireMember(chatId, userId) {
    const chat = this.#one('SELECT * FROM chats WHERE id = ?', Number(chatId));
    const m = chat && this.#member(chat.id, userId);
    if (!m) throw new HttpError(404, 'Чат не найден');
    return { chat, member: m };
  }

  #isAdmin(member) {
    return member && (member.role === 'owner' || member.role === 'admin');
  }

  #canPost(chat, member) {
    if (!member) return false;
    return chat.type !== 'channel' || this.#isAdmin(member);
  }

  chatView(chat, userId) {
    const me = this.#member(chat.id, userId);
    const lastReadId = me?.last_read_id ?? 0;
    const view = {
      id: chat.id,
      type: chat.type,
      title: chat.title,
      description: chat.description || '',
      handle: chat.handle || null,
      avatar: chat.avatar || null,
      createdBy: chat.created_by,
      createdAt: chat.created_at,
      joined: !!me,
      role: me?.role ?? null,
      pinned: !!me?.pinned,
      muted: !!me?.muted,
      canPost: this.#canPost(chat, me),
      lastReadId,
      readUpTo: 0,
      unread: 0,
      memberCount: 0,
      members: [],
      lastMessage: null,
      pinnedMessage: null,
    };
    const { n: memberCount } = this.#one('SELECT COUNT(*) AS n FROM members WHERE chat_id = ?', chat.id);
    view.memberCount = memberCount;
    if (chat.type !== 'channel') {
      const members = this.#all(`
        SELECT u.*, m.role, m.last_read_id FROM members m JOIN users u ON u.id = m.user_id
        WHERE m.chat_id = ? ORDER BY m.joined_at`, chat.id);
      view.members = members.map((m) => ({ ...this.publicUser(m), role: m.role }));
      view.readUpTo = chat.type === 'saved' ? Number.MAX_SAFE_INTEGER
        : Math.max(0, ...members.filter((m) => m.id !== userId).map((m) => m.last_read_id));
    } else {
      // В канале ✓✓ — когда пост прочитал хотя бы один подписчик
      view.readUpTo = this.#one('SELECT COALESCE(MAX(last_read_id), 0) AS r FROM members WHERE chat_id = ? AND user_id != ?', chat.id, userId).r;
    }
    const last = this.#one('SELECT * FROM messages WHERE chat_id = ? AND deleted = 0 ORDER BY id DESC LIMIT 1', chat.id);
    if (last) view.lastMessage = this.messageView(last);
    if (me) {
      view.unread = this.#one(
        "SELECT COUNT(*) AS n FROM messages WHERE chat_id = ? AND id > ? AND user_id != ? AND deleted = 0 AND kind != 'system'",
        chat.id, lastReadId, userId).n;
    }
    if (chat.pinned_message_id) {
      const p = this.#one('SELECT * FROM messages WHERE id = ? AND deleted = 0', chat.pinned_message_id);
      if (p) view.pinnedMessage = this.messageView(p);
    }
    return view;
  }

  listChats(userId) {
    this.savedChat(userId);
    return this.#all('SELECT c.* FROM chats c JOIN members m ON m.chat_id = c.id WHERE m.user_id = ?', userId)
      .map((c) => this.chatView(c, userId))
      .sort((a, b) => (b.pinned - a.pinned) || ((b.lastMessage?.createdAt ?? b.createdAt) - (a.lastMessage?.createdAt ?? a.createdAt)));
  }

  getChat(userId, chatId) {
    return this.chatView(this.#readable(chatId, userId), userId);
  }

  /** «Избранное» — личный чат с самим собой (создаётся автоматически). */
  savedChat(userId) {
    const key = `saved:${userId}`;
    let chat = this.#one('SELECT * FROM chats WHERE direct_key = ?', key);
    if (!chat) {
      const t = this.now();
      const { lastInsertRowid } = this.#run(
        "INSERT INTO chats (type, title, direct_key, created_by, created_at) VALUES ('saved', 'Избранное', ?, ?, ?)", key, userId, t);
      this.#run("INSERT INTO members (chat_id, user_id, role, joined_at) VALUES (?, ?, 'owner', ?)", lastInsertRowid, userId, t);
      chat = this.#one('SELECT * FROM chats WHERE id = ?', lastInsertRowid);
    }
    return chat;
  }

  openSaved(userId) {
    return this.chatView(this.savedChat(userId), userId);
  }

  openDirect(userId, peerId) {
    peerId = Number(peerId);
    if (peerId === userId) return this.openSaved(userId);
    if (!this.#one('SELECT 1 FROM users WHERE id = ?', peerId)) throw new HttpError(404, 'Пользователь не найден');
    const key = [userId, peerId].sort((a, b) => a - b).join(':');
    let chat = this.#one('SELECT * FROM chats WHERE direct_key = ?', key);
    if (!chat) {
      const t = this.now();
      const { lastInsertRowid } = this.#run(
        "INSERT INTO chats (type, direct_key, created_by, created_at) VALUES ('direct', ?, ?, ?)", key, userId, t);
      const add = this.db.prepare('INSERT INTO members (chat_id, user_id, joined_at) VALUES (?, ?, ?)');
      add.run(lastInsertRowid, userId, t);
      add.run(lastInsertRowid, peerId, t);
      chat = this.#one('SELECT * FROM chats WHERE id = ?', lastInsertRowid);
    }
    return this.chatView(chat, userId);
  }

  #validTitle(title) {
    title = str(title);
    if (title.length < 1 || title.length > LIMITS.title) throw new HttpError(400, 'Название: от 1 до 64 символов');
    return title;
  }

  #validDescription(d) {
    d = str(d);
    if (d.length > LIMITS.description) throw new HttpError(400, 'Описание: до 255 символов');
    return d;
  }

  #validHandle(handle, chatId = 0) {
    handle = str(handle).replace(/^@/, '');
    if (!handle) return null;
    if (!HANDLE_RE.test(handle)) throw new HttpError(400, 'Ссылка: 3–32 символа, латиница, цифры и _, начинается с буквы');
    if (this.#handleTaken(handle, chatId)) throw new HttpError(409, 'Эта ссылка уже занята');
    return handle;
  }

  #validAvatar(a) {
    if (a === undefined) return undefined;
    if (a !== null && !IMAGE_FILE.test(a)) throw new HttpError(400, 'Некорректное фото');
    return a;
  }

  #existingUsers(ids, except) {
    return [...new Set((Array.isArray(ids) ? ids : []).map(Number))]
      .filter((id) => id !== except && this.#one('SELECT 1 FROM users WHERE id = ?', id));
  }

  createGroup(userId, { title, members, avatar }) {
    title = this.#validTitle(title);
    const ids = this.#existingUsers(members, userId);
    if (ids.length + 1 > LIMITS.groupMembers) throw new HttpError(400, 'Слишком много участников');
    const t = this.now();
    const { lastInsertRowid } = this.#run(
      "INSERT INTO chats (type, title, avatar, created_by, created_at) VALUES ('group', ?, ?, ?, ?)",
      title, this.#validAvatar(avatar) ?? null, userId, t);
    const chatId = Number(lastInsertRowid);
    this.#run("INSERT INTO members (chat_id, user_id, role, joined_at) VALUES (?, ?, 'owner', ?)", chatId, userId, t);
    const add = this.db.prepare('INSERT INTO members (chat_id, user_id, joined_at) VALUES (?, ?, ?)');
    for (const id of ids) add.run(chatId, id, t);
    this.#system(chatId, userId, 'created');
    this.#broadcastChat(chatId);
    return this.getChat(userId, chatId);
  }

  createChannel(userId, { title, description, handle, avatar }) {
    title = this.#validTitle(title);
    description = this.#validDescription(description);
    handle = this.#validHandle(handle);
    const t = this.now();
    const { lastInsertRowid } = this.#run(
      "INSERT INTO chats (type, title, description, handle, avatar, created_by, created_at) VALUES ('channel', ?, ?, ?, ?, ?, ?)",
      title, description, handle, this.#validAvatar(avatar) ?? null, userId, t);
    const chatId = Number(lastInsertRowid);
    this.#run("INSERT INTO members (chat_id, user_id, role, joined_at) VALUES (?, ?, 'owner', ?)", chatId, userId, t);
    this.#system(chatId, userId, 'channel_created');
    return this.getChat(userId, chatId);
  }

  updateChat(userId, chatId, { title, description, handle, avatar }) {
    const { chat, member } = this.#requireMember(chatId, userId);
    if (chat.type === 'direct' || chat.type === 'saved') throw new HttpError(400, 'Этот чат нельзя изменить');
    if (chat.type === 'channel' && !this.#isAdmin(member)) throw new HttpError(403, 'Изменять канал могут только админы');
    if (title !== undefined) {
      const t = this.#validTitle(title);
      if (t !== chat.title) {
        this.#run('UPDATE chats SET title = ? WHERE id = ?', t, chat.id);
        this.#system(chat.id, userId, `renamed:${t}`);
      }
    }
    if (description !== undefined) this.#run('UPDATE chats SET description = ? WHERE id = ?', this.#validDescription(description), chat.id);
    if (handle !== undefined && chat.type === 'channel') this.#run('UPDATE chats SET handle = ? WHERE id = ?', this.#validHandle(handle, chat.id), chat.id);
    const a = this.#validAvatar(avatar);
    if (a !== undefined) this.#run('UPDATE chats SET avatar = ? WHERE id = ?', a, chat.id);
    this.#broadcastChat(chat.id);
    return this.getChat(userId, chat.id);
  }

  addMembers(userId, chatId, userIds) {
    const { chat } = this.#requireMember(chatId, userId);
    if (chat.type !== 'group') throw new HttpError(400, 'Добавлять участников можно только в группу');
    const current = new Set(this.#memberIds(chat.id));
    const ids = this.#existingUsers(userIds, userId).filter((id) => !current.has(id));
    if (current.size + ids.length > LIMITS.groupMembers) throw new HttpError(400, 'Слишком много участников');
    const add = this.db.prepare('INSERT INTO members (chat_id, user_id, joined_at) VALUES (?, ?, ?)');
    for (const id of ids) {
      add.run(chat.id, id, this.now());
      this.#system(chat.id, userId, `added:${id}`);
    }
    this.#broadcastChat(chat.id);
    return this.getChat(userId, chat.id);
  }

  joinChannel(userId, chatId) {
    const chat = this.#readable(chatId, userId);
    if (chat.type !== 'channel') throw new HttpError(400, 'Подписаться можно только на канал');
    if (!this.#member(chat.id, userId)) {
      const { m } = this.#one('SELECT COALESCE(MAX(id), 0) AS m FROM messages WHERE chat_id = ?', chat.id);
      this.#run('INSERT INTO members (chat_id, user_id, last_read_id, joined_at) VALUES (?, ?, ?, ?)', chat.id, userId, m, this.now());
    }
    const view = this.getChat(userId, chat.id);
    this.emit([userId], { type: 'chat', chat: view });
    return view;
  }

  leaveChat(userId, chatId) {
    const { chat } = this.#requireMember(chatId, userId);
    if (chat.type === 'direct' || chat.type === 'saved') throw new HttpError(400, 'Из этого чата выйти нельзя');
    if (chat.type === 'group') this.#system(chat.id, userId, 'left');
    this.#run('DELETE FROM members WHERE chat_id = ? AND user_id = ?', chat.id, userId);
    const rest = this.#all('SELECT user_id, role FROM members WHERE chat_id = ? ORDER BY joined_at', chat.id);
    if (rest.length === 0) {
      this.#run('DELETE FROM chats WHERE id = ?', chat.id);
    } else if (!rest.some((m) => m.role === 'owner')) {
      this.#run("UPDATE members SET role = 'owner' WHERE chat_id = ? AND user_id = ?", chat.id, rest[0].user_id);
    }
    this.emit([userId], { type: 'chat_removed', chatId: chat.id });
    if (rest.length && chat.type === 'group') this.#broadcastChat(chat.id);
  }

  /** Личные настройки чата: закрепить в списке, без звука. */
  chatSettings(userId, chatId, { pinned, muted }) {
    const { chat } = this.#requireMember(chatId, userId);
    if (pinned !== undefined) this.#run('UPDATE members SET pinned = ? WHERE chat_id = ? AND user_id = ?', pinned ? this.now() : 0, chat.id, userId);
    if (muted !== undefined) this.#run('UPDATE members SET muted = ? WHERE chat_id = ? AND user_id = ?', muted ? 1 : 0, chat.id, userId);
    const view = this.getChat(userId, chat.id);
    this.emit([userId], { type: 'chat', chat: view });
    return view;
  }

  pinMessage(userId, chatId, messageId) {
    const { chat, member } = this.#requireMember(chatId, userId);
    if (chat.type === 'channel' && !this.#isAdmin(member)) throw new HttpError(403, 'Закреплять могут только админы');
    let id = null;
    if (messageId) {
      const m = this.#one("SELECT * FROM messages WHERE id = ? AND chat_id = ? AND deleted = 0 AND kind != 'system'", Number(messageId), chat.id);
      if (!m) throw new HttpError(404, 'Сообщение не найдено');
      id = m.id;
    }
    this.#run('UPDATE chats SET pinned_message_id = ? WHERE id = ?', id, chat.id);
    if (id && chat.type === 'group') this.#system(chat.id, userId, `pinned:${id}`);
    this.#broadcastChat(chat.id);
    return this.getChat(userId, chat.id);
  }

  #broadcastChat(chatId) {
    const chat = this.#one('SELECT * FROM chats WHERE id = ?', chatId);
    if (!chat) return;
    const ids = this.#memberIds(chatId);
    if (chat.type === 'channel') {
      // У подписчиков разные счётчики, но общие поля одинаковые — шлём лёгкое событие
      this.emit(ids, { type: 'chat_meta', chatId, title: chat.title, description: chat.description, handle: chat.handle, avatar: chat.avatar,
        pinnedMessage: chat.pinned_message_id ? this.#viewById(chat.pinned_message_id) : null });
      return;
    }
    for (const id of ids) this.emit([id], { type: 'chat', chat: this.chatView(chat, id) });
  }

  // ---------- сообщения ----------

  #viewById(id) {
    const m = this.#one('SELECT * FROM messages WHERE id = ? AND deleted = 0', id);
    return m ? this.messageView(m) : null;
  }

  reactionsOf(messageId) {
    const rows = this.#all('SELECT emoji, user_id FROM reactions WHERE message_id = ? ORDER BY created_at', messageId);
    const map = new Map();
    for (const r of rows) {
      if (!map.has(r.emoji)) map.set(r.emoji, []);
      map.get(r.emoji).push(r.user_id);
    }
    return [...map].map(([emoji, userIds]) => ({ emoji, userIds }));
  }

  messageView(m) {
    let reply = null;
    if (m.reply_to) {
      const r = this.#one('SELECT id, user_id, kind, text, deleted FROM messages WHERE id = ?', m.reply_to);
      if (r) reply = { id: r.id, userId: r.user_id, kind: r.kind, text: r.deleted ? '' : r.text.slice(0, 200), deleted: !!r.deleted };
    }
    return {
      id: m.id,
      chatId: m.chat_id,
      userId: m.user_id,
      kind: m.kind,
      text: m.deleted ? '' : m.text,
      file: m.deleted ? null : m.file,
      duration: m.duration || null,
      replyTo: reply,
      forward: m.fwd_name ? { userId: m.fwd_user_id, chatId: m.fwd_chat_id, name: m.fwd_name } : null,
      reactions: m.deleted ? [] : this.reactionsOf(m.id),
      createdAt: m.created_at,
      editedAt: m.edited_at,
      deleted: !!m.deleted,
    };
  }

  #system(chatId, userId, text) {
    const { lastInsertRowid } = this.#run(
      "INSERT INTO messages (chat_id, user_id, kind, text, created_at) VALUES (?, ?, 'system', ?, ?)",
      chatId, userId, text, this.now());
    this.emit(this.#memberIds(chatId), { type: 'message', message: this.#viewById(lastInsertRowid) });
  }

  listMessages(userId, chatId, { before, limit } = {}) {
    const chat = this.#readable(chatId, userId);
    const n = Math.min(Math.max(Number(limit) || LIMITS.pageSize, 1), 100);
    const rows = before
      ? this.#all('SELECT * FROM messages WHERE chat_id = ? AND id < ? ORDER BY id DESC LIMIT ?', chat.id, Number(before), n)
      : this.#all('SELECT * FROM messages WHERE chat_id = ? ORDER BY id DESC LIMIT ?', chat.id, n);
    return rows.reverse().map((m) => this.messageView(m));
  }

  #insertMessage(chat, userId, fields) {
    const { lastInsertRowid } = this.#run(
      `INSERT INTO messages (chat_id, user_id, kind, text, file, duration, reply_to, fwd_user_id, fwd_chat_id, fwd_name, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      chat.id, userId, fields.kind, fields.text, fields.file ?? null, fields.duration ?? null, fields.replyTo ?? null,
      fields.fwdUserId ?? null, fields.fwdChatId ?? null, fields.fwdName ?? null, this.now());
    const id = Number(lastInsertRowid);
    this.#run('UPDATE members SET last_read_id = ? WHERE chat_id = ? AND user_id = ?', id, chat.id, userId);
    const msg = this.#viewById(id);
    const members = this.#memberIds(chat.id);
    // Отправитель заодно прочитал всё, что было до его сообщения — у собеседника появятся ✓✓
    this.emit(members, { type: 'read', chatId: chat.id, userId, messageId: id });
    this.emit(members, { type: 'message', message: msg });
    return msg;
  }

  sendMessage(userId, chatId, { text, replyTo, file, duration }) {
    const { chat, member } = this.#requireMember(chatId, userId);
    if (!this.#canPost(chat, member)) throw new HttpError(403, 'Публиковать в канале могут только админы');
    text = typeof text === 'string' ? text.trim() : '';
    let kind = 'text';
    if (typeof file === 'string' && IMAGE_FILE.test(file)) kind = 'image';
    else if (typeof file === 'string' && VOICE_FILE.test(file)) kind = 'voice';
    else file = null;
    if (!text && !file) throw new HttpError(400, 'Пустое сообщение');
    if (text.length > LIMITS.text) throw new HttpError(400, `Сообщение длиннее ${LIMITS.text} символов`);
    let reply = null;
    if (replyTo) {
      reply = this.#one('SELECT id FROM messages WHERE id = ? AND chat_id = ?', Number(replyTo), chat.id);
      if (!reply) throw new HttpError(400, 'Сообщение для ответа не найдено');
    }
    const dur = kind === 'voice' ? Math.max(0, Math.min(3600, Math.round(Number(duration) || 0))) : null;
    return this.#insertMessage(chat, userId, { kind, text: kind === 'voice' ? '' : text, file, duration: dur, replyTo: reply?.id });
  }

  forwardMessages(userId, { messageIds, chatIds }) {
    const ids = [...new Set((Array.isArray(messageIds) ? messageIds : [messageIds]).map(Number))].slice(0, 100);
    const targets = [...new Set((Array.isArray(chatIds) ? chatIds : [chatIds]).map(Number))].slice(0, LIMITS.forwardTargets);
    if (!ids.length || !targets.length) throw new HttpError(400, 'Нечего пересылать');
    const sources = ids.map((id) => {
      const m = this.#one("SELECT * FROM messages WHERE id = ? AND deleted = 0 AND kind != 'system'", id);
      if (!m) throw new HttpError(404, 'Сообщение не найдено');
      const chat = this.#readable(m.chat_id, userId);
      return { m, chat };
    }).sort((a, b) => a.m.id - b.m.id);
    const sent = [];
    for (const target of targets) {
      const { chat, member } = this.#requireMember(target, userId);
      if (!this.#canPost(chat, member)) throw new HttpError(403, 'Публиковать в канале могут только админы');
      for (const { m, chat: src } of sources) {
        let fwd;
        if (m.fwd_name) fwd = { fwdUserId: m.fwd_user_id, fwdChatId: m.fwd_chat_id, fwdName: m.fwd_name };
        else if (src.type === 'channel') fwd = { fwdChatId: src.id, fwdName: src.title };
        else {
          const author = this.#one('SELECT name FROM users WHERE id = ?', m.user_id);
          fwd = { fwdUserId: m.user_id, fwdName: author?.name || 'Пользователь' };
        }
        sent.push(this.#insertMessage(chat, userId, { kind: m.kind, text: m.text, file: m.file, duration: m.duration, ...fwd }));
      }
    }
    return sent;
  }

  #messageFor(userId, messageId) {
    const m = this.#one('SELECT * FROM messages WHERE id = ?', Number(messageId));
    if (!m) throw new HttpError(404, 'Сообщение не найдено');
    const chat = this.#readable(m.chat_id, userId);
    return { m, chat, member: this.#member(chat.id, userId) };
  }

  editMessage(userId, messageId, text) {
    const { m } = this.#messageFor(userId, messageId);
    if (m.user_id !== userId || m.kind === 'system' || m.fwd_name) throw new HttpError(403, 'Это сообщение нельзя изменить');
    if (m.deleted) throw new HttpError(410, 'Сообщение удалено');
    if (m.kind === 'voice') throw new HttpError(400, 'Голосовое сообщение нельзя изменить');
    text = typeof text === 'string' ? text.trim() : '';
    if (!text && !m.file) throw new HttpError(400, 'Пустое сообщение');
    if (text.length > LIMITS.text) throw new HttpError(400, `Сообщение длиннее ${LIMITS.text} символов`);
    this.#run('UPDATE messages SET text = ?, edited_at = ? WHERE id = ?', text, this.now(), m.id);
    const msg = this.#viewById(m.id);
    this.emit(this.#memberIds(m.chat_id), { type: 'message_edit', message: msg });
    return msg;
  }

  /** Удаляет сообщение у всех. Возвращает имя файла, если на него больше никто не ссылается. */
  deleteMessage(userId, messageId) {
    const { m, chat, member } = this.#messageFor(userId, messageId);
    if (m.deleted) return null;
    const own = m.user_id === userId && m.kind !== 'system';
    const admin = (chat.type === 'group' || chat.type === 'channel') && this.#isAdmin(member);
    if (!own && !admin) throw new HttpError(403, 'Можно удалять только свои сообщения');
    this.#run("UPDATE messages SET deleted = 1, text = '', file = NULL WHERE id = ?", m.id);
    this.#run('DELETE FROM reactions WHERE message_id = ?', m.id);
    if (chat.pinned_message_id === m.id) {
      this.#run('UPDATE chats SET pinned_message_id = NULL WHERE id = ?', chat.id);
      this.#broadcastChat(chat.id);
    }
    this.emit(this.#memberIds(m.chat_id), { type: 'message_delete', chatId: m.chat_id, messageId: m.id });
    if (m.file && !this.#fileInUse(m.file)) return m.file;
    return null;
  }

  #fileInUse(file) {
    return !!this.#one('SELECT 1 FROM messages WHERE file = ? LIMIT 1', file)
      || !!this.#one('SELECT 1 FROM users WHERE avatar = ? LIMIT 1', file)
      || !!this.#one('SELECT 1 FROM chats WHERE avatar = ? LIMIT 1', file);
  }

  react(userId, messageId, emoji) {
    const { m, member } = this.#messageFor(userId, messageId);
    if (!member) throw new HttpError(403, 'Подпишитесь, чтобы ставить реакции');
    if (m.deleted || m.kind === 'system') throw new HttpError(400, 'Сюда нельзя поставить реакцию');
    const cur = this.#one('SELECT emoji FROM reactions WHERE message_id = ? AND user_id = ?', m.id, userId);
    if (!emoji || cur?.emoji === emoji) {
      this.#run('DELETE FROM reactions WHERE message_id = ? AND user_id = ?', m.id, userId);
    } else {
      if (!REACTIONS.includes(emoji)) throw new HttpError(400, 'Такой реакции нет');
      this.#run(`INSERT INTO reactions (message_id, user_id, emoji, created_at) VALUES (?, ?, ?, ?)
        ON CONFLICT (message_id, user_id) DO UPDATE SET emoji = excluded.emoji, created_at = excluded.created_at`,
      m.id, userId, emoji, this.now());
    }
    const reactions = this.reactionsOf(m.id);
    this.emit(this.#memberIds(m.chat_id), { type: 'reactions', chatId: m.chat_id, messageId: m.id, reactions });
    return { reactions };
  }

  markRead(userId, chatId, messageId) {
    const { chat } = this.#requireMember(chatId, userId);
    const { max } = this.#one('SELECT COALESCE(MAX(id), 0) AS max FROM messages WHERE chat_id = ?', chat.id);
    const id = Math.min(Number(messageId) || max, max);
    const res = this.#run('UPDATE members SET last_read_id = ? WHERE chat_id = ? AND user_id = ? AND last_read_id < ?',
      id, chat.id, userId, id);
    if (res.changes) {
      // В канале прочтения видят только админы (и сам читатель — для синхронизации устройств)
      const to = chat.type === 'channel'
        ? [userId, ...this.#all("SELECT user_id FROM members WHERE chat_id = ? AND role IN ('owner', 'admin')", chat.id).map((r) => r.user_id)]
        : this.#memberIds(chat.id);
      this.emit(to, { type: 'read', chatId: chat.id, userId, messageId: id });
    }
    return { lastReadId: id };
  }

  canSeeFile(userId, file) {
    // Аватарки видны всем, кто вошёл; вложения — участникам чата или читателям публичного канала
    if (this.#one('SELECT 1 FROM users WHERE avatar = ? LIMIT 1', file)) return true;
    if (this.#one('SELECT 1 FROM chats WHERE avatar = ? LIMIT 1', file)) return true;
    if (this.#one(`SELECT 1 FROM messages m JOIN members mb ON mb.chat_id = m.chat_id
      WHERE m.file = ? AND mb.user_id = ? LIMIT 1`, file, userId)) return true;
    return !!this.#one(`SELECT 1 FROM messages m JOIN chats c ON c.id = m.chat_id
      WHERE m.file = ? AND c.type = 'channel' AND c.handle IS NOT NULL LIMIT 1`, file);
  }
}
