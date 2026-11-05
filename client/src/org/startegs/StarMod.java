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

    /** Пункт меню сообщения «История изменений» (номер не пересекается с OPTION_* Telegram). */
    public static final int OPTION_EDIT_HISTORY = 9001;
    /** Пункт меню сообщения «Спросить ИИ». */
    public static final int OPTION_ASK_AI = 9002;
    /** Пункт меню профиля «ID и дата регистрации». */
    public static final int MENU_PEER_INFO = 9301;
    /** Пункты меню профиля: замок и фильтр пользователя. */
    public static final int MENU_LOCK = 9302;
    public static final int MENU_FILTER_USER = 9303;
    /** Кнопка StarTegs в списке чатов. */
    public static final int MENU_HUB = 9400;
    /** Кнопка «Чат с ИИ» в списке чатов. */
    public static final int MENU_AI = 9401;
    /** Пункт меню профиля: скрыть чат из списка. */
    public static final int MENU_HIDE = 9304;

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

    public static boolean showSeconds() {
        return prefs().getBoolean("show_seconds", false);
    }

    /** Не отправлять «печатает…». */
    public static boolean ghostTyping() {
        return prefs().getBoolean("ghost_typing", false) && isPro();
    }

    /** Не отправлять отметку о прочтении в личных чатах. */
    public static boolean ghostRead() {
        return prefs().getBoolean("ghost_read", false) && isPro();
    }

    /** Не показывать «в сети»: клиент сообщает серверу, что он офлайн. */
    public static boolean ghostOnline() {
        return prefs().getBoolean("ghost_online", false) && isPro();
    }

    /** Смотреть истории, не попадая в список просмотревших. */
    public static boolean ghostStories() {
        return prefs().getBoolean("ghost_stories", false) && isPro();
    }

    public static boolean hideStories() {
        return prefs().getBoolean("hide_stories", false);
    }

    /** Скрыть Premium, Business и подарки в настройках. */
    public static boolean hidePromo() {
        return prefs().getBoolean("hide_promo", false);
    }

    // ---------------------------------------------------------------- Premium мода (тариф в боте StarTegs)

    /** Есть ли у пользователя Premium или VIP в боте StarTegs. Без указанного бота всё бесплатно. */
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

    /** true — можно; иначе предлагает оформить Premium в боте и возвращает false. */
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

    /** Вызывается из LauncherIconController.setIcon: иконки, кроме стандартной, — для Premium. */
    public static boolean allowIcon(int ordinal) {
        if (ordinal == 0 || isPro()) return true;
        AndroidUtilities.runOnUIThread(() -> requirePro(org.telegram.ui.LaunchActivity.getLastFragment(), "Иконки StarTegs"));
        return false;
    }

    /** Переключает настройку; def — значение по умолчанию, если её ещё не меняли. */
    public static void toggle(String key, boolean def) {
        prefs().edit().putBoolean(key, !prefs().getBoolean(key, def)).apply();
    }

    // ---------------------------------------------------------------- своя база мода

    private static final class Db extends SQLiteOpenHelper {
        Db(Context c) {
            super(c, "startegs_mod.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE deleted (account INTEGER, dialog INTEGER, mid INTEGER, date INTEGER, synced INTEGER DEFAULT 0, PRIMARY KEY (account, dialog, mid))");
            db.execSQL("CREATE TABLE edits (id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, dialog INTEGER, mid INTEGER, date INTEGER, text TEXT)");
            db.execSQL("CREATE INDEX edits_idx ON edits (account, dialog, mid)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE deleted ADD COLUMN synced INTEGER DEFAULT 0");
        }
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

    /** Удалено ли сообщение mid (scope: -channel_id для каналов, 0 для остальных). */
    static boolean isDeletedKey(int account, long scope, int mid) {
        ensureLoaded(account);
        synchronized (StarMod.class) {
            return deletedCache.contains(key(account, scope, mid));
        }
    }

    public static boolean isDeleted(int account, MessageObject m) {
        if (m == null || m.messageOwner == null) return false;
        ensureLoaded(account);
        synchronized (StarMod.class) {
            return deletedCache.contains(key(account, deleteScope(m), m.getId()));
        }
    }

    /** Подпись к времени сообщения: секунды (если включены) и «🗑 удалено». */
    public static CharSequence decorateTime(int account, MessageObject m, CharSequence time) {
        try {
            if (time != null && m != null && m.messageOwner != null && showSeconds() && m.messageOwner.date > 0) {
                time = withSeconds(time, m.messageOwner.date);
            }
            if (isDeleted(account, m)) return TextUtils.concat("🗑 удалено  ", time);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return time;
    }

    /** 12:30 → 12:30:07 (и 3:15 PM → 3:15:07 PM), не трогая остальную подпись. */
    private static CharSequence withSeconds(CharSequence time, int date) {
        String hm = LocaleController.getInstance().getFormatterDay().format((long) date * 1000);
        int at = TextUtils.indexOf(time, hm);
        if (at < 0) return time;
        String sec = String.format(Locale.US, "%02d", date % 60);
        String hms = hm.replaceFirst("(\\d{1,2}[:.]\\d{2})", "$1:" + sec);
        return TextUtils.concat(time.subSequence(0, at), hms, time.subSequence(at + hm.length(), time.length()));
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
    private static final int ID_HUB = ID_BASE + 99;

    /** Добавляет раздел в начало экрана настроек (вызывается из SettingsActivity.fillItems). */
    public static void fillSettings(BaseFragment fragment, ArrayList<UItem> items) {
        checkUpdate(fragment, false);
        items.add(UItem.asHeader("⭐ StarTegs"));
        for (int i = 0; i < StarModConfig.SERVICES.length; i++) {
            String[] s = StarModConfig.SERVICES[i];
            if (TextUtils.isEmpty(s[2])) continue; // бот не указан в startegs.properties
            items.add(SettingsActivity.SettingCell.Factory.of(ID_BASE + i, color(s[3]), color(s[4]), iconFor(s[5]), s[0], s[1]));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_HUB, 0xFF9B6BFF, 0xFF5A7BFF, R.drawable.settings_features,
                "Оформление и настройки StarTegs", updateAvailable() ? "🔔 Доступна новая версия" : StarTheme.isActive() ? "Фирменный дизайн включён" : "Дизайн, приватность, сохранение сообщений"));
        items.add(UItem.asShadow(null));
    }

    static int color(String hex) {
        return (int) Long.parseLong(hex, 16);
    }

    static int iconFor(String name) {
        switch (name) {
            case "stars": return R.drawable.settings_stars;
            case "shop": return R.drawable.settings_gift;
            case "business": return R.drawable.settings_business;
            case "chat": return R.drawable.settings_ask;
            case "premium": return R.drawable.settings_premium;
            default: return R.drawable.settings_features;
        }
    }

    /** Обработка нажатий в нашем разделе настроек. true — нажатие наше. */
    public static boolean onSettingsClick(BaseFragment fragment, int id, Runnable refresh) {
        if (id == ID_HUB) {
            fragment.presentFragment(new StarHubActivity());
            return true;
        }
        int i = id - ID_BASE;
        if (i < 0 || i >= StarModConfig.SERVICES.length) return false;
        openService(fragment, i);
        return true;
    }

    /** Открывает бота сервиса: ?startapp — сразу его главное мини-приложение (если включено в @BotFather). */
    static void openService(BaseFragment fragment, int index) {
        String[] s = StarModConfig.SERVICES[index];
        if (TextUtils.isEmpty(s[2]) || fragment.getParentActivity() == null) return;
        Browser.openUrl(fragment.getParentActivity(), "https://t.me/" + s[2] + ("app".equals(s[6]) ? "?startapp" : ""));
    }

    static void confirmClear(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) return;
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

    static void showStats(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) return;
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
                        "Сохранено удалённых сообщений: %d\nСохранено старых версий правок: %d\n\n"
                                + "Всё хранится только на этом телефоне.", deleted, edits))
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------------------------------------------------------------- ID и дата регистрации

    /** Опорные точки «ID пользователя → примерная дата регистрации» (месяц, год). */
    private static final long[][] ID_DATES = {
            {1_000_000L, 2013, 9}, {50_000_000L, 2014, 11}, {100_000_000L, 2015, 3}, {200_000_000L, 2016, 4},
            {300_000_000L, 2017, 2}, {400_000_000L, 2017, 9}, {500_000_000L, 2018, 1}, {600_000_000L, 2018, 6},
            {700_000_000L, 2018, 11}, {800_000_000L, 2019, 2}, {900_000_000L, 2019, 6}, {1_000_000_000L, 2019, 9},
            {1_100_000_000L, 2020, 1}, {1_300_000_000L, 2020, 6}, {1_500_000_000L, 2020, 12}, {1_700_000_000L, 2021, 3},
            {2_000_000_000L, 2021, 7}, {5_000_000_000L, 2021, 9}, {5_500_000_000L, 2022, 5}, {6_000_000_000L, 2023, 3},
            {6_500_000_000L, 2023, 10}, {7_000_000_000L, 2024, 3}, {7_500_000_000L, 2024, 9}, {8_000_000_000L, 2025, 3},
            {8_500_000_000L, 2025, 9},
    };

    private static final String[] MONTHS = {"январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август",
            "сентябрь", "октябрь", "ноябрь", "декабрь"};

    /** Примерный месяц регистрации по ID (линейно между опорными точками). */
    static String estimateRegDate(long userId) {
        if (userId <= 0) return null;
        long[] a = ID_DATES[0], b = ID_DATES[ID_DATES.length - 1];
        if (userId <= a[0]) return "до " + a[1];
        for (int i = 1; i < ID_DATES.length; i++) {
            if (userId <= ID_DATES[i][0]) {
                a = ID_DATES[i - 1];
                b = ID_DATES[i];
                break;
            }
            if (i == ID_DATES.length - 1) return "после " + MONTHS[(int) b[2] - 1] + " " + b[1];
        }
        double k = (double) (userId - a[0]) / (b[0] - a[0]);
        long ma = a[1] * 12 + a[2] - 1, mb = b[1] * 12 + b[2] - 1;
        long m = Math.round(ma + (mb - ma) * k);
        return MONTHS[(int) (m % 12)] + " " + (m / 12);
    }

    public static void showPeerInfo(BaseFragment fragment, long dialogId) {
        if (fragment == null || fragment.getParentActivity() == null || dialogId == 0) return;
        StringBuilder sb = new StringBuilder();
        final String copyId;
        if (dialogId > 0) {
            copyId = String.valueOf(dialogId);
            sb.append("ID: ").append(dialogId);
            String reg = estimateRegDate(dialogId);
            if (reg != null) sb.append("\n\nРегистрация: примерно ").append(reg)
                    .append("\n\nДата оценивается по номеру ID и может отличаться на несколько месяцев.");
        } else {
            long chatId = -dialogId;
            TLRPC.Chat chat = MessagesController.getInstance(fragment.getCurrentAccount()).getChat(chatId);
            boolean channel = ChatObject.isChannel(chat);
            copyId = channel ? "-100" + chatId : String.valueOf(-chatId);
            sb.append("ID: ").append(chatId).append("\nID для ботов: ").append(copyId);
            if (chat != null && chat.date > 0) sb.append("\n\nСоздан: ").append(formatDate(chat.date));
        }
        String note = StarTools.note(dialogId);
        if (!note.isEmpty()) sb.append("\n\n📝 Ваша заметка:\n").append(note);
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🪪 Информация")
                .setMessage(sb.toString())
                .setPositiveButton("Копировать ID", (d, w) -> {
                    AndroidUtilities.addToClipboard(copyId);
                    Toast.makeText(fragment.getParentActivity(), "ID скопирован", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    // ---------------------------------------------------------------- спросить ИИ

    public static boolean canAskAi(MessageObject m) {
        return !TextUtils.isEmpty(StarModConfig.AI_BOT) && m != null && m.messageOwner != null && !TextUtils.isEmpty(m.messageOwner.message);
    }

    /** «Спросить ИИ» в меню сообщения: ответ показывается прямо в чате (см. StarAi). */
    public static void askAi(BaseFragment fragment, MessageObject m) {
        if (fragment == null || !canAskAi(m)) return;
        StarAi.askAboutMessage(fragment, m);
    }

    // ---------------------------------------------------------------- автообновление

    private static volatile boolean checking;

    public static boolean updateAvailable() {
        String name = prefs().getString("update_name", "");
        return !TextUtils.isEmpty(name) && !TextUtils.isEmpty(StarModConfig.BUILD_SHA) && !name.contains(StarModConfig.BUILD_SHA);
    }

    public static String updateName() {
        return prefs().getString("update_name", "");
    }

    /** Проверяет релиз client-latest на GitHub (не чаще раза в 6 часов, если не force). */
    public static void checkUpdate(BaseFragment fragment, boolean force) {
        if (TextUtils.isEmpty(StarModConfig.UPDATE_REPO) || TextUtils.isEmpty(StarModConfig.BUILD_SHA) || checking) return;
        long last = prefs().getLong("update_checked", 0);
        if (!force && System.currentTimeMillis() - last < 6 * 3600_000L) return;
        checking = true;
        Utilities.globalQueue.postRunnable(() -> {
            String bestName = null, bestUrl = null, bestDate = "";
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + StarModConfig.UPDATE_REPO + "/releases/tags/client-latest").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) body.append(line);
                }
                JSONArray assets = new JSONObject(body.toString()).getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String name = a.optString("name"), date = a.optString("created_at");
                    if (name.startsWith("StarTegs-") && name.endsWith(".apk") && date.compareTo(bestDate) > 0) {
                        bestName = name;
                        bestUrl = a.optString("browser_download_url");
                        bestDate = date;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            final String name = bestName, url = bestUrl;
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                SharedPreferences.Editor e = prefs().edit().putLong("update_checked", System.currentTimeMillis());
                if (name != null) e.putString("update_name", name).putString("update_url", url);
                e.apply();
                if (name != null && updateAvailable() && !name.equals(prefs().getString("update_notified", ""))) {
                    prefs().edit().putString("update_notified", name).apply();
                    if (fragment != null && fragment.getParentActivity() != null) offerUpdate(fragment);
                } else if (force && fragment != null && fragment.getParentActivity() != null) {
                    if (updateAvailable()) offerUpdate(fragment);
                    else Toast.makeText(fragment.getParentActivity(), "У вас последняя версия ✓", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    public static void offerUpdate(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null || !updateAvailable()) return;
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🔔 Новая версия StarTegs")
                .setMessage("Доступна " + updateName() + ".\n\nСкачайте файл и установите его поверх текущей версии — чаты и настройки сохранятся.")
                .setPositiveButton("Скачать", (d, w) -> {
                    try {
                        fragment.getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(prefs().getString("update_url", ""))));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    // ---------------------------------------------------------------- список удалённых

    /** Последние удалённые сообщения аккаунта: пары {scope, mid}, новые сверху. */
    static List<long[]> deletedKeys(int account, int limit) {
        ArrayList<long[]> out = new ArrayList<>();
        try (Cursor c = sql().rawQuery("SELECT dialog, mid FROM deleted WHERE account = ? ORDER BY date DESC, mid DESC LIMIT " + limit,
                new String[]{String.valueOf(account)})) {
            while (c.moveToNext()) out.add(new long[]{c.getLong(0), c.getLong(1)});
        } catch (Exception e) {
            FileLog.e(e);
        }
        return out;
    }

    // ---------------------------------------------------------------- кнопка StarTegs в чатах

    public static boolean showChatsButton() {
        return prefs().getBoolean("chats_button", true);
    }

    // ---------------------------------------------------------------- форма аватарок

    /** 0 — круг, 1 — скруглённый квадрат, 2 — квадрат. */
    public static int avatarShape() {
        return prefs().getInt("avatar_shape", 0);
    }

    static void setAvatarShape(int shape) {
        prefs().edit().putInt("avatar_shape", shape).apply();
    }

    /** Радиус скругления аватарки: меняем только обычные круглые аватарки (radius == circleRadius). */
    public static int avatarRadius(int radius, int circleRadius) {
        int shape = avatarShape();
        if (shape == 0 || radius != circleRadius) return radius;
        return shape == 1 ? Math.max(2, Math.round(circleRadius * 0.45f)) : Math.max(2, Math.round(circleRadius * 0.12f));
    }

    /** То же для заглушки с буквами (AvatarDrawable): -1 — рисовать кругом. */
    public static int avatarDrawableRadius(int radius, int circleRadius) {
        int r = avatarRadius(radius, circleRadius);
        return r == radius ? -1 : r;
    }

    // ---------------------------------------------------------------- фильтр слов и людей

    private static volatile String[] filterWords;
    private static volatile Set<Long> filterUsers;

    public static boolean filterEnabled() {
        return prefs().getBoolean("filter_enabled", false);
    }

    static String filterWordsText() {
        return prefs().getString("filter_words", "");
    }

    static void setFilterWords(String text) {
        prefs().edit().putString("filter_words", text == null ? "" : text.trim()).apply();
        filterWords = null;
    }

    private static String[] words() {
        String[] w = filterWords;
        if (w == null) {
            ArrayList<String> list = new ArrayList<>();
            for (String line : filterWordsText().split("[\\n,]")) {
                String t = line.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) list.add(t);
            }
            w = filterWords = list.toArray(new String[0]);
        }
        return w;
    }

    private static Set<Long> users() {
        Set<Long> u = filterUsers;
        if (u == null) {
            u = new HashSet<>();
            for (String id : prefs().getStringSet("filter_users", new HashSet<>())) {
                try {
                    u.add(Long.parseLong(id));
                } catch (NumberFormatException ignore) {
                }
            }
            filterUsers = u;
        }
        return u;
    }

    public static boolean isUserFiltered(long userId) {
        return users().contains(userId);
    }

    public static int filteredUsersCount() {
        return users().size();
    }

    public static void toggleUserFilter(BaseFragment fragment, long userId) {
        Set<String> s = new HashSet<>(prefs().getStringSet("filter_users", new HashSet<>()));
        boolean add = !s.remove(String.valueOf(userId));
        if (add) s.add(String.valueOf(userId));
        prefs().edit().putStringSet("filter_users", s).putBoolean("filter_enabled", add || filterEnabled()).apply();
        filterUsers = null;
        if (fragment != null && fragment.getParentActivity() != null) {
            Toast.makeText(fragment.getParentActivity(), add ? "Сообщения этого человека будут скрыты" : "Сообщения снова видны", Toast.LENGTH_SHORT).show();
        }
    }

    static void clearUserFilter() {
        prefs().edit().remove("filter_users").apply();
        filterUsers = null;
    }

    /**
     * Вызывается из конструктора MessageObject до построения разметки. true — текст
     * сообщения нужно заменить пометкой «скрыто фильтром».
     */
    public static boolean filterMessage(MessageObject m) {
        try {
            if (!filterEnabled() || m == null || m.messageOwner == null || m.messageOwner.out) return false;
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

    static void editFilterWords(BaseFragment fragment, Runnable done) {
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
                .setMessage("Сообщения, в которых есть эти слова, будут скрыты (кроме ваших). Регистр не важен.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    setFilterWords(input.getText().toString());
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------------------------------------------------------------- облачный архив: что уже отправлено

    static List<long[]> unsyncedDeleted(int account, int limit) {
        ArrayList<long[]> out = new ArrayList<>();
        try (Cursor c = sql().rawQuery("SELECT dialog, mid FROM deleted WHERE account = ? AND synced = 0 ORDER BY date LIMIT " + limit,
                new String[]{String.valueOf(account)})) {
            while (c.moveToNext()) out.add(new long[]{c.getLong(0), c.getLong(1)});
        } catch (Exception e) {
            FileLog.e(e);
        }
        return out;
    }

    static void markSynced(int account, List<long[]> keys) {
        try {
            SQLiteDatabase s = sql();
            s.beginTransaction();
            try {
                for (long[] k : keys) {
                    s.execSQL("UPDATE deleted SET synced = 1 WHERE account = ? AND dialog = ? AND mid = ?", new Object[]{account, k[0], k[1]});
                }
                s.setTransactionSuccessful();
            } finally {
                s.endTransaction();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
