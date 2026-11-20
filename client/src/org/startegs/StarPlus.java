/*
 * StarTegs 4.0 — функции в духе WhiteGram:
 *   • перевод сообщения и перевод своего текста перед отправкой (бесплатный переводчик Telegram);
 *   • «Повторить (+1)» и «К первому сообщению»;
 *   • история профиля: смена имени, @username и аватарки собеседников;
 *   • цензор мата во входящих (звёздочки вместо слов);
 *   • «Прочитать все чаты» одной кнопкой;
 *   • двойное дно: запрет скриншотов в закрытых и скрытых чатах, скрытие из «Недавних»,
 *     тревожный код (стирает сохранённое модом и открывает пустой режим).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StarPlus {

    private StarPlus() {}

    public static final int OPTION_REPEAT = 9030;
    public static final int OPTION_TRANSLATE = 9031;
    public static final int MENU_FIRST = 9330;
    public static final int MENU_TRANSLATE_INPUT = 9331;
    public static final int MENU_PROFILE_HISTORY = 9332;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    static void toggle(String key, boolean def) {
        prefs().edit().putBoolean(key, !flag(key, def)).apply();
    }

    public static boolean censorOn() { return flag("plus_censor", false); }
    public static boolean profileHistoryOn() { return flag("plus_profile_history", true); }
    public static boolean secureLocked() { return flag("plus_secure_locked", true); }
    public static boolean hideRecents() { return flag("plus_hide_recents", false); }
    public static boolean repeatOn() { return flag("plus_repeat", true); }

    // ================================================================ запуск

    /** Вызывается из DialogsActivity.createView. */
    public static void init(Activity activity) {
        applyRecents(activity);
        StarWhen.init();
        StarShade.init(activity);
        StarTrial.maybeOfferOnLaunch();
        StarLife.init(activity);
        StarGuard.init(activity);
        StarDecor.init(activity);
        StarVault.cleanup(org.telegram.messenger.UserConfig.selectedAccount, false); // 4.9: автоочистка корзины
        StarVault.weekly(org.telegram.messenger.UserConfig.selectedAccount); // 4.9: итоги недели
        StarGhost.refreshIndicator();
        StarYou.maybeWelcome(); // 5.0: знакомство при первом запуске
        StarNight.attach(activity); // 5.2: тёплый ночной фильтр
        StarPolish.maybeNews(); // 5.3: «Что нового» после обновления
    }

    /** Секретная клавиатура: просим клавиатуру не запоминать ввод (IME_FLAG_NO_PERSONALIZED_LEARNING). */
    public static boolean incognitoKeyboard() { return flag("plus_incognito_kb", false); }

    static void applyRecents(Activity activity) {
        try {
            Context ctx = activity != null ? activity : ApplicationLoader.applicationContext;
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            for (ActivityManager.AppTask t : am.getAppTasks()) t.setExcludeFromRecents(hideRecents());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ================================================================ двойное дно

    /** Запрет скриншотов (FLAG_SECURE) в закрытых PIN-кодом и скрытых чатах. */
    public static boolean secureChat(long dialogId) {
        try {
            return secureLocked() && dialogId != 0 && (StarLock.isLocked(dialogId) || StarHidden.isHidden(dialogId));
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("startegs-panic:" + s).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static boolean hasPanic() {
        return prefs().contains("plus_panic");
    }

    /**
     * Тревожный код (проверяется в StarPrivacy.unlock). Если введён он — стираем всё,
     * что сохранил мод (удалённые, правки, история профилей), и открываем «пустой» режим.
     */
    static boolean panic(String password) {
        String h = prefs().getString("plus_panic", null);
        if (h == null || password == null || !h.equals(sha(password))) return false;
        try {
            StarMod.wipeArchive();
            SQLiteDatabase s = sql();
            s.delete("profile", null, null);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    static void setupPanic(BaseFragment f, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        if (org.telegram.messenger.SharedConfig.passcodeHash.length() == 0) {
            new AlertDialog.Builder(f.getParentActivity())
                    .setTitle("🚨 Тревожный код")
                    .setMessage("Сначала включите код-пароль Telegram: Настройки → Конфиденциальность → Код-пароль.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        EditText input = new EditText(f.getParentActivity());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Код, отличный от настоящего");
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🚨 Тревожный код")
                .setMessage("Если на экране блокировки ввести этот код, мод сотрёт всё, что сохранил (удалённые сообщения, правки, "
                        + "историю профилей), и откроет приложение в «пустом» режиме без скрытых чатов и заметок.")
                .setView(input)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() < 4) {
                        Toast.makeText(f.getParentActivity(), "Нужно минимум 4 цифры", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (org.telegram.messenger.SharedConfig.checkPasscode(code)) {
                        Toast.makeText(f.getParentActivity(), "Это ваш настоящий код — выберите другой", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs().edit().putString("plus_panic", sha(code)).apply();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null);
        if (hasPanic()) {
            b.setNeutralButton("Убрать", (d, w) -> {
                prefs().edit().remove("plus_panic").apply();
                if (done != null) done.run();
            });
        }
        b.show();
    }

    // ================================================================ история профиля

    private static final class Db extends SQLiteOpenHelper {
        Db(Context c) { super(c, "startegs_plus.db", null, 1); }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE profile (id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, uid INTEGER, date INTEGER, kind TEXT, old TEXT, new TEXT)");
            db.execSQL("CREATE INDEX profile_idx ON profile (account, uid)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    }

    private static Db db;

    private static synchronized SQLiteDatabase sql() {
        if (db == null) db = new Db(ApplicationLoader.applicationContext);
        return db.getWritableDatabase();
    }

    private static String name(TLRPC.User u) {
        String f = u.first_name == null ? "" : u.first_name.trim();
        String l = u.last_name == null ? "" : u.last_name.trim();
        return (f + " " + l).trim();
    }

    private static long photoId(TLRPC.User u) {
        return u.photo != null ? u.photo.photo_id : 0;
    }

    /** Вызывается из MessagesController.putUser до замены старой версии пользователя. */
    public static void onUser(int account, TLRPC.User oldUser, TLRPC.User user, boolean fromCache) {
        try {
            if (fromCache || oldUser == null || user == null || oldUser == user || user.min || oldUser.min || user.self) return;
            if (!profileHistoryOn()) return;
            final ArrayList<String[]> changes = new ArrayList<>();
            String on = name(oldUser), nn = name(user);
            if (!on.isEmpty() && !nn.isEmpty() && !on.equals(nn)) changes.add(new String[]{"name", on, nn});
            String ou = oldUser.username == null ? "" : oldUser.username, nu = user.username == null ? "" : user.username;
            if (!ou.equalsIgnoreCase(nu) && !(ou.isEmpty() && nu.isEmpty())) changes.add(new String[]{"username", ou, nu});
            long op = photoId(oldUser), np = photoId(user);
            if (op != 0 && op != np) changes.add(new String[]{"photo", String.valueOf(op), String.valueOf(np)});
            if (changes.isEmpty()) return;
            final long uid = user.id;
            final int now = (int) (System.currentTimeMillis() / 1000);
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    SQLiteDatabase s = sql();
                    for (String[] c : changes) {
                        ContentValues v = new ContentValues();
                        v.put("account", account);
                        v.put("uid", uid);
                        v.put("date", now);
                        v.put("kind", c[0]);
                        v.put("old", c[1]);
                        v.put("new", c[2]);
                        s.insert("profile", null, v);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void showProfileHistory(BaseFragment f, long dialogId) {
        if (f == null || f.getParentActivity() == null) return;
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("ru"));
        try (Cursor c = sql().rawQuery("SELECT date, kind, old, new FROM profile WHERE account = ? AND uid = ? ORDER BY date DESC, id DESC LIMIT 100",
                new String[]{String.valueOf(f.getCurrentAccount()), String.valueOf(dialogId)})) {
            while (c.moveToNext()) {
                String kind = c.getString(1), o = c.getString(2), n = c.getString(3);
                sb.append(fmt.format(new Date(c.getInt(0) * 1000L))).append('\n');
                if ("name".equals(kind)) sb.append("✏️ Имя: «").append(o).append("» → «").append(n).append("»");
                else if ("username".equals(kind)) sb.append("🔗 Username: ").append(o.isEmpty() ? "нет" : "@" + o).append(" → ").append(n.isEmpty() ? "нет" : "@" + n);
                else sb.append(TextUtils.equals(n, "0") ? "🖼 Удалил аватарку" : "🖼 Сменил аватарку");
                sb.append("\n\n");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (sb.length() == 0) sb.append("Пока изменений не замечено. Мод записывает смену имени, @username и аватарки с момента установки StarTegs 4.0.");
        new AlertDialog.Builder(f.getParentActivity()).setTitle("🕓 История профиля").setMessage(sb.toString().trim()).setPositiveButton("OK", null).show();
    }

    // ================================================================ сообщения

    public static boolean canRepeat(MessageObject m) {
        return repeatOn() && m != null && m.messageOwner != null && m.getId() > 0 && !m.isSponsored()
                && m.messageOwner.action == null && m.canForwardMessage();
    }

    /** Повторить сообщение в этот же чат от своего имени (без «Переслано от»). */
    public static void repeat(ChatActivity chat, MessageObject m) {
        if (chat == null || m == null) return;
        try {
            ArrayList<MessageObject> list = new ArrayList<>();
            list.add(m);
            SendMessagesHelper.getInstance(chat.getCurrentAccount()).sendMessage(list, chat.getDialogId(), true, false, true, 0, 0);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void jumpToFirst(ChatActivity chat) {
        if (chat == null) return;
        try {
            chat.jumpToDate(1375315200); // август 2013 — раньше Telegram не было
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ================================================================ перевод

    static final String[][] LANGS = {
            {"ru", "Русский"}, {"en", "English"}, {"uk", "Українська"}, {"de", "Deutsch"}, {"es", "Español"},
            {"fr", "Français"}, {"it", "Italiano"}, {"tr", "Türkçe"}, {"pl", "Polski"}, {"kk", "Қазақша"},
            {"uz", "O‘zbek"}, {"zh", "中文"}, {"ja", "日本語"}, {"ko", "한국어"}, {"ar", "العربية"}, {"pt", "Português"},
    };

    interface Done {
        void run(String text, String error);
    }

    /** Перевод текста бесплатным переводчиком Telegram (messages.translateText). */
    static void translate(int account, String text, String lang, Done cb) {
        if (TextUtils.isEmpty(text)) {
            cb.run(null, "пустой текст");
            return;
        }
        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        req.flags |= 2;
        TLRPC.TL_textWithEntities t = new TLRPC.TL_textWithEntities();
        t.text = text;
        req.text.add(t);
        req.to_lang = lang;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_messages_translateResult && !((TLRPC.TL_messages_translateResult) res).result.isEmpty()) {
                cb.run(((TLRPC.TL_messages_translateResult) res).result.get(0).text, null);
            } else {
                cb.run(null, err != null ? err.text : "не удалось перевести");
            }
        }));
    }

    static String myLang() {
        try {
            String l = LocaleController.getInstance().getCurrentLocale().getLanguage();
            return TextUtils.isEmpty(l) ? "ru" : l;
        } catch (Exception e) {
            return "ru";
        }
    }

    public static boolean canTranslate(MessageObject m) {
        return m != null && m.messageOwner != null && !TextUtils.isEmpty(m.messageOwner.message) && !m.isSponsored();
    }

    public static void translateMessage(BaseFragment f, MessageObject m) {
        if (f == null || f.getParentActivity() == null || !canTranslate(m)) return;
        Toast.makeText(f.getParentActivity(), "🌍 Перевожу…", Toast.LENGTH_SHORT).show();
        translate(f.getCurrentAccount(), StarCodec.strip(m.messageOwner.message), myLang(), (text, error) -> {
            if (f.getParentActivity() == null) return;
            if (text == null) {
                Toast.makeText(f.getParentActivity(), "Ошибка перевода: " + error, Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(f.getParentActivity())
                    .setTitle("🌍 Перевод")
                    .setMessage(text)
                    .setPositiveButton("Копировать", (d, w) -> AndroidUtilities.addToClipboard(text))
                    .setNegativeButton("Закрыть", null).show();
        });
    }

    /** Перевести текст в поле ввода на выбранный язык (язык запоминается). */
    public static void translateInput(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null || chat.getChatActivityEnterView() == null) return;
        CharSequence cur = chat.getChatActivityEnterView().getFieldText();
        if (TextUtils.isEmpty(cur)) {
            Toast.makeText(chat.getParentActivity(), "Сначала напишите текст в поле ввода", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) names[i] = LANGS[i][1];
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("🌍 На какой язык?")
                .setItems(names, (d, which) -> {
                    String lang = LANGS[which][0];
                    prefs().edit().putString("plus_last_lang", lang).apply();
                    translate(chat.getCurrentAccount(), cur.toString(), lang, (text, error) -> {
                        if (text == null) {
                            Toast.makeText(ApplicationLoader.applicationContext, "Ошибка перевода: " + error, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (chat.getChatActivityEnterView() != null) chat.getChatActivityEnterView().setFieldText(text);
                    });
                })
                .setNegativeButton("Отмена", null).show();
    }

    // ================================================================ цензор

    private static final Pattern MAT = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:"
                    + "[хx][уy][йеёяюи]\\p{L}*|"
                    + "п[иеё]зд\\p{L}*|"
                    + "(?:[её]|йо)б\\p{L}*|"
                    + "(?:за|на|по|от|у|вы|съ|раз|до|под|об|при|про)[ъь]?[её]б\\p{L}*|"
                    + "бл[яэ](?:д\\p{L}*|т\\p{L}*)?|"
                    + "муд[аио]к\\p{L}*|"
                    + "пид[оа]р\\p{L}*|"
                    + "f+u+c+k+\\p{L}*|s+h+i+t+\\p{L}*"
                    + ")(?![\\p{L}])");

    /** Звёздочки вместо мата: «б***» (длина и оформление сообщения сохраняются). */
    public static CharSequence censor(CharSequence text) {
        if (!censorOn() || text == null || text.length() == 0) return text;
        try {
            Matcher m = MAT.matcher(text);
            SpannableStringBuilder sb = null;
            while (m.find()) {
                if (sb == null) sb = new SpannableStringBuilder(text);
                for (int i = m.start() + 1; i < m.end(); i++) sb.replace(i, i + 1, "*");
            }
            if (sb == null) return text;
            return text instanceof Spanned ? sb : sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return text;
        }
    }

    // ================================================================ прочитать всё

    static void readAll(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("✔️ Прочитать все чаты?")
                .setMessage("Все непрочитанные сообщения и упоминания в этом аккаунте будут отмечены прочитанными.")
                .setPositiveButton("Прочитать", (d, w) -> {
                    MessagesController mc = MessagesController.getInstance(f.getCurrentAccount());
                    int n = 0;
                    for (TLRPC.Dialog dialog : new ArrayList<>(mc.getAllDialogs())) {
                        if (dialog == null || dialog.unread_count <= 0 && dialog.unread_mentions_count <= 0) continue;
                        long did = dialog.id;
                        try {
                            if (mc.isForum(did)) mc.markAllTopicsAsRead(did);
                            mc.markMentionsAsRead(did, 0);
                            mc.markDialogAsRead(did, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0);
                            n++;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    Toast.makeText(f.getParentActivity(), n == 0 ? "Непрочитанных нет 👌" : "Прочитано чатов: " + n, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null).show();
    }
}
