/*
 * StarTegs 3.1 — достижения и уровень в моде. Счётчики ведутся на телефоне,
 * за открытые достижения бот StarTegs (v60) один раз начисляет монеты ✦, опыт и иногда 💎.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public final class StarAchieve {

    private StarAchieve() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    /** код награды в боте, счётчик, цель, эмодзи, название, очки */
    static final Object[][] LIST = {
            {"ach_sent1000", "sent", 1000, "💬", "Болтун: 1000 сообщений", 30},
            {"ach_streak7", "streak", 7, "🔥", "Неделя стрика", 20},
            {"ach_streak30", "streak", 30, "⚡", "Месяц стрика", 50},
            {"ach_streak100", "streak", 100, "💎", "100 дней стрика", 150},
            {"ach_games10", "games", 10, "🎮", "10 игровых ходов", 15},
            {"ach_games50", "games", 50, "🕹", "50 игровых ходов", 40},
            {"ach_win10", "wins", 10, "🏆", "10 побед и отгадок", 30},
            {"ach_ask10", "asks", 10, "🤫", "10 анонимных вопросов", 20},
            {"ach_friends3", "friends", 3, "💞", "Топ-3 друзей собран", 10},
            {"ach_voice_fx", "voice_fx", 1, "🤖", "Голосовое с эффектом", 10},
            {"ach_study7", "homework", 7, "📚", "7 заданий сделано", 25},
            {"ach_first_frame", "frames", 1, "🖼", "Первая рамка", 10},
            {"ach_showcase", "showcase", 1, "🌟", "Витрина заполнена", 10},
            {"ach_quests7", "quests", 7, "🎯", "7 заданий дня", 40},
    };

    static int get(String counter) {
        return prefs().getInt("ach_c_" + counter, 0);
    }

    /** Увеличить счётчик (streak — записывается максимум). */
    public static void bump(String counter, int by) {
        if (by <= 0) return;
        prefs().edit().putInt("ach_c_" + counter, get(counter) + by).apply();
        check();
    }

    static void setMax(String counter, int value) {
        if (value > get(counter)) {
            prefs().edit().putInt("ach_c_" + counter, value).apply();
            check();
        }
    }

    static boolean done(Object[] a) {
        return get((String) a[1]) >= (Integer) a[2];
    }

    static boolean claimed(Object[] a) {
        return prefs().getBoolean("ach_claimed_" + a[0], false);
    }

    static int points() {
        int p = 0;
        for (Object[] a : LIST) if (done(a)) p += (Integer) a[5];
        p += get("quests") * 5;
        return p;
    }

    static int level() {
        return 1 + (int) Math.sqrt(points() / 10.0);
    }

    /** {очков на текущем уровне, очков до следующего} */
    static int[] levelProgress() {
        int lv = level(), p = points();
        int from = (lv - 1) * (lv - 1) * 10, to = lv * lv * 10;
        return new int[]{p - from, to - from};
    }

    private static boolean checking;

    /** Новые открытые достижения: тост и награда от бота. */
    static void check() {
        if (checking) return;
        for (Object[] a : LIST) {
            if (done(a) && !prefs().getBoolean("ach_toast_" + a[0], false)) {
                prefs().edit().putBoolean("ach_toast_" + a[0], true).apply();
                AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, "🏆 Достижение: " + a[3] + " " + a[4] + " — забери награду в StarTegs", Toast.LENGTH_LONG).show());
            }
        }
    }

    static void claim(Object[] a, Runnable done) {
        if (!done(a) || claimed(a)) return;
        checking = true;
        try {
            StarCloud.call(UserConfig.selectedAccount, "reward", new JSONObject().put("code", a[0]), new StarCloud.Result() {
                @Override
                public void ok(JSONObject res) {
                    checking = false;
                    prefs().edit().putBoolean("ach_claimed_" + a[0], true).apply();
                    Toast.makeText(ApplicationLoader.applicationContext, "🎁 +" + res.optInt("coins") + " ✦  +" + res.optInt("xp") + " XP"
                            + (res.optInt("crystals") > 0 ? "  +" + res.optInt("crystals") + " 💎" : ""), Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                }

                @Override
                public void fail(String why) {
                    checking = false;
                    if (why.contains("уже")) prefs().edit().putBoolean("ach_claimed_" + a[0], true).apply();
                    Toast.makeText(ApplicationLoader.applicationContext, why, Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                }
            });
        } catch (Exception e) {
            checking = false;
        }
    }

    /** Экран «Достижения». */
    public static class Screen extends UniversalFragment {
        private static final int ID_ACH = 1000;

        @Override
        protected CharSequence getTitle() {
            return "🏆 Достижения";
        }

        @Override
        public void onResume() {
            super.onResume();
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            int[] lp = levelProgress();
            items.add(UItem.asHeader("Уровень " + level() + " · " + points() + " очков"));
            int filled = lp[1] == 0 ? 0 : Math.round(12f * lp[0] / lp[1]);
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < 12; i++) bar.append(i < filled ? "🟪" : "⬜");
            items.add(UItem.asShadow(bar + "\nДо следующего уровня: " + (lp[1] - lp[0]) + " очков. Очки — за достижения и задания дня."));
            items.add(UItem.asHeader("Достижения"));
            for (int i = 0; i < LIST.length; i++) {
                Object[] a = LIST[i];
                int c = Math.min(get((String) a[1]), (Integer) a[2]);
                String value = claimed(a) ? "✓" : done(a) ? "🎁 забрать" : c + "/" + a[2];
                items.add(UItem.asButton(ID_ACH + i, (done(a) ? a[3] : "🔒") + "  " + a[4], value));
            }
            items.add(UItem.asShadow("Награды (монеты ✦, опыт и иногда 💎) начисляет бот @" + StarModConfig.ECO_BOT + " — один раз за каждое достижение."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - ID_ACH;
            if (i < 0 || i >= LIST.length) return;
            Object[] a = LIST[i];
            if (done(a) && !claimed(a)) claim(a, () -> {
                if (listView != null) listView.adapter.update(true);
            });
            else if (!done(a) && getParentActivity() != null) {
                Toast.makeText(getParentActivity(), a[3] + " " + a[4] + ": " + Math.min(get((String) a[1]), (Integer) a[2]) + " из " + a[2], Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
