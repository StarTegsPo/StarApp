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

    /** Для снимка (отчёт картинкой): сразу конечный вид без анимации. */
    static void finish(View v) {
        if (v instanceof Bars) ((Bars) v).progress = 1f;
        else if (v instanceof Line) ((Line) v).progress = 1f;
        else if (v instanceof Ring) ((Ring) v).progress = 1f;
    }

    /** Линия с заливкой: значения по дням. */
    static View line(Context ctx, String[] labels, float[] values, String unit) {
        return new Line(ctx, labels, values, unit);
    }

    /** Тепловая карта 7×24: строки — дни недели (Пн..Вс), столбцы — часы. */
    static View heatmap(Context ctx, int[][] grid) {
        return new Heat(ctx, grid);
    }

    private static final class Line extends View {
        private final String[] labels;
        private final float[] values;
        private final String unit;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        Line(Context c, String[] labels, float[] values, String unit) {
            super(c);
            this.labels = labels;
            this.values = values;
            this.unit = unit;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.dp(2.5f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            text.setTextSize(AndroidUtilities.dp(10));
            text.setTextAlign(Paint.Align.CENTER);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(180));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(800);
            a.setInterpolator(new DecelerateInterpolator(1.5f));
            a.addUpdateListener(v -> {
                progress = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int n = values.length;
            if (n < 2) return;
            float max = 1;
            for (float v : values) max = Math.max(max, v);
            float pad = AndroidUtilities.dp(18), top = AndroidUtilities.dp(22), bottom = getHeight() - AndroidUtilities.dp(24);
            float stepX = (getWidth() - pad * 2) / (n - 1);
            android.graphics.Path path = new android.graphics.Path(), area = new android.graphics.Path();
            int shown = Math.max(2, (int) Math.ceil(n * progress));
            for (int i = 0; i < shown; i++) {
                float x = pad + stepX * i, y = bottom - (bottom - top) * values[i] / max;
                if (i == 0) {
                    path.moveTo(x, y);
                    area.moveTo(x, bottom);
                    area.lineTo(x, y);
                } else {
                    path.lineTo(x, y);
                    area.lineTo(x, y);
                }
                if (i == shown - 1) {
                    area.lineTo(x, bottom);
                    area.close();
                }
            }
            fill.setShader(new LinearGradient(0, top, 0, bottom, (accent() & 0x00FFFFFF) | 0x55000000, accent() & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawPath(area, fill);
            stroke.setShader(new LinearGradient(pad, 0, getWidth() - pad, 0, accent(), accent2(), Shader.TileMode.CLAMP));
            canvas.drawPath(path, stroke);
            text.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            int every = Math.max(1, n / 7);
            for (int i = 0; i < n; i += every) canvas.drawText(labels[i], pad + stepX * i, getHeight() - AndroidUtilities.dp(6), text);
            if (progress > 0.95f) {
                float last = values[n - 1];
                dot.setColor(accent2());
                float x = pad + stepX * (n - 1), y = bottom - (bottom - top) * last / max;
                canvas.drawCircle(x, y, AndroidUtilities.dp(4), dot);
                text.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(Math.round(last) + " " + unit, x, y - AndroidUtilities.dp(8), text);
                text.setTextAlign(Paint.Align.CENTER);
            }
        }
    }

    private static final class Heat extends View {
        private final int[][] grid;
        private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Heat(Context c, int[][] grid) {
            super(c);
            this.grid = grid;
            text.setTextSize(AndroidUtilities.dp(9));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(170));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int max = 1;
            for (int[] row : grid) for (int v : row) max = Math.max(max, v);
            float left = AndroidUtilities.dp(30), top = AndroidUtilities.dp(10), right = getWidth() - AndroidUtilities.dp(12), bottom = getHeight() - AndroidUtilities.dp(20);
            float cw = (right - left) / 24f, ch = (bottom - top) / 7f;
            String[] days = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
            int a = accent();
            text.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            for (int d = 0; d < 7; d++) {
                canvas.drawText(days[d], AndroidUtilities.dp(8), top + ch * d + ch * 0.7f, text);
                for (int h = 0; h < 24; h++) {
                    float t = (float) grid[d][h] / max;
                    cell.setColor((a & 0x00FFFFFF) | ((int) (25 + 230 * t) << 24));
                    r.set(left + cw * h + 1, top + ch * d + 1, left + cw * (h + 1) - 1, top + ch * (d + 1) - 1);
                    canvas.drawRoundRect(r, AndroidUtilities.dp(2), AndroidUtilities.dp(2), cell);
                }
            }
            for (int h = 0; h < 24; h += 6) canvas.drawText(String.format(java.util.Locale.ROOT, "%02d", h), left + cw * h, getHeight() - AndroidUtilities.dp(6), text);
        }
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
