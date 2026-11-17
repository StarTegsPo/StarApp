/*
 * StarTegs 4.0 — «Premium без Premium»: то, что Telegram Premium даёт на стороне приложения,
 * открыто в моде для всех. Быстрая загрузка файлов, сохранение историй в галерею,
 * автоответ «Нет на месте» и приветствие новым собеседникам (как в Telegram Business).
 * Функции, которые проверяет сервер Telegram (4 ГБ, лимиты папок, премиум-стикеры,
 * эмодзи-статус, цвет имени), мод открыть не может — экран честно об этом пишет.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.EditText;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class StarPremium {

    private StarPremium() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    static void toggle(String key, boolean def) {
        prefs().edit().putBoolean(key, !flag(key, def)).apply();
    }

    // ---------------------------------------------------------------- приложение

    /** Быстрая загрузка: больше параллельных запросов, как у Premium (FileLoadOperation). */
    public static boolean fastDownload() { return flag("prem_fast_dl", true); }

    /** «Сохранить в галерею» в чужих историях (если автор разрешил пересылку). */
    public static boolean saveStories() { return flag("prem_save_stories", true); }

    // ---------------------------------------------------------------- «Нет на месте» и приветствие

    static final String DEF_AWAY = "Сейчас не на связи — отвечу, как только освобожусь 🙌";
    static final String DEF_GREET = "Привет! Спасибо, что написали 👋 Скоро отвечу.";
    private static final int AWAY_COOLDOWN = 6 * 3600;

    public static boolean awayOn() { return flag("prem_away", false); }
    public static boolean greetOn() { return flag("prem_greet", false); }
    static String awayText() { return prefs().getString("prem_away_text", DEF_AWAY); }
    static String greetText() { return prefs().getString("prem_greet_text", DEF_GREET); }

    static void editText(BaseFragment f, boolean away, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        EditText input = new EditText(f.getParentActivity());
        input.setText(away ? awayText() : greetText());
        input.setMinLines(2);
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle(away ? "🌙 Текст «Нет на месте»" : "👋 Текст приветствия")
                .setView(input)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) t = away ? DEF_AWAY : DEF_GREET;
                    if (t.length() > 1000) t = t.substring(0, 1000);
                    prefs().edit().putString(away ? "prem_away_text" : "prem_greet_text", t).apply();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null).show();
    }

    private static Set<String> greeted() {
        Set<String> s = prefs().getStringSet("prem_greeted", null);
        return s != null ? new HashSet<>(s) : new HashSet<>();
    }

    /** Новые входящие (вызывается из StarSend.init для всех аккаунтов). */
    static void onIncoming(int account, long dialogId, ArrayList<MessageObject> msgs) {
        try {
            if ((!awayOn() && !greetOn()) || dialogId <= 0 || msgs == null || msgs.isEmpty()) return;
            if (dialogId == UserConfig.getInstance(account).getClientUserId() || dialogId == 777000) return;
            TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
            if (u == null || u.bot || u.self || u.deleted || u.support) return;
            int now = ConnectionsManager.getInstance(account).getCurrentTime();
            boolean fresh = false;
            for (MessageObject m : msgs) {
                if (m != null && m.messageOwner != null && !m.isOut() && now - m.messageOwner.date < 120) {
                    fresh = true;
                    break;
                }
            }
            if (!fresh) return;
            String key = account + ":" + dialogId;
            if (greetOn() && !u.contact) {
                Set<String> g = greeted();
                if (!g.contains(key)) {
                    g.add(key);
                    prefs().edit().putStringSet("prem_greeted", g).apply();
                    send(account, dialogId, greetText());
                    return;
                }
            }
            if (awayOn()) {
                int last = prefs().getInt("prem_away_at_" + key, 0);
                if (now - last < AWAY_COOLDOWN) return;
                prefs().edit().putInt("prem_away_at_" + key, now).apply();
                send(account, dialogId, awayText());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void send(int account, long dialogId, String text) {
        if (TextUtils.isEmpty(text)) return;
        StarSend.direct(account, SendMessagesHelper.SendMessageParams.of(text, dialogId));
    }
}
