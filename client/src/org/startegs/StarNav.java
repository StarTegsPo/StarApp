/*
 * StarTegs 6.4 «Навигатор»: удобные переходы по StarTegs.
 * - Плавающая стеклянная панель вкладок внизу каждого экрана StarTegs (Главная, Поиск, Стиль, Ускорить, Gold),
 *   прячется при прокрутке вниз и появляется при прокрутке вверх; вкладки не копят экраны в истории.
 * - «Недавние» и «Избранные» разделы на главном экране (избранное — долгое нажатие на плитку).
 * - Ярлыки разделов на рабочий стол телефона: открывают раздел StarTegs сразу.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
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

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public final class StarNav {

    private StarNav() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ключи мест: 0..22 — разделы StarSections, 23 стиль, 24 Gold, 25 оптимизация, 26 переезд, 27 поиск, 28 главная
    static final int SKIN = 23, GOLD = 24, OPT = 25, MOVE = 26, SEARCH = 27, HOME = 28;

    static String emoji(int key) {
        switch (key) {
            case OPT: return "🚀";
            case MOVE: return "🔁";
            case SEARCH: return "🔎";
            case HOME: return "⭐";
            default: return key >= 0 && key < StarSections.SECTIONS.length ? StarSections.SECTIONS[key][0] : "⭐";
        }
    }

    static String title(int key) {
        switch (key) {
            case OPT: return "Оптимизация";
            case MOVE: return "Переезд";
            case SEARCH: return "Поиск";
            case HOME: return "StarTegs";
            default: return key >= 0 && key < StarSections.SECTIONS.length ? StarSections.SECTIONS[key][1] : "StarTegs";
        }
    }

    static BaseFragment fragmentFor(int key) {
        switch (key) {
            case SKIN: return new StarSkin.Screen();
            case GOLD: return new StarGold.Screen();
            case OPT: return new StarOptimize.Screen();
            case MOVE: return new StarMove.Screen();
            case SEARCH: return new StarSections.Search();
            case HOME: return new StarSections.Index();
            default: return key >= 0 && key < StarSections.FIRST_SPECIAL ? new StarSections.Screen(key) : new StarSections.Index();
        }
    }

    static int keyOf(BaseFragment f) {
        if (f instanceof StarSections.Index) return HOME;
        if (f instanceof StarSections.Screen) return ((StarSections.Screen) f).section;
        if (f instanceof StarSkin.Screen) return SKIN;
        if (f instanceof StarGold.Screen) return GOLD;
        if (f instanceof StarOptimize.Screen) return OPT;
        if (f instanceof StarMove.Screen) return MOVE;
        if (f instanceof StarSections.Search) return SEARCH;
        return -1;
    }

    // ================================================================ недавние и избранные

    private static ArrayList<Integer> list(String k) {
        ArrayList<Integer> out = new ArrayList<>();
        for (String s : prefs().getString(k, "").split(",")) {
            try {
                if (!s.isEmpty()) out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    private static void save(String k, List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int i : l) sb.append(sb.length() == 0 ? "" : ",").append(i);
        prefs().edit().putString(k, sb.toString()).apply();
    }

    static void visit(int key) {
        if (key < 0 || key == HOME || key == SEARCH) return;
        ArrayList<Integer> l = list("nav_recent");
        l.remove(Integer.valueOf(key));
        l.add(0, key);
        while (l.size() > 8) l.remove(l.size() - 1);
        save("nav_recent", l);
    }

    static ArrayList<Integer> recents() {
        return list("nav_recent");
    }

    static ArrayList<Integer> favorites() {
        return list("nav_fav");
    }

    static boolean isFavorite(int key) {
        return favorites().contains(key);
    }

    /** true — теперь в избранном. */
    static boolean toggleFavorite(int key) {
        ArrayList<Integer> l = favorites();
        boolean on = !l.remove(Integer.valueOf(key));
        if (on) l.add(key);
        save("nav_fav", l);
        return on;
    }

    /** Строка чипов: ⭐ избранное, затем 🕘 недавние. null — если показывать нечего. */
    static View chips(BaseFragment f) {
        ArrayList<Integer> fav = favorites(), rec = recents();
        rec.removeAll(fav);
        if (fav.isEmpty() && rec.isEmpty()) return null;
        Context ctx = f.getParentActivity();
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ctx);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        for (int k : fav) row.addView(chip(f, k, true));
        for (int i = 0; i < Math.min(6, rec.size()); i++) row.addView(chip(f, rec.get(i), false));
        hs.addView(row);
        return hs;
    }

    private static View chip(BaseFragment f, int key, boolean fav) {
        Context ctx = f.getParentActivity();
        TextView t = StarYou.text(ctx, (fav ? "⭐ " : "🕘 ") + emoji(key) + " " + title(key), 14,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), fav);
        t.setSingleLine(true);
        int p = AndroidUtilities.dp(14);
        t.setPadding(p, AndroidUtilities.dp(8), p, AndroidUtilities.dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(18));
        int accent = StarSkin.skin().accent;
        bg.setColor(fav ? (accent & 0x00FFFFFF) | 0x33000000 : Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setStroke(AndroidUtilities.dp(1), (accent & 0x00FFFFFF) | (fav ? 0x99000000 : 0x33000000));
        t.setBackground(bg);
        t.setOnClickListener(v -> {
            StarPolish.tap(v);
            f.presentFragment(fragmentFor(key));
        });
        t.setOnLongClickListener(v -> {
            pinShortcut(f.getParentActivity(), key);
            return true;
        });
        StarSilk.spring(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = AndroidUtilities.dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    // ================================================================ нижняя панель вкладок

    static boolean barOn() {
        return prefs().getBoolean("nav_bar", true);
    }

    static void setBar(boolean on) {
        prefs().edit().putBoolean("nav_bar", on).apply();
    }

    static final int[] TABS = {HOME, SEARCH, SKIN, OPT, GOLD};
    static final String[] TAB_ICONS = {"🏠", "🔎", "🫧", "🚀", "👑"};
    static final String[] TAB_NAMES = {"Главная", "Поиск", "Стиль", "Ускорить", "Gold"};

    /** Из StarSilk.decorate: поставить панель на экран StarTegs (один раз). */
    static void attach(BaseFragment f, View list) {
        try {
            int key = keyOf(f);
            if (key < 0) return;
            visit(key);
            if (!barOn() || !(f.getFragmentView() instanceof FrameLayout) || f.getParentActivity() == null) return;
            FrameLayout root = (FrameLayout) f.getFragmentView();
            if (root.findViewWithTag("star_nav") != null) return;
            Context ctx = f.getParentActivity();
            Bar bar = new Bar(ctx);
            bar.setTag("star_nav");
            for (int i = 0; i < TABS.length; i++) {
                final int target = TABS[i];
                boolean active = target == key || (i == 0 && key == HOME);
                LinearLayout tab = new LinearLayout(ctx);
                tab.setOrientation(LinearLayout.VERTICAL);
                tab.setGravity(Gravity.CENTER);
                TextView ic = StarYou.text(ctx, TAB_ICONS[i], active ? 21 : 19, Color.WHITE, false);
                ic.setGravity(Gravity.CENTER);
                tab.addView(ic);
                TextView nm = StarYou.text(ctx, TAB_NAMES[i], 10, Theme.getColor(active ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText), active);
                nm.setGravity(Gravity.CENTER);
                tab.addView(nm);
                if (active) {
                    GradientDrawable sel = new GradientDrawable();
                    sel.setCornerRadius(AndroidUtilities.dp(22));
                    sel.setColor((StarSkin.skin().accent & 0x00FFFFFF) | 0x2E000000);
                    tab.setBackground(sel);
                }
                tab.setPadding(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5));
                tab.setOnClickListener(v -> {
                    StarPolish.tap(v);
                    if (!active) go(f, target);
                });
                tab.setOnLongClickListener(v -> {
                    pinShortcut(f.getParentActivity(), target);
                    return true;
                });
                StarSilk.spring(tab);
                bar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(60), Gravity.BOTTOM);
            lp.leftMargin = lp.rightMargin = AndroidUtilities.dp(14);
            lp.bottomMargin = AndroidUtilities.dp(14);
            root.addView(bar, lp);
            if (list instanceof RecyclerView) {
                RecyclerView rv = (RecyclerView) list;
                rv.setPadding(rv.getPaddingLeft(), rv.getPaddingTop(), rv.getPaddingRight(), AndroidUtilities.dp(84));
                rv.setClipToPadding(false);
                rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView r, int dx, int dy) {
                        if (Math.abs(dy) < 4) return;
                        boolean hide = dy > 0 && r.canScrollVertically(1);
                        float to = hide ? AndroidUtilities.dp(90) : 0;
                        if (bar.getTag(R_TARGET) == null || (float) bar.getTag(R_TARGET) != to) {
                            bar.setTag(R_TARGET, to);
                            bar.animate().translationY(to).setDuration(220).start();
                        }
                    }
                });
            }
            if (StarSilk.motion()) {
                bar.setTranslationY(AndroidUtilities.dp(90));
                bar.animate().translationY(0).setStartDelay(120).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
            }
        } catch (Throwable ignore) {
        }
    }

    private static final int R_TARGET = 0x7f5a0001;

    /** Переход по вкладке: «Главная» возвращает к уже открытому главному экрану, остальные заменяют текущий экран. */
    static void go(BaseFragment f, int target) {
        if (f.getParentLayout() == null) return;
        List<BaseFragment> stack = f.getParentLayout().getFragmentStack();
        int idx = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (keyOf(stack.get(i)) == target) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) { // уже открыт ниже в истории — возвращаемся к нему
            ArrayList<BaseFragment> remove = new ArrayList<>();
            for (int i = idx + 1; i < stack.size() - 1; i++) remove.add(stack.get(i));
            for (BaseFragment r : remove) f.getParentLayout().removeFragmentFromStack(r);
            f.finishFragment();
            return;
        }
        boolean starBelow = stack.size() >= 2 && keyOf(stack.get(stack.size() - 2)) >= 0;
        // если под нами есть экран StarTegs — заменяем текущий (история не растёт), иначе просто открываем
        f.presentFragment(fragmentFor(target), starBelow && keyOf(f) != HOME);
    }

    /** Стеклянная подложка панели: полупрозрачный фон, светлая кромка, тень в цвет стиля. */
    static final class Bar extends LinearLayout {
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        Bar(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            int p = AndroidUtilities.dp(5);
            setPadding(p, p, p, p);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(30));
            int base = Theme.getColor(Theme.key_windowBackgroundWhite);
            bg.setColor((base & 0x00FFFFFF) | 0xE6000000);
            setBackground(bg);
            setElevation(AndroidUtilities.dp(10));
            if (Build.VERSION.SDK_INT >= 28) {
                setOutlineSpotShadowColor(StarSkin.skin().accent);
                setOutlineAmbientShadowColor(StarSkin.skin().accent);
            }
            setWillNotDraw(false);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(AndroidUtilities.dp(1));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float s = edge.getStrokeWidth() / 2;
            r.set(s, s, getWidth() - s, getHeight() - s);
            edge.setShader(new LinearGradient(0, 0, 0, getHeight(), 0x88FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, AndroidUtilities.dp(30), AndroidUtilities.dp(30), edge);
        }
    }

    // ================================================================ ярлыки на рабочий стол

    static final String ACTION = "org.startegs.OPEN";

    private static Bitmap icon(int key) {
        int size = AndroidUtilities.dp(48);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int[] col = key < StarYou.TILES.length ? new int[]{StarYou.TILES[key][1], StarYou.TILES[key][2]} : new int[]{0xFF7B5CFF, 0xFF33A6FF};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size, col[0], col[1], Shader.TileMode.CLAMP));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setShader(null);
        p.setTextSize(size * 0.48f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(emoji(key), size / 2f, size / 2f - (fm.ascent + fm.descent) / 2, p);
        return b;
    }

    static void pinShortcut(Activity a, int key) {
        if (a == null) return;
        try {
            if (Build.VERSION.SDK_INT < 26) {
                Toast.makeText(a, "Ярлыки на рабочий стол — на Android 8 и новее", Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.pm.ShortcutManager sm = a.getSystemService(android.content.pm.ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                Toast.makeText(a, "Лаунчер не поддерживает ярлыки", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(a, a.getClass());
            i.setAction(ACTION);
            i.putExtra("startegs_key", key);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(a, "startegs_" + key)
                    .setShortLabel(title(key))
                    .setIcon(android.graphics.drawable.Icon.createWithBitmap(icon(key)))
                    .setIntent(i).build();
            sm.requestPinShortcut(info, null);
            Toast.makeText(a, "📌 Ярлык «" + title(key) + "» — подтверди добавление на рабочий стол", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(a, "Не получилось добавить ярлык", Toast.LENGTH_SHORT).show();
        }
    }

    /** Из LaunchActivity.handleIntent: ярлык StarTegs → открыть раздел, когда интерфейс готов. */
    public static void handleIntent(Intent intent) {
        try {
            if (intent == null || !ACTION.equals(intent.getAction())) return;
            int key = intent.getIntExtra("startegs_key", -1);
            if (key < 0) return;
            intent.setAction(Intent.ACTION_MAIN); // дальше Telegram обработает как обычный запуск
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment last = org.telegram.ui.LaunchActivity.getLastFragment();
                if (last != null) last.presentFragment(fragmentFor(key));
            }, 450);
        } catch (Throwable ignore) {
        }
    }
}
