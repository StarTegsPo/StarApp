/*
 * StarTegs 6.3: «Оптимизировать» — проверка здоровья приложения одной кнопкой.
 * Кольцо «здоровья» 0–100, разбор: кэш медиа, временные файлы, база Telegram, память, тяжёлые эффекты,
 * а по нажатию — очистка старого кэша, сжатие базы (VACUUM), очистка памяти картинок, корзина StarTegs
 * и по желанию «Турбо» (меньше анимаций для слабых телефонов). Ничего важного не удаляется:
 * кэш медиа заново скачается при открытии, переписки хранятся на серверах Telegram.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public final class StarOptimize {

    private StarOptimize() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static final String[] HEAVY = {"decor_rain", "decor_music", "look_parallax", "look_pinned_breath", "look_reaction_fx", "look_badge_glow", "look_cinema"};
    static final String[] HEAVY_NAMES = {"дождь на фоне", "музыкальная полоска", "параллакс", "дыхание закрепа", "эффекты реакций", "свечение значков", "кино-режим"};
    static final int[] KEEP_DAYS = {3, 7, 30, 0};
    static final String[] KEEP_NAMES = {"старше 3 дней", "старше 7 дней", "старше 30 дней", "весь"};

    static int keepIdx() {
        return Math.max(0, Math.min(3, prefs().getInt("opt_keep", 1)));
    }

    static boolean turbo() {
        return prefs().getBoolean("opt_turbo", false);
    }

    /** Результат проверки. */
    static final class Report {
        long cache, cacheOld, temp, db;
        int memPct, heavy, score;
        boolean lowRam;
        final ArrayList<String> heavyOn = new ArrayList<>();
    }

    private static long[] dirSize(File dir, long olderThan, int[] budget) {
        long all = 0, old = 0;
        if (dir == null) return new long[]{0, 0};
        File[] files = dir.listFiles();
        if (files == null) return new long[]{0, 0};
        for (File f : files) {
            if (budget[0]-- <= 0) break; // не зависаем на огромных папках
            if (f.isDirectory()) {
                long[] s = dirSize(f, olderThan, budget);
                all += s[0];
                old += s[1];
            } else {
                long l = f.length();
                all += l;
                if (olderThan <= 0 || f.lastModified() < olderThan) old += l;
            }
        }
        return new long[]{all, old};
    }

    private static long border() {
        int d = KEEP_DAYS[keepIdx()];
        return d == 0 ? 0 : System.currentTimeMillis() - d * 86400_000L;
    }

    static Report check(int account) {
        Report r = new Report();
        try {
            long[] c = dirSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), border(), new int[]{60000});
            r.cache = c[0];
            r.cacheOld = c[1];
            r.temp = dirSize(ApplicationLoader.applicationContext.getCacheDir(), 0, new int[]{20000})[0];
            r.db = MessagesStorage.getInstance(account).getDatabaseSize();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        Runtime rt = Runtime.getRuntime();
        r.memPct = (int) (100 * (rt.totalMemory() - rt.freeMemory()) / Math.max(1, rt.maxMemory()));
        try {
            ActivityManager am = (ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
            r.lowRam = am.isLowRamDevice() || am.getMemoryClass() < 256;
        } catch (Throwable ignore) {
        }
        for (int i = 0; i < HEAVY.length; i++) {
            if (prefs().getBoolean(HEAVY[i], false)) {
                r.heavy++;
                r.heavyOn.add(HEAVY_NAMES[i]);
            }
        }
        int s = 100;
        long mb = 1024 * 1024;
        s -= r.cacheOld > 1500 * mb ? 25 : r.cacheOld > 500 * mb ? 15 : r.cacheOld > 150 * mb ? 6 : 0;
        s -= r.temp > 100 * mb ? 10 : r.temp > 30 * mb ? 4 : 0;
        s -= r.db > 500 * mb ? 15 : r.db > 200 * mb ? 8 : 0;
        s -= r.memPct > 85 ? 10 : r.memPct > 70 ? 4 : 0;
        s -= Math.min(16, r.heavy * 4);
        if (r.lowRam && !turbo()) s -= 10;
        r.score = Math.max(5, s);
        return r;
    }

    private static long deleteIn(File dir, long olderThan) {
        long freed = 0;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                freed += deleteIn(f, olderThan);
            } else if ((olderThan <= 0 || f.lastModified() < olderThan) && f.lastModified() < System.currentTimeMillis() - 10 * 60_000L) {
                // свежие файлы (последние 10 минут) не трогаем — они могут скачиваться прямо сейчас
                long l = f.length();
                if (f.delete()) freed += l;
            }
        }
        return freed;
    }

    /** Сама оптимизация (в фоне). step(i) вызывается на UI после каждого шага. */
    interface Step { void on(int i); }

    interface Done { void on(long freed); }

    static void run(int account, Step step, Done done) {
        new Thread(() -> {
            long freed = 0;
            try {
                freed += deleteIn(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), border());
            } catch (Throwable e) {
                FileLog.e(e);
            }
            post(step, 0);
            try {
                freed += deleteIn(ApplicationLoader.applicationContext.getCacheDir(), 0);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            post(step, 1);
            long before = MessagesStorage.getInstance(account).getDatabaseSize();
            Object lock = new Object();
            boolean[] ready = {false};
            MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                try {
                    MessagesStorage.getInstance(account).getDatabase().executeFast("PRAGMA journal_size_limit = 0").stepThis().dispose();
                    MessagesStorage.getInstance(account).getDatabase().executeFast("VACUUM").stepThis().dispose();
                    MessagesStorage.getInstance(account).getDatabase().executeFast("PRAGMA journal_size_limit = 10485760").stepThis().dispose();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                synchronized (lock) {
                    ready[0] = true;
                    lock.notifyAll();
                }
            });
            synchronized (lock) {
                long until = System.currentTimeMillis() + 120_000;
                while (!ready[0] && System.currentTimeMillis() < until) {
                    try {
                        lock.wait(500);
                    } catch (InterruptedException ignore) {
                        break;
                    }
                }
            }
            freed += Math.max(0, before - MessagesStorage.getInstance(account).getDatabaseSize());
            post(step, 2);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ImageLoader.getInstance().clearMemory();
                } catch (Throwable ignore) {
                }
            });
            System.gc();
            post(step, 3);
            try {
                StarVault.cleanup(account, true);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            post(step, 4);
            prefs().edit().putLong("opt_last", System.currentTimeMillis()).putLong("opt_total", prefs().getLong("opt_total", 0) + freed).apply();
            final long f = freed;
            AndroidUtilities.runOnUIThread(() -> done.on(f));
        }, "StarOptimize").start();
    }

    private static void post(Step step, int i) {
        AndroidUtilities.runOnUIThread(() -> step.on(i));
        try {
            Thread.sleep(350); // чтобы шаги были видны глазом
        } catch (InterruptedException ignore) {
        }
    }

    static void setTurbo(boolean on) {
        SharedPreferences p = prefs();
        if (on) {
            if (!p.contains("opt_lite_before")) p.edit().putInt("opt_lite_before", LiteMode.getValue(true)).apply();
            LiteMode.setAllFlags(LiteMode.getValue(true) & ~(LiteMode.FLAG_PARTICLES | LiteMode.FLAG_CHAT_THANOS | LiteMode.FLAG_AUTOPLAY_GIFS
                    | LiteMode.FLAG_CHAT_BLUR | LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM | LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM | LiteMode.FLAG_LIQUID_GLASS));
            p.edit().putBoolean("opt_turbo", true).putBoolean("silk_motion", false).apply();
        } else {
            int before = p.getInt("opt_lite_before", -1);
            if (before != -1) LiteMode.setAllFlags(before);
            p.edit().putBoolean("opt_turbo", false).putBoolean("silk_motion", true).remove("opt_lite_before").apply();
        }
    }

    static String mb(long bytes) {
        double m = bytes / 1048576.0;
        return m >= 1024 ? String.format(Locale.US, "%.1f ГБ", m / 1024) : String.format(Locale.US, "%.0f МБ", m);
    }

    // ================================================================ кольцо здоровья

    static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        float shown = 0, target = -1;
        String caption = "Проверяю…";
        private long start = System.currentTimeMillis();

        Ring(Context c) {
            super(c);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(int score, String cap) {
            target = score;
            caption = cap;
            start = System.currentTimeMillis();
            invalidate();
        }

        static int color(float s) {
            return s >= 85 ? 0xFF34C759 : s >= 60 ? 0xFFFFB020 : 0xFFFF453A;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(230));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2, cy = AndroidUtilities.dp(112), rad = AndroidUtilities.dp(84);
            boolean busy = target < 0;
            if (!busy && Math.abs(shown - target) > 0.3f) {
                shown += (target - shown) * 0.12f;
                postInvalidateOnAnimation();
            } else if (!busy) shown = target;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(AndroidUtilities.dp(16));
            p.setShader(null);
            p.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) & 0x22FFFFFF);
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            c.drawArc(r, 135, 270, false, p);
            int col = color(busy ? 70 : shown);
            SweepGradient g = new SweepGradient(cx, cy, new int[]{AndroidUtilities.getOffsetColor(col, Color.WHITE, 0.35f, 1f), col, col}, new float[]{0.3f, 0.75f, 1f});
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(busy ? ((System.currentTimeMillis() - start) / 4f) % 360 : 90, cx, cy);
            g.setLocalMatrix(m);
            p.setShader(g);
            if (busy) {
                c.drawArc(r, ((System.currentTimeMillis() - start) / 3f) % 360, 70, false, p);
                postInvalidateOnAnimation();
            } else {
                c.drawArc(r, 135, 270 * shown / 100f, false, p);
            }
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            p.setTextSize(AndroidUtilities.dp(52));
            p.setTypeface(Typeface.create(AndroidUtilities.bold(), Typeface.BOLD));
            c.drawText(busy ? "…" : String.valueOf(Math.round(shown)), cx, cy + AndroidUtilities.dp(16), p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(AndroidUtilities.dp(14));
            p.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            c.drawText(busy ? "здоровье" : "здоровье из 100", cx, cy + AndroidUtilities.dp(40), p);
            p.setTextSize(AndroidUtilities.dp(16));
            p.setColor(busy ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) : color(shown));
            p.setTypeface(AndroidUtilities.bold());
            c.drawText(caption, cx, AndroidUtilities.dp(222), p);
        }
    }

    // ================================================================ экран

    public static class Screen extends UniversalFragment {
        private Report report;
        private Ring ring;
        private int doneSteps = -1;
        private boolean running;
        private long lastFreed = -1;

        static final String[] STEPS = {"🗂 Старый кэш медиа", "🧾 Временные файлы", "🗄 Сжатие базы Telegram", "🧠 Память картинок", "🗑 Корзина StarTegs"};

        @Override
        protected CharSequence getTitle() {
            return "🚀 Оптимизация";
        }

        @Override
        public View createView(Context context) {
            View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFF34C759, 0xFF00B3A6}, listView);
            scan();
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        private void scan() {
            new Thread(() -> {
                Report r = check(currentAccount);
                AndroidUtilities.runOnUIThread(() -> {
                    report = r;
                    if (ring != null) ring.set(r.score, verdict(r.score));
                    update();
                });
            }, "StarOptimizeScan").start();
        }

        static String verdict(int s) {
            return s >= 90 ? "Отлично — летает 🚀" : s >= 75 ? "Хорошо, но можно лучше" : s >= 55 ? "Пора оптимизировать" : "Телефону тяжело 😮‍💨";
        }

        private void update() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (getParentActivity() != null) {
                if (ring == null) ring = new Ring(getParentActivity());
                if (ring.getParent() instanceof ViewGroup) ((ViewGroup) ring.getParent()).removeView(ring);
                FrameLayout wrap = new FrameLayout(getParentActivity());
                wrap.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                items.add(UItem.asCustom(wrap));
            }
            items.add(UItem.asButton(1, running ? "⏳ Оптимизирую…" : lastFreed >= 0 ? "✅ Готово · ещё раз" : "🚀 Оптимизировать", "").accent());
            if (running || doneSteps >= 0) {
                items.add(UItem.asHeader("Шаги"));
                for (int i = 0; i < STEPS.length; i++) {
                    items.add(UItem.asButton(200 + i, STEPS[i], i <= doneSteps ? "✓" : running && i == doneSteps + 1 ? "…" : ""));
                }
                if (lastFreed >= 0) items.add(UItem.asShadow("Освобождено: " + mb(lastFreed) + ". Всего за всё время: " + mb(prefs().getLong("opt_total", 0)) + "."));
            }
            items.add(UItem.asHeader("Что нашёл"));
            Report r = report;
            if (r == null) {
                items.add(UItem.asShadow("Считаю размер кэша и базы…"));
            } else {
                items.add(UItem.asButton(10, "🗂 Кэш медиа", mb(r.cache) + (r.cacheOld > 0 && r.cacheOld < r.cache ? " · можно убрать " + mb(r.cacheOld) : "")));
                items.add(UItem.asButton(11, "🧾 Временные файлы", mb(r.temp)));
                items.add(UItem.asButton(12, "🗄 База Telegram", mb(r.db)));
                items.add(UItem.asButton(13, "🧠 Память приложения", r.memPct + "%"));
                items.add(UItem.asButton(14, "✨ Тяжёлые эффекты StarTegs", r.heavy == 0 ? "нет" : String.valueOf(r.heavy)));
                if (r.lowRam && !turbo()) items.add(UItem.asShadow("📱 Телефон с небольшим объёмом памяти — включи «Турбо», будет заметно быстрее."));
            }
            items.add(UItem.asHeader("Настройки"));
            items.add(UItem.asButton(20, "Удалять кэш медиа", KEEP_NAMES[keepIdx()]));
            items.add(UItem.asCheck(21, "⚡ Турбо: меньше анимаций, быстрее на слабых телефонах").setChecked(turbo()));
            if (r != null && r.heavy > 0) items.add(UItem.asButton(22, "✨ Выключить тяжёлые эффекты", android.text.TextUtils.join(", ", r.heavyOn)));
            items.add(UItem.asShadow("Ничего важного не удаляется: картинки и видео заново скачаются, когда откроешь их, переписки хранятся в Telegram. "
                    + (prefs().getLong("opt_last", 0) > 0 ? "Последняя оптимизация: " + android.text.format.DateFormat.format("d MMM, HH:mm", prefs().getLong("opt_last", 0)) + "." : "")));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            StarPolish.tap(view);
            switch (item.id) {
                case 1:
                    if (running) return;
                    running = true;
                    doneSteps = -1;
                    lastFreed = -1;
                    if (ring != null) ring.set(-1, "Оптимизирую…");
                    update();
                    run(currentAccount, i -> {
                        doneSteps = i;
                        update();
                    }, freed -> {
                        running = false;
                        lastFreed = freed;
                        StarAchieve.bump("optimize", 1);
                        if (getParentActivity() != null) Toast.makeText(getParentActivity(), "🚀 Готово! Освобождено " + mb(freed), Toast.LENGTH_LONG).show();
                        scan();
                    });
                    return;
                case 20:
                    if (getParentActivity() == null) return;
                    new AlertDialog.Builder(getParentActivity()).setTitle("Какой кэш медиа удалять")
                            .setItems(KEEP_NAMES, (d, w) -> {
                                prefs().edit().putInt("opt_keep", w).apply();
                                scan();
                            }).show();
                    return;
                case 21:
                    setTurbo(!turbo());
                    scan();
                    return;
                case 22:
                    SharedPreferences.Editor e = prefs().edit();
                    for (String k : HEAVY) e.putBoolean(k, false);
                    e.apply();
                    scan();
                    return;
            }
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    /** Подсказка на главном экране: пора оптимизировать (раз в неделю). */
    static boolean due() {
        return System.currentTimeMillis() - prefs().getLong("opt_last", 0) > 7 * 86400_000L;
    }

    static int account() {
        return UserConfig.selectedAccount;
    }
}
