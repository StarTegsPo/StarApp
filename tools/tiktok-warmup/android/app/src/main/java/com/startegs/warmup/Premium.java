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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    static final String BOT_URL = "https://t.me/Vibe2Scool_bot";
    static final String BUY_URL = BOT_URL + "?start=premium";

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

    static final long DAY = 86_400_000L;
    static final long TRIAL_MS = DAY;          // пробный Premium — 24 часа с первого запуска
    static final long MONTH_MS = 30 * DAY;

    /** Месячные коды (assets/codes_m.sha): «yyyyMM хеш» — месяц, в котором код продан. */
    private static Map<String, String> monthHashes;

    private static synchronized Map<String, String> monthHashes(Context c) {
        if (monthHashes != null) return monthHashes;
        Map<String, String> map = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("codes_m.sha"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) map.put(parts[1], parts[0]);
            }
        } catch (IOException ignored) { }
        monthHashes = map;
        return map;
    }

    static boolean isActive(Context c) {
        return lifetime(c) || monthLeftMs(c) > 0 || trialLeftMs(c) > 0;
    }

    static boolean lifetime(Context c) {
        return prefs(c).getBoolean("premium", false);
    }

    static long monthLeftMs(Context c) {
        return Math.max(0, prefs(c).getLong("premiumUntil", 0) - System.currentTimeMillis());
    }

    /** Остаток пробного периода; отсчёт с первого запуска приложения. */
    static long trialLeftMs(Context c) {
        SharedPreferences p = prefs(c);
        long first = p.getLong("firstRun", 0);
        if (first == 0) {
            first = System.currentTimeMillis();
            p.edit().putLong("firstRun", first).apply();
        }
        return Math.max(0, TRIAL_MS - (System.currentTimeMillis() - first));
    }

    /** "life", "month", "trial" или "free". */
    static String kind(Context c) {
        if (lifetime(c)) return "life";
        if (monthLeftMs(c) > 0) return "month";
        if (trialLeftMs(c) > 0) return "trial";
        return "free";
    }

    static final int INVALID = 0, LIFETIME = 1, MONTH = 2, USED = 3, EXPIRED = 4;

    /** Активирует код: навсегда (STAR-…) или на 30 дней (STRM-…). */
    static int activateResult(Context c, String code) {
        String norm = code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (norm.isEmpty()) return INVALID;
        String h = sha256(SALT + norm);
        if (hashes(c).contains(h)) {
            prefs(c).edit().putBoolean("premium", true).apply();
            return LIFETIME;
        }
        String month = monthHashes(c).get(h);
        if (month == null) return INVALID;
        SharedPreferences p = prefs(c);
        Set<String> used = new HashSet<>(p.getStringSet("usedMonthCodes", new HashSet<>()));
        if (used.contains(h)) return USED;
        // месячный код можно активировать в месяце покупки или в следующем
        String now = new SimpleDateFormat("yyyyMM", Locale.ROOT).format(new Date());
        if (!now.equals(month) && !now.equals(nextMonth(month))) return EXPIRED;
        used.add(h);
        long until = Math.max(System.currentTimeMillis(), p.getLong("premiumUntil", 0)) + MONTH_MS;
        p.edit().putLong("premiumUntil", until).putStringSet("usedMonthCodes", used).apply();
        return MONTH;
    }

    static boolean activate(Context c, String code) {
        int r = activateResult(c, code);
        return r == LIFETIME || r == MONTH;
    }

    static String nextMonth(String yyyymm) {
        int y = Integer.parseInt(yyyymm.substring(0, 4)), m = Integer.parseInt(yyyymm.substring(4));
        m++;
        if (m > 12) { m = 1; y++; }
        return String.format(Locale.ROOT, "%04d%02d", y, m);
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
