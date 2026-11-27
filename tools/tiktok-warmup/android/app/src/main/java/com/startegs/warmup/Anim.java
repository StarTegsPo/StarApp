package com.startegs.warmup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Анимации приложения. Все рисуют кадры только пока есть что анимировать
 * и полностью выключаются, если в Android отключены анимации.
 */
final class Anim {

    static boolean enabled(Context c) {
        return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }

    static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /** Число «набегает» от старого значения к новому. */
    static void countTo(final TextView tv, final String prefix, int from, final int to, final String suffix) {
        if (!enabled(tv.getContext()) || from < 0 || from == to) {
            tv.setText(prefix + to + suffix);
            return;
        }
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(Math.min(900, 250 + 60L * Math.abs(to - from)));
        va.setInterpolator(new DecelerateInterpolator(1.6f));
        va.addUpdateListener(a -> tv.setText(prefix + a.getAnimatedValue() + suffix));
        va.start();
    }

    // =====================================================================

    /** Конфетти и сердечки: burst() выстреливает частицы из точки, дальше гравитация. */
    static class Confetti extends View {
        private static class P {
            float x, y, vx, vy, rot, vr, size, life;
            int color, shape;
        }

        private final List<P> ps = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private long last;
        private final float density;

        Confetti(Context c) {
            super(c);
            density = c.getResources().getDisplayMetrics().density;
            setClickable(false);
        }

        void burst(float cx, float cy, int n, int[] colors) {
            if (!enabled(getContext())) return;
            for (int i = 0; i < n; i++) {
                P p = new P();
                p.x = cx;
                p.y = cy;
                double a = rnd.nextDouble() * Math.PI * 2;
                float sp = (300 + rnd.nextFloat() * 900) * density / 2.5f;
                p.vx = (float) Math.cos(a) * sp;
                p.vy = (float) Math.sin(a) * sp - 500 * density / 2.5f;
                p.rot = rnd.nextFloat() * 360;
                p.vr = (rnd.nextFloat() - 0.5f) * 720;
                p.size = (5 + rnd.nextFloat() * 6) * density;
                p.color = colors[rnd.nextInt(colors.length)];
                p.shape = rnd.nextInt(5);   // 0–2 прямоугольник, 3 круг, 4 сердце
                ps.add(p);
            }
            last = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - last) / 1000f);
            last = now;
            float g = 1400 * density / 2.5f;
            for (int i = ps.size() - 1; i >= 0; i--) {
                P p = ps.get(i);
                p.vy += g * dt;
                p.vx *= 0.99f;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.rot += p.vr * dt;
                p.life += dt;
                if (p.y > getHeight() + 40 * density || p.life > 3.5f) {
                    ps.remove(i);
                    continue;
                }
                paint.setColor(p.color);
                paint.setAlpha((int) (255 * Math.max(0, Math.min(1, 3.5f - p.life))));
                c.save();
                c.translate(p.x, p.y);
                c.rotate(p.rot);
                if (p.shape == 3) {
                    c.drawCircle(0, 0, p.size / 2.4f, paint);
                } else if (p.shape == 4) {
                    drawHeart(c, p.size * 1.3f, paint);
                } else {
                    c.drawRect(-p.size / 2, -p.size / 4, p.size / 2, p.size / 4, paint);
                }
                c.restore();
            }
            if (!ps.isEmpty()) postInvalidateOnAnimation();
        }
    }

    static void drawHeart(Canvas c, float s, Paint p) {
        Path h = new Path();
        h.moveTo(0, s * 0.35f);
        h.cubicTo(-s * 0.9f, -s * 0.15f, -s * 0.45f, -s * 0.75f, 0, -s * 0.3f);
        h.cubicTo(s * 0.45f, -s * 0.75f, s * 0.9f, -s * 0.15f, 0, s * 0.35f);
        c.drawPath(h, p);
    }

    // =====================================================================

    /** Кольца, расходящиеся от главной кнопки, пока идёт прогрев. */
    static class Pulse extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final long t0 = SystemClock.uptimeMillis();
        private boolean active;
        private final int color;
        private final float inset;

        Pulse(Context c, int color, float insetPx) {
            super(c);
            this.color = color;
            this.inset = insetPx;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(c, 2));
        }

        void setActive(boolean on) {
            active = on && enabled(getContext());
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!active) return;
            float t = ((SystemClock.uptimeMillis() - t0) % 1800) / 1800f;
            for (int k = 0; k < 2; k++) {
                float f = (t + k * 0.5f) % 1f;
                float grow = inset * f;
                r.set(inset - grow, inset - grow, getWidth() - inset + grow, getHeight() - inset + grow);
                paint.setColor(color);
                paint.setAlpha((int) (200 * (1 - f)));
                float rad = (getHeight() - 2 * inset) / 2f + grow;
                c.drawRoundRect(r, rad, rad, paint);
            }
            postInvalidateOnAnimation();
        }
    }

    // =====================================================================

    /** Иллюстрация онбординга: телефон, лента сама листается, на роликах вспыхивают сердца. */
    static class FeedPhone extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final long t0 = SystemClock.uptimeMillis();
        private final Theme th;
        private final boolean animate;
        private final float d;
        private final int[][] cards = {
                {0xFFFF5F6D, 0xFFFFC371}, {0xFF7B5CFF, 0xFF33A6FF}, {0xFF11998E, 0xFF38EF7D},
                {0xFFFC466B, 0xFF3F5EFB}, {0xFFF7971E, 0xFFFFD200}, {0xFF8E2DE2, 0xFF4A00E0}};

        FeedPhone(Context c, Theme th) {
            super(c);
            this.th = th;
            animate = enabled(c);
            d = c.getResources().getDisplayMetrics().density;
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            float t = animate ? (SystemClock.uptimeMillis() - t0) / 1000f : 0.4f;
            float pw = Math.min(getWidth() * 0.52f, getHeight() * 0.5f), ph = pw * 1.95f;
            if (ph > getHeight() * 0.96f) { ph = getHeight() * 0.96f; pw = ph / 1.95f; }
            float px = (getWidth() - pw) / 2, py = (getHeight() - ph) / 2;
            float rad = pw * 0.14f;

            // тень-свечение и корпус
            paint.setShader(null);
            paint.setColor(Theme.alpha(th.a1, 0x40));
            r.set(px - 10 * d, py - 10 * d, px + pw + 10 * d, py + ph + 10 * d);
            c.drawRoundRect(r, rad + 10 * d, rad + 10 * d, paint);
            paint.setColor(0xFF0B0B0D);
            r.set(px, py, px + pw, py + ph);
            c.drawRoundRect(r, rad, rad, paint);

            // экран: лента, листается каждые 2,2 с
            float sx = px + 6 * d, sy = py + 6 * d, sw = pw - 12 * d, sh = ph - 12 * d;
            c.save();
            Path clip = new Path();
            clip.addRoundRect(new RectF(sx, sy, sx + sw, sy + sh), rad * 0.8f, rad * 0.8f, Path.Direction.CW);
            c.clipPath(clip);
            float cycle = 2.2f;
            int idx = (int) (t / cycle);
            float phase = (t % cycle) / cycle;
            float slide = phase < 0.78f ? 0 : ease((phase - 0.78f) / 0.22f);
            for (int k = 0; k < 2; k++) {
                int[] col = cards[(idx + k) % cards.length];
                float top = sy + sh * (k - slide);
                paint.setShader(new LinearGradient(sx, top, sx + sw, top + sh, col[0], col[1], Shader.TileMode.CLAMP));
                c.drawRect(sx, top, sx + sw, top + sh, paint);
                paint.setShader(null);
                // «подпись» и «кнопки» ролика
                paint.setColor(0x66FFFFFF);
                r.set(sx + sw * 0.08f, top + sh * 0.82f, sx + sw * 0.62f, top + sh * 0.85f);
                c.drawRoundRect(r, 4 * d, 4 * d, paint);
                r.set(sx + sw * 0.08f, top + sh * 0.88f, sx + sw * 0.45f, top + sh * 0.905f);
                c.drawRoundRect(r, 4 * d, 4 * d, paint);
                for (int b = 0; b < 3; b++) c.drawCircle(sx + sw * 0.87f, top + sh * (0.48f + b * 0.11f), sw * 0.05f, paint);
                // сердце-лайк на каждом втором ролике
                if (k == 0 && (idx % 2 == 0) && phase > 0.3f && phase < 0.75f) {
                    float hp = (phase - 0.3f) / 0.45f;
                    float sc = hp < 0.25f ? ease(hp / 0.25f) * 1.25f : 1.25f - 0.25f * Math.min(1, (hp - 0.25f) / 0.2f);
                    paint.setColor(0xFFFF2D55);
                    paint.setAlpha((int) (255 * (hp > 0.8f ? (1 - hp) / 0.2f : 1)));
                    c.save();
                    c.translate(sx + sw / 2, top + sh * 0.42f - hp * 18 * d);
                    drawHeart(c, sw * 0.32f * sc, paint);
                    c.restore();
                    paint.setAlpha(255);
                }
            }
            c.restore();

            // палец, который делает свайп
            if (phase > 0.7f) {
                float fp = Math.min(1, (phase - 0.7f) / 0.3f);
                paint.setColor(0xCCFFFFFF);
                c.drawCircle(sx + sw * 0.5f, sy + sh * (0.8f - 0.5f * ease(fp)), sw * 0.07f, paint);
            }

            // плашка «● прогрев»
            int likes = idx / 2 + 1;
            String pill = "● прогрев  🎬 " + (idx + 7) + "  ❤ " + likes;
            text.setTextSize(sw * 0.075f);
            float tw = text.measureText(pill);
            paint.setShader(new LinearGradient(0, 0, tw, 0, th.a1, th.a2, Shader.TileMode.CLAMP));
            r.set(sx + sw / 2 - tw / 2 - 8 * d, sy + sh * 0.06f, sx + sw / 2 + tw / 2 + 8 * d, sy + sh * 0.06f + sw * 0.14f);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint);
            paint.setShader(null);
            c.drawText(pill, sx + sw / 2, r.centerY() + text.getTextSize() * 0.35f, text);

            if (animate) postInvalidateOnAnimation();
        }

        private static float ease(float x) {
            x = Math.max(0, Math.min(1, x));
            return 1 - (1 - x) * (1 - x) * (1 - x);
        }
    }

    /** Иллюстрация онбординга: большой переключатель сам включается, появляется галочка. */
    static class ToggleShow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final long t0 = SystemClock.uptimeMillis();
        private final Theme th;
        private final boolean animate;
        private final float d;

        ToggleShow(Context c, Theme th) {
            super(c);
            this.th = th;
            animate = enabled(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas c) {
            float t = animate ? ((SystemClock.uptimeMillis() - t0) % 2600) / 2600f : 0.8f;
            float on = t < 0.25f ? 0 : t < 0.45f ? (t - 0.25f) / 0.2f : t < 0.9f ? 1 : 1 - (t - 0.9f) / 0.1f;
            on = on * on * (3 - 2 * on);
            float w = Math.min(getWidth() * 0.6f, 220 * d), h = w * 0.5f;
            float x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;

            // свечение
            paint.setShader(null);
            paint.setColor(Theme.alpha(th.a1, (int) (90 * on)));
            r.set(x - 14 * d, y - 14 * d, x + w + 14 * d, y + h + 14 * d);
            c.drawRoundRect(r, h, h, paint);
            // дорожка
            r.set(x, y, x + w, y + h);
            if (on > 0.01f) {
                paint.setShader(new LinearGradient(x, y, x + w, y + h, th.a1, th.a2, Shader.TileMode.CLAMP));
                paint.setAlpha((int) (255 * Math.max(0.25f, on)));
            } else {
                paint.setColor(th.soft);
            }
            c.drawRoundRect(r, h / 2, h / 2, paint);
            paint.setShader(null);
            paint.setAlpha(255);
            // бегунок
            float pad = h * 0.1f, kr = (h - 2 * pad) / 2;
            float kx = x + pad + kr + (w - 2 * pad - 2 * kr) * on;
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(kx, y + h / 2, kr, paint);
            // галочка в бегунке
            if (on > 0.6f) {
                float p = Math.min(1, (on - 0.6f) / 0.4f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(kr * 0.22f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(th.a1);
                Path ck = new Path();
                ck.moveTo(kx - kr * 0.4f, y + h / 2);
                ck.lineTo(kx - kr * 0.1f, y + h / 2 + kr * 0.3f);
                ck.lineTo(kx + kr * 0.45f, y + h / 2 - kr * 0.35f);
                android.graphics.PathMeasure pm = new android.graphics.PathMeasure(ck, false);
                Path part = new Path();
                pm.getSegment(0, pm.getLength() * p, part, true);
                c.drawPath(part, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (animate) postInvalidateOnAnimation();
        }
    }
}
