/*
 * StarTegs 6.2: свои быстрые кнопки на главном экране StarTegs — 5 мест, 16 действий на выбор.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

final class StarQuick {

    private StarQuick() {}

    static final String[][] ACTIONS = {
            {"👻", "Призрак"}, {"✨", "Шрифт"}, {"🪪", "Имя"}, {"🛍", "Магазин"}, {"🐾", "Питомец"},
            {"🎨", "Стиль"}, {"👑", "Gold"}, {"🌙", "Ночь"}, {"🔕", "Тишина"}, {"🛠", "Утилиты"},
            {"✉️", "Удалённые"}, {"🤖", "ИИ"}, {"🔎", "Поиск"}, {"🧠", "Память"}, {"🔒", "Приватность"}, {"🖌", "Дизайн"}, {"🚀", "Ускорить"}, {"👥", "Позвать"},
    };

    private static final int[] DEFAULT = {0, 1, 5, 16, 6};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static int[] slots() {
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            int a = prefs().getInt("quick_" + i, DEFAULT[i]);
            out[i] = a >= 0 && a < ACTIONS.length ? a : DEFAULT[i];
        }
        return out;
    }

    static String[][] buttons() {
        int[] s = slots();
        String[][] b = new String[5][];
        for (int i = 0; i < 5; i++) b[i] = ACTIONS[s[i]];
        return b;
    }

    static void run(BaseFragment f, int slot, Runnable refresh) {
        int a = slots()[Math.max(0, Math.min(4, slot))];
        switch (a) {
            case 0:
                if (!StarMod.requirePro(f, "Режим призрака")) return;
                StarSections.setGhostAll(!StarSections.ghostAll());
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), StarSections.ghostAll()
                        ? "👻 Призрак включён: тебя не видно в сети, «печатает» и «прочитано» не отправляются" : "👻 Призрак выключен", Toast.LENGTH_LONG).show();
                refresh.run();
                break;
            case 1: StarStyle.chooseAuto(f, refresh); break;
            case 2: StarStyle.fancyName(f); break;
            case 3: f.presentFragment(new StarCosmetics.Shop()); break;
            case 4: StarFun.pet(f, refresh); break;
            case 5: f.presentFragment(new StarSkin.Screen()); break;
            case 6: f.presentFragment(new StarGold.Screen()); break;
            case 7: StarNight.choose(f, refresh); break;
            case 8: StarBonus.silence(f, refresh); break;
            case 9: f.presentFragment(new StarSections.Screen(22)); break;
            case 10: f.presentFragment(new StarSections.Screen(4)); break;
            case 11: f.presentFragment(new StarSections.Screen(7)); break;
            case 12: f.presentFragment(new StarSections.Search()); break;
            case 13: f.presentFragment(new StarMemory.Screen()); break;
            case 14: f.presentFragment(new StarSections.Screen(1)); break;
            case 16: f.presentFragment(new StarOptimize.Screen()); break;
            case 17: StarGold.invite(f); break;
            default: f.presentFragment(new StarSections.Screen(20));
        }
    }

    /** Выбрать, что стоит на каждой из 5 кнопок. */
    static void customize(BaseFragment f, Runnable refresh) {
        if (f.getParentActivity() == null) return;
        int[] s = slots();
        CharSequence[] slotNames = new CharSequence[5];
        for (int i = 0; i < 5; i++) slotNames[i] = (i + 1) + ".  " + ACTIONS[s[i]][0] + "  " + ACTIONS[s[i]][1];
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("⚡ Какую кнопку поменять?")
                .setItems(slotNames, (d, slot) -> {
                    CharSequence[] all = new CharSequence[ACTIONS.length];
                    for (int i = 0; i < ACTIONS.length; i++) all[i] = ACTIONS[i][0] + "  " + ACTIONS[i][1];
                    new AlertDialog.Builder(f.getParentActivity())
                            .setTitle("Кнопка " + (slot + 1))
                            .setItems(all, (d2, a) -> {
                                prefs().edit().putInt("quick_" + slot, a).apply();
                                refresh.run();
                            }).show();
                })
                .setNegativeButton("Сбросить", (d, w) -> {
                    SharedPreferences.Editor e = prefs().edit();
                    for (int i = 0; i < 5; i++) e.remove("quick_" + i);
                    e.apply();
                    refresh.run();
                })
                .show();
    }
}
