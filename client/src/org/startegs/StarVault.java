/*
 * StarTegs 4.9 — «Удалённые 2.0»: уведомление «удалил сообщение», время удаления, исключения по чатам,
 * удалённые посты каналов, копии уже загруженных медиа, восстановление в «Избранное», ответ цитатой,
 * корзина с автоочисткой, PIN на раздел, «было → стало» в истории правок.
 * Работает только с тем, что уже пришло на этот телефон. Одноразовые фото и видео не сохраняются.
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
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StarVault {

    private StarVault() {}

    public static final int OPTION_QUOTE = 9075;
    public static final int OPTION_RESTORE = 9076;
    public static final int MENU_VAULT_SKIP = 9373;
    public static final int MENU_VAULT_WATCH = 9374;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static boolean flag(String k, boolean def) {
        return prefs().getBoolean(k, def);
    }

    static void toggle(String k, boolean def) {
        prefs().edit().putBoolean(k, !flag(k, def)).apply();
    }

    // ================================================================ настройки

    /** 0 — не уведомлять, 1 — из всех чатов, 2 — только из отмеченных. */
    public static int notifyMode() { return prefs().getInt("vault_notify", 1); }

    static final String[] NOTIFY_MODES = {"не уведомлять", "из всех чатов", "только из отмеченных чатов"};

    public static boolean saveChannels() { return flag("vault_channels", true); }

    public static boolean copyMedia() { return flag("vault_media", true); }

    public static boolean pinOn() { return flag("vault_pin", false); }

    /** Дней хранения: 0 — всегда. */
    public static int keepDays() { return prefs().getInt("vault_keep", 0); }

    static final int[] KEEP = {0, 7, 30, 90};

    public static String keepTitle() {
        return keepDays() == 0 ? "всегда" : keepDays() + " дн.";
    }

    private static Set<String> set(String k) {
        return new HashSet<>(prefs().getStringSet(k, new HashSet<>()));
    }

    /** Чаты, где удалённые не сохраняются. */
    public static boolean skipped(long did) { return set("vault_skip").contains(String.valueOf(did)); }

    /** Чаты, из которых приходят уведомления (режим 2). */
    public static boolean watched(long did) { return set("vault_watch").contains(String.valueOf(did)); }

    public static String skipTitle(long did) {
        return skipped(did) ? "🗑 Снова сохранять удалённые здесь" : "🚫 Не сохранять удалённые в этом чате";
    }

    public static void toggleSkip(BaseFragment f, long did) {
        Set<String> s = set("vault_skip");
        String k = String.valueOf(did);
        boolean add = !s.remove(k);
        if (add) s.add(k);
        prefs().edit().putStringSet("vault_skip", s).apply();
        if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), add ? "В этом чате удалённые больше не сохраняются" : "Удалённые в этом чате снова сохраняются", Toast.LENGTH_SHORT).show();
    }

    public static void toggleWatch(BaseFragment f, long did) {
        Set<String> s = set("vault_watch");
        String k = String.valueOf(did);
        boolean add = !s.remove(k);
        if (add) s.add(k);
        prefs().edit().putStringSet("vault_watch", s).putInt("vault_notify", add ? 2 : notifyMode()).apply();
        if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), add ? "🔔 Буду сообщать, когда здесь удалят сообщение" : "Уведомления об удалении в этом чате выключены", Toast.LENGTH_SHORT).show();
    }

    public static int skippedCount() { return set("vault_skip").size(); }

    public static String watchTitle(long did) {
        return watched(did) ? "🔕 Не сообщать об удалении здесь" : "🔔 Сообщать, когда здесь удалят сообщение";
    }

    public static void chooseNotify(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        new AlertDialog.Builder(a).setTitle("🔔 «Удалил сообщение»").setItems(NOTIFY_MODES, (d, w) -> {
            prefs().edit().putInt("vault_notify", w).apply();
            if (done != null) done.run();
        }).show();
    }

    // ================================================================ после перехвата удаления

    static final class Snap {
        long uid;
        int mid;
        TLRPC.Message msg;
        boolean unread;
    }

    /** Читает удалённые сообщения из базы Telegram (вызывать в очереди хранилища). */
    static ArrayList<Snap> read(MessagesStorage storage, long scope, List<Integer> ids) {
        ArrayList<Snap> out = new ArrayList<>();
        for (Integer mid : ids) {
            if (mid == null) continue;
            SQLiteCursor c = null;
            try {
                c = scope != 0
                        ? storage.getDatabase().queryFinalized("SELECT uid, data FROM messages_v2 WHERE uid = " + scope + " AND mid = " + mid + " LIMIT 1")
                        : storage.getDatabase().queryFinalized("SELECT uid, data FROM messages_v2 WHERE mid = " + mid + " AND is_channel = 0 LIMIT 1");
                if (c.next()) {
                    NativeByteBuffer data = c.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (m != null) {
                            Snap s = new Snap();
                            s.uid = c.longValue(0);
                            s.mid = mid;
                            s.msg = m;
                            out.add(s);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.dispose();
            }
        }
        return out;
    }

    /** Вызывается из StarMod.interceptDelete после того, как удаление перехвачено. */
    static void onDeleted(int account, long scope, List<Integer> ids) {
        final ArrayList<Integer> copy = new ArrayList<>(ids);
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<Snap> snaps = read(storage, scope, copy);
            AndroidUtilities.runOnUIThread(() -> process(account, scope, snaps));
        });
    }

    private static void process(int account, long scope, ArrayList<Snap> snaps) {
        MessagesController mc = MessagesController.getInstance(account);
        ArrayList<Integer> drop = new ArrayList<>();
        HashMap<Long, ArrayList<Snap>> byChat = new HashMap<>();
        for (Snap s : snaps) {
            boolean channelPost = s.uid < 0 && ChatObject.isChannelAndNotMegaGroup(mc.getChat(-s.uid));
            if (skipped(s.uid) || channelPost && !saveChannels()) {
                drop.add(s.mid);
                continue;
            }
            if (copyMedia()) copyFile(account, s);
            if (!s.msg.out) { // №3: удалили раньше, чем ты прочитал
                TLRPC.Dialog dlg = mc.dialogs_dict.get(s.uid);
                if (s.msg.unread || dlg != null && s.mid > dlg.read_inbox_max_id) {
                    long sc = s.msg.peer_id != null && s.msg.peer_id.channel_id != 0 ? -s.msg.peer_id.channel_id : 0;
                    StarMod.markDeletedUnread(account, sc, s.mid);
                    s.unread = true;
                }
            }
            if (!channelPost && !s.msg.out) {
                ArrayList<Snap> l = byChat.get(s.uid);
                if (l == null) byChat.put(s.uid, l = new ArrayList<>());
                l.add(s);
            }
        }
        if (!drop.isEmpty()) reallyDelete(account, scope, drop);
        if (notifyMode() == 0) return;
        for (java.util.Map.Entry<Long, ArrayList<Snap>> e : byChat.entrySet()) {
            long did = e.getKey();
            if (notifyMode() == 2 && !watched(did)) continue;
            BaseFragment top = LaunchActivity.getLastFragment();
            if (top instanceof ChatActivity && ((ChatActivity) top).getDialogId() == did) continue; // видно и так
            ArrayList<Snap> l = e.getValue();
            Snap first = l.get(0);
            String who = sender(mc, first.msg, did);
            String chat = title(mc, did);
            String title;
            String text;
            if (l.size() >= 10) {
                title = "🗑 " + who + " " + (did > 0 ? "удалил(а) переписку" : "удалил(а) " + l.size() + " сообщений в «" + chat + "»");
                text = l.size() + " сообщений — копия сохранена в StarTegs";
            } else {
                boolean unseen = false;
                for (Snap s : l) unseen |= s.unread;
                title = "🗑 " + who + " удалил(а) " + (l.size() == 1 ? "сообщение" : l.size() + " сообщения") + (did < 0 ? " в «" + chat + "»" : "")
                        + (unseen ? " — до того, как ты прочитал(а)" : "");
                StringBuilder b = new StringBuilder();
                for (Snap s : l) {
                    if (b.length() > 0) b.append('\n');
                    b.append(preview(s.msg));
                }
                text = b.toString();
            }
            notify(account, did, title, text);
        }
    }

    static String sender(MessagesController mc, TLRPC.Message m, long did) {
        long from = MessageObject.getFromChatId(m);
        if (from == 0) from = did;
        if (from > 0) {
            TLRPC.User u = mc.getUser(from);
            return u != null ? UserObject.getFirstName(u) : "Кто-то";
        }
        TLRPC.Chat c = mc.getChat(-from);
        return c != null ? c.title : "Кто-то";
    }

    static String title(MessagesController mc, long did) {
        if (did > 0) {
            TLRPC.User u = mc.getUser(did);
            return u != null ? UserObject.getUserName(u) : "Личный чат";
        }
        TLRPC.Chat c = mc.getChat(-did);
        return c != null ? c.title : "Чат";
    }

    static String preview(TLRPC.Message m) {
        String media = "";
        if (m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty) && !(m.media instanceof TLRPC.TL_messageMediaWebPage)) {
            if (m.media.photo != null) media = "🖼 Фото ";
            else if (m.media.document != null) {
                TLRPC.Document d = m.media.document;
                media = MessageObject.isVoiceDocument(d) ? "🎤 Голосовое " : MessageObject.isRoundVideoDocument(d) ? "⭕ Кружок "
                        : MessageObject.isStickerDocument(d) ? "🌟 Стикер " : MessageObject.isVideoDocument(d) ? "🎬 Видео " : "📎 Файл ";
            } else media = "📎 ";
        }
        String t = m.message == null ? "" : m.message;
        if (t.length() > 300) t = t.substring(0, 300) + "…";
        return (media + t).trim();
    }

    private static int notifyId = 7900;

    private static void notify(int account, long did, String title, String text) {
        Context ctx = ApplicationLoader.applicationContext;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String ch = "startegs_deleted";
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(ch) == null) {
            NotificationChannel c = new NotificationChannel(ch, "StarTegs: удалённые сообщения", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(c);
        }
        Intent intent = new Intent(ctx, LaunchActivity.class);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(32768);
        if (did > 0) intent.putExtra("userId", did);
        else if (did < 0) intent.putExtra("chatId", -did);
        intent.putExtra("currentAccount", account);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(ctx, ch) : new android.app.Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.notification).setContentTitle(title).setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setDefaults(0);
        try {
            nm.notify(notifyId++, b.build());
            if (notifyId > 8000) notifyId = 7900;
        } catch (SecurityException e) {
            FileLog.e(e);
        }
    }

    // ================================================================ сводка недели (№18)

    public static boolean weeklyOn() { return flag("vault_weekly", true); }

    /** Раз в 7 дней — одно тихое уведомление: сколько удалённых сохранено за неделю. Только число. */
    static void weekly(int account) {
        if (!weeklyOn() || !saveDeleted()) return;
        long now = System.currentTimeMillis();
        long last = prefs().getLong("vault_week_" + account, 0);
        if (last == 0) {
            prefs().edit().putLong("vault_week_" + account, now).apply();
            return;
        }
        if (now - last < 7 * 86400_000L) return;
        prefs().edit().putLong("vault_week_" + account, now).apply();
        int n = StarMod.deletedSince(account, (int) (last / 1000));
        if (n <= 0) return;
        notify(account, 0, "🗑 Итоги недели", "За неделю StarTegs сохранил " + n + " " + StarCmds.plural(n, "удалённое сообщение", "удалённых сообщения", "удалённых сообщений")
                + ". Посмотреть: StarTegs → Сообщения → Все удалённые.");
    }

    private static boolean saveDeleted() {
        return StarMod.saveDeleted();
    }

    public static String weekTitle(int account) {
        int n = StarMod.deletedSince(account, (int) (System.currentTimeMillis() / 1000) - 7 * 86400);
        return n + " за 7 дней";
    }

    // ================================================================ удалить по-настоящему

    /** Убирает сообщения из базы и из открытых чатов — как сделал бы обычный Telegram. */
    static void reallyDelete(int account, long scope, ArrayList<Integer> ids) {
        if (ids.isEmpty()) return;
        StarMod.forgetDeleted(account, scope, ids);
        for (Integer mid : ids) {
            File f = mediaFile(account, scope, mid);
            if (f != null) f.delete();
        }
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messagesDeleted, ids, -scope, false);
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<Long> dialogIds = storage.markMessagesAsDeleted(scope, ids, false, true, 0, 0);
            storage.updateDialogsWithDeletedMessages(scope, -scope, ids, dialogIds);
        });
    }

    // ================================================================ копии медиа (№8)

    static File mediaDir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "startegs_deleted");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File mediaFile(int account, long scope, int mid) {
        File[] list = mediaDir().listFiles();
        if (list == null) return null;
        String prefix = account + "_" + scope + "_" + mid + ".";
        for (File f : list) if (f.getName().startsWith(prefix)) return f;
        return null;
    }

    private static void copyFile(int account, Snap s) {
        try {
            TLRPC.Message m = s.msg;
            if (m.media == null || m.media.ttl_seconds != 0) return; // одноразовые — никогда
            File src = FileLoader.getInstance(account).getPathToMessage(m);
            if (src == null || !src.exists() || src.length() == 0 || src.length() > 50L * 1024 * 1024) return;
            String name = src.getName();
            String ext = name.lastIndexOf('.') > 0 ? name.substring(name.lastIndexOf('.') + 1) : "bin";
            long scope = m.peer_id != null && m.peer_id.channel_id != 0 ? -m.peer_id.channel_id : 0;
            File dst = new File(mediaDir(), account + "_" + scope + "_" + s.mid + "." + ext);
            new Thread(() -> {
                try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }).start();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static long mediaBytes() {
        long n = 0;
        File[] list = mediaDir().listFiles();
        if (list != null) for (File f : list) n += f.length();
        return n;
    }

    public static String sizeTitle() {
        long b = mediaBytes();
        String size = b < 1024 * 1024 ? (b / 1024) + " КБ" : String.format(Locale.US, "%.1f МБ", b / 1048576f);
        return StarMod.deletedCount(UserConfig.selectedAccount) + " сообщ. · медиа " + size;
    }

    static void shareFile(Activity a, File f) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(a, ApplicationLoader.getApplicationId() + ".provider", f);
            String ext = f.getName().substring(f.getName().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(mime != null ? mime : "*/*")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Сохранить файл"));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(a, "Не получилось открыть файл", Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================ автоочистка (№14)

    public static void chooseKeep(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] t = {"Хранить всегда", "7 дней", "30 дней", "90 дней"};
        new AlertDialog.Builder(a).setTitle("🧺 Сколько хранить удалённые").setItems(t, (d, w) -> {
            prefs().edit().putInt("vault_keep", KEEP[w]).apply();
            cleanup(UserConfig.selectedAccount, true);
            if (done != null) done.run();
        }).show();
    }

    /** Раз в сутки (или сразу, если force): удаляет по-настоящему то, что хранится дольше срока. */
    static void cleanup(int account, boolean force) {
        int days = keepDays();
        if (days <= 0) return;
        long last = prefs().getLong("vault_clean_" + account, 0);
        if (!force && System.currentTimeMillis() - last < 24 * 3600_000L) return;
        prefs().edit().putLong("vault_clean_" + account, System.currentTimeMillis()).apply();
        int border = (int) (System.currentTimeMillis() / 1000) - days * 86400;
        HashMap<Long, ArrayList<Integer>> old = StarMod.deletedBefore(account, border);
        for (java.util.Map.Entry<Long, ArrayList<Integer>> e : old.entrySet()) reallyDelete(account, e.getKey(), e.getValue());
    }

    public static void clearAll(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        new AlertDialog.Builder(a).setTitle("Очистить корзину?")
                .setMessage("Все сохранённые удалённые сообщения исчезнут из чатов, как в обычном Telegram. Отменить нельзя.")
                .setPositiveButton("Очистить", (d, w) -> {
                    int account = f.getCurrentAccount();
                    HashMap<Long, ArrayList<Integer>> all = StarMod.deletedBefore(account, Integer.MAX_VALUE);
                    for (java.util.Map.Entry<Long, ArrayList<Integer>> e : all.entrySet()) reallyDelete(account, e.getKey(), e.getValue());
                    Toast.makeText(a, "🧺 Корзина очищена", Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                }).setNegativeButton("Отмена", null).show();
    }

    // ================================================================ открыть раздел (PIN, №16)

    public static void open(BaseFragment f) {
        if (pinOn()) StarLock.askPinThen(f, () -> f.presentFragment(new StarDeletedActivity()));
        else f.presentFragment(new StarDeletedActivity());
    }

    public static void togglePin(BaseFragment f, Runnable done) {
        if (!pinOn() && !StarLock.hasPin()) {
            StarLock.setupPin(f, () -> {
                if (StarLock.hasPin()) prefs().edit().putBoolean("vault_pin", true).apply();
                if (done != null) done.run();
            });
            return;
        }
        toggle("vault_pin", false);
        if (done != null) done.run();
    }

    // ================================================================ действия с удалённым (№11, №12)

    public static boolean isDeleted(int account, MessageObject m) {
        return StarMod.isDeleted(account, m);
    }

    /** Копия удалённого сообщения — себе в «Избранное». */
    public static void restore(BaseFragment f, int account, long did, TLRPC.Message m) {
        MessagesController mc = MessagesController.getInstance(account);
        String when = new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new Date(m.date * 1000L));
        String head = "🗑 Удалённое от " + sender(mc, m, did) + (did < 0 ? " в «" + title(mc, did) + "»" : "") + " · " + when;
        String body = preview(m);
        long self = UserConfig.getInstance(account).getClientUserId();
        StarSend.direct(account, SendMessagesHelper.SendMessageParams.of(head + "\n\n" + body, self, null, null, null, false, null, null, null, true, 0, 0, null, false));
        long scope = m.peer_id != null && m.peer_id.channel_id != 0 ? -m.peer_id.channel_id : 0;
        File file = mediaFile(account, scope, m.id);
        if (f != null && f.getParentActivity() != null) {
            Toast.makeText(f.getParentActivity(), file != null ? "⭐ Текст в «Избранном». Файл можно сохранить из списка удалённых" : "⭐ Сохранено в «Избранное»", Toast.LENGTH_LONG).show();
        }
    }

    /** «Ответить цитатой»: вставляет текст удалённого в поле ввода. */
    public static void quote(ChatActivity chat, TLRPC.Message m) {
        if (chat.getChatActivityEnterView() == null) return;
        String t = preview(m);
        if (t.length() > 500) t = t.substring(0, 500) + "…";
        String q = "«" + t + "» — ";
        CharSequence cur = chat.getChatActivityEnterView().getFieldText();
        chat.getChatActivityEnterView().setFieldText(TextUtils.isEmpty(cur) ? q : q + cur);
        chat.getChatActivityEnterView().openKeyboard();
    }

    // ================================================================ «было → стало» (№6, №7)

    private static List<String> words(String s) {
        ArrayList<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s+|[^\\s]+").matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** Разница по словам: удалённое зачёркнуто красным, добавленное — зелёным. */
    static CharSequence diff(String a, String b) {
        List<String> x = words(a == null ? "" : a), y = words(b == null ? "" : b);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (x.size() * (long) y.size() > 250_000) { // очень длинный текст — без разметки
            sb.append(b == null ? "" : b);
            return sb;
        }
        int[][] l = new int[x.size() + 1][y.size() + 1];
        for (int i = x.size() - 1; i >= 0; i--)
            for (int j = y.size() - 1; j >= 0; j--)
                l[i][j] = x.get(i).equals(y.get(j)) ? l[i + 1][j + 1] + 1 : Math.max(l[i + 1][j], l[i][j + 1]);
        int i = 0, j = 0;
        while (i < x.size() || j < y.size()) {
            if (i < x.size() && j < y.size() && x.get(i).equals(y.get(j))) {
                sb.append(x.get(i));
                i++;
                j++;
            } else if (j < y.size() && (i >= x.size() || l[i][j + 1] > l[i + 1][j])) {
                int st = sb.length();
                sb.append(y.get(j++));
                sb.setSpan(new ForegroundColorSpan(0xFF2EAA4F), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                int st = sb.length();
                sb.append(x.get(i++));
                sb.setSpan(new ForegroundColorSpan(0xFFE5484D), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StrikethroughSpan(), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    /** Все версии по порядку; у каждой — что изменилось по сравнению с предыдущей. */
    static CharSequence history(List<String[]> versions, String now) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String prev = null;
        int n = 0;
        ArrayList<String[]> all = new ArrayList<>(versions);
        all.add(new String[]{"сейчас", now == null ? "" : now});
        for (String[] v : all) {
            n++;
            int st = sb.length();
            sb.append(v[0].equals("сейчас") ? "✅ Сейчас" : "Версия " + n + " · " + v[0]);
            sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append('\n');
            if (prev == null) sb.append(TextUtils.isEmpty(v[1]) ? "(без текста)" : v[1]);
            else sb.append(diff(prev, v[1]));
            sb.append("\n\n");
            prev = v[1];
        }
        int st = sb.length();
        sb.append("Красным — что убрали, зелёным — что добавили.");
        sb.setSpan(new ForegroundColorSpan(Color.GRAY), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
