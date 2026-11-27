package com.startegs.warmup;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки прогрева из SharedPreferences → WarmupService.Config.
 * Один источник правды и для кнопки «Начать прогрев», и для запуска по расписанию.
 */
final class Cfg {

    /** Ключи настроек, которые относятся к аккаунту (сохраняются в профилях и переносятся с iPhone). */
    static final String[] INT_KEYS = {"videos", "likeMin", "likeMax", "watchMin", "watchMax", "skipChance",
            "longChance", "favChance", "repostChance", "commentChance", "phrasePercent", "followChance",
            "followDaily", "sessionMinutes"};
    static final String[] BOOL_KEYS = {"photos", "skipLive", "skipAds", "capLoops", "nicheSearch", "newAcc"};
    static final String[] STR_KEYS = {"emoji", "phrases", "repostTexts", "niche"};
    static final String[] LONG_KEYS = {"newAccStart"};

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static int i(SharedPreferences p, String k) {
        switch (k) {
            case "videos": return p.getInt(k, 120);
            case "likeMin": return p.getInt(k, 1);
            case "likeMax": return p.getInt(k, 18);
            case "watchMin": return p.getInt(k, 5);
            case "watchMax": return p.getInt(k, 25);
            case "skipChance": return p.getInt(k, 15);
            case "longChance": return p.getInt(k, 10);
            case "favChance": return p.getInt(k, 4);
            case "repostChance": return p.getInt(k, 2);
            case "commentChance": return p.getInt(k, 2);
            case "phrasePercent": return p.getInt(k, 60);
            case "followChance": return p.getInt(k, 1);
            case "followDaily": return p.getInt(k, 10);
            case "sessionMinutes": return p.getInt(k, 40);
            default: return p.getInt(k, 0);
        }
    }

    static boolean b(SharedPreferences p, String k) {
        return p.getBoolean(k, !"newAcc".equals(k));   // всё включено по умолчанию, кроме «Новый аккаунт»
    }

    /** День «нового аккаунта» (0 — первый) или -1, если режим выключен. */
    static int newAccDay(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("newAcc", false)) return -1;
        long start = p.getLong("newAccStart", 0);
        if (start == 0) return 0;
        return (int) Math.max(0, (System.currentTimeMillis() - start) / 86_400_000L);
    }

    /**
     * Готовый Config. timed=true — сессия по расписанию: ограничена «sessionMinutes».
     * Возвращает null, если бесплатный лимит на сегодня исчерпан.
     */
    static WarmupService.Config build(Context ctx, boolean timed) {
        SharedPreferences p = prefs(ctx);
        WarmupService.Config c = new WarmupService.Config();
        c.videos = i(p, "videos");
        c.likeMin = Math.min(i(p, "likeMin"), i(p, "likeMax"));
        c.likeMax = Math.max(i(p, "likeMin"), i(p, "likeMax"));
        c.watchMin = Math.min(i(p, "watchMin"), i(p, "watchMax"));
        c.watchMax = Math.max(i(p, "watchMin"), i(p, "watchMax"));
        c.skipChance = i(p, "skipChance");
        c.longChance = Math.min(i(p, "longChance"), 100 - c.skipChance);
        c.favChance = i(p, "favChance");
        c.repostChance = i(p, "repostChance");
        c.commentChance = i(p, "commentChance");
        c.phrasePercent = i(p, "phrasePercent");
        c.followChance = i(p, "followChance");
        c.followDaily = i(p, "followDaily");
        c.photos = b(p, "photos");
        c.skipLive = b(p, "skipLive");
        c.skipAds = b(p, "skipAds");
        c.capLoops = b(p, "capLoops");
        c.nicheSearch = b(p, "nicheSearch");
        c.niche = keywords(p.getString("niche", ""));
        String[] em = p.getString("emoji", WarmupService.DEFAULT_EMOJI).trim().split("\\s+");
        c.emoji = em.length == 1 && em[0].isEmpty() ? new String[0] : em;
        c.phrases = WarmupService.lines(p.getString("phrases", WarmupService.DEFAULT_PHRASES));
        c.repostTexts = WarmupService.lines(p.getString("repostTexts", WarmupService.DEFAULT_REPOST));
        c.sessionMinutes = timed ? i(p, "sessionMinutes") : 0;

        // «Новый аккаунт»: первые 3 дня очень мягко, дни 4–7 — вполсилы, дальше обычный режим
        int day = newAccDay(ctx);
        if (day >= 0 && day < 7) {
            boolean soft = day < 3;
            int k = soft ? 3 : 2;
            c.likeMin = Math.max(c.likeMin, 2) * k;
            c.likeMax = Math.max(c.likeMax, c.likeMin) * k;
            c.favChance = soft ? 0 : c.favChance / 2;
            c.commentChance = soft ? 0 : c.commentChance / 2;
            c.repostChance = soft ? 0 : c.repostChance / 2;
            c.followChance = soft ? 0 : c.followChance;
            c.followDaily = soft ? 0 : Math.min(c.followDaily, 3);
            c.nicheSearch = !soft && c.nicheSearch;
            int cap = soft ? 50 : 80;
            c.videos = c.videos == 0 ? cap : Math.min(c.videos, cap);
        }

        if (!Premium.isActive(ctx)) {
            int left = Premium.leftToday(ctx);
            if (left <= 0) return null;
            c.dailyLimit = Premium.FREE_DAILY;
            c.skipAds = false;
            c.commentChance = 0;
            c.repostChance = 0;
        }
        return c;
    }

    /** «авто, #мемы, фитнес» → [авто, мемы, фитнес] */
    static String[] keywords(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split("[,;\\n]+")) {
            s = s.trim().replaceFirst("^#", "").trim().toLowerCase();
            if (s.length() >= 2) out.add(s);
        }
        return out.toArray(new String[0]);
    }

    // ---------- снимок настроек: профили аккаунтов и перенос с iPhone ----------

    static JSONObject snapshot(Context ctx) {
        SharedPreferences p = prefs(ctx);
        JSONObject o = new JSONObject();
        try {
            for (String k : INT_KEYS) o.put(k, i(p, k));
            for (String k : BOOL_KEYS) o.put(k, b(p, k));
            for (String k : STR_KEYS) if (p.contains(k)) o.put(k, p.getString(k, ""));
            for (String k : LONG_KEYS) o.put(k, p.getLong(k, 0));
        } catch (JSONException ignored) { }
        return o;
    }

    static void restore(Context ctx, JSONObject o) {
        SharedPreferences.Editor ed = prefs(ctx).edit();
        for (String k : INT_KEYS) if (o.has(k)) ed.putInt(k, Math.max(0, o.optInt(k)));
        for (String k : BOOL_KEYS) if (o.has(k)) ed.putBoolean(k, o.optBoolean(k));
        for (String k : STR_KEYS) if (o.has(k)) ed.putString(k, o.optString(k));
        for (String k : LONG_KEYS) if (o.has(k)) ed.putLong(k, o.optLong(k));
        ed.apply();
    }

    // ---------- профили ----------

    static List<String> profiles(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString("profiles", "[\"Основной\"]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException e) {
            out.add("Основной");
        }
        if (out.isEmpty()) out.add("Основной");
        return out;
    }

    static int currentProfile(Context ctx) {
        return Math.min(prefs(ctx).getInt("profile", 0), profiles(ctx).size() - 1);
    }

    /** Сохраняет текущие настройки в профиль cur и загружает профиль to. */
    static void switchProfile(Context ctx, int to) {
        SharedPreferences p = prefs(ctx);
        int cur = currentProfile(ctx);
        p.edit().putString("profile_data_" + cur, snapshot(ctx).toString()).apply();
        String data = p.getString("profile_data_" + to, null);
        if (data != null) {
            try {
                restore(ctx, new JSONObject(data));
            } catch (JSONException ignored) { }
        }
        p.edit().putInt("profile", to).apply();
    }

    static void addProfile(Context ctx, String name) {
        List<String> list = profiles(ctx);
        list.add(name);
        saveNames(ctx, list);
        // новый профиль начинается с копии текущих настроек
        prefs(ctx).edit().putString("profile_data_" + (list.size() - 1), snapshot(ctx).toString()).apply();
        switchProfile(ctx, list.size() - 1);
    }

    static void deleteProfile(Context ctx, int idx) {
        List<String> list = profiles(ctx);
        if (list.size() <= 1 || idx < 0 || idx >= list.size()) return;
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor ed = p.edit();
        // сдвигаем данные профилей после удалённого
        for (int i = idx; i < list.size() - 1; i++) {
            ed.putString("profile_data_" + i, p.getString("profile_data_" + (i + 1), "{}"));
        }
        ed.remove("profile_data_" + (list.size() - 1));
        ed.apply();
        list.remove(idx);
        saveNames(ctx, list);
        int cur = p.getInt("profile", 0);
        int target = cur == idx ? 0 : (cur > idx ? cur - 1 : cur);
        p.edit().putInt("profile", target).apply();
        if (cur == idx) {
            try {
                restore(ctx, new JSONObject(p.getString("profile_data_" + target, "{}")));
            } catch (JSONException ignored) { }
        }
    }

    private static void saveNames(Context ctx, List<String> names) {
        prefs(ctx).edit().putString("profiles", new JSONArray(names).toString()).apply();
    }
}
