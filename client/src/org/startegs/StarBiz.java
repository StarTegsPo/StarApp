/*
 * StarTegs Business — бизнес-режим: подписка, мини-CRM (этап, сумма, заметка, напоминание),
 * неотвеченные клиенты, автоответы по ключевым словам и ИИ-продавец по вашему каталогу.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

public final class StarBiz {

    private StarBiz() {}

    public static final int MENU_CLIENT = 9313;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_biz", Context.MODE_PRIVATE);
    }

    // ================================================================ подписка

    /** Бизнес-режим включён пользователем и оплачен (по последнему ответу бота). */
    public static boolean on() {
        return prefs().getBoolean("enabled", false) && paid();
    }

    static boolean paid() {
        return prefs().getLong("until", 0) * 1000 > System.currentTimeMillis() || prefs().getBoolean("forever", false);
    }

    static void setEnabled(boolean v) {
        prefs().edit().putBoolean("enabled", v).apply();
    }

    static boolean enabled() {
        return prefs().getBoolean("enabled", false);
    }

    static int price() {
        return prefs().getInt("price", 299);
    }

    static long until() {
        return prefs().getLong("until", 0);
    }

    static void refresh(int account, Runnable done) {
        StarCloud.call(account, "biz_state", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                long until = res.optLong("until");
                boolean active = res.optBoolean("active");
                prefs().edit().putLong("until", until).putBoolean("forever", active && until * 1000 < System.currentTimeMillis())
                        .putInt("price", res.optInt("price", 299)).apply();
                if (done != null) done.run();
            }

            @Override
            public void fail(String why) {
                if (done != null) done.run();
            }
        });
    }

    /** Открывает оплату Business за Stars прямо в Telegram. */
    static void buy(BaseFragment f) {
        StarCloud.call(f.getCurrentAccount(), "biz_link", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                if (f.getParentActivity() != null) Browser.openUrl(f.getParentActivity(), res.optString("link"));
            }

            @Override
            public void fail(String why) {
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
            }
        });
    }

    static boolean require(BaseFragment f) {
        if (on()) return true;
        if (f.getParentActivity() != null) {
            new AlertDialog.Builder(f.getParentActivity())
                    .setTitle("💼 StarTegs Business")
                    .setMessage("Графики, клиенты, неотвеченные, автоответы, ИИ-продавец и контент-план — в подписке Business за " + price() + " ⭐ на 30 дней.")
                    .setPositiveButton("Оформить", (d, w) -> buy(f))
                    .setNegativeButton("Позже", null)
                    .show();
        }
        return false;
    }

    // ================================================================ мини-CRM

    static final String[][] STAGES = {{"🆕", "Новый"}, {"💬", "В работе"}, {"🤔", "Думает"}, {"🔥", "Горячий"}, {"✅", "Оплатил"}, {"❌", "Отказ"}};

    static JSONObject clients() {
        try {
            return new JSONObject(prefs().getString("crm", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static JSONObject client(long dialogId) {
        return clients().optJSONObject(String.valueOf(dialogId));
    }

    static void saveClient(long dialogId, JSONObject c) {
        JSONObject all = clients();
        try {
            if (c == null) all.remove(String.valueOf(dialogId));
            else all.put(String.valueOf(dialogId), c.put("upd", System.currentTimeMillis() / 1000));
        } catch (Exception ignore) {
        }
        prefs().edit().putString("crm", all.toString()).apply();
    }

    static void setStage(long dialogId, int stage) {
        JSONObject c = client(dialogId);
        try {
            if (c == null) c = new JSONObject().put("since", System.currentTimeMillis() / 1000);
            c.put("stage", stage);
        } catch (Exception ignore) {
        }
        saveClient(dialogId, c);
    }

    /** Значок этапа перед именем в списке чатов (бизнес-режим). */
    static String stageEmoji(long dialogId) {
        if (!on()) return "";
        JSONObject c = client(dialogId);
        return c == null ? "" : STAGES[Math.max(0, Math.min(STAGES.length - 1, c.optInt("stage")))][0];
    }

    /** Профиль → ⋮ → «Карточка клиента». */
    public static void editClient(BaseFragment f, long dialogId) {
        if (!require(f)) return;
        Context ctx = f.getParentActivity();
        JSONObject c0 = client(dialogId);
        final JSONObject c;
        try {
            c = c0 != null ? c0 : new JSONObject().put("stage", 0).put("since", System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            return;
        }
        CharSequence[] items = new CharSequence[STAGES.length + 3];
        for (int i = 0; i < STAGES.length; i++) items[i] = (c.optInt("stage") == i ? "✓ " : "") + STAGES[i][0] + " " + STAGES[i][1];
        items[STAGES.length] = "💰 Сумма сделки" + (c.optLong("amount") > 0 ? ": " + c.optLong("amount") : "");
        items[STAGES.length + 1] = "📝 Заметка" + (c.optString("note").isEmpty() ? "" : ": " + cut(c.optString("note"), 24));
        items[STAGES.length + 2] = "⏰ Напомнить связаться";
        new AlertDialog.Builder(ctx)
                .setTitle("💼 " + StarTools.dialogTitle(f.getCurrentAccount(), dialogId))
                .setItems(items, (d, which) -> {
                    try {
                        if (which < STAGES.length) {
                            c.put("stage", which);
                            saveClient(dialogId, c);
                            Toast.makeText(ctx, "Этап: " + STAGES[which][0] + " " + STAGES[which][1], Toast.LENGTH_SHORT).show();
                        } else if (which == STAGES.length) {
                            input(ctx, "💰 Сумма сделки", "Например 4990", c.optLong("amount") > 0 ? String.valueOf(c.optLong("amount")) : "", true, v -> {
                                try {
                                    c.put("amount", Long.parseLong(v.replaceAll("\\D", "").isEmpty() ? "0" : v.replaceAll("\\D", "")));
                                } catch (Exception ignore) {
                                }
                                saveClient(dialogId, c);
                            });
                        } else if (which == STAGES.length + 1) {
                            input(ctx, "📝 Заметка о клиенте", "Что хочет, бюджет, детали", c.optString("note"), false, v -> {
                                try {
                                    c.put("note", v);
                                } catch (Exception ignore) {
                                }
                                saveClient(dialogId, c);
                            });
                        } else {
                            remindClient(f, dialogId);
                        }
                    } catch (Exception ignore) {
                    }
                })
                .show();
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    interface Value {
        void set(String v);
    }

    static void input(Context ctx, String title, String hint, String value, boolean number, Value cb) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setText(value);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        else e.setMinLines(2);
        LinearLayout box = new LinearLayout(ctx);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> cb.set(e.getText().toString().trim()))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static void remindClient(BaseFragment f, long dialogId) {
        CharSequence[] when = {"Через 2 часа", "Завтра в 10:00", "Через 3 дня", "Через неделю"};
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("⏰ Напомнить связаться")
                .setItems(when, (d, which) -> {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    if (which == 0) c.add(java.util.Calendar.HOUR_OF_DAY, 2);
                    else {
                        c.add(java.util.Calendar.DAY_OF_YEAR, which == 1 ? 1 : which == 2 ? 3 : 7);
                        c.set(java.util.Calendar.HOUR_OF_DAY, 10);
                        c.set(java.util.Calendar.MINUTE, 0);
                    }
                    int ts = (int) (c.getTimeInMillis() / 1000);
                    long self = UserConfig.getInstance(f.getCurrentAccount()).getClientUserId();
                    String link = dialogId > 0 ? "tg://openmessage?user_id=" + dialogId : "tg://openmessage?chat_id=" + (-dialogId);
                    SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(
                            "💼 Напоминание StarTegs: связаться с клиентом — " + StarTools.dialogTitle(f.getCurrentAccount(), dialogId) + "\n" + link, self);
                    p.scheduleDate = ts;
                    SendMessagesHelper.getInstance(f.getCurrentAccount()).sendMessage(p);
                    JSONObject cl = client(dialogId);
                    try {
                        if (cl == null) cl = new JSONObject().put("stage", 1);
                        cl.put("next", ts);
                    } catch (Exception ignore) {
                    }
                    saveClient(dialogId, cl);
                    Toast.makeText(f.getParentActivity(), "⏰ Напомню: " + when[which].toString().toLowerCase(Locale.ROOT), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ================================================================ неотвеченные

    /** Секунд без ответа, если последнее сообщение в личном чате от клиента; иначе -1. */
    static int waiting(int account, long dialogId) {
        if (dialogId <= 0) return -1;
        MessagesController mc = MessagesController.getInstance(account);
        TLRPC.User u = mc.getUser(dialogId);
        if (u == null || u.bot || u.self || u.support) return -1;
        ArrayList<MessageObject> lastList = mc.dialogMessage.get(dialogId);
        MessageObject last = lastList == null || lastList.isEmpty() ? null : lastList.get(0);
        if (last == null || last.isOut() || last.messageOwner == null) return -1;
        return ConnectionsManager.getInstance(account).getCurrentTime() - last.messageOwner.date;
    }

    /** Подпись «⏳2ч» к имени, если клиент ждёт ответа дольше часа (бизнес-режим). */
    static String waitSuffix(long dialogId) {
        if (!on() || dialogId <= 0) return "";
        int w = waiting(UserConfig.selectedAccount, dialogId);
        if (w < 3600 || w > 7 * 86400) return "";
        return w < 86400 ? " ⏳" + (w / 3600) + "ч" : " ⏳" + (w / 86400) + "д";
    }

    // ================================================================ автоответы по ключевым словам

    static JSONArray rules() {
        try {
            return new JSONArray(prefs().getString("rules", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void saveRules(JSONArray a) {
        prefs().edit().putString("rules", a.toString()).apply();
    }

    private static final HashMap<String, Long> ruleSent = new HashMap<>();

    /** Вызывается из StarSend на каждое новое входящее сообщение. */
    static void onIncoming(int account, long dialogId, ArrayList<MessageObject> msgs) {
        if (!on() || dialogId <= 0 || msgs == null) return;
        TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
        if (u == null || u.bot || u.self) return;
        JSONArray rs = rules();
        if (rs.length() == 0) return;
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        for (MessageObject m : msgs) {
            if (m == null || m.isOut() || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message) || now - m.messageOwner.date > 120) continue;
            String text = m.messageOwner.message.toLowerCase(Locale.ROOT);
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.optJSONObject(i);
                if (r == null) continue;
                boolean hit = false;
                for (String kw : r.optString("k").toLowerCase(Locale.ROOT).split(",")) {
                    kw = kw.trim();
                    if (!kw.isEmpty() && text.contains(kw)) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) continue;
                String key = account + ":" + dialogId + ":" + i;
                Long last = ruleSent.get(key);
                if (last != null && System.currentTimeMillis() - last < 30 * 60_000L) continue; // не чаще раза в 30 мин
                ruleSent.put(key, System.currentTimeMillis());
                StarSend.direct(account, SendMessagesHelper.SendMessageParams.of(r.optString("r"), dialogId));
                if (client(dialogId) == null) setStage(dialogId, 0);
                return;
            }
        }
    }

    static void editRule(BaseFragment f, int index, Runnable done) {
        Context ctx = f.getParentActivity();
        JSONArray rs = rules();
        JSONObject cur = index >= 0 ? rs.optJSONObject(index) : null;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        EditText k = new EditText(ctx);
        k.setHint("Слова через запятую: цена, сколько стоит, прайс");
        EditText r = new EditText(ctx);
        r.setHint("Ответ клиенту");
        r.setMinLines(3);
        if (cur != null) {
            k.setText(cur.optString("k"));
            r.setText(cur.optString("r"));
        }
        box.addView(k);
        box.addView(r);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(cur == null ? "➕ Новый автоответ" : "✏️ Автоответ")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    if (k.getText().toString().trim().isEmpty() || r.getText().toString().trim().isEmpty()) return;
                    try {
                        JSONObject o = new JSONObject().put("k", k.getText().toString().trim()).put("r", r.getText().toString().trim());
                        JSONArray a = rules();
                        if (index >= 0 && index < a.length()) a.put(index, o);
                        else a.put(o);
                        saveRules(a);
                    } catch (Exception ignore) {
                    }
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null);
        if (cur != null) b.setNeutralButton("Удалить", (d, w) -> {
            JSONArray a = rules(), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (i != index) out.put(a.opt(i));
            saveRules(out);
            if (done != null) done.run();
        });
        b.show();
    }

    // ================================================================ ИИ-продавец

    static boolean seller() {
        return on() && prefs().getBoolean("seller", false) && StarAi.enabled();
    }

    static void toggleSeller() {
        prefs().edit().putBoolean("seller", !prefs().getBoolean("seller", false)).apply();
        StarAuto.init();
    }

    static String catalog() {
        return prefs().getString("catalog", "");
    }

    static void setCatalog(String s) {
        prefs().edit().putString("catalog", s.length() > 6000 ? s.substring(0, 6000) : s).apply();
    }

    /** ИИ-продавец отвечает новым клиентам — тем, кого нет в контактах. */
    static boolean sellerApplies(int account, long dialogId) {
        if (!seller() || dialogId <= 0) return false;
        TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
        return u != null && !u.bot && !u.self && !u.contact;
    }

    static String sellerPrompt() {
        String cat = catalog();
        return "Ты — вежливый продавец в Telegram. Отвечай клиенту коротко, по делу, дружелюбно, на его языке. Используй только этот каталог и цены, "
                + "ничего не выдумывай; если ответа нет в каталоге — скажи, что уточнишь у менеджера. Подводи к покупке: предложи вариант и следующий шаг. "
                + "Если клиент готов купить или просит связаться/оплатить — в самом конце ответа добавь метку [HOT].\n\nКаталог:\n"
                + (cat.isEmpty() ? "(каталог не заполнен)" : cat);
    }

    /** Ответ ИИ с меткой [HOT] → клиент «горячий», владелец получает подсказку. */
    static String handleHot(int account, long dialogId, String reply) {
        if (reply == null || !reply.contains("[HOT]")) return reply;
        setStage(dialogId, 3);
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext,
                "🔥 Горячий клиент: " + StarTools.dialogTitle(account, dialogId) + " — готов купить!", Toast.LENGTH_LONG).show());
        return reply.replace("[HOT]", "").trim();
    }

    // ================================================================ список клиентов

    static ArrayList<long[]> clientList() {
        ArrayList<long[]> out = new ArrayList<>();
        JSONObject all = clients();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject c = all.optJSONObject(k);
            if (c == null) continue;
            try {
                out.add(new long[]{Long.parseLong(k), c.optInt("stage"), c.optLong("amount"), c.optLong("upd")});
            } catch (NumberFormatException ignore) {
            }
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b[3], a[3]));
        return out;
    }
}
