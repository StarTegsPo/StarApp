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
        return activePalette() >= 0;
    }

    /** Номер активной палитры или -1, если стоит не наша тема. */
    public static int activePalette() {
        try {
            Theme.ThemeInfo t = Theme.getActiveTheme();
            if (!isOurs(t)) return -1;
            String key = t.getKey().replace(".attheme", "").replace(" Night", "");
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
}
