/*
 * StarTegs 1.8 — автоответ ИИ выбранным собеседникам и перевод чатов нейросетью
 * (встроенная кнопка «Перевести чат» Telegram без Premium). Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class StarAuto {

    private StarAuto() {}

    public static final int MENU_AUTOREPLY = 9309;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ================================================================ автоответ ИИ

    private static Set<String> enabledSet() {
        return new HashSet<>(prefs().getStringSet("autoreply", new HashSet<>()));
    }

    public static boolean isOn(long dialogId) {
        return dialogId > 0 && enabledSet().contains(String.valueOf(dialogId));
    }

    public static int count() {
        return enabledSet().size();
    }

    public static boolean markReplies() {
        return prefs().getBoolean("autoreply_mark", true);
    }

    static String instructions() {
        return prefs().getString("autoreply_prompt", "");
    }

    public static String menuTitle(long dialogId) {
        return isOn(dialogId) ? "Автоответ ИИ: выключить" : "Автоответ ИИ: включить";
    }

    public static void toggle(BaseFragment fragment, long dialogId) {
        if (dialogId <= 0 || fragment.getParentActivity() == null) return;
        TLRPC.User u = MessagesController.getInstance(fragment.getCurrentAccount()).getUser(dialogId);
        if (u == null || u.bot || UserObject.isUserSelf(u)) {
            Toast.makeText(fragment.getParentActivity(), "Автоответ работает только в личной переписке с людьми", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> s = enabledSet();
        boolean on = !s.remove(String.valueOf(dialogId));
        if (on) s.add(String.valueOf(dialogId));
        prefs().edit().putStringSet("autoreply", s).apply();
        init();
        Toast.makeText(fragment.getParentActivity(), on
                ? "🤖 Нейросеть будет отвечать " + UserObject.getFirstName(u) + ", пока вы не напишете сами (пауза 15 мин)"
                : "Автоответ выключен", Toast.LENGTH_LONG).show();
    }

    static void disableAll() {
        prefs().edit().remove("autoreply").apply();
    }

    static void toggleMark() {
        prefs().edit().putBoolean("autoreply_mark", !markReplies()).apply();
    }

    static void editInstructions(BaseFragment fragment, Runnable done) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setText(instructions());
        input.setMinLines(3);
        input.setHint("Например: я в отпуске до понедельника; отвечай коротко и дружелюбно; цены — в боте @StarTegsShop_bot");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle("🤖 Как отвечать")
                .setMessage("Что нейросеть должна знать и как отвечать вместо вас.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    prefs().edit().putString("autoreply_prompt", input.getText().toString().trim()).apply();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static boolean inited;
    private static final HashMap<String, Long> lastReply = new HashMap<>();
    private static final HashMap<String, Long> pausedUntil = new HashMap<>();
    private static final ArrayDeque<Long> hourWindow = new ArrayDeque<>();
    private static final HashSet<String> busy = new HashSet<>();
    private static final int MAX_PER_HOUR = 20;

    /** Подписаться на новые сообщения всех аккаунтов (вызывается из DialogsActivity). */
    public static void init() {
        if (inited || count() == 0) return;
        inited = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final int account = a;
            NotificationCenter.getInstance(account).addObserver((id, acc, args) -> {
                try {
                    if (id != NotificationCenter.didReceiveNewMessages || args.length < 2) return;
                    if (args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2]) return; // отложенные
                    long dialogId = (Long) args[0];
                    @SuppressWarnings("unchecked") ArrayList<MessageObject> msgs = (ArrayList<MessageObject>) args[1];
                    onNew(account, dialogId, msgs);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }, NotificationCenter.didReceiveNewMessages);
        }
    }

    private static void onNew(int account, long dialogId, ArrayList<MessageObject> msgs) {
        if (!isOn(dialogId) || msgs == null || msgs.isEmpty()) return;
        String key = account + ":" + dialogId;
        long now = System.currentTimeMillis();
        int serverNow = ConnectionsManager.getInstance(account).getCurrentTime();
        MessageObject incoming = null;
        for (MessageObject m : msgs) {
            if (m == null || m.messageOwner == null) continue;
            if (m.isOut()) {
                pausedUntil.put(key, now + 15 * 60_000L); // владелец ответил сам — не мешаем 15 минут
                return;
            }
            if (!TextUtils.isEmpty(m.messageOwner.message) && serverNow - m.messageOwner.date < 120) incoming = m;
        }
        if (incoming == null) return;
        Long paused = pausedUntil.get(key);
        if (paused != null && paused > now) return;
        Long last = lastReply.get(key);
        if (last != null && now - last < 20_000L) return;
        while (!hourWindow.isEmpty() && now - hourWindow.peekFirst() > 3600_000L) hourWindow.pollFirst();
        if (hourWindow.size() >= MAX_PER_HOUR || busy.contains(key)) return;
        busy.add(key);
        lastReply.put(key, now);
        hourWindow.addLast(now);
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            ArrayList<StarTools.Msg> hist = StarTools.readMessages(account, dialogId, 12);
            AndroidUtilities.runOnUIThread(() -> reply(account, dialogId, key, hist));
        });
    }

    private static void reply(int account, long dialogId, String key, ArrayList<StarTools.Msg> hist) {
        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        TLRPC.User other = MessagesController.getInstance(account).getUser(dialogId);
        String myName = me != null ? UserObject.getFirstName(me) : "я";
        String otherName = other != null ? UserObject.getFirstName(other) : "собеседник";
        StringBuilder dialog = new StringBuilder();
        for (int i = hist.size() - 1; i >= 0; i--) {
            StarTools.Msg m = hist.get(i);
            if (m.text.isEmpty()) continue;
            dialog.append(m.out ? myName : otherName).append(": ").append(m.text.length() > 500 ? m.text.substring(0, 500) : m.text).append('\n');
        }
        String extra = instructions();
        String prompt = "Ты отвечаешь в Telegram вместо пользователя по имени " + myName + ", пока он занят. "
                + "Пиши от первого лица, коротко и естественно, на языке собеседника, без markdown. Не обещай того, чего не знаешь; "
                + "если вопрос требует решения самого пользователя — вежливо скажи, что ответишь позже."
                + (extra.isEmpty() ? "" : "\nВажно знать: " + extra)
                + "\n\nПереписка (последнее сообщение — от " + otherName + "):\n" + dialog
                + "\nНапиши только текст ответа.";
        MessagesController.getInstance(account).sendTyping(dialogId, 0, 0, 0);
        StarAi.ask(account, prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                busy.remove(key);
                String t = text == null ? "" : text.trim();
                if (t.isEmpty() || !isOn(dialogId)) return;
                Long paused = pausedUntil.get(key);
                if (paused != null && paused > System.currentTimeMillis()) return; // владелец успел ответить сам
                if (t.length() > 3500) t = t.substring(0, 3500);
                SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(markReplies() ? "🤖 " + t : t, dialogId));
            }

            @Override
            public void error(String why) {
                busy.remove(key);
                FileLog.d("StarTegs autoreply: " + why);
            }
        });
    }

    // ================================================================ перевод чатов нейросетью

    public static boolean aiTranslate() {
        return StarAi.enabled() && prefs().getBoolean("ai_translate", false);
    }

    static void toggleTranslate() {
        prefs().edit().putBoolean("ai_translate", !prefs().getBoolean("ai_translate", false)).apply();
    }

    private static final HashMap<String, String> cache = new HashMap<>();
    private static final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private static boolean running;

    /**
     * Вызывается из TranslateController.pushToTranslate, если у пользователя нет Premium:
     * переводит текст через ИИ и отдаёт результат в тот же колбэк, что и сервер Telegram.
     */
    public static void translate(int account, int messageId, boolean isTranscription, String text, String language,
                                 Utilities.Callback4<Boolean, Integer, TLRPC.TL_textWithEntities, String> callback) {
        if (TextUtils.isEmpty(text) || callback == null) return;
        String ck = language + ":" + text;
        String cached = cache.get(ck);
        if (cached != null) {
            deliver(callback, isTranscription, messageId, cached, language);
            return;
        }
        String langName;
        try {
            langName = new Locale(language).getDisplayLanguage(new Locale("ru"));
        } catch (Exception e) {
            langName = language;
        }
        final String target = TextUtils.isEmpty(langName) ? language : langName;
        queue.addLast(() -> StarAi.ask(account, "Переведи текст на язык: " + target + ". Сохрани смысл, эмодзи и переносы строк. Дай только перевод.\n\n" + text, 0,
                new StarAi.Stream() {
                    @Override
                    public void partial(String t) {
                    }

                    @Override
                    public void done(String t) {
                        if (!TextUtils.isEmpty(t)) {
                            cache.put(ck, t.trim());
                            deliver(callback, isTranscription, messageId, t.trim(), language);
                        }
                        next();
                    }

                    @Override
                    public void error(String why) {
                        next();
                    }
                }));
        if (!running) next();
    }

    private static void next() {
        Runnable r = queue.pollFirst();
        running = r != null;
        if (r != null) r.run();
    }

    private static void deliver(Utilities.Callback4<Boolean, Integer, TLRPC.TL_textWithEntities, String> cb, boolean tr, int id, String text, String lang) {
        TLRPC.TL_textWithEntities t = new TLRPC.TL_textWithEntities();
        t.text = text;
        AndroidUtilities.runOnUIThread(() -> cb.run(tr, id, t, lang));
    }
}
