/*
 * StarTegs 4.1 — пробный Premium на 3 дня. Один раз на аккаунт Telegram: бот StarTegs (v61)
 * запоминает, кто уже пробовал. После установки мод один раз предлагает попробовать.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StarTrial {

    private StarTrial() {}

    static final int DAYS = 3;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static long until() {
        return prefs().getLong("trial_until", 0);
    }

    /** Идёт пробный период (учитывается в StarMod.isPro). */
    public static boolean active() {
        return until() > System.currentTimeMillis();
    }

    static boolean used() {
        return prefs().getBoolean("trial_used", false);
    }

    static boolean available() {
        return !StarModConfig.ECO_BOT.isEmpty() && !used() && !"pro".equals(StarCloud.tier()) && !"vip".equals(StarCloud.tier());
    }

    static String untilText() {
        return new SimpleDateFormat("d MMMM, HH:mm", new Locale("ru")).format(new Date(until()));
    }

    /** Один раз после установки 4.1 — предложение попробовать (через 4 с после открытия списка чатов). */
    static void maybeOfferOnLaunch() {
        if (!available() || prefs().getBoolean("trial_offered", false)) return;
        prefs().edit().putBoolean("trial_offered", true).apply();
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment f = LaunchActivity.getLastFragment();
            if (f != null && f.getParentActivity() != null) offer(f, null);
        }, 4000);
    }

    static void offer(BaseFragment f, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🎁 Premium StarTegs — " + DAYS + " дня бесплатно")
                .setMessage("Откройте всё сразу: «Ответь за меня» без лимита, поиск по смыслу, призрак, скрытые чаты и замок, "
                        + "все палитры, иконки, рамки и значки, шторку приватности и «отправить, когда появится в сети».\n\n"
                        + "Без привязки карты. Через " + DAYS + " дня всё вернётся к бесплатной версии само.")
                .setPositiveButton("Попробовать", (d, w) -> start(f, done))
                .setNegativeButton("Позже", null)
                .show();
    }

    static void start(BaseFragment f, Runnable done) {
        StarCloud.call(UserConfig.selectedAccount, "trial_start", new JSONObject(), new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                long u = res.optLong("until") * 1000L;
                prefs().edit().putLong("trial_until", u).putBoolean("trial_used", true).apply();
                if (f.getParentActivity() != null) {
                    new AlertDialog.Builder(f.getParentActivity())
                            .setTitle("🎉 Premium включён")
                            .setMessage("Пробный Premium действует до " + untilText() + ". Загляните в StarTegs → «⭐ Premium-функции» и «🧪 Новое».")
                            .setPositiveButton("Отлично", null).show();
                }
                if (done != null) done.run();
            }

            @Override
            public void fail(String why) {
                if (why != null && why.contains("уже")) prefs().edit().putBoolean("trial_used", true).apply();
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            }
        });
    }
}
