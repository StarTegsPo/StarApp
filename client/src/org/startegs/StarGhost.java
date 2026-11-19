/*
 * StarTegs 4.9 — режим призрака 2.0: расписание, автовыключение, готовые наборы, призрак для
 * отдельных чатов, «прочитать при ответе», ручное «прочитать», без «прослушано», предупреждение
 * перед отправкой, «не в сети» сразу после отправки, кнопка над чатами и плитка в шторке.
 * Всё касается только того, что видят о тебе. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class StarGhost {

    private StarGhost() {}

    public static final int MENU_GHOST_CHAT = 9370;
    public static final int MENU_READ_NOW = 9371;
    public static final int MENU_GHOST_BUTTON = 9372;

    static final String[] KEYS = {"ghost_typing", "ghost_read", "ghost_online", "ghost_stories"};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static boolean flag(String k, boolean def) {
        return prefs().getBoolean(k, def);
    }

    static void toggle(String k, boolean def) {
        prefs().edit().putBoolean(k, !flag(k, def)).apply();
    }

    // ================================================================ включено ли

    /** Ручной переключатель, расписание и автовыключение вместе. Только Premium (как и раньше). */
    static boolean active(String key) {
        if (!StarMod.isPro()) return false;
        long until = prefs().getLong("ghost_until", 0);
        if (until > 0 && System.currentTimeMillis() > until) {
            SharedPreferences.Editor e = prefs().edit().putLong("ghost_until", 0);
            for (String k : KEYS) e.putBoolean(k, false);
            e.apply();
            AndroidUtilities.runOnUIThread(() -> {
                refreshIndicator();
                Toast.makeText(ApplicationLoader.applicationContext, "👻 Призрак выключился по таймеру", Toast.LENGTH_SHORT).show();
            });
            return false;
        }
        return prefs().getBoolean(key, false) || scheduledNow();
    }

    public static boolean any() {
        for (String k : KEYS) if (active(k)) return true;
        return false;
    }

    // ================================================================ расписание (№19)

    public static boolean scheduleOn() { return flag("ghost_sched", false); }

    public static int schedFrom() { return prefs().getInt("ghost_sched_from", 8); }

    public static int schedTo() { return prefs().getInt("ghost_sched_to", 15); }

    public static String schedTitle() {
        return String.format(Locale.US, "%02d:00–%02d:00", schedFrom(), schedTo());
    }

    static boolean scheduledNow() {
        if (!scheduleOn()) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), a = schedFrom(), b = schedTo();
        return a == b || (a < b ? h >= a && h < b : h >= a || h < b);
    }

    public static void chooseSchedule(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.US, "%02d:00", i);
        new AlertDialog.Builder(a).setTitle("👻 Призрак с…").setItems(hours, (d, from) ->
                new AlertDialog.Builder(a).setTitle("…и до").setItems(hours, (d2, to) -> {
                    prefs().edit().putInt("ghost_sched_from", from).putInt("ghost_sched_to", to).putBoolean("ghost_sched", true).apply();
                    if (done != null) done.run();
                }).show()).show();
    }

    // ================================================================ автовыключение (№28)

    static final int[] AUTO_OFF = {0, 1, 3, 8};

    public static int autoOffHours() { return prefs().getInt("ghost_auto_h", 0); }

    public static String autoOffTitle() {
        int h = autoOffHours();
        if (h == 0) return "не выключать";
        long until = prefs().getLong("ghost_until", 0);
        String left = until > System.currentTimeMillis() ? " · осталось " + Math.max(1, (until - System.currentTimeMillis()) / 60000) + " мин" : "";
        return "через " + h + " ч" + left;
    }

    public static void chooseAutoOff(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] t = {"Не выключать", "Через 1 час", "Через 3 часа", "Через 8 часов"};
        new AlertDialog.Builder(a).setTitle("⏱ Выключать призрака").setItems(t, (d, w) -> {
            prefs().edit().putInt("ghost_auto_h", AUTO_OFF[w]).apply();
            if (anyManual()) arm();
            if (done != null) done.run();
        }).show();
    }

    private static boolean anyManual() {
        for (String k : KEYS) if (prefs().getBoolean(k, false)) return true;
        return false;
    }

    /** Вызывать после ручного включения: запускает таймер автовыключения. */
    static void arm() {
        int h = autoOffHours();
        prefs().edit().putLong("ghost_until", h > 0 && anyManual() ? System.currentTimeMillis() + h * 3600_000L : 0).apply();
        refreshIndicator();
    }

    // ================================================================ наборы (№27)

    static final String[] PRESETS = {"🫥 Полный невидимка", "⌨️ Только не печатаю", "📖 Без «прочитано»", "🌙 Не в сети", "👀 Тайно смотрю истории", "❌ Выключить всё"};
    private static final boolean[][] PRESET_KEYS = {
            {true, true, true, true}, {true, false, false, false}, {false, true, false, false},
            {false, false, true, false}, {false, false, false, true}, {false, false, false, false}};

    public static void choosePreset(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        if (!StarMod.requirePro(f, "Режим призрака")) return;
        new AlertDialog.Builder(a).setTitle("👻 Набор призрака").setItems(PRESETS, (d, w) -> {
            SharedPreferences.Editor e = prefs().edit();
            for (int i = 0; i < KEYS.length; i++) e.putBoolean(KEYS[i], PRESET_KEYS[w][i]);
            e.apply();
            arm();
            if (done != null) done.run();
        }).show();
    }

    // ================================================================ призрак для отдельных чатов (№20)

    /** 0 — как везде, 1 — призрак ТОЛЬКО в отмеченных чатах (плюс общий), 2 — отмеченные чаты — исключения. */
    public static int chatMode() { return prefs().getInt("ghost_chat_mode", 1); }

    static final String[] CHAT_MODES = {"не использовать", "невидим в отмеченных чатах", "отмеченные — исключения"};

    private static Set<String> chats() {
        return new HashSet<>(prefs().getStringSet("ghost_chats", new HashSet<>()));
    }

    public static boolean inList(long did) {
        return chats().contains(String.valueOf(did));
    }

    public static int listSize() {
        return chats().size();
    }

    public static String chatMenuTitle(long did) {
        if (chatMode() == 2) return inList(did) ? "👻 Снова скрываться в этом чате" : "👁 Не скрываться в этом чате";
        return inList(did) ? "👻 Призрак в этом чате: выключить" : "👻 Призрак в этом чате";
    }

    public static void toggleChat(BaseFragment f, long did) {
        if (!StarMod.requirePro(f, "Призрак для чата")) return;
        Set<String> s = chats();
        String k = String.valueOf(did);
        boolean add = !s.remove(k);
        if (add) s.add(k);
        SharedPreferences.Editor e = prefs().edit().putStringSet("ghost_chats", s);
        if (chatMode() == 0) e.putInt("ghost_chat_mode", 1);
        e.apply();
        if (f.getParentActivity() != null) {
            String msg = chatMode() == 2
                    ? (add ? "👁 В этом чате тебя видно как обычно" : "👻 В этом чате снова работает призрак")
                    : (add ? "👻 Здесь не видно «печатает» и «прочитано»" : "Призрак в этом чате выключен");
            Toast.makeText(f.getParentActivity(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    public static void chooseChatMode(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        new AlertDialog.Builder(a).setTitle("👻 Призрак по чатам").setItems(CHAT_MODES, (d, w) -> {
            prefs().edit().putInt("ghost_chat_mode", w).apply();
            if (done != null) done.run();
        }).show();
    }

    /** null — список не влияет, иначе решение для этого чата. */
    private static Boolean listDecision(long did) {
        int m = chatMode();
        if (m == 0 || !StarMod.isPro()) return null;
        boolean in = inList(did);
        if (m == 1) return in ? Boolean.TRUE : null;
        return in ? Boolean.FALSE : null;
    }

    // ================================================================ решения для патчей

    /** Не отправлять «печатает», «записывает голосовое», «выбирает стикер». */
    public static boolean typing(long did) {
        Boolean d = listDecision(did);
        return d != null ? d : active("ghost_typing");
    }

    private static final HashMap<Long, Long> readAllowedUntil = new HashMap<>();

    /** Не отправлять «прочитано» в этот чат. */
    public static boolean read(long did) {
        Long until = readAllowedUntil.get(did);
        if (until != null && until > System.currentTimeMillis()) return false;
        Boolean d = listDecision(did);
        if (d != null) return d;
        return active("ghost_read") && did > 0;
    }

    /** Не отправлять «прослушано» для голосовых и кружков (одноразовые — всегда как обычно). */
    public static boolean noListen(MessageObject m) {
        if (!flag("ghost_listen", false) || !StarMod.isPro() || m == null || m.messageOwner == null || m.isOut()) return false;
        if (m.messageOwner.media != null && m.messageOwner.media.ttl_seconds != 0) return false;
        return m.isVoice() || m.isRoundVideo();
    }

    // ================================================================ прочитать вручную / при ответе (№21, №22)

    public static boolean readOnReply() { return flag("ghost_read_reply", true); }

    /** Отмечает чат прочитанным на сервере, даже если включён призрак. */
    public static void markRead(int account, long did) {
        try {
            MessagesController mc = MessagesController.getInstance(account);
            TLRPC.Dialog dialog = mc.dialogs_dict.get(did);
            if (dialog == null || dialog.top_message == 0) return;
            readAllowedUntil.put(did, System.currentTimeMillis() + 3000);
            if (did < 0) {
                TLRPC.Chat ch = mc.getChat(-did);
                if (ChatObject.isChannel(ch)) {
                    TLRPC.TL_channels_readHistory req = new TLRPC.TL_channels_readHistory();
                    req.channel = mc.getInputChannel(-did);
                    req.max_id = dialog.top_message;
                    ConnectionsManager.getInstance(account).sendRequest(req, null);
                    return;
                }
            }
            TLRPC.TL_messages_readHistory req = new TLRPC.TL_messages_readHistory();
            req.peer = mc.getInputPeer(did);
            req.max_id = dialog.top_message;
            ConnectionsManager.getInstance(account).sendRequest(req, null);
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    public static void readNow(ChatActivity chat) {
        markRead(chat.getCurrentAccount(), chat.getDialogId());
        if (chat.getParentActivity() != null) Toast.makeText(chat.getParentActivity(), "👁 Отмечено прочитанным", Toast.LENGTH_SHORT).show();
    }

    // ================================================================ отправка (№21, №29, №30)

    public static boolean warnOn() { return flag("ghost_warn", false); }

    public static boolean offlineAfter() { return flag("ghost_offline_after", true); }

    private static long confirmedUntil;

    /** true — показали предупреждение, отправка продолжится после «Отправить». */
    static boolean confirmSend(int account, SendMessagesHelper.SendMessageParams p) {
        if (!warnOn() || !active("ghost_online") || System.currentTimeMillis() < confirmedUntil) return false;
        BaseFragment f = LaunchActivity.getLastFragment();
        if (f == null || f.getParentActivity() == null) return false;
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("👻 Ты в режиме призрака")
                .setMessage("После отправки собеседник может увидеть, что ты был(а) в сети только что. Отправить?")
                .setPositiveButton("Отправить", (d, w) -> {
                    confirmedUntil = System.currentTimeMillis() + 10 * 60_000L;
                    SendMessagesHelper.getInstance(account).sendMessage(p);
                })
                .setNegativeButton("Отмена", (d, w) -> StarSend.restore(p))
                .setOnCancelListener(d -> StarSend.restore(p))
                .show();
        return true;
    }

    /** После отправки: «прочитать при ответе» и снова «не в сети». */
    static void afterSend(int account, long did) {
        if (did != 0 && readOnReply() && read(did)) markRead(account, did);
        if (offlineAfter() && active("ghost_online")) {
            Runnable off = () -> {
                TL_account.updateStatus req = new TL_account.updateStatus();
                req.offline = true;
                ConnectionsManager.getInstance(account).sendRequest(req, null);
            };
            AndroidUtilities.runOnUIThread(off, 1500);
            AndroidUtilities.runOnUIThread(off, 6000);
        }
    }

    // ================================================================ кнопка над чатами и плитка (№25, №26)

    public static boolean buttonOn() { return flag("ghost_button", true) && StarMod.isPro(); }

    private static WeakReference<ActionBarMenuItem> indicator;

    public static void attachIndicator(ActionBarMenuItem item) {
        indicator = new WeakReference<>(item);
        refreshIndicator();
    }

    static void refreshIndicator() {
        ActionBarMenuItem i = indicator != null ? indicator.get() : null;
        if (i != null) i.setAlpha(any() ? 1f : 0.35f);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                StarGhostTile.requestUpdate();
            } catch (Throwable ignore) {
            }
        }
    }

    /** Нажатие на 👻 над чатами или плитку в шторке. */
    public static void quickToggle(Context ctx) {
        if (!StarMod.isPro()) {
            Toast.makeText(ctx, "👻 Режим призрака — в StarTegs Premium", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean on = !StarSections.ghostAll();
        StarSections.setGhostAll(on);
        arm();
        Toast.makeText(ctx, on ? "👻 Призрак включён" : "👻 Призрак выключен", Toast.LENGTH_SHORT).show();
    }

    static int account() {
        return UserConfig.selectedAccount;
    }
}
