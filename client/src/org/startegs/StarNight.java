/*
 * StarTegs 5.2 «Ночь»: тёплый ночной фильтр экрана по расписанию, живые фоны чата «Сияние» и
 * «Узор из эмодзи», картинка-карточка стиля для сторис. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;

public final class StarNight {

    private StarNight() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ================================================================ тёплый ночной фильтр

    /** 0 — выключен, 1 — всегда, 2 — по расписанию. */
    public static int mode() { return prefs().getInt("night_mode", 0); }

    static final String[] MODES = {"Выключен", "Всегда", "По расписанию"};
    static final int[] STRENGTH = {12, 22, 34};
    static final String[] STRENGTH_NAMES = {"Мягко", "Средне", "Сильно"};

    public static int strength() { return prefs().getInt("night_strength", 1); }

    public static int from() { return prefs().getInt("night_from", 22); }

    public static int to() { return prefs().getInt("night_to", 7); }

    public static String title() {
        int m = mode();
        return m == 0 ? "выключен" : m == 1 ? STRENGTH_NAMES[strength()].toLowerCase(Locale.ROOT) + ", всегда"
                : String.format(Locale.US, "%s, %02d:00–%02d:00", STRENGTH_NAMES[strength()].toLowerCase(Locale.ROOT), from(), to());
    }

    static boolean activeNow() {
        int m = mode();
        if (m == 0) return false;
        if (m == 1) return true;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), a = from(), b = to();
        return a == b || (a < b ? h >= a && h < b : h >= a || h < b);
    }

    private static WeakReference<View> overlay;
    private static Runnable ticker;

    /** Из StarPlus.init: тонкий тёплый слой поверх приложения (касания проходят насквозь). */
    static void attach(Activity a) {
        try {
            ViewGroup content = a.findViewById(android.R.id.content);
            if (content == null) return;
            View v = new View(a);
            v.setClickable(false);
            v.setFocusable(false);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay = new WeakReference<>(v);
            update();
            if (ticker == null) {
                ticker = new Runnable() {
                    @Override
                    public void run() {
                        update();
                        AndroidUtilities.runOnUIThread(this, 5 * 60_000L);
                    }
                };
                AndroidUtilities.runOnUIThread(ticker, 5 * 60_000L);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    static void update() {
        View v = overlay != null ? overlay.get() : null;
        if (v == null) return;
        if (!activeNow()) {
            v.setVisibility(View.GONE);
            return;
        }
        int alpha = STRENGTH[Math.max(0, Math.min(2, strength()))] * 255 / 100;
        v.setBackgroundColor(Color.argb(alpha, 255, 140, 30));
        v.setVisibility(View.VISIBLE);
        v.bringToFront();
    }

    public static void choose(BaseFragment f, Runnable done) {
        if (f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity()).setTitle("🌙 Тёплый ночной фильтр").setItems(MODES, (d, w) -> {
            prefs().edit().putInt("night_mode", w).apply();
            if (w == 0) {
                update();
                if (done != null) done.run();
                return;
            }
            new AlertDialog.Builder(f.getParentActivity()).setTitle("Насколько тёплый").setItems(STRENGTH_NAMES, (d2, s) -> {
                prefs().edit().putInt("night_strength", s).apply();
                if (w == 2) {
                    String[] hours = new String[24];
                    for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.US, "%02d:00", i);
                    new AlertDialog.Builder(f.getParentActivity()).setTitle("Включать с…").setItems(hours, (d3, a) ->
                            new AlertDialog.Builder(f.getParentActivity()).setTitle("…и до").setItems(hours, (d4, b) -> {
                                prefs().edit().putInt("night_from", a).putInt("night_to", b).apply();
                                update();
                                if (done != null) done.run();
                            }).show()).show();
                } else {
                    update();
                    if (done != null) done.run();
                }
            }).show();
        }).show();
    }

    // ================================================================ фон «Сияние»

    static int accentHue() {
        float[] hsv = new float[3];
        Color.colorToHSV(Theme.getColor(Theme.key_featuredStickers_addButton), hsv);
        return (int) hsv[0];
    }

    /** Мягкие пятна света, медленно плывущие по фону чата в цветах темы. */
    static final class Aurora extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float hue = accentHue();
        private final long start = System.currentTimeMillis();

        Aurora(Context c) {
            super(c);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0) return;
            boolean dark = Theme.isCurrentThemeDark();
            boolean anim = StarPro.anim(LiteMode.FLAG_CHAT_BACKGROUND);
            double t = anim ? (System.currentTimeMillis() - start) / 9000.0 : 0;
            float[][] blobs = {{0.2f, 0.25f, 0f}, {0.8f, 0.45f, 40f}, {0.4f, 0.8f, -50f}};
            for (int i = 0; i < blobs.length; i++) {
                float cx = (float) (w * (blobs[i][0] + 0.12 * Math.sin(t + i * 2.1)));
                float cy = (float) (h * (blobs[i][1] + 0.08 * Math.cos(t * 0.8 + i)));
                float r = Math.max(w, h) * 0.55f;
                int c = Color.HSVToColor(dark ? 70 : 55, new float[]{(hue + blobs[i][2] + 360) % 360, dark ? 0.7f : 0.45f, dark ? 0.8f : 1f});
                p.setShader(new RadialGradient(cx, cy, r, c, 0x00000000, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, r, p);
            }
            if (anim && isShown()) postInvalidateDelayed(66); // ~15 кадров/с — плавно и экономно
        }
    }

    // ================================================================ фон «Узор из эмодзи»

    static final String[][] DOODLES = {
            {"⭐", "✨", "🌙", "💫"}, {"💖", "🌸", "🦋", "✨"}, {"🎮", "🎧", "🍕", "⚡"},
            {"🐱", "🐾", "🐟", "💤"}, {"📚", "✏️", "💡", "☕"}, {"🌊", "🐚", "☀️", "🌴"},
            {"🌸", "🍙", "🎌", "✨"}, {"⚽", "🏀", "🎾", "🏆"}, {"🪐", "🚀", "⭐", "👽"}, {"🍩", "🍓", "🧁", "🍔"},
    };
    static final String[] DOODLE_NAMES = {"⭐ Звёзды", "💖 Нежность", "🎮 Геймер", "🐱 Котики", "📚 Учёба", "🌊 Лето", "🌸 Аниме", "⚽ Спорт", "🪐 Космос", "🍩 Вкусняшки"};

    public static int doodle() { return prefs().getInt("night_doodle", 0); }

    public static void chooseDoodle(BaseFragment f, Runnable done) {
        if (f.getParentActivity() == null) return;
        new AlertDialog.Builder(f.getParentActivity()).setTitle("🎨 Узор из эмодзи").setItems(DOODLE_NAMES, (d, w) -> {
            prefs().edit().putInt("night_doodle", w).apply();
            if (done != null) done.run();
        }).show();
    }

    /** Повторяющийся узор: плитка рисуется один раз, дальше — шейдером (почти не тратит батарею). */
    static final class Doodle extends View {
        private final Paint p = new Paint();
        private Bitmap tile;

        Doodle(Context c) {
            super(c);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private void build() {
            int s = AndroidUtilities.dp(120);
            tile = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tile);
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTextSize(AndroidUtilities.dp(20));
            tp.setAlpha(Theme.isCurrentThemeDark() ? 60 : 75);
            String[] set = DOODLES[Math.max(0, Math.min(DOODLES.length - 1, doodle()))];
            float[][] pos = {{0.12f, 0.25f, -15}, {0.6f, 0.18f, 12}, {0.35f, 0.62f, 20}, {0.8f, 0.75f, -8}};
            for (int i = 0; i < pos.length; i++) {
                c.save();
                c.rotate(pos[i][2], s * pos[i][0], s * pos[i][1]);
                c.drawText(set[i % set.length], s * pos[i][0], s * pos[i][1], tp);
                c.restore();
            }
            p.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (tile == null) build();
            canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }

    // ================================================================ карточка стиля для сторис

    public static void shareCard(BaseFragment f) {
        if (f.getParentActivity() == null) return;
        try {
            int w = 1080, h = 1350;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            int cv = StarDecor.cover();
            int[] bg = cv > 0 && cv < StarDecor.COVER_COLORS.length ? StarDecor.COVER_COLORS[cv] : new int[]{0xFF7B5CFF, 0xFFFF5CA8};
            p.setShader(new LinearGradient(0, 0, w, h, bg[0], bg[1], Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            // декоративные звёзды
            TextPaint deco = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            deco.setTextSize(64);
            deco.setAlpha(90);
            String[] stars = {"✨", "⭐", "💫"};
            for (int i = 0; i < 14; i++) c.drawText(stars[i % 3], (i * 337) % (w - 80), 120 + (i * 211) % (h - 200), deco);

            int account = f.getCurrentAccount();
            TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
            float cx = w / 2f, cy = 470, r = 230;
            Bitmap ava = null;
            if (me != null && me.photo != null && me.photo.photo_big != null) {
                File file = FileLoader.getInstance(account).getPathToAttach(me.photo.photo_big, true);
                if (file != null && file.exists()) ava = BitmapFactory.decodeFile(file.getAbsolutePath());
            }
            if (ava == null && me != null && me.photo != null && me.photo.photo_small != null) {
                File file = FileLoader.getInstance(account).getPathToAttach(me.photo.photo_small, true);
                if (file != null && file.exists()) ava = BitmapFactory.decodeFile(file.getAbsolutePath());
            }
            if (ava != null) {
                BitmapShader sh = new BitmapShader(ava, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                Matrix m = new Matrix();
                float k = 2 * r / Math.min(ava.getWidth(), ava.getHeight());
                m.setScale(k, k);
                m.postTranslate(cx - ava.getWidth() * k / 2, cy - ava.getHeight() * k / 2);
                sh.setLocalMatrix(m);
                p.setShader(sh);
                c.drawCircle(cx, cy, r, p);
                p.setShader(null);
            } else {
                p.setColor(0x55FFFFFF);
                c.drawCircle(cx, cy, r, p);
            }
            String frame = StarCosmetics.myFrame();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(22);
            if (frame != null && !frame.isEmpty()) p.setShader(new SweepGradient(cx, cy, StarCosmetics.colors(frame), null));
            else p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, r + 16, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);

            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(Color.WHITE);
            tp.setTypeface(AndroidUtilities.bold());
            tp.setTextSize(92);
            String name = me != null ? UserObject.getFirstName(me) : "Я";
            int style = StarDecor.nameStyle();
            if (style >= 0 && style < StarStyle.STYLES.length) name = StarStyle.apply(style, name);
            if (StarDecor.nameColor() != 0) tp.setColor(StarDecor.nameColor());
            String line = TextUtils.ellipsize(name, tp, w - 160, TextUtils.TruncateAt.END) + " " + StarDecor.medal(StarAchieve.level());
            c.drawText(line, cx, 860, tp);
            tp.setColor(0xEEFFFFFF);
            tp.setTypeface(null);
            tp.setTextSize(48);
            String status = StarProfile.statusTitle();
            c.drawText(TextUtils.isEmpty(status) ? "Уровень " + StarAchieve.level() : status, cx, 950, tp);
            tp.setTextSize(40);
            tp.setColor(0xCCFFFFFF);
            c.drawText("⭐ Мой стиль в StarTegs", cx, h - 90, tp);

            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "StarTegs_style.png");
            try (FileOutputStream os = new FileOutputStream(file)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            out.recycle();
            if (ava != null) ava.recycle();
            StarVault.shareFile(f.getParentActivity(), file);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(f.getParentActivity(), "Не получилось сделать картинку", Toast.LENGTH_SHORT).show();
        }
    }
}
