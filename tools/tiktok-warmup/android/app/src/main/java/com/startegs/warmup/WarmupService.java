package com.startegs.warmup;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Имитирует живого зрителя в TikTok: смотрит ролики случайное время, листает фото-посты,
 * ставит лайки, иногда добавляет в избранное и делает репост.
 * Жесты идут только в окно TikTok (работает и в разделённом экране / плавающем окне).
 * Если TikTok не виден — пауза.
 */
public class WarmupService extends AccessibilityService {

    static WarmupService instance;

    static final String[] TIKTOK = {
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme"
    };

    static final String IDLE = "idle", RUNNING = "running", PAUSED = "paused", BREAK = "break", PHOTOS = "photos",
            SKIP = "skip", COMMENT = "comment";

    static final String DEFAULT_EMOJI = "🔥 😂 ❤️ 😍 👍 💯 🤣 👏 😮 🙌 ✨ 😁";

    /** Фразы для комментариев (по одной в строке). */
    static final String DEFAULT_PHRASES = String.join("\n",
            "Это просто огонь",
            "Пересмотрел уже раз пять",
            "Как же жизненно",
            "Обожаю такой контент",
            "Вот это поворот",
            "Сохранил, чтобы не потерять",
            "Ну это шедевр",
            "Кто тоже залип на этом видео?",
            "Лучшее, что я видел сегодня",
            "Аж настроение поднялось",
            "Ахаха, это же я",
            "Очень красиво получилось",
            "Хочу ещё такого",
            "Это гениально",
            "Скинул другу, он в восторге",
            "Почему это так смешно",
            "Вот это талант",
            "Идеально под вечер",
            "Не ожидал такой концовки",
            "Автор, ты лучший");

    /** Тексты для репоста — связные предложения (по одному в строке). */
    static final String DEFAULT_REPOST = String.join("\n",
            "Это стоит посмотреть каждому",
            "Не мог не поделиться, это шедевр",
            "Сохраняю к себе, слишком хорошо",
            "Пересматриваю уже который раз",
            "Обязательно досмотрите до конца",
            "Очень жизненно, согласен на все сто",
            "Вот такое у меня сегодня настроение",
            "Лучшее, что попалось мне за день",
            "Пусть будет у меня на странице",
            "Покажу это всем друзьям",
            "Вот это действительно круто",
            "Не могу перестать смеяться с этого");

    static String[] lines(String text) {
        List<String> out = new ArrayList<>();
        if (text != null) for (String l : text.split("\n")) if (!l.trim().isEmpty()) out.add(l.trim());
        return out.toArray(new String[0]);
    }

    /** Настройки запуска. videos = 0 — без ограничения, *Chance — проценты. */
    static class Config {
        int videos = 120, likeMin = 1, likeMax = 18, watchMin = 5, watchMax = 25;
        int skipChance = 15, longChance = 10, favChance = 4, repostChance = 2, commentChance = 2;
        boolean photos = true, skipLive = true, skipAds = true;
        String[] emoji = DEFAULT_EMOJI.split("\\s+");
        String[] phrases = lines(DEFAULT_PHRASES);
        String[] repostTexts = lines(DEFAULT_REPOST);
        int phrasePercent = 60;   // какая доля комментариев — фразы (остальные — эмодзи)
        int dailyLimit = 0;       // 0 — без дневного лимита (Premium)
    }

    // Подписи кнопок TikTok (английский и русский интерфейс)
    private static final Pattern P_SHARE = Pattern.compile("(?iu)(share|поделит|отправить)");
    private static final Pattern P_NOT_SHARE = Pattern.compile("(?iu)(comment|коммент|like|лайк)");
    private static final Pattern P_FAV = Pattern.compile("(?iu)(favou?rite|избранн|bookmark|закладк)");
    private static final Pattern P_FAV_DONE = Pattern.compile("(?iu)(remove from|удалить из|убрать из)");
    private static final Pattern P_REPOST = Pattern.compile("(?iu)^\\s*(repost|репост|сделать репост)\\s*$");
    // Прямые эфиры и реклама в ленте
    private static final Pattern P_LIVE = Pattern.compile(
            "(?iu)(^\\s*(live|live now|прямой эфир|в эфире|эфир)\\s*$|watch live|смотреть live|смотреть эфир|tap to watch live|нажмите, чтобы смотреть)");
    // Короткая подпись со словом LIVE / эфир (не хэштег и не описание ролика)
    private static final Pattern P_LIVE_SHORT = Pattern.compile(
            "(?iu)^(?!\\s*[#@])(?=.{1,40}$).*(\\blive\\b|эфир|трансляци)");
    private static final Pattern P_LIKE_BTN = Pattern.compile("(?iu)(like|лайк|нрав)");
    private static final Pattern P_NOT_LIKE = Pattern.compile("(?iu)(comment|коммент|share|подел)");
    private static final Pattern P_REPOST_CONFIRM = Pattern.compile(
            "(?iu)(^|\\s)(repost|репост\\S*|post|опубликовать|отправить|готово|done)(\\s|$)");
    private static final Pattern P_AD = Pattern.compile(
            "(?iu)^\\s*(sponsored|реклама|спонсорский контент|спонсировано|promoted|продвигается|ad|промо)\\s*$");
    // Комментарии
    private static final Pattern P_COMMENT = Pattern.compile("(?iu)(comment|коммент)");
    private static final Pattern P_NOT_COMMENT = Pattern.compile("(?iu)(like|лайк|share|подел)");
    private static final Pattern P_INPUT_HINT = Pattern.compile(
            "(?iu)(add comment|add a comment|добав\\S* комментар|оставь\\S* комментар|напиш\\S* комментар)");
    // Заголовок открытой панели комментариев: «1 234 комментария», «56 comments»
    private static final Pattern P_SHEET = Pattern.compile(
            "(?iu)^\\s*[\\d][\\d\\s.,]*\\s*(k|m|тыс\\.?|млн)?\\s*(comments?|комментари\\S*)\\s*$");
    private static final Pattern P_SEND = Pattern.compile("(?iu)(^|\\s)(post|send|отправ\\S*|опубликов\\S*)(\\s|$)");
    private static final Pattern P_PHOTO = Pattern.compile("^\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*$");

    private interface Act { long run(); }

    private static class Win {
        final Rect r;
        final AccessibilityNodeInfo root;
        Win(Rect r, AccessibilityNodeInfo root) { this.r = r; this.root = root; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private boolean running;
    private Config cfg = new Config();
    private int done, likes, favorites, reposts, photos, comments, skipped, untilLike, untilBreak;
    private int photoLeft;
    private boolean photoWentBack, rechecked, limitReached, likeButtonSeen;
    private String stopReason = "";
    private long startedAt;
    private String state = IDLE;
    private String lastPkg = "";

    private WindowManager wm;
    private TextView overlay;
    private WindowManager.LayoutParams overlayLp;

    // ---------- жизненный цикл ----------

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String pkg = e.getPackageName().toString();
            if (!pkg.equals(getPackageName())) lastPkg = pkg;
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        stop();
        instance = null;
        super.onDestroy();
    }

    // ---------- состояние для экрана приложения ----------

    static boolean isRunning() { return instance != null && instance.running; }
    String state() { return state; }
    int done() { return done; }
    int likes() { return likes; }
    int favorites() { return favorites; }
    int reposts() { return reposts; }
    int photos() { return photos; }
    int comments() { return comments; }
    int skipped() { return skipped; }
    String stopReason() { return stopReason; }
    int videos() { return cfg.videos; }
    long elapsedMs() { return running ? SystemClock.elapsedRealtime() - startedAt : 0; }

    // ---------- управление ----------

    void start(Config c) {
        stop();
        cfg = c;
        done = likes = favorites = reposts = photos = comments = skipped = 0;
        stopReason = "";
        limitReached = false;
        rechecked = false;
        untilLike = rand(c.likeMin, c.likeMax);
        untilBreak = rand(30, 50);
        startedAt = SystemClock.elapsedRealtime();
        running = true;
        state = RUNNING;
        showOverlay();
        updateOverlay();
        // 5 c на открытие TikTok, затем смотрим первый пост
        handler.postDelayed(afterSwipe, 5000);
    }

    void stop() {
        running = false;
        state = IDLE;
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
    }

    private boolean pauseIfHidden(Win w, Runnable retry) {
        if (w != null) return false;
        state = PAUSED;
        updateOverlay();
        handler.postDelayed(retry, 2000);
        return true;
    }

    // ---------- цикл: пост открыт → смотрим / листаем фото → действия → следующий ----------

    /** Новый пост только что появился на экране. */
    private final Runnable afterSwipe = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Win w = tiktok();
            if (pauseIfHidden(w, this)) return;
            state = RUNNING;
            // прямой эфир или реклама — быстро пролистываем, без лайков и действий
            boolean live = cfg.skipLive && isLive(w);
            boolean ad = cfg.skipAds && findInPost(w, P_AD) != null;
            if (live || ad) {
                // эфир мог ещё не прогрузиться — перепроверяем через секунду, чтобы не ошибиться
                if (!rechecked) {
                    rechecked = true;
                    handler.postDelayed(this, 1000);
                    return;
                }
                skipped++;
                state = SKIP;
                updateOverlay();
                handler.postDelayed(next, rand(900, 2500));
                return;
            }
            int[] ph = cfg.photos ? photoCounter(w) : null;
            if (ph != null && ph[1] > 1 && ph[0] < ph[1]) {
                photos++;
                state = PHOTOS;
                int remaining = ph[1] - ph[0];
                // как человек: чаще листает все фото, иногда бросает на середине
                photoLeft = rnd.nextInt(100) < 60 ? remaining : rand(1, remaining);
                photoWentBack = false;
                updateOverlay();
                handler.postDelayed(photoStep, rand(1500, 4000));
            } else {
                updateOverlay();
                handler.postDelayed(step, watchMs());
            }
        }
    };

    /** Листание фото в карусели: пауза на каждом фото, иногда возврат на одно назад. */
    private final Runnable photoStep = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Win w = tiktok();
            if (pauseIfHidden(w, this)) return;
            if (photoLeft <= 0) {
                state = RUNNING;
                handler.postDelayed(step, rand(1000, 3000));
                return;
            }
            if (!photoWentBack && photoLeft > 1 && rnd.nextInt(100) < 12) {
                swipeHorizontal(w.r, false);
                photoWentBack = true;
                photoLeft++;
            } else {
                swipeHorizontal(w.r, true);
                photoLeft--;
            }
            handler.postDelayed(this, rand(1500, 4500));
        }
    };

    /** Пост просмотрен: случайные действия, затем следующий. */
    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Win w = tiktok();
            if (pauseIfHidden(w, this)) return;
            state = RUNNING;
            done++;
            List<Act> acts = new ArrayList<>();
            if (--untilLike <= 0) {
                acts.add(WarmupService.this::actLike);
                untilLike = rand(cfg.likeMin, cfg.likeMax);
            }
            if (roll(cfg.favChance)) acts.add(WarmupService.this::actFavorite);
            if (roll(cfg.repostChance)) acts.add(WarmupService.this::actRepost);
            if (roll(cfg.commentChance) && (cfg.emoji.length > 0 || cfg.phrases.length > 0))
                acts.add(WarmupService.this::actComment);
            if (cfg.dailyLimit > 0 && Premium.addToday(WarmupService.this) >= cfg.dailyLimit) limitReached = true;
            updateOverlay();
            runChain(new ArrayDeque<>(acts), () -> {
                if (limitReached) {
                    stop();
                    stopReason = "limit";
                } else if (cfg.videos > 0 && done >= cfg.videos) {
                    stop();
                    stopReason = "done";
                } else {
                    handler.post(next);
                }
            });
        }
    };

    private final Runnable next = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Win w = tiktok();
            if (pauseIfHidden(w, this)) return;
            swipeUp(w.r);
            rechecked = false;
            long delay = rand(1100, 1700);
            if (--untilBreak <= 0) {
                delay += rand(30, 120) * 1000L;
                untilBreak = rand(30, 50);
                state = BREAK;
            } else {
                state = RUNNING;
            }
            updateOverlay();
            handler.postDelayed(afterSwipe, delay);
        }
    };

    private void runChain(final ArrayDeque<Act> acts, final Runnable then) {
        if (!running) return;
        if (acts.isEmpty()) {
            handler.postDelayed(then, rand(300, 900));
            return;
        }
        long d = acts.poll().run();
        handler.postDelayed(() -> runChain(acts, then), Math.max(d, 200));
    }

    // ---------- действия ----------

    private long actLike() {
        Win w = tiktok();
        if (w == null) return 0;
        doubleTap(w.r);
        likes++;
        bumpOverlay();
        return rand(1200, 2500);
    }

    private long actFavorite() {
        Win w = tiktok();
        if (w == null) return 0;
        AccessibilityNodeInfo n = find(w, P_FAV, null);
        if (n == null || n.isSelected() || n.isChecked() || P_FAV_DONE.matcher(label(n)).find()) return 0;
        click(n);
        favorites++;
        bumpOverlay();
        return rand(1000, 2200);
    }

    private long actRepost() {
        Win w = tiktok();
        if (w == null) return 0;
        AccessibilityNodeInfo share = find(w, P_SHARE, P_NOT_SHARE);
        if (share == null) return 0;
        click(share);
        // ждём панель «Поделиться», ищем «Репост»
        handler.postDelayed(() -> {
            if (!running) return;
            AccessibilityNodeInfo rp = findAnyTikTokWindow(P_REPOST);
            if (rp != null) {
                click(rp);
                reposts++;
                bumpOverlay();
                handler.postDelayed(() -> {
                    if (!running) return;
                    // TikTok иногда предлагает «Добавить мысли» к репосту — пишем связное предложение
                    AccessibilityNodeInfo field = findInput(false);
                    if (field != null && cfg.repostTexts.length > 0) {
                        setText(field, pick(cfg.repostTexts));
                        handler.postDelayed(() -> {
                            if (!running) return;
                            AccessibilityNodeInfo f = findInput(false);
                            AccessibilityNodeInfo ok = f == null ? null : findNearest(P_REPOST_CONFIRM, f, Integer.MAX_VALUE);
                            if (ok != null) click(ok);
                            handler.postDelayed(() -> closeComments(0), rand(1200, 1800));
                        }, rand(1200, 2200));
                    } else if (field != null) {
                        closeComments(0);
                    } else if (findAnyTikTokWindow(P_REPOST) != null) {
                        performGlobalAction(GLOBAL_ACTION_BACK);   // панель не закрылась сама
                    }
                }, rand(1300, 1900));
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }, rand(1400, 2200));
        return 10000;
    }

    /**
     * Комментарий из 1–3 случайных эмодзи: открыть комментарии → поле ввода → текст → отправить → закрыть.
     * Если на каком-то шаге кнопка не нашлась — аккуратно закрывает панель и идёт дальше.
     */
    private long actComment() {
        Win w = tiktok();
        if (w == null) return 0;
        AccessibilityNodeInfo btn = find(w, P_COMMENT, P_NOT_COMMENT);
        if (btn == null) return 0;
        state = COMMENT;
        updateOverlay();
        click(btn);
        handler.postDelayed(() -> {
            if (!running) return;
            AccessibilityNodeInfo input = findInput();
            if (input == null) { closeComments(0); return; }
            click(input);
            handler.postDelayed(() -> {
                if (!running) return;
                AccessibilityNodeInfo field = findInput();
                if (field == null) { closeComments(0); return; }
                if (!setText(field, commentText())) {
                    closeComments(0);
                    return;
                }
                handler.postDelayed(() -> {
                    if (!running) return;
                    AccessibilityNodeInfo f = findInput();
                    AccessibilityNodeInfo send = f == null ? null : findSendNear(f);
                    if (send != null) {
                        click(send);
                        comments++;
                        bumpOverlay();
                    }
                    handler.postDelayed(() -> closeComments(0), rand(1200, 1800));
                }, rand(1000, 2000));
            }, rand(900, 1400));
        }, rand(1500, 2300));
        return 11000;
    }

    /** Фраза (иногда с эмодзи в конце) или 1–3 эмодзи. */
    private CharSequence commentText() {
        if (cfg.phrases.length > 0 && (cfg.emoji.length == 0 || roll(cfg.phrasePercent))) {
            String t = pick(cfg.phrases);
            if (cfg.emoji.length > 0 && roll(35)) t += " " + pick(cfg.emoji);
            return t;
        }
        return randomEmoji();
    }

    private boolean setText(AccessibilityNodeInfo field, CharSequence text) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private String pick(String[] a) {
        return a[rnd.nextInt(a.length)];
    }

    /**
     * Прямой эфир: подпись LIVE/«эфир» на посте, или у поста нет обычных кнопок
     * «лайк» и «поделиться» (у роликов, фото и рекламы они есть, у превью эфира — нет).
     */
    private boolean isLive(Win w) {
        if (findInPost(w, P_LIVE) != null || findInPost(w, P_LIVE_SHORT) != null) return true;
        if (find(w, P_LIKE_BTN, P_NOT_LIKE) != null) {
            likeButtonSeen = true;
            return false;
        }
        // эвристику «нет кнопок» включаем, только если эта версия TikTok вообще показывает кнопки,
        // иначе пропускались бы все посты подряд
        return likeButtonSeen && find(w, P_SHARE, P_NOT_SHARE) == null;
    }

    private CharSequence randomEmoji() {
        int n = rand(1, 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(cfg.emoji[rnd.nextInt(cfg.emoji.length)]);
        return sb;
    }

    /** Закрывает клавиатуру и панель комментариев кнопкой «Назад» (не больше 3 раз). */
    private void closeComments(int attempt) {
        if (!running) return;
        boolean open = findInput(false) != null || findAnyTikTokWindow(P_SHEET) != null;
        if (attempt >= 3 || !open) {
            state = RUNNING;
            updateOverlay();
            return;
        }
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> closeComments(attempt + 1), 800);
    }

    /** Поле ввода комментария в любом окне TikTok. */
    private AccessibilityNodeInfo findInput() {
        return findInput(true);
    }

    /** allowHint=false — только настоящее поле ввода (без надписи «Добавить комментарий» в ленте). */
    private AccessibilityNodeInfo findInput(boolean allowHint) {
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null || !isTikTok(root.getPackageName().toString())) continue;
                AccessibilityNodeInfo n = findEditable(root, allowHint);
                if (n != null) return n;
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root, boolean allowHint) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        AccessibilityNodeInfo byHint = null;
        while (!q.isEmpty() && seen++ < 4000) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isVisibleToUser()) {
                CharSequence cls = n.getClassName();
                if (n.isEditable() || (cls != null && cls.toString().contains("EditText"))) return n;
                if (allowHint && byHint == null && P_INPUT_HINT.matcher(label(n)).find()) byHint = n;
            }
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return byHint;
    }

    /** Кнопка «Отправить» рядом с полем ввода (по вертикали). */
    private AccessibilityNodeInfo findSendNear(AccessibilityNodeInfo input) {
        return findNearest(P_SEND, input, dp(120));
    }

    /**
     * Ближайшая по вертикали к anchor кнопка с подходящей подписью (в любом окне TikTok).
     * Берётся только небольшая нажимаемая кнопка, не поле ввода и не вся панель.
     */
    private AccessibilityNodeInfo findNearest(Pattern p, AccessibilityNodeInfo anchor, int maxDy) {
        Rect in = new Rect();
        anchor.getBoundsInScreen(in);
        DisplayMetrics m = getResources().getDisplayMetrics();
        AccessibilityNodeInfo best = null;
        int bestDy = Integer.MAX_VALUE;
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null || !isTikTok(root.getPackageName().toString())) continue;
                ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
                q.add(root);
                Rect b = new Rect();
                int seen = 0;
                while (!q.isEmpty() && seen++ < 4000) {
                    AccessibilityNodeInfo n = q.poll();
                    if (n == null) continue;
                    if (n.isVisibleToUser() && !n.isEditable() && p.matcher(label(n)).find()) {
                        AccessibilityNodeInfo c = n;
                        for (int up = 0; c != null && !c.isClickable() && up < 2; up++) c = c.getParent();
                        if (c != null && c.isClickable()) {
                            c.getBoundsInScreen(b);
                            int dy = Math.abs(b.centerY() - in.centerY());
                            if (b.width() < m.widthPixels * 0.6f && dy <= maxDy && dy < bestDy) {
                                best = c;
                                bestDy = dy;
                            }
                        }
                    }
                    for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
                }
            }
        } catch (RuntimeException ignored) { }
        return best;
    }

    /** Элемент в области самого поста (без верхних вкладок «LIVE / Подписки / Для вас»). */
    private AccessibilityNodeInfo findInPost(Win w, Pattern p) {
        Rect post = new Rect(w.r.left, w.r.top + w.r.height() * 18 / 100, w.r.right, w.r.bottom - w.r.height() * 4 / 100);
        return findIn(w.root, post, p, null);
    }

    // ---------- поиск окна и кнопок TikTok ----------

    /** Окно TikTok на экране или null, если TikTok сейчас не виден. */
    private Win tiktok() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo w : windows) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null) continue;
                if (isTikTok(root.getPackageName().toString())) {
                    Rect r = new Rect();
                    w.getBoundsInScreen(r);
                    if (r.width() > 100 && r.height() > 200) return new Win(r, root);
                }
            }
            if (!windows.isEmpty()) return null;
        } catch (RuntimeException ignored) { }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root != null && root.getPackageName() != null ? root.getPackageName().toString() : lastPkg;
        if (!isTikTok(pkg)) return null;
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return new Win(new Rect(0, 0, m.widthPixels, m.heightPixels), root);
    }

    private static boolean isTikTok(String pkg) {
        for (String p : TIKTOK) if (p.equals(pkg)) return true;
        return false;
    }

    private static String label(AccessibilityNodeInfo n) {
        CharSequence d = n.getContentDescription(), t = n.getText();
        return (d != null ? d : "") + " " + (t != null ? t : "");
    }

    /** Видимый элемент текущего поста, подпись которого подходит под шаблон. */
    private AccessibilityNodeInfo find(Win w, Pattern p, Pattern exclude) {
        return findIn(w.root, w.r, p, exclude);
    }

    private AccessibilityNodeInfo findIn(AccessibilityNodeInfo root, Rect area, Pattern p, Pattern exclude) {
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        Rect b = new Rect();
        int seen = 0;
        while (!q.isEmpty() && seen++ < 4000) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            String s = label(n);
            if (s.trim().length() > 0 && p.matcher(s).find() && (exclude == null || !exclude.matcher(s).find())
                    && n.isVisibleToUser()) {
                n.getBoundsInScreen(b);
                if (area == null || area.contains(b.centerX(), b.centerY())) return n;
            }
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return null;
    }

    private AccessibilityNodeInfo findAnyTikTokWindow(Pattern p) {
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null || !isTikTok(root.getPackageName().toString())) continue;
                AccessibilityNodeInfo n = findIn(root, null, p, null);
                if (n != null) return n;
            }
        } catch (RuntimeException ignored) { }
        Win w = tiktok();
        return w == null ? null : findIn(w.root, null, p, null);
    }

    /** Счётчик фото «2/7» на экране → {2, 7}, иначе null. */
    private int[] photoCounter(Win w) {
        AccessibilityNodeInfo n = find(w, P_PHOTO, null);
        if (n == null) return null;
        Matcher m = P_PHOTO.matcher(label(n).trim());
        if (!m.find()) return null;
        int cur = Integer.parseInt(m.group(1)), total = Integer.parseInt(m.group(2));
        if (cur < 1 || total > 35 || cur > total) return null;
        return new int[]{cur, total};
    }

    private void click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        while (n != null && !n.isClickable()) n = n.getParent();
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        Path p = new Path();
        p.moveTo(b.centerX(), b.centerY());
        dispatch(new GestureDescription.StrokeDescription(p, 0, rand(40, 90)));
    }

    // ---------- жесты (в координатах окна TikTok) ----------

    private void swipeUp(Rect r) {
        float x = r.centerX() + rand(-r.width() / 18, r.width() / 18);
        Path p = new Path();
        p.moveTo(x, r.top + r.height() * rand(70, 78) / 100f);
        p.lineTo(x + rand(-25, 25), r.top + r.height() * rand(22, 30) / 100f);
        dispatch(new GestureDescription.StrokeDescription(p, 0, rand(220, 400)));
    }

    /** Свайп по карусели фото: forward — к следующему (справа налево). */
    private void swipeHorizontal(Rect r, boolean forward) {
        float y = r.top + r.height() * rand(38, 52) / 100f;
        float from = r.left + r.width() * (forward ? rand(72, 82) : rand(18, 28)) / 100f;
        float to = r.left + r.width() * (forward ? rand(18, 28) : rand(72, 82)) / 100f;
        Path p = new Path();
        p.moveTo(from, y);
        p.lineTo(to, y + rand(-20, 20));
        dispatch(new GestureDescription.StrokeDescription(p, 0, rand(200, 360)));
    }

    /** Двойной тап по центру — в TikTok он только ставит лайк и никогда не снимает его. */
    private void doubleTap(Rect r) {
        float x = r.centerX() + rand(-r.width() / 18, r.width() / 18);
        float y = r.top + r.height() * rand(38, 50) / 100f;
        Path p1 = new Path();
        p1.moveTo(x, y);
        Path p2 = new Path();
        p2.moveTo(x + rand(-6, 6), y + rand(-6, 6));
        int gap = rand(90, 150);
        dispatch(new GestureDescription.StrokeDescription(p1, 0, 40),
                new GestureDescription.StrokeDescription(p2, 40 + gap, 40));
    }

    private void dispatch(GestureDescription.StrokeDescription... strokes) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (GestureDescription.StrokeDescription s : strokes) b.addStroke(s);
        dispatchGesture(b.build(), null, null);
    }

    // ---------- плавающая плашка: нажать — стоп, тащить — переместить ----------

    private void showOverlay() {
        if (overlay != null || wm == null) return;
        Theme th = Theme.get(this);
        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(13);
        overlay.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int pad = dp(14);
        overlay.setPadding(pad, dp(9), pad, dp(9));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.alpha(th.a1, 0xEE), Theme.alpha(th.a3, 0xEE)});
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x55FFFFFF);
        overlay.setBackground(bg);
        overlay.setElevation(dp(8));

        overlayLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        overlayLp.gravity = Gravity.TOP | Gravity.START;
        overlayLp.x = dp(12);
        overlayLp.y = dp(90);
        overlayLp.windowAnimations = android.R.style.Animation_Toast;

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        overlay.setOnTouchListener(new android.view.View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;

            @Override
            public boolean onTouch(android.view.View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = overlayLp.x;
                        startY = overlayLp.y;
                        dragging = false;
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(120).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging && overlay != null) {
                            overlayLp.x = startX + (int) dx;
                            overlayLp.y = startY + (int) dy;
                            try { wm.updateViewLayout(overlay, overlayLp); } catch (RuntimeException ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                        if (!dragging) stop();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(overlay, overlayLp);
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void updateOverlay() {
        if (overlay == null) return;
        String icon;
        switch (state) {
            case PAUSED: icon = "⏸ откройте TikTok"; break;
            case BREAK:  icon = "☕ перерыв"; break;
            case PHOTOS: icon = "🖼 листаю фото"; break;
            case SKIP:   icon = "⏭ пропускаю эфир/рекламу"; break;
            case COMMENT: icon = "💬 комментирую"; break;
            default:     icon = "● прогрев";
        }
        StringBuilder sb = new StringBuilder(icon)
                .append("   🎬 ").append(done).append(cfg.videos > 0 ? "/" + cfg.videos : "")
                .append("   ❤ ").append(likes);
        if (favorites > 0) sb.append("   ★ ").append(favorites);
        if (reposts > 0) sb.append("   ↻ ").append(reposts);
        if (comments > 0) sb.append("   💬 ").append(comments);
        if (skipped > 0) sb.append("   ⏭ ").append(skipped);
        sb.append("   ■");
        overlay.setText(sb);
    }

    /** Короткая «пульсация» плашки после действия. */
    private void bumpOverlay() {
        if (overlay == null) return;
        updateOverlay();
        overlay.animate().cancel();
        overlay.setScaleX(1.12f);
        overlay.setScaleY(1.12f);
        overlay.animate().scaleX(1f).scaleY(1f).setDuration(350)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    private void hideOverlay() {
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay);
            } catch (RuntimeException ignored) { }
        }
        overlay = null;
    }

    // ---------- утилиты ----------

    /**
     * Случайное время просмотра: обычно от watchMin до watchMax,
     * с шансом skipChance% — быстрый пролист (1–3 c),
     * с шансом longChance% — долгий просмотр (от watchMax до 2×watchMax).
     */
    private long watchMs() {
        int roll = rnd.nextInt(100);
        if (roll < cfg.skipChance) return rand(1000, 3000);
        if (roll < cfg.skipChance + cfg.longChance) return rand(cfg.watchMax * 1000, cfg.watchMax * 2000);
        return rand(cfg.watchMin * 1000, cfg.watchMax * 1000);
    }

    private boolean roll(int percent) {
        return percent > 0 && rnd.nextInt(100) < percent;
    }

    private int rand(int a, int b) {
        if (b < a) { int t = a; a = b; b = t; }
        return a + rnd.nextInt(b - a + 1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
