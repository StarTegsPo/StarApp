/*
 * StarTegs 1.7 — метки чатов, защита от мошенников, «напомнить ответить»,
 * расшифровка голосовых, двойное нажатие и ИИ-вопрос по переписке.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;

public final class StarExtra {

    private StarExtra() {}

    public static final int OPTION_TRANSCRIBE = 9003;
    public static final int OPTION_REMIND = 9004;
    public static final int MENU_LABEL = 9308;
    public static final int MENU_ASK_CHAT = 9012;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static SharedPreferences labelPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_labels", Context.MODE_PRIVATE);
    }

    // ================================================================ метки чатов

    /** Эмодзи и название меток. Индекс 0 — «без метки». */
    public static final String[][] LABELS = {
            {"", "Без метки"},
            {"🟢", "Новый"},
            {"🟡", "В работе"},
            {"🔵", "Оплатил"},
            {"🟣", "VIP"},
            {"🔴", "Проблемный"},
            {"⚪", "Завершён"},
    };

    public static int label(long dialogId) {
        int l = labelPrefs().getInt(String.valueOf(dialogId), 0);
        return l >= 0 && l < LABELS.length ? l : 0;
    }

    public static String labelMenuTitle(long dialogId) {
        int l = label(dialogId);
        return l == 0 ? "Поставить метку" : "Метка: " + LABELS[l][0] + " " + LABELS[l][1];
    }

    /** Вызывается из DialogCell: эмодзи метки перед именем чата. */
    public static CharSequence decorateName(long dialogId, CharSequence name) {
        try {
            if (name == null || dialogId == 0) return name;
            int l = label(dialogId);
            return l == 0 ? name : TextUtils.concat(LABELS[l][0], " ", name);
        } catch (Exception e) {
            return name;
        }
    }

    public static void chooseLabel(BaseFragment fragment, long dialogId) {
        if (fragment.getParentActivity() == null || dialogId == 0) return;
        CharSequence[] items = new CharSequence[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) items[i] = (label(dialogId) == i ? "✓ " : "") + (LABELS[i][0] + " " + LABELS[i][1]).trim();
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🏷 Метка чата")
                .setItems(items, (d, which) -> {
                    if (which == 0) labelPrefs().edit().remove(String.valueOf(dialogId)).apply();
                    else labelPrefs().edit().putInt(String.valueOf(dialogId), which).apply();
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
                })
                .show();
    }

    /** Все чаты с меткой l. */
    public static ArrayList<Long> withLabel(int l) {
        ArrayList<Long> out = new ArrayList<>();
        for (java.util.Map.Entry<String, ?> e : labelPrefs().getAll().entrySet()) {
            try {
                if (e.getValue() instanceof Integer && (Integer) e.getValue() == l) out.add(Long.parseLong(e.getKey()));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    // ================================================================ защита от мошенников

    public static boolean guardEnabled() {
        return prefs().getBoolean("scam_guard", true);
    }

    private static final Pattern[] SCAM = {
            Pattern.compile("(скинь|пришли|отправь|назови|продиктуй|сообщи|скажи)[^.\\n]{0,30}(код|пароль|смс)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("код (из|в) (смс|sms|telegram|телеграм)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("(служба|команда) (поддержки|безопасности) (telegram|телеграм)|telegram support|telegram security", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("(аккаунт|профиль)[^.\\n]{0,30}(будет|может быть)[^.\\n]{0,15}(удал|заблок)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("(вы|ты) (выиграли|выиграл|победил)|(получи|забери)[^.\\n]{0,20}(приз|подарок|выигрыш)|бесплатн[а-я]* (premium|премиум|звёзд|звезд)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("(удвою|умножу|x2|х2)[^.\\n]{0,30}(крипт|usdt|btc|ton|звёзд|звезд|деньг)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("https?://[^\\s]*(te1egram|telegrarn|teiegram|telegramm|tg-premium|premium-tg|telegram-gift|fragment-|-telegram\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(переведи|скинь|отправь)[^.\\n]{0,25}(на карту|денег|деньги)[^.\\n]{0,30}(срочно|быстро|верну)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
    };

    /** Вызывается из конструктора MessageObject: true — текст похож на мошенничество. */
    public static boolean isSuspicious(MessageObject m) {
        try {
            if (!guardEnabled() || m == null || m.messageOwner == null || m.messageOwner.out || m.type != MessageObject.TYPE_TEXT) return false;
            String t = m.messageOwner.message;
            if (TextUtils.isEmpty(t) || t.length() < 8) return false;
            long from = m.getFromChatId();
            if (from > 0) {
                TLRPC.User u = MessagesController.getInstance(m.currentAccount).getUser(from);
                if (u != null && (u.contact || u.verified || u.id == 777000)) return false; // контакты, официальные и сервисные — не трогаем
            }
            for (Pattern p : SCAM) if (p.matcher(t).find()) return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    // ================================================================ напомнить ответить

    /** Ставит напоминание: отложенное сообщение в «Избранное», Telegram пришлёт уведомление. */
    public static void remind(BaseFragment fragment, MessageObject m) {
        if (fragment.getParentActivity() == null || m == null) return;
        CharSequence[] items = {"Через 30 минут", "Через 1 час", "Через 3 часа", "Сегодня в 20:00", "Завтра в 9:00"};
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("⏰ Напомнить ответить")
                .setItems(items, (d, which) -> {
                    int now = fragment.getConnectionsManager().getCurrentTime();
                    int when;
                    Calendar c = Calendar.getInstance();
                    switch (which) {
                        case 0: when = now + 30 * 60; break;
                        case 1: when = now + 3600; break;
                        case 2: when = now + 3 * 3600; break;
                        case 3:
                            c.set(Calendar.HOUR_OF_DAY, 20);
                            c.set(Calendar.MINUTE, 0);
                            c.set(Calendar.SECOND, 0);
                            when = (int) (c.getTimeInMillis() / 1000);
                            if (when < now + 120) when += 86400;
                            break;
                        default:
                            c.add(Calendar.DAY_OF_YEAR, 1);
                            c.set(Calendar.HOUR_OF_DAY, 9);
                            c.set(Calendar.MINUTE, 0);
                            c.set(Calendar.SECOND, 0);
                            when = (int) (c.getTimeInMillis() / 1000);
                    }
                    long dialog = m.getDialogId();
                    String who = StarTools.dialogTitle(fragment.getCurrentAccount(), dialog);
                    String text = m.messageOwner != null && !TextUtils.isEmpty(m.messageOwner.message) ? m.messageOwner.message : "[медиа]";
                    if (text.length() > 300) text = text.substring(0, 300) + "…";
                    String msg = "⏰ Напоминание StarTegs: ответить — " + who + "\n«" + text + "»\n" + link(fragment.getCurrentAccount(), m);
                    long self = UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
                    SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(msg, self);
                    p.scheduleDate = when;
                    SendMessagesHelper.getInstance(fragment.getCurrentAccount()).sendMessage(p);
                    Toast.makeText(fragment.getParentActivity(), "⏰ Напомню: " + items[which].toString().toLowerCase(Locale.ROOT), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static String link(int account, MessageObject m) {
        long dialog = m.getDialogId();
        int id = m.getId();
        if (dialog > 0) return "tg://openmessage?user_id=" + dialog + "&message_id=" + id;
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialog);
        if (ChatObject.isChannel(chat)) {
            String un = ChatObject.getPublicUsername(chat);
            return !TextUtils.isEmpty(un) ? "https://t.me/" + un + "/" + id : "https://t.me/c/" + (-dialog) + "/" + id;
        }
        return "tg://openmessage?chat_id=" + (-dialog) + "&message_id=" + id;
    }

    // ================================================================ расшифровка голосовых

    public static boolean canTranscribe(MessageObject m) {
        return StarAi.enabled() && m != null && (m.isVoice() || m.isRoundVideo());
    }

    public static void transcribe(BaseFragment fragment, MessageObject m) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null || !canTranscribe(m)) return;
        File f = FileLoader.getInstance(fragment.getCurrentAccount()).getPathToMessage(m.messageOwner);
        if (f == null || !f.exists() || f.length() < 100) {
            Toast.makeText(ctx, "Сначала прослушайте сообщение — оно должно загрузиться", Toast.LENGTH_LONG).show();
            return;
        }
        if (f.length() > 20 * 1024 * 1024) {
            Toast.makeText(ctx, "Запись слишком длинная (до 20 МБ)", Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.TextView tv = new android.widget.TextView(ctx);
        tv.setTextSize(16);
        tv.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        tv.setTextIsSelectable(true);
        tv.setText("🎙 Расшифровываю…");
        int pad = AndroidUtilities.dp(22);
        tv.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(8));
        final String[] result = {null};
        new AlertDialog.Builder(ctx)
                .setTitle("🎙 Текст сообщения")
                .setView(tv)
                .setPositiveButton("Копировать", (d, w) -> {
                    if (result[0] != null) AndroidUtilities.addToClipboard(result[0]);
                })
                .setNegativeButton("Закрыть", null)
                .show();
        StarAi.api(fragment.getCurrentAccount(), new StarAi.Api() {
            @Override
            public void ok(String base, String init) {
                Utilities.globalQueue.postRunnable(() -> {
                    String text, err = null;
                    try {
                        byte[] data = readAll(f);
                        HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/stt").openConnection();
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(120000);
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/octet-stream");
                        c.setRequestProperty("X-Init-Data", init);
                        try (OutputStream os = c.getOutputStream()) {
                            os.write(data);
                        }
                        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
                        JSONObject r = new JSONObject(new String(readStream(in), "UTF-8"));
                        text = r.optString("text", "");
                        if (r.has("error")) err = r.optString("error");
                    } catch (Exception e) {
                        FileLog.e(e);
                        text = null;
                        err = "Сервер ИИ недоступен — бот запущен?";
                    }
                    final String t = text, er = err;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (er != null || TextUtils.isEmpty(t)) tv.setText("⚠️ " + (er != null ? er : "Не удалось разобрать речь"));
                        else {
                            result[0] = t;
                            tv.setText(t);
                        }
                    });
                });
            }

            @Override
            public void fail(String why) {
                tv.setText("⚠️ " + why);
            }
        });
    }

    private static byte[] readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            return readStream(in);
        }
    }

    private static byte[] readStream(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    // ================================================================ двойное нажатие

    public static final String[] TAP_ACTIONS = {"Реакция (как в Telegram)", "Ответить", "Спросить ИИ", "Копировать текст", "Напомнить ответить"};

    public static int tapAction() {
        return prefs().getInt("double_tap", 0);
    }

    static void setTapAction(int a) {
        prefs().edit().putInt("double_tap", a).apply();
    }

    /** Вызывается из ChatActivity.onDoubleTap. true — нажатие обработано модом (реакцию не ставим). */
    public static boolean onDoubleTap(ChatActivity chat, MessageObject m) {
        try {
            switch (tapAction()) {
                case 1:
                    chat.showFieldPanelForReply(m);
                    return true;
                case 2:
                    if (StarMod.canAskAi(m)) StarAi.askAboutMessage(chat, m);
                    return true;
                case 3:
                    if (m.messageOwner != null && !TextUtils.isEmpty(m.messageOwner.message)) {
                        AndroidUtilities.addToClipboard(m.messageOwner.message);
                        Toast.makeText(chat.getParentActivity(), "Скопировано", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case 4:
                    remind(chat, m);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    // ================================================================ ИИ-вопрос по переписке

    /** Меню чата → «Спросить ИИ про этот чат»: ответ по последним 400 сообщениям из базы. */
    public static void askAboutChat(ChatActivity chat) {
        Context ctx = chat.getParentActivity();
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setHint("Например: о чём договорились по оплате?");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        final long dialog = chat.getDialogId();
        final int account = chat.getCurrentAccount();
        new AlertDialog.Builder(ctx)
                .setTitle("🧠 Спросить ИИ про чат")
                .setMessage("Нейросеть прочитает последние сообщения этого чата и ответит на вопрос.")
                .setView(box)
                .setPositiveButton("Спросить", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.isEmpty()) return;
                    MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                        ArrayList<StarTools.Msg> list = StarTools.readMessages(account, dialog, 400);
                        AndroidUtilities.runOnUIThread(() -> {
                            StringBuilder sb = new StringBuilder();
                            SimpleDate f = new SimpleDate();
                            for (int i = list.size() - 1; i >= 0; i--) {
                                StarTools.Msg m = list.get(i);
                                if (m.text.isEmpty()) continue;
                                String who = m.out ? "Я" : StarTools.dialogTitle(account, m.from);
                                String t = m.text.length() > 300 ? m.text.substring(0, 300) + "…" : m.text;
                                sb.append('[').append(f.format(m.date)).append("] ").append(who).append(": ").append(t).append('\n');
                                if (sb.length() > 12000) break;
                            }
                            if (sb.length() == 0) {
                                Toast.makeText(ctx, "В приложении нет текстовых сообщений этого чата", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            StarAi.streamDialog(chat, "🧠 Ответ по чату",
                                    "Ниже переписка. Ответь на вопрос только по ней, укажи даты нужных сообщений. Если ответа нет — так и скажи.\n\nВопрос: "
                                            + q + "\n\nПереписка:\n" + sb, null, null);
                        });
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static final class SimpleDate {
        private final java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());

        String format(int unix) {
            return f.format(new java.util.Date((long) unix * 1000));
        }
    }
}
