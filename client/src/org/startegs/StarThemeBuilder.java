/*
 * StarTegs 4.5 — «Конструктор темы» (№27): цвет, градиентные пузыри, неон, материал, форма пузырей
 * и шрифт на одном экране с живым предпросмотром. «Применить» собирает тему на телефоне. Premium.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class StarThemeBuilder extends UniversalFragment {

    private int hue = 270, material = StarLook.bubbleMaterial(), shape = StarLook.bubbleShape(), font = StarLook.font();
    private boolean gradient = StarLook.gradientBubbles(), neon = StarLook.neon();

    private static final int ID_APPLY = 1, ID_GRADIENT = 2, ID_NEON = 3;
    private static final int ID_HUE = 100, ID_MAT = 200, ID_SHAPE = 300, ID_FONT = 400;

    @Override
    protected CharSequence getTitle() {
        return "🎨 Конструктор темы";
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }

    private View preview(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                neon ? new int[]{0xFF000000, 0xFF0A0A12} : new int[]{hsv(hue, 0.18f, 0.97f), hsv(hue, 0.30f, 0.90f)});
        bg.setCornerRadius(AndroidUtilities.dp(18));
        box.setBackground(bg);
        float radius = shape == 1 ? 26 : shape == 2 ? 4 : shape == 3 ? 40 : 16;
        String[] in = {"Привет! Как тебе новая тема? 👀", "Огонь 🔥"};
        String[] out = {"Сделал(а) сам(а) в StarTegs ✨", "Цвет, пузыри и шрифт — всё моё 😎"};
        for (int i = 0; i < 2; i++) {
            box.addView(bubble(ctx, in[i], false, radius));
            box.addView(bubble(ctx, out[i], true, radius));
        }
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, m, m, 0);
        wrap.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private View bubble(Context ctx, String text, boolean outgoing, float radius) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(15);
        int ph = AndroidUtilities.dp(12), pv = AndroidUtilities.dp(8);
        t.setPadding(ph, pv, ph, pv);
        String family = StarLook.FONTS[Math.max(0, Math.min(font, StarLook.FONTS.length - 1))][0];
        if (!family.isEmpty()) t.setTypeface(android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL));
        GradientDrawable d;
        int accent = hsv(hue, neon ? 0.85f : 0.62f, neon ? 1f : 0.86f);
        if (outgoing) {
            d = gradient ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{accent, hsv((hue + 40) % 360, 0.6f, 0.9f)}) : new GradientDrawable();
            if (!gradient) d.setColor(neon ? 0xFF000000 : hsv(hue, 0.25f, 0.98f));
            if (neon) d.setStroke(AndroidUtilities.dp(1.5f), accent);
            t.setTextColor(gradient ? Color.WHITE : neon ? accent : 0xFF111111);
        } else {
            d = new GradientDrawable();
            d.setColor(neon ? 0xFF111118 : Color.WHITE);
            t.setTextColor(neon ? 0xFFEEEEEE : 0xFF111111);
        }
        if (material == 1) d.setAlpha(200); // стекло
        if (material == 3) t.setShadowLayer(AndroidUtilities.dp(6), 0, 0, accent); // soft glow
        d.setCornerRadius(AndroidUtilities.dp(radius));
        t.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = outgoing ? Gravity.END : Gravity.START;
        lp.topMargin = AndroidUtilities.dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getParentActivity() != null) items.add(UItem.asCustom(preview(getParentActivity())));
        items.add(UItem.asButton(ID_APPLY, R.drawable.msg_palette, StarMod.isPro() ? "✅ Применить тему" : "✅ Применить тему  ⭐").accent());
        items.add(UItem.asHeader("Цвет"));
        for (int i = 0; i < StarTheme.HUES.length; i++) {
            int h = (Integer) StarTheme.HUES[i][1];
            items.add(UItem.asRadio(ID_HUE + i, (String) StarTheme.HUES[i][0]).setChecked(h == hue));
        }
        items.add(UItem.asHeader("Пузыри"));
        items.add(UItem.asCheck(ID_GRADIENT, "🌈 Градиентные пузыри").setChecked(gradient));
        items.add(UItem.asCheck(ID_NEON, "🌃 Неон на чёрном (OLED)").setChecked(neon));
        for (int i = 0; i < StarLook.MATERIAL_NAMES.length; i++) items.add(UItem.asRadio(ID_MAT + i, "Материал: " + StarLook.MATERIAL_NAMES[i]).setChecked(material == i));
        for (int i = 0; i < StarLook.SHAPE_NAMES.length; i++) items.add(UItem.asRadio(ID_SHAPE + i, "Форма: " + StarLook.SHAPE_NAMES[i]).setChecked(shape == i));
        items.add(UItem.asHeader("Шрифт"));
        for (int i = 0; i < StarLook.FONTS.length; i++) items.add(UItem.asRadio(ID_FONT + i, StarLook.FONTS[i][1]).setChecked(font == i));
        items.add(UItem.asShadow("Нажимай варианты — превью сверху меняется сразу. «Применить» соберёт тему; опубликовать её для других можно в «Темах от пользователей»."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_APPLY) {
            if (!StarMod.requirePro(this, "Конструктор темы")) return;
            StarLook.setBubbleMaterial(material);
            StarLook.setBubbleShape(shape);
            StarLook.setFont(font);
            StarCosmetics.applyStyle(hue, gradient, neon);
            if (getParentActivity() != null) Toast.makeText(getParentActivity(), "🎨 Тема применена", Toast.LENGTH_SHORT).show();
            return;
        }
        if (id == ID_GRADIENT) gradient = !gradient;
        else if (id == ID_NEON) neon = !neon;
        else if (id >= ID_HUE && id < ID_HUE + StarTheme.HUES.length) hue = (Integer) StarTheme.HUES[id - ID_HUE][1];
        else if (id >= ID_MAT && id < ID_MAT + StarLook.MATERIAL_NAMES.length) material = id - ID_MAT;
        else if (id >= ID_SHAPE && id < ID_SHAPE + StarLook.SHAPE_NAMES.length) shape = id - ID_SHAPE;
        else if (id >= ID_FONT && id < ID_FONT + StarLook.FONTS.length) font = id - ID_FONT;
        else return;
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
