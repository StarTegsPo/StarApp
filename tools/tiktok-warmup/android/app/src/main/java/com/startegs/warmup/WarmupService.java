package com.startegs.warmup;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.Random;

/**
 * Листает ленту TikTok свайпами и ставит лайк двойным тапом через случайное число роликов.
 * Работает только пока TikTok на экране; в других приложениях ставится на паузу.
 */
public class WarmupService extends AccessibilityService {

    static WarmupService instance;

    static final String[] TIKTOK = {
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private boolean running;
    private int videos, likeMin, likeMax, watchMin, watchMax;
    private int done, likes, untilLike, untilBreak;
    private String lastPkg = "";

    private WindowManager wm;
    private TextView overlay;

    // ---------- жизненный цикл ----------

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String pkg = e.getPackageName().toString();
            if (!pkg.equals(getPackageName())) lastPkg = pkg;
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        stop();
        instance = null;
        super.onDestroy();
    }

    // ---------- управление ----------

    static boolean isRunning() {
        return instance != null && instance.running;
    }

    void start(int videos, int likeMin, int likeMax, int watchMin, int watchMax) {
        stop();
        this.videos = videos;
        this.likeMin = likeMin;
        this.likeMax = likeMax;
        this.watchMin = watchMin;
        this.watchMax = watchMax;
        done = 0;
        likes = 0;
        untilLike = rand(likeMin, likeMax);
        untilBreak = rand(30, 50);
        running = true;
        showOverlay();
        updateOverlay("▶ старт через 5 c");
        // 5 c на открытие TikTok + просмотр первого ролика
        handler.postDelayed(step, 5000 + watchMs());
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
    }

    // ---------- основной цикл ----------

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!tiktokOnTop()) {
                updateOverlay("⏸ пауза — откройте TikTok");
                handler.postDelayed(this, 2000);
                return;
            }
            done++;
            long delay = rand(300, 900);
            if (--untilLike <= 0) {
                doubleTap();
                likes++;
                untilLike = rand(likeMin, likeMax);
                delay = rand(1200, 2500);
            }
            updateOverlay(null);
            if (done >= videos) {
                handler.postDelayed(() -> {
                    stop();
                }, delay);
                return;
            }
            handler.postDelayed(() -> {
                if (!running) return;
                swipeUp();
                long next = watchMs();
                if (--untilBreak <= 0) {
                    next += rand(30, 120) * 1000L;
                    untilBreak = rand(30, 50);
                    updateOverlay("☕ перерыв");
                }
                handler.postDelayed(step, next);
            }, delay);
        }
    };

    private boolean tiktokOnTop() {
        String pkg = lastPkg;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) pkg = root.getPackageName().toString();
        for (String p : TIKTOK) if (p.equals(pkg)) return true;
        return false;
    }

    // ---------- жесты ----------

    private void swipeUp() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int w = m.widthPixels, h = m.heightPixels;
        float x = w / 2f + rand(-60, 60);
        Path p = new Path();
        p.moveTo(x, h * rand(70, 78) / 100f);
        p.lineTo(x + rand(-25, 25), h * rand(22, 30) / 100f);
        dispatch(new GestureDescription.StrokeDescription(p, 0, rand(220, 400)));
    }

    /** Двойной тап по центру ролика — в TikTok он только ставит лайк и никогда не снимает его. */
    private void doubleTap() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        float x = m.widthPixels / 2f + rand(-60, 60);
        float y = m.heightPixels * rand(38, 50) / 100f;
        Path p1 = new Path();
        p1.moveTo(x, y);
        Path p2 = new Path();
        p2.moveTo(x + rand(-6, 6), y + rand(-6, 6));
        int gap = rand(90, 150);
        dispatch(new GestureDescription.StrokeDescription(p1, 0, 40),
                new GestureDescription.StrokeDescription(p2, 40 + gap, 40));
    }

    private void dispatch(GestureDescription.StrokeDescription... strokes) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (GestureDescription.StrokeDescription s : strokes) b.addStroke(s);
        dispatchGesture(b.build(), null, null);
    }

    // ---------- плавающая кнопка «Стоп» ----------

    private void showOverlay() {
        if (overlay != null || wm == null) return;
        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(13);
        int pad = dp(10);
        overlay.setPadding(pad, pad / 2, pad, pad / 2);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC000000);
        bg.setCornerRadius(dp(16));
        overlay.setBackground(bg);
        overlay.setOnClickListener(v -> stop());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(90);
        try {
            wm.addView(overlay, lp);
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void updateOverlay(String note) {
        if (overlay == null) return;
        String text = "■ Стоп   🎬 " + done + "/" + videos + "   ❤ " + likes;
        if (note != null) text += "\n" + note;
        overlay.setText(text);
    }

    private void hideOverlay() {
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay);
            } catch (RuntimeException ignored) { }
        }
        overlay = null;
    }

    // ---------- утилиты ----------

    private long watchMs() {
        return rand(watchMin * 1000, watchMax * 1000);
    }

    private int rand(int a, int b) {
        if (b < a) { int t = a; a = b; b = t; }
        return a + rnd.nextInt(b - a + 1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
