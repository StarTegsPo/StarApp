/*
 * StarTegs 5.0 «Твой стиль»: карточка «как тебя видят друзья», галерея готовых тем с живыми
 * мини-превью, знакомство при первом запуске, цветные плитки разделов и «дышащий» градиент шапки.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
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
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public final class StarYou {

    private StarYou() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static TextView text(Context ctx, CharSequence s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(AndroidUtilities.bold());
        return t;
    }

    // ================================================================ цветные плитки разделов

    /** {иконка, цвет сверху, цвет снизу} для каждого раздела StarSections.SECTIONS. */
    static final int[][] TILES = {
            {R.drawable.settings_premium, 0xFFB46CFF, 0xFF7B4DFF},   // 0 Premium
            {R.drawable.settings_privacy, 0xFF6E7B91, 0xFF4A5568},   // 1 Приватность
            {R.drawable.settings_policy, 0xFF4A4A55, 0xFF26262E},    // 2 Двойное дно
            {R.drawable.settings_chat, 0xFF4FB2FF, 0xFF2A7DE1},      // 3 Чаты
            {R.drawable.settings_ask, 0xFF34D1BF, 0xFF16A394},       // 4 Сообщения
            {R.drawable.settings_invite, 0xFF5EDB76, 0xFF2EAE4C},    // 5 Отправка
            {R.drawable.settings_data, 0xFFFFAE52, 0xFFF0781D},      // 6 Медиа
            {R.drawable.settings_features, 0xFFE968FF, 0xFF9B45F0},  // 7 Нейросеть
            {R.drawable.settings_faq, 0xFF7C8CFF, 0xFF5161E8},       // 8 Плагины
            {R.drawable.settings_gift, 0xFFFF6FA0, 0xFFE8336F},      // 9 Оформление
            {R.drawable.settings_business, 0xFFFFA24C, 0xFFD9661C},  // 10 Бизнес
            {R.drawable.settings_sounds, 0xFFFFD24D, 0xFFF5A623},    // 11 Развлечения
            {R.drawable.settings_channel, 0xFF3FD0F5, 0xFF1AA3D6},   // 12 Каналы
            {R.drawable.settings_devices, 0xFF9AA3AE, 0xFF6C7580},   // 13 Прочее
            {R.drawable.settings_folders, 0xFF56D88A, 0xFF22A85A},   // 14 Продуктивность
            {R.drawable.settings_power, 0xFFFF6B6B, 0xFFE03A3F},     // 15 Безопасность
            {R.drawable.settings_wallet, 0xFF35C98F, 0xFF119E6A},    // 16 Кошелёк
            {R.drawable.settings_group, 0xFF5AA7FF, 0xFF3B6FE8},     // 17 Сервисы
            {R.drawable.settings_stars, 0xFFFFC94D, 0xFFFF8A3D},     // 18 Стиль и крутость
            {R.drawable.settings_features, 0xFFA0A7B4, 0xFF6F7682},  // 19 Ещё
            {R.drawable.settings_language, 0xFFFF7EDB, 0xFF8A5CFF},  // 20 Дизайн
            {R.drawable.settings_folders, 0xFF5BC0EB, 0xFF3A86FF},   // 21 Удобство
    };

    static UItem tile(int id, int section) {
        int[] t = TILES[Math.min(section, TILES.length - 1)];
        return org.telegram.ui.SettingsActivity.SettingCell.Factory.of(id, t[1], t[2], t[0],
                StarSections.SECTIONS[section][1], StarSections.SECTIONS[section][2]);
    }

    // ================================================================ «дышащий» градиент шапки

    /** Медленно переливает градиент карточки, пока она на экране (и если не включено энергосбережение). */
    static void breathe(View card, GradientDrawable bg, int[] from, int[] to) {
        if (!StarPro.anim(LiteMode.FLAG_CHAT_BACKGROUND)) return;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(7000);
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.addUpdateListener(a -> {
            float k = (float) a.getAnimatedValue();
            int[] c = new int[from.length];
            for (int i = 0; i < c.length; i++) c[i] = AndroidUtilities.getOffsetColor(from[i], to[i], k, 1f);
            bg.setColors(c);
        });
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                va.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                va.cancel();
            }
        });
    }

    // ================================================================ карточка «как тебя видят друзья»

    /** Аватарка с кольцом рамки из магазина (или мягким белым кольцом). */
    static final class Ring extends FrameLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String frame;

        Ring(Context c, String frame) {
            super(c);
            this.frame = frame;
            setWillNotDraw(false);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(AndroidUtilities.dp(3));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - AndroidUtilities.dp(2);
            if (frame != null && !frame.isEmpty()) p.setShader(new SweepGradient(cx, cy, StarCosmetics.colors(frame), null));
            else {
                p.setShader(null);
                p.setColor(0xAAFFFFFF);
            }
            canvas.drawCircle(cx, cy, r, p);
        }
    }

    static View profileCard(BaseFragment f, Runnable onClick) {
        Context ctx = f.getParentActivity();
        int account = f.getCurrentAccount();
        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();

        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, AndroidUtilities.dp(4), m, AndroidUtilities.dp(4));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = AndroidUtilities.dp(14);
        card.setPadding(p, p, p, p);
        int cv = StarDecor.cover();
        int[] colors = cv > 0 && cv < StarDecor.COVER_COLORS.length ? StarDecor.COVER_COLORS[cv] : new int[]{0xFF23243A, 0xFF3A2E5C};
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(AndroidUtilities.dp(20));
        card.setBackground(bg);

        Ring ring = new Ring(ctx, StarCosmetics.myFrame());
        BackupImageView avatar = new BackupImageView(ctx);
        avatar.setRoundRadius(AndroidUtilities.dp(26));
        AvatarDrawable ad = new AvatarDrawable();
        if (me != null) {
            ad.setInfo(account, me);
            avatar.setForUserOrChat(me, ad);
        }
        ring.addView(avatar, new FrameLayout.LayoutParams(AndroidUtilities.dp(52), AndroidUtilities.dp(52), Gravity.CENTER));
        card.addView(ring, new LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64)));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        String first = me != null ? UserObject.getFirstName(me) : "Ты";
        CharSequence name = first;
        int style = StarDecor.nameStyle();
        if (style >= 0 && style < StarStyle.STYLES.length) name = StarStyle.apply(style, first);
        int color = StarDecor.nameColor();
        if (color != 0) {
            SpannableString sp = new SpannableString(name);
            sp.setSpan(new ForegroundColorSpan(color), 0, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            name = sp;
        }
        int level = StarAchieve.level();
        TextView nameView = text(ctx, TextUtils.concat(name, " ", StarDecor.medal(level)), 19, Color.WHITE, true);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(nameView);
        String status = StarProfile.statusTitle();
        col.addView(text(ctx, TextUtils.isEmpty(status) ? "Уровень " + level + " · так тебя видят друзья · удерживай — поделиться" : status + " · уровень " + level, 13, 0xCCFFFFFF, false));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = AndroidUtilities.dp(8);
        col.addView(StarPolish.levelBar(ctx), blp); // 5.3: прогресс уровня
        TextView edit = text(ctx, "✏️ Изменить стиль", 12, Color.WHITE, true);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x33FFFFFF);
        pill.setCornerRadius(AndroidUtilities.dp(12));
        edit.setBackground(pill);
        edit.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = AndroidUtilities.dp(8);
        col.addView(edit, elp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = AndroidUtilities.dp(14);
        card.addView(col, clp);
        card.setOnClickListener(v -> onClick.run());
        card.setOnLongClickListener(v -> { // 5.2: долгое нажатие — картинка для сторис
            StarNight.shareCard(f);
            return true;
        });
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    // ================================================================ галерея готовых тем

    /** {название, оттенок, градиент, неон, материал, форма, Premium}. */
    static final Object[][] PRESETS = {
            {"⭐ Звёздная", 270f, true, false, 0, 0, false},
            {"🌊 Океан", 200f, true, false, 0, 1, false},
            {"🌿 Мята", 150f, false, false, 0, 1, false},
            {"🔷 Классика", 216f, false, false, 0, 0, false},
            {"🌸 Сакура", 335f, true, false, 1, 3, true},
            {"🌅 Закат", 20f, true, false, 0, 1, true},
            {"🍋 Лимонад", 52f, true, false, 3, 3, true},
            {"🌃 Неон", 290f, true, true, 3, 0, true},
            {"🌌 Полночь", 230f, false, true, 1, 2, true},
            {"🌋 Лава", 8f, true, true, 3, 1, true},
    };

    static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }

    /** Мини-превью темы: фон и два пузыря. */
    static final class Preview extends View {
        private final Object[] t;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        boolean selected;

        Preview(Context c, Object[] t) {
            super(c);
            this.t = t;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), hue = (Float) t[1];
            boolean grad = (Boolean) t[2], neon = (Boolean) t[3];
            int shape = (Integer) t[5];
            float rad = AndroidUtilities.dp(shape == 1 ? 10 : shape == 2 ? 3 : shape == 3 ? 14 : 7);
            p.setShader(new LinearGradient(0, 0, 0, h, neon ? 0xFF050509 : hsv(hue, 0.16f, 0.98f), neon ? 0xFF14141F : hsv(hue, 0.32f, 0.9f), Shader.TileMode.CLAMP));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, AndroidUtilities.dp(16), AndroidUtilities.dp(16), p);
            p.setShader(null);
            // входящий
            p.setColor(neon ? 0xFF1C1C28 : Color.WHITE);
            r.set(w * 0.08f, h * 0.16f, w * 0.62f, h * 0.38f);
            c.drawRoundRect(r, rad, rad, p);
            // исходящий
            int accent = hsv(hue, neon ? 0.85f : 0.6f, neon ? 1f : 0.88f);
            if (grad) p.setShader(new LinearGradient(w * 0.35f, 0, w * 0.92f, 0, accent, hsv((hue + 40) % 360, 0.6f, 0.92f), Shader.TileMode.CLAMP));
            else p.setColor(neon ? 0xFF000000 : hsv(hue, 0.28f, 0.98f));
            r.set(w * 0.35f, h * 0.46f, w * 0.92f, h * 0.70f);
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            if (neon) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(1.5f));
                p.setColor(accent);
                c.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.FILL);
            }
            if (selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(3));
                p.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                r.set(AndroidUtilities.dp(1.5f), AndroidUtilities.dp(1.5f), w - AndroidUtilities.dp(1.5f), h - AndroidUtilities.dp(1.5f));
                c.drawRoundRect(r, AndroidUtilities.dp(16), AndroidUtilities.dp(16), p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    static int currentPreset() {
        return prefs().getInt("you_preset", -1);
    }

    static void applyPreset(BaseFragment f, int i, Runnable done) {
        Object[] t = PRESETS[i];
        if ((Boolean) t[6] && !StarMod.requirePro(f, "Тема «" + t[0] + "»")) return;
        StarLook.setBubbleMaterial((Integer) t[4]);
        StarLook.setBubbleShape((Integer) t[5]);
        StarCosmetics.applyStyle((Float) t[1], (Boolean) t[2], (Boolean) t[3]);
        prefs().edit().putInt("you_preset", i).apply();
        if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), "🎨 Тема «" + ((String) t[0]).substring(((String) t[0]).indexOf(' ') + 1) + "» применена", Toast.LENGTH_SHORT).show();
        if (done != null) done.run();
    }

    static View gallery(BaseFragment f, Runnable done) {
        Context ctx = f.getParentActivity();
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        int sel = currentPreset();
        for (int i = 0; i < PRESETS.length; i++) {
            final int k = i;
            Object[] t = PRESETS[i];
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            Preview pv = new Preview(ctx, t);
            pv.selected = sel == i;
            cell.addView(pv, new LinearLayout.LayoutParams(AndroidUtilities.dp(92), AndroidUtilities.dp(118)));
            TextView label = text(ctx, t[0] + ((Boolean) t[6] && !StarMod.isPro() ? " ⭐" : ""), 12, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = AndroidUtilities.dp(6);
            cell.addView(label, llp);
            cell.setOnClickListener(v -> applyPreset(f, k, done));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(10);
            row.addView(cell, lp);
        }
        hs.addView(row);
        return hs;
    }

    // ================================================================ знакомство при первом запуске

    static boolean welcomeDone() {
        return prefs().getBoolean("you_welcome", false);
    }

    /** Из StarPlus.init: один раз, когда аккаунт уже вошёл. */
    static void maybeWelcome() {
        if (welcomeDone() || StarPrivacy.decoy()) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (welcomeDone() || !UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) return;
                BaseFragment top = LaunchActivity.getLastFragment();
                if (top == null || top instanceof Welcome || top.getParentActivity() == null) return;
                top.presentFragment(new Welcome());
            } catch (Exception ignore) {
            }
        }, 2500);
    }

    public static class Welcome extends UniversalFragment {

        private int step;
        private static final int ID_NEXT = 1, ID_SKIP = 2, ID_GHOST = 10, ID_SHADE = 11, ID_BUBBLE = 12, ID_FONT = 13, ID_NAME = 14;

        private static final String[][] STEPS = {
                {"🎨", "Твой стиль", "Выбери тему — её видно сразу во всём Telegram. Поменять можно в любой момент."},
                {"👻", "Будь невидимкой", "Призрак скрывает «в сети», «печатает» и «прочитано». Можно по расписанию и для отдельных чатов."},
                {"✨", "Познакомься со Звёздочкой", "Звёздочка — твой ИИ-помощник. А в чате работают 60+ команд: напиши .help."},
        };

        @Override
        protected CharSequence getTitle() {
            return "Добро пожаловать в StarTegs";
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        private View header(Context ctx) {
            String[] s = STEPS[step];
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = AndroidUtilities.dp(22);
            card.setPadding(p, p, p, p);
            int[] a = {0xFF7B5CFF, 0xFF33A6FF}, b = {0xFFFF5CA8, 0xFF8A5CFF};
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, a.clone());
            bg.setCornerRadius(AndroidUtilities.dp(24));
            card.setBackground(bg);
            breathe(card, bg, a, b);
            TextView emoji = text(ctx, s[0], 54, Color.WHITE, false);
            emoji.setGravity(Gravity.CENTER);
            card.addView(emoji);
            TextView title = text(ctx, s[1], 24, Color.WHITE, true);
            title.setGravity(Gravity.CENTER);
            card.addView(title);
            TextView sub = text(ctx, s[2], 14, 0xE6FFFFFF, false);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, AndroidUtilities.dp(6), 0, 0);
            card.addView(sub);
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < STEPS.length; i++) dots.append(i == step ? "●" : "○").append(i < STEPS.length - 1 ? "  " : "");
            TextView d = text(ctx, dots.toString(), 12, Color.WHITE, false);
            d.setGravity(Gravity.CENTER);
            d.setPadding(0, AndroidUtilities.dp(10), 0, 0);
            card.addView(d);
            FrameLayout wrap = new FrameLayout(ctx);
            int m = AndroidUtilities.dp(12);
            wrap.setPadding(m, m, m, AndroidUtilities.dp(6));
            wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrap;
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (getParentActivity() == null) return;
            items.add(UItem.asCustom(header(getParentActivity())));
            if (step == 0) {
                items.add(UItem.asHeader("Готовые темы"));
                items.add(UItem.asCustom(gallery(this, this::refresh)));
                items.add(UItem.asButton(ID_FONT, R.drawable.msg_edit, "✨ Красивый шрифт для сообщений", StarStyle.autoStyleTitle()));
                items.add(UItem.asButton(ID_NAME, R.drawable.msg_edit, "🪪 Красивое имя в профиле", ""));
            } else if (step == 1) {
                items.add(UItem.asCheck(ID_GHOST, "👻 Режим призрака" + (StarMod.isPro() ? "" : "  ⭐")).setChecked(StarSections.ghostAll()));
                items.add(UItem.asCheck(ID_SHADE, "🫥 Шторка приватности" + (StarMod.isPro() ? "" : "  ⭐")).setChecked(StarShade.enabled()));
                items.add(UItem.asShadow("Удалённые сообщения сохраняются автоматически — ты увидишь, что именно стёрли. Всё настраивается в «Приватности» и «Сообщениях»."));
            } else {
                items.add(UItem.asCheck(ID_BUBBLE, "⭐ Плавающая кнопка со Звёздочкой").setChecked(StarDecor.bubbleOn()));
                items.add(UItem.asShadow("Попробуй в любом чате: .hug Имя, .rps камень, .ball вопрос, .font 4 текст. А долгое нажатие на сообщение → «🧠 В память»."));
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(ID_NEXT, step < STEPS.length - 1 ? "Дальше →" : "Готово 🎉", "").accent());
            if (step < STEPS.length - 1) items.add(UItem.asButton(ID_SKIP, "Пропустить", ""));
        }

        private void finish() {
            prefs().edit().putBoolean("you_welcome", true).apply();
            if (getParentActivity() != null) Toast.makeText(getParentActivity(), "🎉 Всё готово! StarTegs — в Настройках и кнопкой ⭐ над чатами", Toast.LENGTH_LONG).show();
            finishFragment();
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            switch (item.id) {
                case ID_NEXT:
                    if (step < STEPS.length - 1) {
                        step++;
                        refresh();
                    } else finish();
                    return;
                case ID_SKIP:
                    finish();
                    return;
                case ID_GHOST:
                    if (!StarMod.requirePro(this, "Режим призрака")) return;
                    StarSections.setGhostAll(!StarSections.ghostAll());
                    break;
                case ID_SHADE:
                    if (!StarMod.requirePro(this, "Шторка приватности")) return;
                    StarMod.toggle("shade_on", false);
                    StarShade.update();
                    break;
                case ID_BUBBLE:
                    StarDecor.toggle("decor_bubble", false);
                    StarDecor.updateBubble();
                    break;
                case ID_FONT:
                    StarStyle.chooseAuto(this, this::refresh);
                    return;
                case ID_NAME:
                    StarStyle.fancyName(this);
                    return;
                default:
                    return;
            }
            refresh();
        }

        @Override
        public boolean onBackPressed(boolean invoked) {
            prefs().edit().putBoolean("you_welcome", true).apply();
            return super.onBackPressed(invoked);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
