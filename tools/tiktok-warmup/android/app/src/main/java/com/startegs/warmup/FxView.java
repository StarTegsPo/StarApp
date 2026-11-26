package com.startegs.warmup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Random;

/**
 * Живой фон: медленно плавающие светящиеся пятна в цветах темы
 * и поднимающиеся мерцающие искры. Если анимации в системе выключены — статичный.
 */
public class FxView extends View {

    private static final int N = 30;
    private final float[] fx = new float[N], fy = new float[N], vx = new float[N], vy = new float[N],
            ph = new float[N], size = new float[N];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long t0 = SystemClock.uptimeMillis();
    private final boolean animate;
    private final float density;
    private final Theme th;

    public FxView(Context c, Theme th) {
        super(c);
        this.th = th;
        density = c.getResources().getDisplayMetrics().density;
        animate = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            fx[i] = r.nextFloat();
            fy[i] = r.nextFloat();
            vx[i] = (r.nextFloat() - 0.5f) * 0.010f;
            vy[i] = -0.008f - r.nextFloat() * 0.018f;   // искры поднимаются вверх
            ph[i] = r.nextFloat() * 6.28f;
            size[i] = 1.0f + r.nextFloat() * 1.8f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = animate ? (SystemClock.uptimeMillis() - t0) / 1000f : 0f;
        int w = getWidth(), h = getHeight();
        c.drawColor(th.bg);
        int k = th.light ? 2 : 3; // в светлой теме пятна мягче

        blob(c, w * (0.15f + 0.10f * (float) Math.sin(t * 0.21)), h * (0.10f + 0.05f * (float) Math.cos(t * 0.17)),
                w * 0.80f, th.a1, 0x22 * k);
        blob(c, w * (0.90f + 0.08f * (float) Math.cos(t * 0.15)), h * (0.45f + 0.07f * (float) Math.sin(t * 0.19)),
                w * 0.65f, th.a3, 0x1C * k);
        blob(c, w * (0.25f + 0.12f * (float) Math.sin(t * 0.13 + 1)), h * (0.88f + 0.05f * (float) Math.sin(t * 0.23)),
                w * 0.72f, th.a2, 0x14 * k);

        paint.setShader(null);
        for (int i = 0; i < N; i++) {
            float x = wrap(fx[i] + vx[i] * t + 0.02f * (float) Math.sin(t * 0.8 + ph[i])) * w;
            float y = wrap(fy[i] + vy[i] * t) * h;
            float flick = 0.5f + 0.5f * (float) Math.sin(t * 2.2 + ph[i]);
            float a = 0.2f + 0.8f * flick * flick;
            float rad = size[i] * density;
            int col = i % 4 == 0 ? th.a2 : th.spark;
            paint.setColor(Theme.alpha(col, (int) (a * (th.light ? 40 : 60))));
            c.drawCircle(x, y, rad * 3.4f, paint);
            paint.setColor(Theme.alpha(col, (int) (a * (th.light ? 170 : 235))));
            c.drawCircle(x, y, rad, paint);
        }

        if (animate) postInvalidateOnAnimation();
    }

    private void blob(Canvas c, float cx, float cy, float r, int color, int alpha) {
        paint.setShader(new RadialGradient(cx, cy, r, Theme.alpha(color, alpha), Theme.alpha(color, 0), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, paint);
    }

    private static float wrap(float v) {
        v = v % 1f;
        return v < 0 ? v + 1f : v;
    }
}
