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
 * Живой фон в стиле StarApp: медленно плавающие светящиеся пятна (фиолет / голубой / розовый)
 * и мерцающие светлячки. Если в системе отключены анимации — рисуется статично.
 */
public class FxView extends View {

    static final int BG = 0xFF0F1017;
    static final int A1 = 0xFF7B5CFF, A2 = 0xFF33A6FF, A3 = 0xFFFF5CA8;

    private static final int N = 26;
    private final float[] fx = new float[N], fy = new float[N], vx = new float[N], vy = new float[N],
            ph = new float[N], size = new float[N];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long t0 = SystemClock.uptimeMillis();
    private final boolean animate;
    private final float density;

    public FxView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        float scale = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        animate = scale != 0f;
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            fx[i] = r.nextFloat();
            fy[i] = r.nextFloat();
            vx[i] = (r.nextFloat() - 0.5f) * 0.012f;
            vy[i] = -0.006f - r.nextFloat() * 0.014f;   // светлячки медленно всплывают
            ph[i] = r.nextFloat() * 6.28f;
            size[i] = 1.2f + r.nextFloat() * 1.8f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = animate ? (SystemClock.uptimeMillis() - t0) / 1000f : 0f;
        int w = getWidth(), h = getHeight();
        c.drawColor(BG);

        blob(c, w * (0.18f + 0.10f * (float) Math.sin(t * 0.21)), h * (0.12f + 0.05f * (float) Math.cos(t * 0.17)),
                w * 0.75f, A1, 0x66);
        blob(c, w * (0.88f + 0.08f * (float) Math.cos(t * 0.15)), h * (0.42f + 0.07f * (float) Math.sin(t * 0.19)),
                w * 0.62f, A2, 0x48);
        blob(c, w * (0.25f + 0.12f * (float) Math.sin(t * 0.13 + 1)), h * (0.86f + 0.05f * (float) Math.sin(t * 0.23)),
                w * 0.70f, A3, 0x3C);

        paint.setShader(null);
        for (int i = 0; i < N; i++) {
            float x = wrap(fx[i] + vx[i] * t + 0.02f * (float) Math.sin(t * 0.7 + ph[i])) * w;
            float y = wrap(fy[i] + vy[i] * t) * h;
            float a = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * 1.6 + ph[i]));
            float rad = size[i] * density;
            int tint = i % 3 == 0 ? 0xFFFFD7F0 : (i % 3 == 1 ? 0xFFD9D1FF : 0xFFCFEFFF);
            paint.setColor(withAlpha(tint, (int) (a * 50)));
            c.drawCircle(x, y, rad * 3.2f, paint);
            paint.setColor(withAlpha(tint, (int) (a * 230)));
            c.drawCircle(x, y, rad, paint);
        }

        if (animate) postInvalidateOnAnimation();
    }

    private void blob(Canvas c, float cx, float cy, float r, int color, int alpha) {
        paint.setShader(new RadialGradient(cx, cy, r, withAlpha(color, alpha), withAlpha(color, 0), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, paint);
    }

    private static float wrap(float v) {
        v = v % 1f;
        return v < 0 ? v + 1f : v;
    }

    static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
