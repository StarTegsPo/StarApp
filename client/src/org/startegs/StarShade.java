/*
 * StarTegs 4.1 — «Шторка приватности»: экран закрывается размытием, когда телефон
 * кладут на стол экраном вверх или переворачивают/наклоняют верхом от себя.
 * Поднимите телефон или коснитесь шторки — она исчезнет. Premium.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public final class StarShade {

    private StarShade() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean enabled() {
        return prefs().getBoolean("shade_on", false) && StarMod.isPro();
    }

    private static Activity current;
    private static View cover;
    private static boolean registered, lifecycle;
    private static long dismissedAt;
    private static int tiltCount;

    private static final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            float gx = e.values[0], gy = e.values[1], gz = e.values[2];
            // на столе экраном вверх: почти вся гравитация по Z; перевёрнут/наклонён от себя: верх телефона ниже низа (gy < 0)
            boolean flat = gz > 9.0f && Math.abs(gx) < 1.5f && Math.abs(gy) < 1.5f;
            boolean away = gy < -3.5f;
            boolean hide = flat || away;
            tiltCount = hide ? Math.min(tiltCount + 1, 10) : 0;
            if (tiltCount >= 4 && System.currentTimeMillis() - dismissedAt > 4000) show();
            else if (!hide && gy > 4f) hide();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    /** Вызывается из StarPlus.init. */
    static void init(Activity activity) {
        if (lifecycle || activity == null) return;
        lifecycle = true;
        current = activity;
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { current = a; update(); }
            @Override public void onActivityPaused(Activity a) { unregister(); hide(); }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (current == a) current = null; }
        });
        update();
    }

    /** Включить/выключить датчик по настройке. */
    static void update() {
        if (enabled() && current != null) register();
        else {
            unregister();
            hide();
        }
    }

    private static void register() {
        if (registered) return;
        try {
            SensorManager sm = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
            Sensor s = sm != null ? sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
            if (s == null) return;
            registered = sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void unregister() {
        if (!registered) return;
        try {
            SensorManager sm = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) sm.unregisterListener(listener);
        } catch (Exception ignore) {
        }
        registered = false;
        tiltCount = 0;
    }

    private static void show() {
        Activity a = current;
        if (a == null || cover != null) return;
        try {
            ViewGroup root = (ViewGroup) a.getWindow().getDecorView();
            View content = a.findViewById(android.R.id.content);
            if (Build.VERSION.SDK_INT >= 31 && content != null) {
                content.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP));
            }
            FrameLayout f = new FrameLayout(a);
            f.setBackgroundColor(Build.VERSION.SDK_INT >= 31 ? 0x66000000 : 0xF0101018);
            TextView t = new TextView(a);
            t.setText("🔒\nStarTegs\nПоднимите телефон или коснитесь");
            t.setTextColor(Color.WHITE);
            t.setTextSize(18);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            f.setClickable(true);
            f.setOnClickListener(v -> {
                dismissedAt = System.currentTimeMillis();
                hide();
            });
            root.addView(f, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            cover = f;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void hide() {
        View c = cover;
        if (c == null) return;
        cover = null;
        try {
            if (c.getParent() instanceof ViewGroup) ((ViewGroup) c.getParent()).removeView(c);
            Activity a = current;
            View content = a != null ? a.findViewById(android.R.id.content) : null;
            if (Build.VERSION.SDK_INT >= 31 && content != null) content.setRenderEffect(null);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
