package com.startegs.warmup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** История сессий прогрева и уведомление-отчёт после каждой сессии. */
final class History {

    static final class Session {
        long time;
        int posts, likes, favorites, comments, reposts, follows;
        long seconds;
        String reason;
    }

    private static final int KEEP = 200;

    static void add(Context c, int posts, int likes, int fav, int com, int rep, int fol, long seconds, String reason) {
        JSONArray a = raw(c);
        JSONObject o = new JSONObject();
        try {
            o.put("t", System.currentTimeMillis());
            o.put("p", posts);
            o.put("l", likes);
            o.put("f", fav);
            o.put("c", com);
            o.put("r", rep);
            o.put("fo", fol);
            o.put("s", seconds);
            o.put("why", reason == null ? "" : reason);
        } catch (JSONException ignored) { }
        a.put(o);
        JSONArray trimmed = new JSONArray();
        for (int i = Math.max(0, a.length() - KEEP); i < a.length(); i++) trimmed.put(a.opt(i));
        Cfg.prefs(c).edit().putString("history", trimmed.toString()).apply();
    }

    static List<Session> all(Context c) {
        List<Session> out = new ArrayList<>();
        JSONArray a = raw(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Session s = new Session();
            s.time = o.optLong("t");
            s.posts = o.optInt("p");
            s.likes = o.optInt("l");
            s.favorites = o.optInt("f");
            s.comments = o.optInt("c");
            s.reposts = o.optInt("r");
            s.follows = o.optInt("fo");
            s.seconds = o.optLong("s");
            s.reason = o.optString("why");
            out.add(s);
        }
        return out;
    }

    /** Ролики и лайки по дням за последние n дней (последний элемент — сегодня). */
    static int[][] byDay(Context c, int n) {
        int[][] res = new int[2][n];
        Calendar today = Calendar.getInstance();
        for (Session s : all(c)) {
            Calendar d = Calendar.getInstance();
            d.setTimeInMillis(s.time);
            int diff = daysBetween(d, today);
            if (diff >= 0 && diff < n) {
                res[0][n - 1 - diff] += s.posts;
                res[1][n - 1 - diff] += s.likes;
            }
        }
        return res;
    }

    static int daysBetween(Calendar a, Calendar b) {
        Calendar x = (Calendar) a.clone(), y = (Calendar) b.clone();
        for (Calendar k : new Calendar[]{x, y}) {
            k.set(Calendar.HOUR_OF_DAY, 12);
            k.set(Calendar.MINUTE, 0);
            k.set(Calendar.SECOND, 0);
            k.set(Calendar.MILLISECOND, 0);
        }
        return (int) Math.round((y.getTimeInMillis() - x.getTimeInMillis()) / 86_400_000.0);
    }

    private static JSONArray raw(Context c) {
        try {
            return new JSONArray(Cfg.prefs(c).getString("history", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** Ссылка в бота: он красиво оформит отчёт и сохранит его у себя. */
    static String reportLink(int posts, int likes, int fav, int com, int rep, int fol, long seconds) {
        return Premium.BOT_URL + "?start=r_" + posts + "_" + likes + "_" + fav + "_" + com + "_" + rep + "_" + fol
                + "_" + Math.max(1, Math.round(seconds / 60.0));
    }

    static void notifyDone(Context c, int posts, int likes, int fav, int com, int rep, int fol, long seconds) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String ch = "warmup_report";
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(ch, "Отчёты о прогреве",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        String text = "🎬 " + posts + "  ❤ " + likes + "  ★ " + fav + "  💬 " + com + "  ↻ " + rep + "  ➕ " + fol
                + "  ⏱ " + Math.max(1, Math.round(seconds / 60.0)) + " мин";
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(reportLink(posts, likes, fav, com, rep, fol, seconds)));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(c, 7, open, flags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("✅ Прогрев завершён")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\nНажмите, чтобы отправить отчёт в Telegram"))
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            nm.notify(1001, b.build());
        } catch (SecurityException ignored) {
            // нет разрешения на уведомления (Android 13+) — отчёт останется в истории
        }
    }
}
