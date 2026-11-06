/*
 * StarTegs 2.2 — оформление: стеклянные панели, градиентные пузыри, неоновый OLED-режим, шрифты,
 * анимации сообщений, тема по аватарке, компактный режим, цветные папки и сезонная иконка.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LauncherIconController;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class StarLook {

    private StarLook() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    private static void toggleFlag(String key, boolean def) {
        prefs().edit().putBoolean(key, !flag(key, def)).apply();
    }

    // ================================================================ 1. стеклянные панели

    public static boolean glassAvailable() {
        return SharedConfig.canBlurChat();
    }

    public static boolean glass() {
        return SharedConfig.chatBlurEnabled();
    }

    static void toggleGlass(BaseFragment f) {
        if (!glassAvailable()) {
            if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), "Этот телефон не тянет размытие — Telegram отключил его для экономии", Toast.LENGTH_LONG).show();
            return;
        }
        SharedConfig.toggleChatBlur();
    }

    // ================================================================ 2 и 4. градиентные пузыри и неон OLED

    public static boolean gradientBubbles() {
        return flag("look_gradient", false);
    }

    public static boolean neon() {
        return flag("look_neon", false);
    }

    /** Переключить и пересобрать тему StarTegs. */
    static void toggleThemeFlag(BaseFragment f, String key) {
        toggleFlag(key, false);
        boolean night = key.equals("look_neon") && neon(); // неон — это тёмная тема
        if (!StarTheme.reapply(night)) StarTheme.enableDefault(night); // не было нашей темы — включаем фирменную
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{(h % 360 + 360) % 360, Math.max(0, Math.min(1, s)), Math.max(0, Math.min(1, v))});
    }

    private static int parse(String v) {
        if (v.startsWith("#")) {
            int c = (int) Long.parseLong(v.substring(1), 16);
            return v.length() == 7 ? c | 0xFF000000 : c;
        }
        return (int) Long.parseLong(v);
    }

    private static final String[][] WHITE_OUT = {
            {"chat_messageTextOut", "FFFFFFFF"}, {"chat_messageLinkOut", "FFFFFFFF"}, {"chat_outTimeText", "B3FFFFFF"},
            {"chat_outTimeSelectedText", "CCFFFFFF"}, {"chat_outSentCheck", "CCFFFFFF"}, {"chat_outSentCheckRead", "FFFFFFFF"},
            {"chat_outSentCheckSelected", "CCFFFFFF"}, {"chat_outSentCheckReadSelected", "FFFFFFFF"}, {"chat_outSentClock", "B3FFFFFF"},
            {"chat_outSentClockSelected", "B3FFFFFF"}, {"chat_outReplyLine", "FFFFFFFF"}, {"chat_outReplyLine2", "B3FFFFFF"},
            {"chat_outReplyNameText", "FFFFFFFF"}, {"chat_outReplyMessageText", "E6FFFFFF"}, {"chat_outReplyMediaMessageText", "CCFFFFFF"},
            {"chat_outReplyMediaMessageSelectedText", "CCFFFFFF"}, {"chat_outForwardedNameText", "FFFFFFFF"}, {"chat_outPreviewLine", "FFFFFFFF"},
            {"chat_outSiteNameText", "FFFFFFFF"}, {"chat_outViews", "B3FFFFFF"}, {"chat_outQuote", "FFFFFFFF"},
            {"chat_outLinkSelectBackground", "33FFFFFF"}, {"chat_outTextSelectionHighlight", "4DFFFFFF"}, {"chat_outTextSelectionCursor", "FFFFFFFF"},
            {"chat_outFileNameText", "FFFFFFFF"}, {"chat_outFileInfoText", "CCFFFFFF"}, {"chat_outFileInfoSelectedText", "CCFFFFFF"},
            {"chat_outAudioTitleText", "FFFFFFFF"}, {"chat_outAudioDurationText", "CCFFFFFF"}, {"chat_outAudioDurationSelectedText", "CCFFFFFF"},
            {"chat_outAudioPerfomerText", "CCFFFFFF"}, {"chat_outAudioPerfomerSelectedText", "CCFFFFFF"}, {"chat_outContactNameText", "FFFFFFFF"},
            {"chat_outContactPhoneText", "CCFFFFFF"}, {"chat_outContactPhoneSelectedText", "CCFFFFFF"}, {"chat_outAudioSeekbar", "66FFFFFF"},
            {"chat_outAudioSeekbarFill", "FFFFFFFF"}, {"chat_outAudioSeekbarSelected", "66FFFFFF"}, {"chat_outAudioCacheSeekbar", "40FFFFFF"},
            {"chat_outVoiceSeekbar", "66FFFFFF"}, {"chat_outVoiceSeekbarFill", "FFFFFFFF"}, {"chat_outVoiceSeekbarSelected", "66FFFFFF"},
            {"chat_outLoader", "33FFFFFF"}, {"chat_outLoaderSelected", "4DFFFFFF"}, {"chat_outMediaIcon", "FFFFFFFF"},
            {"chat_outMediaIconSelected", "FFFFFFFF"}, {"chat_outFileProgress", "FFFFFFFF"}, {"chat_outFileProgressSelected", "FFFFFFFF"},
            {"chat_outAudioProgress", "FFFFFFFF"}, {"chat_outAudioSelectedProgress", "FFFFFFFF"}, {"chat_outInstant", "FFFFFFFF"},
            {"chat_outPreviewInstantText", "FFFFFFFF"}, {"chat_outMenu", "B3FFFFFF"}, {"chat_outMenuSelected", "B3FFFFFF"},
            {"chat_outAdminText", "B3FFFFFF"}, {"chat_outCodeBackground", "26000000"},
    };

    private static String hex(int c) {
        return String.format(Locale.ROOT, "#%08X", c);
    }

    /** Вызывается StarTheme при сборке каждого файла темы. */
    static String processTheme(String text, boolean night) {
        boolean grad = gradientBubbles(), oled = neon() && night;
        if (!grad && !oled) return text;
        String[] lines = text.split("\n", -1);
        LinkedHashMap<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            int eq = lines[i].indexOf('=');
            if (eq > 0 && !lines[i].startsWith("#")) idx.put(lines[i].substring(0, eq).trim(), i);
        }
        LinkedHashMap<String, String> set = new LinkedHashMap<>();
        float accentHue = 260;
        Integer ai = idx.get("chats_actionBackground");
        if (ai != null) {
            try {
                float[] h = new float[3];
                Color.colorToHSV(parse(lines[ai].substring(lines[ai].indexOf('=') + 1).trim()), h);
                if (h[1] > 0.2f) accentHue = h[0];
            } catch (Exception ignore) {
            }
        }
        if (oled) {
            // все тёмные фоны → чёрный (OLED), акценты → яркие, «светящиеся»
            for (Map.Entry<String, Integer> e : idx.entrySet()) {
                String k = e.getKey(), line = lines[e.getValue()];
                try {
                    int c = parse(line.substring(line.indexOf('=') + 1).trim());
                    float[] h = new float[3];
                    Color.colorToHSV(c, h);
                    int alpha = c & 0xFF000000;
                    if ((k.contains("ackground") || k.equals("chat_wallpaper") || k.startsWith("actionBarDefault") && !k.contains("Icon") && !k.contains("Title")
                            || k.equals("chat_inBubble")) && h[2] < 0.35f && alpha == 0xFF000000) {
                        set.put(k, hex(k.equals("chat_inBubble") ? 0xFF16161E : k.equals("windowBackgroundGray") || k.equals("chat_wallpaper") ? 0xFF000000 : 0xFF050507));
                    } else if (h[1] > 0.35f && h[2] > 0.35f) {
                        set.put(k, hex(alpha | (hsv(h[0], Math.min(1f, h[1] * 1.15f), 1f) & 0x00FFFFFF)));
                    }
                } catch (Exception ignore) {
                }
            }
            set.put("chat_outBubble", hex(hsv(accentHue, 0.75f, 0.32f)));
            set.put("chat_outBubbleSelected", hex(hsv(accentHue, 0.75f, 0.40f)));
            set.put("chat_inBubbleSelected", hex(0xFF22222C));
            set.put("chat_inBubbleShadow", hex(0x00000000));
            set.put("chat_outBubbleShadow", hex(0x00000000));
            set.put("chat_messagePanelBackground", hex(0xFF050507));
        }
        if (grad) {
            float v1 = night ? 0.62f : 0.92f, v2 = night ? 0.55f : 0.86f;
            if (oled) {
                v1 = 0.55f;
                v2 = 0.45f;
            }
            int g1 = hsv(accentHue, 0.72f, v1), g2 = hsv(accentHue + 38, 0.78f, v2);
            set.put("chat_outBubble", hex(g1));
            set.put("chat_outBubbleGradient", hex(g1));
            set.put("chat_outBubbleGradient2", hex(g2));
            set.put("chat_outBubbleGradientAnimated", "1");
            set.put("chat_outBubbleSelected", hex(hsv(accentHue, 0.72f, v1 * 0.85f)));
            set.put("chat_outBubbleGradientSelectedOverlay", "#14000000");
            for (String[] w : WHITE_OUT) set.put(w[0], "#" + w[1]);
        }
        StringBuilder sb = new StringBuilder();
        int wps = -1;
        for (int i = 0; i < lines.length; i++) if (lines[i].startsWith("WPS")) wps = i;
        for (int i = 0; i < lines.length; i++) {
            if (i == wps) {
                appendMissing(sb, set, idx);
            }
            int eq = lines[i].indexOf('=');
            if (eq > 0 && !lines[i].startsWith("#")) {
                String k = lines[i].substring(0, eq).trim();
                if (set.containsKey(k)) {
                    sb.append(k).append('=').append(set.get(k));
                    if (i < lines.length - 1) sb.append('\n');
                    continue;
                }
            }
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append('\n');
        }
        if (wps < 0) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            appendMissing(sb, set, idx);
        }
        return sb.toString();
    }

    private static void appendMissing(StringBuilder sb, Map<String, String> set, Map<String, Integer> idx) {
        for (Map.Entry<String, String> e : set.entrySet()) {
            if (!idx.containsKey(e.getKey())) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
    }

    // ================================================================ 5. шрифты

    static final String[][] FONTS = {
            {"", "Обычный (как в Telegram)"}, {"sans-serif-condensed", "Узкий"}, {"sans-serif-light", "Лёгкий"},
            {"serif", "С засечками — строгий"}, {"monospace", "Моноширинный — как код"}, {"casual", "Игривый"}, {"cursive", "Рукописный"},
    };

    static int font() {
        int f = prefs().getInt("look_font", 0);
        return f >= 0 && f < FONTS.length ? f : 0;
    }

    static void setFont(int i) {
        prefs().edit().putInt("look_font", i).apply();
        cachedFor = -1;
        applyFonts();
    }

    private static int cachedFor = -1;
    private static Typeface regular, bold;
    private static final HashSet<Typeface> ours = new HashSet<>();

    private static void ensure() {
        int f = font();
        if (cachedFor == f) return;
        cachedFor = f;
        if (f == 0) {
            regular = null;
            bold = null;
        } else {
            regular = Typeface.create(FONTS[f][0], Typeface.NORMAL);
            bold = Typeface.create(FONTS[f][0], Typeface.BOLD);
            ours.add(regular);
            ours.add(bold);
        }
    }

    /** Вызывается в начале AndroidUtilities.bold(): жирный вариант выбранного шрифта или null. */
    public static Typeface boldOverride() {
        try {
            ensure();
            return bold;
        } catch (Exception e) {
            return null;
        }
    }

    /** Ставит выбранный шрифт всем текстовым кистям Theme (чаты, список чатов). Курсив и моноширинный код не трогаем. */
    public static void applyFonts() {
        try {
            ensure();
            Typeface origBold = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM);
            Typeface r = regular, b = bold != null ? bold : origBold;
            for (Field fl : Theme.class.getFields()) {
                if (!Modifier.isStatic(fl.getModifiers())) continue;
                Object v = fl.get(null);
                if (v instanceof TextPaint) fix((TextPaint) v, r, b, origBold);
                else if (v instanceof TextPaint[]) for (TextPaint p : (TextPaint[]) v) fix(p, r, b, origBold);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void fix(TextPaint p, Typeface r, Typeface b, Typeface origBold) {
        if (p == null) return;
        Typeface t = p.getTypeface();
        boolean isBold = t != null && (t == origBold || ours.contains(t) && t.isBold() || t == bold);
        boolean isRegular = t == null || t == Typeface.DEFAULT || ours.contains(t) && !t.isBold();
        if (isBold) p.setTypeface(b);
        else if (isRegular) p.setTypeface(r);
    }

    // ================================================================ 6. анимации

    public static boolean animations() {
        return flag("look_anim", true);
    }

    private static final HashSet<String> popped = new HashSet<>();

    /** Подключается к списку сообщений ChatActivity: новые сообщения появляются с лёгким отскоком. */
    public static void attachList(RecyclerView list) {
        if (list == null) return;
        applyFonts();
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View v) {
                try {
                    if (!animations() || !(v instanceof ChatMessageCell)) return;
                    MessageObject m = ((ChatMessageCell) v).getMessageObject();
                    if (m == null || m.messageOwner == null) return;
                    int now = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
                    boolean fresh = m.isSending() || now - m.messageOwner.date <= 3;
                    String key = m.getDialogId() + ":" + m.getId() + ":" + m.messageOwner.date;
                    if (!fresh || !popped.add(key)) return;
                    if (popped.size() > 500) popped.clear();
                    v.setPivotX(m.isOut() ? v.getWidth() : 0);
                    v.setPivotY(v.getHeight());
                    v.setScaleX(0.88f);
                    v.setScaleY(0.88f);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(new OvershootInterpolator(2.4f)).start();
                } catch (Exception ignore) {
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View v) {
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
        });
    }

    /** Короткая вспышка в цвет палитры при отправке. */
    public static void sendFlash(BaseFragment f) {
        try {
            if (!animations() || !(f instanceof ChatActivity) || f.getFragmentView() == null) return;
            if (!(f.getFragmentView() instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) f.getFragmentView();
            View glow = new View(root.getContext());
            int c = Theme.getColor(Theme.key_chat_messagePanelSend);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{(c & 0x00FFFFFF) | 0x66000000, 0x00000000});
            glow.setBackground(g);
            glow.setClickable(false);
            glow.setAlpha(0f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(220));
            lp.gravity = android.view.Gravity.BOTTOM;
            root.addView(glow, lp);
            glow.animate().alpha(1f).setDuration(140).withEndAction(() ->
                    glow.animate().alpha(0f).setDuration(380).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            root.removeView(glow);
                        }
                    }).start()).start();
        } catch (Exception ignore) {
        }
    }

    // ================================================================ 7. тема по аватарке

    static void themeFromAvatar(BaseFragment f) {
        Context ctx = f.getParentActivity();
        if (ctx == null || !StarMod.requirePro(f, "Свои палитры")) return;
        try {
            TLRPC.User me = UserConfig.getInstance(f.getCurrentAccount()).getCurrentUser();
            if (me == null || me.photo == null || me.photo.photo_small == null) {
                Toast.makeText(ctx, "У вас нет фото профиля", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = FileLoader.getInstance(f.getCurrentAccount()).getPathToAttach(me.photo.photo_big != null ? me.photo.photo_big : me.photo.photo_small, true);
            if (file == null || !file.exists()) file = FileLoader.getInstance(f.getCurrentAccount()).getPathToAttach(me.photo.photo_small, true);
            Bitmap bmp = file != null && file.exists() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
            if (bmp == null) {
                Toast.makeText(ctx, "Откройте свой профиль, чтобы фото загрузилось, и попробуйте снова", Toast.LENGTH_LONG).show();
                return;
            }
            float[] bins = new float[36];
            float[] h = new float[3];
            int step = Math.max(1, Math.min(bmp.getWidth(), bmp.getHeight()) / 64);
            for (int y = 0; y < bmp.getHeight(); y += step) {
                for (int x = 0; x < bmp.getWidth(); x += step) {
                    Color.colorToHSV(bmp.getPixel(x, y), h);
                    if (h[1] < 0.25f || h[2] < 0.25f) continue;
                    bins[(int) (h[0] / 10) % 36] += h[1] * h[2];
                }
            }
            bmp.recycle();
            int best = 0;
            float total = 0;
            for (int i = 0; i < 36; i++) {
                total += bins[i];
                if (bins[i] > bins[best]) best = i;
            }
            if (total < 1f) {
                Toast.makeText(ctx, "На фото почти нет цвета — выберите палитру вручную", Toast.LENGTH_LONG).show();
                return;
            }
            // уточняем: средневзвешенный оттенок соседних корзин
            float sum = 0, w = 0;
            for (int d = -1; d <= 1; d++) {
                int i = (best + d + 36) % 36;
                sum += (best * 10 + 5 + d * 10) * bins[i];
                w += bins[i];
            }
            float hue = ((sum / w) % 360 + 360) % 360;
            StarTheme.applyCustom(hue, false);
            Toast.makeText(ctx, "🎨 Тема подобрана по вашему фото", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ================================================================ 8. компактный режим

    public static boolean compact() {
        return flag("look_compact", false);
    }

    static void toggleCompact() {
        boolean on = !compact();
        SharedPreferences main = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs().edit().putBoolean("look_compact", on);
        if (on) {
            e.putInt("compact_prev_font", SharedConfig.fontSize).putInt("compact_prev_radius", SharedConfig.bubbleRadius);
            SharedConfig.fontSize = Math.max(12, SharedConfig.fontSize - 2);
            SharedConfig.bubbleRadius = Math.min(SharedConfig.bubbleRadius, 10);
        } else {
            SharedConfig.fontSize = prefs().getInt("compact_prev_font", 16);
            SharedConfig.bubbleRadius = prefs().getInt("compact_prev_radius", 17);
        }
        e.apply();
        main.edit().putInt("fons_size", SharedConfig.fontSize).putInt("bubbleRadius", SharedConfig.bubbleRadius).apply();
        SharedConfig.fontSizeIsDefault = false;
        Theme.createCommonMessageResources();
        Theme.chat_msgTextPaint.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize));
        applyFonts();
    }

    // ================================================================ 9. цветные папки

    public static boolean folderColors() {
        return flag("look_folders", false);
    }

    private static final int[] RAINBOW = {0xFF7B5CFF, 0xFF33A6FF, 0xFF1FB98A, 0xFFF5B400, 0xFFFF7A45, 0xFFFF4F8B, 0xFFB45CFF, 0xFF00B8D4};

    /** Вызывается из FilterTabsView: цвет подписи вкладки папки. */
    public static int tabColor(int tabId, boolean isDefault, int color) {
        if (!folderColors() || isDefault) return color; // «Все чаты» — как в теме
        int c = RAINBOW[(tabId & 0x7FFFFFFF) % RAINBOW.length];
        int a = Color.alpha(color);
        // активная вкладка — насыщенный цвет, остальные — приглушённый
        return (Math.max(a, 160) << 24) | (blend(color, c, 0.8f) & 0x00FFFFFF);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    // ================================================================ 3. иконка по сезону

    public static boolean seasonIcon() {
        return flag("look_season_icon", false);
    }

    static void toggleSeasonIcon() {
        toggleFlag("look_season_icon", false);
        prefs().edit().remove("season_icon_applied").apply();
        checkSeasonIcon();
    }

    static String seasonIconName() {
        int m = Calendar.getInstance().get(Calendar.MONTH);
        return m == 11 || m <= 1 ? "❄️ зимняя «Ночь»" : m <= 4 ? "🌸 весенний «Неон»" : m <= 7 ? "☀️ летнее «Золото»" : "🍂 осенний «Закат»";
    }

    /** Вызывается при открытии списка чатов: раз в сезон меняет иконку приложения. */
    public static void checkSeasonIcon() {
        try {
            if (!seasonIcon()) return;
            int m = Calendar.getInstance().get(Calendar.MONTH);
            int season = m == 11 || m <= 1 ? 0 : m <= 4 ? 1 : m <= 7 ? 2 : 3;
            if (prefs().getInt("season_icon_applied", -1) == season) return;
            LauncherIconController.LauncherIcon[] icons = {LauncherIconController.LauncherIcon.NOX, LauncherIconController.LauncherIcon.PREMIUM,
                    LauncherIconController.LauncherIcon.VINTAGE, LauncherIconController.LauncherIcon.TURBO};
            if (!StarMod.allowIcon(icons[season].ordinal())) return;
            if (!LauncherIconController.isEnabled(icons[season])) LauncherIconController.setIcon(icons[season]);
            prefs().edit().putInt("season_icon_applied", season).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Названия палитры для подписей. */
    static ArrayList<String> summary() {
        ArrayList<String> s = new ArrayList<>();
        if (gradientBubbles()) s.add("градиент");
        if (neon()) s.add("неон");
        if (font() != 0) s.add(FONTS[font()][1].toLowerCase(Locale.ROOT));
        if (compact()) s.add("компакт");
        return s;
    }
}
