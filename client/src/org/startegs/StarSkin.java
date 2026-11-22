/*
 * StarTegs 6.2 «Стиль интерфейса»: 18 стилей с живыми превью — iOS Liquid Glass, iOS Classic, Material You,
 * Neon Cyber, Y2K Aqua, Minimal Mono, Pastel, Sunset Retro, Glass Dark, Paper и др.; Gold Luxe, Diamond и
 * Aurora Gold — для StarTegs Gold. Стиль меняет шапки, карточки и рамки StarTegs и (по желанию) перекрашивает
 * сам Telegram: оттенок темы, градиентные пузыри, материал и форма пузырей, размытие.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public final class StarSkin {

    private StarSkin() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    /** Один стиль интерфейса. */
    static final class Skin {
        final String name, about;
        final int barA, barB, bgA, bgB, accent, radius;
        final boolean glass, dark, gold;
        final float hue;
        final boolean gradient, neon;
        final int material, shape;

        Skin(String name, String about, int barA, int barB, int bgA, int bgB, int accent, int radius, boolean glass, boolean dark,
             float hue, boolean gradient, boolean neon, int material, int shape, boolean gold) {
            this.name = name;
            this.about = about;
            this.barA = barA;
            this.barB = barB;
            this.bgA = bgA;
            this.bgB = bgB;
            this.accent = accent;
            this.radius = radius;
            this.glass = glass;
            this.dark = dark;
            this.hue = hue;
            this.gradient = gradient;
            this.neon = neon;
            this.material = material;
            this.shape = shape;
            this.gold = gold;
        }
    }

    // material: 0 классика, 1 стекло, 2 матовый, 3 soft glow, 4 бумага; shape: 0..3 как в StarLook
    static final Skin[] SKINS = {
            new Skin("✨ Классика StarTegs", "Свой цвет у каждого раздела", 0, 0, 0xFFEDE7FF, 0xFFDDF0FF, 0xFF7B5CFF, 22, false, false, -1, false, false, 0, 0, false),
            new Skin("🫧 iOS Liquid Glass", "Жидкое стекло, блики и размытие как в iOS 26", 0xFF7FB2FF, 0xFFC59BFF, 0xFFBFE3FF, 0xFFF5D0FF, 0xFF0A84FF, 28, true, false, 212, true, false, 1, 3, false),
            new Skin("🍏 iOS Classic", "Чистый белый, системный синий", 0xFF007AFF, 0xFF34AADC, 0xFFF2F2F7, 0xFFFFFFFF, 0xFF007AFF, 14, false, false, 211, false, false, 0, 3, false),
            new Skin("🎨 Material You", "Мягкие тона и крупные скругления Android", 0xFF6750A4, 0xFF7D5260, 0xFFEADDFF, 0xFFFFD8E4, 0xFF6750A4, 28, false, false, 262, false, false, 2, 1, false),
            new Skin("🌃 Neon Cyber", "Неон на чёрном, киберпанк", 0xFFFF00E5, 0xFF00F0FF, 0xFF0B0B1A, 0xFF1A0B2E, 0xFFFF00E5, 12, false, true, 295, true, true, 3, 0, false),
            new Skin("💿 Y2K Aqua", "Глянцевые кнопки нулевых", 0xFF00C6FF, 0xFF0072FF, 0xFFD6F4FF, 0xFFE9F0FF, 0xFF0091FF, 24, true, false, 200, true, false, 1, 1, false),
            new Skin("⬛ Minimal Mono", "Ничего лишнего, строгий чёрно-белый", 0xFF1C1C1E, 0xFF3A3A3C, 0xFFF4F4F4, 0xFFE6E6E6, 0xFF1C1C1E, 10, false, false, 220, false, false, 0, 2, false),
            new Skin("🍬 Pastel", "Нежные пастельные цвета", 0xFFFFB5E8, 0xFFB5DEFF, 0xFFFFF0FA, 0xFFEAF6FF, 0xFFFF7EC8, 26, false, false, 320, true, false, 2, 3, false),
            new Skin("🌅 Sunset Retro", "Закат, синтвейв, 80-е", 0xFFFF5F6D, 0xFFFFC371, 0xFF2D1B45, 0xFF6B2C5A, 0xFFFF5F6D, 18, false, true, 12, true, true, 3, 1, false),
            new Skin("🌑 Glass Dark", "Тёмное стекло для ночи и OLED", 0xFF232526, 0xFF414345, 0xFF0E0F12, 0xFF22252B, 0xFF8AB4FF, 24, true, true, 222, false, true, 1, 3, false),
            new Skin("📜 Paper", "Бумага и тёплые чернила", 0xFFC8A97E, 0xFFA47E58, 0xFFF7EFE1, 0xFFEADBC4, 0xFF8B5E34, 8, false, false, 33, false, false, 4, 2, false),
            new Skin("🌿 Nature", "Свежая зелень", 0xFF56AB2F, 0xFFA8E063, 0xFFEFFBE6, 0xFFDDF5CE, 0xFF3E9A1E, 22, false, false, 105, true, false, 2, 1, false),
            new Skin("🍓 Berry", "Ягодный градиент", 0xFFE0115F, 0xFF8E2DE2, 0xFFFFE3EF, 0xFFEDE1FF, 0xFFE0115F, 22, false, false, 330, true, false, 0, 1, false),
            new Skin("🌊 Ocean Deep", "Глубокий океан", 0xFF2193B0, 0xFF6DD5ED, 0xFF06283D, 0xFF0F4C75, 0xFF6DD5ED, 22, true, true, 195, true, true, 1, 3, false),
            new Skin("🎮 Gamer RGB", "RGB-подсветка как у игровой клавиатуры", 0xFFFF0080, 0xFF7928CA, 0xFF0A0A0A, 0xFF16112A, 0xFF00FF88, 16, false, true, 285, true, true, 3, 0, false),
            new Skin("👑 Gold Luxe", "Чёрное золото — самый дорогой вид", 0xFFFFD86B, 0xFFB8860B, 0xFF0C0A05, 0xFF2B2108, 0xFFFFC94D, 24, true, true, 42, true, true, 3, 3, true),
            new Skin("💎 Diamond", "Холодный блеск бриллианта", 0xFFE0F7FF, 0xFFB39DDB, 0xFFF3FBFF, 0xFFEDE4FF, 0xFF7FD7FF, 30, true, false, 200, true, false, 1, 3, true),
            new Skin("🌌 Aurora Gold", "Северное сияние на ночном небе", 0xFF00FFA3, 0xFF7A5CFF, 0xFF050B1A, 0xFF101A3A, 0xFF00FFA3, 26, true, true, 160, true, true, 3, 3, true),
    };

    static int current() {
        int i = prefs().getInt("skin", 0);
        if (i < 0 || i >= SKINS.length) return 0;
        if (SKINS[i].gold && !StarGold.active()) return 0; // Gold закончился — тихо возвращаем классику
        return i;
    }

    static Skin skin() {
        return SKINS[current()];
    }

    static boolean paintTelegram() {
        return prefs().getBoolean("skin_tg", true);
    }

    /** Цвета шапки: у «Классики» — цвета раздела, иначе — цвета стиля. */
    static int[] bar(int[] section) {
        Skin s = skin();
        return s.barA == 0 ? section : new int[]{s.barA, s.barB};
    }

    static int radius() {
        return AndroidUtilities.dp(skin().radius);
    }

    /** Цвета бегущей рамки вокруг карточек. */
    static int[] ring() {
        Skin s = skin();
        if (current() == 0) return new int[]{0xFFFF7EB3, 0xFFFFD166, 0xFF5EDB76, 0xFF4FB2FF, 0xFFB46CFF, 0xFFFF7EB3};
        return new int[]{s.barA, s.accent, s.barB, 0xFFFFFFFF, s.barA};
    }

    /** Из StarSilk.card: скругление и «стекло» под выбранный стиль. */
    static void style(View v) {
        Skin s = skin();
        Drawable d = v.getBackground();
        if (d instanceof GradientDrawable) {
            GradientDrawable g = (GradientDrawable) d;
            g.setCornerRadius(AndroidUtilities.dp(s.radius));
            if (s.glass) g.setStroke(AndroidUtilities.dp(1), s.dark ? 0x40FFFFFF : 0x99FFFFFF);
            else if (s.neon) g.setStroke(AndroidUtilities.dp(1.2f), s.accent);
        }
        if (Build.VERSION.SDK_INT >= 28 && current() != 0) {
            v.setOutlineSpotShadowColor(s.accent);
            v.setOutlineAmbientShadowColor(s.accent);
        }
    }

    static void apply(BaseFragment f, int i, Runnable done) {
        Skin s = SKINS[i];
        if (s.gold && !StarGold.require(f, "Стиль «" + s.name.substring(s.name.indexOf(' ') + 1) + "»")) return;
        prefs().edit().putInt("skin", i).apply();
        if (paintTelegram() && s.hue >= 0) {
            StarLook.setBubbleMaterial(s.material);
            StarLook.setBubbleShape(s.shape);
            StarCosmetics.applyStyle(s.hue, s.gradient, s.neon);
            if (s.glass && StarLook.glassAvailable() && !StarLook.glass()) StarLook.toggleGlass(f);
        }
        if (f.getParentActivity() != null) {
            Toast.makeText(f.getParentActivity(), "🎨 Стиль «" + s.name.substring(s.name.indexOf(' ') + 1) + "» включён", Toast.LENGTH_SHORT).show();
        }
        StarPolish.tap(f.getFragmentView());
        if (done != null) done.run();
    }

    // ================================================================ живое превью стиля

    static final class Preview extends View {
        final Skin s;
        boolean selected;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final long start = System.currentTimeMillis();

        Preview(Context c, Skin s) {
            super(c);
            this.s = s;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = AndroidUtilities.dp(Math.min(18, s.radius));
            boolean anim = StarSilk.motion();
            float t = anim ? (System.currentTimeMillis() - start) / 1000f : 0;
            // фон: «меш»-градиент из двух пятен
            r.set(0, 0, w, h);
            p.setShader(new LinearGradient(0, 0, w, h, s.bgA, s.bgB, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, p);
            int save = c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            int barA = s.barA == 0 ? 0xFF7B5CFF : s.barA, barB = s.barB == 0 ? 0xFF33A6FF : s.barB;
            float bx = w * (0.25f + 0.15f * (float) Math.sin(t * 0.9)), by = h * (0.45f + 0.1f * (float) Math.cos(t * 0.7));
            p.setShader(new RadialGradient(bx, by, w * 0.7f, (barA & 0x00FFFFFF) | 0x77000000, barA & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(new RadialGradient(w * 0.85f, h * 0.8f, w * 0.6f, (barB & 0x00FFFFFF) | 0x66000000, barB & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            // шапка
            float bh = AndroidUtilities.dp(20);
            p.setShader(new LinearGradient(0, 0, w, 0, s.glass ? (barA & 0x00FFFFFF) | 0xAA000000 : barA, s.glass ? (barB & 0x00FFFFFF) | 0xAA000000 : barB, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, bh, p);
            p.setShader(null);
            p.setColor(0xCCFFFFFF);
            r.set(AndroidUtilities.dp(8), bh / 2 - AndroidUtilities.dp(2), w * 0.5f, bh / 2 + AndroidUtilities.dp(2));
            c.drawRoundRect(r, AndroidUtilities.dp(2), AndroidUtilities.dp(2), p);
            // входящий пузырь (стекло/белый) и исходящий (акцент)
            float br = AndroidUtilities.dp(Math.max(4, s.radius / 3f));
            r.set(AndroidUtilities.dp(8), bh + AndroidUtilities.dp(10), w * 0.68f, bh + AndroidUtilities.dp(30));
            p.setColor(s.glass ? (s.dark ? 0x33FFFFFF : 0x88FFFFFF) : (s.dark ? 0xFF2A2A35 : 0xFFFFFFFF));
            c.drawRoundRect(r, br, br, p);
            if (s.glass) glassEdge(c, br);
            r.set(w * 0.32f, bh + AndroidUtilities.dp(38), w - AndroidUtilities.dp(8), bh + AndroidUtilities.dp(58));
            if (s.gradient) p.setShader(new LinearGradient(r.left, 0, r.right, 0, barA, s.accent, Shader.TileMode.CLAMP));
            else p.setColor(s.accent);
            c.drawRoundRect(r, br, br, p);
            p.setShader(null);
            if (s.neon) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(1.2f));
                p.setColor(s.accent);
                p.setAlpha(140 + (int) (100 * Math.abs(Math.sin(t * 2))));
                c.drawRoundRect(r, br, br, p);
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(255);
            }
            // парящая панель внизу
            float nb = AndroidUtilities.dp(22);
            r.set(AndroidUtilities.dp(12), h - nb - AndroidUtilities.dp(8), w - AndroidUtilities.dp(12), h - AndroidUtilities.dp(8));
            p.setColor(s.glass ? (s.dark ? 0x40FFFFFF : 0x99FFFFFF) : (s.dark ? 0xFF1E1E26 : 0xFFFFFFFF));
            c.drawRoundRect(r, nb / 2, nb / 2, p);
            if (s.glass) glassEdge(c, nb / 2);
            p.setColor(s.accent);
            for (int i = 0; i < 4; i++) {
                float cx = r.left + (r.width() / 4f) * (i + 0.5f);
                p.setAlpha(i == 0 ? 255 : 110);
                c.drawCircle(cx, r.centerY(), AndroidUtilities.dp(3.5f), p);
            }
            p.setAlpha(255);
            // блик Gold/Diamond
            if (s.gold && anim) {
                float x = ((t * 0.5f) % 1.6f - 0.3f) * w;
                p.setShader(new LinearGradient(x - w * 0.15f, 0, x + w * 0.15f, h * 0.3f, new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }
            c.restoreToCount(save);
            if (selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(3));
                p.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                r.set(AndroidUtilities.dp(1.5f), AndroidUtilities.dp(1.5f), w - AndroidUtilities.dp(1.5f), h - AndroidUtilities.dp(1.5f));
                c.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.FILL);
            }
            // анимируем только выбранный стиль и стили Gold — остальные превью статичны (меньше нагрузка)
            if (anim && isShown() && (selected || s.gold)) postInvalidateDelayed(50);
        }

        private void glassEdge(Canvas c, float rad) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(AndroidUtilities.dp(1));
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, 0xCCFFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private static View cell(BaseFragment f, int i, int sel, int wDp, int hDp, Runnable done) {
        Context ctx = f.getParentActivity();
        Skin s = SKINS[i];
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        Preview pv = new Preview(ctx, s);
        pv.selected = sel == i;
        cell.addView(pv, new LinearLayout.LayoutParams(AndroidUtilities.dp(wDp), AndroidUtilities.dp(hDp)));
        String label = s.name + (s.gold && !StarGold.active() ? " 🔒" : "");
        TextView t = StarYou.text(ctx, label, 12, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), sel == i);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(wDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(6);
        cell.addView(t, lp);
        cell.setOnClickListener(v -> apply(f, i, done));
        StarSilk.spring(cell);
        return cell;
    }

    /** Горизонтальная лента стилей (для главного экрана StarTegs). */
    static View gallery(BaseFragment f, Runnable done) {
        HorizontalScrollView hs = new HorizontalScrollView(f.getParentActivity());
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(f.getParentActivity());
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        int sel = current();
        for (int i = 0; i < SKINS.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(10);
            row.addView(cell(f, i, sel, 96, 132, done), lp);
        }
        hs.addView(row);
        return hs;
    }

    /** Сетка 2 колонки (для экрана стилей). */
    private static View grid(BaseFragment f, int from, int to, Runnable done) {
        Context ctx = f.getParentActivity();
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        int sel = current();
        int wDp = (int) ((AndroidUtilities.displaySize.x / AndroidUtilities.density - 24 - 12) / 2);
        for (int i = from; i < to; i += 2) {
            LinearLayout row = new LinearLayout(ctx);
            for (int k = i; k < Math.min(i + 2, to); k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (k == i) lp.rightMargin = AndroidUtilities.dp(12);
                row.addView(cell(f, k, sel, wDp, (int) (wDp * 1.3f), done), lp);
            }
            if (to - i == 1) row.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = AndroidUtilities.dp(14);
            col.addView(row, rp);
        }
        return col;
    }

    // ================================================================ экран «Стиль интерфейса»

    public static class Screen extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "🎨 Стиль интерфейса";
        }

        @Override
        public View createView(Context context) {
            View v = super.createView(context);
            StarSilk.decorate(this, StarSilk.PINK, listView);
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        private void refresh() {
            if (actionBar != null) StarSilk.decorate(this, StarSilk.PINK, null);
            if (listView != null) listView.adapter.update(true);
        }

        private int goldFrom() {
            for (int i = 0; i < SKINS.length; i++) if (SKINS[i].gold) return i;
            return SKINS.length;
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            Skin cur = skin();
            items.add(UItem.asShadow("Сейчас: " + cur.name + " — " + cur.about + ". Нажми на превью, чтобы примерить."));
            items.add(UItem.asCheck(1, "Перекрашивать и сам Telegram").setChecked(paintTelegram()));
            items.add(UItem.asShadow("Вместе со StarTegs поменяются тема Telegram, форма и материал пузырей и размытие. Выключи, если хочешь менять только экраны StarTegs."));
            if (getParentActivity() == null) return;
            int g = goldFrom();
            items.add(UItem.asHeader("Стили"));
            items.add(UItem.asCustom(grid(this, 0, g, this::refresh)));
            items.add(UItem.asHeader("👑 Стили Gold"));
            items.add(UItem.asCustom(grid(this, g, SKINS.length, this::refresh)));
            if (!StarGold.active()) items.add(UItem.asButton(2, "👑 Открыть стили Gold — " + StarGold.price() + " ⭐", "").accent());
            items.add(UItem.asHeader("Ещё оформление"));
            items.add(UItem.asButton(3, org.telegram.messenger.R.drawable.msg_palette, "🎨 Темы одним касанием", ""));
            items.add(UItem.asButton(4, org.telegram.messenger.R.drawable.msg_palette, "🛠 Конструктор своей темы", ""));
            items.add(UItem.asButton(5, org.telegram.messenger.R.drawable.msg_palette, "💎 Рамки, значки и эффекты", ""));
            items.add(UItem.asButton(6, org.telegram.messenger.R.drawable.msg_palette, "🖌 Раздел «Дизайн» целиком", ""));
            items.add(UItem.asShadow("Стиль хранится только на этом телефоне. Gold-стиль после окончания Gold сам вернётся к классике."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            StarPolish.tap(view);
            switch (item.id) {
                case 1:
                    prefs().edit().putBoolean("skin_tg", !paintTelegram()).apply();
                    refresh();
                    return;
                case 2: presentFragment(new StarGold.Screen()); return;
                case 3: presentFragment(new StarSections.Screen(9)); return;
                case 4: presentFragment(new StarThemeBuilder()); return;
                case 5: presentFragment(new StarCosmetics.Shop()); return;
                case 6: presentFragment(new StarSections.Screen(20)); return;
            }
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
