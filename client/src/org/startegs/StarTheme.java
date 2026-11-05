/*
 * StarTegs — фирменное оформление: несколько палитр StarTegs (светлая и тёмная тема
 * у каждой), которые можно включить, сменить или выключить. Лицензия: GNU GPL v2 или новее.
 *
 * Темы лежат в assets (их делает client/patches/brand_theme.py из встроенных тем
 * Telegram). При первом включении запоминаем прежние дневную и ночную темы, чтобы
 * при выключении вернуть именно их.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class StarTheme {

    private StarTheme() {}

    /** id палитры, название. id совпадает с именем файлов startegs_<id>_day/night.attheme. */
    static final String[][] PALETTES = {
            {"violet", "Фиолет"},
            {"neon", "Неон"},
            {"gold", "Золото"},
            {"emerald", "Изумруд"},
            {"sunset", "Закат"},
    };

    private static final String PREFIX = "StarTegs";

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    /** Имя темы в списке тем Telegram. Фиолетовая называется просто «StarTegs», как в версии 1.1. */
    private static String themeName(int palette, boolean night) {
        String base = palette == 0 ? PREFIX : PREFIX + " " + PALETTES[palette][1];
        return night ? base + " Night" : base;
    }

    private static boolean isOurs(Theme.ThemeInfo t) {
        return t != null && t.getKey() != null && t.getKey().startsWith(PREFIX);
    }

    /** Включён ли фирменный дизайн сейчас (по реально активной теме). */
    public static boolean isActive() {
        return activePalette() != -1;
    }

    /** Номер активной палитры, -2 — своя палитра, -1 — стоит не наша тема. */
    public static int activePalette() {
        try {
            Theme.ThemeInfo t = Theme.getActiveTheme();
            if (!isOurs(t)) return -1;
            String key = t.getKey().replace(".attheme", "").replace(" Night", "");
            if (key.startsWith(CUSTOM)) return -2; // своя палитра
            for (int i = PALETTES.length - 1; i > 0; i--) {
                if (key.equals(themeName(i, false))) return i;
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Включить палитру (0..N-1) или выключить фирменный дизайн (-1). */
    public static void setPalette(BaseFragment fragment, int palette) {
        if (palette > 0 && !StarMod.requirePro(fragment, "Палитры StarTegs")) return;
        try {
            // Экраны перекрашивает LaunchActivity по событию needSetDayNightTheme, как при обычной смене темы
            if (palette < 0) disable(); else enable(palette);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Совместимость с 1.1: просто вкл/выкл фирменной фиолетовой темы. */
    public static void setActive(BaseFragment fragment, boolean enable) {
        setPalette(fragment, enable ? 0 : -1);
    }

    private static File fromAssets(String asset) throws Exception {
        File out = new File(ApplicationLoader.applicationContext.getCacheDir(), asset);
        try (InputStream in = ApplicationLoader.applicationContext.getAssets().open(asset);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out;
    }

    private static void enable(int palette) throws Exception {
        boolean dark = Theme.getActiveTheme().isDark();
        Theme.ThemeInfo prevDay = Theme.getCurrentTheme();
        Theme.ThemeInfo prevNight = Theme.getCurrentNightTheme();
        SharedPreferences.Editor e = prefs().edit();
        if (prevDay != null && !isOurs(prevDay)) e.putString("prev_day_theme", prevDay.getKey());
        if (prevNight != null && !isOurs(prevNight)) e.putString("prev_night_theme", prevNight.getKey());
        e.apply();

        String id = PALETTES[palette][0];
        // applyThemeFile регистрирует тему в списке тем Telegram и применяет её
        Theme.ThemeInfo night = Theme.applyThemeFile(fromAssets("startegs_" + id + "_night.attheme"), themeName(palette, true), null, false);
        Theme.ThemeInfo day = Theme.applyThemeFile(fromAssets("startegs_" + id + "_day.attheme"), themeName(palette, false), null, false);
        if (day == null || night == null) return;
        Theme.setCurrentNightTheme(night);
        saveLast(day, night);
        post(dark ? night : day, dark);
    }

    private static void disable() {
        boolean dark = Theme.getActiveTheme().isDark();
        Theme.ThemeInfo day = Theme.getTheme(prefs().getString("prev_day_theme", "Blue"));
        Theme.ThemeInfo night = Theme.getTheme(prefs().getString("prev_night_theme", "Night"));
        if (day == null || isOurs(day)) day = Theme.getTheme("Blue");
        if (night == null || isOurs(night)) night = Theme.getTheme("Night");
        if (day == null || night == null) return;
        Theme.setCurrentNightTheme(night);
        saveLast(day, night);
        post(dark ? night : day, dark);
    }

    private static void saveLast(Theme.ThemeInfo day, Theme.ThemeInfo night) {
        ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit()
                .putString("lastDayTheme", day.getKey())
                .putString("lastDarkTheme", night.getKey())
                .apply();
    }

    private static void post(Theme.ThemeInfo theme, boolean night) {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, night, null, -1);
    }

    // ---------------------------------------------------------------- свои палитры

    /** Готовые оттенки для своей палитры: название, оттенок в градусах. */
    static final Object[][] HUES = {
            {"🔴 Красный", 356}, {"🟠 Оранжевый", 24}, {"🟡 Янтарь", 42}, {"🟢 Лайм", 88}, {"🌿 Зелёный", 135},
            {"🌊 Бирюза", 172}, {"🔷 Голубой", 196}, {"🔵 Синий", 216}, {"🟣 Индиго", 236}, {"💜 Сирень", 270},
            {"🌺 Пурпур", 296}, {"🌸 Розовый", 330},
    };

    private static final String CUSTOM = PREFIX + " Свой";

    /** Перекраска как в brand_theme.py: синие акценты Telegram → выбранный оттенок. */
    private static int recolor(int argb, float hue) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(argb, hsv);
        if (hsv[1] < 0.18f || hsv[0] < 185 || hsv[0] > 235) return argb;
        // жёлто-зелёные оттенки при той же яркости светлее синего — затемняем, чтобы белый текст читался
        float kv = hue >= 35 && hue <= 175 ? 0.80f : hue > 175 && hue < 205 ? 0.9f : hue > 240 && hue < 280 ? 1.05f : 0.95f;
        hsv[0] = hue;
        hsv[1] = Math.min(1f, hsv[1] * 0.85f);
        hsv[2] = Math.min(1f, hsv[2] * kv);
        return (argb & 0xFF000000) | (android.graphics.Color.HSVToColor(hsv) & 0x00FFFFFF);
    }

    private static File buildCustom(String asset, float hue, String out) throws Exception {
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(ApplicationLoader.applicationContext.getAssets().open(asset), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) {
                String v = line.substring(eq + 1).trim();
                try {
                    int c = v.startsWith("#") ? (int) Long.parseLong(v.substring(1), 16) : (int) Long.parseLong(v);
                    if (v.startsWith("#") && v.length() == 7) c |= 0xFF000000;
                    line = line.substring(0, eq + 1) + recolor(c, hue);
                } catch (NumberFormatException ignore) {
                }
            }
            sb.append(line).append('\n');
        }
        r.close();
        File f = new File(ApplicationLoader.applicationContext.getCacheDir(), out);
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(sb.toString().getBytes("UTF-8"));
        }
        return f;
    }

    public static boolean isCustomActive() {
        try {
            Theme.ThemeInfo t = Theme.getActiveTheme();
            return t != null && t.getKey() != null && t.getKey().startsWith(CUSTOM);
        } catch (Exception e) {
            return false;
        }
    }

    /** Выбор своего цвета; тема собирается на телефоне из встроенных тем Telegram. */
    public static void chooseCustom(BaseFragment fragment) {
        if (fragment.getParentActivity() == null || !StarMod.requirePro(fragment, "Свои палитры")) return;
        CharSequence[] items = new CharSequence[HUES.length];
        for (int i = 0; i < HUES.length; i++) items[i] = (String) HUES[i][0];
        new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🎨 Своя палитра")
                .setItems(items, (d, which) -> {
                    try {
                        float hue = ((Integer) HUES[which][1]).floatValue();
                        boolean dark = Theme.getActiveTheme().isDark();
                        Theme.ThemeInfo prevDay = Theme.getCurrentTheme();
                        Theme.ThemeInfo prevNight = Theme.getCurrentNightTheme();
                        SharedPreferences.Editor e = prefs().edit();
                        if (prevDay != null && !isOurs(prevDay)) e.putString("prev_day_theme", prevDay.getKey());
                        if (prevNight != null && !isOurs(prevNight)) e.putString("prev_night_theme", prevNight.getKey());
                        e.putInt("custom_hue", (int) hue).apply();
                        Theme.ThemeInfo night = Theme.applyThemeFile(buildCustom("night.attheme", hue, "startegs_custom_night.attheme"), CUSTOM + " Night", null, false);
                        Theme.ThemeInfo day = Theme.applyThemeFile(buildCustom("day.attheme", hue, "startegs_custom_day.attheme"), CUSTOM, null, false);
                        if (day == null || night == null) return;
                        Theme.setCurrentNightTheme(night);
                        saveLast(day, night);
                        post(dark ? night : day, dark);
                    } catch (Exception ex) {
                        FileLog.e(ex);
                    }
                })
                .show();
    }
}
