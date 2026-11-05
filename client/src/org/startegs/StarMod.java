/*
 * StarTegs — мод клиента Telegram для Android.
 * Лицензия: GNU GPL v2 или новее (как и исходный клиент Telegram).
 *
 * Весь код мода собран в этом файле, а в исходники Telegram вставляются только
 * короткие вызовы StarMod.* (см. client/patches/apply.py). Так мод легче
 * переносить на новые версии Telegram.
 */
package org.startegs;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class StarMod {

    private StarMod() {}

    /** Пункт меню сообщения «История изменений» (номер не пересекается с OPTION_* Telegram). */
    public static final int OPTION_EDIT_HISTORY = 9001;

    // ---------------------------------------------------------------- настройки

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean saveDeleted() {
        return prefs().getBoolean("save_deleted", true);
    }

    public static boolean saveEdits() {
        return prefs().getBoolean("save_edits", true);
    }

    private static void toggle(String key) {
        prefs().edit().putBoolean(key, !prefs().getBoolean(key, true)).apply();
    }

    // ---------------------------------------------------------------- своя база мода

    private static final class Db extends SQLiteOpenHelper {
        Db(Context c) {
            super(c, "startegs_mod.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE deleted (account INTEGER, dialog INTEGER, mid INTEGER, date INTEGER, PRIMARY KEY (account, dialog, mid))");
            db.execSQL("CREATE TABLE edits (id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, dialog INTEGER, mid INTEGER, date INTEGER, text TEXT)");
            db.execSQL("CREATE INDEX edits_idx ON edits (account, dialog, mid)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    }

    private static Db db;

    private static synchronized SQLiteDatabase sql() {
        if (db == null) db = new Db(ApplicationLoader.applicationContext);
        return db.getWritableDatabase();
    }

    /** Быстрый кэш для отрисовки: какие сообщения удалены / изменены (ключ account:dialog:mid). */
    private static final HashSet<String> deletedCache = new HashSet<>();
    private static final HashSet<String> editsCache = new HashSet<>();
    private static final HashSet<Integer> loadedAccounts = new HashSet<>();

    private static String key(int account, long dialog, int mid) {
        return account + ":" + dialog + ":" + mid;
    }

    private static synchronized void ensureLoaded(int account) {
        if (loadedAccounts.contains(account)) return;
        loadedAccounts.add(account);
        try (Cursor c = sql().rawQuery("SELECT dialog, mid FROM deleted WHERE account = ?", new String[]{String.valueOf(account)})) {
            while (c.moveToNext()) deletedCache.add(key(account, c.getLong(0), c.getInt(1)));
        } catch (Exception e) {
            FileLog.e(e);
        }
        try (Cursor c = sql().rawQuery("SELECT DISTINCT dialog, mid FROM edits WHERE account = ?", new String[]{String.valueOf(account)})) {
            while (c.moveToNext()) editsCache.add(key(account, c.getLong(0), c.getInt(1)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Ключ диалога в терминах обновлений об удалении: у каналов и супергрупп id сообщений
     * свои (-channel_id), у личных чатов и обычных групп — общие на аккаунт (0).
     */
    private static long deleteScope(MessageObject m) {
        if (m == null || m.messageOwner == null || m.messageOwner.peer_id == null) return 0;
        long ch = m.messageOwner.peer_id.channel_id;
        return ch != 0 ? -ch : 0;
    }

    // ---------------------------------------------------------------- удалённые сообщения

    /**
     * Вызывается из MessagesController при получении updateDeleteMessages /
     * updateDeleteChannelMessages. Возвращает true, если удаление перехвачено:
     * сообщения остаются в локальной базе Telegram и помечаются как удалённые.
     */
    public static boolean interceptDelete(int account, long scope, List<Integer> ids) {
        if (!saveDeleted() || ids == null || ids.isEmpty()) return false;
        try {
            ensureLoaded(account);
            SQLiteDatabase s = sql();
            int now = (int) (System.currentTimeMillis() / 1000);
            s.beginTransaction();
            try {
                for (Integer mid : ids) {
                    if (mid == null) continue;
                    ContentValues v = new ContentValues();
                    v.put("account", account);
                    v.put("dialog", scope);
                    v.put("mid", mid);
                    v.put("date", now);
                    s.insertWithOnConflict("deleted", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                    synchronized (StarMod.class) {
                        deletedCache.add(key(account, scope, mid));
                    }
                }
                s.setTransactionSuccessful();
            } finally {
                s.endTransaction();
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false; // при любой ошибке ведём себя как обычный Telegram
        }
    }

    public static boolean isDeleted(int account, MessageObject m) {
        if (m == null || m.messageOwner == null) return false;
        ensureLoaded(account);
        synchronized (StarMod.class) {
            return deletedCache.contains(key(account, deleteScope(m), m.getId()));
        }
    }

    /** Подпись к времени сообщения: «🗑 удалено», «✏️» если есть сохранённая история. */
    public static CharSequence decorateTime(int account, MessageObject m, CharSequence time) {
        try {
            if (isDeleted(account, m)) return TextUtils.concat("🗑 удалено  ", time);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return time;
    }

    // ---------------------------------------------------------------- история изменений

    /**
     * Вызывается из MessagesController перед тем, как новая версия отредактированных
     * сообщений будет записана в базу. Читает старую версию из базы Telegram в той же
     * очереди хранилища (поэтому успевает до перезаписи) и сохраняет её текст.
     */
    public static void rememberEdits(int account, long dialogId, ArrayList<MessageObject> edited) {
        if (!saveEdits() || edited == null || edited.isEmpty()) return;
        final ArrayList<TLRPC.Message> fresh = new ArrayList<>();
        for (MessageObject m : edited) if (m != null && m.messageOwner != null) fresh.add(m.messageOwner);
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            for (TLRPC.Message now : fresh) {
                SQLiteCursor cursor = null;
                try {
                    cursor = storage.getDatabase().queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + dialogId + " AND mid = " + now.id + " LIMIT 1");
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message old = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            String oldText = old != null && old.message != null ? old.message : "";
                            String newText = now.message != null ? now.message : "";
                            if (old != null && !oldText.equals(newText)) {
                                saveEdit(account, dialogId, now.id, old.edit_date != 0 ? old.edit_date : old.date, oldText);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) cursor.dispose();
                }
            }
        });
    }

    private static void saveEdit(int account, long dialog, int mid, int date, String text) {
        ContentValues v = new ContentValues();
        v.put("account", account);
        v.put("dialog", dialog);
        v.put("mid", mid);
        v.put("date", date);
        v.put("text", text);
        sql().insert("edits", null, v);
        synchronized (StarMod.class) {
            editsCache.add(key(account, dialog, mid));
        }
    }

    public static boolean hasEdits(int account, MessageObject m) {
        if (m == null) return false;
        ensureLoaded(account);
        synchronized (StarMod.class) {
            return editsCache.contains(key(account, m.getDialogId(), m.getId()));
        }
    }

    public static void showEditHistory(BaseFragment fragment, MessageObject m) {
        if (fragment == null || m == null || fragment.getParentActivity() == null) return;
        StringBuilder sb = new StringBuilder();
        try (Cursor c = sql().rawQuery("SELECT date, text FROM edits WHERE account = ? AND dialog = ? AND mid = ? ORDER BY date, id",
                new String[]{String.valueOf(fragment.getCurrentAccount()), String.valueOf(m.getDialogId()), String.valueOf(m.getId())})) {
            int n = 0;
            while (c.moveToNext()) {
                n++;
                sb.append("Версия ").append(n).append(" · ").append(formatDate(c.getInt(0))).append('\n')
                        .append(TextUtils.isEmpty(c.getString(1)) ? "(без текста)" : c.getString(1)).append("\n\n");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        sb.append("Сейчас:\n").append(m.messageOwner != null && m.messageOwner.message != null ? m.messageOwner.message : "");
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("✏️ История изменений")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private static String formatDate(int unix) {
        return LocaleController.getInstance().getFormatterDayMonth().format((long) unix * 1000) + ", "
                + LocaleController.getInstance().getFormatterDay().format((long) unix * 1000);
    }

    // ---------------------------------------------------------------- раздел «StarTegs» в настройках

    private static final int ID_BASE = 9100;
    private static final int ID_MOD_SETTINGS = ID_BASE + 99;

    /** Добавляет раздел в начало экрана настроек (вызывается из SettingsActivity.fillItems). */
    public static void fillSettings(ArrayList<UItem> items) {
        items.add(UItem.asHeader("⭐ StarTegs"));
        for (int i = 0; i < StarModConfig.SERVICES.length; i++) {
            String[] s = StarModConfig.SERVICES[i];
            if (TextUtils.isEmpty(s[2])) continue; // бот не указан в startegs.properties
            items.add(SettingsActivity.SettingCell.Factory.of(ID_BASE + i, (int) Long.parseLong(s[3], 16), (int) Long.parseLong(s[4], 16),
                    iconFor(s[5]), s[0], s[1]));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_MOD_SETTINGS, 0xFF9B6BFF, 0xFF5A7BFF, R.drawable.settings_features,
                "Настройки мода", (saveDeleted() ? "🗑 удалённые ✓" : "🗑 удалённые ✗") + "   " + (saveEdits() ? "✏️ правки ✓" : "✏️ правки ✗")));
        items.add(UItem.asShadow(null));
    }

    private static int iconFor(String name) {
        switch (name) {
            case "stars": return R.drawable.settings_stars;
            case "shop": return R.drawable.settings_gift;
            case "business": return R.drawable.settings_business;
            case "chat": return R.drawable.settings_ask;
            case "premium": return R.drawable.settings_premium;
            default: return R.drawable.settings_features;
        }
    }

    /** Обработка нажатий в нашем разделе. true — нажатие наше. */
    public static boolean onSettingsClick(BaseFragment fragment, int id, Runnable refresh) {
        if (id == ID_MOD_SETTINGS) {
            showModSettings(fragment, refresh);
            return true;
        }
        int i = id - ID_BASE;
        if (i < 0 || i >= StarModConfig.SERVICES.length) return false;
        String[] s = StarModConfig.SERVICES[i];
        // ?startapp открывает главное мини-приложение бота, если оно настроено в @BotFather,
        // иначе Telegram просто откроет чат с ботом (там кнопка меню с мини-приложением).
        String url = "https://t.me/" + s[2] + ("app".equals(s[6]) ? "?startapp" : "");
        Browser.openUrl(fragment.getParentActivity(), url);
        return true;
    }

    private static void showModSettings(BaseFragment fragment, Runnable refresh) {
        if (fragment.getParentActivity() == null) return;
        CharSequence[] items = new CharSequence[]{
                (saveDeleted() ? "✅ " : "⬜ ") + "Сохранять удалённые сообщения",
                (saveEdits() ? "✅ " : "⬜ ") + "Сохранять историю изменений",
                "🧹 Очистить сохранённое модом",
                "ℹ️ О моде StarTegs",
        };
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("Настройки StarTegs")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) toggle("save_deleted");
                    else if (which == 1) toggle("save_edits");
                    else if (which == 2) clearAll(fragment);
                    else if (which == 3) about(fragment);
                    if (which <= 1) {
                        if (refresh != null) refresh.run();
                        AndroidUtilities.runOnUIThread(() -> showModSettings(fragment, refresh), 150);
                    }
                })
                .show();
    }

    private static void clearAll(BaseFragment fragment) {
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("Очистить?")
                .setMessage("Пометки об удалённых сообщениях и сохранённые старые версии правок будут стёрты. Сами сообщения останутся в кэше, пока Telegram его не обновит.")
                .setPositiveButton("Очистить", (d, w) -> {
                    try {
                        sql().execSQL("DELETE FROM deleted");
                        sql().execSQL("DELETE FROM edits");
                        synchronized (StarMod.class) {
                            deletedCache.clear();
                            editsCache.clear();
                        }
                        Toast.makeText(fragment.getParentActivity(), "Готово", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static void about(BaseFragment fragment) {
        int deleted = 0, edits = 0;
        try (Cursor c = sql().rawQuery("SELECT (SELECT COUNT(*) FROM deleted), (SELECT COUNT(*) FROM edits)", null)) {
            if (c.moveToFirst()) {
                deleted = c.getInt(0);
                edits = c.getInt(1);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("⭐ StarTegs " + StarModConfig.MOD_VERSION)
                .setMessage(String.format(Locale.US,
                        "Неофициальный клиент на основе исходного кода Telegram для Android.\n\n"
                                + "Сохранено удалённых сообщений: %d\nСохранено старых версий правок: %d\n\n"
                                + "Всё хранится только на этом телефоне.", deleted, edits))
                .setPositiveButton("OK", null)
                .show();
    }
}
