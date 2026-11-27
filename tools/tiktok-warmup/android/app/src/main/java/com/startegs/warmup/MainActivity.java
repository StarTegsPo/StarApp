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

    private FrameLayout frame;
    private TextView heroStatus, chipVideos, chipLikes, chipFav, chipRepost, chipPhotos, chipTime, chipComments, chipSkipped;
    private ShimmerButton mainButton;
    private LinearLayout connectBox, connectedBox;
    private TextView parallelHow;
    private boolean photosOn, skipLiveOn, skipAdsOn, capLoopsOn, premium;
    private View premiumCard;
    private TextView freeLeftText;
    private View freeFill;
    private Toggle tPhotos, tLive, tAds;
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
        setContentView(frame);

        col.addView(buildThemeSwitch());
        col.addView(buildHero());
        col.addView(buildMainButton());
        col.addView(buildPremiumCard());
        col.addView(buildConnectCard());
        col.addView(buildBestCard());
        col.addView(buildLikesCard());
        col.addView(buildActionsCard());
        col.addView(buildSkipCard());
        col.addView(buildCommentsCard());
        col.addView(buildWatchCard());
        col.addView(buildParallelCard());
        col.addView(buildFooter());

        runEntrance();
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
        if (!uiState.equals(lastUiState)) {
            lastUiState = uiState;
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
        chip.setText(prefix + v + suffix);
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
        indicator.setElevation(dp(3));
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
            t.setElevation(dp(4));
            t.setOnClickListener(v -> {
                if (idx == cur) return;
                indicator.animate().translationX(row.getWidth() / 3f * idx).setDuration(260)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                Theme.save(this, Theme.IDS[idx]);
                frame.animate().alpha(0f).setStartDelay(160).setDuration(220).withEndAction(() -> {
                    recreate();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
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
        entrance.add(mainButton);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
        lp.bottomMargin = dp(14);
        mainButton.setLayoutParams(lp);
        return mainButton;
    }

    // ---------- Premium ----------

    private View buildPremiumCard() {
        if (premium) {
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
        TextView feats = text("✓ Без дневного лимита постов\n"
                + "✓ Авто-пропуск рекламы\n"
                + "✓ Комментарии: эмодзи и фразы\n"
                + "✓ Репосты с текстом", 14, th.text, false);
        feats.setPadding(0, dp(4), 0, dp(10));
        c.addView(feats);

        freeLeftText = text("", 13, th.hint, false);
        c.addView(freeLeftText);
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
        c.addView(track, tlp);

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
            if (Premium.activate(this, code.getText().toString())) {
                Toast.makeText(this, "👑 Premium активирован!", Toast.LENGTH_LONG).show();
                frame.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    recreate();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
            } else {
                shake(code);
                Toast.makeText(this, "Неверный код", Toast.LENGTH_SHORT).show();
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
        return c;
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

    // ---------- запуск ----------

    private void startWarmup() {
        if (WarmupService.instance == null) {
            shake(connectBox);
            Toast.makeText(this, "Сначала нажмите «Подключить»", Toast.LENGTH_LONG).show();
            return;
        }
        WarmupService.Config c = new WarmupService.Config();
        c.videos = sVideos.value;
        c.likeMin = Math.min(sLikeMin.value, sLikeMax.value);
        c.likeMax = Math.max(sLikeMin.value, sLikeMax.value);
        c.watchMin = Math.min(sWatchMin.value, sWatchMax.value);
        c.watchMax = Math.max(sWatchMin.value, sWatchMax.value);
        c.skipChance = sSkip.value;
        c.longChance = Math.min(sLong.value, 100 - sSkip.value);
        c.favChance = sFav.value;
        c.repostChance = sRepost.value;
        c.photos = photosOn;
        c.capLoops = capLoopsOn;
        c.skipLive = skipLiveOn;
        c.skipAds = skipAdsOn;
        c.commentChance = sComment.value;
        String[] em = prefs.getString("emoji", WarmupService.DEFAULT_EMOJI).trim().split("\\s+");
        c.emoji = em.length == 1 && em[0].isEmpty() ? new String[0] : em;
        c.phrases = WarmupService.lines(prefs.getString("phrases", WarmupService.DEFAULT_PHRASES));
        c.repostTexts = WarmupService.lines(prefs.getString("repostTexts", WarmupService.DEFAULT_REPOST));
        c.phrasePercent = sPhrase.value;
        if (!premium) {
            int left = Premium.leftToday(this);
            if (left <= 0) {
                shake(premiumCard);
                Toast.makeText(this, "Бесплатный лимит на сегодня исчерпан. В Premium — без лимита 👑",
                        Toast.LENGTH_LONG).show();
                return;
            }
            c.dailyLimit = Premium.FREE_DAILY;
            c.skipAds = false;
            c.commentChance = 0;
            c.repostChance = 0;
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
