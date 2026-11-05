/*
 * StarTegs — отчёт о сбоях. Если приложение упало, текст ошибки сохраняется, и при
 * следующем запуске приходит уведомление: нажмите — откроется «Поделиться» с текстом
 * отчёта, его можно переслать разработчику. Лицензия: GNU GPL v2 или новее.
 *
 * Работает до инициализации Telegram и ничего от неё не зависит, поэтому срабатывает
 * даже при сбое на самом старте.
 */
package org.startegs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class StarCrash {

    private StarCrash() {}

    private static final String FILE = "startegs_crash.txt";

    /** Вызывается первой строкой ApplicationLoader.onCreate. */
    public static void install(Context ctx) {
        if (ctx == null) return;
        try {
            report(ctx);
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String text = "StarTegs " + StarModConfig.MOD_VERSION + " (" + StarModConfig.BUILD_SHA + "), Android " + Build.VERSION.RELEASE
                            + ", " + Build.MANUFACTURER + " " + Build.MODEL + "\nПоток: " + thread.getName() + "\n\n" + sw;
                    try (FileOutputStream os = new FileOutputStream(new File(ctx.getFilesDir(), FILE))) {
                        os.write(text.getBytes("UTF-8"));
                    }
                } catch (Throwable ignore) {
                }
                if (prev != null) prev.uncaughtException(thread, e);
            });
        } catch (Throwable ignore) {
        }
    }

    /** Если в прошлый раз был сбой — показать уведомление с отчётом и удалить файл. */
    private static void report(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return;
        String text;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 60000)];
            int n = in.read(b);
            text = new String(b, 0, Math.max(n, 0), "UTF-8");
        } catch (Throwable e) {
            text = "не удалось прочитать отчёт: " + e;
        }
        f.delete();
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channel = "startegs_crash";
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(channel, "Отчёты о сбоях StarTegs", NotificationManager.IMPORTANCE_HIGH));
            }
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            Intent chooser = Intent.createChooser(share, "Отчёт о сбое StarTegs");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, 7001, chooser,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            String first = text.contains("\n\n") ? text.substring(text.indexOf("\n\n") + 2) : text;
            first = first.split("\n", 2)[0];
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, channel) : new Notification.Builder(ctx);
            nb.setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("StarTegs упал в прошлый раз")
                    .setContentText("Нажмите, чтобы отправить отчёт: " + first)
                    .setStyle(new Notification.BigTextStyle().bigText("Нажмите, чтобы отправить отчёт разработчику.\n\n" + first))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(7001, nb.build());
        } catch (Throwable ignore) {
        }
    }
}
