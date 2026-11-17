/*
 * StarTegs 4.3 — безопасность и каналы:
 *   • журнал входов: список устройств аккаунта и предупреждение о новом входе — №67;
 *   • аварийный жест: встряхните телефон — StarTegs закроется на код-пароль и уйдёт с экрана — №66;
 *   • фильтр постов каналов по словам («скрыть всё про крипту») — №49;
 *   • фишинговые ссылки: поддельные домены Telegram и punycode-ссылки помечаются ⚠️ — №64.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class StarGuard {

    private StarGuard() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean panicGesture() { return prefs().getBoolean("guard_shake", false) && StarMod.isPro(); }
    public static boolean loginAlerts() { return prefs().getBoolean("guard_logins", true); }

    // ================================================================ фишинг

    private static final Pattern PHISH = Pattern.compile(
            "(?i)(https?://|\\b)([a-z0-9-]+\\.)*(xn--[a-z0-9-]+|t-me\\.|telegram-[a-z]+\\.|[a-z]*-telegram\\.|tg-[a-z]+\\.(com|net|org|site|online|xyz)|"
                    + "teleqram|telegrarn|te1egram|teiegram|telegarm|tlegram|telegram\\.(?!org\\b|me\\b)[a-z]{2,}|fragment-[a-z]+\\.)");

    /** Для StarExtra.isSuspicious: ссылка похожа на поддельный Telegram. */
    static boolean phishing(String text) {
        try {
            return !TextUtils.isEmpty(text) && PHISH.matcher(text).find();
        } catch (Exception e) {
            return false;
        }
    }

    // ================================================================ фильтр каналов

    static String channelWordsText() {
        return prefs().getString("guard_ch_words", "");
    }

    static void editChannelWords(BaseFragment f, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        EditText input = new EditText(f.getParentActivity());
        input.setText(channelWordsText());
        input.setHint("крипта\nставки\nрозыгрыш");
        input.setMinLines(3);
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("📢 Скрывать посты каналов со словами")
                .setMessage("Каждое слово с новой строки. Работает для новых загруженных постов.")
                .setView(input)
                .setPositiveButton("Сохранить", (d, w) -> {
                    prefs().edit().putString("guard_ch_words", input.getText().toString().trim().toLowerCase(Locale.ROOT)).apply();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null).show();
    }

    static int channelWordsCount() {
        String t = channelWordsText();
        if (t.isEmpty()) return 0;
        int n = 0;
        for (String l : t.split("\n")) if (!l.trim().isEmpty()) n++;
        return n;
    }

    /** Из StarMod.filterMessage: пост канала со стоп-словом. */
    public static boolean channelFiltered(MessageObject m) {
        try {
            if (!StarMod.isPro()) return false;
            String words = channelWordsText();
            if (words.isEmpty() || m == null || m.messageOwner == null || m.messageOwner.peer_id == null || m.messageOwner.peer_id.channel_id == 0) return false;
            TLRPC.Chat ch = MessagesController.getInstance(m.currentAccount).getChat(m.messageOwner.peer_id.channel_id);
            if (ch == null || !ChatObject.isChannelAndNotMegaGroup(ch)) return false;
            return wordHit(m.messageOwner.message);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    /** Есть ли в тексте стоп-слово фильтра каналов. */
    static boolean wordHit(String text) {
        String words = channelWordsText();
        if (words.isEmpty() || TextUtils.isEmpty(text)) return false;
        String low = text.toLowerCase(Locale.ROOT);
        for (String w : words.split("\n")) {
            w = w.trim();
            if (w.length() >= 2 && low.contains(w)) return true;
        }
        return false;
    }

    // ================================================================ журнал входов

    private static String fmt(int unix) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("ru")).format(new Date(unix * 1000L));
    }

    private static String describe(TLRPC.TL_authorization a) {
        return (a.current ? "📱 Это устройство — " : "💻 ") + a.device_model + " · " + a.app_name + " " + a.app_version
                + "\n" + (TextUtils.isEmpty(a.country) ? "" : a.country + " · ") + "вход " + fmt(a.date_created) + ", активен " + fmt(a.date_active);
    }

    private interface Got {
        void run(TL_account.authorizations res);
    }

    private static void load(int account, Got cb) {
        ConnectionsManager.getInstance(account).sendRequest(new TL_account.getAuthorizations(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_account.authorizations) cb.run((TL_account.authorizations) res);
        }));
    }

    static void showDevices(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        load(f.getCurrentAccount(), res -> {
            if (f.getParentActivity() == null) return;
            StringBuilder sb = new StringBuilder();
            for (TLRPC.TL_authorization a : res.authorizations) sb.append(describe(a)).append("\n\n");
            sb.append("Завершить чужой сеанс: Настройки Telegram → Устройства.");
            new AlertDialog.Builder(f.getParentActivity()).setTitle("🛡 Устройства аккаунта (" + res.authorizations.size() + ")")
                    .setMessage(sb.toString()).setPositiveButton("OK", null).show();
            remember(f.getCurrentAccount(), res);
        });
    }

    private static void remember(int account, TL_account.authorizations res) {
        HashSet<String> hs = new HashSet<>();
        for (TLRPC.TL_authorization a : res.authorizations) hs.add(String.valueOf(a.hash));
        prefs().edit().putStringSet("guard_auth_" + account, hs).apply();
    }

    /** Раз в 3 часа: есть ли новые входы, которых мы раньше не видели. */
    static void checkLogins() {
        if (!loginAlerts()) return;
        long last = prefs().getLong("guard_auth_at", 0);
        if (System.currentTimeMillis() - last < 3 * 3600_000L) return;
        prefs().edit().putLong("guard_auth_at", System.currentTimeMillis()).apply();
        for (int acc = 0; acc < UserConfig.MAX_ACCOUNT_COUNT; acc++) {
            if (!UserConfig.getInstance(acc).isClientActivated()) continue;
            final int account = acc;
            load(account, res -> {
                Set<String> known = prefs().getStringSet("guard_auth_" + account, null);
                if (known != null && !known.isEmpty()) {
                    for (TLRPC.TL_authorization a : res.authorizations) {
                        if (a.current || known.contains(String.valueOf(a.hash))) continue;
                        BaseFragment top = org.telegram.ui.LaunchActivity.getLastFragment();
                        if (top != null && top.getParentActivity() != null) {
                            new AlertDialog.Builder(top.getParentActivity())
                                    .setTitle("🛡 Новый вход в аккаунт")
                                    .setMessage(describe(a) + "\n\nЕсли это не вы — сразу завершите сеанс: Настройки → Устройства, и смените облачный пароль.")
                                    .setPositiveButton("Понятно", null).show();
                        }
                        break;
                    }
                }
                remember(account, res);
            });
        }
    }

    // ================================================================ аварийный жест

    private static Activity current;
    private static boolean lifecycle, registered;
    private static long lastShake;
    private static int shakes;

    private static final SensorEventListener shaker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            float x = e.values[0], y = e.values[1], z = e.values[2];
            double g = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
            if (g < 2.7) return;
            long now = System.currentTimeMillis();
            if (now - lastShake > 1200) shakes = 0;
            if (now - lastShake < 150) return;
            lastShake = now;
            if (++shakes >= 3) {
                shakes = 0;
                panic();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    static void panic() {
        try {
            if (SharedConfig.passcodeHash.length() > 0) {
                SharedConfig.appLocked = true;
                SharedConfig.saveConfig();
            }
            Activity a = current;
            if (a != null) {
                StarPlus.applyRecents(a);
                a.moveTaskToBack(true);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    static void update() {
        SensorManager sm = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return;
        if (panicGesture() && current != null) {
            if (!registered) {
                Sensor s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (s != null) registered = sm.registerListener(shaker, s, SensorManager.SENSOR_DELAY_GAME);
            }
        } else if (registered) {
            sm.unregisterListener(shaker);
            registered = false;
        }
    }

    /** Из StarPlus.init. */
    static void init(Activity activity) {
        checkLogins();
        if (lifecycle || activity == null) return;
        lifecycle = true;
        current = activity;
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { current = a; update(); }
            @Override public void onActivityPaused(Activity a) { current = null; update(); }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        update();
    }
}
