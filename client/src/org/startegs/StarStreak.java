/*
 * StarTegs 3.0 — стрики 🔥: сколько дней подряд вы и друг пишете друг другу.
 * Считается на телефоне по загруженной переписке. Огонёк с числом — справа от имени в списке чатов.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

public final class StarStreak {

    private StarStreak() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean enabled() {
        return prefs().getBoolean("streaks", true);
    }

    static void toggle() {
        prefs().edit().putBoolean("streaks", !enabled()).apply();
    }

    /** dialogId → {стрик, сегодня уже переписывались (1/0), когда посчитано (мин)} */
    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final LinkedHashSet<Long> queue = new LinkedHashSet<>();
    private static boolean running;

    static int dayIndex(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
    }

    private static int prevDay(int d) {
        int y = d / 400, doy = d % 400;
        if (doy > 1) return d - 1;
        Calendar c = Calendar.getInstance();
        c.set(y - 1, Calendar.DECEMBER, 31);
        return (y - 1) * 400 + c.get(Calendar.DAY_OF_YEAR);
    }

    /** Стрик по сообщениям (новые первыми): {дней подряд, сегодня обе стороны писали ? 1 : 0}. */
    static int[] compute(ArrayList<StarTools.Msg> msgs) {
        HashSet<Integer> mine = new HashSet<>(), theirs = new HashSet<>();
        for (StarTools.Msg m : msgs) {
            int d = dayIndex(m.date * 1000L);
            if (m.out) mine.add(d);
            else theirs.add(d);
        }
        int today = dayIndex(System.currentTimeMillis());
        boolean todayBoth = mine.contains(today) && theirs.contains(today);
        int d = todayBoth ? today : prevDay(today); // сегодня ещё можно успеть — стрик не сгорел
        int n = 0;
        while (mine.contains(d) && theirs.contains(d) && n < 3650) {
            n++;
            d = prevDay(d);
        }
        return new int[]{n, todayBoth ? 1 : 0};
    }

    /** Подпись к имени: « 🔥12» (от 3 дней), « 🔥12⏳» — сегодня ещё не переписывались. */
    public static String suffix(long dialogId) {
        if (dialogId <= 0 || !enabled()) return "";
        int nowMin = (int) (System.currentTimeMillis() / 60000);
        int[] c = cache.get(dialogId);
        if (c == null || nowMin - c[2] > 20) request(dialogId);
        if (c == null || c[0] < 3) return "";
        String fire = c[0] >= 100 ? "💎" : c[0] >= 30 ? "⚡" : "🔥";
        return " " + fire + c[0] + (c[1] == 0 ? "⏳" : "");
    }

    public static int value(long dialogId) {
        int[] c = cache.get(dialogId);
        return c == null ? 0 : c[0];
    }

    private static void request(long dialogId) {
        synchronized (queue) {
            if (!queue.add(dialogId) || running) return;
        }
        running = true;
        final int account = UserConfig.selectedAccount;
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            boolean changed = false;
            int done = 0;
            while (done < 40) {
                Long id;
                synchronized (queue) {
                    if (queue.isEmpty()) break;
                    id = queue.iterator().next();
                    queue.remove(id);
                }
                TLRPC.User u = MessagesController.getInstance(account).getUser(id);
                int[] r = u == null || u.bot || u.self ? new int[]{0, 0} : compute(StarTools.readMessages(account, id, 1500));
                int[] old = cache.get(id);
                int[] val = {r[0], r[1], (int) (System.currentTimeMillis() / 60000)};
                cache.put(id, val);
                if (old == null ? r[0] >= 3 : old[0] != r[0] || old[1] != r[1]) changed = true;
                done++;
            }
            final boolean ch = changed;
            AndroidUtilities.runOnUIThread(() -> {
                running = false;
                if (ch) NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
                Long next = null;
                synchronized (queue) {
                    if (!queue.isEmpty()) {
                        next = queue.iterator().next();
                        queue.remove(next);
                    }
                }
                if (next != null) request(next);
            });
        });
    }

    /** Сообщение-награда за круглые даты (показывается при открытии чата). */
    static String milestone(long dialogId) {
        int n = value(dialogId);
        int[] marks = {7, 30, 50, 100, 200, 365};
        for (int m : marks) {
            if (n == m && !prefs().getBoolean("streak_seen_" + dialogId + "_" + m, false)) {
                prefs().edit().putBoolean("streak_seen_" + dialogId + "_" + m, true).apply();
                return m >= 100 ? "💎 " + m + " дней подряд! Легендарная дружба" : m >= 30 ? "⚡ " + m + " дней подряд! Вы на одной волне" : "🔥 Неделя подряд! Так держать";
            }
        }
        return null;
    }
}
