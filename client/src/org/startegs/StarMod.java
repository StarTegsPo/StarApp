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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.SettingsActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;

public final class StarMod {

    private StarMod() {}

    public static final int OPTION_EDIT_HISTORY = 9001;
    public static final int OPTION_ASK_AI = 9002;
    public static final int MENU_PEER_INFO = 9301;
    public static final int MENU_LOCK = 9302;
    public static final int MENU_FILTER_USER = 9303;
    public static final int MENU_HUB = 9400;
    public static final int MENU_AI = 9401;
    public static final int MENU_HIDE = 9304;
    public static final int MENU_ADMINS = 9305;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean saveDeleted() { return prefs().getBoolean("save_deleted", true); }
    public static boolean saveEdits() { return prefs().getBoolean("save_edits", true); }
    public static boolean showSeconds() { return prefs().getBoolean("show_seconds", false); }
    public static boolean ghostTyping() { return prefs().getBoolean("ghost_typing", false) && isPro(); }
    public static boolean ghostRead() { return prefs().getBoolean("ghost_read", false) && isPro(); }
    public static boolean ghostOnline() { return prefs().getBoolean("ghost_online", false) && isPro(); }
    public static boolean ghostStories() { return prefs().getBoolean("ghost_stories", false) && isPro(); }
    public static boolean hideStories() { return prefs().getBoolean("hide_stories", false); }
    public static boolean hidePromo() { return prefs().getBoolean("hide_promo", false); }

    public static boolean isPro() {
        return TextUtils.isEmpty(StarModConfig.ECO_BOT) || !"free".equals(StarCloud.tier());
    }

    static String tierTitle() {
        switch (StarCloud.tier()) {
            case "vip": return "VIP 👑";
            case "pro": return "Premium ⭐";
            default: return "Free";
        }
    }

    public static boolean requirePro(BaseFragment fragment, String feature) {
        if (isPro()) return true;
        if (fragment == null || fragment.getParentActivity() == null) return false;
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("⭐ " + feature + " — Premium")
                .setMessage("Эта функция мода доступна с Premium или VIP в @" + StarModConfig.ECO_BOT + ". "
                        + "Оформите подписку в боте за Telegram Stars, затем нажмите «Проверить».")
                .setPositiveButton("Оформить", (d, w) -> Browser.openUrl(fragment.getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=premium"))
                .setNeutralButton("Проверить", (d, w) -> StarCloud.refreshTier(fragment, true, null))
                .setNegativeButton("Закрыть", null)
                .show();
        return false;
    }

    public static boolean allowIcon(int ordinal) {
        if (ordinal == 0 || isPro()) return true;
        AndroidUtilities.runOnUIThread(() -> requirePro(org.telegram.ui.LaunchActivity.getLastFragment(), "Иконки StarTegs"));
        return false;
    }

    public static void toggle(String key, boolean def) {
        prefs().edit().putBoolean(key, !prefs().getBoolean(key, def)).apply();
    }

    public static boolean filterMessage(MessageObject m) {
        try {
            if (m == null || m.messageOwner == null) return false;
            if (StarAds.shouldHideMessage(m)) return true;
            if (!filterEnabled() || m.messageOwner.out) return false;
            if (m.type != MessageObject.TYPE_TEXT) return false;
            if (!users().isEmpty() && users().contains(m.getFromChatId())) return true;
            String text = m.messageOwner.message;
            if (TextUtils.isEmpty(text)) return false;
            String low = text.toLowerCase(Locale.ROOT);
            for (String w : words()) if (low.contains(w)) return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    // NOTE: Full StarMod body restored in next commit from Termux/local 40957-byte build.
    // Critical Stage1 hooks above are active. Remaining methods provided as safe stubs below.

    private static final class Db extends SQLiteOpenHelper {
        Db(Context c) { super(c, "startegs_mod.db", null, 2); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE deleted (account INTEGER, dialog INTEGER, mid INTEGER, date INTEGER, synced INTEGER DEFAULT 0, PRIMARY KEY (account, dialog, mid))");
            db.execSQL("CREATE TABLE edits (id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, dialog INTEGER, mid INTEGER, date INTEGER, text TEXT)");
            db.execSQL("CREATE INDEX edits_idx ON edits (account, dialog, mid)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE deleted ADD COLUMN synced INTEGER DEFAULT 0");
        }
    }
    private static Db db;
    private static synchronized SQLiteDatabase sql() {
        if (db == null) db = new Db(ApplicationLoader.applicationContext);
        return db.getWritableDatabase();
    }
    private static final HashSet<String> deletedCache = new HashSet<>();
    private static final HashSet<String> editsCache = new HashSet<>();
    private static final HashSet<Integer> loadedAccounts = new HashSet<>();
    private static String key(int account, long dialog, int mid) { return account + ":" + dialog + ":" + mid; }

    private static synchronized void ensureLoaded(int account) {
        if (loadedAccounts.contains(account)) return;
        loadedAccounts.add(account);
        try (Cursor c = sql().rawQuery("SELECT dialog, mid FROM deleted WHERE account = ?", new String[]{String.valueOf(account)})) {
            while (c.moveToNext()) deletedCache.add(key(account, c.getLong(0), c.getInt(1)));
        } catch (Exception e) { FileLog.e(e); }
        try (Cursor c = sql().rawQuery("SELECT DISTINCT dialog, mid FROM edits WHERE account = ?", new String[]{String.valueOf(account)})) {
            while (c.moveToNext()) editsCache.add(key(account, c.getLong(0), c.getInt(1)));
        } catch (Exception e) { FileLog.e(e); }
    }

    private static long deleteScope(MessageObject m) {
        if (m == null || m.messageOwner == null || m.messageOwner.peer_id == null) return 0;
        long ch = m.messageOwner.peer_id.channel_id;
        return ch != 0 ? -ch : 0;
    }

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
                    v.put("account", account); v.put("dialog", scope); v.put("mid", mid); v.put("date", now);
                    s.insertWithOnConflict("deleted", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                    synchronized (StarMod.class) { deletedCache.add(key(account, scope, mid)); }
                }
                s.setTransactionSuccessful();
            } finally { s.endTransaction(); }
            return true;
        } catch (Exception e) { FileLog.e(e); return false; }
    }

    static boolean isDeletedKey(int account, long scope, int mid) {
        ensureLoaded(account);
        synchronized (StarMod.class) { return deletedCache.contains(key(account, scope, mid)); }
    }

    public static boolean isDeleted(int account, MessageObject m) {
        if (m == null || m.messageOwner == null) return false;
        ensureLoaded(account);
        synchronized (StarMod.class) { return deletedCache.contains(key(account, deleteScope(m), m.getId())); }
    }

    public static CharSequence decorateTime(int account, MessageObject m, CharSequence time) {
        try {
            if (time != null && m != null && m.messageOwner != null && showSeconds() && m.messageOwner.date > 0) {
                time = withSeconds(time, m.messageOwner.date);
            }
            if (isDeleted(account, m)) return TextUtils.concat("🗑 удалено  ", time);
        } catch (Exception e) { FileLog.e(e); }
        return time;
    }

    private static CharSequence withSeconds(CharSequence time, int date) {
        String hm = LocaleController.getInstance().getFormatterDay().format((long) date * 1000);
        int at = TextUtils.indexOf(time, hm);
        if (at < 0) return time;
        String sec = String.format(Locale.US, "%02d", date % 60);
        String hms = hm.replaceFirst("(\\d{1,2}[:.]\\d{2})", "$1:" + sec);
        return TextUtils.concat(time.subSequence(0, at), hms, time.subSequence(at + hm.length(), time.length()));
    }

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
                                ContentValues v = new ContentValues();
                                v.put("account", account); v.put("dialog", dialogId); v.put("mid", now.id);
                                v.put("date", old.edit_date != 0 ? old.edit_date : old.date); v.put("text", oldText);
                                sql().insert("edits", null, v);
                                synchronized (StarMod.class) { editsCache.add(key(account, dialogId, now.id)); }
                            }
                        }
                    }
                } catch (Exception e) { FileLog.e(e); }
                finally { if (cursor != null) cursor.dispose(); }
            }
        });
    }

    public static boolean hasEdits(int account, MessageObject m) {
        if (m == null) return false;
        ensureLoaded(account);
        synchronized (StarMod.class) { return editsCache.contains(key(account, m.getDialogId(), m.getId())); }
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
        } catch (Exception e) { FileLog.e(e); }
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

    // --- filter helpers ---
    public static boolean filterEnabled() { return prefs().getBoolean("filter_on", false); }
    private static Set<String> words() {
        Set<String> s = prefs().getStringSet("filter_words", null);
        return s != null ? s : new HashSet<>();
    }
    private static Set<Long> users() {
        Set<String> raw = prefs().getStringSet("filter_users", null);
        HashSet<Long> out = new HashSet<>();
        if (raw != null) for (String x : raw) try { out.add(Long.parseLong(x)); } catch (Exception ignore) {}
        return out;
    }
    public static String filterWordsText() { return TextUtils.join("\n", words()); }
    public static void setFilterWords(String text) {
        HashSet<String> s = new HashSet<>();
        if (text != null) for (String line : text.split("\n")) { line = line.trim().toLowerCase(Locale.ROOT); if (!line.isEmpty()) s.add(line); }
        prefs().edit().putStringSet("filter_words", s).apply();
    }
    public static int filteredUsersCount() { return users().size(); }
    public static void clearUserFilter() { prefs().edit().remove("filter_users").apply(); }
    public static boolean isUserFiltered(long id) { return users().contains(id); }
    public static void toggleUserFilter(long id) {
        Set<Long> u = users();
        HashSet<String> raw = new HashSet<>();
        for (Long x : u) raw.add(String.valueOf(x));
        String k = String.valueOf(id);
        if (raw.contains(k)) raw.remove(k); else raw.add(k);
        prefs().edit().putStringSet("filter_users", raw).apply();
    }
    public static void editFilterWords(BaseFragment fragment, Runnable done) {
        if (fragment.getParentActivity() == null) return;
        android.widget.EditText input = new android.widget.EditText(fragment.getParentActivity());
        input.setText(filterWordsText());
        input.setMinLines(3);
        input.setHint("каждое слово с новой строки");
        android.widget.FrameLayout box = new android.widget.FrameLayout(fragment.getParentActivity());
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🚫 Слова для фильтра")
                .setMessage("Сообщения с этими словами будут скрыты (кроме ваших).")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> { setFilterWords(input.getText().toString()); if (done != null) done.run(); })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // --- settings / UI stubs that other files call ---
    public static void fillSettings(BaseFragment fragment, ArrayList<UItem> items) {}
    public static boolean onSettingsClick(BaseFragment f, int id, Runnable refresh) { return false; }
    public static boolean updateAvailable() { return false; }
    public static String updateName() { return ""; }
    public static void checkUpdate(BaseFragment f, boolean force) {}
    public static void offerUpdate(BaseFragment f) {}
    public static boolean showChatsButton() { return prefs().getBoolean("chats_button", true); }
    public static int avatarShape() { return prefs().getInt("avatar_shape", 0); }
    public static void setAvatarShape(int v) { prefs().edit().putInt("avatar_shape", v).apply(); }
    public static int avatarRadius(int def, int fallback) { return avatarShape() == 0 ? def : fallback; }
    public static int avatarDrawableRadius(int def, int fallback) { return avatarRadius(def, fallback); }
    public static void showStats(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity()).setTitle("Статистика мода").setMessage("Удалённые: " + deletedCache.size() + "\nПравки: " + editsCache.size()).setPositiveButton("OK", null).show();
    }
    public static void confirmClear(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity()).setTitle("Очистить?").setMessage("Будут удалены сохранённые удалённые и правки.").setPositiveButton("Очистить", (d,w) -> {
            sql().delete("deleted", null, null); sql().delete("edits", null, null);
            deletedCache.clear(); editsCache.clear(); loadedAccounts.clear();
            Toast.makeText(f.getParentActivity(), "Очищено", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Отмена", null).show();
    }
    public static boolean canAskAi(MessageObject m) { return StarAi.enabled() && m != null; }
    public static void askAi(BaseFragment f, MessageObject m) { if (f != null) StarAi.askAbout(f, m); }
    public static void showPeerInfo(BaseFragment f, long dialogId) {
        if (f == null || f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity()).setTitle("ID").setMessage(String.valueOf(dialogId)).setPositiveButton("OK", null).show();
    }
    public static int color(String hex) {
        try { return (int) Long.parseLong(hex.replace("#", ""), 16) | 0xFF000000; } catch (Exception e) { return 0xFF888888; }
    }
    public static int iconFor(String name) { return R.drawable.msg_fave; }
    public static void openService(BaseFragment f, int i) {}
    public static ArrayList<String> deletedKeys(int account) { return new ArrayList<>(); }
    public static ArrayList<Integer> unsyncedDeleted(int account) { return new ArrayList<>(); }
    public static void markSynced(int account, long dialog, int mid) {}
}
