/*
 * StarTegs 4.5 — дизайн:
 *   • эмодзи-дождь по словам («с днём рождения» → конфетти, «люблю» → сердечки) и пасхалки — №8, №28;
 *   • цветная полоска у закреплённых и отмеченных чатов, эмодзи вместо аватарки — №10, №11;
 *   • фон чата: размытая аватарка собеседника или живые частицы — №5, №7;
 *   • плавающая кнопка StarTegs со «Звёздочкой» (ИИ) — №22;
 *   • вибро при отправке и звук отправки из наборов StarTegs — №23, №24;
 *   • режим «Минимализм» одной кнопкой — №26;
 *   • день рождения: конфетти в твой день и 🎂 у имени у друзей — №29;
 *   • «Сейчас играет»: музыка из Telegram сама попадает в витрину — №17;
 *   • стиль для друзей: цвет и шрифт имени, значок уровня, обложка витрины (бот v63) — №15, №18, №20.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public final class StarDecor {

    private StarDecor() {}

    public static final int MENU_EMOJI_AVATAR = 9351;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static boolean flag(String k, boolean d) { return prefs().getBoolean(k, d); }

    static void toggle(String k, boolean d) { prefs().edit().putBoolean(k, !flag(k, d)).apply(); }

    // ================================================================ эмодзи-дождь и пасхалки

    public static boolean rainOn() { return flag("decor_rain", true); }

    /** слово → эффект StarFx. Пасхалки отмечены «*» — они считаются в «Найдено N из M». */
    private static final String[][] RAIN = {
            {"с днём рождения", "fx_confetti"}, {"с днем рождения", "fx_confetti"}, {"с др", "fx_confetti"}, {"поздравляю", "fx_confetti"},
            {"люблю", "fx_hearts"}, {"обнимаю", "fx_hearts"}, {"❤️", "fx_hearts"},
            {"с новым годом", "fx_snow"}, {"снег", "fx_snow"},
            {"огонь", "fx_fire"}, {"🔥🔥🔥", "fx_fire"},
            {"зарплата", "fx_money"}, {"деньги пришли", "fx_money"},
            {"ура", "fx_stars"}, {"победа", "fx_stars"},
            // пасхалки
            {"*звёздный путь", "fx_stars"}, {"*бум", "fx_bolt"}, {"*миллионер", "fx_money"}, {"*метель", "fx_snow"},
            {"*валентинка", "fx_hearts"}, {"*салют", "fx_confetti"}, {"*молния маквин", "fx_bolt"}, {"*дракон", "fx_fire"},
            {"*starTegs forever", "fx_stars"}, {"*42", "fx_bolt"}, {"*котик", "fx_hearts"}, {"*кринж", "fx_fire"},
    };

    static int eggsTotal() {
        int n = 0;
        for (String[] r : RAIN) if (r[0].startsWith("*")) n++;
        return n;
    }

    static int eggsFound() {
        return prefs().getStringSet("decor_eggs", new HashSet<>()).size();
    }

    /** Из StarSend: входящее сообщение в открытом чате или своё исходящее. */
    static void onText(BaseFragment f, String text) {
        if (!rainOn() || f == null || TextUtils.isEmpty(text)) return;
        String low = text.toLowerCase(Locale.ROOT).trim();
        for (String[] r : RAIN) {
            boolean egg = r[0].startsWith("*");
            String w = (egg ? r[0].substring(1) : r[0]).toLowerCase(Locale.ROOT);
            if (egg ? low.equals(w) : low.contains(w)) {
                StarFx.play(f, r[1]);
                if (egg) {
                    HashSet<String> s = new HashSet<>(prefs().getStringSet("decor_eggs", new HashSet<>()));
                    if (s.add(w)) {
                        prefs().edit().putStringSet("decor_eggs", s).apply();
                        Toast.makeText(ApplicationLoader.applicationContext, "🥚 Пасхалка найдена! " + s.size() + " из " + eggsTotal(), Toast.LENGTH_SHORT).show();
                        StarAchieve.bump("eggs", 1);
                    }
                }
                return;
            }
        }
    }

    // ================================================================ полоска и эмодзи-аватарка (DialogCell)

    public static boolean stripeOn() { return flag("decor_stripe", true); }

    private static final Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint emojiPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final int[] LABEL_COLORS = {0, 0xFF34C759, 0xFFFFCC00, 0xFF0A84FF, 0xFFAF52DE, 0xFFFF3B30, 0xFFB0B0B0};

    static String emojiAvatar(long dialogId) {
        return prefs().getString("decor_emoji_" + dialogId, "");
    }

    /** Из StarCosmetics.drawFrame (DialogCell, после аватарки). */
    static void drawCell(Canvas canvas, RectF avatar, long dialogId, View cell) {
        try {
            String e = emojiAvatar(dialogId);
            if (!e.isEmpty()) {
                circle.setColor(0xFF7B5CFF);
                float r = avatar.width() / 2f;
                if (StarMod.avatarShape() == 0) canvas.drawCircle(avatar.centerX(), avatar.centerY(), r, circle);
                else canvas.drawRoundRect(avatar, r * 0.45f, r * 0.45f, circle);
                emojiPaint.setTextSize(avatar.height() * 0.55f);
                emojiPaint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = emojiPaint.getFontMetrics();
                canvas.drawText(e, avatar.centerX(), avatar.centerY() - (fm.ascent + fm.descent) / 2f, emojiPaint);
            }
            if (!stripeOn() || cell == null) return;
            int color = 0;
            int l = StarExtra.label(dialogId);
            if (l > 0 && l < LABEL_COLORS.length) color = LABEL_COLORS[l];
            if (color == 0) {
                TLRPC.Dialog d = MessagesController.getInstance(UserConfig.selectedAccount).dialogs_dict.get(dialogId);
                if (d != null && d.pinned) color = 0xFF7B5CFF;
            }
            if (color == 0) return;
            stripe.setColor(color);
            float w = AndroidUtilities.dp(3.5f), m = AndroidUtilities.dp(10);
            float x = cell.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? cell.getWidth() - w : 0;
            canvas.drawRoundRect(x, m, x + w, cell.getHeight() - m, w, w, stripe);
        } catch (Exception ignore) {
        }
    }

    static final String[] AVATAR_EMOJI = {"😎", "🔥", "💀", "👑", "🐱", "🐶", "🦊", "🌸", "⚡", "🎮", "💜", "🍕", "🚀", "🌙", "🍀", "🤡"};

    public static void chooseEmojiAvatar(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null) return;
        long did = chat.getDialogId();
        CharSequence[] items = new CharSequence[AVATAR_EMOJI.length + 1];
        System.arraycopy(AVATAR_EMOJI, 0, items, 0, AVATAR_EMOJI.length);
        items[AVATAR_EMOJI.length] = "🚫 Обычная аватарка";
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("😀 Эмодзи вместо аватарки (видишь только ты)")
                .setItems(items, (d, w) -> {
                    if (w == AVATAR_EMOJI.length) prefs().edit().remove("decor_emoji_" + did).apply();
                    else prefs().edit().putString("decor_emoji_" + did, AVATAR_EMOJI[w]).apply();
                    NotificationCenter.getInstance(chat.getCurrentAccount()).postNotificationName(NotificationCenter.dialogsNeedReload);
                }).show();
    }

    // ================================================================ фон чата

    /** 0 — как в Telegram, 1 — размытая аватарка собеседника, 2.. — живые частицы. */
    static final String[] BG = {"Как в Telegram", "Размытая аватарка собеседника", "✨ Звёзды", "🫧 Пузыри", "💖 Сердечки", "🌸 Лепестки", "🔥 Искры", "🌈 Сияние в цветах темы", "🎨 Узор из эмодзи", "❄️ Снегопад", "🎉 Конфетти", "🎵 Ноты", "🪐 Космос", "🍂 Листопад", "🦋 Бабочки", "💧 Дождик", "🌠 Звездопад"};
    private static final String[][] PARTICLES = {{}, {}, {"✨", "⭐", "💫"}, {"🫧"}, {"💖", "💕", "💗"}, {"🌸", "🌺"}, {"🔥", "✨"}, {}, {}, {"❄️", "❄️", "✨"}, {"🎉", "🎊", "✨"}, {"🎵", "🎶", "🎧"}, {"🪐", "⭐", "🚀", "🌙"}, {"🍂", "🍁"}, {"🦋", "🌼"}, {"💧", "💦"}, {"🌠", "✨", "⭐"}};

    static int bg() { return prefs().getInt("decor_bg", 0); }

    static void setBg(int v) { prefs().edit().putInt("decor_bg", v).apply(); }

    /** Вызывается в ChatActivity.createView рядом с StarWall.attach. */
    public static void attachChat(ViewGroup root, long dialogId, int account) {
        try {
            int mode = bg();
            if (root == null || mode == 0) return;
            if (mode >= 2 && !StarMod.isPro()) return;
            int at = root.getChildCount() > 0 && root.getChildAt(0).getClass().getSimpleName().equals("BackgroundView") ? 1 : 0;
            if (mode >= BG.length) return;
            View v = mode == 1 ? new AvatarBg(root.getContext(), dialogId, account)
                    : mode == 7 ? new StarNight.Aurora(root.getContext()) // 5.2
                    : mode == 8 ? new StarNight.Doodle(root.getContext()) // 5.2
                    : new Particles(root.getContext(), PARTICLES[mode]);
            root.addView(v, at, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    static final class AvatarBg extends View {
        private Bitmap tiny;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();

        AvatarBg(Context c, long dialogId, int account) {
            super(c);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            try {
                TLRPC.FileLocation loc = null;
                if (dialogId > 0) {
                    TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
                    if (u != null && u.photo != null) loc = u.photo.photo_small;
                } else {
                    TLRPC.Chat ch = MessagesController.getInstance(account).getChat(-dialogId);
                    if (ch != null && ch.photo != null) loc = ch.photo.photo_small;
                }
                if (loc != null) {
                    File f = FileLoader.getInstance(account).getPathToAttach(loc, true);
                    if (f != null && f.exists()) {
                        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                        if (b != null) {
                            tiny = Bitmap.createScaledBitmap(b, 12, 12, true); // «размытие» — растягиваем крошечную копию
                            if (tiny != b) b.recycle();
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (tiny == null) return;
            dst.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(tiny, null, dst, p);
            canvas.drawColor(org.telegram.ui.ActionBar.Theme.isCurrentThemeDark() ? 0x99000000 : 0x66FFFFFF);
        }
    }

    static final class Particles extends View {
        private final String[] set;
        private final float[][] pts = new float[18][4]; // x, y, скорость, размер
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private long last;

        Particles(Context c, String[] set) {
            super(c);
            this.set = set;
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            tp.setAlpha(150);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || set.length == 0) return;
            long now = System.currentTimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            for (int i = 0; i < pts.length; i++) {
                float[] q = pts[i];
                if (q[3] == 0 || q[1] < -q[3]) {
                    q[0] = rnd.nextFloat() * w;
                    q[1] = q[3] == 0 ? rnd.nextFloat() * h : h + AndroidUtilities.dp(20);
                    q[2] = AndroidUtilities.dp(14 + rnd.nextInt(26));
                    q[3] = AndroidUtilities.dp(12 + rnd.nextInt(12));
                }
                q[1] -= q[2] * dt;
                q[0] += (float) Math.sin((now / 1000.0) + i) * dt * AndroidUtilities.dp(6);
                tp.setTextSize(q[3]);
                canvas.drawText(set[i % set.length], q[0], q[1], tp);
            }
            // 30 кадров в секунду вместо 60 и полная пауза при энергосбережении или когда фон не виден
            if (isShown() && StarPro.anim(org.telegram.messenger.LiteMode.FLAG_PARTICLES)) postInvalidateDelayed(33);
            else last = 0;
        }
    }

    // ================================================================ плавающая кнопка и «Звёздочка»

    public static boolean bubbleOn() { return flag("decor_bubble", false); }

    private static Activity current;
    private static View bubble;
    private static boolean lifecycle;

    static void init(Activity activity) {
        checkBirthday();
        if (lifecycle || activity == null) return;
        lifecycle = true;
        current = activity;
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { current = a; updateBubble(); }
            @Override public void onActivityPaused(Activity a) { removeBubble(); }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (current == a) current = null; }
        });
        updateBubble();
        AndroidUtilities.runOnUIThread(musicTick, 15_000);
    }

    static void updateBubble() {
        removeBubble();
        Activity a = current;
        if (!bubbleOn() || a == null || StarPrivacy.decoy()) return;
        try {
            TextView b = new TextView(a);
            b.setText("⭐");
            b.setTextSize(22);
            b.setGravity(Gravity.CENTER);
            GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF7B5CFF, 0xFF33A6FF});
            bgd.setShape(GradientDrawable.OVAL);
            b.setBackground(bgd);
            b.setElevation(AndroidUtilities.dp(6));
            int size = AndroidUtilities.dp(52);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = prefs().getInt("decor_bubble_x", AndroidUtilities.displaySize.x - size - AndroidUtilities.dp(16));
            lp.topMargin = prefs().getInt("decor_bubble_y", AndroidUtilities.displaySize.y / 2);
            final float[] down = new float[4];
            final boolean[] moved = {false};
            b.setOnTouchListener((v, ev) -> {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        down[0] = ev.getRawX(); down[1] = ev.getRawY(); down[2] = p.leftMargin; down[3] = p.topMargin;
                        moved[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = ev.getRawX() - down[0], dy = ev.getRawY() - down[1];
                        if (Math.abs(dx) + Math.abs(dy) > AndroidUtilities.dp(6)) moved[0] = true;
                        p.leftMargin = (int) (down[2] + dx);
                        p.topMargin = (int) (down[3] + dy);
                        v.setLayoutParams(p);
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs().edit().putInt("decor_bubble_x", p.leftMargin).putInt("decor_bubble_y", p.topMargin).apply();
                        if (!moved[0]) bubbleMenu();
                        return true;
                }
                return false;
            });
            ((ViewGroup) a.getWindow().getDecorView()).addView(b, lp);
            bubble = b;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void removeBubble() {
        View b = bubble;
        bubble = null;
        if (b != null && b.getParent() instanceof ViewGroup) ((ViewGroup) b.getParent()).removeView(b);
    }

    private static void bubbleMenu() {
        BaseFragment f = LaunchActivity.getLastFragment();
        if (f == null || f.getParentActivity() == null) return;
        CharSequence[] items = {"🤖 Спросить Звёздочку", "⭐ Открыть StarTegs", StarSections.ghostAll() ? "👻 Выключить призрака" : "👻 Включить призрака", "✨ Красивый шрифт", "🙈 Спрятать кнопку"};
        new AlertDialog.Builder(f.getParentActivity())
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: askStar(f); break;
                        case 1: f.presentFragment(new StarSections.Index()); break;
                        case 2:
                            if (StarMod.requirePro(f, "Режим призрака")) StarSections.setGhostAll(!StarSections.ghostAll());
                            break;
                        case 3: StarStyle.chooseAuto(f, null); break;
                        default:
                            prefs().edit().putBoolean("decor_bubble", false).apply();
                            removeBubble();
                    }
                }).show();
    }

    /** «Звёздочка» — быстрый вопрос нейросети откуда угодно. */
    static void askStar(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        if (!StarAi.enabled()) {
            Toast.makeText(f.getParentActivity(), "Нейросеть не настроена", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(f.getParentActivity());
        input.setHint("Спроси что угодно: домашка, идея для подписи, совет…");
        input.setMinLines(2);
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("✨ Звёздочка")
                .setView(input)
                .setPositiveButton("Спросить", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.isEmpty()) return;
                    AlertDialog wait = new AlertDialog.Builder(f.getParentActivity()).setTitle("✨ Звёздочка думает…").setMessage("…").setNegativeButton("Закрыть", null).create();
                    wait.show();
                    StarAi.ask(f.getCurrentAccount(), q, 0, new StarAi.Stream() {
                        @Override
                        public void partial(String text) {
                            try { wait.setMessage(text); } catch (Exception ignore) {}
                        }

                        @Override
                        public void done(String text) {
                            try {
                                wait.setTitle("✨ Звёздочка");
                                wait.setMessage(text);
                            } catch (Exception ignore) {}
                            StarAchieve.bump("star_ai", 1);
                        }

                        @Override
                        public void error(String why) {
                            try { wait.setMessage("⚠️ " + why); } catch (Exception ignore) {}
                        }
                    });
                })
                .setNegativeButton("Отмена", null).show();
    }

    // ================================================================ вибро и звук отправки

    static final String[] HAPTICS = {"Без вибро", "Лёгкий тап", "Двойной тап", "Сердцебиение"};

    static int haptic() { return prefs().getInt("decor_haptic", 0); }

    static void setHaptic(int v) { prefs().edit().putInt("decor_haptic", v).apply(); }

    /** Из StarSend.intercept — при отправке. */
    static void onSend() {
        int h = haptic();
        if (h == 0) return;
        try {
            Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = h == 1 ? new long[]{0, 18} : h == 2 ? new long[]{0, 15, 70, 15} : new long[]{0, 25, 110, 45};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            else v.vibrate(pattern, -1);
        } catch (Exception ignore) {
        }
    }

    static int sendSoundIndex() { return prefs().getInt("decor_send_sound", -1); }

    static void setSendSound(int v) { prefs().edit().putInt("decor_send_sound", v).apply(); }

    /** Для NotificationsController: звук отправки (raw-ресурс). */
    public static int sendSound() {
        try {
            int i = sendSoundIndex();
            if (i >= 0 && i < StarCosmetics.SOUNDS.length) return (Integer) StarCosmetics.SOUNDS[i][1];
        } catch (Exception ignore) {
        }
        return R.raw.sound_out;
    }

    // ================================================================ минимализм

    public static boolean minimal() { return flag("decor_minimal", false); }

    static void setMinimal(boolean on) {
        SharedPreferences.Editor e = prefs().edit().putBoolean("decor_minimal", on);
        if (on) {
            e.putBoolean("decor_min_prev_stories", flag("hide_stories", false)).putBoolean("decor_min_prev_promo", flag("hide_promo", false))
                    .putBoolean("decor_min_prev_btn", flag("chats_button", true))
                    .putBoolean("hide_stories", true).putBoolean("hide_promo", true).putBoolean("chats_button", false);
        } else {
            e.putBoolean("hide_stories", flag("decor_min_prev_stories", false)).putBoolean("hide_promo", flag("decor_min_prev_promo", false))
                    .putBoolean("chats_button", flag("decor_min_prev_btn", true));
        }
        e.apply();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }

    // ================================================================ день рождения

    static String birthday() { return prefs().getString("decor_bday", ""); }

    static void chooseBirthday(BaseFragment f, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        EditText input = new EditText(f.getParentActivity());
        input.setHint("ДД.ММ, например 14.03");
        input.setText(birthday().isEmpty() ? "" : birthday().substring(3) + "." + birthday().substring(0, 2));
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🎂 Мой день рождения")
                .setMessage("В этот день StarTegs устроит праздник, а друзья с модом увидят 🎂 у твоего имени.")
                .setView(input)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String t = input.getText().toString().trim();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})[./-](\\d{1,2})$").matcher(t);
                    if (!m.find()) {
                        prefs().edit().remove("decor_bday").apply();
                    } else {
                        int day = Integer.parseInt(m.group(1)), mon = Integer.parseInt(m.group(2));
                        if (day < 1 || day > 31 || mon < 1 || mon > 12) return;
                        prefs().edit().putString("decor_bday", String.format(Locale.US, "%02d-%02d", mon, day)).apply();
                    }
                    pushLook(f.getCurrentAccount());
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null).show();
    }

    private static void checkBirthday() {
        String b = birthday();
        if (b.isEmpty()) return;
        String today = new SimpleDateFormat("MM-dd", Locale.US).format(new Date());
        String key = "decor_bday_done_" + new SimpleDateFormat("yyyy", Locale.US).format(new Date());
        if (!today.equals(b) || prefs().getBoolean(key, false)) return;
        prefs().edit().putBoolean(key, true).apply();
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment f = LaunchActivity.getLastFragment();
            if (f == null || f.getParentActivity() == null) return;
            StarFx.play(f, "fx_confetti");
            new AlertDialog.Builder(f.getParentActivity()).setTitle("🎂 С днём рождения!")
                    .setMessage("Команда StarTegs поздравляет тебя! 🎉 Сегодня друзья с модом видят 🎂 у твоего имени.")
                    .setPositiveButton("Спасибо 🥳", null).show();
        }, 2500);
    }

    // ================================================================ «Сейчас играет»

    public static boolean musicStatus() { return flag("decor_music", true); }

    private static String lastSong = "";

    private static final Runnable musicTick = new Runnable() {
        @Override
        public void run() {
            try {
                if (musicStatus() && !StarModConfig.ECO_BOT.isEmpty()) {
                    MessageObject m = MediaController.getInstance().getPlayingMessageObject();
                    if (m != null && m.isMusic() && !MediaController.getInstance().isMessagePaused()) {
                        String song = (m.getMusicAuthor() + " — " + m.getMusicTitle()).trim();
                        if (song.length() > 80) song = song.substring(0, 80);
                        if (!song.equals(lastSong)) {
                            lastSong = song;
                            JSONObject mine = StarProfile.mine();
                            mine.put("song", "▶️ " + song);
                            prefs().edit().putString("prof_mine", mine.toString()).apply();
                            StarCloud.call(UserConfig.selectedAccount, "prof_set", mine, new StarCloud.Result() {
                                @Override public void ok(JSONObject res) {}
                                @Override public void fail(String why) {}
                            });
                        }
                    }
                }
            } catch (Exception ignore) {
            }
            AndroidUtilities.runOnUIThread(this, 60_000);
        }
    };

    // ================================================================ стиль для друзей (бот v63)

    static final int[] NAME_COLORS = {0, 0xFFFF2D6F, 0xFFFF8C00, 0xFFFFC400, 0xFF34C759, 0xFF00C2FF, 0xFF7B5CFF, 0xFFE040FB, 0xFFFF3B30, 0xFF2EE6C5, 0xFFB39DFF, 0xFFB0BEC5};
    static final String[] NAME_COLOR_TITLES = {"Обычный", "💗 Розовый", "🧡 Оранжевый", "💛 Золотой", "💚 Зелёный", "💙 Голубой", "💜 Фиолетовый", "🪩 Неон", "❤️ Алый", "🩵 Мятный", "🪻 Лавандовый", "🩶 Серебро"};
    static final String[] COVERS = {"Без обложки", "🌌 Космос", "🌅 Закат", "🌊 Океан", "🔥 Огонь", "🌸 Сакура", "🖤 Ночь", "💚 Матрица", "🍭 Леденец", "👑 Золото"};
    static final int[][] COVER_COLORS = {{0, 0}, {0xFF1B1446, 0xFF5B2C83}, {0xFFFF5F8F, 0xFFFFB86C}, {0xFF00C9A7, 0xFF3A86FF}, {0xFFFF3D00, 0xFFFFC400},
            {0xFFFFC1E3, 0xFFFF7EB6}, {0xFF111111, 0xFF3A3A3A}, {0xFF003300, 0xFF00C853}, {0xFFFF9AE6, 0xFF7FD6FF}, {0xFFFFE082, 0xFFC99A2E}};
    static final String[] LEVEL_MEDALS = {"", "🥉", "🥈", "🥇", "💎", "👑"};

    static int nameColor() { return prefs().getInt("look_name_color", 0); }
    static int nameStyle() { return prefs().getInt("look_name_style", -1); }
    static int cover() { return prefs().getInt("look_cover", 0); }

    static String medal(int level) {
        int i = level >= 30 ? 5 : level >= 20 ? 4 : level >= 10 ? 3 : level >= 5 ? 2 : level >= 2 ? 1 : 0;
        return LEVEL_MEDALS[i];
    }

    static void pushLook(int account) {
        if (StarModConfig.ECO_BOT.isEmpty()) return;
        try {
            JSONObject b = new JSONObject().put("lvl", StarAchieve.level()).put("cover", cover() - 1).put("bday", birthday());
            if (StarMod.isPro()) b.put("name_color", nameColor()).put("name_style", nameStyle());
            StarCloud.call(account, "look_set", b, new StarCloud.Result() {
                @Override public void ok(JSONObject res) {}
                @Override public void fail(String why) {}
            });
        } catch (Exception ignore) {
        }
    }

    static void setLook(BaseFragment f, String key, int value) {
        prefs().edit().putInt(key, value).apply();
        pushLook(f.getCurrentAccount());
        Toast.makeText(ApplicationLoader.applicationContext, "✨ Друзья со StarTegs увидят это", Toast.LENGTH_SHORT).show();
    }

    private static final HashMap<Long, JSONObject> looks = new HashMap<>();
    private static final HashSet<Long> pending = new HashSet<>();
    private static boolean loaded, fetching;
    private static long lastFetch;

    private static JSONObject lookOf(long uid) {
        if (uid <= 0 || StarModConfig.ECO_BOT.isEmpty() || StarPrivacy.decoy()) return null;
        if (!loaded) {
            loaded = true;
            try {
                JSONObject o = new JSONObject(prefs().getString("look_cache", "{}"));
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    looks.put(Long.parseLong(k), o.optJSONObject(k));
                }
            } catch (Exception ignore) {
            }
        }
        JSONObject l = looks.get(uid);
        if (l == null || System.currentTimeMillis() - prefs().getLong("look_at", 0) > 3 * 3600_000L) {
            pending.add(uid);
            schedule();
        }
        return l;
    }

    private static void schedule() {
        if (fetching || pending.isEmpty()) return;
        fetching = true;
        long wait = Math.max(800, 30_000 - (System.currentTimeMillis() - lastFetch));
        AndroidUtilities.runOnUIThread(() -> {
            JSONArray ids = new JSONArray();
            for (Long id : pending) {
                ids.put(id);
                if (ids.length() >= 300) break;
            }
            pending.clear();
            lastFetch = System.currentTimeMillis();
            try {
                StarCloud.call(UserConfig.selectedAccount, "look_get", new JSONObject().put("ids", ids), new StarCloud.Result() {
                    @Override
                    public void ok(JSONObject res) {
                        fetching = false;
                        JSONObject all = res.optJSONObject("look");
                        for (int i = 0; i < ids.length(); i++) {
                            long id = ids.optLong(i);
                            JSONObject v = all != null ? all.optJSONObject(String.valueOf(id)) : null;
                            looks.put(id, v != null ? v : new JSONObject());
                        }
                        try {
                            JSONObject c = new JSONObject();
                            for (java.util.Map.Entry<Long, JSONObject> e : looks.entrySet()) c.put(String.valueOf(e.getKey()), e.getValue());
                            prefs().edit().putString("look_cache", c.toString()).putLong("look_at", System.currentTimeMillis()).apply();
                        } catch (Exception ignore) {
                        }
                        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        schedule();
                    }

                    @Override
                    public void fail(String why) {
                        fetching = false;
                        lastFetch = System.currentTimeMillis() + 10 * 60_000L; // бот без v63 — молчим
                    }
                });
            } catch (Exception e) {
                fetching = false;
            }
        }, wait);
    }

    /** Из StarExtra.decorateName: шрифт и цвет имени друга, медаль уровня, 🎂. */
    public static CharSequence decorate(long dialogId, CharSequence name) {
        try {
            if (dialogId <= 0 || name == null) return name;
            JSONObject l = lookOf(dialogId);
            if (l == null || l.length() == 0) return name;
            CharSequence out = name;
            int style = l.optInt("s", -1);
            if (style >= 0 && style < StarStyle.STYLES.length) out = StarStyle.apply(style, out.toString());
            String suffix = medal(l.optInt("l")) + (l.optBoolean("b") ? "🎂" : "");
            int color = l.optInt("c", 0);
            if (color != 0) {
                SpannableString sp = new SpannableString(out);
                sp.setSpan(new ForegroundColorSpan(color), 0, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out = sp;
            }
            return suffix.isEmpty() ? out : TextUtils.concat(out, " ", suffix);
        } catch (Exception e) {
            return name;
        }
    }

    /** Обложка витрины друга (для StarProfile.showFriend) или null. */
    static View coverView(Context ctx, long uid) {
        JSONObject l = lookOf(uid);
        int cv = l != null ? l.optInt("cv", -1) + 1 : 0;
        if (cv <= 0 || cv >= COVER_COLORS.length) return null;
        TextView t = new TextView(ctx);
        t.setText(COVERS[cv] + "  " + medal(l.optInt("l")));
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, AndroidUtilities.dp(26), 0, AndroidUtilities.dp(26));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, COVER_COLORS[cv]);
        g.setCornerRadius(AndroidUtilities.dp(16));
        t.setBackground(g);
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(20);
        wrap.setPadding(m, AndroidUtilities.dp(10), m, 0);
        wrap.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }
}
