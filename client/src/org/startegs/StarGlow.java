/*
 * StarTegs 5.1 «Сияние»: тема по цвету аватарки или обоев телефона, сезонные частицы в шапке
 * StarTegs, праздник при новом уровне, цветные баннеры разделов и красивые пустые экраны.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.Calendar;

public final class StarGlow {

    private StarGlow() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ================================================================ тема из аватарки / обоев

    /** Самый «живой» оттенок картинки: гистограмма по насыщенным пикселям. -1 — не нашли. */
    static float dominantHue(Bitmap b) {
        int[] bins = new int[36];
        int w = b.getWidth(), h = b.getHeight();
        int step = Math.max(1, Math.max(w, h) / 64);
        float[] hsv = new float[3];
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                Color.colorToHSV(b.getPixel(x, y), hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue;
                bins[(int) (hsv[0] / 10f) % 36] += 1 + (int) (hsv[1] * 3);
            }
        }
        int best = -1, max = 0;
        for (int i = 0; i < 36; i++) {
            int v = bins[i] + bins[(i + 35) % 36] / 2 + bins[(i + 1) % 36] / 2;
            if (v > max) {
                max = v;
                best = i;
            }
        }
        return best < 0 || max < 8 ? -1 : best * 10 + 5;
    }

    private static void apply(BaseFragment f, float hue, String from, Runnable done) {
        if (hue < 0) {
            Toast.makeText(f.getParentActivity(), "Не нашёл яркого цвета — попробуй другую картинку", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean gradient = prefs().getBoolean("look_gradient", true);
        StarCosmetics.applyStyle(hue, gradient, false);
        prefs().edit().putInt("you_preset", -1).apply();
        Toast.makeText(f.getParentActivity(), "🪄 Тема в цвет " + from + " готова", Toast.LENGTH_SHORT).show();
        if (done != null) done.run();
    }

    public static void fromAvatar(BaseFragment f, Runnable done) {
        if (f.getParentActivity() == null || !StarMod.requirePro(f, "Тема из аватарки")) return;
        try {
            TLRPC.User me = UserConfig.getInstance(f.getCurrentAccount()).getCurrentUser();
            if (me == null || me.photo == null || me.photo.photo_small == null) {
                Toast.makeText(f.getParentActivity(), "Сначала поставь аватарку 🙂", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = FileLoader.getInstance(f.getCurrentAccount()).getPathToAttach(me.photo.photo_small, true);
            Bitmap b = file != null && file.exists() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
            if (b == null) {
                Toast.makeText(f.getParentActivity(), "Аватарка ещё не загрузилась — открой свой профиль и попробуй снова", Toast.LENGTH_SHORT).show();
                return;
            }
            float hue = dominantHue(b);
            b.recycle();
            apply(f, hue, "твоей аватарки", done);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void fromWallpaper(BaseFragment f, Runnable done) {
        if (f.getParentActivity() == null || !StarMod.requirePro(f, "Тема из обоев")) return;
        if (Build.VERSION.SDK_INT < 27) {
            Toast.makeText(f.getParentActivity(), "Нужен Android 8.1 или новее", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.app.WallpaperColors wc = WallpaperManager.getInstance(f.getParentActivity()).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) {
                apply(f, -1, "", done);
                return;
            }
            float[] hsv = new float[3];
            Color.colorToHSV(wc.getPrimaryColor().toArgb(), hsv);
            if (hsv[1] < 0.2f && wc.getSecondaryColor() != null) Color.colorToHSV(wc.getSecondaryColor().toArgb(), hsv);
            apply(f, hsv[1] < 0.15f ? -1 : hsv[0], "твоих обоев", done);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ================================================================ сезонные частицы в шапке

    public static boolean seasonOn() {
        return prefs().getBoolean("glow_season", true);
    }

    static String[] seasonSet() {
        int m = Calendar.getInstance().get(Calendar.MONTH);
        if (m == 11 || m <= 1) return new String[]{"❄️", "✨", "❄️"};
        if (m <= 4) return new String[]{"🌸", "🌼", "🌸"};
        if (m <= 7) return new String[]{"✨", "☀️", "🌟"};
        return new String[]{"🍂", "🍁", "🍂"};
    }

    /** Накладывает тихие частицы сезона поверх шапки StarTegs. */
    static void decorateHero(FrameLayout hero) {
        if (!seasonOn() || !StarPro.anim(LiteMode.FLAG_PARTICLES)) return;
        View p = new StarDecor.Particles(hero.getContext(), seasonSet());
        p.setAlpha(0.55f);
        hero.addView(p, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ================================================================ праздник нового уровня

    /** Из StarAchieve.check: если уровень вырос — красивая карточка. */
    static void checkLevel() {
        int lv = StarAchieve.level();
        int seen = prefs().getInt("glow_level_seen", 0);
        if (seen == 0) {
            prefs().edit().putInt("glow_level_seen", lv).apply();
            return;
        }
        if (lv <= seen) return;
        prefs().edit().putInt("glow_level_seen", lv).apply();
        AndroidUtilities.runOnUIThread(() -> levelUp(lv), 600);
    }

    static void levelUp(int lv) {
        BaseFragment f = LaunchActivity.getLastFragment();
        Activity a = f != null ? f.getParentActivity() : null;
        if (a == null) return;
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pd = AndroidUtilities.dp(24);
        card.setPadding(pd, pd, pd, pd);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFFB347, 0xFFFF5CA8, 0xFF8A5CFF});
        bg.setCornerRadius(AndroidUtilities.dp(22));
        card.setBackground(bg);
        TextView medal = StarYou.text(a, StarDecor.medal(lv), 64, Color.WHITE, false);
        medal.setGravity(Gravity.CENTER);
        card.addView(medal);
        TextView t = StarYou.text(a, "Новый уровень: " + lv + "!", 24, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        TextView s = StarYou.text(a, "Друзья со StarTegs видят твою медаль рядом с именем. Так держать! 🚀", 14, 0xEEFFFFFF, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        card.addView(s);
        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(18), 0);
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (StarPro.anim(LiteMode.FLAG_PARTICLES)) {
            View conf = new StarDecor.Particles(a, new String[]{"🎉", "⭐", "✨", "🎊"});
            wrap.addView(conf, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            medal.setScaleX(0.3f);
            medal.setScaleY(0.3f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(ObjectAnimator.ofFloat(medal, View.SCALE_X, 0.3f, 1f), ObjectAnimator.ofFloat(medal, View.SCALE_Y, 0.3f, 1f));
            set.setInterpolator(new OvershootInterpolator(3f));
            set.setDuration(700);
            set.setStartDelay(200);
            set.start();
        }
        new AlertDialog.Builder(a).setView(wrap).setPositiveButton("Ура! 🎉", null).show();
    }

    // ================================================================ баннер раздела и пустые экраны

    /** Цветной баннер в начале раздела StarTegs: эмодзи, название, описание — в цветах плитки. */
    static View banner(Context ctx, int section) {
        int[] t = StarYou.TILES[Math.min(section, StarYou.TILES.length - 1)];
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = AndroidUtilities.dp(16);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t[1], t[2]});
        bg.setCornerRadius(AndroidUtilities.dp(20));
        card.setBackground(bg);
        TextView e = StarYou.text(ctx, StarSections.SECTIONS[section][0], 34, Color.WHITE, false);
        card.addView(e);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(StarYou.text(ctx, StarSections.SECTIONS[section][1], 20, Color.WHITE, true));
        col.addView(StarYou.text(ctx, StarSections.SECTIONS[section][2], 13, 0xE6FFFFFF, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = AndroidUtilities.dp(14);
        card.addView(col, lp);
        card.setElevation(AndroidUtilities.dp(3)); // 5.6: мягкая тень
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, m, m, AndroidUtilities.dp(4));
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    /** Пустой экран: большой эмодзи, заголовок и подсказка. */
    static View empty(Context ctx, String emoji, String title, String hint) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = AndroidUtilities.dp(28);
        col.setPadding(p, AndroidUtilities.dp(36), p, AndroidUtilities.dp(36));
        TextView e = StarYou.text(ctx, emoji, 64, Color.WHITE, false);
        e.setGravity(Gravity.CENTER);
        col.addView(e);
        TextView t = StarYou.text(ctx, title, 20, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        col.addView(t);
        TextView h = StarYou.text(ctx, hint, 14, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        col.addView(h);
        if (StarPro.anim(LiteMode.FLAG_CHAT_BACKGROUND)) { // мягко «парит»
            ObjectAnimator fl = ObjectAnimator.ofFloat(e, View.TRANSLATION_Y, 0f, -AndroidUtilities.dp(8));
            fl.setDuration(1800);
            fl.setRepeatMode(ObjectAnimator.REVERSE);
            fl.setRepeatCount(ObjectAnimator.INFINITE);
            e.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    fl.start();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    fl.cancel();
                }
            });
        }
        return col;
    }
}
