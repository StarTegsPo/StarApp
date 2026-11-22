/*
 * StarTegs 6.3: «Переезд из других модов» — где в StarTegs найти привычные функции AyuGram, exteraGram,
 * WhiteGram, Nekogram и др., плюс то, чего нет ни у кого. И размер стикеров (как в exteraGram).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public final class StarMove {

    private StarMove() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static final int[] STICKER_PCT = {60, 75, 90, 100, 115, 130, 150};
    private static float scale = -1;

    /** Из ChatMessageCell: множитель размера стикеров (1 = как в Telegram). */
    public static float stickerScale() {
        if (scale < 0) scale = prefs().getInt("sticker_pct", 100) / 100f;
        return scale;
    }

    static void setStickerPct(int pct) {
        prefs().edit().putInt("sticker_pct", pct).apply();
        scale = pct / 100f;
    }

    /** {откуда, функция, раздел StarTegs (-1 — размер стикеров здесь же, 23 — стили, 25 — оптимизация)}. */
    static final Object[][] MAP = {
            {"AyuGram", "👻 Режим призрака: не читать, не в сети, не печатать", 1},
            {"AyuGram", "🗑 Сохранение удалённых сообщений", 4},
            {"AyuGram", "✏️ История правок «было → стало»", 4},
            {"AyuGram", "📖 Прочитать все чаты одним касанием", 3},
            {"AyuGram", "🧹 Фильтры сообщений по словам", 3},
            {"AyuGram", "🚫 Без рекламы в каналах", 12},
            {"AyuGram", "⭐ Функции Premium", 0},
            {"exteraGram", "🧩 Плагины и свои команды", 8},
            {"exteraGram", "🧸 Размер стикеров", -1},
            {"exteraGram", "🔤 Шрифты, иконки, оформление", 20},
            {"exteraGram", "⌨️ Удобство: тихие часы, ключевые слова, чистые ссылки", 21},
            {"WhiteGram", "🕳 Скрытые чаты и PIN", 2},
            {"WhiteGram", "🛡 Безопасность: устройства, новые входы", 15},
            {"Nekogram", "🌐 Переводчик и нейросеть", 7},
            {"Nekogram", "📤 Отправка: отмена, без звука, подтверждение", 5},
            {"Nekogram", "🖼 Медиа: скачать всё, кружки, истории", 6},
            {"Telegraher", "💾 Резервная копия и очистка", 13},
    };

    static final Object[][] ONLY_US = {
            {"🫧 18 стилей интерфейса с живыми превью", 23},
            {"🚀 Оптимизация с «кольцом здоровья»", 25},
            {"👑 Золотое имя ✦ и рамки, которые видят друзья", 24},
            {"🧠 Память: сообщение в память одним касанием", 4},
            {"🛠 Утилиты: QR, конвертер, пароли, таймер", 22},
            {"🎨 Темы одним касанием и конструктор темы", 20},
    };

    static void open(org.telegram.ui.ActionBar.BaseFragment f, int sec) {
        if (sec == 23) f.presentFragment(new StarSkin.Screen());
        else if (sec == 24) f.presentFragment(new StarGold.Screen());
        else if (sec == 25) f.presentFragment(new StarOptimize.Screen());
        else if (sec >= 0) f.presentFragment(new StarSections.Screen(sec));
    }

    public static class Screen extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "🔁 Переезд из других модов";
        }

        @Override
        public View createView(Context context) {
            View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFF5AA7FF, 0xFF8A5CFF}, listView);
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asShadow("Пользовался AyuGram, exteraGram или другим модом? Тут видно, где в StarTegs то же самое. Нажми — откроется нужное место."));
            items.add(UItem.asButton(1, "🧸 Размер стикеров", Math.round(stickerScale() * 100) + "%"));
            String last = "";
            for (int i = 0; i < MAP.length; i++) {
                String app = (String) MAP[i][0];
                if (!app.equals(last)) {
                    items.add(UItem.asHeader("Было в " + app));
                    last = app;
                }
                int sec = (Integer) MAP[i][2];
                items.add(UItem.asButton(100 + i, (String) MAP[i][1], sec < 0 ? "здесь" : "→ " + StarSections.SECTIONS[sec][1]));
            }
            items.add(UItem.asHeader("✨ Есть только в StarTegs"));
            for (int i = 0; i < ONLY_US.length; i++) items.add(UItem.asButton(300 + i, (String) ONLY_US[i][0], "открыть"));
            items.add(UItem.asShadow("Нет любимой функции? Напиши в боте StarTegs Hub — добавим."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            StarPolish.tap(view);
            if (item.id == 1 || (item.id >= 100 && item.id < 100 + MAP.length && (Integer) MAP[item.id - 100][2] < 0)) {
                if (getParentActivity() == null) return;
                CharSequence[] names = new CharSequence[STICKER_PCT.length];
                for (int i = 0; i < names.length; i++) names[i] = STICKER_PCT[i] + "%" + (STICKER_PCT[i] == 100 ? " — как в Telegram" : "");
                new AlertDialog.Builder(getParentActivity()).setTitle("🧸 Размер стикеров в чатах")
                        .setItems(names, (d, w) -> {
                            setStickerPct(STICKER_PCT[w]);
                            if (listView != null) listView.adapter.update(true);
                        }).show();
                return;
            }
            if (item.id >= 100 && item.id < 100 + MAP.length) open(this, (Integer) MAP[item.id - 100][2]);
            else if (item.id >= 300 && item.id < 300 + ONLY_US.length) open(this, (Integer) ONLY_US[item.id - 300][1]);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
