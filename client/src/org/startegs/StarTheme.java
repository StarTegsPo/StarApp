/*
 * StarTegs — фирменное оформление: светлая и тёмная темы StarTegs, которые можно
 * включить и выключить. Лицензия: GNU GPL v2 или новее.
 *
 * Темы лежат в assets (их делает client/patches/brand_theme.py из встроенных тем
 * Telegram). При включении запоминаем прежние дневную и ночную темы, чтобы при
 * выключении вернуть именно их.
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

    static final String DAY = "StarTegs";
    static final String NIGHT = "StarTegs Night";

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean isOurs(Theme.ThemeInfo t) {
        return t != null && t.getKey() != null && t.getKey().startsWith(DAY);
    }

    /** Включён ли фирменный дизайн сейчас (по реально активной теме). */
    public static boolean isActive() {
        try {
            return isOurs(Theme.getActiveTheme());
        } catch (Exception e) {
            return false;
        }
    }

    public static void setActive(BaseFragment fragment, boolean enable) {
        try {
            // Экраны перекрашивает LaunchActivity по событию needSetDayNightTheme (как при обычной смене темы)
            if (enable) enable(); else disable();
        } catch (Exception e) {
            FileLog.e(e);
        }
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

    private static void enable() throws Exception {
        boolean dark = Theme.getActiveTheme().isDark();
        Theme.ThemeInfo prevDay = Theme.getCurrentTheme();
        Theme.ThemeInfo prevNight = Theme.getCurrentNightTheme();
        SharedPreferences.Editor e = prefs().edit();
        if (prevDay != null && !isOurs(prevDay)) e.putString("prev_day_theme", prevDay.getKey());
        if (prevNight != null && !isOurs(prevNight)) e.putString("prev_night_theme", prevNight.getKey());
        e.apply();

        // applyThemeFile регистрирует тему в списке тем Telegram и применяет её
        Theme.ThemeInfo night = Theme.applyThemeFile(fromAssets("startegs_night.attheme"), NIGHT, null, false);
        Theme.ThemeInfo day = Theme.applyThemeFile(fromAssets("startegs_day.attheme"), DAY, null, false);
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
