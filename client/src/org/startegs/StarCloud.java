/*
 * StarTegs — связь мода с ботом StarTegs: тариф (Free / Premium / VIP) и облачный
 * архив удалённых сообщений. Лицензия: GNU GPL v2 или новее.
 *
 * Мод открывает мини-приложение бота так же, как кнопка меню в чате с ботом
 * (messages.requestWebView). Telegram отдаёт ссылку, в которой есть подписанные им
 * данные пользователя (initData) и адрес API бота (?api=…). С этими данными мод
 * обращается к API бота тем же способом, что и мини-приложение: бот проверяет
 * подпись Telegram, поэтому подделать тариф нельзя.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

public final class StarCloud {

    private StarCloud() {}

    interface Session {
        void ok(String api, String init);

        void fail(String why);
    }

    private static String cachedApi, cachedInit;
    private static long cachedAt;
    private static volatile boolean busy;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- сессия с ботом

    static void session(int account, Session cb) {
        if (TextUtils.isEmpty(StarModConfig.ECO_BOT)) {
            cb.fail("Бот StarTegs не указан в настройках мода");
            return;
        }
        if (cachedInit != null && System.currentTimeMillis() - cachedAt < 30 * 60_000L) {
            cb.ok(cachedApi, cachedInit);
            return;
        }
        MessagesController mc = MessagesController.getInstance(account);
        mc.getUserNameResolver().resolve(StarModConfig.ECO_BOT, peerId -> AndroidUtilities.runOnUIThread(() -> {
            if (peerId == null || peerId <= 0) {
                cb.fail("Не удалось найти @" + StarModConfig.ECO_BOT);
                return;
            }
            TLRPC.TL_users_getFullUser full = new TLRPC.TL_users_getFullUser();
            full.id = mc.getInputUser(peerId);
            ConnectionsManager.getInstance(account).sendRequest(full, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                String menuUrl = null;
                if (response instanceof TLRPC.TL_users_userFull) {
                    TLRPC.UserFull uf = ((TLRPC.TL_users_userFull) response).full_user;
                    if (uf != null && uf.bot_info != null && uf.bot_info.menu_button instanceof TL_bots.TL_botMenuButton) {
                        menuUrl = ((TL_bots.TL_botMenuButton) uf.bot_info.menu_button).url;
                    }
                }
                if (TextUtils.isEmpty(menuUrl)) {
                    cb.fail("У бота нет кнопки мини-приложения. Откройте бота и нажмите /start");
                    return;
                }
                TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
                req.bot = mc.getInputUser(peerId);
                req.peer = mc.getInputPeer(peerId);
                req.platform = "android";
                req.url = menuUrl;
                req.flags |= 2;
                req.from_bot_menu = true;
                final String fallbackUrl = menuUrl;
                ConnectionsManager.getInstance(account).sendRequest(req, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!(res2 instanceof TLRPC.TL_webViewResultUrl)) {
                        cb.fail("Telegram не открыл мини-приложение бота" + (err2 != null ? ": " + err2.text : ""));
                        return;
                    }
                    String url = ((TLRPC.TL_webViewResultUrl) res2).url;
                    String api = param(url, "api");
                    if (api == null) api = param(fallbackUrl, "api");
                    String init = fragmentParam(url, "tgWebAppData");
                    if (api == null || !api.startsWith("https://")) {
                        cb.fail("Бот не сообщил адрес своего сервера — он запущен?");
                        return;
                    }
                    if (init == null) {
                        cb.fail("Telegram не передал данные авторизации");
                        return;
                    }
                    cachedApi = api.replaceAll("/+$", "");
                    cachedInit = init;
                    cachedAt = System.currentTimeMillis();
                    cb.ok(cachedApi, cachedInit);
                }));
            }));
        }));
    }

    private static String param(String url, String key) {
        try {
            return Uri.parse(url).getQueryParameter(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fragmentParam(String url, String key) {
        try {
            String frag = Uri.parse(url).getEncodedFragment();
            if (frag == null) return null;
            for (String part : frag.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equals(key)) return URLDecoder.decode(part.substring(eq + 1), "UTF-8");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    /** POST {api}/api с JSON. Вызывать не на главном потоке. */
    private static JSONObject post(String api, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(api + "/api").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString());
    }

    // ---------------------------------------------------------------- тариф

    /** "free", "pro" или "vip". */
    static String tier() {
        return prefs().getString("tier", "free");
    }

    static long tierCheckedAt() {
        return prefs().getLong("tier_at", 0);
    }

    /** Узнать тариф у бота. force — без ограничения «не чаще раза в 6 часов», с сообщением о результате. */
    public static void refreshTier(BaseFragment fragment, boolean force, Runnable done) {
        if (busy) return;
        if (!force && System.currentTimeMillis() - tierCheckedAt() < 6 * 3600_000L) return;
        busy = true;
        int account = fragment.getCurrentAccount();
        session(account, new Session() {
            @Override
            public void ok(String api, String init) {
                Utilities.globalQueue.postRunnable(() -> {
                    String tier = null, err = null;
                    try {
                        JSONObject res = post(api, new JSONObject().put("init", init).put("a", "state"));
                        JSONObject st = res.optJSONObject("state");
                        if (st == null) {
                            err = res.optString("msg", "Бот не ответил");
                        } else {
                            String t = st.optString("tier", "Free");
                            tier = st.optInt("vip", 0) == 1 || "VIP".equalsIgnoreCase(t) ? "vip" : "Premium".equalsIgnoreCase(t) ? "pro" : "free";
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        err = "Сервер бота недоступен";
                    }
                    final String t = tier, er = err;
                    AndroidUtilities.runOnUIThread(() -> {
                        busy = false;
                        if (t != null) prefs().edit().putString("tier", t).putLong("tier_at", System.currentTimeMillis()).apply();
                        if (force && fragment.getParentActivity() != null) {
                            Toast.makeText(fragment.getParentActivity(), t != null ? "Тариф: " + StarMod.tierTitle() : er, Toast.LENGTH_LONG).show();
                        }
                        if (done != null) done.run();
                    });
                });
            }

            @Override
            public void fail(String why) {
                busy = false;
                if (force && fragment.getParentActivity() != null) Toast.makeText(fragment.getParentActivity(), why, Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            }
        });
    }

    // ---------------------------------------------------------------- облачный архив

    public static boolean archiveEnabled() {
        return prefs().getBoolean("cloud_archive", false);
    }

    /** Отправить в бота удалённые сообщения, которые ещё не отправлялись (до 200 за раз). */
    public static void syncArchive(BaseFragment fragment, boolean verbose, Runnable done) {
        if (!archiveEnabled() || busy) return;
        final int account = fragment.getCurrentAccount();
        final List<long[]> keys = StarMod.unsyncedDeleted(account, 200);
        if (keys.isEmpty()) {
            if (verbose && fragment.getParentActivity() != null) Toast.makeText(fragment.getParentActivity(), "Архив уже актуален ✓", Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        session(account, new Session() {
            @Override
            public void ok(String api, String init) {
                MessagesStorage storage = MessagesStorage.getInstance(account);
                storage.getStorageQueue().postRunnable(() -> {
                    JSONArray items = new JSONArray();
                    for (long[] k : keys) {
                        long scope = k[0];
                        int mid = (int) k[1];
                        SQLiteCursor c = null;
                        try {
                            c = scope != 0
                                    ? storage.getDatabase().queryFinalized("SELECT uid, data FROM messages_v2 WHERE uid = " + scope + " AND mid = " + mid + " LIMIT 1")
                                    : storage.getDatabase().queryFinalized("SELECT uid, data FROM messages_v2 WHERE mid = " + mid + " AND is_channel = 0 LIMIT 1");
                            if (c.next()) {
                                long uid = c.longValue(0);
                                NativeByteBuffer data = c.byteBufferValue(1);
                                if (data != null) {
                                    TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    data.reuse();
                                    if (m != null) {
                                        long from = m.from_id != null ? m.from_id.user_id : 0;
                                        // id чата в формате Bot API: каналы и супергруппы — -100…
                                        long chat = scope != 0 ? scope - 1_000_000_000_000L : uid;
                                        items.put(new JSONObject().put("chat", chat).put("mid", mid).put("from", from)
                                                .put("ts", m.date).put("text", m.message != null ? m.message : "").put("media", m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty)));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        } finally {
                            if (c != null) c.dispose();
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        // имена отправителей — из кэша пользователей Telegram
                        MessagesController mc = MessagesController.getInstance(account);
                        try {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject it = items.getJSONObject(i);
                                TLRPC.User u = mc.getUser(it.optLong("from"));
                                it.put("name", u != null ? UserObject.getUserName(u) : "");
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        Utilities.globalQueue.postRunnable(() -> {
                            boolean okSent = false;
                            String msg = null;
                            try {
                                JSONObject res = post(api, new JSONObject().put("init", init).put("a", "modarch").put("items", items));
                                okSent = res.optBoolean("ok", false);
                                msg = okSent ? "В облачный архив отправлено: " + res.optInt("saved", items.length()) : res.optString("msg", "Бот не принял архив");
                                if ("bad".equals(res.optString("err")) || "unknown".equals(res.optString("err"))) {
                                    msg = "Обновите бота StarTegs до версии v58 — старая версия не принимает архив";
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                                msg = "Сервер бота недоступен";
                            }
                            final boolean sent = okSent;
                            final String m = msg;
                            AndroidUtilities.runOnUIThread(() -> {
                                busy = false;
                                if (sent) StarMod.markSynced(account, keys);
                                if (verbose && fragment.getParentActivity() != null) Toast.makeText(fragment.getParentActivity(), m, Toast.LENGTH_LONG).show();
                                if (done != null) done.run();
                            });
                        });
                    });
                });
            }

            @Override
            public void fail(String why) {
                busy = false;
                if (verbose && fragment.getParentActivity() != null) Toast.makeText(fragment.getParentActivity(), why, Toast.LENGTH_LONG).show();
            }
        });
    }
}
