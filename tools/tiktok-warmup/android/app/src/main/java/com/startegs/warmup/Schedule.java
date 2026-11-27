package com.startegs.warmup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

import java.util.Calendar;

/**
 * Автозапуск прогрева по расписанию (2 раза в день).
 * Будильник Android → экран включается → прогрев стартует сам и идёт sessionMinutes минут.
 */
final class Schedule {

    static final String ACTION = "com.startegs.warmup.SCHEDULED_START";

    static boolean enabled(Context c) {
        return Cfg.prefs(c).getBoolean("schedOn", false);
    }

    /** Время в минутах от полуночи: [утро, вечер]. */
    static int[] times(Context c) {
        SharedPreferences p = Cfg.prefs(c);
        return new int[]{p.getInt("sched1", 10 * 60 + 30), p.getInt("sched2", 19 * 60 + 30)};
    }

    static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Ставит (или снимает) оба будильника на ближайшее время. */
    static void apply(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int[] t = times(c);
        for (int i = 0; i < t.length; i++) {
            PendingIntent pi = pending(c, i);
            am.cancel(pi);
            if (!enabled(c)) continue;
            long at = next(t[i]);
            if (exactAllowed(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
    }

    static long next(int minutesOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        c.set(Calendar.MINUTE, minutesOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis() + 30_000) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    private static PendingIntent pending(Context c, int i) {
        Intent intent = new Intent(c, Receiver.class).setAction(ACTION).putExtra("slot", i);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, 500 + i, intent, flags);
    }

    static String fmt(int minutesOfDay) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    /** Будильник сработал или телефон перезагрузился. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            apply(c);   // следующий раз — завтра в это же время
            if (!ACTION.equals(intent.getAction()) || !enabled(c)) return;
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP, "warmup:scheduled");
                wl.acquire(20_000);
            }
            WarmupService s = WarmupService.instance;
            if (s != null) s.wakeAndStart();
        }
    }
}
