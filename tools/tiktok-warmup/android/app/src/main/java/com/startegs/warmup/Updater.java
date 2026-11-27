package com.startegs.warmup;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Проверка обновлений: сборка кладёт в релиз version.json ({versionCode, versionName, notes[]}),
 * приложение раз в 6 часов смотрит, нет ли версии новее, и предлагает обновиться.
 */
final class Updater {

    static final String RELEASE = "https://github.com/StarTegsPo/StarApp/releases/download/warmup-latest/";
    static final String APK_URL = RELEASE + "TikTokWarmup.apk";
    private static final String VERSION_URL = RELEASE + "version.json";
    private static final long EVERY = 6 * 3_600_000L;

    interface Callback { void onUpdate(int code, String name, String notes); }

    static int currentCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String currentName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** Сразу отдаёт сохранённый результат, а в фоне (не чаще раза в 6 ч) спрашивает GitHub. */
    static void check(final Context ctx, final Callback cb) {
        final SharedPreferences p = Cfg.prefs(ctx);
        final int cur = currentCode(ctx);
        if (p.getInt("updCode", 0) > cur) cb.onUpdate(p.getInt("updCode", 0), p.getString("updName", ""), p.getString("updNotes", ""));
        if (System.currentTimeMillis() - p.getLong("updChecked", 0) < EVERY) return;
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject(fetch(VERSION_URL));
                int code = o.getInt("versionCode");
                String name = o.optString("versionName");
                StringBuilder notes = new StringBuilder();
                JSONArray arr = o.optJSONArray("notes");
                if (arr != null) for (int i = 0; i < arr.length(); i++) notes.append(i > 0 ? "\n" : "").append("• ").append(arr.getString(i));
                p.edit().putLong("updChecked", System.currentTimeMillis()).putInt("updCode", code)
                        .putString("updName", name).putString("updNotes", notes.toString()).apply();
                if (code > cur) main.post(() -> cb.onUpdate(code, name, notes.toString()));
            } catch (Exception ignored) {
                // нет сети или GitHub недоступен — попробуем в следующий раз
            }
        }, "update-check").start();
    }

    private static String fetch(String url) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "tiktok-warmup-app");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 64_000) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("too many redirects");
    }

    /** Заметки «Что нового» текущей версии (assets/whatsnew.txt). */
    static String bundledNotes(Context c) {
        try (InputStream in = c.getAssets().open("whatsnew.txt")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (String line : out.toString(StandardCharsets.UTF_8.name()).split("\n")) {
                if (!line.trim().isEmpty()) sb.append(sb.length() > 0 ? "\n" : "").append("• ").append(line.trim());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
