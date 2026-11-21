/*
 * StarTegs 5.3 «Полировка»: «Что нового» после обновления, совет дня на главном экране,
 * полоска прогресса уровня, лёгкая отдача на касаниях. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Calendar;

public final class StarPolish {

    private StarPolish() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ================================================================ лёгкая отдача на касаниях

    public static boolean hapticsOn() { return prefs().getBoolean("polish_haptic", true); }

    static void tap(View v) {
        if (v == null || !hapticsOn()) return;
        try {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
    }

    // ================================================================ полоска уровня

    /** Градиентная полоска «до следующего уровня» с плавным заполнением. */
    static final class LevelBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float shown;
        private final float target;

        LevelBar(Context c) {
            super(c);
            int[] pr = StarAchieve.levelProgress();
            target = pr[1] <= 0 ? 0 : Math.max(0.04f, Math.min(1f, pr[0] / (float) pr[1]));
            if (StarPro.anim(LiteMode.FLAG_CHAT_BACKGROUND)) {
                ValueAnimator va = ValueAnimator.ofFloat(0f, target);
                va.setDuration(900);
                va.setStartDelay(250);
                va.setInterpolator(new DecelerateInterpolator());
                va.addUpdateListener(a -> {
                    shown = (float) a.getAnimatedValue();
                    invalidate();
                });
                addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        va.start();
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        va.cancel();
                    }
                });
            } else shown = target;
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth(), rad = h / 2f;
            p.setShader(null);
            p.setColor(0x33FFFFFF);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(new LinearGradient(0, 0, w, 0, 0xFFFFE066, 0xFFFF7EB3, Shader.TileMode.CLAMP));
            r.set(0, 0, Math.max(h, w * shown), h);
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    static View levelBar(Context ctx) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        int[] pr = StarAchieve.levelProgress();
        col.addView(new LevelBar(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(6)));
        TextView t = StarYou.text(ctx, "До уровня " + (StarAchieve.level() + 1) + ": " + Math.max(0, pr[1] - pr[0]) + " очк.", 11, 0xCCFFFFFF, false);
        t.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        col.addView(t);
        return col;
    }

    // ================================================================ совет дня

    /** {эмодзи, текст, раздел для кнопки (-1 — без кнопки)}. */
    static final Object[][] TIPS = {
            {"🧠", "Двойной тап по сообщению может сразу отправлять его в «Память».", 4},
            {"👻", "Призрак умеет включаться сам — например, на время уроков.", 1},
            {"🎨", "Тема может подстроиться под цвет твоей аватарки или обоев.", 20},
            {"🗑", "Удалённое сообщение помечается временем удаления — и «до прочтения», если ты не успел его увидеть.", 4},
            {"🎱", "Напиши в чате .ball вопрос — шар судьбы ответит за тебя.", 8},
            {"🌙", "Тёплый ночной фильтр бережёт глаза — включи его по расписанию.", 20},
            {"📂", "Папки «Личные», «Группы», «Каналы» и «Боты» создаются в один тап.", 21},
            {"🔔", "Ключевые слова: узнай, когда в заглушённой группе напишут твоё имя.", 21},
            {"📤", "Удерживай карточку профиля — получишь красивую картинку для сторис.", 20},
            {"🤗", ".hug Имя, .pat Имя, .dance Имя — ролевые действия прямо в чате.", 8},
            {"⌨️", "Написал не на той раскладке? В меню ⋮ чата есть «Исправить раскладку».", 21},
            {"🧹", "Ссылки, которые ты отправляешь, очищаются от меток слежки автоматически.", 21},
            {"🎯", "Напиши .dart, .basket или .slot — Telegram покажет анимированную игру.", 8},
            {"📊", ".poll Куда идём? | Кино | Пицца — настоящий опрос одной строкой.", 8},
            {"🌈", "Дневник настроения: отмечай день эмодзи и смотри календарь месяца.", 14},
            {"🔥", "Привычки со стриками: отмечай каждый день и не прерывай серию.", 14},
            {"⏳", "Обратный отсчёт: сколько дней до ДР, каникул или концерта.", 14},
            {"🧾", ".split 1500 3 — StarTegs разделит счёт на всех.", 8},
    };

    static int tipIndex() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % TIPS.length;
    }

    static View tipCard(BaseFragment f, int section) {
        Context ctx = f.getParentActivity();
        Object[] t = TIPS[tipIndex()];
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = AndroidUtilities.dp(14);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setStroke(AndroidUtilities.dp(1.5f), Theme.getColor(Theme.key_featuredStickers_addButton) & 0x66FFFFFF);
        bg.setCornerRadius(AndroidUtilities.dp(18));
        card.setBackground(bg);
        card.addView(StarYou.text(ctx, (String) t[0], 30, 0xFF000000, false));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(StarYou.text(ctx, "💡 Совет дня", 12, Theme.getColor(Theme.key_featuredStickers_addButton), true));
        col.addView(StarYou.text(ctx, (String) t[1], 14, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = AndroidUtilities.dp(12);
        card.addView(col, lp);
        int sec = (Integer) t[2];
        StarSilk.card(card); // 5.6
        if (sec >= 0) card.setOnClickListener(v -> {
            tap(v);
            f.presentFragment(new StarSections.Screen(sec));
        });
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, AndroidUtilities.dp(6), m, AndroidUtilities.dp(2));
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    // ================================================================ «Что нового»

    static final String[][] NEWS = {
            {"🎨", "Цветные шапки", "Каждый экран StarTegs — в цвет своего раздела: плавный градиент вместо серой полосы."},
            {"✨", "Плавные анимации", "Списки мягко выплывают, карточки пружинят под пальцем, у карточек — нежные тени."},
            {"🌈", "Живая рамка профиля", "Вокруг твоей карточки на главном экране медленно бежит радужная рамка."},
            {"⭕", "«Мой день»", "Кольца прогресса на главном: вода, привычки и настроение за сегодня — в одном взгляде."},
            {"🎲", "А ещё в прошлых версиях", "100+ команд (.help), дневник настроения, привычки, обратный отсчёт, 20 тем и 17 фонов чата."},
    };

    /** Из StarPlus.init: после обновления до новой версии (если знакомство уже пройдено). */
    static void maybeNews() {
        String v = StarModConfig.MOD_VERSION;
        String seen = prefs().getString("polish_news_seen", "");
        if (v.equals(seen)) return;
        if (!StarYou.welcomeDone()) { // новый пользователь — ему покажут знакомство
            prefs().edit().putString("polish_news_seen", v).apply();
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated() || StarPrivacy.decoy()) return;
                BaseFragment top = LaunchActivity.getLastFragment();
                if (top == null || top.getParentActivity() == null || top instanceof News) return;
                prefs().edit().putString("polish_news_seen", v).apply();
                top.presentFragment(new News());
            } catch (Exception ignore) {
            }
        }, 3000);
    }

    public static class News extends UniversalFragment {
        @Override
        public android.view.View createView(android.content.Context context) {
            android.view.View v = super.createView(context);
            StarSilk.decorate(this, StarSilk.PINK, listView); // 5.6: цветная шапка и плавное появление
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        @Override
        protected CharSequence getTitle() {
            return "✨ Что нового в StarTegs " + StarModConfig.MOD_VERSION;
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            for (String[] n : NEWS) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int p = AndroidUtilities.dp(16);
                row.setPadding(p, AndroidUtilities.dp(10), p, AndroidUtilities.dp(10));
                TextView e = StarYou.text(ctx, n[0], 30, 0xFF000000, false);
                e.setGravity(Gravity.CENTER);
                GradientDrawable bubble = new GradientDrawable();
                bubble.setShape(GradientDrawable.OVAL);
                bubble.setColor(Theme.getColor(Theme.key_featuredStickers_addButton) & 0x22FFFFFF);
                e.setBackground(bubble);
                row.addView(e, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), AndroidUtilities.dp(54)));
                LinearLayout col = new LinearLayout(ctx);
                col.setOrientation(LinearLayout.VERTICAL);
                col.addView(StarYou.text(ctx, n[1], 16, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true));
                col.addView(StarYou.text(ctx, n[2], 14, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = AndroidUtilities.dp(14);
                row.addView(col, lp);
                items.add(UItem.asCustom(row));
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(1, "Открыть StarTegs ⭐", "").accent());
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == 1) presentFragment(new StarSections.Index(), true);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
