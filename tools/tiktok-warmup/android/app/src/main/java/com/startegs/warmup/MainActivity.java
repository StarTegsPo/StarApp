package com.startegs.warmup;

import android.animation.ArgbEvaluator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private Theme th;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<View> entrance = new ArrayList<>();
    private final List<ValueAnimator> loops = new ArrayList<>();
    private boolean reduceMotion;

    private FrameLayout frame, root;
    private Anim.Confetti confetti;
    private Anim.Pulse pulse;
    private View mainWrap;
    private LinearLayout updateCard;
    /** Снимок старой темы для «волны» при смене темы (живёт между recreate). */
    private static android.graphics.Bitmap themeShot;
    private static float themeX, themeY;
    private TextView heroStatus, chipVideos, chipLikes, chipFav, chipRepost, chipPhotos, chipTime, chipComments, chipSkipped;
    private ShimmerButton mainButton;
    private LinearLayout connectBox, connectedBox;
    private TextView parallelHow;
    private boolean photosOn, skipLiveOn, skipAdsOn, capLoopsOn, premium;
    private View premiumCard;
    private TextView freeLeftText;
    private View freeFill;
    private Toggle tPhotos, tLive, tAds;
    private TextView batteryStatus, exactStatus, historyText;
    private BarChart historyChart;
    private View exactButton;
    private String emojiText;
    private int[] last = {-1, -1, -1, -1, -1, -1, -1};
    private String lastUiState = "";

    // ---------- настройка «− значение +» ----------
    private class Stepper {
        final String key;
        final int min, max, step;
        final boolean infiniteZero;
        final String unit;
        int value;
        boolean pro;
        TextView valueView;

        Stepper(String key, int def, int min, int max, int step, String unit, boolean infiniteZero) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
            this.infiniteZero = infiniteZero;
            this.value = Math.max(min, Math.min(max, prefs.getInt(key, def)));
        }

        String text() {
            return infiniteZero && value == 0 ? "∞" : value + unit;
        }

        void change(int dir) {
            if (pro && !premium) {
                locked(valueView);
                return;
            }
            int nv = Math.max(min, Math.min(max, value + dir * step));
            if (nv == value) {
                shake(valueView);
                return;
            }
            value = nv;
            valueView.setText(text());
            pop(valueView);
            prefs.edit().putInt(key, value).apply();
        }

        void set(int v) {
            value = Math.max(min, Math.min(max, v));
            valueView.setText(text());
            prefs.edit().putInt(key, value).apply();
            pop(valueView);
        }
    }

    private Stepper sVideos, sLikeMin, sLikeMax, sWatchMin, sWatchMax, sSkip, sLong, sFav, sRepost, sComment, sPhrase;

    // =====================================================================

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        th = Theme.get(this);
        reduceMotion = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        premium = Premium.isActive(this);
        photosOn = prefs.getBoolean("photos", true);
        capLoopsOn = prefs.getBoolean("capLoops", true);
        skipLiveOn = prefs.getBoolean("skipLive", true);
        skipAdsOn = prefs.getBoolean("skipAds", true);
        emojiText = prefs.getString("emoji", WarmupService.DEFAULT_EMOJI);

        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        int flags = 0;
        if (th.light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);

        frame = new FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new FxView(this, th), new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(32));
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        col.setLayoutTransition(lt);
        scroll.addView(col);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root = new FrameLayout(this);
        root.setBackgroundColor(th.bg);
        root.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        confetti = new Anim.Confetti(this);
        root.addView(confetti, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        col.addView(buildThemeSwitch());
        col.addView(buildProfiles());
        col.addView(buildUpdateCard());
        col.addView(buildHero());
        col.addView(buildMainButton());
        col.addView(buildPremiumCard());
        col.addView(buildConnectCard());
        col.addView(buildBatteryCard());
        col.addView(buildBestCard());
        col.addView(buildScheduleCard());
        col.addView(buildNicheCard());
        col.addView(buildLikesCard());
        col.addView(buildActionsCard());
        col.addView(buildSkipCard());
        col.addView(buildCommentsCard());
        col.addView(buildWatchCard());
        col.addView(buildHistoryCard());
        col.addView(buildParallelCard());
        col.addView(buildFooter());

        boolean revealing = playThemeReveal();
        boolean onboarding = !prefs.getBoolean("onboarded", false);
        if (onboarding) {
            showOnboarding();
        } else if (!revealing) {
            runEntrance();
        }
        if (!onboarding) maybeShowWhatsNew();
        if (prefs.getBoolean("celebrate", false)) {
            prefs.edit().remove("celebrate").apply();
            root.postDelayed(() -> celebrate(mainButton), 700);
        }
        Updater.check(this, this::showUpdate);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 33);
        }
        Schedule.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        for (ValueAnimator a : loops) a.cancel();
        super.onDestroy();
    }

    // ---------- живое обновление ----------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 500);
        }
    };

    private void refresh() {
        WarmupService s = WarmupService.instance;
        boolean enabled = s != null;
        boolean running = WarmupService.isRunning();
        String st = s == null ? "off" : s.state();

        connectBox.setVisibility(enabled ? View.GONE : View.VISIBLE);
        connectedBox.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (freeLeftText != null) {
            int left = Premium.leftToday(this);
            freeLeftText.setText("Бесплатно: осталось " + left + " из " + Premium.FREE_DAILY + " постов сегодня");
            freeFill.setPivotX(0);
            freeFill.setScaleX(left / (float) Premium.FREE_DAILY);
        }

        updateSystemStatus();

        String status;
        if (!running && s != null && "limit".equals(s.stopReason())) st = "limit";
        switch (st) {
            case "limit": status = "Бесплатный лимит на сегодня исчерпан"; break;
            case "off": status = "Нужно подключить доступ"; break;
            case WarmupService.RUNNING: status = "Идёт прогрев"; break;
            case WarmupService.PHOTOS: status = "Листаю фото-пост"; break;
            case WarmupService.PAUSED: status = "Пауза — TikTok не на экране"; break;
            case WarmupService.BREAK: status = "Перерыв, как у живого человека"; break;
            default: status = "Готов к запуску";
        }
        if (!status.equals(heroStatus.getText().toString())) {
            heroStatus.setAlpha(0f);
            heroStatus.setText(status);
            heroStatus.animate().alpha(1f).setDuration(250).start();
        }

        if (running) {
            int total = s.videos();
            setChip(0, chipVideos, s.done(), "🎬 ", total > 0 ? " / " + total : "");
            setChip(1, chipLikes, s.likes(), "❤ ", "");
            setChip(2, chipFav, s.favorites(), "★ ", "");
            setChip(3, chipRepost, s.reposts(), "↻ ", "");
            setChip(4, chipPhotos, s.photos(), "🖼 ", "");
            setChip(5, chipComments, s.comments(), "💬 ", "");
            setChip(6, chipSkipped, s.skipped(), "⏭ ", "");
            long sec = s.elapsedMs() / 1000;
            chipTime.setText(String.format(Locale.US, "⏱ %d:%02d", sec / 60, sec % 60));
        }

        String uiState = running ? "run" : (enabled ? "ready" : "off");
        boolean first = lastUiState.isEmpty();
        if ("run".equals(lastUiState) && !running) {   // сессия закончилась — история и праздник
            updateHistory();
            if (s != null && s.done() > 0) celebrate(mainButton);
        }
        if (!uiState.equals(lastUiState)) {
            lastUiState = uiState;
            pulse.setActive(running);
            if (running) {
                mainButton.setText("■  Остановить");
                mainButton.setColors(th.a3, th.a1);
                mainButton.setShimmer(false);
            } else {
                mainButton.setText("▶  Начать прогрев");
                mainButton.setColors(th.a1, th.a2);
                mainButton.setShimmer(true);
            }
            if (!first) pop(mainButton);
        }
    }

    private void setChip(int i, TextView chip, int v, String prefix, String suffix) {
        if (last[i] == v) return;
        Anim.countTo(chip, prefix, last[i], v, suffix);
        if (last[i] >= 0) pop(chip);
        last[i] = v;
    }

    // ---------- переключатель темы ----------

    private View buildThemeSwitch() {
        final FrameLayout box = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.card);
        bg.setCornerRadius(dp(99));
        bg.setStroke(dp(1), th.line);
        box.setBackground(bg);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));

        final View indicator = new View(this);
        GradientDrawable ind = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{th.a1, th.a2});
        ind.setCornerRadius(dp(99));
        indicator.setBackground(ind);
        box.addView(indicator, new FrameLayout.LayoutParams(0, dp(36)));

        final LinearLayout row = new LinearLayout(this);
        box.addView(row, new FrameLayout.LayoutParams(-1, dp(36)));
        int current = 0;
        for (int i = 0; i < Theme.IDS.length; i++) if (Theme.IDS[i].equals(th.id)) current = i;
        final int cur = current;
        for (int i = 0; i < Theme.IDS.length; i++) {
            final int idx = i;
            TextView t = text(Theme.TITLES[i], 13, i == cur ? Color.WHITE : th.text, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(4), 0, dp(4), 0);
            if (Build.VERSION.SDK_INT >= 26) {
                t.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            }
            t.setOnClickListener(v -> {
                if (idx == cur) return;
                indicator.animate().translationX(row.getWidth() / 3f * idx).setDuration(260)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                Theme.save(this, Theme.IDS[idx]);
                int[] loc = new int[2];
                v.getLocationInWindow(loc);
                themeX = loc[0] + v.getWidth() / 2f;
                themeY = loc[1] + v.getHeight() / 2f;
                ui.postDelayed(() -> {
                    if (!reduceMotion) {
                        try {
                            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(root.getWidth(),
                                    root.getHeight(), android.graphics.Bitmap.Config.RGB_565);
                            root.draw(new android.graphics.Canvas(b));
                            themeShot = b;
                        } catch (Throwable ignored) {
                            themeShot = null;
                        }
                    }
                    recreate();
                    overridePendingTransition(0, 0);
                }, 240);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        // индикатор под выбранной темой (ширина известна после разметки)
        row.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicator.getLayoutParams();
            lp.width = row.getWidth() / 3;
            indicator.setLayoutParams(lp);
            indicator.setTranslationX(row.getWidth() / 3f * cur);
        });
        entrance.add(box);
        return withMargin(box, dp(14));
    }

    // ---------- шапка ----------

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(18));
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{th.a1, th.a3});
        bg.setCornerRadius(dp(26));
        hero.setBackground(bg);
        hero.setElevation(dp(10));
        glow(hero, th.a1);
        if (!reduceMotion) {
            // градиент медленно «переливается»
            final ArgbEvaluator ev = new ArgbEvaluator();
            ValueAnimator va = loop(7000, true);
            va.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                bg.setColors(new int[]{(int) ev.evaluate(f, th.a1, th.a2), (int) ev.evaluate(f, th.a3, th.a1)});
            });
            va.start();
        }

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heart = text("❤", 30, Color.WHITE, false);
        heart.setGravity(Gravity.CENTER);
        GradientDrawable hb = new GradientDrawable();
        hb.setShape(GradientDrawable.OVAL);
        hb.setColor(0x33FFFFFF);
        hb.setStroke(dp(1), 0x40FFFFFF);
        heart.setBackground(hb);
        top.addView(heart, new LinearLayout.LayoutParams(dp(58), dp(58)));
        heartbeat(heart);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(text("Прогрев TikTok", 24, Color.WHITE, true));
        heroStatus = text("Готов к запуску", 14, 0xE6FFFFFF, false);
        titles.addView(heroStatus);
        top.addView(titles);
        hero.addView(top);

        LinearLayout r1 = new LinearLayout(this);
        r1.setPadding(0, dp(16), 0, 0);
        chipVideos = chip("🎬 0");
        chipLikes = chip("❤ 0");
        chipTime = chip("⏱ 0:00");
        r1.addView(chipVideos);
        r1.addView(chipLikes);
        r1.addView(chipTime);
        hero.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        r2.setPadding(0, dp(8), 0, 0);
        chipFav = chip("★ 0");
        chipRepost = chip("↻ 0");
        chipPhotos = chip("🖼 0");
        r2.addView(chipFav);
        r2.addView(chipRepost);
        chipComments = chip("💬 0");
        r2.addView(chipComments);
        hero.addView(r2);
        LinearLayout r3 = new LinearLayout(this);
        r3.setPadding(0, dp(8), 0, 0);
        chipSkipped = chip("⏭ 0");
        r3.addView(chipPhotos);
        r3.addView(chipSkipped);
        hero.addView(r3);

        entrance.add(hero);
        return withMargin(hero, dp(14));
    }

    private TextView chip(String s) {
        TextView t = text(s, 13, Color.WHITE, true);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x2EFFFFFF);
        d.setCornerRadius(dp(99));
        t.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    // ---------- главная кнопка ----------

    private View buildMainButton() {
        mainButton = new ShimmerButton(this, th.a1, th.a2);
        mainButton.setText("▶  Начать прогрев");
        mainButton.setTextSize(17);
        mainButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mainButton.setElevation(dp(8));
        glow(mainButton, th.a1);
        pressable(mainButton);
        mainButton.setOnClickListener(v -> {
            if (WarmupService.isRunning()) {
                WarmupService.instance.stop();
                refresh();
            } else {
                startWarmup();
            }
        });
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        pulse = new Anim.Pulse(this, th.a1, dp(12));
        wrap.addView(pulse, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, dp(60));
        blp.setMargins(dp(12), dp(12), dp(12), dp(12));
        wrap.addView(mainButton, blp);
        mainWrap = wrap;
        entrance.add(wrap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(84));
        lp.setMargins(-dp(12), -dp(12), -dp(12), dp(2));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    // ---------- Premium ----------

    private View buildPremiumCard() {
        if (Premium.lifetime(this)) {
            ShimmerButton b = new ShimmerButton(this, th.a1, 0xFFFFB648);
            b.setText("👑  Premium активен · все функции открыты");
            b.setTextSize(15);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setElevation(dp(6));
            glow(b, 0xFFFFB648);
            b.setClickable(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
            lp.bottomMargin = dp(12);
            b.setLayoutParams(lp);
            entrance.add(b);
            premiumCard = b;
            return b;
        }
        LinearLayout c = card();
        premiumCard = c;
        c.addView(cardTitle("👑", "Premium"));
        String kind = Premium.kind(this);
        if (!"free".equals(kind)) {
            long left = "trial".equals(kind) ? Premium.trialLeftMs(this) : Premium.monthLeftMs(this);
            String status = "trial".equals(kind)
                    ? "🎁 Пробный Premium — осталось " + Math.max(1, left / 3_600_000L) + " ч"
                    : "👑 Premium на месяц — осталось " + Math.max(1, left / Premium.DAY) + " дн.";
            TextView st = text(status, 15, Color.WHITE, true);
            st.setPadding(dp(14), dp(10), dp(14), dp(10));
            GradientDrawable sb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{th.a1, 0xFFFFB648});
            sb.setCornerRadius(dp(14));
            st.setBackground(sb);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.bottomMargin = dp(8);
            c.addView(st, slp);
        }
        TextView feats = text("✓ Без дневного лимита постов\n"
                + "✓ Авто-пропуск рекламы\n"
                + "✓ Комментарии: эмодзи и фразы\n"
                + "✓ Репосты с текстом", 14, th.text, false);
        feats.setPadding(0, dp(4), 0, dp(10));
        c.addView(feats);

        TextView prices = text("Навсегда — 50 ⭐  ·  Месяц — 30 ⭐", 14, th.text, true);
        prices.setPadding(0, 0, 0, dp(8));
        c.addView(prices);
        if (!premium) {
            freeLeftText = text("", 13, th.hint, false);
            c.addView(freeLeftText);
        }
        FrameLayout track = new FrameLayout(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(th.soft);
        tbg.setCornerRadius(dp(99));
        track.setBackground(tbg);
        freeFill = new View(this);
        GradientDrawable fbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{th.a1, th.a2});
        fbg.setCornerRadius(dp(99));
        freeFill.setBackground(fbg);
        track.addView(freeFill, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(8));
        tlp.topMargin = dp(6);
        if (!premium) c.addView(track, tlp);

        ShimmerButton buy = new ShimmerButton(this, th.a1, 0xFFFFB648);
        buy.setText("👑  Купить Premium");
        buy.setTextSize(16);
        buy.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        buy.setElevation(dp(6));
        glow(buy, th.a1);
        pressable(buy);
        buy.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Premium.BUY_URL)));
            } catch (RuntimeException e) {
                Toast.makeText(this, Premium.BUY_URL, Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(52));
        blp.topMargin = dp(16);
        c.addView(buy, blp);

        final LinearLayout codeBox = new LinearLayout(this);
        codeBox.setOrientation(LinearLayout.VERTICAL);
        codeBox.setVisibility(View.GONE);
        final EditText code = new EditText(this);
        code.setHint("STAR-XXXX-XXXX-XXXX");
        code.setTextColor(th.text);
        code.setHintTextColor(th.hint);
        code.setTextSize(16);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(th.soft);
        cb.setCornerRadius(dp(14));
        cb.setStroke(dp(1), th.line);
        code.setBackground(cb);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(12);
        codeBox.addView(code, clp);
        TextView activate = smallButton("Активировать");
        activate.setOnClickListener(v -> {
            int res = Premium.activateResult(this, code.getText().toString());
            if (res == Premium.LIFETIME || res == Premium.MONTH) {
                prefs.edit().putBoolean("celebrate", true).apply();
                Toast.makeText(this, res == Premium.LIFETIME ? "👑 Premium навсегда активирован!"
                        : "👑 Premium на 30 дней активирован!", Toast.LENGTH_LONG).show();
                frame.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    recreate();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
            } else {
                shake(code);
                Toast.makeText(this, res == Premium.USED ? "Этот код уже был активирован на этом телефоне"
                        : res == Premium.EXPIRED ? "Срок активации этого кода истёк — купите новый в боте"
                        : "Неверный код", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = dp(10);
        codeBox.addView(activate, alp);
        c.addView(codeBox);

        TextView have = smallButton("У меня есть код");
        have.setOnClickListener(v -> {
            boolean show = codeBox.getVisibility() != View.VISIBLE;
            codeBox.setVisibility(show ? View.VISIBLE : View.GONE);
            ((TextView) v).setText(show ? "Скрыть" : "У меня есть код");
        });
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = dp(10);
        c.addView(have, hlp);
        return c;
    }

    // ---------- лучшие настройки ----------

    private View buildBestCard() {
        LinearLayout c = card();
        c.addView(cardTitle("🛡", "Лучшие настройки"));
        c.addView(note("Минимальный риск бана: спокойный темп, мало действий, перерывы и пропуск эфиров/рекламы. "
                + "Запускайте 2–3 раза в день по 30–40 минут."));
        ShimmerButton best = new ShimmerButton(this, th.a3, th.a1);
        best.setText("✨  Применить лучшие настройки");
        best.setTextSize(15);
        best.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        best.setElevation(dp(5));
        glow(best, th.a1);
        pressable(best);
        best.setOnClickListener(v -> applyBest());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(12);
        c.addView(best, lp);
        TextView imp = smallButton("📥 Вставить настройки с iPhone");
        imp.setOnClickListener(v -> importSettings());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.topMargin = dp(10);
        c.addView(imp, ilp);
        return c;
    }

    /** Настройки из iPhone-приложения: код TWSET:<base64 JSON> из буфера обмена. */
    private void importSettings() {
        String text = "";
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData cd = cm == null ? null : cm.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null) {
                text = cd.getItemAt(0).getText().toString().trim();
            }
        } catch (RuntimeException ignored) { }
        if (!text.startsWith("TWSET:")) {
            Toast.makeText(this, "В буфере нет кода настроек. На iPhone нажмите «▶ Начать прогрев» — код скопируется",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String json = new String(android.util.Base64.decode(text.substring(6), android.util.Base64.DEFAULT),
                    java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject o = new org.json.JSONObject(json);
            Cfg.restore(this, o);
            Toast.makeText(this, "📥 Настройки с iPhone применены", Toast.LENGTH_LONG).show();
            frame.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                recreate();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }).start();
        } catch (Exception e) {
            shake(premiumCard);
            Toast.makeText(this, "Код настроек повреждён — скопируйте его на iPhone ещё раз", Toast.LENGTH_LONG).show();
        }
    }

    private void applyBest() {
        sVideos.set(80);
        sLikeMin.set(4);
        sLikeMax.set(15);
        sWatchMin.set(7);
        sWatchMax.set(30);
        sSkip.set(15);
        sLong.set(15);
        sFav.set(3);
        tPhotos.set.on(true);
        tLive.set.on(true);
        if (premium) {
            sRepost.set(1);
            sComment.set(1);
            sPhrase.set(70);
            tAds.set.on(true);
        }
        Toast.makeText(this, "✨ Готово! Безопасные настройки применены", Toast.LENGTH_LONG).show();
    }

    // ---------- подключение ----------

    private View buildConnectCard() {
        LinearLayout c = card();

        // --- не подключено ---
        connectBox = new LinearLayout(this);
        connectBox.setOrientation(LinearLayout.VERTICAL);
        connectBox.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout plugWrap = new FrameLayout(this);
        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setShape(GradientDrawable.OVAL);
        rd.setStroke(dp(2), th.a1);
        ring.setBackground(rd);
        plugWrap.addView(ring, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        TextView plug = text("🔌", 28, Color.WHITE, false);
        plug.setGravity(Gravity.CENTER);
        GradientDrawable pd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{th.a1, th.a3});
        pd.setShape(GradientDrawable.OVAL);
        plug.setBackground(pd);
        plugWrap.addView(plug, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        connectBox.addView(plugWrap, new LinearLayout.LayoutParams(dp(96), dp(96)));
        if (!reduceMotion) {
            // расходящееся кольцо-«радар» вокруг иконки
            ValueAnimator va = loop(1800, false);
            va.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                ring.setScaleX(0.9f + 0.45f * f);
                ring.setScaleY(0.9f + 0.45f * f);
                ring.setAlpha(1f - f);
            });
            va.start();
        }

        TextView title = text("Подключите прогрев", 18, th.text, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(6), 0, dp(4));
        connectBox.addView(title);
        TextView sub = text("Один раз включите «Прогрев TikTok» в Спецвозможностях — без этого приложение не может листать ленту.",
                13, th.hint, false);
        sub.setGravity(Gravity.CENTER);
        connectBox.addView(sub);

        ShimmerButton connect = new ShimmerButton(this, th.a1, th.a3);
        connect.setText("Подключить  →");
        connect.setTextSize(16);
        connect.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        connect.setElevation(dp(6));
        glow(connect, th.a1);
        pressable(connect);
        breathe(connect);
        connect.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Найдите «Прогрев TikTok» и включите", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(54));
        blp.topMargin = dp(16);
        connectBox.addView(connect, blp);

        TextView restricted = text("Пишет «Настройка ограничена»? Настройки → Приложения → Прогрев TikTok → ⋮ → "
                + "«Разрешить ограниченные настройки», затем снова «Подключить».", 12, th.hint, false);
        restricted.setGravity(Gravity.CENTER);
        restricted.setPadding(0, dp(12), 0, 0);
        connectBox.addView(restricted);
        c.addView(connectBox);

        // --- подключено ---
        connectedBox = new LinearLayout(this);
        connectedBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView ok = text("✓", 18, Color.WHITE, true);
        ok.setGravity(Gravity.CENTER);
        GradientDrawable od = new GradientDrawable();
        od.setShape(GradientDrawable.OVAL);
        od.setColor(th.good);
        ok.setBackground(od);
        connectedBox.addView(ok, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout okText = new LinearLayout(this);
        okText.setOrientation(LinearLayout.VERTICAL);
        okText.setPadding(dp(12), 0, 0, 0);
        okText.addView(text("Доступ подключён", 16, th.text, true));
        okText.addView(text("Можно запускать прогрев", 13, th.hint, false));
        connectedBox.addView(okText);
        c.addView(connectedBox);
        return c;
    }

    // ---------- карточки настроек ----------

    private View buildLikesCard() {
        LinearLayout c = card();
        c.addView(cardTitle("❤", "Лайки"));
        sLikeMin = stepper(c, "Лайк через: от", "likeMin", 1, 1, 100, 1, " вид.", false);
        sLikeMax = stepper(c, "Лайк через: до", "likeMax", 18, 1, 100, 1, " вид.", false);
        sVideos = stepper(c, "Постов за запуск", "videos", 120, 0, 2000, 10, "", true);
        c.addView(note("∞ — без ограничения, до нажатия «Стоп»"));
        return c;
    }

    private View buildActionsCard() {
        LinearLayout c = card();
        c.addView(cardTitle("✦", "Как живой человек"));
        sFav = stepper(c, "★ В избранное", "favChance", 4, 0, 50, 1, "%", false);
        sRepost = stepper(c, "↻ Репост", "repostChance", 2, 0, 50, 1, "%", false, true);
        editor(c, "Текст к репосту — если TikTok предложит «Добавить мысли» (по одному в строке)",
                "repostTexts", WarmupService.DEFAULT_REPOST, true, 15, true);
        tPhotos = toggleRow("🖼 Листать фото-посты", "Смотрит каждое фото 1,5–4,5 с, иногда возвращается назад",
                photosOn, on -> {
                    photosOn = on;
                    prefs.edit().putBoolean("photos", on).apply();
                }, false);
        c.addView(tPhotos.row);
        c.addView(note("Проценты — шанс действия на каждом посте. Репосты видят ваши подписчики — "
                + "не ставьте много."));
        return c;
    }

    private View buildSkipCard() {
        LinearLayout c = card();
        c.addView(cardTitle("⏭", "Пропускать"));
        tLive = toggleRow("📡 Прямые эфиры (LIVE)", "Эфир в ленте сразу пролистывается, внутрь не заходит",
                skipLiveOn, on -> {
                    skipLiveOn = on;
                    prefs.edit().putBoolean("skipLive", on).apply();
                }, false);
        c.addView(tLive.row);
        tAds = toggleRow("📢 Авто-пропуск рекламы", "Посты с пометкой «Реклама» / «Sponsored» — пролист за 1–2 с, без лайков",
                skipAdsOn && premium, on -> {
                    skipAdsOn = on;
                    prefs.edit().putBoolean("skipAds", on).apply();
                }, true);
        c.addView(tAds.row);
        return c;
    }

    private View buildCommentsCard() {
        LinearLayout c = card();
        c.addView(cardTitle("💬", "Комментарии"));
        sComment = stepper(c, "Комментировать", "commentChance", 2, 0, 30, 1, "%", false, true);
        sPhrase = stepper(c, "Из них фразами", "phrasePercent", 60, 0, 100, 10, "%", false, true);
        final EditText em = editor(c, "Эмодзи (через пробел)", "emoji", WarmupService.DEFAULT_EMOJI, false, 20, true);
        final EditText ph = editor(c, "Фразы (по одной в строке)", "phrases", WarmupService.DEFAULT_PHRASES, true, 15, true);
        TextView reset = smallButton("Вернуть наборы по умолчанию");
        reset.setOnClickListener(v -> {
            if (!premium) { locked(v); return; }
            em.setText(WarmupService.DEFAULT_EMOJI);
            ph.setText(WarmupService.DEFAULT_PHRASES);
            pop(em);
            pop(ph);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        c.addView(reset, lp);
        c.addView(note("Пишет фразу (иногда с эмодзи) или 1–3 эмодзи и закрывает комментарии. "
                + "Комментарии видят все — держите процент маленьким."));
        return c;
    }

    private View buildWatchCard() {
        LinearLayout c = card();
        c.addView(cardTitle("⏱", "Время просмотра"));
        sWatchMin = stepper(c, "Смотреть от", "watchMin", 5, 1, 300, 1, " с", false);
        sWatchMax = stepper(c, "Смотреть до", "watchMax", 25, 1, 300, 1, " с", false);
        sSkip = stepper(c, "Быстро пролистать (1–3 с)", "skipChance", 15, 0, 100, 5, "%", false);
        sLong = stepper(c, "Досмотреть подольше", "longChance", 10, 0, 100, 5, "%", false);
        c.addView(toggleRow("🔁 Не дольше 1,5 длины ролика",
                "Короткий ролик не крутится по кругу: 10-секундный — максимум 15 с. На перерывах видео на паузе",
                capLoopsOn, on -> {
                    capLoopsOn = on;
                    prefs.edit().putBoolean("capLoops", on).apply();
                }, false).row);
        c.addView(note("Время каждого ролика случайное. «Подольше» — до двух «до»."));
        return c;
    }

    private View buildParallelCard() {
        LinearLayout c = card();
        c.addView(cardTitle("📱", "Другое приложение параллельно"));
        c.addView(note("Откройте TikTok в разделённом экране или плавающем окне — прогрев идёт в нём, "
                + "а второй половиной экрана пользуйтесь как обычно."));
        parallelHow = text("1. Нажмите «Начать прогрев» — откроется TikTok.\n"
                + "2. Откройте список недавних приложений.\n"
                + "3. Нажмите на иконку TikTok сверху карточки → «Разделить экран» "
                + "(или «Открыть в окне» / «Всплывающее окно» на Samsung и Xiaomi).\n"
                + "4. Во вторую половину выберите нужное приложение.\n\n"
                + "Плашку «● прогрев» можно перетащить пальцем. Нажатие на неё — остановка.", 13, th.text, false);
        parallelHow.setPadding(0, dp(10), 0, dp(4));
        parallelHow.setVisibility(View.GONE);
        c.addView(parallelHow);
        TextView more = smallButton("Как это сделать?");
        more.setOnClickListener(v -> {
            boolean show = parallelHow.getVisibility() != View.VISIBLE;
            parallelHow.setVisibility(show ? View.VISIBLE : View.GONE);
            ((TextView) v).setText(show ? "Скрыть" : "Как это сделать?");
            if (show && !reduceMotion) {
                parallelHow.setAlpha(0f);
                parallelHow.setTranslationY(-dp(8));
                parallelHow.animate().alpha(1f).translationY(0).setDuration(300).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        c.addView(more, lp);
        return c;
    }

    private View buildFooter() {
        TextView t = text("TikTok запрещает автоматизацию — не гоняйте прогрев часами подряд, "
                + "чтобы не получить теневой бан.", 12, th.hint, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), 0);
        entrance.add(t);
        return t;
    }

    // ---------- анимации: праздник, наклон карточек, смена темы ----------

    /** Конфетти и сердечки из центра view. */
    private void celebrate(View from) {
        if (from == null || reduceMotion) return;
        int[] loc = new int[2], rl = new int[2];
        from.getLocationInWindow(loc);
        root.getLocationInWindow(rl);
        confetti.burst(loc[0] - rl[0] + from.getWidth() / 2f, loc[1] - rl[1] + from.getHeight() / 2f, 90,
                new int[]{th.a1, th.a2, th.a3, 0xFFFFB648, 0xFFFFFFFF, th.spark});
    }

    /** Карточка слегка наклоняется к пальцу, как физический объект. */
    private void tilt(final View v) {
        if (reduceMotion) return;
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    float rx = (e.getY() / Math.max(1, view.getHeight()) - 0.5f) * -6f;
                    float ry = (e.getX() / Math.max(1, view.getWidth()) - 0.5f) * 6f;
                    view.setCameraDistance(view.getWidth() * 12f);
                    view.animate().rotationX(rx).rotationY(ry).scaleX(0.985f).scaleY(0.985f).setDuration(140).start();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().rotationX(0).rotationY(0).scaleX(1f).scaleY(1f).setDuration(380)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    return true;
            }
            return false;
        });
    }

    /** Новая тема раскрывается «волной» из точки нажатия поверх снимка старой. */
    private boolean playThemeReveal() {
        final android.graphics.Bitmap shot = themeShot;
        themeShot = null;
        if (shot == null || reduceMotion) return false;
        final android.widget.ImageView old = new android.widget.ImageView(this);
        old.setImageBitmap(shot);
        old.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        root.addView(old, 0, new FrameLayout.LayoutParams(-1, -1));
        frame.setVisibility(View.INVISIBLE);
        frame.post(() -> {
            float r = (float) Math.hypot(Math.max(themeX, frame.getWidth() - themeX),
                    Math.max(themeY, frame.getHeight() - themeY));
            android.animation.Animator a = android.view.ViewAnimationUtils.createCircularReveal(
                    frame, (int) themeX, (int) themeY, 0, r);
            a.setDuration(650);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    root.removeView(old);
                    shot.recycle();
                }
            });
            frame.setVisibility(View.VISIBLE);
            a.start();
        });
        return true;
    }

    // ---------- обновления и «Что нового» ----------

    private View buildUpdateCard() {
        updateCard = card();
        entrance.remove(updateCard);
        updateCard.setVisibility(View.GONE);
        return updateCard;
    }

    private void showUpdate(int code, String name, String notes) {
        if (updateCard == null || updateCard.getVisibility() == View.VISIBLE) return;
        updateCard.removeAllViews();
        updateCard.addView(cardTitle("🆕", "Доступна версия " + name));
        if (!notes.isEmpty()) {
            TextView n = text(notes, 13, th.hint, false);
            n.setMaxLines(6);
            updateCard.addView(n);
        }
        ShimmerButton upd = new ShimmerButton(this, th.a1, th.a2);
        upd.setText("⬇  Обновить");
        upd.setTextSize(15);
        upd.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pressable(upd);
        upd.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Updater.APK_URL)));
            } catch (RuntimeException e) {
                Toast.makeText(this, Updater.APK_URL, Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(10);
        updateCard.addView(upd, lp);
        TextView bot = smallButton("Скачать через бота");
        bot.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Premium.BOT_URL)));
            } catch (RuntimeException ignored) { }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = dp(8);
        updateCard.addView(bot, blp);
        updateCard.setVisibility(View.VISIBLE);
        if (!reduceMotion) {
            updateCard.setAlpha(0f);
            updateCard.setTranslationY(-dp(20));
            updateCard.animate().alpha(1f).translationY(0).setDuration(450)
                    .setInterpolator(new OvershootInterpolator(1.4f)).start();
        }
    }

    /** После обновления — карточка «Что нового» (на свежей установке не показывается). */
    private void maybeShowWhatsNew() {
        int cur = Updater.currentCode(this);
        int seen = prefs.getInt("seenVersion", 0);
        prefs.edit().putInt("seenVersion", cur).apply();
        if (seen == 0 || cur <= seen) return;
        String notes = Updater.bundledNotes(this);
        if (notes.isEmpty()) return;
        final FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0x99000000);
        dim.setClickable(true);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(22), dp(22), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.light ? Color.WHITE : th.bg);
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), th.line);
        c.setBackground(bg);
        c.setElevation(dp(16));
        TextView title = text("✨ Что нового в " + Updater.currentName(this), 20, th.text, true);
        c.addView(title);
        TextView body = text(notes, 14, th.text, false);
        body.setPadding(0, dp(12), 0, dp(16));
        c.addView(body);
        ShimmerButton ok = new ShimmerButton(this, th.a1, th.a2);
        ok.setText("Круто!");
        ok.setTextSize(16);
        pressable(ok);
        c.addView(ok, new LinearLayout.LayoutParams(-1, dp(50)));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        clp.setMargins(dp(20), 0, dp(20), 0);
        dim.addView(c, clp);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        confetti.bringToFront();
        Runnable close = () -> dim.animate().alpha(0f).setDuration(250).withEndAction(() -> root.removeView(dim)).start();
        ok.setOnClickListener(v -> {
            celebrate(v);
            close.run();
        });
        dim.setOnClickListener(v -> close.run());
        if (!reduceMotion) {
            dim.setAlpha(0f);
            dim.animate().alpha(1f).setDuration(250).start();
            c.setScaleX(0.85f);
            c.setScaleY(0.85f);
            c.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(new OvershootInterpolator(1.8f)).start();
        }
    }

    // ---------- онбординг ----------

    private int obIndex;

    /** Знакомство при первом запуске: 3 анимированных экрана. */
    private void showOnboarding() {
        final FrameLayout ob = new FrameLayout(this);
        ob.setBackgroundColor(th.bg);
        ob.setClickable(true);
        ob.addView(new FxView(this, th), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(48), dp(24), dp(28));
        final FrameLayout stage = new FrameLayout(this);
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        final View[] slides = {obSlide(0), obSlide(1), obSlide(2)};
        for (View sl : slides) {
            sl.setVisibility(View.INVISIBLE);
            stage.addView(sl, new FrameLayout.LayoutParams(-1, -1));
        }

        final LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, dp(16), 0, dp(18));
        for (int i = 0; i < 3; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{th.a1, th.a2});
            g.setCornerRadius(dp(99));
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(d, lp);
        }
        col.addView(dots);

        final ShimmerButton next = new ShimmerButton(this, th.a1, th.a2);
        next.setTextSize(17);
        next.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        glow(next, th.a1);
        pressable(next);
        col.addView(next, new LinearLayout.LayoutParams(-1, dp(58)));
        ob.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView skip = text("Пропустить", 14, th.hint, true);
        skip.setPadding(dp(16), dp(10), dp(16), dp(10));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        slp.setMargins(0, dp(8), dp(8), 0);
        ob.addView(skip, slp);

        root.addView(ob, new FrameLayout.LayoutParams(-1, -1));
        confetti.bringToFront();

        final Runnable finish = () -> {
            prefs.edit().putBoolean("onboarded", true).apply();
            prefs.edit().putInt("seenVersion", Updater.currentCode(this)).apply();
            if (reduceMotion) {
                root.removeView(ob);
            } else {
                ob.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(380)
                        .withEndAction(() -> root.removeView(ob)).start();
            }
            runEntrance();
        };
        final java.util.function.IntConsumer[] go = new java.util.function.IntConsumer[1];
        go[0] = target -> {
            int from = obIndex;
            obIndex = target;
            next.setText(target == 2 ? "🔌  Подключить доступ" : "Далее  →");
            for (int i = 0; i < 3; i++) {
                final View d = dots.getChildAt(i);
                final int to = dp(i == target ? 26 : 8);
                d.setAlpha(i == target ? 1f : 0.35f);
                if (reduceMotion) {
                    d.getLayoutParams().width = to;
                    d.requestLayout();
                } else {
                    ValueAnimator va = ValueAnimator.ofInt(d.getLayoutParams().width, to);
                    va.setDuration(300);
                    va.addUpdateListener(a -> {
                        d.getLayoutParams().width = (int) a.getAnimatedValue();
                        d.requestLayout();
                    });
                    va.start();
                }
            }
            View in = slides[target];
            in.setVisibility(View.VISIBLE);
            if (from == target || reduceMotion) {
                for (int i = 0; i < 3; i++) slides[i].setVisibility(i == target ? View.VISIBLE : View.INVISIBLE);
                in.setAlpha(1f);
                in.setTranslationX(0);
                if (target == 0) ui.postDelayed(() -> burstFromCenter(in), 500);
                return;
            }
            float dir = target > from ? 1 : -1;
            final View out = slides[from];
            out.animate().translationX(-dir * stage.getWidth() * 0.6f).alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(360).withEndAction(() -> out.setVisibility(View.INVISIBLE)).start();
            in.setTranslationX(dir * stage.getWidth() * 0.6f);
            in.setAlpha(0f);
            in.setScaleX(0.92f);
            in.setScaleY(0.92f);
            in.animate().translationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            popChildren(in);
        };
        next.setOnClickListener(v -> {
            if (obIndex < 2) {
                go[0].accept(obIndex + 1);
            } else {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    Toast.makeText(this, "Найдите «Прогрев TikTok» и включите", Toast.LENGTH_LONG).show();
                } catch (RuntimeException ignored) { }
                finish.run();
            }
        });
        skip.setOnClickListener(v -> finish.run());
        final float[] down = new float[2];
        stage.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getX();
                    down[1] = e.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = e.getX() - down[0], dy = e.getY() - down[1];
                    if (Math.abs(dx) > v.getWidth() * 0.15f && Math.abs(dx) > Math.abs(dy)) {
                        if (dx < 0 && obIndex < 2) go[0].accept(obIndex + 1);
                        if (dx > 0 && obIndex > 0) go[0].accept(obIndex - 1);
                    }
                    return true;
            }
            return true;
        });

        obIndex = 0;
        go[0].accept(0);
    }

    private View obSlide(int i) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        View art;
        String title, body;
        if (i == 0) {
            FrameLayout logo = new FrameLayout(this);
            logo.setClipChildren(false);
            Anim.Pulse ring = new Anim.Pulse(this, th.a1, dp(36));
            ring.setActive(true);
            logo.addView(ring, new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER));
            TextView heart = text("❤", 72, Color.WHITE, false);
            heart.setGravity(Gravity.CENTER);
            GradientDrawable hb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{th.a1, th.a3});
            hb.setShape(GradientDrawable.OVAL);
            heart.setBackground(hb);
            heart.setElevation(dp(14));
            glow(heart, th.a1);
            logo.addView(heart, new FrameLayout.LayoutParams(dp(148), dp(148), Gravity.CENTER));
            heartbeat(heart);
            art = logo;
            title = "Прогрев TikTok";
            body = "Аккаунт прогревается сам — так, будто ленту листает живой человек. "
                    + "Алгоритм быстрее понимает вас и чаще показывает ваши ролики.";
        } else if (i == 1) {
            art = new Anim.FeedPhone(this, th);
            title = "Как живой человек";
            body = "Случайное время просмотра, ❤ через 1–18 роликов, избранное, фото-посты. "
                    + "Эфиры и реклама — мимо. Можно по нише и по расписанию.";
        } else {
            art = new Anim.ToggleShow(this, th);
            title = "Один шаг — и готово";
            body = "Включите «Прогрев TikTok» в Спецвозможностях: так приложение сможет листать ленту. "
                    + "Оно только свайпает и лайкает в TikTok — ничего не читает и никуда не отправляет.";
        }
        s.addView(art, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView t = text(title, 28, th.text, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(18), 0, dp(8));
        s.addView(t);
        TextView b = text(body, 15, th.hint, false);
        b.setGravity(Gravity.CENTER);
        s.addView(b);
        if (i == 1) {
            LinearLayout chips = new LinearLayout(this);
            chips.setGravity(Gravity.CENTER);
            chips.setPadding(0, dp(14), 0, 0);
            for (String c : new String[]{"🎯 Ниша", "⏰ Расписание", "📊 Отчёты"}) {
                TextView ch = text(c, 12, th.light ? th.a1 : Color.WHITE, true);
                ch.setSingleLine(true);
                ch.setPadding(dp(10), dp(5), dp(10), dp(5));
                GradientDrawable g = new GradientDrawable();
                g.setColor(Theme.alpha(th.a1, th.light ? 0x1F : 0x40));
                g.setStroke(dp(1), Theme.alpha(th.a1, 0x66));
                g.setCornerRadius(dp(99));
                ch.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMargins(dp(3), 0, dp(3), 0);
                chips.addView(ch, lp);
            }
            s.addView(chips);
        }
        return s;
    }

    /** Заголовок, текст и чипсы слайда появляются по очереди. */
    private void popChildren(View slide) {
        if (reduceMotion || !(slide instanceof LinearLayout)) return;
        LinearLayout l = (LinearLayout) slide;
        for (int i = 1; i < l.getChildCount(); i++) {
            View c = l.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dp(16));
            c.animate().alpha(1f).translationY(0).setStartDelay(120L * i).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        if (l.getChildCount() > 3 && l.getChildAt(3) instanceof LinearLayout) {
            LinearLayout chips = (LinearLayout) l.getChildAt(3);
            for (int i = 0; i < chips.getChildCount(); i++) {
                View ch = chips.getChildAt(i);
                ch.setScaleX(0f);
                ch.setScaleY(0f);
                ch.animate().scaleX(1f).scaleY(1f).setStartDelay(450 + 110L * i).setDuration(420)
                        .setInterpolator(new OvershootInterpolator(2.5f)).start();
            }
        }
    }

    private void burstFromCenter(View v) {
        if (reduceMotion || v.getWidth() == 0) return;
        int[] loc = new int[2], rl = new int[2];
        v.getLocationInWindow(loc);
        root.getLocationInWindow(rl);
        confetti.burst(loc[0] - rl[0] + v.getWidth() / 2f, loc[1] - rl[1] + v.getHeight() * 0.35f, 70,
                new int[]{th.a1, th.a2, 0xFFFFB648, 0xFFFFFFFF});
    }

    // ---------- запуск ----------

    private void startWarmup() {
        if (WarmupService.instance == null) {
            shake(connectBox);
            Toast.makeText(this, "Сначала нажмите «Подключить»", Toast.LENGTH_LONG).show();
            return;
        }
        WarmupService.Config c = Cfg.build(this, false);
        if (c == null) {
            shake(premiumCard);
            Toast.makeText(this, "Бесплатный лимит на сегодня исчерпан. В Premium — без лимита 👑",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent launch = null;
        for (String pkg : WarmupService.TIKTOK) {
            launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) break;
        }
        if (launch == null) {
            Toast.makeText(this, "TikTok не установлен", Toast.LENGTH_LONG).show();
            return;
        }
        last = new int[]{-1, -1, -1, -1, -1, -1, -1};
        WarmupService.instance.start(c);
        refresh();
        startActivity(launch);
    }

    // ---------- профили аккаунтов ----------

    private View buildProfiles() {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        java.util.List<String> names = Cfg.profiles(this);
        int cur = Cfg.currentProfile(this);
        for (int i = 0; i < names.size(); i++) {
            final int idx = i;
            TextView chip = text((i == cur ? "👤 " : "") + names.get(i), 14, i == cur ? Color.WHITE : th.text, true);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            GradientDrawable d = i == cur
                    ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{th.a1, th.a2})
                    : new GradientDrawable();
            if (i != cur) {
                d.setColor(th.card);
                d.setStroke(dp(1), th.line);
            }
            d.setCornerRadius(dp(99));
            chip.setBackground(ripple(d));
            pressable(chip);
            chip.setOnClickListener(v -> {
                if (idx == Cfg.currentProfile(this)) {
                    Toast.makeText(this, "Удерживайте, чтобы удалить профиль", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (WarmupService.isRunning()) WarmupService.instance.stop();
                Cfg.switchProfile(this, idx);
                Toast.makeText(this, "Профиль «" + names.get(idx) + "»", Toast.LENGTH_SHORT).show();
                fadeRecreate();
            });
            chip.setOnLongClickListener(v -> {
                if (names.size() <= 1) {
                    Toast.makeText(this, "Последний профиль удалить нельзя", Toast.LENGTH_SHORT).show();
                    return true;
                }
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Удалить профиль «" + names.get(idx) + "»?")
                        .setPositiveButton("Удалить", (dlg, w) -> {
                            Cfg.deleteProfile(this, idx);
                            fadeRecreate();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
        TextView add = text("＋ Аккаунт", 14, th.light ? th.a1 : Color.WHITE, true);
        add.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable ad = new GradientDrawable();
        ad.setCornerRadius(dp(99));
        ad.setStroke(dp(1), Theme.alpha(th.a1, 0x88));
        add.setBackground(ripple(ad));
        pressable(add);
        add.setOnClickListener(v -> {
            final EditText name = new EditText(this);
            name.setHint("Например: Магазин");
            name.setSingleLine(true);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Новый профиль аккаунта")
                    .setMessage("Свои настройки для каждого аккаунта TikTok. Новый профиль начнётся с копии текущих настроек.")
                    .setView(name)
                    .setPositiveButton("Создать", (dlg, w) -> {
                        String n = name.getText().toString().trim();
                        Cfg.addProfile(this, n.isEmpty() ? "Аккаунт " + (names.size() + 1) : n);
                        fadeRecreate();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
        row.addView(add);
        hs.addView(row);
        entrance.add(hs);
        return withMargin(hs, dp(12));
    }

    private void fadeRecreate() {
        frame.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            recreate();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }).start();
    }

    // ---------- батарея и автозапуск ----------

    private View buildBatteryCard() {
        LinearLayout c = card();
        c.addView(cardTitle("🔋", "Чтобы Android не выключал прогрев"));
        batteryStatus = text("", 14, th.text, false);
        c.addView(batteryStatus);
        c.addView(note("Xiaomi, Huawei, Samsung и другие сами останавливают фоновые приложения. "
                + "Нажмите обе кнопки и разрешите работу без ограничений."));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        TextView bat = smallButton("🔋 Батарея");
        bat.setOnClickListener(v -> openBatterySettings());
        row.addView(bat);
        TextView auto = smallButton("🚀 Автозапуск");
        auto.setOnClickListener(v -> openAutostartSettings());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        row.addView(auto, lp);
        c.addView(row);
        return c;
    }

    private boolean batteryOk() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private int lastSystemStatus = -1;

    /** Обновляет строки «Батарея» и «Точное время» только если что-то изменилось. */
    private void updateSystemStatus() {
        int stateBits = (batteryOk() ? 1 : 0) | (Schedule.exactAllowed(this) ? 2 : 0);
        if (stateBits == lastSystemStatus) return;
        lastSystemStatus = stateBits;
        if (batteryStatus != null) {
            boolean ok = batteryOk();
            batteryStatus.setText(ok ? "✅ Батарея: без ограничений" : "⚠️ Батарея: Android может остановить прогрев");
            batteryStatus.setTextColor(ok ? th.good : th.text);
        }
        if (exactStatus != null) {
            boolean ok = Schedule.exactAllowed(this);
            exactStatus.setText(ok ? "✅ Точное время запуска разрешено" : "⚠️ Без разрешения запуск может опоздать на 10–15 мин");
            exactButton.setVisibility(ok ? View.GONE : View.VISIBLE);
        }
    }

    private void openBatterySettings() {
        try {
            if (!batteryOk()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName())));
                return;
            }
        } catch (RuntimeException ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (RuntimeException e) {
            openAppDetails();
        }
    }

    /** Экран «Автозапуск» у разных производителей; если такого нет — настройки приложения. */
    private void openAutostartSettings() {
        String[][] targets = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
                {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
        };
        for (String[] t : targets) {
            try {
                startActivity(new Intent().setComponent(new android.content.ComponentName(t[0], t[1]))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Toast.makeText(this, "Найдите «Прогрев TikTok» и разрешите автозапуск", Toast.LENGTH_LONG).show();
                return;
            } catch (RuntimeException ignored) { }
        }
        openAppDetails();
        Toast.makeText(this, "Батарея → Без ограничений; и разрешите работу в фоне", Toast.LENGTH_LONG).show();
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException ignored) { }
    }

    // ---------- расписание ----------

    private View buildScheduleCard() {
        LinearLayout c = card();
        c.addView(cardTitle("⏰", "Запуск по расписанию"));
        c.addView(toggleRow("Запускать прогрев сам 2 раза в день",
                "В нужное время экран включится, откроется TikTok и пойдёт сессия",
                Schedule.enabled(this), on -> {
                    prefs.edit().putBoolean("schedOn", on).apply();
                    Schedule.apply(this);
                    if (on && WarmupService.instance == null) {
                        Toast.makeText(this, "Не забудьте «Подключить» доступ — без него расписание не сработает",
                                Toast.LENGTH_LONG).show();
                    }
                }, false).row);
        int[] t = Schedule.times(this);
        c.addView(timeRow("🌅 Утром", "sched1", t[0]));
        c.addView(timeRow("🌙 Вечером", "sched2", t[1]));
        stepper(c, "Длительность сессии", "sessionMinutes", 40, 10, 120, 5, " мин", false);
        exactStatus = text("", 13, th.hint, false);
        exactStatus.setPadding(0, dp(6), 0, 0);
        c.addView(exactStatus);
        TextView exact = smallButton("Разрешить точное время");
        exact.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:" + getPackageName())));
                } catch (RuntimeException e) {
                    openAppDetails();
                }
            }
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, -2);
        elp.topMargin = dp(8);
        c.addView(exact, elp);
        exactButton = exact;
        c.addView(note("Важно: телефон на зарядке, а блокировка экрана — без пароля (только свайп). "
                + "С паролем или отпечатком Android не даст начать без вас."));
        return c;
    }

    private View timeRow(String label, final String key, int minutes) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(text(label, 15, th.text, false), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = smallButton(Schedule.fmt(minutes));
        val.setOnClickListener(v -> {
            int m = prefs.getInt(key, minutes);
            new android.app.TimePickerDialog(this, (tp, h, mi) -> {
                prefs.edit().putInt(key, h * 60 + mi).apply();
                val.setText(Schedule.fmt(h * 60 + mi));
                pop(val);
                Schedule.apply(this);
            }, m / 60, m % 60, true).show();
        });
        row.addView(val);
        return row;
    }

    // ---------- ниша и новый аккаунт ----------

    private View buildNicheCard() {
        LinearLayout c = card();
        c.addView(cardTitle("🎯", "Прогрев по нише"));
        c.addView(note("Впишите темы аккаунта через запятую. Ролики с этими словами и хэштегами смотрятся дольше "
                + "и лайкаются чаще, остальные — пролистываются быстрее. Так алгоритм быстрее поймёт аккаунт."));
        editor(c, "Темы ниши (через запятую)", "niche", "", false, 16, false).setHint("авто, мемы, фитнес");
        c.addView(toggleRow("🔎 Иногда искать по нише", "Раз в 25–40 роликов открывает поиск, смотрит 2–4 ролика и возвращается (β)",
                Cfg.b(prefs, "nicheSearch"), on -> prefs.edit().putBoolean("nicheSearch", on).apply(), false).row);
        stepper(c, "➕ Подписка на автора", "followChance", 1, 0, 20, 1, "%", false);
        stepper(c, "Подписок в день: до", "followDaily", 10, 0, 50, 1, "", false);
        int day = Cfg.newAccDay(this);
        c.addView(toggleRow("🐣 Новый аккаунт",
                day >= 0 ? (day < 7 ? "День " + (day + 1) + " из 7: активность растёт постепенно" : "Прогрев новичка завершён — обычный режим")
                        : "Первые 3 дня — очень мягко, дни 4–7 — вполсилы, потом обычный режим",
                day >= 0, on -> {
                    SharedPreferences.Editor ed = prefs.edit().putBoolean("newAcc", on);
                    if (on) ed.putLong("newAccStart", System.currentTimeMillis());
                    ed.apply();
                    Toast.makeText(this, on ? "🐣 День 1 из 7 — начинаем мягко" : "Обычный режим", Toast.LENGTH_SHORT).show();
                }, false).row);
        return c;
    }

    // ---------- история ----------

    private View buildHistoryCard() {
        LinearLayout c = card();
        c.addView(cardTitle("📊", "История прогрева"));
        historyChart = new BarChart(this);
        c.addView(historyChart, new LinearLayout.LayoutParams(-1, dp(130)));
        historyText = text("", 13, th.text, false);
        historyText.setPadding(0, dp(8), 0, 0);
        c.addView(historyText);
        TextView send = smallButton("📤 Отчёт за сегодня в Telegram");
        send.setOnClickListener(v -> {
            int[] t = todayTotals();
            if (t[0] == 0) {
                Toast.makeText(this, "Сегодня ещё не было прогрева", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        History.reportLink(t[0], t[1], t[2], t[3], t[4], t[5], t[6]))));
            } catch (RuntimeException e) {
                Toast.makeText(this, "Telegram не установлен", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        c.addView(send, lp);
        updateHistory();
        return c;
    }

    /** {роликов, лайков, избранное, комменты, репосты, подписки, секунд} за сегодня. */
    private int[] todayTotals() {
        int[] t = new int[7];
        java.util.Calendar now = java.util.Calendar.getInstance();
        for (History.Session s : History.all(this)) {
            java.util.Calendar d = java.util.Calendar.getInstance();
            d.setTimeInMillis(s.time);
            if (History.daysBetween(d, now) != 0) continue;
            t[0] += s.posts; t[1] += s.likes; t[2] += s.favorites; t[3] += s.comments;
            t[4] += s.reposts; t[5] += s.follows; t[6] += (int) s.seconds;
        }
        return t;
    }

    private void updateHistory() {
        if (historyChart == null) return;
        int[][] d = History.byDay(this, 7);
        historyChart.set(d[0], d[1]);
        java.util.List<History.Session> all = History.all(this);
        int[] t = todayTotals();
        int week = 0, weekLikes = 0;
        for (int v : d[0]) week += v;
        for (int v : d[1]) weekLikes += v;
        StringBuilder sb = new StringBuilder();
        sb.append("Сегодня: 🎬 ").append(t[0]).append("  ❤ ").append(t[1]).append("  ⏱ ")
                .append(t[6] / 60).append(" мин\n")
                .append("За 7 дней: 🎬 ").append(week).append("  ❤ ").append(weekLikes)
                .append("  · сессий всего: ").append(all.size());
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.ROOT);
        for (int i = all.size() - 1; i >= Math.max(0, all.size() - 4); i--) {
            History.Session s = all.get(i);
            sb.append("\n").append(f.format(new java.util.Date(s.time))).append(" — 🎬 ").append(s.posts)
                    .append(" ❤ ").append(s.likes).append(" · ").append(Math.max(1, s.seconds / 60)).append(" мин");
        }
        historyText.setText(sb);
    }

    /** Столбики по дням: ролики (полупрозрачные) и лайки (яркие). */
    private class BarChart extends View {
        private int[] posts = new int[7], likes = new int[7];
        private final android.graphics.Paint pBar = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint pText = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private float grow = 1f;

        BarChart(android.content.Context c) {
            super(c);
            pText.setColor(th.hint);
            pText.setTextSize(dp(11));
            pText.setTextAlign(android.graphics.Paint.Align.CENTER);
        }

        void set(int[] p, int[] l) {
            posts = p;
            likes = l;
            if (reduceMotion) { grow = 1f; invalidate(); return; }
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(700);
            va.setInterpolator(new DecelerateInterpolator(2f));
            va.addUpdateListener(a -> { grow = (float) a.getAnimatedValue(); invalidate(); });
            va.start();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            int n = posts.length;
            float w = getWidth(), h = getHeight() - dp(18);
            int max = 1;
            for (int v : posts) max = Math.max(max, v);
            float slot = w / n, bw = slot * 0.5f;
            String[] days = {"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (int i = 0; i < n; i++) {
                float cx = slot * i + slot / 2;
                float hp = h * posts[i] / max * grow, hl = h * likes[i] / max * grow;
                pBar.setColor(Theme.alpha(th.a1, 0x55));
                r.set(cx - bw / 2, h - hp, cx + bw / 2, h);
                c.drawRoundRect(r, dp(6), dp(6), pBar);
                pBar.setColor(th.a2);
                r.set(cx - bw / 2, h - hl, cx + bw / 2, h);
                c.drawRoundRect(r, dp(6), dp(6), pBar);
                java.util.Calendar d = (java.util.Calendar) cal.clone();
                d.add(java.util.Calendar.DAY_OF_YEAR, i - (n - 1));
                c.drawText(i == n - 1 ? "сег." : days[d.get(java.util.Calendar.DAY_OF_WEEK) - 1], cx,
                        getHeight() - dp(3), pText);
            }
        }
    }

    // ---------- конструкторы UI ----------

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable d = new GradientDrawable();
        d.setColor(th.card);
        d.setCornerRadius(dp(22));
        d.setStroke(dp(1), th.line);
        c.setBackground(d);
        if (th.light) c.setElevation(dp(2));
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        c.setLayoutTransition(lt);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        tilt(c);
        entrance.add(c);
        return c;
    }

    private View cardTitle(String icon, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        TextView i = text(icon, 15, Color.WHITE, false);
        i.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{th.a1, th.a3});
        d.setCornerRadius(dp(10));
        i.setBackground(d);
        row.addView(i, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView t = text(title, 17, th.text, true);
        t.setPadding(dp(12), 0, 0, 0);
        row.addView(t);
        return row;
    }

    private Stepper stepper(LinearLayout parent, String label, String key, int def, int min, int max,
                            int step, String unit, boolean infiniteZero) {
        return stepper(parent, label, key, def, min, max, step, unit, infiniteZero, false);
    }

    private Stepper stepper(LinearLayout parent, String label, String key, int def, int min, int max,
                            int step, String unit, boolean infiniteZero, boolean pro) {
        final Stepper s = new Stepper(key, def, min, max, step, unit, infiniteZero);
        s.pro = pro;
        if (pro && !premium) label = "🔒 " + label;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        row.addView(text(label, 15, th.text, false), new LinearLayout.LayoutParams(0, -2, 1f));

        TextView minus = roundButton("−");
        minus.setOnClickListener(v -> s.change(-1));
        repeatOnHold(minus, () -> s.change(-1));
        row.addView(minus, new LinearLayout.LayoutParams(dp(36), dp(36)));

        s.valueView = text(s.text(), 16, th.text, true);
        s.valueView.setGravity(Gravity.CENTER);
        row.addView(s.valueView, new LinearLayout.LayoutParams(dp(68), -2));

        TextView plus = roundButton("+");
        plus.setOnClickListener(v -> s.change(+1));
        repeatOnHold(plus, () -> s.change(+1));
        row.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));

        parent.addView(row);
        return s;
    }

    private interface OnToggle { void on(boolean value); }

    private static class Toggle {
        View row;
        OnToggle set;
    }

    /** Строка с анимированным переключателем. pro — только в Premium. */
    private Toggle toggleRow(String label, String hint, boolean initial, OnToggle cb, boolean pro) {
        if (pro && !premium) label = "🔒 " + label;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 15, th.text, false));
        TextView h = text(hint, 12, th.hint, false);
        texts.addView(h);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        final FrameLayout track = new FrameLayout(this);
        final GradientDrawable trackBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                initial ? new int[]{th.a1, th.a2} : new int[]{th.soft, th.soft});
        trackBg.setCornerRadius(dp(99));
        trackBg.setStroke(dp(1), th.line);
        track.setBackground(trackBg);
        final View thumb = new View(this);
        GradientDrawable tb = new GradientDrawable();
        tb.setShape(GradientDrawable.OVAL);
        tb.setColor(Color.WHITE);
        thumb.setBackground(tb);
        thumb.setElevation(dp(2));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER_VERTICAL);
        tlp.leftMargin = dp(3);
        track.addView(thumb, tlp);
        thumb.setTranslationX(initial ? dp(22) : 0);
        final boolean[] state = {initial};
        final Toggle t = new Toggle();
        t.row = row;
        t.set = on -> {
            if (state[0] == on) return;
            state[0] = on;
            thumb.animate().translationX(on ? dp(22) : 0).setDuration(220)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
            animateColors(trackBg, on ? new int[]{th.a1, th.a2} : new int[]{th.soft, th.soft});
            cb.on(on);
        };
        track.setOnClickListener(v -> {
            if (pro && !premium) {
                locked(track);
                return;
            }
            t.set.on(!state[0]);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(30));
        lp.leftMargin = dp(12);
        row.addView(track, lp);
        return t;
    }

    /** Поле для текста, сохраняется в настройках по ключу. */
    private EditText editor(LinearLayout parent, String title, final String key, String def,
                            boolean multiline, float size, boolean pro) {
        TextView l = text((pro && !premium ? "🔒 " : "") + title, 13, th.hint, false);
        l.setPadding(0, dp(10), 0, dp(6));
        parent.addView(l);
        EditText e = new EditText(this);
        e.setText(prefs.getString(key, def));
        e.setTextSize(size);
        e.setTextColor(th.text);
        e.setHintTextColor(th.hint);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(3);
            e.setMaxLines(7);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setVerticalScrollBarEnabled(true);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        final GradientDrawable eb = new GradientDrawable();
        eb.setColor(th.soft);
        eb.setCornerRadius(dp(14));
        eb.setStroke(dp(1), th.line);
        e.setBackground(eb);
        e.setOnFocusChangeListener((v, has) -> eb.setStroke(dp(has ? 2 : 1), has ? th.a1 : th.line));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence x, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable x) {
                prefs.edit().putString(key, x.toString()).apply();
            }
        });
        if (pro && !premium) {
            e.setFocusable(false);
            e.setAlpha(0.55f);
            e.setOnClickListener(v -> locked(v));
        }
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    /** Функция только в Premium: потрясти, подсказать, подсветить карточку Premium. */
    private void locked(View v) {
        shake(v);
        if (premiumCard != null) pop(premiumCard);
        Toast.makeText(this, "🔒 Доступно в Premium", Toast.LENGTH_SHORT).show();
    }

    private TextView roundButton(String s) {
        TextView b = text(s, 20, th.text, true);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(th.soft);
        d.setStroke(dp(1), th.line);
        b.setBackground(ripple(d));
        return b;
    }

    private TextView smallButton(String s) {
        TextView b = text(s, 14, th.light ? th.a1 : Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Theme.alpha(th.a1, th.light ? 0x1F : 0x55), Theme.alpha(th.a3, th.light ? 0x14 : 0x55)});
        d.setCornerRadius(dp(99));
        d.setStroke(dp(1), Theme.alpha(th.a1, 0x55));
        b.setBackground(ripple(d));
        pressable(b);
        return b;
    }

    private TextView note(String s) {
        TextView t = text(s, 13, th.hint, false);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(th.light ? 0x22000000 : 0x40FFFFFF), content, null);
    }

    private View withMargin(View v, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = bottom;
        v.setLayoutParams(lp);
        return v;
    }

    /** Цветная тень (Android 9+). */
    private void glow(View v, int color) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineSpotShadowColor(color);
            v.setOutlineAmbientShadowColor(color);
        }
    }

    // ---------- анимации ----------

    private ValueAnimator loop(long ms, boolean reverse) {
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(ms);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.setRepeatMode(reverse ? ValueAnimator.REVERSE : ValueAnimator.RESTART);
        va.setInterpolator(new LinearInterpolator());
        loops.add(va);
        return va;
    }

    /** Карточки по очереди «всплывают» снизу при открытии. */
    private void runEntrance() {
        if (reduceMotion) return;
        for (int i = 0; i < entrance.size(); i++) {
            View v = entrance.get(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(36));
            v.setScaleX(0.97f);
            v.setScaleY(0.97f);
            v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                    .setStartDelay(70L * i).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
        // страховка: что бы ни случилось с анимацией — через секунду всё видно и на месте
        ui.postDelayed(() -> {
            for (View v : entrance) {
                if (v.getAlpha() < 1f || v.getTranslationY() != 0f) {
                    v.setAlpha(1f);
                    v.setTranslationY(0f);
                    v.setScaleX(1f);
                    v.setScaleY(1f);
                }
            }
        }, 70L * entrance.size() + 1000);
    }

    /** Сердце в шапке «бьётся»: два коротких удара и пауза. */
    private void heartbeat(View v) {
        if (reduceMotion) return;
        ValueAnimator va = loop(1400, false);
        va.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float s = 1f + 0.16f * bump(t, 0.08f) + 0.10f * bump(t, 0.30f);
            v.setScaleX(s);
            v.setScaleY(s);
        });
        va.start();
    }

    /** Кнопка медленно «дышит». */
    private void breathe(View v) {
        if (reduceMotion) return;
        ValueAnimator va = loop(1600, true);
        va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        va.addUpdateListener(a -> {
            if (v.isPressed()) return;
            float s = 1f + 0.025f * (float) a.getAnimatedValue();
            v.setScaleX(s);
            v.setScaleY(s);
        });
        va.start();
    }

    private static float bump(float t, float center) {
        float d = Math.abs(t - center) / 0.09f;
        return d >= 1f ? 0f : (float) (0.5 + 0.5 * Math.cos(Math.PI * d));
    }

    /**
     * «Подпрыгивание» — меняет только масштаб отдельным аниматором и не отменяет
     * другие анимации (раньше отменяло появление кнопки, и она оставалась невидимой).
     */
    private void pop(View v) {
        if (reduceMotion || v == null || v.getAlpha() < 0.99f) return;
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.14f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.14f, 1f));
        a.setDuration(380);
        a.setInterpolator(new OvershootInterpolator(3f));
        a.start();
    }

    private void shake(View v) {
        if (reduceMotion || v == null) return;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(420);
        va.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            v.setTranslationX((float) (Math.sin(t * Math.PI * 6) * dp(8) * (1 - t)));
        });
        va.start();
    }

    private void animateColors(final GradientDrawable d, final int[] to) {
        final int[] from = d.getColors() != null ? d.getColors() : to;
        if (reduceMotion || from.length != to.length) {
            d.setColors(to);
            return;
        }
        final ArgbEvaluator ev = new ArgbEvaluator();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(300);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            int[] c = new int[to.length];
            for (int i = 0; i < to.length; i++) c[i] = (int) ev.evaluate(f, from[i], to[i]);
            d.setColors(c);
        });
        va.start();
    }

    /** Нажатие слегка «утапливает» кнопку. */
    private void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(110).setStartDelay(0).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start();
                    break;
            }
            return false;
        });
    }

    /** Удержание «+»/«−» — быстрая прокрутка значения. */
    private void repeatOnHold(View v, Runnable action) {
        final Runnable[] rep = new Runnable[1];
        rep[0] = () -> {
            action.run();
            ui.postDelayed(rep[0], 90);
        };
        v.setOnLongClickListener(view -> {
            ui.post(rep[0]);
            return true;
        });
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(110).setStartDelay(0).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(rep[0]);
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start();
                    break;
            }
            return false;
        });
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
