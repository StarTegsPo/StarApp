package com.startegs.warmup;

import android.animation.ArgbEvaluator;
import android.animation.LayoutTransition;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // ---------- палитра (как в StarApp: «Фиолет») ----------
    private static final int BG = FxView.BG;
    private static final int CARD = 0xB31B1D26;
    private static final int LINE = 0x1FFFFFFF;
    private static final int TEXT = 0xFFF3F4F8;
    private static final int HINT = 0xFF8D93A6;
    private static final int A1 = FxView.A1, A2 = FxView.A2, A3 = FxView.A3;
    private static final int GOOD = 0xFF34D399;
    private static final int BAD = 0xFFFF6B6B;

    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<View> entrance = new ArrayList<>();
    private final List<Stepper> steppers = new ArrayList<>();
    private boolean reduceMotion;

    // элементы, которые обновляются
    private TextView heroStatus, chipVideos, chipLikes, chipTime;
    private TextView mainButton;
    private GradientDrawable mainButtonBg;
    private LinearLayout accessCard;
    private TextView accessDot, accessText, accessButton;
    private TextView parallelHow;
    private int lastDone = -1, lastLikes = -1;
    private String lastUiState = "";

    // ---------- шаги настроек ----------
    private class Stepper {
        final String key;
        final int min, max, step;
        int value;
        final boolean infiniteZero;
        final String unit;
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
            if (infiniteZero && value == 0) return "∞";
            return value + unit;
        }

        void change(int dir) {
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
    }

    private Stepper sVideos, sLikeMin, sLikeMax, sWatchMin, sWatchMax, sSkip, sLong;

    // =====================================================================

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        reduceMotion = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(new FxView(this), new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(20), dp(16), dp(32));
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        col.setLayoutTransition(lt);
        scroll.addView(col);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        col.addView(buildHero());
        col.addView(buildMainButton());
        col.addView(buildAccessCard());
        col.addView(buildLikesCard());
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

        // доступ
        accessCard.setVisibility(enabled ? View.GONE : View.VISIBLE);

        // статус в шапке
        String status;
        switch (st) {
            case "off": status = "Нужен доступ к спецвозможностям"; break;
            case WarmupService.RUNNING: status = "Идёт прогрев"; break;
            case WarmupService.PAUSED: status = "Пауза — TikTok не на экране"; break;
            case WarmupService.BREAK: status = "Перерыв, как у живого человека"; break;
            default: status = "Готов к запуску";
        }
        if (!status.equals(heroStatus.getText().toString())) {
            heroStatus.setAlpha(0f);
            heroStatus.setText(status);
            heroStatus.animate().alpha(1f).setDuration(250).start();
        }

        // счётчики
        int done = running ? s.done() : (lastDone < 0 ? 0 : lastDone);
        int likes = running ? s.likes() : (lastLikes < 0 ? 0 : lastLikes);
        if (done != lastDone) {
            int total = running ? s.videos() : sVideos.value;
            chipVideos.setText("🎬 " + done + (total > 0 ? " / " + total : ""));
            if (lastDone >= 0) pop(chipVideos);
            lastDone = done;
        }
        if (likes != lastLikes) {
            chipLikes.setText("❤ " + likes);
            if (lastLikes >= 0) pop(chipLikes);
            lastLikes = likes;
        }
        long sec = running ? s.elapsedMs() / 1000 : 0;
        if (running) chipTime.setText(String.format(Locale.US, "⏱ %d:%02d", sec / 60, sec % 60));

        // главная кнопка
        String uiState = running ? "run" : (enabled ? "ready" : "off");
        if (!uiState.equals(lastUiState)) {
            lastUiState = uiState;
            if (running) {
                mainButton.setText("■  Остановить");
                animateColors(mainButtonBg, new int[]{A3, 0xFFFF8A5C});
            } else {
                mainButton.setText("▶  Начать прогрев");
                animateColors(mainButtonBg, new int[]{A1, A2});
                if (!enabled) chipTime.setText("⏱ 0:00");
            }
            pop(mainButton);
        }
    }

    // ---------- шапка ----------

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(18));
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{A1, A2});
        bg.setCornerRadius(dp(26));
        hero.setBackground(bg);
        hero.setElevation(dp(10));
        if (!reduceMotion) {
            // градиент медленно «переливается» фиолет → розовый → голубой
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(7000);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new LinearInterpolator());
            final ArgbEvaluator ev = new ArgbEvaluator();
            va.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                bg.setColors(new int[]{(int) ev.evaluate(f, A1, A3), (int) ev.evaluate(f, A2, A1)});
            });
            va.start();
        }

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heart = new TextView(this);
        heart.setText("❤");
        heart.setTextSize(30);
        heart.setTextColor(Color.WHITE);
        heart.setGravity(Gravity.CENTER);
        GradientDrawable hb = new GradientDrawable();
        hb.setShape(GradientDrawable.OVAL);
        hb.setColor(0x33FFFFFF);
        heart.setBackground(hb);
        top.addView(heart, new LinearLayout.LayoutParams(dp(58), dp(58)));
        heartbeat(heart);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView title = text("Прогрев TikTok", 24, Color.WHITE, true);
        titles.addView(title);
        heroStatus = text("Готов к запуску", 14, 0xE6FFFFFF, false);
        titles.addView(heroStatus);
        top.addView(titles);
        hero.addView(top);

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(16), 0, 0);
        chipVideos = chip("🎬 0");
        chipLikes = chip("❤ 0");
        chipTime = chip("⏱ 0:00");
        chips.addView(chipVideos);
        chips.addView(chipLikes);
        chips.addView(chipTime);
        hero.addView(chips);

        entrance.add(hero);
        return withMargin(hero, 0, 0, 0, dp(14));
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
        mainButton = text("▶  Начать прогрев", 17, Color.WHITE, true);
        mainButton.setGravity(Gravity.CENTER);
        mainButtonBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{A1, A2});
        mainButtonBg.setCornerRadius(dp(99));
        mainButton.setBackground(ripple(mainButtonBg));
        mainButton.setElevation(dp(8));
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

    // ---------- карточка «доступ» ----------

    private View buildAccessCard() {
        accessCard = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        accessDot = new TextView(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(BAD);
        accessDot.setBackground(dot);
        row.addView(accessDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        if (!reduceMotion) {
            ValueAnimator va = ValueAnimator.ofFloat(1f, 0.25f);
            va.setDuration(900);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.addUpdateListener(a -> accessDot.setAlpha((float) a.getAnimatedValue()));
            va.start();
        }
        accessText = text("Включите доступ к спецвозможностям", 15, TEXT, true);
        accessText.setPadding(dp(10), 0, 0, 0);
        row.addView(accessText, new LinearLayout.LayoutParams(0, -2, 1f));
        accessCard.addView(row);

        TextView hint = text("Без него приложение не может листать ленту. "
                + "Если Android пишет «Настройка ограничена»: Настройки → Приложения → Прогрев TikTok → ⋮ → "
                + "«Разрешить ограниченные настройки».", 13, HINT, false);
        hint.setPadding(0, dp(8), 0, dp(12));
        accessCard.addView(hint);

        accessButton = smallButton("Открыть настройки");
        accessButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Найдите «Прогрев TikTok» и включите", Toast.LENGTH_LONG).show();
        });
        accessCard.addView(accessButton);
        return accessCard;
    }

    // ---------- карточки настроек ----------

    private View buildLikesCard() {
        LinearLayout c = card();
        c.addView(cardTitle("❤", "Лайки"));
        sLikeMin = stepper(c, "Лайк через: от", "likeMin", 1, 1, 100, 1, " вид.", false);
        sLikeMax = stepper(c, "Лайк через: до", "likeMax", 18, 1, 100, 1, " вид.", false);
        sVideos = stepper(c, "Роликов за запуск", "videos", 120, 0, 2000, 10, "", true);
        c.addView(note("∞ — без ограничения, до нажатия «Стоп»"));
        return c;
    }

    private View buildWatchCard() {
        LinearLayout c = card();
        c.addView(cardTitle("⏱", "Время просмотра"));
        sWatchMin = stepper(c, "Смотреть от", "watchMin", 5, 1, 300, 1, " с", false);
        sWatchMax = stepper(c, "Смотреть до", "watchMax", 25, 1, 300, 1, " с", false);
        sSkip = stepper(c, "Быстро пролистать (1–3 с)", "skipChance", 15, 0, 100, 5, "%", false);
        sLong = stepper(c, "Досмотреть подольше", "longChance", 10, 0, 100, 5, "%", false);
        c.addView(note("Время каждого ролика случайное. «Подольше» — до двух «до»."));
        return c;
    }

    private View buildParallelCard() {
        LinearLayout c = card();
        c.addView(cardTitle("📱", "Пользоваться другим приложением"));
        c.addView(note("Прогрев работает, пока TikTok виден на экране. Откройте его в разделённом экране "
                + "или в плавающем окне — и пользуйтесь второй половиной экрана как обычно."));
        parallelHow = text("1. Нажмите «Начать прогрев» — откроется TikTok.\n"
                + "2. Откройте список недавних приложений.\n"
                + "3. Нажмите на иконку TikTok сверху карточки → «Разделить экран» "
                + "(или «Открыть в окне» / «Всплывающее окно» на Samsung и Xiaomi).\n"
                + "4. Во вторую половину выберите нужное приложение.\n\n"
                + "Плашку «● прогрев» можно перетащить пальцем, чтобы она не мешала. "
                + "Нажатие на неё — остановка.", 13, TEXT, false);
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
                + "чтобы не получить теневой бан.", 12, HINT, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), 0);
        entrance.add(t);
        return t;
    }

    // ---------- запуск ----------

    private void startWarmup() {
        if (WarmupService.instance == null) {
            shake(accessCard);
            Toast.makeText(this, "Сначала включите доступ", Toast.LENGTH_LONG).show();
            return;
        }
        int likeMin = Math.min(sLikeMin.value, sLikeMax.value);
        int likeMax = Math.max(sLikeMin.value, sLikeMax.value);
        int watchMin = Math.min(sWatchMin.value, sWatchMax.value);
        int watchMax = Math.max(sWatchMin.value, sWatchMax.value);
        int skip = sSkip.value;
        int lng = Math.min(sLong.value, 100 - skip);

        Intent launch = null;
        for (String pkg : WarmupService.TIKTOK) {
            launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) break;
        }
        if (launch == null) {
            Toast.makeText(this, "TikTok не установлен", Toast.LENGTH_LONG).show();
            return;
        }
        lastDone = -1;
        lastLikes = -1;
        WarmupService.instance.start(sVideos.value, likeMin, likeMax, watchMin, watchMax, skip, lng);
        refresh();
        startActivity(launch);
    }

    // ---------- конструкторы UI ----------

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(22));
        d.setStroke(dp(1), LINE);
        c.setBackground(d);
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
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{A1, A2});
        d.setCornerRadius(dp(10));
        i.setBackground(d);
        row.addView(i, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView t = text(title, 17, TEXT, true);
        t.setPadding(dp(12), 0, 0, 0);
        row.addView(t);
        return row;
    }

    private Stepper stepper(LinearLayout parent, String label, String key, int def, int min, int max,
                            int step, String unit, boolean infiniteZero) {
        final Stepper s = new Stepper(key, def, min, max, step, unit, infiniteZero);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView l = text(label, 15, TEXT, false);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView minus = roundButton("−");
        minus.setOnClickListener(v -> s.change(-1));
        repeatOnHold(minus, () -> s.change(-1));
        row.addView(minus, new LinearLayout.LayoutParams(dp(36), dp(36)));

        s.valueView = text(s.text(), 16, Color.WHITE, true);
        s.valueView.setGravity(Gravity.CENTER);
        row.addView(s.valueView, new LinearLayout.LayoutParams(dp(68), -2));

        TextView plus = roundButton("+");
        plus.setOnClickListener(v -> s.change(+1));
        repeatOnHold(plus, () -> s.change(+1));
        row.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));

        parent.addView(row);
        steppers.add(s);
        return s;
    }

    private TextView roundButton(String s) {
        TextView b = text(s, 20, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x24FFFFFF);
        d.setStroke(dp(1), 0x1AFFFFFF);
        b.setBackground(ripple(d));
        pressable(b);
        return b;
    }

    private TextView smallButton(String s) {
        TextView b = text(s, 14, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{FxView.withAlpha(A1, 0x55), FxView.withAlpha(A2, 0x55)});
        d.setCornerRadius(dp(99));
        d.setStroke(dp(1), 0x33FFFFFF);
        b.setBackground(ripple(d));
        pressable(b);
        return b;
    }

    private TextView note(String s) {
        TextView t = text(s, 13, HINT, false);
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
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, null);
    }

    private View withMargin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        v.setLayoutParams(lp);
        return v;
    }

    // ---------- анимации ----------

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
                    .setStartDelay(80L * i).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
    }

    /** Сердце в шапке «бьётся»: два коротких удара и пауза. */
    private void heartbeat(View v) {
        if (reduceMotion) return;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(1400);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float s = 1f + 0.16f * bump(t, 0.08f) + 0.10f * bump(t, 0.30f);
            v.setScaleX(s);
            v.setScaleY(s);
        });
        va.start();
    }

    private static float bump(float t, float center) {
        float d = Math.abs(t - center) / 0.09f;
        return d >= 1f ? 0f : (float) (0.5 + 0.5 * Math.cos(Math.PI * d));
    }

    private void pop(View v) {
        if (reduceMotion) return;
        v.animate().cancel();
        v.setScaleX(1.14f);
        v.setScaleY(1.14f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(380).setStartDelay(0)
                .setInterpolator(new OvershootInterpolator(3f)).start();
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
        va.setDuration(400);
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

    /** Удержание кнопки «+»/«−» — быстрая прокрутка значения. */
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
        // pressable уже назначил OnTouchListener — оборачиваем, чтобы остановить повтор
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(110).setStartDelay(0).start();
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
