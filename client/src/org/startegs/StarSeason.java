/*
 * StarTegs — сезонные эффекты над списком чатов: снег, листья, лепестки, светлячки.
 * Включается пользователем; не перехватывает нажатия. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

public final class StarSeason {

    private StarSeason() {}

    /** 0 — выключено, 1 — авто по сезону, 2 — снег, 3 — листья, 4 — лепестки, 5 — светлячки. */
    public static final String[] MODES = {"Выключено", "Авто по времени года", "❄️ Снег", "🍂 Листопад", "🌸 Лепестки", "✨ Светлячки"};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static int mode() {
        return prefs().getInt("season", 0);
    }

    static void setMode(int m) {
        prefs().edit().putInt("season", m).apply();
        for (SeasonView v : views) v.restart();
    }

    /** Какой эффект показывать сейчас (с учётом «авто»). */
    static int effective() {
        int m = mode();
        if (m != 1) return m;
        int month = Calendar.getInstance().get(Calendar.MONTH); // 0 — январь
        if (month == 11 || month <= 1) return 2;
        if (month <= 4) return 4;
        if (month <= 7) return 5;
        return 3;
    }

    private static final ArrayList<SeasonView> views = new ArrayList<>();

    /** Вызывается в конце DialogsActivity.createView. */
    public static void attach(ViewGroup root) {
        try {
            if (root == null) return;
            SeasonView v = new SeasonView(root.getContext());
            root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception ignore) {
        }
    }

    private static final class P {
        float x, y, vx, vy, size, rot, vrot, phase;
        String glyph;
        int alpha;
    }

    static final class SeasonView extends View {
        private final ArrayList<P> ps = new ArrayList<>();
        private final Random rnd = new Random();
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint glyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private long last;
        private int kind = -1;

        SeasonView(Context c) {
            super(c);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            glyphPaint.setTextAlign(Paint.Align.CENTER);
        }

        void restart() {
            ps.clear();
            kind = -1;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            views.add(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            views.remove(this);
        }

        private P spawn(int w, int h, boolean anywhere) {
            P p = new P();
            p.x = rnd.nextFloat() * w;
            p.y = anywhere ? rnd.nextFloat() * h : -AndroidUtilities.dp(20);
            p.phase = rnd.nextFloat() * 6.28f;
            p.rot = rnd.nextFloat() * 360;
            p.vrot = (rnd.nextFloat() - 0.5f) * 90;
            switch (kind) {
                case 2: // снег
                    p.size = AndroidUtilities.dp(1.5f + rnd.nextFloat() * 2.5f);
                    p.vy = AndroidUtilities.dp(25 + rnd.nextFloat() * 35);
                    p.vx = AndroidUtilities.dp((rnd.nextFloat() - 0.5f) * 15);
                    p.alpha = 140 + rnd.nextInt(100);
                    break;
                case 3: // листья
                    p.glyph = rnd.nextBoolean() ? "🍂" : (rnd.nextBoolean() ? "🍁" : "🍃");
                    p.size = AndroidUtilities.dp(14 + rnd.nextFloat() * 8);
                    p.vy = AndroidUtilities.dp(30 + rnd.nextFloat() * 30);
                    p.vx = AndroidUtilities.dp((rnd.nextFloat() - 0.5f) * 30);
                    p.alpha = 200;
                    break;
                case 4: // лепестки
                    p.glyph = rnd.nextInt(4) == 0 ? "🌼" : "🌸";
                    p.size = AndroidUtilities.dp(11 + rnd.nextFloat() * 6);
                    p.vy = AndroidUtilities.dp(22 + rnd.nextFloat() * 22);
                    p.vx = AndroidUtilities.dp((rnd.nextFloat() - 0.5f) * 25);
                    p.alpha = 190;
                    break;
                default: // светлячки
                    if (!anywhere) p.y = rnd.nextFloat() * h;
                    p.size = AndroidUtilities.dp(2 + rnd.nextFloat() * 2);
                    p.vy = AndroidUtilities.dp((rnd.nextFloat() - 0.5f) * 12);
                    p.vx = AndroidUtilities.dp((rnd.nextFloat() - 0.5f) * 12);
                    p.alpha = 255;
            }
            return p;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int k = effective();
            if (k == 0) {
                ps.clear();
                return;
            }
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            if (k != kind) {
                kind = k;
                ps.clear();
                int n = k == 2 ? 45 : k == 5 ? 22 : 16;
                for (int i = 0; i < n; i++) ps.add(spawn(w, h, true));
                last = 0;
            }
            long now = System.currentTimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            boolean dark = Theme.isCurrentThemeDark();
            for (int i = 0; i < ps.size(); i++) {
                P p = ps.get(i);
                p.phase += dt * 1.5f;
                p.x += (p.vx + (float) Math.sin(p.phase) * AndroidUtilities.dp(kind == 5 ? 6 : 12)) * dt;
                p.y += p.vy * dt;
                p.rot += p.vrot * dt;
                boolean out = p.y > h + AndroidUtilities.dp(24) || p.x < -AndroidUtilities.dp(30) || p.x > w + AndroidUtilities.dp(30)
                        || (kind == 5 && (p.y < -AndroidUtilities.dp(10)));
                if (out) {
                    ps.set(i, spawn(w, h, kind == 5));
                    continue;
                }
                if (kind == 2) {
                    dot.setColor(dark ? Color.WHITE : 0xFF9DB4D6);
                    dot.setAlpha(p.alpha);
                    canvas.drawCircle(p.x, p.y, p.size, dot);
                } else if (kind == 5) {
                    int a = (int) (120 + 135 * (0.5f + 0.5f * Math.sin(p.phase * 2)));
                    dot.setColor(dark ? 0xFFFFE27A : 0xFFFFB300);
                    dot.setAlpha(a / 3);
                    canvas.drawCircle(p.x, p.y, p.size * 3, dot);
                    dot.setAlpha(a);
                    canvas.drawCircle(p.x, p.y, p.size, dot);
                } else {
                    glyphPaint.setTextSize(p.size);
                    glyphPaint.setAlpha(p.alpha);
                    canvas.save();
                    canvas.rotate(p.rot, p.x, p.y);
                    canvas.drawText(p.glyph, p.x, p.y, glyphPaint);
                    canvas.restore();
                }
            }
            postInvalidateOnAnimation();
        }
    }
}
