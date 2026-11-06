/*
 * StarTegs 2.2 — красивые графики для статистики: столбцы с градиентом, которые «вырастают»
 * при открытии, и кольцо «я / собеседники». Цвета берутся из текущей темы. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

final class StarChart {

    private StarChart() {}

    private static int accent() {
        return Theme.getColor(Theme.key_chats_actionBackground);
    }

    private static int accent2() {
        float[] h = new float[3];
        android.graphics.Color.colorToHSV(accent(), h);
        h[0] = (h[0] + 40) % 360;
        return android.graphics.Color.HSVToColor(h);
    }

    /** Столбчатая диаграмма: подписи снизу, значения над столбцами. */
    static View bars(Context ctx, String[] labels, int[] values) {
        return new Bars(ctx, labels, values);
    }

    /** Кольцо: доля первого значения. */
    static View ring(Context ctx, int mine, int total, String caption) {
        return new Ring(ctx, mine, total, caption);
    }

    private static final class Bars extends View {
        private final String[] labels;
        private final int[] values;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;

        Bars(Context c, String[] labels, int[] values) {
            super(c);
            this.labels = labels;
            this.values = values;
            text.setTextSize(AndroidUtilities.dp(11));
            text.setTextAlign(Paint.Align.CENTER);
            setMinimumHeight(AndroidUtilities.dp(170));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(170));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(700);
            a.setInterpolator(new DecelerateInterpolator(1.6f));
            a.addUpdateListener(v -> {
                progress = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int n = values.length;
            if (n == 0) return;
            int max = 1;
            for (int v : values) max = Math.max(max, v);
            float pad = AndroidUtilities.dp(16), top = AndroidUtilities.dp(18), bottom = getHeight() - AndroidUtilities.dp(22);
            float slot = (getWidth() - pad * 2) / n, w = slot * 0.62f;
            bar.setShader(new LinearGradient(0, top, 0, bottom, accent2(), accent(), Shader.TileMode.CLAMP));
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            for (int i = 0; i < n; i++) {
                float cx = pad + slot * i + slot / 2;
                float hgt = (bottom - top) * values[i] / max * progress;
                r.set(cx - w / 2, bottom - Math.max(hgt, AndroidUtilities.dp(3)), cx + w / 2, bottom);
                canvas.drawRoundRect(r, AndroidUtilities.dp(6), AndroidUtilities.dp(6), bar);
                text.setColor(textColor);
                canvas.drawText(labels[i], cx, getHeight() - AndroidUtilities.dp(6), text);
                if (values[i] > 0 && progress > 0.9f) {
                    text.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    canvas.drawText(values[i] >= 1000 ? (values[i] / 100) / 10f + "k" : String.valueOf(values[i]), cx, r.top - AndroidUtilities.dp(4), text);
                }
            }
        }
    }

    private static final class Ring extends View {
        private final int mine, total;
        private final String caption;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG), small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;

        Ring(Context c, int mine, int total, String caption) {
            super(c);
            this.mine = mine;
            this.total = Math.max(1, total);
            this.caption = caption;
            track.setStyle(Paint.Style.STROKE);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            track.setStrokeWidth(AndroidUtilities.dp(14));
            arc.setStrokeWidth(AndroidUtilities.dp(14));
            big.setTextSize(AndroidUtilities.dp(26));
            big.setFakeBoldText(true);
            big.setTextAlign(Paint.Align.CENTER);
            small.setTextSize(AndroidUtilities.dp(12));
            small.setTextAlign(Paint.Align.CENTER);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(190));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(900);
            a.setInterpolator(new DecelerateInterpolator(1.4f));
            a.addUpdateListener(v -> {
                progress = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = AndroidUtilities.dp(150), cx = getWidth() / 2f, cy = getHeight() / 2f;
            r.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            track.setColor((Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) & 0x00FFFFFF) | 0x33000000);
            canvas.drawArc(r, 0, 360, false, track);
            arc.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, accent(), accent2(), Shader.TileMode.CLAMP));
            canvas.drawArc(r, -90, 360f * mine / total * progress, false, arc);
            big.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.drawText(Math.round(100f * mine / total * progress) + "%", cx, cy + AndroidUtilities.dp(4), big);
            small.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            canvas.drawText(caption, cx, cy + AndroidUtilities.dp(24), small);
        }
    }
}
