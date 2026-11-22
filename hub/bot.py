"""StarTegs Hub — один универсальный бот вместо четырёх.

В Telegram:  удалённые и изменённые сообщения (Telegram Business), нейросеть «Звёздочка», маркет объявлений,
             аналитика каналов и групп, напоминания, заметки, мини-приложение в стиле iOS.
Для приложения StarTegs: тот же протокол, что у старого бота (POST /api) — стиль для друзей, витрины,
             облачный архив удалённых и нейросеть без своего ключа.

Запуск:  BOT_TOKEN=… ADMIN_ID=… PUBLIC_URL=https://… python bot.py   (см. README.md)
"""
import asyncio
import hashlib
import hmac
import html
import json
import logging
import os
import re
import secrets
import time
import uuid
from urllib.parse import parse_qsl

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ChatMemberStatus, ChatType, ParseMode
from aiogram.filters import Command, CommandObject, CommandStart
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.types import (BusinessConnection, BusinessMessagesDeleted, CallbackQuery, ChatMemberUpdated,
                           InlineKeyboardButton, InlineKeyboardMarkup, LabeledPrice, MenuButtonWebApp, Message,
                           PreCheckoutQuery, WebAppInfo)
from aiohttp import web

import ai
from db import DB

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("hub")

BOT_TOKEN = os.environ.get("BOT_TOKEN", "")
ADMIN_ID = int(os.environ.get("ADMIN_ID", "0") or 0)
PUBLIC_URL = os.environ.get("PUBLIC_URL", "").rstrip("/")
PORT = int(os.environ.get("PORT", "8080"))
AI_DAY_LIMIT = int(os.environ.get("AI_DAY_LIMIT", "40"))
AI_GOLD_LIMIT = int(os.environ.get("AI_GOLD_LIMIT", "300"))
GOLD_PRICE = int(os.environ.get("GOLD_PRICE", "49"))  # звёзд Telegram
GOLD_ITEMS = {"fr_diamond", "fr_crown", "fr_aurora", "fr_phoenix", "fx_firework", "fx_gold", "fx_lovebomb"}
GOLD_DAYS = int(os.environ.get("GOLD_DAYS", "30"))
WEBAPP_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "webapp")

db = DB()
router = Router()
esc = html.escape


def now() -> int:
    return int(time.time())


def fmt_time(ts: int) -> str:
    return time.strftime("%d.%m %H:%M", time.localtime(ts))


# ====================================================================== главное меню

def app_url() -> str:
    return f"{PUBLIC_URL}/app/?api={PUBLIC_URL}/api" if PUBLIC_URL else ""


def main_menu() -> InlineKeyboardMarkup:
    rows = []
    if PUBLIC_URL:
        rows.append([InlineKeyboardButton(text="✨ Открыть StarTegs", web_app=WebAppInfo(url=app_url()))])
    rows += [
        [InlineKeyboardButton(text="🗑 Удалённые", callback_data="m:del"), InlineKeyboardButton(text="🤖 Звёздочка", callback_data="m:ai")],
        [InlineKeyboardButton(text="🛍 Маркет", callback_data="m:market"), InlineKeyboardButton(text="📊 Аналитика", callback_data="m:stats")],
        [InlineKeyboardButton(text="⏰ Напоминания", callback_data="m:rem"), InlineKeyboardButton(text="📝 Заметки", callback_data="m:notes")],
        [InlineKeyboardButton(text=f"👑 StarTegs Gold — {GOLD_PRICE} ⭐", callback_data="m:gold")],
        [InlineKeyboardButton(text="⚙️ Настройки", callback_data="m:set"), InlineKeyboardButton(text="❓ Помощь", callback_data="m:help")],
    ]
    return InlineKeyboardMarkup(inline_keyboard=rows)


WELCOME = ("⭐ <b>StarTegs Hub</b> — всё в одном боте\n\n"
           "🗑 <b>Удалённые и правки</b> — увидишь, что стёрли или изменили в твоих личных чатах\n"
           "🤖 <b>Звёздочка</b> — нейросеть: просто напиши вопрос\n"
           "🛍 <b>Маркет</b> — объявления «куплю/продам»\n"
           "📊 <b>Аналитика</b> — рост твоих каналов и групп\n"
           "⏰ <b>Напоминания</b> и 📝 <b>заметки</b>\n\n"
           "А в приложении StarTegs бот включает стиль для друзей и облачный архив.")

HELP = ("<b>Как подключить «Удалённые»</b>\n"
        "Настройки Telegram → Telegram для бизнеса → Чат-боты → добавь этого бота (нужен Telegram Premium). "
        "После этого бот будет присылать удалённые и изменённые сообщения из твоих личных чатов.\n\n"
        "<b>Команды</b>\n"
        "/ai вопрос — спросить Звёздочку (или просто напиши текст)\n/new — новый диалог с ИИ\n"
        "/tr en текст — перевод\n/sell — разместить объявление, /market — смотреть, /my — мои\n"
        "/stats — аналитика каналов (добавь бота админом в канал)\n"
        "/remind 30m купить хлеб · /remind 18:30 позвонить\n/note текст · /notes\n"
        "/deleted — последние удалённые · /find слово — поиск по архиву")


@router.message(CommandStart())
async def cmd_start(m: Message, command: CommandObject):
    db.touch_user(m.from_user)
    arg = command.args or ""
    if arg.startswith("ad_"):
        try:
            await show_ad(m.chat.id, int(arg[3:]), m.from_user.id)
            return
        except ValueError:
            pass
    await m.answer(WELCOME, reply_markup=main_menu())


@router.message(Command("help"))
async def cmd_help(m: Message):
    await m.answer(HELP, reply_markup=main_menu())


@router.callback_query(F.data == "m:help")
async def cb_help(c: CallbackQuery):
    await c.message.answer(HELP)
    await c.answer()


# ====================================================================== удалённые и правки (Telegram Business)

def media_of(m: Message):
    if m.photo:
        return "photo", m.photo[-1].file_id
    for kind in ("video", "voice", "video_note", "animation", "audio", "document", "sticker"):
        obj = getattr(m, kind, None)
        if obj:
            return kind, obj.file_id
    return "", ""


MEDIA_NAMES = {"photo": "🖼 Фото", "video": "🎬 Видео", "voice": "🎤 Голосовое", "video_note": "⭕ Кружок",
               "animation": "🎞 GIF", "audio": "🎵 Аудио", "document": "📎 Файл", "sticker": "🌟 Стикер"}


async def owner_of(bot: Bot, conn_id: str):
    r = db.one("SELECT owner FROM biz WHERE conn_id=?", (conn_id,))
    if r:
        return r["owner"]
    try:
        bc = await bot.get_business_connection(conn_id)
        db.x("INSERT OR REPLACE INTO biz(conn_id, owner, enabled, ts) VALUES(?,?,?,?)", (conn_id, bc.user.id, int(bc.is_enabled), now()))
        return bc.user.id
    except Exception:
        log.exception("business connection")
        return None


@router.business_connection()
async def on_biz_conn(bc: BusinessConnection, bot: Bot):
    db.x("INSERT OR REPLACE INTO biz(conn_id, owner, enabled, ts) VALUES(?,?,?,?)", (bc.id, bc.user.id, int(bc.is_enabled), now()))
    db.touch_user(bc.user)
    try:
        await bot.send_message(bc.user.id, "✅ <b>Бот подключён к личным чатам.</b>\nТеперь я пришлю, если кто-то удалит или изменит сообщение."
                               if bc.is_enabled else "⏸ Бот отключён от личных чатов.")
    except Exception:
        pass


@router.business_message()
async def on_biz_msg(m: Message, bot: Bot):
    owner = await owner_of(bot, m.business_connection_id)
    if not owner:
        return
    kind, fid = media_of(m)
    sender = m.from_user
    chat_name = " ".join(x for x in [m.chat.first_name or "", m.chat.last_name or ""] if x) or (m.chat.title or "")
    db.x("INSERT OR REPLACE INTO msgs(owner, chat, mid, sender, sender_name, chat_name, text, media_type, file_id, date, out) "
         "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
         (owner, m.chat.id, m.message_id, sender.id if sender else 0, (sender.full_name if sender else ""), chat_name,
          m.text or m.caption or "", kind, fid, int(m.date.timestamp()), int(bool(sender and sender.id == owner))))


@router.edited_business_message()
async def on_biz_edit(m: Message, bot: Bot):
    owner = await owner_of(bot, m.business_connection_id)
    if not owner:
        return
    new = m.text or m.caption or ""
    old_row = db.one("SELECT text, out, sender_name FROM msgs WHERE owner=? AND chat=? AND mid=?", (owner, m.chat.id, m.message_id))
    old = old_row["text"] if old_row else ""
    db.x("UPDATE msgs SET text=?, edited=edited+1 WHERE owner=? AND chat=? AND mid=?", (new, owner, m.chat.id, m.message_id))
    if old_row and old != new:
        db.x("INSERT INTO edits(owner, chat, mid, old, new, ts) VALUES(?,?,?,?,?,?)", (owner, m.chat.id, m.message_id, old, new, now()))
        u = db.user(owner)
        if not old_row["out"] and (not u or u["notify_edited"]):
            try:
                await bot.send_message(owner, f"✏️ <b>{esc(old_row['sender_name'] or 'Собеседник')}</b> изменил(а) сообщение\n\n"
                                              f"<b>Было:</b> {esc(old[:1500]) or '—'}\n<b>Стало:</b> {esc(new[:1500]) or '—'}")
            except Exception:
                pass


@router.deleted_business_messages()
async def on_biz_del(ev: BusinessMessagesDeleted, bot: Bot):
    owner = await owner_of(bot, ev.business_connection_id)
    if not owner:
        return
    rows = []
    for mid in ev.message_ids:
        r = db.one("SELECT * FROM msgs WHERE owner=? AND chat=? AND mid=?", (owner, ev.chat.id, mid))
        db.x("UPDATE msgs SET deleted=1, deleted_at=? WHERE owner=? AND chat=? AND mid=?", (now(), owner, ev.chat.id, mid))
        if r and not r["out"]:
            rows.append(r)
    u = db.user(owner)
    if not rows or (u and not u["notify_deleted"]):
        return
    who = esc(rows[0]["sender_name"] or "Собеседник")
    if len(rows) >= 8:
        await bot.send_message(owner, f"🗑 <b>{who}</b> удалил(а) {len(rows)} сообщений. Копия — в /deleted и в мини-приложении.")
        return
    for r in rows:
        head = f"🗑 <b>{who}</b> удалил(а) сообщение · {fmt_time(r['date'])}"
        try:
            if r["file_id"] and r["media_type"]:
                cap = (head + ("\n\n" + esc(r["text"][:900]) if r["text"] else ""))
                send = {"photo": bot.send_photo, "video": bot.send_video, "voice": bot.send_voice, "animation": bot.send_animation,
                        "audio": bot.send_audio, "document": bot.send_document}.get(r["media_type"])
                if send:
                    await send(owner, r["file_id"], caption=cap)
                else:
                    await bot.send_message(owner, head + f"\n\n{MEDIA_NAMES.get(r['media_type'], '📎')}")
                    if r["media_type"] == "video_note":
                        await bot.send_video_note(owner, r["file_id"])
                    elif r["media_type"] == "sticker":
                        await bot.send_sticker(owner, r["file_id"])
            else:
                await bot.send_message(owner, head + "\n\n" + (esc(r["text"][:3500]) or "—"))
        except Exception:
            log.exception("deleted notify")


def deleted_list(owner: int, limit: int = 20, q: str = ""):
    if q:
        return db.q("SELECT * FROM msgs WHERE owner=? AND deleted=1 AND (text LIKE ? OR sender_name LIKE ?) ORDER BY deleted_at DESC LIMIT ?",
                    (owner, f"%{q}%", f"%{q}%", limit))
    return db.q("SELECT * FROM msgs WHERE owner=? AND deleted=1 ORDER BY deleted_at DESC LIMIT ?", (owner, limit))


@router.message(Command("deleted"))
async def cmd_deleted(m: Message):
    await send_deleted(m.chat.id, m.from_user.id, m.bot)


@router.callback_query(F.data == "m:del")
async def cb_deleted(c: CallbackQuery):
    await send_deleted(c.message.chat.id, c.from_user.id, c.bot)
    await c.answer()


async def send_deleted(chat_id: int, uid: int, bot: Bot):
    rows = deleted_list(uid, 15)
    if not rows:
        connected = db.one("SELECT 1 FROM biz WHERE owner=? AND enabled=1", (uid,))
        await bot.send_message(chat_id, "🗑 Пока ничего не удалено." if connected else "🗑 Удалённых пока нет.\n\n" + HELP.split("\n\n")[0])
        return
    lines = [f"• <b>{esc(r['sender_name'] or r['chat_name'])}</b> · {fmt_time(r['deleted_at'])}\n  "
             f"{esc((MEDIA_NAMES.get(r['media_type'], '') + ' ' + (r['text'] or '')).strip()[:200]) or '—'}" for r in rows]
    await bot.send_message(chat_id, "🗑 <b>Последние удалённые</b>\n\n" + "\n".join(lines) + "\n\n🔎 Поиск: /find слово")


@router.message(Command("find"))
async def cmd_find(m: Message, command: CommandObject):
    q = (command.args or "").strip()
    if not q:
        await m.answer("Напиши: /find слово")
        return
    rows = db.q("SELECT * FROM msgs WHERE owner=? AND text LIKE ? ORDER BY date DESC LIMIT 15", (m.from_user.id, f"%{q}%"))
    if not rows:
        await m.answer("Ничего не нашёл 🙈")
        return
    await m.answer("🔎 <b>Нашёл</b>\n\n" + "\n".join(
        f"{'🗑 ' if r['deleted'] else ''}<b>{esc(r['sender_name'] or r['chat_name'])}</b> · {fmt_time(r['date'])}\n  {esc((r['text'] or '')[:200])}" for r in rows))


# ====================================================================== нейросеть «Звёздочка»

def ai_allowed(uid: int) -> bool:
    if uid == ADMIN_ID:
        return True
    day = time.strftime("%Y-%m-%d")
    u = db.user(uid)
    used = u["ai_used"] if u and u["ai_used_day"] == day else 0
    if used >= (AI_GOLD_LIMIT if db.gold(uid) else AI_DAY_LIMIT):
        return False
    db.x("UPDATE users SET ai_used_day=?, ai_used=? WHERE id=?", (day, used + 1, uid))
    return True


def history(uid: int, limit: int = 12) -> list:
    rows = db.q("SELECT role, content FROM ai_hist WHERE uid=? ORDER BY ts DESC, rowid DESC LIMIT ?", (uid, limit))
    return [{"role": r["role"], "content": r["content"]} for r in reversed(rows)]


def remember(uid: int, role: str, content: str):
    db.x("INSERT INTO ai_hist(uid, role, content, ts) VALUES(?,?,?,?)", (uid, role, content[:6000], now()))
    db.x("DELETE FROM ai_hist WHERE uid=? AND rowid NOT IN (SELECT rowid FROM ai_hist WHERE uid=? ORDER BY ts DESC, rowid DESC LIMIT 40)", (uid, uid))


async def ai_reply(m: Message, prompt: str, keep: bool = True):
    uid = m.from_user.id
    if not ai.provider():
        await m.answer("🤖 Нейросеть ещё не подключена — админу нужно добавить ключ (бесплатный OpenRouter подойдёт).")
        return
    if not ai_allowed(uid):
        await m.answer(f"🤖 На сегодня всё: {AI_DAY_LIMIT} вопросов в день. Завтра продолжим ✨")
        return
    msgs = (history(uid) if keep else []) + [{"role": "user", "content": prompt[:8000]}]
    sent = await m.answer("✨ Думаю…")
    text, last_edit = "", 0.0
    try:
        async for piece in ai.stream(msgs):
            text += piece
            if time.time() - last_edit > 1.3 and text.strip():
                last_edit = time.time()
                try:
                    await sent.edit_text(esc(text[:4000]) + " ▍")
                except Exception:
                    pass
        text = text.strip() or "…"
        await sent.edit_text(esc(text[:4000]))
        if keep:
            remember(uid, "user", prompt)
            remember(uid, "assistant", text)
    except ai.AIError as e:
        await sent.edit_text("⚠️ " + esc(str(e)))
    except Exception:
        log.exception("ai")
        await sent.edit_text("⚠️ Нейросеть временно недоступна, попробуй ещё раз")


@router.message(Command("ai"))
async def cmd_ai(m: Message, command: CommandObject):
    if not command.args:
        await m.answer("🤖 Напиши вопрос после /ai или просто отправь текст.")
        return
    await ai_reply(m, command.args)


@router.message(Command("new"))
async def cmd_new(m: Message):
    db.x("DELETE FROM ai_hist WHERE uid=?", (m.from_user.id,))
    await m.answer("✨ Новый диалог. О чём поговорим?")


@router.message(Command("tr"))
async def cmd_tr(m: Message, command: CommandObject):
    args = (command.args or "").split(maxsplit=1)
    if len(args) < 2:
        await m.answer("Формат: /tr en Привет, как дела?")
        return
    await ai_reply(m, f"Переведи на язык с кодом «{args[0]}». Ответь только переводом:\n\n{args[1]}", keep=False)


@router.callback_query(F.data == "m:ai")
async def cb_ai(c: CallbackQuery):
    await c.message.answer(f"🤖 <b>Звёздочка</b> · {ai.title()}\n\nПросто напиши вопрос — отвечу. /new — новый диалог, /tr en текст — перевод.\n"
                           "Перешли мне длинное сообщение — сделаю краткий пересказ.")
    await c.answer()


# ====================================================================== маркет

CATS = ["📱 Техника", "👕 Одежда", "🎮 Игры", "📚 Учёба", "🎨 Творчество", "🛠 Услуги", "🐾 Животные", "📦 Другое"]


class SellForm(StatesGroup):
    kind = State()
    cat = State()
    title = State()
    price = State()
    photo = State()


def ad_text(a) -> str:
    return (f"{'🟢 Продам' if a['kind'] == 'sell' else '🔵 Куплю'} · {esc(a['cat'])}\n<b>{esc(a['title'])}</b>\n"
            f"💰 {esc(a['price'])}\n{esc(a['descr'] or '')}\n\n👁 {a['views']} · {fmt_time(a['ts'])}").strip()


async def show_ad(chat_id: int, ad_id: int, viewer: int, bot: Bot = None):
    bot = bot or BOT
    a = db.one("SELECT * FROM ads WHERE id=? AND active=1", (ad_id,))
    if not a:
        await bot.send_message(chat_id, "Объявление уже снято 🙈")
        return
    if viewer != a["uid"]:
        db.x("UPDATE ads SET views=views+1 WHERE id=?", (ad_id,))
    seller = db.user(a["uid"])
    kb = []
    if viewer != a["uid"]:
        link = f"https://t.me/{seller['username']}" if seller and seller["username"] else f"tg://user?id={a['uid']}"
        kb.append([InlineKeyboardButton(text="💬 Написать", url=link), InlineKeyboardButton(text="⭐ В избранное", callback_data=f"fav:{ad_id}")])
    else:
        kb.append([InlineKeyboardButton(text="🗑 Снять объявление", callback_data=f"adel:{ad_id}")])
    markup = InlineKeyboardMarkup(inline_keyboard=kb)
    if a["photo"]:
        await bot.send_photo(chat_id, a["photo"], caption=ad_text(a)[:1000], reply_markup=markup)
    else:
        await bot.send_message(chat_id, ad_text(a), reply_markup=markup)


@router.message(Command("sell"))
@router.callback_query(F.data == "mk:new")
async def sell_start(ev, state: FSMContext):
    m = ev.message if isinstance(ev, CallbackQuery) else ev
    kb = InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text="🟢 Продаю", callback_data="sk:sell"),
                                                InlineKeyboardButton(text="🔵 Куплю", callback_data="sk:buy")]])
    await state.set_state(SellForm.kind)
    await m.answer("🛍 <b>Новое объявление</b>\nЧто хочешь сделать?", reply_markup=kb)
    if isinstance(ev, CallbackQuery):
        await ev.answer()


@router.callback_query(SellForm.kind, F.data.startswith("sk:"))
async def sell_kind(c: CallbackQuery, state: FSMContext):
    await state.update_data(kind=c.data[3:])
    rows = [[InlineKeyboardButton(text=CATS[i], callback_data=f"sc:{i}"), InlineKeyboardButton(text=CATS[i + 1], callback_data=f"sc:{i + 1}")]
            for i in range(0, len(CATS), 2)]
    await state.set_state(SellForm.cat)
    await c.message.edit_text("Выбери категорию:", reply_markup=InlineKeyboardMarkup(inline_keyboard=rows))
    await c.answer()


@router.callback_query(SellForm.cat, F.data.startswith("sc:"))
async def sell_cat(c: CallbackQuery, state: FSMContext):
    await state.update_data(cat=CATS[int(c.data[3:])])
    await state.set_state(SellForm.title)
    await c.message.edit_text("Напиши название и описание. Первая строка — заголовок.\nНапример:\n<i>AirPods Pro 2\nКак новые, с коробкой</i>")
    await c.answer()


@router.message(SellForm.title, F.text)
async def sell_title(m: Message, state: FSMContext):
    lines = m.text.strip().split("\n", 1)
    await state.update_data(title=lines[0][:80], descr=(lines[1] if len(lines) > 1 else "")[:600])
    await state.set_state(SellForm.price)
    await m.answer("💰 Цена? Например: 5000 ₽, договорная, обмен")


@router.message(SellForm.price, F.text)
async def sell_price(m: Message, state: FSMContext):
    await state.update_data(price=m.text.strip()[:40])
    await state.set_state(SellForm.photo)
    await m.answer("📸 Пришли фото (или /skip — без фото)")


@router.message(SellForm.photo, F.photo | (F.text == "/skip"))
async def sell_photo(m: Message, state: FSMContext):
    d = await state.get_data()
    photo = m.photo[-1].file_id if m.photo else ""
    cur = db.x("INSERT INTO ads(uid, kind, cat, title, descr, price, photo, ts) VALUES(?,?,?,?,?,?,?,?)",
               (m.from_user.id, d.get("kind", "sell"), d.get("cat", CATS[-1]), d.get("title", ""), d.get("descr", ""), d.get("price", ""), photo, now()))
    await state.clear()
    await m.answer("✅ Объявление опубликовано! Его видят все в /market и в мини-приложении.")
    await show_ad(m.chat.id, cur.lastrowid, m.from_user.id, m.bot)


@router.message(Command("market"))
@router.callback_query(F.data.startswith("m:market"))
async def market(ev):
    m = ev.message if isinstance(ev, CallbackQuery) else ev
    rows = db.q("SELECT * FROM ads WHERE active=1 ORDER BY ts DESC LIMIT 10")
    kb = [[InlineKeyboardButton(text=f"{'🟢' if a['kind'] == 'sell' else '🔵'} {a['title'][:28]} · {a['price'][:12]}", callback_data=f"ad:{a['id']}")] for a in rows]
    kb.append([InlineKeyboardButton(text="➕ Разместить", callback_data="mk:new"), InlineKeyboardButton(text="📋 Мои", callback_data="mk:my")])
    if PUBLIC_URL:
        kb.append([InlineKeyboardButton(text="✨ Маркет в приложении", web_app=WebAppInfo(url=app_url() + "#market"))])
    await m.answer("🛍 <b>Маркет</b>" + ("" if rows else "\n\nПока пусто — будь первым!"), reply_markup=InlineKeyboardMarkup(inline_keyboard=kb))
    if isinstance(ev, CallbackQuery):
        await ev.answer()


@router.callback_query(F.data.startswith("ad:"))
async def cb_ad(c: CallbackQuery):
    await show_ad(c.message.chat.id, int(c.data[3:]), c.from_user.id, c.bot)
    await c.answer()


@router.message(Command("my"))
@router.callback_query(F.data == "mk:my")
async def my_ads(ev):
    m = ev.message if isinstance(ev, CallbackQuery) else ev
    uid = ev.from_user.id
    rows = db.q("SELECT * FROM ads WHERE uid=? AND active=1 ORDER BY ts DESC", (uid,))
    if not rows:
        await m.answer("📋 У тебя нет объявлений. /sell — разместить")
    else:
        kb = [[InlineKeyboardButton(text=f"{a['title'][:30]} · 👁 {a['views']}", callback_data=f"ad:{a['id']}")] for a in rows]
        await m.answer("📋 <b>Мои объявления</b>", reply_markup=InlineKeyboardMarkup(inline_keyboard=kb))
    if isinstance(ev, CallbackQuery):
        await ev.answer()


@router.callback_query(F.data.startswith("adel:"))
async def cb_adel(c: CallbackQuery):
    db.x("UPDATE ads SET active=0 WHERE id=? AND uid=?", (int(c.data[5:]), c.from_user.id))
    await c.answer("Снято ✅", show_alert=False)


@router.callback_query(F.data.startswith("fav:"))
async def cb_fav(c: CallbackQuery):
    db.x("INSERT OR IGNORE INTO favs(uid, ad) VALUES(?,?)", (c.from_user.id, int(c.data[4:])))
    await c.answer("⭐ В избранном — смотри в мини-приложении")


# ====================================================================== аналитика каналов и групп

@router.my_chat_member()
async def on_my_member(ev: ChatMemberUpdated, bot: Bot):
    if ev.chat.type == ChatType.PRIVATE:
        return
    st = ev.new_chat_member.status
    if st in (ChatMemberStatus.ADMINISTRATOR, ChatMemberStatus.MEMBER):
        owner = ev.from_user.id
        db.x("INSERT INTO channels(id, owner, title, username, kind, added, active) VALUES(?,?,?,?,?,?,1) "
             "ON CONFLICT(id) DO UPDATE SET title=excluded.title, username=excluded.username, active=1",
             (ev.chat.id, owner, ev.chat.title or "", ev.chat.username or "", ev.chat.type, now()))
        await snapshot(bot, ev.chat.id)
        try:
            await bot.send_message(owner, f"📊 <b>{esc(ev.chat.title or 'Чат')}</b> подключён к аналитике. Статистика — /stats")
        except Exception:
            pass
    elif st in (ChatMemberStatus.LEFT, ChatMemberStatus.KICKED):
        db.x("UPDATE channels SET active=0 WHERE id=?", (ev.chat.id,))


@router.chat_member()
async def on_member(ev: ChatMemberUpdated):
    was = ev.old_chat_member.status in (ChatMemberStatus.MEMBER, ChatMemberStatus.ADMINISTRATOR, ChatMemberStatus.CREATOR, ChatMemberStatus.RESTRICTED)
    is_ = ev.new_chat_member.status in (ChatMemberStatus.MEMBER, ChatMemberStatus.ADMINISTRATOR, ChatMemberStatus.CREATOR, ChatMemberStatus.RESTRICTED)
    if was != is_:
        db.x("INSERT INTO joins(chat, ts, delta) VALUES(?,?,?)", (ev.chat.id, now(), 1 if is_ else -1))


@router.channel_post()
async def on_post(m: Message):
    kind, _ = media_of(m)
    db.x("INSERT OR IGNORE INTO posts(chat, mid, ts, kind) VALUES(?,?,?,?)", (m.chat.id, m.message_id, now(), kind or "text"))


async def snapshot(bot: Bot, chat_id: int):
    try:
        n = await bot.get_chat_member_count(chat_id)
        db.x("INSERT INTO snaps(chat, ts, members) VALUES(?,?,?)", (chat_id, now(), n))
    except Exception:
        log.warning("snapshot %s failed", chat_id)


def channel_stats(chat_id: int) -> dict:
    t = now()
    snaps = db.q("SELECT ts, members FROM snaps WHERE chat=? AND ts>? ORDER BY ts", (chat_id, t - 30 * 86400))
    cur = snaps[-1]["members"] if snaps else 0

    def at(sec):
        old = [s for s in snaps if s["ts"] <= t - sec]
        return cur - old[-1]["members"] if old else None

    joins = db.q("SELECT delta FROM joins WHERE chat=? AND ts>?", (chat_id, t - 7 * 86400))
    hours = [0] * 24
    for p in db.q("SELECT ts FROM posts WHERE chat=? AND ts>?", (chat_id, t - 30 * 86400)):
        hours[time.localtime(p["ts"]).tm_hour] += 1
    days = {}
    for s in snaps:
        days[time.strftime("%d.%m", time.localtime(s["ts"]))] = s["members"]
    return {"members": cur, "d1": at(86400), "d7": at(7 * 86400), "d30": at(30 * 86400),
            "in7": sum(1 for j in joins if j["delta"] > 0), "out7": sum(1 for j in joins if j["delta"] < 0),
            "posts7": db.one("SELECT COUNT(*) n FROM posts WHERE chat=? AND ts>?", (chat_id, t - 7 * 86400))["n"],
            "best_hour": max(range(24), key=lambda h: hours[h]) if any(hours) else None,
            "chart": [{"d": k, "v": v} for k, v in list(days.items())[-30:]]}


def fmt_delta(v):
    return "—" if v is None else (f"+{v}" if v > 0 else str(v))


@router.message(Command("stats"))
@router.callback_query(F.data == "m:stats")
async def stats(ev):
    m = ev.message if isinstance(ev, CallbackQuery) else ev
    rows = db.q("SELECT * FROM channels WHERE owner=? AND active=1", (ev.from_user.id,))
    if not rows:
        await m.answer("📊 Добавь бота администратором в свой канал или группу — и здесь появится статистика: подписчики, рост, отток, лучшее время для постов.")
    for c in rows:
        s = channel_stats(c["id"])
        txt = (f"📊 <b>{esc(c['title'])}</b>\n\n👥 Подписчиков: <b>{s['members']}</b>\n"
               f"📈 За сутки: {fmt_delta(s['d1'])} · за неделю: {fmt_delta(s['d7'])} · за месяц: {fmt_delta(s['d30'])}\n"
               f"➕ Пришли за 7 дней: {s['in7']} · ➖ ушли: {s['out7']}\n📝 Постов за неделю: {s['posts7']}"
               + (f"\n⏰ Чаще всего постишь в {s['best_hour']:02d}:00" if s["best_hour"] is not None else ""))
        await m.answer(txt)
    if isinstance(ev, CallbackQuery):
        await ev.answer()


# ====================================================================== напоминания и заметки

def parse_when(s: str):
    s = s.strip().lower()
    m = re.fullmatch(r"(\d+)\s*(m|м|мин|min|h|ч|час|d|д|дн)", s)
    if m:
        n, u = int(m.group(1)), m.group(2)
        mult = 60 if u in ("m", "м", "мин", "min") else 3600 if u in ("h", "ч", "час") else 86400
        return now() + n * mult
    m = re.fullmatch(r"(\d{1,2})[:.](\d{2})", s)
    if m:
        lt = time.localtime()
        at = int(time.mktime((lt.tm_year, lt.tm_mon, lt.tm_mday, int(m.group(1)), int(m.group(2)), 0, 0, 0, -1)))
        return at if at > now() else at + 86400
    return None


@router.message(Command("remind"))
async def cmd_remind(m: Message, command: CommandObject):
    parts = (command.args or "").split(maxsplit=1)
    at = parse_when(parts[0]) if parts else None
    if not at:
        await m.answer("⏰ Формат: /remind 30m купить хлеб · /remind 2h · /remind 18:30 позвонить маме")
        return
    text = parts[1] if len(parts) > 1 else "Напоминание"
    db.x("INSERT INTO reminders(uid, at, text) VALUES(?,?,?)", (m.from_user.id, at, text[:500]))
    await m.answer(f"⏰ Напомню {time.strftime('%d.%m в %H:%M', time.localtime(at))}: {esc(text)}")


@router.callback_query(F.data == "m:rem")
async def cb_rem(c: CallbackQuery):
    rows = db.q("SELECT * FROM reminders WHERE uid=? AND done=0 ORDER BY at", (c.from_user.id,))
    txt = "⏰ <b>Напоминания</b>\n\n" + ("\n".join(f"• {fmt_time(r['at'])} — {esc(r['text'])}" for r in rows) or "Пока нет.") + \
          "\n\nДобавить: /remind 30m текст или /remind 18:30 текст"
    await c.message.answer(txt)
    await c.answer()


@router.message(Command("note"))
async def cmd_note(m: Message, command: CommandObject):
    if not command.args:
        await m.answer("📝 Формат: /note текст заметки")
        return
    db.x("INSERT INTO notes(uid, text, ts) VALUES(?,?,?)", (m.from_user.id, command.args[:2000], now()))
    await m.answer("📝 Сохранил. Все заметки — /notes")


@router.message(Command("notes"))
@router.callback_query(F.data == "m:notes")
async def notes(ev):
    m = ev.message if isinstance(ev, CallbackQuery) else ev
    rows = db.q("SELECT * FROM notes WHERE uid=? ORDER BY ts DESC LIMIT 30", (ev.from_user.id,))
    await m.answer("📝 <b>Заметки</b>\n\n" + ("\n".join(f"• {esc(r['text'][:200])}" for r in rows) or "Пусто. /note текст — добавить"))
    if isinstance(ev, CallbackQuery):
        await ev.answer()


async def reminder_loop(bot: Bot):
    while True:
        try:
            for r in db.q("SELECT * FROM reminders WHERE done=0 AND at<=? LIMIT 50", (now(),)):
                db.x("UPDATE reminders SET done=1 WHERE id=?", (r["id"],))
                try:
                    await bot.send_message(r["uid"], "⏰ <b>Напоминание</b>\n\n" + esc(r["text"]))
                except Exception:
                    pass
        except Exception:
            log.exception("reminders")
        await asyncio.sleep(20)


async def snapshot_loop(bot: Bot):
    while True:
        try:
            for c in db.q("SELECT id FROM channels WHERE active=1"):
                await snapshot(bot, c["id"])
                await asyncio.sleep(0.3)
        except Exception:
            log.exception("snapshots")
        await asyncio.sleep(3 * 3600)


# ====================================================================== настройки и админка

def settings_kb(uid: int) -> InlineKeyboardMarkup:
    u = db.user(uid)
    d, e = (u["notify_deleted"], u["notify_edited"]) if u else (1, 1)
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text=("✅" if d else "⬜") + " Сообщать об удалённых", callback_data="st:d")],
        [InlineKeyboardButton(text=("✅" if e else "⬜") + " Сообщать об изменённых", callback_data="st:e")],
        [InlineKeyboardButton(text="🧹 Очистить мой архив", callback_data="st:clear")]])


@router.callback_query(F.data == "m:set")
async def cb_settings(c: CallbackQuery):
    db.touch_user(c.from_user)
    await c.message.answer("⚙️ <b>Настройки</b>", reply_markup=settings_kb(c.from_user.id))
    await c.answer()


@router.callback_query(F.data.startswith("st:"))
async def cb_set(c: CallbackQuery):
    k = c.data[3:]
    if k == "clear":
        db.x("DELETE FROM msgs WHERE owner=?", (c.from_user.id,))
        db.x("DELETE FROM edits WHERE owner=?", (c.from_user.id,))
        await c.answer("Архив очищен 🧹", show_alert=True)
        return
    col = "notify_deleted" if k == "d" else "notify_edited"
    db.x(f"UPDATE users SET {col}=1-{col} WHERE id=?", (c.from_user.id,))
    await c.message.edit_reply_markup(reply_markup=settings_kb(c.from_user.id))
    await c.answer()


@router.message(Command("admin"), F.from_user.id == ADMIN_ID)
async def cmd_admin(m: Message):
    n = lambda sql: db.one(sql)["n"]
    await m.answer("👑 <b>Админка StarTegs Hub</b>\n\n"
                   f"👥 Пользователей: {n('SELECT COUNT(*) n FROM users')} · за сутки: {n(f'SELECT COUNT(*) n FROM users WHERE last_seen>{now() - 86400}')}\n"
                   f"🔗 Business-подключений: {n('SELECT COUNT(*) n FROM biz WHERE enabled=1')}\n"
                   f"🗑 Сохранено удалённых: {n('SELECT COUNT(*) n FROM msgs WHERE deleted=1')}\n"
                   f"🛍 Объявлений: {n('SELECT COUNT(*) n FROM ads WHERE active=1')}\n"
                   f"📊 Каналов в аналитике: {n('SELECT COUNT(*) n FROM channels WHERE active=1')}\n"
                   f"👑 Gold активен у: {n(f'SELECT COUNT(*) n FROM users WHERE gold_until>{now()}')} · "
                   f"звёзд всего: {db.one('SELECT COALESCE(SUM(amount),0) n FROM payments WHERE refunded=0')['n']} ⭐\n"
                   f"🤖 Нейросеть: {ai.title()}\n\nРассылка: /broadcast текст · Возврат: /refund ID ПЛАТЁЖ")


@router.message(Command("broadcast"), F.from_user.id == ADMIN_ID)
async def cmd_broadcast(m: Message, command: CommandObject):
    if not command.args:
        await m.answer("Формат: /broadcast текст")
        return
    db.kv_set("broadcast", command.args)
    cnt = db.one("SELECT COUNT(*) n FROM users WHERE banned=0")["n"]
    await m.answer(f"Отправить {cnt} пользователям?\n\n{esc(command.args)}", reply_markup=InlineKeyboardMarkup(inline_keyboard=[[
        InlineKeyboardButton(text="✅ Отправить", callback_data="bc:go"), InlineKeyboardButton(text="Отмена", callback_data="bc:no")]]))


@router.callback_query(F.data.startswith("bc:"), F.from_user.id == ADMIN_ID)
async def cb_broadcast(c: CallbackQuery, bot: Bot):
    await c.answer()
    if c.data == "bc:no":
        await c.message.edit_text("Рассылка отменена")
        return
    text = db.kv_get("broadcast", "")
    await c.message.edit_text("📨 Рассылаю…")
    ok = bad = 0
    for u in db.q("SELECT id FROM users WHERE banned=0"):
        try:
            await bot.send_message(u["id"], text)
            ok += 1
        except Exception:
            bad += 1
        await asyncio.sleep(0.05)
    await c.message.answer(f"Готово: доставлено {ok}, не доставлено {bad}")


# ====================================================================== StarTegs Gold (звёзды Telegram)

GOLD_PERKS = ("👑 <b>StarTegs Gold</b> — выделяйся среди всех\n\n"
              "✦ Золотая отметка у имени — её видят друзья со StarTegs\n"
              "🌈 Переливающееся имя для друзей\n"
              "💎 Эксклюзивные анимированные рамки: Алмаз, Корона, Аврора, Феникс\n"
              "🎆 Эффекты отправки: Фейерверк, Золотой дождь, Сердечный взрыв\n"
              "✨ Эксклюзивные стили интерфейса: Gold Luxe и Diamond\n"
              "🤖 Звёздочка почти без лимита\n\n")


def gold_invoice() -> dict:
    return dict(title="StarTegs Gold 👑", description=f"Золотая отметка, переливающееся имя, эксклюзивные рамки, эффекты и стили на {GOLD_DAYS} дней",
                payload=f"gold:{GOLD_DAYS}", currency="XTR", prices=[LabeledPrice(label=f"Gold на {GOLD_DAYS} дней", amount=GOLD_PRICE)])


@router.message(Command("gold"))
@router.callback_query(F.data == "m:gold")
async def gold_offer(ev, bot: Bot):
    m = ev.message if isinstance(ev, CallbackQuery) else ev
    db.touch_user(ev.from_user)
    until = db.gold_until(ev.from_user.id)
    status = f"✅ Gold активен до {time.strftime('%d.%m.%Y', time.localtime(until))}\n\n" if until > now() else ""
    await m.answer(GOLD_PERKS + status + f"Цена: <b>{GOLD_PRICE} ⭐ за {GOLD_DAYS} дней</b>. Продление добавляет дни.")
    await bot.send_invoice(m.chat.id, **gold_invoice())
    if isinstance(ev, CallbackQuery):
        await ev.answer()


@router.pre_checkout_query()
async def pre_checkout(q: PreCheckoutQuery):
    await q.answer(ok=q.invoice_payload.startswith("gold:") and q.currency == "XTR", error_message="Этот платёж не подходит")


@router.message(F.successful_payment)
async def paid(m: Message):
    p = m.successful_payment
    db.touch_user(m.from_user)
    if not p.invoice_payload.startswith("gold:"):
        return
    days = int(p.invoice_payload.split(":")[1] or GOLD_DAYS)
    if db.one("SELECT 1 FROM payments WHERE charge=?", (p.telegram_payment_charge_id,)):
        return
    db.x("INSERT INTO payments(charge, uid, amount, days, ts) VALUES(?,?,?,?,?)", (p.telegram_payment_charge_id, m.from_user.id, p.total_amount, days, now()))
    until = db.add_gold(m.from_user.id, days)
    await m.answer(f"👑 <b>Gold активирован до {time.strftime('%d.%m.%Y', time.localtime(until))}!</b>\n\n"
                   "Открой StarTegs → ⭐ StarTegs → 👑 Gold, чтобы включить золотые фишки. Спасибо, что поддерживаешь StarTegs 💛")
    if ADMIN_ID:
        try:
            await m.bot.send_message(ADMIN_ID, f"💰 Gold: {esc(m.from_user.full_name)} ({m.from_user.id}) · {p.total_amount} ⭐ · {p.telegram_payment_charge_id}")
        except Exception:
            pass


@router.message(Command("refund"), F.from_user.id == ADMIN_ID)
async def cmd_refund(m: Message, command: CommandObject, bot: Bot):
    args = (command.args or "").split()
    if len(args) != 2:
        await m.answer("Формат: /refund ID_пользователя ID_платежа (ID платежа приходит в уведомлении о покупке)")
        return
    try:
        uid = int(args[0])
        await bot.refund_star_payment(user_id=uid, telegram_payment_charge_id=args[1])
        pay = db.one("SELECT days FROM payments WHERE charge=?", (args[1],))
        db.x("UPDATE payments SET refunded=1 WHERE charge=?", (args[1],))
        if pay:
            db.x("UPDATE users SET gold_until=MAX(?, gold_until-?) WHERE id=?", (now(), pay["days"] * 86400, uid))
        await m.answer("✅ Звёзды возвращены")
    except Exception as e:
        await m.answer(f"Не получилось: {esc(str(e))}")


# ---------------------------------------------------------------------- любой текст в личке → Звёздочка

@router.message(F.chat.type == ChatType.PRIVATE, F.text & ~F.text.startswith("/"))
async def any_text(m: Message, state: FSMContext):
    if await state.get_state():
        return
    db.touch_user(m.from_user)
    prompt = m.text
    if m.forward_origin is not None and len(prompt) > 300:
        prompt = "Сделай краткий пересказ этого сообщения (3–5 пунктов):\n\n" + prompt
    await ai_reply(m, prompt)


# ====================================================================== веб: API приложения и мини-приложения

def check_init(init: str):
    """Проверка подписи initData Telegram WebApp. Возвращает пользователя (dict) или None."""
    if not init or not BOT_TOKEN:
        return None
    try:
        pairs = dict(parse_qsl(init, keep_blank_values=True))
        got = pairs.pop("hash", "")
        data = "\n".join(f"{k}={v}" for k, v in sorted(pairs.items()))
        secret = hmac.new(b"WebAppData", BOT_TOKEN.encode(), hashlib.sha256).digest()
        calc = hmac.new(secret, data.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(calc, got):
            return None
        if now() - int(pairs.get("auth_date", "0")) > 7 * 86400:
            return None
        return json.loads(pairs.get("user", "{}")) or None
    except Exception:
        return None


CORS = {"Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type", "Cache-Control": "no-store"}


def js(d, status=200):
    return web.json_response(d, status=status, headers=CORS)


_rate = {}


def rate_ok(uid: int, key: str, per_min: int) -> bool:
    t = time.time()
    hist = [x for x in _rate.get((uid, key), []) if t - x < 60]
    if len(hist) >= per_min:
        return False
    hist.append(t)
    _rate[(uid, key)] = hist
    return True


_jobs = {}


async def _run_job(job_id: str, uid: int, prompt: str):
    j = _jobs[job_id]
    try:
        async for piece in ai.stream([{"role": "user", "content": prompt[:12000]}], max_tokens=2000):
            j["text"] += piece
    except ai.AIError as e:
        j["err"] = str(e)
    except Exception:
        log.exception("ai job")
        j["err"] = "Нейросеть временно недоступна"
    finally:
        j["done"], j["ts"] = True, time.time()


def clean_ids(v):
    out = []
    for x in (v or [])[:300]:
        try:
            out.append(int(x))
        except (TypeError, ValueError):
            pass
    return out


def look_of(uid: int) -> dict:
    r = db.one("SELECT data FROM looks WHERE uid=?", (uid,))
    return json.loads(r["data"]) if r else {}


def save_look(uid: int, **kw):
    d = look_of(uid)
    d.update(kw)
    db.x("INSERT OR REPLACE INTO looks(uid, data, ts) VALUES(?,?,?)", (uid, json.dumps(d, ensure_ascii=False), now()))
    return d


async def api_app(request: web.Request):
    """Протокол приложения StarTegs (тот же, что у старого бота)."""
    try:
        body = await request.json()
    except Exception:
        return js({"ok": False, "err": "bad"}, 400)
    u = check_init(str(body.get("init", ""))[:4000])
    if not u:
        return js({"ok": False, "err": "auth", "msg": "Открой бота и нажми /start"}, 401)
    uid = int(u["id"])
    a = str(body.get("a", ""))
    if not rate_ok(uid, a, 200 if a == "ai_job" else 40):
        return js({"ok": False, "err": "rate", "msg": "Слишком часто, подожди минуту"}, 429)
    if db.user(uid) is None:
        db.x("INSERT OR IGNORE INTO users(id, name, username, joined, last_seen) VALUES(?,?,?,?,?)",
             (uid, u.get("first_name", ""), u.get("username", ""), now(), now()))
    look = look_of(uid)

    if a in ("ping", "state", "gold"):
        g = db.gold_until(uid)
        return js({"ok": True, "state": {"tier": "Premium" if g > now() else "Free"}, "hub": True,
                   "gold_until": g, "gold": g > now(), "price": GOLD_PRICE, "days": GOLD_DAYS})
    if a == "gold_link":
        try:
            return js({"ok": True, "link": await BOT.create_invoice_link(**gold_invoice())})
        except Exception:
            log.exception("invoice link")
            return js({"ok": False, "msg": "Не получилось создать счёт, попробуй /gold в боте"}, 502)
    if a == "cos_state":
        return js({"ok": True, "premium": True, "crystals": 0, "frame": look.get("frame", ""), "effect": look.get("effect", ""),
                   "badge": look.get("badge", "")})
    if a == "cos_equip":
        frame, effect = str(body.get("frame", ""))[:32], str(body.get("effect", ""))[:32]
        if not db.gold(uid):  # рамки и эффекты Gold — только с Gold
            frame = "" if frame in GOLD_ITEMS else frame
            effect = "" if effect in GOLD_ITEMS else effect
        save_look(uid, frame=frame, effect=effect)
        return js({"ok": True, "premium": True, **{k: look_of(uid).get(k, "") for k in ("frame", "effect", "badge")}})
    if a == "badge_set":
        save_look(uid, badge=str(body.get("badge", ""))[:32])
        return js({"ok": True, "premium": True, **{k: look_of(uid).get(k, "") for k in ("frame", "effect", "badge")}})
    if a == "prof_set":
        save_look(uid, mood=str(body.get("mood", ""))[:40], song=str(body.get("song", ""))[:80], emojis=str(body.get("emojis", ""))[:24],
                  status=str(body.get("status", ""))[:24], status_until=int(body.get("status_until", 0) or 0))
        return js({"ok": True})
    if a == "look_set":
        try:
            save_look(uid, c=int(body.get("name_color", 0)) & 0xFFFFFFFF, s=max(-1, min(11, int(body.get("name_style", -1)))),
                      cv=max(-1, min(9, int(body.get("cover", -1)))), l=max(0, min(99, int(body.get("lvl", 0)))), bday=str(body.get("bday", ""))[:5])
        except (TypeError, ValueError):
            return js({"ok": False, "err": "bad"}, 400)
        return js({"ok": True})
    if a in ("cos_get", "prof_get", "look_get", "badge_get"):
        ids = clean_ids(body.get("ids"))
        frames, profs, looks, badges = {}, {}, {}, {}
        today = time.strftime("%m-%d")
        for i in ids:
            d = look_of(i)
            if not d and not db.gold(i):
                continue
            if d.get("frame"):
                frames[str(i)] = d["frame"]
            if d.get("badge"):
                badges[str(i)] = d["badge"]
            st = d.get("status", "") if not d.get("status_until") or d.get("status_until", 0) > now() else ""
            if any(d.get(k) for k in ("mood", "song", "emojis")) or st:
                profs[str(i)] = {"mood": d.get("mood", ""), "song": d.get("song", ""), "emojis": d.get("emojis", ""), "status": st}
            gold = db.gold(i)
            if any(k in d for k in ("c", "s", "cv", "l")) or gold:
                looks[str(i)] = {"c": d.get("c", 0), "s": d.get("s", -1), "cv": d.get("cv", -1), "l": d.get("l", 0), "b": d.get("bday") == today,
                                 "g": 1 if gold else 0}
        return js({"ok": True, "frames": frames, "profiles": profs, "look": looks, "badges": badges})
    if a == "modarch":
        items = body.get("items") if isinstance(body.get("items"), list) else []
        saved = 0
        for it in items[:200]:
            try:
                db.x("INSERT OR IGNORE INTO msgs(owner, chat, mid, sender, sender_name, chat_name, text, media_type, file_id, date, deleted, deleted_at) "
                     "VALUES(?,?,?,?,?,?,?,?,?,?,1,?)",
                     (uid, int(it.get("chat", 0)), int(it.get("mid", 0)), int(it.get("from", 0) or 0), str(it.get("name", ""))[:80],
                      str(it.get("name", ""))[:80], str(it.get("text", ""))[:4000], "file" if it.get("media") else "", "", int(it.get("ts", 0) or 0), now()))
                saved += 1
            except Exception:
                pass
        return js({"ok": True, "saved": saved})
    if a == "ai_start":
        if not ai.provider():
            return js({"ok": False, "err": "nokey", "msg": "Нейросеть на сервере не подключена"}, 503)
        prompt = str(body.get("prompt", "")).strip()
        if not prompt:
            return js({"ok": False, "err": "bad", "msg": "Пустой вопрос"}, 400)
        if not ai_allowed(uid):
            return js({"ok": False, "err": "limit", "msg": "Дневной лимит нейросети исчерпан. С 👑 Gold — почти без лимита"}, 429)
        for k in [k for k, v in _jobs.items() if v["done"] and time.time() - v["ts"] > 600]:
            _jobs.pop(k, None)
        job_id = uuid.uuid4().hex
        _jobs[job_id] = {"uid": uid, "text": "", "done": False, "err": None, "ts": time.time()}
        asyncio.get_running_loop().create_task(_run_job(job_id, uid, prompt))
        return js({"ok": True, "job": job_id})
    if a == "ai_job":
        j = _jobs.get(str(body.get("id", "")))
        if not j or j["uid"] != uid:
            return js({"ok": False, "err": "gone", "msg": "Ответ потерялся, спроси ещё раз"}, 404)
        if j["err"]:
            return js({"ok": False, "err": "ai", "msg": j["err"]}, 502)
        return js({"ok": True, "text": j["text"], "done": j["done"]})
    return js({"ok": False, "err": "bad", "msg": "Эта функция больше не нужна — в StarTegs всё работает на телефоне"}, 400)


async def api_mini(request: web.Request):
    """API мини-приложения."""
    try:
        body = await request.json()
    except Exception:
        return js({"ok": False}, 400)
    u = check_init(str(body.get("init", ""))[:4000])
    if not u:
        return js({"ok": False, "msg": "Открой мини-приложение из бота"}, 401)
    uid = int(u["id"])
    a = str(body.get("a", ""))
    if not rate_ok(uid, "mini:" + a, 60):
        return js({"ok": False, "msg": "Слишком часто"}, 429)
    db.x("INSERT INTO users(id, name, username, joined, last_seen) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET last_seen=excluded.last_seen",
         (uid, u.get("first_name", ""), u.get("username", ""), now(), now()))
    me = db.user(uid)

    if a == "home":
        n = lambda sql, *p: db.one(sql, p)["n"]
        return js({"ok": True, "name": u.get("first_name", ""), "ai": ai.title(), "connected": bool(db.one("SELECT 1 FROM biz WHERE owner=? AND enabled=1", (uid,))),
                   "deleted": n("SELECT COUNT(*) n FROM msgs WHERE owner=? AND deleted=1", uid),
                   "deleted_today": n("SELECT COUNT(*) n FROM msgs WHERE owner=? AND deleted=1 AND deleted_at>?", uid, now() - 86400),
                   "edits": n("SELECT COUNT(*) n FROM edits WHERE owner=?", uid),
                   "ads": n("SELECT COUNT(*) n FROM ads WHERE active=1"), "my_ads": n("SELECT COUNT(*) n FROM ads WHERE uid=? AND active=1", uid),
                   "channels": n("SELECT COUNT(*) n FROM channels WHERE owner=? AND active=1", uid),
                   "reminders": n("SELECT COUNT(*) n FROM reminders WHERE uid=? AND done=0", uid),
                   "notes": n("SELECT COUNT(*) n FROM notes WHERE uid=?", uid),
                   "notify_deleted": me["notify_deleted"], "notify_edited": me["notify_edited"],
                   "gold_until": db.gold_until(uid), "gold_price": GOLD_PRICE, "gold_days": GOLD_DAYS})
    if a == "gold_link":
        try:
            return js({"ok": True, "link": await BOT.create_invoice_link(**gold_invoice())})
        except Exception:
            log.exception("invoice link")
            return js({"ok": False, "msg": "Не получилось создать счёт"})
    if a == "deleted":
        rows = deleted_list(uid, 100, str(body.get("q", ""))[:60])
        return js({"ok": True, "items": [{"who": r["sender_name"] or r["chat_name"], "text": r["text"], "media": MEDIA_NAMES.get(r["media_type"], ""),
                                          "at": r["deleted_at"], "date": r["date"]} for r in rows]})
    if a == "edits":
        rows = db.q("SELECT e.*, m.sender_name FROM edits e LEFT JOIN msgs m ON m.owner=e.owner AND m.chat=e.chat AND m.mid=e.mid "
                    "WHERE e.owner=? ORDER BY e.ts DESC LIMIT 60", (uid,))
        return js({"ok": True, "items": [{"who": r["sender_name"] or "", "old": r["old"], "new": r["new"], "at": r["ts"]} for r in rows]})
    if a == "ai":
        text = str(body.get("text", "")).strip()[:4000]
        if not text:
            return js({"ok": False, "msg": "Пустой вопрос"})
        if not ai.provider():
            return js({"ok": False, "msg": "Нейросеть ещё не подключена"})
        if not ai_allowed(uid):
            return js({"ok": False, "msg": f"Лимит на сегодня: {AI_DAY_LIMIT} вопросов"})
        try:
            answer = await ai.ask(history(uid) + [{"role": "user", "content": text}])
        except ai.AIError as e:
            return js({"ok": False, "msg": str(e)})
        remember(uid, "user", text)
        remember(uid, "assistant", answer)
        return js({"ok": True, "text": answer})
    if a == "ai_history":
        return js({"ok": True, "items": history(uid, 30)})
    if a == "ai_reset":
        db.x("DELETE FROM ai_hist WHERE uid=?", (uid,))
        return js({"ok": True})
    if a == "market":
        q, cat = str(body.get("q", ""))[:60], str(body.get("cat", ""))[:40]
        sql, args = "SELECT * FROM ads WHERE active=1", []
        if q:
            sql += " AND (title LIKE ? OR descr LIKE ?)"
            args += [f"%{q}%", f"%{q}%"]
        if cat:
            sql += " AND cat=?"
            args.append(cat)
        if body.get("mine"):
            sql += " AND uid=?"
            args.append(uid)
        if body.get("favs"):
            sql += " AND id IN (SELECT ad FROM favs WHERE uid=?)"
            args.append(uid)
        rows = db.q(sql + " ORDER BY ts DESC LIMIT 80", args)
        favs = {r["ad"] for r in db.q("SELECT ad FROM favs WHERE uid=?", (uid,))}
        out = []
        for r in rows:
            seller = db.user(r["uid"])
            out.append({"id": r["id"], "kind": r["kind"], "cat": r["cat"], "title": r["title"], "descr": r["descr"], "price": r["price"],
                        "photo": bool(r["photo"]), "ts": r["ts"], "views": r["views"], "mine": r["uid"] == uid, "fav": r["id"] in favs,
                        "seller": (seller["name"] if seller else ""), "contact": (f"https://t.me/{seller['username']}" if seller and seller["username"] else "")})
        return js({"ok": True, "items": out, "cats": CATS})
    if a == "market_add":
        title = str(body.get("title", "")).strip()[:80]
        if not title:
            return js({"ok": False, "msg": "Нужно название"})
        kind = "buy" if body.get("kind") == "buy" else "sell"
        cat = str(body.get("cat", "")) if str(body.get("cat", "")) in CATS else CATS[-1]
        cur = db.x("INSERT INTO ads(uid, kind, cat, title, descr, price, ts) VALUES(?,?,?,?,?,?,?)",
                   (uid, kind, cat, title, str(body.get("descr", ""))[:600], str(body.get("price", ""))[:40] or "договорная", now()))
        return js({"ok": True, "id": cur.lastrowid})
    if a == "market_del":
        db.x("UPDATE ads SET active=0 WHERE id=? AND uid=?", (int(body.get("id", 0) or 0), uid))
        return js({"ok": True})
    if a == "market_fav":
        aid = int(body.get("id", 0) or 0)
        if db.one("SELECT 1 FROM favs WHERE uid=? AND ad=?", (uid, aid)):
            db.x("DELETE FROM favs WHERE uid=? AND ad=?", (uid, aid))
        else:
            db.x("INSERT OR IGNORE INTO favs(uid, ad) VALUES(?,?)", (uid, aid))
        return js({"ok": True})
    if a == "market_open":
        aid = int(body.get("id", 0) or 0)
        try:
            await show_ad(uid, aid, uid)
        except Exception:
            pass
        return js({"ok": True})
    if a == "channels":
        rows = db.q("SELECT * FROM channels WHERE owner=? AND active=1", (uid,))
        return js({"ok": True, "items": [{"id": c["id"], "title": c["title"], "kind": c["kind"], **channel_stats(c["id"])} for c in rows]})
    if a == "notes":
        return js({"ok": True, "items": [{"id": r["id"], "text": r["text"], "ts": r["ts"]} for r in db.q("SELECT * FROM notes WHERE uid=? ORDER BY ts DESC LIMIT 100", (uid,))]})
    if a == "note_add":
        t = str(body.get("text", "")).strip()[:2000]
        if t:
            db.x("INSERT INTO notes(uid, text, ts) VALUES(?,?,?)", (uid, t, now()))
        return js({"ok": True})
    if a == "note_del":
        db.x("DELETE FROM notes WHERE id=? AND uid=?", (int(body.get("id", 0) or 0), uid))
        return js({"ok": True})
    if a == "reminders":
        return js({"ok": True, "items": [{"id": r["id"], "text": r["text"], "at": r["at"]} for r in db.q("SELECT * FROM reminders WHERE uid=? AND done=0 ORDER BY at", (uid,))]})
    if a == "reminder_add":
        at = parse_when(str(body.get("when", "")))
        if not at:
            return js({"ok": False, "msg": "Время: 30m, 2h, 1d или 18:30"})
        db.x("INSERT INTO reminders(uid, at, text) VALUES(?,?,?)", (uid, at, str(body.get("text", "Напоминание"))[:500]))
        return js({"ok": True, "at": at})
    if a == "reminder_del":
        db.x("UPDATE reminders SET done=1 WHERE id=? AND uid=?", (int(body.get("id", 0) or 0), uid))
        return js({"ok": True})
    if a == "settings_set":
        k = str(body.get("key", ""))
        if k in ("notify_deleted", "notify_edited"):
            db.x(f"UPDATE users SET {k}=? WHERE id=?", (1 if body.get("value") else 0, uid))
        elif k == "clear_archive":
            db.x("DELETE FROM msgs WHERE owner=?", (uid,))
            db.x("DELETE FROM edits WHERE owner=?", (uid,))
        return js({"ok": True})
    return js({"ok": False, "msg": "Неизвестное действие"}, 400)


async def options(_request):
    return web.Response(headers={**CORS, "Access-Control-Allow-Methods": "POST, OPTIONS"})


async def app_page(_request):
    return web.FileResponse(os.path.join(WEBAPP_DIR, "index.html"), headers={"Cache-Control": "no-cache"})


def make_web() -> web.Application:
    app = web.Application(client_max_size=2 * 1024 * 1024)
    app.router.add_post("/api", api_app)
    app.router.add_route("OPTIONS", "/api", options)
    app.router.add_post("/mini/api", api_mini)
    app.router.add_route("OPTIONS", "/mini/api", options)
    app.router.add_get("/app/", app_page)
    app.router.add_get("/app", app_page)
    app.router.add_get("/", lambda r: web.HTTPFound("/app/"))
    app.router.add_get("/health", lambda r: web.json_response({"ok": True}))
    return app


# ====================================================================== запуск

BOT = None


async def main():
    global BOT
    if not BOT_TOKEN:
        raise SystemExit("Нужен BOT_TOKEN (токен от @BotFather)")
    BOT = Bot(BOT_TOKEN, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher()
    dp.include_router(router)
    runner = web.AppRunner(make_web())
    await runner.setup()
    await web.TCPSite(runner, "0.0.0.0", PORT).start()
    log.info("веб-сервер на порту %s, PUBLIC_URL=%s", PORT, PUBLIC_URL or "—")
    if PUBLIC_URL:
        try:
            await BOT.set_chat_menu_button(menu_button=MenuButtonWebApp(text="StarTegs", web_app=WebAppInfo(url=app_url())))
        except Exception:
            log.exception("menu button")
    asyncio.get_running_loop().create_task(reminder_loop(BOT))
    asyncio.get_running_loop().create_task(snapshot_loop(BOT))
    await BOT.delete_webhook(drop_pending_updates=False)
    await dp.start_polling(BOT, allowed_updates=dp.resolve_used_update_types())


if __name__ == "__main__":
    asyncio.run(main())
