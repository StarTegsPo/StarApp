/*
 * StarTegs 3.1 — витрина профиля (настроение, песня, любимые эмодзи), статус «чем занят»
 * и живые аватарки: у друга в сети пульсирует его эмодзи. Видят друзья с модом (бот v60).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.HashMap;
import java.util.Iterator;

public final class StarProfile {

    private StarProfile() {}

    public static final int MENU_SHOWCASE = 9312;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static final String[][] STATUSES = {
            {"🎮", "Играю"}, {"📚", "Учусь"}, {"😴", "Сплю"}, {"🎧", "Слушаю музыку"}, {"⚽", "На тренировке"},
            {"🍕", "Ем"}, {"🚗", "В дороге"}, {"🎬", "Смотрю кино"}, {"💼", "Работаю"}, {"🏖", "Отдыхаю"}, {"🤒", "Болею"}, {"💬", "Свободен — пиши!"},
    };

    // ---------------------------------------------------------------- данные друзей (заполняет StarCosmetics при загрузке рамок)

    private static final HashMap<Long, JSONObject> profiles = new HashMap<>();
    private static boolean loaded;

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(prefs().getString("prof_cache", "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                profiles.put(Long.parseLong(k), o.optJSONObject(k));
            }
        } catch (Exception ignore) {
        }
    }

    static void put(JSONObject all) {
        if (all == null) return;
        load();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String k = it.next();
            try {
                profiles.put(Long.parseLong(k), all.optJSONObject(k));
            } catch (NumberFormatException ignore) {
            }
        }
        JSONObject o = new JSONObject();
        try {
            for (java.util.Map.Entry<Long, JSONObject> e : profiles.entrySet()) if (e.getValue() != null) o.put(String.valueOf(e.getKey()), e.getValue());
        } catch (Exception ignore) {
        }
        prefs().edit().putString("prof_cache", o.toString()).apply();
    }

    static JSONObject of(long uid) {
        long self = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (uid == self) return mine();
        load();
        return profiles.get(uid);
    }

    static JSONObject mine() {
        try {
            JSONObject m = new JSONObject(prefs().getString("prof_mine", "{}"));
            long until = m.optLong("status_until");
            if (until > 0 && until * 1000 < System.currentTimeMillis()) m.put("status", "");
            return m;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Значок на аватарке: статус, а если его нет и друг в сети — первое из его любимых эмодзи. null — ничего. */
    static String badge(long uid, boolean[] pulse) {
        JSONObject p = of(uid);
        if (p == null) return null;
        String st = p.optString("status", "");
        boolean online = isOnline(uid);
        pulse[0] = online;
        if (!st.isEmpty()) return firstEmoji(st);
        if (online) {
            String e = firstEmoji(p.optString("emojis", ""));
            return e.isEmpty() ? null : e;
        }
        return null;
    }

    private static boolean isOnline(long uid) {
        try {
            TLRPC.User u = MessagesController.getInstance(UserConfig.selectedAccount).getUser(uid);
            return u != null && u.status != null && u.status.expires > ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        } catch (Exception e) {
            return false;
        }
    }

    static String firstEmoji(String s) {
        if (s == null || s.isEmpty()) return "";
        java.text.BreakIterator it = java.text.BreakIterator.getCharacterInstance();
        it.setText(s);
        int end = it.next();
        return end > 0 ? s.substring(0, end) : "";
    }

    // ---------------------------------------------------------------- своя витрина

    private static void push(BaseFragment f, JSONObject m, Runnable done) {
        prefs().edit().putString("prof_mine", m.toString()).apply();
        StarCloud.call(f.getCurrentAccount(), "prof_set", m, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                StarAchieve.bump("showcase", 1);
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), "✨ Друзья с StarTegs увидят это", Toast.LENGTH_SHORT).show();
                if (done != null) done.run();
            }

            @Override
            public void fail(String why) {
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            }
        });
    }

    static void editShowcase(BaseFragment f, Runnable done) {
        Context ctx = f.getParentActivity();
        if (ctx == null) return;
        JSONObject m = mine();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        EditText mood = field(ctx, "Настроение дня, например: 😎 на чиле", m.optString("mood"));
        EditText song = field(ctx, "Песня на повторе", m.optString("song"));
        EditText emojis = field(ctx, "3–5 любимых эмодзи, например 🔥💜🐱", m.optString("emojis"));
        box.addView(mood);
        box.addView(song);
        box.addView(emojis);
        new AlertDialog.Builder(ctx)
                .setTitle("🌟 Моя витрина")
                .setMessage("Это видят друзья с StarTegs: профиль → ⋮ → «Витрина». Первое эмодзи пульсирует на твоей аватарке, когда ты в сети.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    try {
                        m.put("mood", mood.getText().toString().trim()).put("song", song.getText().toString().trim())
                                .put("emojis", emojis.getText().toString().trim());
                    } catch (Exception ignore) {
                    }
                    push(f, m, done);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static EditText field(Context ctx, String hint, String value) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setText(value);
        return e;
    }

    static String statusTitle() {
        String s = mine().optString("status", "");
        return s.isEmpty() ? "не задан" : s;
    }

    static void chooseStatus(BaseFragment f, Runnable done) {
        Context ctx = f.getParentActivity();
        if (ctx == null) return;
        CharSequence[] items = new CharSequence[STATUSES.length + 1];
        for (int i = 0; i < STATUSES.length; i++) items[i] = STATUSES[i][0] + "  " + STATUSES[i][1];
        items[STATUSES.length] = "✖️ Убрать статус";
        new AlertDialog.Builder(ctx)
                .setTitle("Чем занят?")
                .setItems(items, (d, which) -> {
                    JSONObject m = mine();
                    if (which == STATUSES.length) {
                        try {
                            m.put("status", "").put("status_until", 0);
                        } catch (Exception ignore) {
                        }
                        push(f, m, done);
                        return;
                    }
                    String st = STATUSES[which][0] + " " + STATUSES[which][1];
                    CharSequence[] hours = {"На 1 час", "На 3 часа", "До конца дня", "Пока не сменю"};
                    new AlertDialog.Builder(ctx)
                            .setTitle(st)
                            .setItems(hours, (d2, h) -> {
                                long now = System.currentTimeMillis() / 1000;
                                long until;
                                if (h == 0) until = now + 3600;
                                else if (h == 1) until = now + 3 * 3600;
                                else if (h == 2) {
                                    java.util.Calendar c = java.util.Calendar.getInstance();
                                    c.set(java.util.Calendar.HOUR_OF_DAY, 23);
                                    c.set(java.util.Calendar.MINUTE, 59);
                                    until = c.getTimeInMillis() / 1000;
                                } else until = 0;
                                try {
                                    m.put("status", st).put("status_until", until);
                                } catch (Exception ignore) {
                                }
                                push(f, m, done);
                            })
                            .show();
                })
                .show();
    }

    /** Профиль друга → ⋮ → «Витрина StarTegs». */
    public static void showFriend(BaseFragment f, long uid) {
        Context ctx = f.getParentActivity();
        if (ctx == null || uid <= 0) return;
        JSONObject p = of(uid);
        StringBuilder sb = new StringBuilder();
        if (p != null) {
            if (!p.optString("status").isEmpty()) sb.append("Сейчас: ").append(p.optString("status")).append("\n\n");
            if (!p.optString("mood").isEmpty()) sb.append("Настроение: ").append(p.optString("mood")).append('\n');
            if (!p.optString("song").isEmpty()) sb.append("🎵 На повторе: ").append(p.optString("song")).append('\n');
            if (!p.optString("emojis").isEmpty()) sb.append("Любимые эмодзи: ").append(p.optString("emojis")).append('\n');
        }
        int streak = StarStreak.value(uid);
        if (streak >= 1) sb.append("\n🔥 Ваш стрик: ").append(streak).append(" дн.");
        if (sb.length() == 0) sb.append("У друга пока нет витрины StarTegs. Позови его в мод — тогда увидишь его настроение, статус и песню на повторе 😉");
        new AlertDialog.Builder(ctx)
                .setTitle("🌟 Витрина")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Ок", null)
                .show();
    }
}
