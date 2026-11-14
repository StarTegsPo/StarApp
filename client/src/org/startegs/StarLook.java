/*
 * StarTegs 2.2+ — оформление:
 * стеклянные панели, градиентные/материальные пузыри, неоновый OLED,
 * шрифты, анимации сообщений (в т.ч. направленные), тема по аватарке,
 * компакт + читаемость, цветные папки, сезонная иконка,
 * свечение, параллакс, кинорежим, материалы, дыхание закреплённых,
 * тема по времени суток, варианты вспышки отправки.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;
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

    private static void setFlag(String key, boolean v) {
        prefs().edit().putBoolean(key, v).apply();
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

    // ================================================================ 2 и 4. градиентные пузыри и неон OLED + материалы

    public static boolean gradientBubbles() {
        return flag("look_gradient", false);
    }

    public static boolean neon() {
        return flag("look_neon", false);
    }

    /** 0 = классика, 1 = стекло, 2 = матовый, 3 = soft glow, 4 = бумага */
    public static int bubbleMaterial() {
        return Math.max(0, Math.min(4, prefs().getInt("look_material", 0)));
    }

    public static void setBubbleMaterial(int i) {
        prefs().edit().putInt("look_material", Math.max(0, Math.min(4, i))).apply();
    }

    public static final String[] MATERIAL_NAMES = {
            "Классика", "Стекло", "Матовый", "Soft Glow", "Бумага"
    };

    /** 0 = классика, 1 = таблетка, 2 = острые, 3 = почти круг */
    public static int bubbleShape() {
        return Math.max(0, Math.min(3, prefs().getInt("look_shape", 0)));
    }

    public static void setBubbleShape(int i) {
        prefs().edit().putInt("look_shape", Math.max(0, Math.min(3, i))).apply();
    }

    public static final String[] SHAPE_NAMES = {
            "Классика", "Таблетка", "Острые углы", "Круглые"
    };

    /** Переключить и пересобрать тему StarTegs. */
    static void toggleThemeFlag(BaseFragment f, String key) {
        toggleFlag(key, false);
        boolean night = key.equals("look_neon") && neon();
        if (!StarTheme.reapply(night)) StarTheme.enableDefault(night);
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
        int material = bubbleMaterial();
        int shape = bubbleShape();
        if (!grad && !oled && material == 0 && shape == 0) return text;

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

        if (grad || material > 0) {
            float v1 = night ? 0.62f : 0.92f, v2 = night ? 0.55f : 0.86f;
            if (oled) {
                v1 = 0.55f;
                v2 = 0.45f;
            }
            if (material == 1) { v1 *= 0.92f; v2 *= 0.88f; }
            else if (material == 2) { v1 *= 0.85f; v2 *= 0.80f; }
            else if (material == 3) { v1 = Math.min(1f, v1 * 1.08f); v2 = Math.min(1f, v2 * 1.05f); }
            else if (material == 4) { v1 *= 0.95f; v2 *= 0.90f; }

            int g1 = hsv(accentHue, 0.72f, v1), g2 = hsv(accentHue + 38, 0.78f, v2);
            set.put("chat_outBubble", hex(g1));
            if (grad || material == 1 || material == 3) {
                set.put("chat_outBubbleGradient", hex(g1));
                set.put("chat_outBubbleGradient2", hex(g2));
                set.put("chat_outBubbleGradientAnimated", "1");
            }
            set.put("chat_outBubbleSelected", hex(hsv(accentHue, 0.72f, v1 * 0.85f)));
            set.put("chat_outBubbleGradientSelectedOverlay", "#14000000");

            if (material == 1 || material == 3) {
                set.put("chat_outBubbleShadow", hex(0x33000000));
                set.put("chat_inBubbleShadow", hex(0x22000000));
            } else if (material == 2) {
                set.put("chat_outBubbleShadow", hex(0x1A000000));
                set.put("chat_inBubbleShadow", hex(0x14000000));
            }

            if (grad || material >= 1) {
                for (String[] w : WHITE_OUT) set.put(w[0], "#" + w[1]);
            }
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

    public static Typeface boldOverride() {
        try {
            ensure();
            return bold;
        } catch (Exception e) {
            return null;
        }
    }

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

    // ================================================================ 6. анимации + направленный вход + дыхание

    public static boolean animations() {
        return flag("look_anim", true);
    }

    public static boolean directionalEnter() {
        return flag("look_dir_enter", true);
    }

    public static void toggleDirectionalEnter() {
        toggleFlag("look_dir_enter", true);
    }

    public static boolean pinnedBreath() {
        return flag("look_pinned_breath", false);
    }

    public static void togglePinnedBreath() {
        toggleFlag("look_pinned_breath", false);
    }

    private static final HashSet<String> popped = new HashSet<>();

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

                    boolean out = m.isOut();
                    if (directionalEnter()) {
                        float fromX = out ? AndroidUtilities.dp(28) : -AndroidUtilities.dp(28);
                        v.setTranslationX(fromX);
                        v.setAlpha(0.55f);
                        v.setScaleX(0.92f);
                        v.setScaleY(0.92f);
                        v.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(320)
                                .setInterpolator(new OvershootInterpolator(1.6f))
                                .start();
                    } else {
                        v.setPivotX(out ? v.getWidth() : 0);
                        v.setPivotY(v.getHeight());
                        v.setScaleX(0.88f);
                        v.setScaleY(0.88f);
                        v.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(new OvershootInterpolator(2.4f)).start();
                    }
                } catch (Exception ignore) {
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View v) {
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setTranslationX(0f);
                v.setAlpha(1f);
            }
        });
    }

    public static int sendFlashStyle() {
        return Math.max(0, Math.min(3, prefs().getInt("look_flash_style", 0)));
    }

    public static void setSendFlashStyle(int i) {
        prefs().edit().putInt("look_flash_style", Math.max(0, Math.min(3, i))).apply();
    }

    public static final String[] FLASH_NAMES = {
            "Классика", "Волна", "Искры", "Минимализм"
    };

    public static void sendFlash(BaseFragment f) {
        try {
            if (!animations() || !(f instanceof ChatActivity) || f.getFragmentView() == null) return;
            if (!(f.getFragmentView() instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) f.getFragmentView();
            int style = sendFlashStyle();
            int c = Theme.getColor(Theme.key_chat_messagePanelSend);

            View glow = new View(root.getContext());
            glow.setClickable(false);
            glow.setAlpha(0f);

            GradientDrawable g;
            FrameLayout.LayoutParams lp;

            if (style == 1) {
                g = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{(c & 0x00FFFFFF) | 0x88000000, (c & 0x00FFFFFF) | 0x33000000, 0x00000000});
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(280));
            } else if (style == 3) {
                g = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{(c & 0x00FFFFFF) | 0x44000000, 0x00000000});
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(120));
            } else {
                g = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{(c & 0x00FFFFFF) | 0x66000000, 0x00000000});
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(220));
            }
            glow.setBackground(g);
            lp.gravity = android.view.Gravity.BOTTOM;
            root.addView(glow, lp);

            long fadeIn = style == 3 ? 90 : 140;
            long fadeOut = style == 1 ? 480 : 380;

            glow.animate().alpha(1f).setDuration(fadeIn).withEndAction(() ->
                    glow.animate().alpha(0f).setDuration(fadeOut).setListener(new AnimatorListenerAdapter() {
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
            if (file == null || !file.exists()) {
                Toast.makeText(ctx, "Фото ещё не загружено", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp == null) {
                Toast.makeText(ctx, "Не удалось прочитать фото", Toast.LENGTH_SHORT).show();
                return;
            }
            int w = Math.min(bmp.getWidth(), 64), h = Math.min(bmp.getHeight(), 64);
            Bitmap small = Bitmap.createScaledBitmap(bmp, w, h, true);
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = small.getPixel(x, y);
                    if (Color.alpha(c) < 128) continue;
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                    n++;
                }
            }
            if (n == 0) return;
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
            prefs().edit().putFloat("look_avatar_hue", hsv[0]).apply();
            if (!gradientBubbles()) setFlag("look_gradient", true);
            if (!StarTheme.reapply(Theme.getActiveTheme().isDark())) StarTheme.enableDefault(Theme.getActiveTheme().isDark());
            Toast.makeText(ctx, "Тема построена по аватарке", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(ctx, "Ошибка при создании темы", Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================ 8. компакт + читаемость

    public static boolean compact() {
        return flag("look_compact", false);
    }

    public static boolean highReadability() {
        return flag("look_readable", false);
    }

    public static void toggleCompact() {
        boolean on = !compact();
        SharedPreferences.Editor e = prefs().edit().putBoolean("look_compact", on);
        SharedPreferences main = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
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

    public static void toggleHighReadability() {
        toggleFlag("look_readable", false);
    }

    // ================================================================ 9. цветные папки + акцент

    public static boolean folderColors() {
        return flag("look_folders", false);
    }

    public static boolean folderAccent() {
        return flag("look_folder_accent", true);
    }

    public static void toggleFolderAccent() {
        toggleFlag("look_folder_accent", true);
    }

    private static final int[] RAINBOW = {0xFF7B5CFF, 0xFF33A6FF, 0xFF1FB98A, 0xFFF5B400, 0xFFFF7A45, 0xFFFF4F8B, 0xFFB45CFF, 0xFF00B8D4};

    public static int tabColor(int tabId, boolean isDefault, int color) {
        if (!folderColors() || isDefault) return color;
        int c = RAINBOW[(tabId & 0x7FFFFFFF) % RAINBOW.length];
        int a = Color.alpha(color);
        return (Math.max(a, 160) << 24) | (blend(color, c, 0.8f) & 0x00FFFFFF);
    }

    public static int tabColorActive(int tabId, boolean isDefault, int color) {
        if (!folderColors() || isDefault) return color;
        int c = RAINBOW[(tabId & 0x7FFFFFFF) % RAINBOW.length];
        if (folderAccent()) {
            return (0xFF << 24) | (c & 0x00FFFFFF);
        }
        return tabColor(tabId, isDefault, color);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    // ================================================================ 10. сезонная иконка

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

    // ================================================================ 11. свечение

    public static boolean badgeGlow() {
        return flag("look_badge_glow", false);
    }

    public static void toggleBadgeGlow() {
        toggleFlag("look_badge_glow", false);
    }

    // ================================================================ 12. параллакс

    public static boolean parallax() {
        return flag("look_parallax", false);
    }

    public static void toggleParallax() {
        toggleFlag("look_parallax", false);
    }

    // ================================================================ 13. кинорежим

    public static boolean cinemaMode() {
        return flag("look_cinema", false);
    }

    public static void toggleCinemaMode(BaseFragment f) {
        toggleFlag("look_cinema", false);
        if (f != null && f.getParentActivity() != null) {
            Toast.makeText(f.getParentActivity(),
                    cinemaMode() ? "Кинорежим включён" : "Кинорежим выключен",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================ 14. тема по времени суток

    public static boolean autoTimeTheme() {
        return flag("look_auto_time", false);
    }

    public static void toggleAutoTimeTheme() {
        toggleFlag("look_auto_time", false);
    }

    public static int currentPeriod() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 11) return 0;
        if (h >= 11 && h < 17) return 1;
        if (h >= 17 && h < 22) return 2;
        return 3;
    }

    // ================================================================ 15. микро-реакции

    public static boolean reactionFx() {
        return flag("look_reaction_fx", true);
    }

    public static void toggleReactionFx() {
        toggleFlag("look_reaction_fx", true);
    }

    // ================================================================ summary

    static ArrayList<String> summary() {
        ArrayList<String> s = new ArrayList<>();
        if (gradientBubbles()) s.add("градиент");
        if (neon()) s.add("неон");
        if (bubbleMaterial() > 0) s.add(MATERIAL_NAMES[bubbleMaterial()].toLowerCase(Locale.ROOT));
        if (bubbleShape() > 0) s.add(SHAPE_NAMES[bubbleShape()].toLowerCase(Locale.ROOT));
        if (font() != 0) s.add(FONTS[font()][1].toLowerCase(Locale.ROOT));
        if (compact()) s.add("компакт");
        if (highReadability()) s.add("читаемость");
        if (directionalEnter()) s.add("направл. вход");
        if (badgeGlow()) s.add("свечение");
        if (cinemaMode()) s.add("кино");
        if (autoTimeTheme()) s.add("авто-время");
        return s;
    }
}
