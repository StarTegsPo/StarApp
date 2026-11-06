/*
 * StarTegs 2.1 — «Живые обои»: поверх фона чата мягкий градиент по времени суток
 * (утро, день, вечер, ночь), плавно меняется; зимой можно добавить снег (сезонные эффекты).
 * Лежит под сообщениями и не перехватывает нажатия. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import java.util.Calendar;

public final class StarWall {

    private StarWall() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean enabled() {
        return prefs().getBoolean("live_wall", false);
    }

    static void toggle() {
        prefs().edit().putBoolean("live_wall", !enabled()).apply();
    }

    public static boolean seasonInChats() {
        return prefs().getBoolean("live_wall_season", true);
    }

    static void toggleSeason() {
        prefs().edit().putBoolean("live_wall_season", !seasonInChats()).apply();
    }

    /** Название текущей «поры дня» для подписи в настройках. */
    static String phaseName() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 5 && h < 11 ? "🌅 утро" : h < 17 && h >= 11 ? "☀️ день" : h >= 17 && h < 22 ? "🌇 вечер" : "🌙 ночь";
    }

    // верх и низ градиента для каждой поры (с прозрачностью — поверх обоев пользователя)
    private static final int[][] PHASES = {
            {0x55FFB38A, 0x22FFE3A3}, // утро: персик → тёплый свет
            {0x3364C8FF, 0x1199E6FF}, // день: небо
            {0x66FF7A59, 0x448E5CFF}, // вечер: закат → фиолетовый
            {0x770A1440, 0x55301C6E}, // ночь: глубокий синий → фиолетовый
    };

    private static int phaseAt(float hour) {
        if (hour >= 5 && hour < 11) return 0;
        if (hour >= 11 && hour < 17) return 1;
        if (hour >= 17 && hour < 22) return 2;
        return 3;
    }

    private static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Вызывается в ChatActivity.createView сразу после создания contentView. */
    public static void attach(ViewGroup root) {
        try {
            if (root == null || !enabled()) return;
            // сразу над фоном чата (BackgroundView), но под сообщениями
            int at = root.getChildCount() > 0 && root.getChildAt(0).getClass().getSimpleName().equals("BackgroundView") ? 1 : 0;
            root.addView(new WallView(root.getContext()), at, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (seasonInChats() && StarSeason.effective() != 0) {
                root.addView(new StarSeason.SeasonView(root.getContext()), at + 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } catch (Exception ignore) {
        }
    }

    static final class WallView extends View {
        private final Paint paint = new Paint();
        private int lastW, lastH, lastMinute = -1;

        WallView(Context c) {
            super(c);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Calendar c = Calendar.getInstance();
            int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            int w = getWidth(), h = getHeight();
            if (minute != lastMinute || w != lastW || h != lastH) {
                lastMinute = minute;
                lastW = w;
                lastH = h;
                float hour = minute / 60f;
                int cur = phaseAt(hour);
                // за полчаса до смены поры плавно смешиваем с следующей
                int[] ends = {11, 17, 22, 29};
                float end = ends[cur];
                float hh = cur == 3 && hour < 5 ? hour + 24 : hour;
                float t = Math.max(0f, Math.min(1f, (hh - (end - 0.5f)) / 0.5f));
                int next = (cur + 1) % 4;
                int top = mix(PHASES[cur][0], PHASES[next][0], t), bottom = mix(PHASES[cur][1], PHASES[next][1], t);
                if (!Theme.isCurrentThemeDark() && cur == 3) { // ночь на светлой теме — мягче
                    top = (top & 0x00FFFFFF) | 0x44000000;
                    bottom = (bottom & 0x00FFFFFF) | 0x22000000;
                }
                paint.setShader(new LinearGradient(0, 0, 0, Math.max(1, h), top, bottom, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, w, h, paint);
            postInvalidateDelayed(60_000); // раз в минуту — почти без расхода батареи
        }
    }
}
