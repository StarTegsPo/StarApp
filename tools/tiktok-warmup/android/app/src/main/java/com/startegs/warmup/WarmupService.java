package com.startegs.warmup;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/**
 * Листает ленту TikTok свайпами и ставит лайк двойным тапом через случайное число роликов.
 * Жесты идут только в окно TikTok: в разделённом экране или плавающем окне
 * второй половиной экрана можно пользоваться параллельно.
 * Если TikTok не виден на экране — пауза.
 */
public class WarmupService extends AccessibilityService {

    static WarmupService instance;

    static final String[] TIKTOK = {
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme"
    };

    static final String IDLE = "idle", RUNNING = "running", PAUSED = "paused", BREAK = "break";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private boolean running;
    private int videos, likeMin, likeMax, watchMin, watchMax, skipChance, longChance;
    private int done, likes, untilLike, untilBreak;
    private long startedAt;
    private String state = IDLE;
    private String lastPkg = "";

    private WindowManager wm;
    private TextView overlay;
    private WindowManager.LayoutParams overlayLp;

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

    // ---------- состояние для экрана приложения ----------

    static boolean isRunning() { return instance != null && instance.running; }
    String state() { return state; }
    int done() { return done; }
    int likes() { return likes; }
    int videos() { return videos; }
    long elapsedMs() { return running ? SystemClock.elapsedRealtime() - startedAt : 0; }

    // ---------- управление ----------

    /** videos = 0 — без ограничения. skipChance/longChance — шансы в процентах. */
    void start(int videos, int likeMin, int likeMax, int watchMin, int watchMax,
               int skipChance, int longChance) {
        stop();
        this.videos = videos;
        this.likeMin = likeMin;
        this.likeMax = likeMax;
        this.watchMin = watchMin;
        this.watchMax = watchMax;
        this.skipChance = skipChance;
        this.longChance = longChance;
        done = 0;
        likes = 0;
        untilLike = rand(likeMin, likeMax);
        untilBreak = rand(30, 50);
        startedAt = SystemClock.elapsedRealtime();
        running = true;
        state = RUNNING;
        showOverlay();
        updateOverlay();
        // 5 c на открытие TikTok + просмотр первого ролика
        handler.postDelayed(step, 5000 + watchMs());
    }

    void stop() {
        running = false;
        state = IDLE;
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
    }

    // ---------- основной цикл ----------

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Rect r = tiktokBounds();
            if (r == null) {
                state = PAUSED;
                updateOverlay();
                handler.postDelayed(this, 2000);
                return;
            }
            state = RUNNING;
            done++;
            long delay = rand(300, 900);
            if (--untilLike <= 0) {
                doubleTap(r);
                likes++;
                untilLike = rand(likeMin, likeMax);
                delay = rand(1200, 2500);
                bumpOverlay();
            }
            updateOverlay();
            if (videos > 0 && done >= videos) {
                handler.postDelayed(WarmupService.this::stop, delay);
                return;
            }
            handler.postDelayed(swipe, delay);
        }
    };

    private final Runnable swipe = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Rect r = tiktokBounds();
            if (r == null) {
                state = PAUSED;
                updateOverlay();
                handler.postDelayed(this, 2000);
                return;
            }
            swipeUp(r);
            long next = watchMs();
            if (--untilBreak <= 0) {
                next += rand(30, 120) * 1000L;
                untilBreak = rand(30, 50);
                state = BREAK;
            } else {
                state = RUNNING;
            }
            updateOverlay();
            handler.postDelayed(step, next);
        }
    };

    /** Границы окна TikTok на экране, или null, если TikTok сейчас не виден. */
    private Rect tiktokBounds() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo w : windows) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null) continue;
                if (isTikTok(root.getPackageName().toString())) {
                    Rect r = new Rect();
                    w.getBoundsInScreen(r);
                    if (r.width() > 100 && r.height() > 200) return r;
                }
            }
            if (!windows.isEmpty()) return null;
        } catch (RuntimeException ignored) { }
        // запасной вариант: TikTok на весь экран
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root != null && root.getPackageName() != null ? root.getPackageName().toString() : lastPkg;
        if (!isTikTok(pkg)) return null;
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return new Rect(0, 0, m.widthPixels, m.heightPixels);
    }

    private static boolean isTikTok(String pkg) {
        for (String p : TIKTOK) if (p.equals(pkg)) return true;
        return false;
    }

    // ---------- жесты (в координатах окна TikTok) ----------

    private void swipeUp(Rect r) {
        float x = r.centerX() + rand(-r.width() / 18, r.width() / 18);
        Path p = new Path();
        p.moveTo(x, r.top + r.height() * rand(70, 78) / 100f);
        p.lineTo(x + rand(-25, 25), r.top + r.height() * rand(22, 30) / 100f);
        dispatch(new GestureDescription.StrokeDescription(p, 0, rand(220, 400)));
    }

    /** Двойной тап по центру ролика — в TikTok он только ставит лайк и никогда не снимает его. */
    private void doubleTap(Rect r) {
        float x = r.centerX() + rand(-r.width() / 18, r.width() / 18);
        float y = r.top + r.height() * rand(38, 50) / 100f;
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

    // ---------- плавающая плашка: нажать — стоп, тащить — переместить ----------

    private void showOverlay() {
        if (overlay != null || wm == null) return;
        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(13);
        overlay.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int pad = dp(14);
        overlay.setPadding(pad, dp(9), pad, dp(9));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xEE7B5CFF, 0xEE33A6FF});
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x55FFFFFF);
        overlay.setBackground(bg);
        overlay.setElevation(dp(8));

        overlayLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        overlayLp.gravity = Gravity.TOP | Gravity.START;
        overlayLp.x = dp(12);
        overlayLp.y = dp(90);
        overlayLp.windowAnimations = android.R.style.Animation_Toast;

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        overlay.setOnTouchListener(new android.view.View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;

            @Override
            public boolean onTouch(android.view.View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = overlayLp.x;
                        startY = overlayLp.y;
                        dragging = false;
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(120).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging && overlay != null) {
                            overlayLp.x = startX + (int) dx;
                            overlayLp.y = startY + (int) dy;
                            try { wm.updateViewLayout(overlay, overlayLp); } catch (RuntimeException ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                        if (!dragging) stop();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(overlay, overlayLp);
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void updateOverlay() {
        if (overlay == null) return;
        String icon;
        switch (state) {
            case PAUSED: icon = "⏸ откройте TikTok"; break;
            case BREAK:  icon = "☕ перерыв"; break;
            default:     icon = "● прогрев";
        }
        overlay.setText(icon + "   🎬 " + done + (videos > 0 ? "/" + videos : "")
                + "   ❤ " + likes + "   ■");
    }

    /** Короткая «пульсация» плашки, когда поставлен лайк. */
    private void bumpOverlay() {
        if (overlay == null) return;
        overlay.animate().cancel();
        overlay.setScaleX(1.12f);
        overlay.setScaleY(1.12f);
        overlay.animate().scaleX(1f).scaleY(1f).setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
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

    /**
     * Случайное время просмотра: обычно от watchMin до watchMax,
     * с шансом skipChance% — быстрый пролист (1–3 c),
     * с шансом longChance% — долгий просмотр (от watchMax до 2×watchMax).
     */
    private long watchMs() {
        int roll = rnd.nextInt(100);
        if (roll < skipChance) return rand(1000, 3000);
        if (roll < skipChance + longChance) return rand(watchMax * 1000, watchMax * 2000);
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
