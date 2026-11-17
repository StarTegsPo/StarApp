/*
 * StarTegs 4.1 — «Отправить, когда появится в сети»: сообщение уходит, как только собеседник
 * появляется онлайн. Работает, пока StarTegs запущен (в фоне тоже, если система не выгрузила
 * приложение). Ожидание — до 7 дней. Premium.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;

public final class StarWhen {

    private StarWhen() {}

    public static final int MENU_WHEN_ONLINE = 9333;
    private static final long TTL = 7 * 86400_000L;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static JSONArray pending() {
        try {
            return new JSONArray(prefs().getString("when_online", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(JSONArray a) {
        prefs().edit().putString("when_online", a.toString()).apply();
    }

    static int count() {
        return pending().length();
    }

    public static boolean available(ChatActivity chat) {
        if (chat == null || chat.getDialogId() <= 0) return false;
        TLRPC.User u = chat.getMessagesController().getUser(chat.getDialogId());
        return u != null && !u.bot && !u.self;
    }

    public static void ask(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null || !available(chat)) return;
        if (!StarMod.requirePro(chat, "Отправить, когда появится в сети")) return;
        long uid = chat.getDialogId();
        TLRPC.User u = chat.getMessagesController().getUser(uid);
        EditText input = new EditText(chat.getParentActivity());
        CharSequence cur = chat.getChatActivityEnterView() != null ? chat.getChatActivityEnterView().getFieldText() : null;
        if (cur != null) input.setText(cur);
        input.setHint("Текст сообщения");
        input.setMinLines(2);
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("📨 Когда " + UserObject.getFirstName(u) + " появится в сети")
                .setMessage("Сообщение уйдёт само, как только собеседник будет онлайн (пока StarTegs запущен, до 7 дней).")
                .setView(input)
                .setPositiveButton("Ждать", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    try {
                        JSONArray a = pending();
                        a.put(new JSONObject().put("a", chat.getCurrentAccount()).put("u", uid).put("t", text).put("ts", System.currentTimeMillis()));
                        save(a);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (chat.getChatActivityEnterView() != null) chat.getChatActivityEnterView().setFieldText("");
                    Toast.makeText(chat.getParentActivity(), "⏳ Отправлю, когда " + UserObject.getFirstName(u) + " будет в сети", Toast.LENGTH_LONG).show();
                    check();
                })
                .setNeutralButton(count() > 0 ? "Очередь (" + count() + ")" : null, (d, w) -> manage(chat))
                .setNegativeButton("Отмена", null).show();
    }

    static void manage(ChatActivity chat) {
        JSONArray a = pending();
        if (chat.getParentActivity() == null || a.length() == 0) return;
        CharSequence[] items = new CharSequence[a.length()];
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            TLRPC.User u = MessagesController.getInstance(o.optInt("a")).getUser(o.optLong("u"));
            String t = o.optString("t");
            items[i] = "❌ " + (u != null ? UserObject.getFirstName(u) : "?") + ": " + (t.length() > 40 ? t.substring(0, 40) + "…" : t);
        }
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("Очередь — нажмите, чтобы отменить")
                .setItems(items, (d, which) -> {
                    JSONArray b = new JSONArray();
                    for (int i = 0; i < a.length(); i++) if (i != which) b.put(a.optJSONObject(i));
                    save(b);
                })
                .setNegativeButton("Закрыть", null).show();
    }

    private static boolean inited;

    static void init() {
        if (inited) return;
        inited = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver((id, acc, args) -> {
                if (id == NotificationCenter.updateInterfaces) check();
            }, NotificationCenter.updateInterfaces);
        }
        check();
    }

    private static boolean online(int account, TLRPC.User u) {
        if (u == null || u.status == null) return false;
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        return u.status instanceof TLRPC.TL_userStatusOnline && u.status.expires > now;
    }

    static void check() {
        try {
            JSONArray a = pending();
            if (a.length() == 0) return;
            JSONArray keep = new JSONArray();
            boolean changed = false;
            long nowMs = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) {
                    changed = true;
                    continue;
                }
                int account = o.optInt("a");
                long uid = o.optLong("u");
                if (nowMs - o.optLong("ts") > TTL || !UserConfig.getInstance(account).isClientActivated()) {
                    changed = true;
                    continue;
                }
                TLRPC.User u = MessagesController.getInstance(account).getUser(uid);
                if (online(account, u)) {
                    changed = true;
                    StarSend.direct(account, SendMessagesHelper.SendMessageParams.of(o.optString("t"), uid));
                    Toast.makeText(ApplicationLoader.applicationContext, "📨 " + UserObject.getFirstName(u) + " в сети — сообщение отправлено", Toast.LENGTH_SHORT).show();
                } else {
                    keep.put(o);
                }
            }
            if (changed) save(keep);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
