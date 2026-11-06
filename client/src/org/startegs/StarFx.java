/*
 * StarTegs 3.0 — эффекты при отправке: сердечки, конфетти, огонь, молния, звездопад, снег, деньги.
 * Рисуются поверх чата 1,8 секунды и не мешают нажатиям. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Random;

final class StarFx {

    private StarFx() {}

    /** id эффекта → набор символов и «характер» движения: 0 вверх, 1 вниз, 2 взрыв из центра. */
    static Object[] spec(String id) {
        switch (id == null ? "" : id) {
            case "fx_hearts": return new Object[]{new String[]{"❤️", "💖", "💕", "💗"}, 0};
            case "fx_confetti": return new Object[]{new String[]{"🎉", "🎊", "✨", "🟪", "🟨", "🟦"}, 2};
            case "fx_fire": return new Object[]{new String[]{"🔥", "🔥", "💥"}, 0};
            case "fx_bolt": return new Object[]{new String[]{"⚡", "⚡", "✨"}, 2};
            case "fx_stars": return new Object[]{new String[]{"⭐", "🌟", "✨", "💫"}, 1};
            case "fx_snow": return new Object[]{new String[]{"❄️", "❄️", "⛄"}, 1};
            case "fx_money": return new Object[]{new String[]{"💸", "💵", "💰", "🤑"}, 1};
            default: return null;
        }
    }

    static void play(BaseFragment f, String id) {
        try {
            Object[] sp = spec(id);
            if (sp == null || !(f instanceof ChatActivity) || !(f.getFragmentView() instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) f.getFragmentView();
            Burst b = new Burst(root.getContext(), (String[]) sp[0], (Integer) sp[1]);
            root.addView(b, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception ignore) {
        }
    }

    private static final class P {
        float x, y, vx, vy, size, rot, vrot;
        String g;
    }

    private static final class Burst extends View {
        private final ArrayList<P> ps = new ArrayList<>();
        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String[] glyphs;
        private final int mode;
        private long start, last;

        Burst(Context c, String[] glyphs, int mode) {
            super(c);
            this.glyphs = glyphs;
            this.mode = mode;
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        private void spawn(int w, int h) {
            Random r = new Random();
            int n = mode == 2 ? 46 : 34;
            for (int i = 0; i < n; i++) {
                P p = new P();
                p.g = glyphs[r.nextInt(glyphs.length)];
                p.size = AndroidUtilities.dp(16 + r.nextInt(18));
                p.rot = r.nextFloat() * 40 - 20;
                p.vrot = r.nextFloat() * 120 - 60;
                if (mode == 0) { // поднимаются снизу
                    p.x = r.nextFloat() * w;
                    p.y = h + r.nextFloat() * h * 0.3f;
                    p.vx = (r.nextFloat() - 0.5f) * AndroidUtilities.dp(60);
                    p.vy = -AndroidUtilities.dp(380 + r.nextInt(320));
                } else if (mode == 1) { // падают сверху
                    p.x = r.nextFloat() * w;
                    p.y = -r.nextFloat() * h * 0.4f;
                    p.vx = (r.nextFloat() - 0.5f) * AndroidUtilities.dp(80);
                    p.vy = AndroidUtilities.dp(300 + r.nextInt(300));
                } else { // разлетаются от поля ввода
                    p.x = w / 2f;
                    p.y = h - AndroidUtilities.dp(90);
                    double a = Math.PI + r.nextDouble() * Math.PI; // верхняя полуплоскость
                    float sp = AndroidUtilities.dp(420 + r.nextInt(520));
                    p.vx = (float) Math.cos(a) * sp;
                    p.vy = (float) Math.sin(a) * sp;
                }
                ps.add(p);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = System.currentTimeMillis();
            if (start == 0) {
                start = last = now;
                spawn(getWidth(), getHeight());
            }
            float dt = Math.min(0.05f, (now - last) / 1000f);
            last = now;
            float t = (now - start) / 1800f;
            if (t >= 1f) {
                post(() -> {
                    if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
                });
                return;
            }
            int alpha = (int) (255 * (t < 0.75f ? 1f : (1f - t) / 0.25f));
            for (P p : ps) {
                if (mode == 2) p.vy += AndroidUtilities.dp(900) * dt; // гравитация для конфетти
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.rot += p.vrot * dt;
                paint.setTextSize(p.size);
                paint.setAlpha(alpha);
                canvas.save();
                canvas.rotate(p.rot, p.x, p.y);
                canvas.drawText(p.g, p.x, p.y, paint);
                canvas.restore();
            }
            invalidate();
        }
    }
}
