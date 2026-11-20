/*
 * StarTegs 4.7 — ещё удобства: чистые ссылки, исправление раскладки, тихие часы, таймер сна для музыки,
 * режим стримера, экспорт чата в .txt и уведомления по ключевым словам даже в заглушённых чатах.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StarX {

    private StarX() {}

    public static final int MENU_FIX_LAYOUT = 9360;
    public static final int MENU_EXPORT_TXT = 9361;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static boolean flag(String k, boolean def) {
        return prefs().getBoolean(k, def);
    }

    static void toggle(String k, boolean def) {
        prefs().edit().putBoolean(k, !flag(k, def)).apply();
    }

    // ================================================================ чистые ссылки

    public static boolean cleanLinksOn() { return flag("x_clean_links", true); }

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACK = Pattern.compile(
            "(?i)^(utm_[a-z_]+|fbclid|gclid|yclid|dclid|msclkid|igshid|igsh|si|_openstat|mc_cid|mc_eid|ref_src|ref_url|spm|share_id|from_share|_hsenc|_hsmi|vero_id|wt_mc|cmpid)$");

    /** Убирает метки слежки (utm_…, fbclid, si=…) из ссылок. */
    public static String cleanLinks(String text) {
        if (text == null || text.indexOf("://") < 0) return text;
        Matcher m = URL.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(cleanUrl(m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String cleanUrl(String url) {
        int q = url.indexOf('?');
        if (q < 0) return url;
        int h = url.indexOf('#', q);
        String base = url.substring(0, q);
        String query = h < 0 ? url.substring(q + 1) : url.substring(q + 1, h);
        String frag = h < 0 ? "" : url.substring(h);
        StringBuilder keep = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.isEmpty()) continue;
            String key = part.split("=", 2)[0];
            if (TRACK.matcher(key).matches()) continue;
            if (keep.length() > 0) keep.append('&');
            keep.append(part);
        }
        return base + (keep.length() > 0 ? "?" + keep : "") + frag;
    }

    /** Вызывается перед отправкой: меняет текст, только если в сообщении нет форматирования. */
    public static void beforeSend(org.telegram.messenger.SendMessagesHelper.SendMessageParams p) {
        if (!cleanLinksOn() || p == null || p.message == null || (p.entities != null && !p.entities.isEmpty())) return;
        String c = cleanLinks(p.message);
        if (!c.equals(p.message)) p.message = c;
    }

    // ================================================================ исправление раскладки

    private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>~";
    private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбюёЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁ";

    /** «ghbdtn» → «привет» и обратно (направление — по тому, каких букв больше). */
    public static String fixLayout(String s) {
        int lat = 0, cyr = 0;
        for (char c : s.toCharArray()) {
            if (c < 128 && Character.isLetter(c)) lat++;
            else if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) cyr++;
        }
        String from = lat >= cyr ? EN : RU, to = lat >= cyr ? RU : EN;
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = from.indexOf(c);
            b.append(i >= 0 ? to.charAt(i) : c);
        }
        return b.toString();
    }

    public static void fixInput(ChatActivity chat) {
        if (chat.getChatActivityEnterView() == null) return;
        CharSequence t = chat.getChatActivityEnterView().getFieldText();
        if (TextUtils.isEmpty(t)) {
            if (chat.getParentActivity() != null) Toast.makeText(chat.getParentActivity(), "Сначала напиши текст в поле ввода", Toast.LENGTH_SHORT).show();
            return;
        }
        chat.getChatActivityEnterView().setFieldText(fixLayout(t.toString()));
    }

    // ================================================================ тихие часы

    public static boolean quietOn() { return flag("x_quiet", false); }

    public static int quietFrom() { return prefs().getInt("x_quiet_from", 23); }

    public static int quietTo() { return prefs().getInt("x_quiet_to", 8); }

    public static void setQuiet(int from, int to) {
        prefs().edit().putInt("x_quiet_from", from).putInt("x_quiet_to", to).apply();
    }

    /** Сейчас тихие часы: уведомления приходят без звука и вибрации. */
    public static boolean quietNow() {
        if (System.currentTimeMillis() < prefs().getLong("x_silence_until", 0)) return true; // 5.5: «Тишина на час»
        if (!quietOn()) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), a = quietFrom(), b = quietTo();
        return a == b || (a < b ? h >= a && h < b : h >= a || h < b);
    }

    static void setSilenceUntil(long until) {
        prefs().edit().putLong("x_silence_until", until).apply();
    }

    public static String silenceTitle() {
        long left = prefs().getLong("x_silence_until", 0) - System.currentTimeMillis();
        return left <= 0 ? "" : "ещё " + (left / 3600_000L > 0 ? left / 3600_000L + " ч " : "") + (left / 60_000L % 60) + " мин";
    }

    public static String quietTitle() {
        return String.format(Locale.US, "%02d:00–%02d:00", quietFrom(), quietTo());
    }

    public static void chooseQuiet(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.US, "%02d:00", i);
        new AlertDialog.Builder(a).setTitle("🌙 Тихо с…").setItems(hours, (d, from) ->
                new AlertDialog.Builder(a).setTitle("☀️ …и до").setItems(hours, (d2, to) -> {
                    setQuiet(from, to);
                    if (done != null) done.run();
                }).show()).show();
    }

    // ================================================================ таймер сна для музыки

    private static Runnable sleepTask;
    private static long sleepAt;

    public static String sleepTitle() {
        if (sleepTask == null) return "";
        long left = Math.max(0, sleepAt - System.currentTimeMillis()) / 60000;
        return "через " + (left + 1) + " мин";
    }

    public static void chooseSleep(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        int[] mins = {10, 15, 30, 45, 60, 90};
        String[] t = new String[mins.length + 1];
        for (int i = 0; i < mins.length; i++) t[i] = mins[i] + " минут";
        t[mins.length] = sleepTask != null ? "❌ Выключить таймер" : "Не нужно";
        new AlertDialog.Builder(a).setTitle("😴 Выключить музыку через…").setItems(t, (d, w) -> {
            if (sleepTask != null) AndroidUtilities.cancelRunOnUIThread(sleepTask);
            sleepTask = null;
            if (w < mins.length) {
                sleepAt = System.currentTimeMillis() + mins[w] * 60_000L;
                sleepTask = () -> {
                    sleepTask = null;
                    try {
                        MediaController mc = MediaController.getInstance();
                        MessageObject m = mc.getPlayingMessageObject();
                        if (m != null) mc.pauseMessage(m);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                };
                AndroidUtilities.runOnUIThread(sleepTask, mins[w] * 60_000L);
                Toast.makeText(a, "😴 Музыка выключится через " + mins[w] + " мин", Toast.LENGTH_SHORT).show();
            }
            if (done != null) done.run();
        }).show();
    }

    // ================================================================ режим стримера

    public static boolean streamer() { return flag("x_streamer", false); }

    /** Номер телефона в профиле и настройках: в режиме стримера видны только код страны и 2 последние цифры. */
    public static String phone(String formatted) {
        if (!streamer() || formatted == null) return formatted;
        StringBuilder b = new StringBuilder();
        int digits = 0, total = 0;
        for (char c : formatted.toCharArray()) if (Character.isDigit(c)) total++;
        for (char c : formatted.toCharArray()) {
            if (Character.isDigit(c)) {
                digits++;
                b.append(digits <= 1 || digits > total - 2 ? c : '•');
            } else b.append(c);
        }
        return b.toString();
    }

    // ================================================================ экспорт чата в .txt

    public static void exportChat(ChatActivity chat) {
        Activity a = chat.getParentActivity();
        if (a == null) return;
        String[] t = {"Последние 300 сообщений", "Последние 1000", "Последние 3000 ⭐"};
        int[] n = {300, 1000, 3000};
        new AlertDialog.Builder(a).setTitle("📄 Сохранить переписку в файл").setItems(t, (d, w) -> {
            if (w == 2 && !StarMod.requirePro(chat, "Экспорт 3000 сообщений")) return;
            AlertDialog progress = new AlertDialog(a, AlertDialog.ALERT_TYPE_SPINNER);
            progress.setCanCancel(false);
            progress.show();
            ArrayList<TLRPC.Message> all = new ArrayList<>();
            load(chat, chat.getDialogId(), 0, n[w], all, () -> {
                try {
                    progress.dismiss();
                } catch (Exception ignore) {
                }
                share(chat, all);
            });
        }).show();
    }

    private static void load(ChatActivity chat, long did, int offsetId, int need, ArrayList<TLRPC.Message> all, Runnable done) {
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = chat.getMessagesController().getInputPeer(did);
        req.offset_id = offsetId;
        req.limit = 100;
        chat.getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                chat.getMessagesController().putUsers(r.users, false);
                chat.getMessagesController().putChats(r.chats, false);
                all.addAll(r.messages);
                if (!r.messages.isEmpty() && all.size() < need && r.messages.size() >= 100) {
                    load(chat, did, r.messages.get(r.messages.size() - 1).id, need, all, done);
                    return;
                }
            }
            done.run();
        }));
    }

    private static String media(TLRPC.Message m) {
        if (m.media == null || m.media instanceof TLRPC.TL_messageMediaEmpty || m.media instanceof TLRPC.TL_messageMediaWebPage) return "";
        if (m.media.photo != null) return "[фото] ";
        if (m.media.document != null) {
            TLRPC.Document d = m.media.document;
            if (MessageObject.isVoiceDocument(d)) return "[голосовое] ";
            if (MessageObject.isRoundVideoDocument(d)) return "[кружок] ";
            if (MessageObject.isStickerDocument(d)) return "[стикер] ";
            if (MessageObject.isVideoDocument(d)) return "[видео] ";
            if (MessageObject.isMusicDocument(d)) return "[музыка] ";
            return "[файл] ";
        }
        if (m.media instanceof TLRPC.TL_messageMediaGeo || m.media instanceof TLRPC.TL_messageMediaVenue) return "[место] ";
        if (m.media instanceof TLRPC.TL_messageMediaPoll) return "[опрос] ";
        if (m.media instanceof TLRPC.TL_messageMediaContact) return "[контакт] ";
        return "[вложение] ";
    }

    private static void share(ChatActivity chat, ArrayList<TLRPC.Message> all) {
        Activity a = chat.getParentActivity();
        if (a == null) return;
        if (all.isEmpty()) {
            Toast.makeText(a, "Не получилось загрузить сообщения", Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.reverse(all);
        MessagesController mc = chat.getMessagesController();
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String title;
        long did = chat.getDialogId();
        if (did > 0) title = UserObject.getUserName(mc.getUser(did));
        else {
            TLRPC.Chat c = mc.getChat(-did);
            title = c != null ? c.title : "Чат";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Переписка «").append(title).append("» — ").append(all.size()).append(" сообщений\n");
        sb.append("Сохранено в StarTegs ").append(df.format(new Date())).append("\n\n");
        for (TLRPC.Message m : all) {
            if (m instanceof TLRPC.TL_messageService) continue;
            long from = MessageObject.getFromChatId(m);
            String who;
            if (from > 0) who = UserObject.getUserName(mc.getUser(from));
            else {
                TLRPC.Chat c = mc.getChat(-from);
                who = c != null ? c.title : title;
            }
            sb.append('[').append(df.format(new Date(m.date * 1000L))).append("] ").append(who).append(": ")
                    .append(media(m)).append(m.message == null ? "" : m.message).append('\n');
        }
        try {
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            String safe = title.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_");
            if (safe.length() > 40) safe = safe.substring(0, 40);
            File f = new File(dir, "Переписка_" + safe + ".txt");
            try (FileOutputStream os = new FileOutputStream(f)) {
                os.write(sb.toString().getBytes("UTF-8"));
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(a, ApplicationLoader.getApplicationId() + ".provider", f);
            a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Сохранить переписку"));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(a, "Не получилось сохранить файл", Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================ уведомления по ключевым словам

    public static boolean keywordsOn() { return flag("x_kw", false); }

    public static String keywords() { return prefs().getString("x_kw_words", ""); }

    public static int keywordsCount() {
        String k = keywords().trim();
        return k.isEmpty() ? 0 : k.split("\\s*,\\s*").length;
    }

    public static void editKeywords(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        EditText in = new EditText(a);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        in.setText(keywords());
        in.setHint("Например: Маша, контрольная, ДР");
        FrameLayout box = new FrameLayout(a);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(in);
        new AlertDialog.Builder(a).setTitle("🔔 Слова через запятую").setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    prefs().edit().putString("x_kw_words", in.getText().toString().trim()).putBoolean("x_kw", true).apply();
                    if (done != null) done.run();
                }).setNegativeButton("Отмена", null).show();
    }

    private static String hit(String text) {
        if (text == null || text.isEmpty()) return null;
        String low = text.toLowerCase();
        for (String w : keywords().split(",")) {
            String k = w.trim().toLowerCase();
            if (k.length() >= 2 && low.contains(k)) return w.trim();
        }
        return null;
    }

    private static int notifyId = 7700;

    /** Сообщение со словом из списка в заглушённом чате → отдельное уведомление (только на этом телефоне). */
    public static void onIncoming(int account, long did, ArrayList<MessageObject> msgs) {
        if (!keywordsOn() || msgs == null || keywords().trim().isEmpty()) return;
        try {
            MessagesController mc = MessagesController.getInstance(account);
            if (!mc.isDialogMuted(did, 0)) return; // в обычных чатах и так придёт уведомление
            int now = ConnectionsManager.getInstance(account).getCurrentTime();
            BaseFragment top = LaunchActivity.getLastFragment();
            if (top instanceof ChatActivity && ((ChatActivity) top).getDialogId() == did) return;
            for (MessageObject m : msgs) {
                if (m == null || m.isOut() || m.messageOwner == null || now - m.messageOwner.date > 300) continue;
                String w = hit(m.messageOwner.message);
                if (w == null) continue;
                String chatTitle;
                if (did > 0) chatTitle = UserObject.getUserName(mc.getUser(did));
                else {
                    TLRPC.Chat c = mc.getChat(-did);
                    chatTitle = c != null ? c.title : "Чат";
                }
                show(account, did, "🔔 «" + w + "» в " + chatTitle, m.messageOwner.message);
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void show(int account, long did, String title, String text) {
        Context ctx = ApplicationLoader.applicationContext;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String ch = "startegs_keywords";
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(ch) == null) {
            nm.createNotificationChannel(new NotificationChannel(ch, "StarTegs: ключевые слова", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent intent = new Intent(ctx, LaunchActivity.class);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(32768);
        if (did > 0) intent.putExtra("userId", did);
        else intent.putExtra("chatId", -did);
        intent.putExtra("currentAccount", account);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(ctx, ch) : new android.app.Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.notification).setContentTitle(title)
                .setContentText(text.length() > 200 ? text.substring(0, 200) + "…" : text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text.length() > 600 ? text.substring(0, 600) + "…" : text))
                .setAutoCancel(true).setContentIntent(pi);
        if (quietNow() && Build.VERSION.SDK_INT < 26) b.setDefaults(0);
        try {
            nm.notify(notifyId++, b.build());
            if (notifyId > 7800) notifyId = 7700;
        } catch (SecurityException e) {
            FileLog.e(e); // нет разрешения на уведомления
        }
    }
}
