/*
 * StarTegs 5.4 «Сотня»: дневник настроения с календарём месяца, трекер привычек со стриками
 * и обратный отсчёт до событий цветными карточками. Всё хранится только на телефоне.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class StarDays {

    private StarDays() {}

    private static final Locale RU = new Locale("ru");

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_days", Context.MODE_PRIVATE);
    }

    static String dayKey(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    static String today() {
        return dayKey(Calendar.getInstance());
    }

    private static JSONArray arr(String k) {
        try {
            return new JSONArray(prefs().getString(k, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void put(String k, JSONArray a) {
        prefs().edit().putString(k, a.toString()).apply();
    }

    private static EditText input(Activity a, String hint, String value) {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setHint(hint);
        if (value != null) e.setText(value);
        return e;
    }

    private static FrameLayout pad(Activity a, View v) {
        FrameLayout box = new FrameLayout(a);
        int p = AndroidUtilities.dp(24);
        box.setPadding(p, 0, p, 0);
        box.addView(v);
        return box;
    }

    // ================================================================ дневник настроения

    static final String[] MOODS = {"🤩", "😊", "🙂", "😐", "😔", "😢", "😡", "😴"};
    static final String[] MOOD_NAMES = {"Супер", "Хорошо", "Норм", "Так себе", "Грустно", "Плохо", "Злюсь", "Устал(а)"};

    static String mood(String day) {
        return prefs().getString("mood_" + day, "");
    }

    static void setMood(String day, String m) {
        prefs().edit().putString("mood_" + day, m).apply();
    }

    static int moodStreak() {
        Calendar c = Calendar.getInstance();
        int n = 0;
        while (!mood(dayKey(c)).isEmpty() && n < 3650) {
            n++;
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return n;
    }

    /** Календарь текущего месяца: в каждом дне — эмодзи настроения. */
    static final class MonthView extends View {
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        MonthView(Context c) {
            super(c);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, (int) (width / 7f * 6 + AndroidUtilities.dp(22)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cell = getWidth() / 7f, top = AndroidUtilities.dp(22);
            String[] wd = {"пн", "вт", "ср", "чт", "пт", "сб", "вс"};
            tp.setTextSize(AndroidUtilities.dp(11));
            tp.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            for (int i = 0; i < 7; i++) canvas.drawText(wd[i], cell * i + cell / 2, AndroidUtilities.dp(14), tp);
            Calendar c = Calendar.getInstance();
            int todayDay = c.get(Calendar.DAY_OF_MONTH);
            c.set(Calendar.DAY_OF_MONTH, 1);
            int shift = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int d = 1; d <= days; d++) {
                int pos = shift + d - 1, col = pos % 7, row = pos / 7;
                float cx = cell * col + cell / 2, cy = top + cell * row + cell / 2;
                c.set(Calendar.DAY_OF_MONTH, d);
                String m = mood(dayKey(c));
                if (d == todayDay) {
                    bg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton) & 0x33FFFFFF);
                    r.set(cx - cell / 2 + 4, cy - cell / 2 + 4, cx + cell / 2 - 4, cy + cell / 2 - 4);
                    canvas.drawRoundRect(r, AndroidUtilities.dp(10), AndroidUtilities.dp(10), bg);
                }
                if (!m.isEmpty()) {
                    tp.setTextSize(cell * 0.5f);
                    canvas.drawText(m, cx, cy + cell * 0.18f, tp);
                } else {
                    tp.setTextSize(AndroidUtilities.dp(13));
                    tp.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    canvas.drawText(String.valueOf(d), cx, cy + AndroidUtilities.dp(5), tp);
                }
            }
        }
    }

    public static class Mood extends UniversalFragment {
        @Override
        public android.view.View createView(android.content.Context context) {
            android.view.View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFFFF7EB3, 0xFFFFAE52}, listView); // 5.6: цветная шапка и плавное появление
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        @Override
        protected CharSequence getTitle() {
            return "🌈 Дневник настроения";
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            String now = mood(today());
            items.add(UItem.asHeader(now.isEmpty() ? "Как ты сегодня?" : "Сегодня: " + now + " · подряд дней: " + moodStreak()));
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
            for (int i = 0; i < MOODS.length; i++) {
                final String m = MOODS[i];
                TextView t = StarYou.text(ctx, m, 28, Color.BLACK, false);
                t.setGravity(Gravity.CENTER);
                if (m.equals(now)) {
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(Theme.getColor(Theme.key_featuredStickers_addButton) & 0x44FFFFFF);
                    t.setBackground(g);
                }
                t.setOnClickListener(v -> {
                    StarPolish.tap(v);
                    setMood(today(), m);
                    StarAchieve.bump("mood", 1);
                    refresh();
                });
                row.addView(t, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1f));
            }
            items.add(UItem.asCustom(row));
            items.add(UItem.asHeader(new SimpleDateFormat("LLLL yyyy", RU).format(new Date())));
            FrameLayout wrap = new FrameLayout(ctx);
            wrap.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            wrap.addView(new MonthView(ctx), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            items.add(UItem.asCustom(wrap));
            items.add(UItem.asShadow("Отмечай настроение каждый день — календарь заполнится эмодзи. Видишь только ты."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    // ================================================================ привычки

    static JSONArray habits() { return arr("habits"); }

    static boolean doneOn(JSONObject h, String day) {
        JSONArray d = h.optJSONArray("d");
        if (d == null) return false;
        for (int i = 0; i < d.length(); i++) if (day.equals(d.optString(i))) return true;
        return false;
    }

    static int streak(JSONObject h) {
        Calendar c = Calendar.getInstance();
        if (!doneOn(h, dayKey(c))) c.add(Calendar.DAY_OF_MONTH, -1); // сегодня ещё можно успеть
        int n = 0;
        while (doneOn(h, dayKey(c)) && n < 3650) {
            n++;
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return n;
    }

    static final String[][] HABIT_IDEAS = {{"💧", "Пить воду"}, {"📚", "Читать 10 минут"}, {"🏃", "Зарядка"}, {"😴", "Спать до 23:00"},
            {"📵", "Час без телефона"}, {"🧘", "Медитация"}, {"🇬🇧", "Английский"}, {"🎸", "Практика инструмента"}};

    public static class Habits extends UniversalFragment {
        @Override
        public android.view.View createView(android.content.Context context) {
            android.view.View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFF5EDB76, 0xFF2EAE4C}, listView); // 5.6: цветная шапка и плавное появление
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        private static final int ID_ADD = 1, ID_ITEM = 1000;

        @Override
        protected CharSequence getTitle() {
            return "✅ Привычки";
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            JSONArray a = habits();
            String t = today();
            if (a.length() == 0 && getParentActivity() != null) {
                items.add(UItem.asCustom(StarGlow.empty(getParentActivity(), "🌱", "Заведи первую привычку", "Маленькие шаги каждый день → большой результат. Отмечай галочкой и копи 🔥 стрик.")));
            }
            for (int i = 0; i < a.length(); i++) {
                JSONObject h = a.optJSONObject(i);
                int s = streak(h);
                items.add(UItem.asCheck(ID_ITEM + i, h.optString("e") + "  " + h.optString("t") + (s > 0 ? "   🔥 " + s : "")).setChecked(doneOn(h, t)));
            }
            items.add(UItem.asButton(ID_ADD, org.telegram.messenger.R.drawable.msg_add, "Добавить привычку", ""));
            items.add(UItem.asShadow("Нажми — отметить сегодня. Удерживай — удалить. Стрик 🔥 растёт, пока не пропускаешь дни."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            StarPolish.tap(view);
            if (item.id == ID_ADD) {
                add();
                return;
            }
            int i = item.id - ID_ITEM;
            JSONArray a = habits();
            JSONObject h = a.optJSONObject(i);
            if (h == null) return;
            try {
                JSONArray d = h.optJSONArray("d");
                if (d == null) h.put("d", d = new JSONArray());
                String t = today();
                if (doneOn(h, t)) {
                    JSONArray out = new JSONArray();
                    for (int k = 0; k < d.length(); k++) if (!t.equals(d.optString(k))) out.put(d.optString(k));
                    h.put("d", out);
                } else {
                    d.put(t);
                    while (d.length() > 400) d.remove(0);
                    StarAchieve.bump("habit", 1);
                    int s = streak(h);
                    if (s == 7 || s == 30 || s == 100) Toast.makeText(getParentActivity(), "🔥 " + s + " дней подряд! Так держать!", Toast.LENGTH_LONG).show();
                }
                put("habits", a);
            } catch (Exception ignore) {
            }
            refresh();
        }

        private void add() {
            Activity act = getParentActivity();
            if (act == null) return;
            String[] opts = new String[HABIT_IDEAS.length + 1];
            for (int i = 0; i < HABIT_IDEAS.length; i++) opts[i] = HABIT_IDEAS[i][0] + " " + HABIT_IDEAS[i][1];
            opts[HABIT_IDEAS.length] = "✏️ Своя привычка";
            new AlertDialog.Builder(act).setTitle("Новая привычка").setItems(opts, (d, w) -> {
                if (w < HABIT_IDEAS.length) save(HABIT_IDEAS[w][0], HABIT_IDEAS[w][1]);
                else {
                    EditText in = input(act, "Например: учить 5 слов", null);
                    new AlertDialog.Builder(act).setTitle("✏️ Своя привычка").setView(pad(act, in))
                            .setPositiveButton("Добавить", (d2, w2) -> {
                                String t = in.getText().toString().trim();
                                if (!t.isEmpty()) save("⭐", t);
                            }).setNegativeButton("Отмена", null).show();
                }
            }).show();
        }

        private void save(String e, String t) {
            try {
                JSONArray a = habits();
                a.put(new JSONObject().put("e", e).put("t", t.length() > 60 ? t.substring(0, 60) : t).put("d", new JSONArray()));
                put("habits", a);
            } catch (Exception ignore) {
            }
            refresh();
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - ID_ITEM;
            JSONArray a = habits();
            if (i < 0 || i >= a.length() || getParentActivity() == null) return false;
            new AlertDialog.Builder(getParentActivity()).setTitle("Удалить привычку?").setMessage(a.optJSONObject(i).optString("t"))
                    .setPositiveButton("Удалить", (d, w) -> {
                        JSONArray out = new JSONArray();
                        for (int k = 0; k < a.length(); k++) if (k != i) out.put(a.opt(k));
                        put("habits", out);
                        refresh();
                    }).setNegativeButton("Отмена", null).show();
            return true;
        }
    }

    // ================================================================ обратный отсчёт

    static JSONArray events() { return arr("events"); }

    static final int[][] EVENT_COLORS = {{0xFFFF6FA0, 0xFFE8336F}, {0xFF4FB2FF, 0xFF2A7DE1}, {0xFF5EDB76, 0xFF2EAE4C},
            {0xFFFFAE52, 0xFFF0781D}, {0xFFB46CFF, 0xFF7B4DFF}, {0xFF34D1BF, 0xFF16A394}};

    /** Дней до даты (dd.MM — ближайшая в будущем, dd.MM.yyyy — точная). */
    static long daysLeft(String date) {
        try {
            String[] p = date.split("\\.");
            Calendar now = Calendar.getInstance(), c = Calendar.getInstance();
            int d = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]) - 1;
            c.set(p.length > 2 ? Integer.parseInt(p[2]) : now.get(Calendar.YEAR), m, d, 0, 0, 0);
            for (Calendar k : new Calendar[]{now}) {
                k.set(Calendar.HOUR_OF_DAY, 0);
                k.set(Calendar.MINUTE, 0);
                k.set(Calendar.SECOND, 0);
            }
            if (p.length <= 2 && c.before(now)) c.add(Calendar.YEAR, 1);
            return Math.round((c.getTimeInMillis() - now.getTimeInMillis()) / 86400000.0);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    static View eventCard(Context ctx, JSONObject e, int i) {
        long left = daysLeft(e.optString("d"));
        int[] col = EVENT_COLORS[i % EVENT_COLORS.length];
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = AndroidUtilities.dp(16);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, col);
        bg.setCornerRadius(AndroidUtilities.dp(18));
        card.setBackground(bg);
        card.addView(StarYou.text(ctx, e.optString("e", "⭐"), 32, Color.WHITE, false));
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(StarYou.text(ctx, e.optString("t"), 17, Color.WHITE, true));
        c.addView(StarYou.text(ctx, e.optString("d"), 13, 0xDDFFFFFF, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = AndroidUtilities.dp(12);
        card.addView(c, lp);
        String big = left == Long.MIN_VALUE ? "?" : left == 0 ? "Сегодня!" : left < 0 ? "прошло" : String.valueOf(left);
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        r.addView(StarYou.text(ctx, big, left > 0 ? 30 : 18, Color.WHITE, true));
        if (left > 0) r.addView(StarYou.text(ctx, StarCmds.plural(left, "день", "дня", "дней"), 12, 0xDDFFFFFF, false));
        card.addView(r);
        StarSilk.card(card); // 5.6
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, AndroidUtilities.dp(5), m, AndroidUtilities.dp(5));
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    public static class Countdown extends UniversalFragment {
        @Override
        public android.view.View createView(android.content.Context context) {
            android.view.View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFFFFAE52, 0xFFF0781D}, listView); // 5.6: цветная шапка и плавное появление
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        private static final int ID_ADD = 1;

        @Override
        protected CharSequence getTitle() {
            return "⏳ Обратный отсчёт";
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            JSONArray a = events();
            JSONObject ny = new JSONObject();
            try {
                ny.put("e", "🎄").put("t", "Новый год").put("d", "01.01");
            } catch (Exception ignore) {
            }
            items.add(UItem.asCustom(eventCard(ctx, ny, 4)));
            for (int i = 0; i < a.length(); i++) {
                View v = eventCard(ctx, a.optJSONObject(i), i);
                final int k = i;
                v.setOnLongClickListener(x -> {
                    new AlertDialog.Builder(ctx).setTitle("Удалить событие?").setMessage(a.optJSONObject(k).optString("t"))
                            .setPositiveButton("Удалить", (d, w) -> {
                                JSONArray out = new JSONArray();
                                for (int j = 0; j < a.length(); j++) if (j != k) out.put(a.opt(j));
                                put("events", out);
                                refresh();
                            }).setNegativeButton("Отмена", null).show();
                    return true;
                });
                items.add(UItem.asCustom(v));
            }
            items.add(UItem.asButton(ID_ADD, org.telegram.messenger.R.drawable.msg_add, "Добавить событие", ""));
            items.add(UItem.asShadow("Дни рождения, каникулы, концерты, экзамены. Дата без года (05.06) повторяется каждый год. Удерживай карточку — удалить."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id != ID_ADD) return;
            Activity act = getParentActivity();
            if (act == null) return;
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            EditText t = input(act, "Название: ДР Маши, каникулы…", null);
            EditText d = input(act, "Дата: 05.06 или 01.09.2027", null);
            d.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
            box.addView(t);
            box.addView(d);
            new AlertDialog.Builder(act).setTitle("⏳ Новое событие").setView(pad(act, box)).setPositiveButton("Добавить", (dd, w) -> {
                String title = t.getText().toString().trim(), date = d.getText().toString().trim();
                if (title.isEmpty() || daysLeft(date) == Long.MIN_VALUE) {
                    Toast.makeText(act, "Нужно название и дата вида 05.06", Toast.LENGTH_SHORT).show();
                    return;
                }
                String low = title.toLowerCase(RU);
                String e = low.contains("др") || low.contains("рожд") ? "🎂" : low.contains("канику") || low.contains("отпуск") ? "🏖" : low.contains("экзам") || low.contains("огэ") || low.contains("егэ") ? "📚"
                        : low.contains("концерт") ? "🎤" : low.contains("поезд") || low.contains("полёт") ? "✈️" : "⭐";
                try {
                    JSONArray a = events();
                    a.put(new JSONObject().put("e", e).put("t", title).put("d", date));
                    put("events", a);
                } catch (Exception ignore) {
                }
                refresh();
            }).setNegativeButton("Отмена", null).show();
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
