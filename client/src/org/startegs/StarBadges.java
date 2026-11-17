/*
 * StarTegs 4.1 — значки у имени (✅ 👑 🔥 🎄 …): покупаются в магазине за 💎, их видят
 * друзья с StarTegs в списке чатов справа от имени. Данные — бот StarTegs v61 (badge_get).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public final class StarBadges {

    private StarBadges() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static final HashMap<Long, String> badges = new HashMap<>();
    private static final HashSet<Long> pending = new HashSet<>();
    private static boolean loaded, fetching;
    private static long lastFetch;

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(prefs().getString("badges_cache", "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                badges.put(Long.parseLong(k), o.optString(k));
            }
        } catch (Exception ignore) {
        }
    }

    /** Мой значок (из состояния магазина). */
    static String mine() {
        try {
            return new JSONObject(prefs().getString("cos_state", "{}")).optString("badge_emoji", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Значок пользователя или "" (запрос к боту — пачками, не чаще раза в 30 с). */
    static String of(long userId) {
        if (userId <= 0 || StarModConfig.ECO_BOT.isEmpty() || StarPrivacy.decoy()) return "";
        if (userId == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) return mine();
        load();
        String b = badges.get(userId);
        if (b == null || System.currentTimeMillis() - prefs().getLong("badges_at", 0) > 6 * 3600_000L) {
            pending.add(userId);
            schedule();
        }
        return b == null ? "" : b;
    }

    /** Для StarExtra.decorateName: " ✅" или "". */
    public static String suffix(long dialogId) {
        try {
            String b = of(dialogId);
            return b.isEmpty() ? "" : " " + b;
        } catch (Exception e) {
            return "";
        }
    }

    private static void schedule() {
        if (fetching || pending.isEmpty()) return;
        fetching = true;
        long wait = Math.max(500, 30_000 - (System.currentTimeMillis() - lastFetch));
        AndroidUtilities.runOnUIThread(() -> {
            JSONArray ids = new JSONArray();
            for (Long id : pending) {
                ids.put(id);
                if (ids.length() >= 300) break;
            }
            pending.clear();
            lastFetch = System.currentTimeMillis();
            try {
                StarCloud.call(UserConfig.selectedAccount, "badge_get", new JSONObject().put("ids", ids), new StarCloud.Result() {
                    @Override
                    public void ok(JSONObject res) {
                        fetching = false;
                        JSONObject b = res.optJSONObject("badges");
                        boolean changed = false;
                        for (int i = 0; i < ids.length(); i++) {
                            long id = ids.optLong(i);
                            String v = b != null ? b.optString(String.valueOf(id), "") : "";
                            if (!v.equals(badges.get(id))) changed = true;
                            badges.put(id, v);
                        }
                        try {
                            JSONObject all = new JSONObject();
                            for (java.util.Map.Entry<Long, String> e : badges.entrySet()) all.put(String.valueOf(e.getKey()), e.getValue());
                            prefs().edit().putString("badges_cache", all.toString()).putLong("badges_at", System.currentTimeMillis()).apply();
                        } catch (Exception ignore) {
                        }
                        if (changed) NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        schedule();
                    }

                    @Override
                    public void fail(String why) {
                        fetching = false; // бот без v61 — молчим 10 минут
                        lastFetch = System.currentTimeMillis() + 10 * 60_000L;
                    }
                });
            } catch (Exception e) {
                fetching = false;
            }
        }, wait);
    }
}
