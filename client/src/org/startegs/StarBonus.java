/*
 * StarTegs 5.5 «Сотня» (часть 2): шаблоны «О себе», цитата дня, счётчик воды, список желаний,
 * «Чем заняться, когда скучно», «Тишина на час». Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
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
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

public final class StarBonus {

    private StarBonus() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_days", Context.MODE_PRIVATE);
    }

    // ================================================================ шаблоны «О себе»

    static final String[] BIOS = {
            "✨ живу на вайбе", "🌙 ночной житель · ☕ кофе по расписанию", "🎧 музыка громче мыслей", "📚 учусь · 🎮 играю · 😴 сплю",
            "🌸 мягкая сила", "⚡ не баг, а фича", "🦋 меняюсь к лучшему", "🍕 пицца > всё", "🐱 котик внутри",
            "🎨 рисую свой мир", "🚀 мечтаю по-крупному", "💫 главный герой своей истории", "🌊 спокойствие и чай",
            "🔥 не сдаюсь", "🖤 тише воды", "🌈 радуга после дождя", "🎤 пою в душе на бис", "🏀 спорт · друзья · свобода",
            "📸 ловлю моменты", "🌿 медленно, но верно",
    };

    public static void chooseBio(BaseFragment f) {
        if (f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity()).setTitle("✍️ Красивое «О себе»").setItems(BIOS, (d, w) ->
                new AlertDialog.Builder(f.getParentActivity()).setTitle(BIOS[w])
                        .setMessage("Поставить в «О себе» в профиле? Это видят все.")
                        .setPositiveButton("Поставить", (d2, w2) -> StarStyle.updateProfile(f.getCurrentAccount(), null, BIOS[w], () ->
                                Toast.makeText(f.getParentActivity(), "✅ «О себе» обновлено", Toast.LENGTH_SHORT).show()))
                        .setNeutralButton("Копировать", (d2, w2) -> {
                            AndroidUtilities.addToClipboard(BIOS[w]);
                            Toast.makeText(f.getParentActivity(), "Скопировано", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Отмена", null).show()).show();
    }

    // ================================================================ цитата дня

    static final String[] QUOTES = {
            "«Всё получится, если не сдаваться»", "«Маленькие шаги — тоже шаги»", "«Будь тем, кто делает чей-то день лучше»",
            "«Ошибки — это доказательство, что ты пытаешься»", "«Сегодня — лучший день, чтобы начать»", "«Ты сильнее, чем кажешься»",
            "«Делай, что любишь, и любовь вернётся»", "«Мир меняют те, кто не боится быть странным»", "«Отдых — тоже часть пути»",
            "«Сравнивай себя только с собой вчерашним»", "«Улыбка заразна — поделись ею»", "«Мечты не работают, пока не работаешь ты»",
    };

    static View quoteCard(Context ctx) {
        String q = QUOTES[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % QUOTES.length];
        TextView t = StarYou.text(ctx, "💬 " + q, 15, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        int p = AndroidUtilities.dp(16);
        t.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFF7EB3, 0xFF7B5CFF});
        bg.setCornerRadius(AndroidUtilities.dp(18));
        t.setBackground(bg);
        StarSilk.card(t); // 5.6
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, AndroidUtilities.dp(4), m, AndroidUtilities.dp(4));
        wrap.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    // ================================================================ вода

    static int water() {
        return prefs().getInt("water_" + StarDays.today(), 0);
    }

    static int waterGoal() {
        return prefs().getInt("water_goal", 8);
    }

    public static String waterTitle() {
        int w = water(), g = waterGoal();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(g, 12); i++) b.append(i < w ? "💧" : "◽");
        return w + "/" + g + " " + b;
    }

    public static void addWater(BaseFragment f) {
        int w = water() + 1;
        prefs().edit().putInt("water_" + StarDays.today(), w).apply();
        if (f.getParentActivity() != null) {
            Toast.makeText(f.getParentActivity(), w == waterGoal() ? "🎉 Норма воды на сегодня выполнена!" : "💧 +1 стакан · " + w + " из " + waterGoal(), Toast.LENGTH_SHORT).show();
        }
        if (w == waterGoal()) StarAchieve.bump("water", 1);
    }

    public static void resetWater() {
        prefs().edit().putInt("water_" + StarDays.today(), 0).apply();
    }

    // ================================================================ «Чем заняться, когда скучно»

    static final String[] BORED = {
            "🎨 Нарисуй свой день в трёх смайликах и отправь другу", "📸 Устрой фотосессию своему завтраку", "🎵 Составь плейлист на настроение",
            "📚 Прочитай 10 страниц любой книги", "🧹 Разбери галерею — удали 20 лишних фото", "🏃 Сделай 20 приседаний прямо сейчас",
            "✍️ Запиши три вещи, за которые ты благодарен(на)", "🎮 Сыграй с другом в .rps или .vs", "🍳 Приготовь что-то новое",
            "🌿 Выйди на 15 минут погулять", "📝 Придумай историю: .story поможет начать", "💌 Напиши тёплое сообщение тому, с кем давно не общался(ась)",
            "🧠 Выучи 5 слов на английском", "🎤 Спой любимую песню голосовым", "🧩 Загадай другу загадку: .riddle",
    };

    public static void bored(BaseFragment f) {
        if (f.getParentActivity() == null) return;
        String idea = BORED[new Random().nextInt(BORED.length)];
        new AlertDialog.Builder(f.getParentActivity()).setTitle("🥱 Скучно? Попробуй:").setMessage(idea)
                .setPositiveButton("Ещё идея", (d, w) -> bored(f)).setNegativeButton("Спасибо!", null).show();
    }

    // ================================================================ тишина на час

    public static void silence(BaseFragment f, Runnable done) {
        if (f.getParentActivity() == null) return;
        String[] t = {"На 30 минут", "На 1 час", "На 2 часа", "На 4 часа", "До утра (08:00)", "❌ Выключить"};
        int[] mins = {30, 60, 120, 240, -1, 0};
        new AlertDialog.Builder(f.getParentActivity()).setTitle("🔕 Тишина: уведомления без звука и вибрации").setItems(t, (d, w) -> {
            long until;
            if (mins[w] == 0) until = 0;
            else if (mins[w] < 0) {
                Calendar c = Calendar.getInstance();
                if (c.get(Calendar.HOUR_OF_DAY) >= 8) c.add(Calendar.DAY_OF_MONTH, 1);
                c.set(Calendar.HOUR_OF_DAY, 8);
                c.set(Calendar.MINUTE, 0);
                until = c.getTimeInMillis();
            } else until = System.currentTimeMillis() + mins[w] * 60_000L;
            StarX.setSilenceUntil(until);
            Toast.makeText(f.getParentActivity(), until == 0 ? "🔔 Звук уведомлений снова включён" : "🔕 Тишина включена", Toast.LENGTH_SHORT).show();
            if (done != null) done.run();
        }).show();
    }

    // ================================================================ список желаний

    static JSONArray wishes() {
        try {
            return new JSONArray(prefs().getString("wishes", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static class Wishlist extends UniversalFragment {
        @Override
        public android.view.View createView(android.content.Context context) {
            android.view.View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFFFF6FA0, 0xFFE8336F}, listView); // 5.6: цветная шапка и плавное появление
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        private static final int ID_ADD = 1, ID_ITEM = 1000;

        @Override
        protected CharSequence getTitle() {
            return "🎁 Список желаний";
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            JSONArray a = wishes();
            if (a.length() == 0 && getParentActivity() != null) {
                items.add(UItem.asCustom(StarGlow.empty(getParentActivity(), "🎁", "Список желаний пуст", "Запиши, о чём мечтаешь: подарки, места, цели. Сбылось — отметь галочкой ✨")));
            }
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                items.add(UItem.asCheck(ID_ITEM + i, (o.optBoolean("done") ? "✅ " : "✨ ") + o.optString("t")).setChecked(o.optBoolean("done")));
            }
            items.add(UItem.asButton(ID_ADD, R.drawable.msg_add, "Добавить желание", ""));
            items.add(UItem.asShadow("Нажми — «сбылось». Удерживай — удалить или поделиться списком."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            StarPolish.tap(view);
            Activity act = getParentActivity();
            if (act == null) return;
            if (item.id == ID_ADD) {
                EditText in = new EditText(act);
                in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                in.setHint("Например: наушники, поездка к морю");
                FrameLayout box = new FrameLayout(act);
                int p = AndroidUtilities.dp(24);
                box.setPadding(p, 0, p, 0);
                box.addView(in);
                new AlertDialog.Builder(act).setTitle("🎁 Новое желание").setView(box).setPositiveButton("Добавить", (d, w) -> {
                    String t = in.getText().toString().trim();
                    if (t.isEmpty()) return;
                    try {
                        JSONArray a = wishes();
                        a.put(new JSONObject().put("t", t.length() > 80 ? t.substring(0, 80) : t).put("done", false));
                        prefs().edit().putString("wishes", a.toString()).apply();
                    } catch (Exception ignore) {
                    }
                    refresh();
                }).setNegativeButton("Отмена", null).show();
                return;
            }
            int i = item.id - ID_ITEM;
            JSONArray a = wishes();
            JSONObject o = a.optJSONObject(i);
            if (o == null) return;
            try {
                boolean done = !o.optBoolean("done");
                o.put("done", done);
                prefs().edit().putString("wishes", a.toString()).apply();
                if (done) Toast.makeText(act, "🎉 Ура, желание сбылось!", Toast.LENGTH_SHORT).show();
            } catch (Exception ignore) {
            }
            refresh();
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - ID_ITEM;
            JSONArray a = wishes();
            Activity act = getParentActivity();
            if (i < 0 || i >= a.length() || act == null) return false;
            new AlertDialog.Builder(act).setTitle(a.optJSONObject(i).optString("t"))
                    .setItems(new CharSequence[]{"📤 Поделиться всем списком", "🗑 Удалить"}, (d, w) -> {
                        if (w == 0) {
                            StringBuilder b = new StringBuilder("🎁 Мой список желаний:\n");
                            for (int k = 0; k < a.length(); k++) b.append(a.optJSONObject(k).optBoolean("done") ? "✅ " : "✨ ").append(a.optJSONObject(k).optString("t")).append('\n');
                            act.startActivity(android.content.Intent.createChooser(new android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, b.toString().trim()), "Список желаний"));
                        } else {
                            JSONArray out = new JSONArray();
                            for (int k = 0; k < a.length(); k++) if (k != i) out.put(a.opt(k));
                            prefs().edit().putString("wishes", out.toString()).apply();
                            refresh();
                        }
                    }).show();
            return true;
        }
    }
}
