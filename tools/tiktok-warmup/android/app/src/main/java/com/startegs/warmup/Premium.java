package com.startegs.warmup;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Premium и бесплатные лимиты.
 * Бесплатно: лайки, избранное, фото-посты, пропуск эфиров, темы, лучшие настройки — до FREE_DAILY постов в день.
 * Premium: без дневного лимита, авто-пропуск рекламы, комментарии (эмодзи и фразы), репосты с текстом.
 * Активация — кодом; в приложении хранятся только SHA-256 хеши кодов.
 */
final class Premium {

    static final int FREE_DAILY = 100;
    /** Бот, где продаётся Premium. Поменяйте на username своего бота. */
    static final String BUY_URL = "https://t.me/StarTegsShop_bot";

    private static final String SALT = "tw-premium-v1:";
    /** SHA-256 хеши кодов (assets/codes.sha): 31 первых кодов + 5000 кодов, которые выдаёт бот. */
    private static Set<String> hashes;

    private static synchronized Set<String> hashes(Context c) {
        if (hashes != null) return hashes;
        Set<String> set = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("codes.sha"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) set.add(line);
            }
        } catch (IOException ignored) { }
        hashes = set;
        return set;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static boolean isActive(Context c) {
        return prefs(c).getBoolean("premium", false);
    }

    /** true — код верный, Premium включён. */
    static boolean activate(Context c, String code) {
        String norm = code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (norm.isEmpty()) return false;
        if (!hashes(c).contains(sha256(SALT + norm))) return false;
        prefs(c).edit().putBoolean("premium", true).apply();
        return true;
    }

    // ---------- дневной счётчик постов ----------

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    static int usedToday(Context c) {
        SharedPreferences p = prefs(c);
        return today().equals(p.getString("day", "")) ? p.getInt("dayCount", 0) : 0;
    }

    static int addToday(Context c) {
        int n = usedToday(c) + 1;
        prefs(c).edit().putString("day", today()).putInt("dayCount", n).apply();
        return n;
    }

    static int leftToday(Context c) {
        return Math.max(0, FREE_DAILY - usedToday(c));
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
