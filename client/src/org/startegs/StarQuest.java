/*
 * StarTegs 3.1 — задание дня: одно простое дело в день (отправить фото, сыграть партию,
 * голосовое с эффектом…), за выполнение бот StarTegs даёт ✦, опыт и 💎. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StarQuest {

    private StarQuest() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    /** ключ, цель, описание */
    static final Object[][] QUESTS = {
            {"photo", 1, "📸 Отправь другу фото или мем"},
            {"game", 1, "🎮 Сыграй с другом в игру в чате"},
            {"voice_fx", 1, "🤖 Запиши голосовое с эффектом"},
            {"sent", 20, "💬 Отправь 20 сообщений"},
            {"fx", 3, "🎉 Отправь 3 сообщения с эффектом"},
            {"share", 1, "📲 Поделись карточкой «Лучшие друзья» или вопросом"},
            {"homework", 1, "📚 Отметь сделанным задание по учёбе"},
            {"voice", 2, "🎙 Отправь 2 голосовых"},
    };

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    static Object[] current() {
        String d = today();
        int h = Math.abs(d.hashCode() * 31 + 7);
        return QUESTS[h % QUESTS.length];
    }

    static int count() {
        return prefs().getInt("q_" + today() + "_" + current()[0], 0);
    }

    static boolean complete() {
        return count() >= (Integer) current()[1];
    }

    static boolean claimed() {
        return prefs().getBoolean("q_claimed_" + today(), false);
    }

    /** Отметить действие; если это задание дня и оно выполнено — тост. */
    public static void progress(String key, int by) {
        if (by <= 0) return;
        Object[] q = current();
        if (!q[0].equals(key)) return;
        boolean was = complete();
        String k = "q_" + today() + "_" + key;
        prefs().edit().putInt(k, prefs().getInt(k, 0) + by).apply();
        if (!was && complete()) {
            AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, "🎯 Задание дня выполнено! Забери награду на экране StarTegs", Toast.LENGTH_LONG).show());
        }
    }

    static String title() {
        Object[] q = current();
        return q[2] + "  " + Math.min(count(), (Integer) q[1]) + "/" + q[1];
    }

    static void claim(Runnable done) {
        if (!complete() || claimed()) return;
        try {
            StarCloud.call(UserConfig.selectedAccount, "reward", new JSONObject().put("code", "dq:" + today()), new StarCloud.Result() {
                @Override
                public void ok(JSONObject res) {
                    prefs().edit().putBoolean("q_claimed_" + today(), true).apply();
                    StarAchieve.bump("quests", 1);
                    Toast.makeText(ApplicationLoader.applicationContext, "🎁 +" + res.optInt("coins") + " ✦  +" + res.optInt("xp") + " XP  +" + res.optInt("crystals") + " 💎", Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                }

                @Override
                public void fail(String why) {
                    if (why.contains("уже")) prefs().edit().putBoolean("q_claimed_" + today(), true).apply();
                    Toast.makeText(ApplicationLoader.applicationContext, why, Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                }
            });
        } catch (Exception ignore) {
        }
    }
}
