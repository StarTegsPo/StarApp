package com.startegs.warmup;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.TextView;

/** Градиентная кнопка-«пилюля» с бегущим бликом. */
public class ShimmerButton extends TextView {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final long t0 = SystemClock.uptimeMillis();
    private final boolean animate;
    private int c1, c2;
    private boolean shimmer = true;

    public ShimmerButton(Context c, int c1, int c2) {
        super(c);
        this.c1 = c1;
        this.c2 = c2;
        animate = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        setGravity(Gravity.CENTER);
        setTextColor(0xFFFFFFFF);
        setWillNotDraw(false);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(9999);
        mask.setColor(0xFFFFFFFF);
        setForeground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), null, mask));
    }

    void setColors(int c1, int c2) {
        this.c1 = c1;
        this.c2 = c2;
        invalidate();
    }

    void setShimmer(boolean on) {
        shimmer = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        bg.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, bg);

        if (shimmer && animate) {
            float t = ((SystemClock.uptimeMillis() - t0) % 2800) / 2800f;
            float x = -w * 0.5f + t * w * 2.0f;
            shine.setShader(new LinearGradient(x, 0, x + w * 0.3f, h,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            clip.reset();
            clip.addRoundRect(rect, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawRect(rect, shine);
            c.restore();
            postInvalidateOnAnimation();
        }
        super.onDraw(c);
    }
}
