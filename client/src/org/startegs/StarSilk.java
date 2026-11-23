/*
 * StarTegs 5.6 «Шёлк»: приятность в каждом касании — градиентные шапки экранов StarTegs в цвет
 * раздела, плавное появление списков, пружинящие карточки, мягкие тени, переливающаяся рамка
 * карточки профиля и карточка «Мой день» с кольцами прогресса. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public final class StarSilk {

    private StarSilk() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean barsOn() { return prefs().getBoolean("silk_bars", true); }

    public static boolean motionOn() { return prefs().getBoolean("silk_motion", true); }

    static boolean motion() {
        return motionOn() && StarPro.anim(LiteMode.FLAG_CHAT_BACKGROUND);
    }

    static final int[] PURPLE = {0xFF7B5CFF, 0xFF33A6FF};
    static final int[] PINK = {0xFFFF7EB3, 0xFF8A5CFF};
    static final int[] RED = {0xFFFF6B6B, 0xFFE03A3F};

    static int[] sectionColors(int section) {
        int[] t = StarYou.TILES[Math.max(0, Math.min(section, StarYou.TILES.length - 1))];
        return new int[]{t[1], t[2]};
    }

    // ================================================================ шапка и появление списка

    /** Вызывать в createView после super.createView. */
    static void decorate(BaseFragment f, int[] colors, android.view.View list) {
        try {
            if (barsOn() && f.getActionBar() != null) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, StarSkin.bar(colors)); // 6.2: стиль интерфейса
                f.getActionBar().setBackground(g);
                f.getActionBar().setItemsColor(Color.WHITE, false);
                f.getActionBar().setTitleColor(Color.WHITE);
                f.getActionBar().setItemsBackgroundColor(0x33FFFFFF, false);
            }
            if (list != null) StarNav.attach(f, list); // 6.4: панель вкладок, недавние
            if (list instanceof ViewGroup && motion()) {
                AnimationSet set = new AnimationSet(true);
                TranslateAnimation tr = new TranslateAnimation(0, 0, AndroidUtilities.dp(18), 0);
                AlphaAnimation al = new AlphaAnimation(0f, 1f);
                set.addAnimation(tr);
                set.addAnimation(al);
                set.setDuration(320);
                set.setInterpolator(new DecelerateInterpolator(1.6f));
                LayoutAnimationController lac = new LayoutAnimationController(set, 0.06f);
                ((ViewGroup) list).setLayoutAnimation(lac);
            }
        } catch (Exception ignore) {
        }
    }

    // ================================================================ пружинящее нажатие и тень

    @SuppressLint("ClickableViewAccessibility")
    static void spring(View v) {
        v.setOnTouchListener((view, e) -> {
            if (!motion()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(110).setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });
    }

    /** Мягкая тень по форме фона карточки + пружина. */
    static void card(View v) {
        if (v == null) return;
        v.setElevation(AndroidUtilities.dp(3));
        v.setTranslationZ(0);
        StarSkin.style(v); // 6.2
        spring(v);
    }

    // ================================================================ переливающаяся рамка

    /** Обёртка, вокруг которой медленно бежит радужная рамка (для карточки профиля). */
    static final class Glow extends FrameLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Matrix m = new Matrix();
        private SweepGradient sweep;
        private float lastW, lastH;
        private final long start = System.currentTimeMillis();

        Glow(Context c) {
            super(c);
            setWillNotDraw(false);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(AndroidUtilities.dp(2.5f));
            int pad = AndroidUtilities.dp(3);
            setPadding(pad, pad, pad, pad);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w == 0) return;
            if (sweep == null || w != lastW || h != lastH) {
                sweep = new SweepGradient(w / 2, h / 2, StarSkin.ring(), null);
                lastW = w;
                lastH = h;
            }
            boolean anim = motion();
            m.setRotate(anim ? ((System.currentTimeMillis() - start) / 18f) % 360 : 30, w / 2, h / 2);
            sweep.setLocalMatrix(m);
            p.setShader(sweep);
            float s = p.getStrokeWidth() / 2;
            r.set(s, s, w - s, h - s);
            canvas.drawRoundRect(r, StarSkin.radius(), StarSkin.radius(), p);
            if (anim && isShown()) postInvalidateDelayed(40);
        }
    }

    static View glow(View card) {
        Glow g = new Glow(card.getContext());
        g.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    // ================================================================ «Мой день»: кольца прогресса

    static final class Rings extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float[] values;
        private final String[] labels, icons;
        private final int[][] colors = {{0xFF4FB2FF, 0xFF34D1BF}, {0xFF5EDB76, 0xFFFFD166}, {0xFFFF7EB3, 0xFFB46CFF}};
        private float shown;
        private final long start = System.currentTimeMillis();

        Rings(Context c, float[] values, String[] labels, String[] icons) {
            super(c);
            this.values = values;
            this.labels = labels;
            this.icons = icons;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            tp.setTextAlign(Paint.Align.CENTER);
            shown = motion() ? 0 : 1;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(112));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (shown < 1) {
                shown = Math.min(1f, (System.currentTimeMillis() - start) / 700f);
                postInvalidateOnAnimation();
            }
            float eased = 1 - (1 - shown) * (1 - shown);
            float cell = getWidth() / 3f, rad = AndroidUtilities.dp(30), cy = AndroidUtilities.dp(42);
            p.setStrokeWidth(AndroidUtilities.dp(7));
            for (int i = 0; i < 3; i++) {
                float cx = cell * i + cell / 2;
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                p.setShader(null);
                p.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) & 0x30FFFFFF);
                c.drawArc(r, 0, 360, false, p);
                SweepGradient sg = new SweepGradient(cx, cy, colors[i], null);
                Matrix mm = new Matrix();
                mm.setRotate(-90, cx, cy);
                sg.setLocalMatrix(mm);
                p.setShader(sg);
                c.drawArc(r, -90, 360 * Math.max(0.02f, values[i]) * eased, false, p);
                tp.setTextSize(AndroidUtilities.dp(20));
                tp.setColor(Color.BLACK);
                c.drawText(icons[i], cx, cy + AndroidUtilities.dp(7), tp);
                tp.setTextSize(AndroidUtilities.dp(12));
                tp.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                c.drawText(labels[i], cx, cy + rad + AndroidUtilities.dp(22), tp);
            }
        }
    }

    /** Карточка «Мой день»: вода, привычки, настроение. Нажатие — «Продуктивность». */
    static View dayCard(BaseFragment f) {
        Context ctx = f.getParentActivity();
        float water = Math.min(1f, StarBonus.water() / (float) Math.max(1, StarBonus.waterGoal()));
        JSONArray hs = StarDays.habits();
        int done = 0;
        for (int i = 0; i < hs.length(); i++) if (StarDays.doneOn(hs.optJSONObject(i), StarDays.today())) done++;
        float habits = hs.length() == 0 ? 0 : done / (float) hs.length();
        String mood = StarDays.mood(StarDays.today());
        Rings rings = new Rings(ctx, new float[]{water, habits, mood.isEmpty() ? 0 : 1},
                new String[]{"Вода " + StarBonus.water() + "/" + StarBonus.waterGoal(), "Привычки " + done + "/" + hs.length(), mood.isEmpty() ? "Настроение?" : "Настроение"},
                new String[]{"💧", "🔥", mood.isEmpty() ? "🙂" : mood});
        FrameLayout box = new FrameLayout(ctx);
        int p = AndroidUtilities.dp(10);
        box.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(20));
        box.setBackground(bg);
        box.addView(rings);
        box.setOnClickListener(v -> {
            StarPolish.tap(v);
            f.presentFragment(new StarSections.Screen(14));
        });
        card(box);
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, AndroidUtilities.dp(6), m, AndroidUtilities.dp(6));
        wrap.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }
}
