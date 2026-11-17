/*
 * StarTegs — реклама, прокси, админы, настройки каналов/историй (идеи из WhiteGram).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StarAds {

    private StarAds() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    private static void toggleFlag(String key, boolean def) {
        prefs().edit().putBoolean(key, !flag(key, def)).apply();
    }

    public static boolean hideSponsored() { return flag("ads_hide_sponsored", true); }
    public static void toggleHideSponsored() { toggleFlag("ads_hide_sponsored", true); }
    public static boolean hideKeywordAds() { return flag("ads_hide_keywords", true); }
    public static void toggleHideKeywordAds() { toggleFlag("ads_hide_keywords", true); }

    private static final String[] DEFAULT_AD_KEYWORDS = {
            "erid", "реклама", "партнёрский материал", "на правах рекламы",
            "рекламный пост", "промо", "sponsored", "advertisement",
            "ооо \"", "ооо «", "инн ", "erid:"
    };

    public static Set<String> adKeywords() {
        Set<String> set = prefs().getStringSet("ads_keywords", null);
        if (set == null || set.isEmpty()) return new HashSet<>(Arrays.asList(DEFAULT_AD_KEYWORDS));
        return set;
    }

    public static void setAdKeywords(Set<String> keywords) {
        prefs().edit().putStringSet("ads_keywords", keywords).apply();
    }

    public static boolean shouldHideMessage(org.telegram.messenger.MessageObject msg) {
        if (msg == null || msg.messageOwner == null) return false;
        if (hideSponsored() && msg.isSponsored()) return true;
        if (!hideKeywordAds()) return false;
        String text = msg.messageText != null ? msg.messageText.toString() : null;
        if (TextUtils.isEmpty(text) && msg.messageOwner.message != null) text = msg.messageOwner.message;
        if (TextUtils.isEmpty(text)) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : adKeywords()) {
            if (kw != null && !kw.isEmpty() && lower.contains(kw.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static boolean viewAdminsAlways() { return flag("view_admins_always", true); }
    public static void toggleViewAdminsAlways() { toggleFlag("view_admins_always", true); }

    public static void showAdmins(BaseFragment fragment, long chatId) {
        if (fragment == null || fragment.getParentActivity() == null || chatId == 0) return;
        final int account = fragment.getCurrentAccount();
        final long channelId = chatId > 0 ? chatId : -chatId;
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInstance(account).getInputChannel(channelId);
        if (req.channel == null || req.channel.channel_id == 0) {
            Toast.makeText(fragment.getParentActivity(), "Это не канал/супергруппа", Toast.LENGTH_SHORT).show();
            return;
        }
        req.filter = new TLRPC.TL_channelParticipantsAdmins();
        req.offset = 0;
        req.limit = 100;
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), 3);
        progress.setCanCancel(true);
        progress.show();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try { progress.dismiss(); } catch (Exception ignore) {}
            if (error != null || !(response instanceof TLRPC.TL_channels_channelParticipants)) {
                Toast.makeText(fragment.getParentActivity(), error != null ? error.text : "Не удалось получить список", Toast.LENGTH_SHORT).show();
                return;
            }
            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
            MessagesController.getInstance(account).putUsers(res.users, false);
            MessagesController.getInstance(account).putChats(res.chats, false);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (TLRPC.ChannelParticipant p : res.participants) {
                long uid = 0;
                if (p instanceof TLRPC.TL_channelParticipantAdmin) uid = ((TLRPC.TL_channelParticipantAdmin) p).user_id;
                else if (p instanceof TLRPC.TL_channelParticipantCreator) uid = ((TLRPC.TL_channelParticipantCreator) p).user_id;
                else if (p.peer != null) uid = p.peer.user_id;
                if (uid == 0) continue;
                TLRPC.User u = MessagesController.getInstance(account).getUser(uid);
                n++;
                String name = u != null ? org.telegram.messenger.UserObject.getUserName(u) : ("ID " + uid);
                String role = (p instanceof TLRPC.TL_channelParticipantCreator) ? "создатель" : "админ";
                sb.append(n).append(". ").append(name).append(" (").append(role).append(")\n");
            }
            if (n == 0) sb.append("Администраторы не найдены или канал приватный.");
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle("Администраторы (" + n + ")")
                    .setMessage(sb.toString())
                    .setPositiveButton("OK", null)
                    .show();
        }));
    }

    public static boolean wideChannelPosts() { return flag("channel_wide_posts", false); }
    public static void toggleWideChannelPosts() { toggleFlag("channel_wide_posts", false); }
    public static boolean hideChannelBottomPanel() { return flag("channel_hide_bottom", false); }
    public static void toggleHideChannelBottomPanel() { toggleFlag("channel_hide_bottom", false); }
    public static boolean confirmStoryView() { return flag("story_confirm_view", false); }
    public static void toggleConfirmStoryView() { toggleFlag("story_confirm_view", false); }
    public static boolean disableStorySwipeRecord() { return flag("story_no_swipe_record", false); }
    public static void toggleDisableStorySwipeRecord() { toggleFlag("story_no_swipe_record", false); }
    public static boolean autoProxy() { return flag("proxy_auto", false); }
    public static void toggleAutoProxy() { toggleFlag("proxy_auto", false); }

    private static final AtomicBoolean proxyUpdating = new AtomicBoolean(false);

    public static void refreshProxies(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        if (!proxyUpdating.compareAndSet(false, true)) {
            Toast.makeText(fragment.getParentActivity(), "Уже обновляю…", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(fragment.getParentActivity(), "Ищу свежие прокси…", Toast.LENGTH_SHORT).show();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            List<String> found = new ArrayList<>();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://raw.githubusercontent.com/hookzof/socks5_list/master/tg/mtproto.txt").openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "StarTegs");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("tg://proxy") || line.startsWith("https://t.me/proxy") || (line.contains("server=") && line.contains("secret="))) {
                        found.add(line);
                    }
                    if (found.size() > 40) break;
                }
                br.close();
            } catch (Exception e) { FileLog.e(e); }
            final List<String> result = found;
            AndroidUtilities.runOnUIThread(() -> {
                proxyUpdating.set(false);
                if (result.isEmpty()) {
                    Toast.makeText(fragment.getParentActivity(), "Не удалось найти свежие прокси", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(fragment.getParentActivity())
                        .setTitle("Найдено прокси: " + result.size())
                        .setMessage("Добавить первые 5?\n\n" + TextUtils.join("\n", result.subList(0, Math.min(5, result.size()))))
                        .setPositiveButton("Добавить", (d, w) -> {
                            for (int i = 0; i < Math.min(5, result.size()); i++) {
                                String link = result.get(i);
                                if (link.startsWith("tg://") || link.startsWith("https://t.me/proxy"))
                                    org.telegram.messenger.browser.Browser.openUrl(fragment.getParentActivity(), link);
                            }
                            Toast.makeText(fragment.getParentActivity(), "Ссылки открыты — подтвердите добавление", Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            });
        });
    }

    /** Панель настроек этапа 1 — отдельный экран с переключателями. */
    public static void showSettings(BaseFragment fragment) {
        if (fragment == null) return;
        fragment.presentFragment(new StarAdsActivity());
    }

    public static ArrayList<String> summary() {
        ArrayList<String> s = new ArrayList<>();
        if (hideSponsored()) s.add("без sponsored");
        if (hideKeywordAds()) s.add("скрытие рекламы");
        if (viewAdminsAlways()) s.add("админы");
        if (autoProxy()) s.add("авто-прокси");
        if (wideChannelPosts()) s.add("широкие посты");
        if (confirmStoryView()) s.add("подтверждение историй");
        return s;
    }
}
