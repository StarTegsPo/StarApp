/*
 * StarTegs 2.1 — всё, что происходит в момент отправки:
 *   • отмена отправки (сообщение уходит через несколько секунд, до этого — кнопка «Отменить»);
 *   • проверка тона нейросетью («звучит резко — смягчить?»);
 *   • голосовое как текст (записали голосом — уходит текстом);
 * и напоминания из переписки («давай завтра в 7» → «напомнить?»).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StarSend {

    private StarSend() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- настройки

    /** Секунд до отправки; 0 — отмена отправки выключена. */
    public static int undoSeconds() {
        return prefs().getInt("undo_send", 5);
    }

    static void setUndoSeconds(int s) {
        prefs().edit().putInt("undo_send", s).apply();
    }

    public static boolean toneCheck() {
        return StarAi.enabled() && prefs().getBoolean("tone_check", false);
    }

    static void toggleTone() {
        prefs().edit().putBoolean("tone_check", !prefs().getBoolean("tone_check", false)).apply();
    }

    public static boolean voiceToText() {
        return StarAi.enabled() && prefs().getBoolean("voice_text", false);
    }

    static void toggleVoiceText() {
        prefs().edit().putBoolean("voice_text", !prefs().getBoolean("voice_text", false)).apply();
    }

    public static boolean remindDetect() {
        return prefs().getBoolean("remind_detect", true);
    }

    static void toggleRemindDetect() {
        prefs().edit().putBoolean("remind_detect", !remindDetect()).apply();
    }

    // ---------------------------------------------------------------- перехват отправки

    private static final Set<SendMessagesHelper.SendMessageParams> bypass =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    /** Отправить в обход перехвата (автоответ, рассылка, повторная отправка после проверки). */
    static void direct(int account, SendMessagesHelper.SendMessageParams p) {
        bypass.add(p);
        SendMessagesHelper.getInstance(account).sendMessage(p);
    }

    private static boolean isPlainText(SendMessagesHelper.SendMessageParams p) {
        return p.message != null && !p.message.trim().isEmpty() && p.photo == null && p.document == null && p.location == null
                && p.user == null && p.game == null && p.poll == null && p.todo == null && p.invoice == null
                && p.mediaWebPage == null && p.replyMarkup == null && (p.params == null || !p.params.containsKey("bot"));
    }

    /**
     * Вызывается в начале SendMessagesHelper.sendMessage(SendMessageParams).
     * true — сообщение забрал мод (отправит сам позже или после вопроса).
     */
    public static boolean intercept(int account, SendMessagesHelper.SendMessageParams p) {
        try {
            if (p == null || bypass.remove(p)) return false;
            if (p.scheduleDate != 0 || p.retryMessageObject != null || p.quick_reply_shortcut != null) return false;
            if (Looper.myLooper() != Looper.getMainLooper()) return false;
            StarLook.sendFlash(top());
            if (p.document != null && p.path != null && voiceToText() && MessageObject.isVoiceDocument(p.document)) {
                voiceFlow(account, p);
                return true;
            }
            if (!isPlainText(p)) return false;
            if (p.peer > 0) { // ботам — сразу, без задержки и проверок
                TLRPC.User u = MessagesController.getInstance(account).getUser(p.peer);
                if (u != null && u.bot) return false;
            }
            if (toneCheck() && p.message.trim().length() >= 12) {
                toneFlow(account, p);
                return true;
            }
            if (undoSeconds() > 0) {
                undoFlow(account, p);
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private static BaseFragment top() {
        try {
            return LaunchActivity.getLastFragment();
        } catch (Exception e) {
            return null;
        }
    }

    /** Вернуть текст в поле ввода, если открыт этот же чат. */
    private static void restore(SendMessagesHelper.SendMessageParams p) {
        BaseFragment f = top();
        if (f instanceof ChatActivity && ((ChatActivity) f).getDialogId() == p.peer && ((ChatActivity) f).getChatActivityEnterView() != null) {
            CharSequence cur = ((ChatActivity) f).getChatActivityEnterView().getFieldText();
            if (TextUtils.isEmpty(cur)) ((ChatActivity) f).getChatActivityEnterView().setFieldText(p.message);
        }
    }

    private static void proceed(int account, SendMessagesHelper.SendMessageParams p) {
        if (undoSeconds() > 0 && isPlainText(p)) undoFlow(account, p);
        else direct(account, p);
    }

    private static void undoFlow(int account, SendMessagesHelper.SendMessageParams p) {
        int sec = undoSeconds();
        final boolean[] cancelled = {false};
        Runnable send = () -> {
            if (!cancelled[0]) direct(account, p);
        };
        AndroidUtilities.runOnUIThread(send, sec * 1000L);
        BaseFragment f = top();
        if (f == null) return;
        try {
            String preview = p.message.trim().replace('\n', ' ');
            if (preview.length() > 40) preview = preview.substring(0, 40) + "…";
            BulletinFactory.of(f).createSimpleBulletin(R.raw.timer_3, "Отправка через " + sec + " с: «" + preview + "»", "Отменить", sec * 1000, () -> {
                cancelled[0] = true;
                AndroidUtilities.cancelRunOnUIThread(send);
                restore(p);
            }).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void toneFlow(int account, SendMessagesHelper.SendMessageParams p) {
        final boolean[] finished = {false};
        Runnable timeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            proceed(account, p); // нейросеть не успела — отправляем как есть
        };
        AndroidUtilities.runOnUIThread(timeout, 9000);
        String prompt = "Оцени тон сообщения, которое я собираюсь отправить в Telegram. Если оно звучит грубо, резко, агрессивно, пассивно-агрессивно "
                + "или может обидеть — перепиши мягче и вежливее, сохранив смысл, язык и краткость, и ответь только новым текстом. "
                + "Если тон нормальный (в том числе шутливый или деловой) — ответь ровно: OK\n\nСообщение:\n" + p.message;
        StarAi.ask(account, prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                if (finished[0]) return;
                finished[0] = true;
                AndroidUtilities.cancelRunOnUIThread(timeout);
                String t = text == null ? "" : text.trim();
                if (t.isEmpty() || t.toUpperCase(Locale.ROOT).startsWith("OK") && t.length() < 6 || t.equals(p.message.trim())) {
                    proceed(account, p);
                    return;
                }
                BaseFragment f = top();
                if (f == null || f.getParentActivity() == null) {
                    proceed(account, p);
                    return;
                }
                if (t.length() > 4000) t = t.substring(0, 4000);
                final String softer = t;
                AlertDialog dialog = new AlertDialog.Builder(f.getParentActivity())
                        .setTitle("😬 Звучит резковато")
                        .setMessage("Можно сказать так:\n\n«" + softer + "»")
                        .setPositiveButton("Отправить мягче", (d, w) -> {
                            p.message = softer;
                            p.entities = null;
                            proceed(account, p);
                        })
                        .setNegativeButton("Как есть", (d, w) -> proceed(account, p))
                        .setNeutralButton("Изменить", (d, w) -> restore(p))
                        .create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }

            @Override
            public void error(String why) {
                if (finished[0]) return;
                finished[0] = true;
                AndroidUtilities.cancelRunOnUIThread(timeout);
                proceed(account, p);
            }
        });
    }

    private static void voiceFlow(int account, SendMessagesHelper.SendMessageParams p) {
        File f = new File(p.path);
        Context ctx = ApplicationLoader.applicationContext;
        Toast.makeText(ctx, "🎙 Превращаю голосовое в текст…", Toast.LENGTH_SHORT).show();
        StarExtra.stt(account, f, (text, err) -> {
            BaseFragment top = top();
            if (err != null || TextUtils.isEmpty(text) || top == null || top.getParentActivity() == null) {
                if (err != null) Toast.makeText(ctx, "Не удалось распознать — отправляю голосовым", Toast.LENGTH_SHORT).show();
                direct(account, p);
                return;
            }
            AlertDialog dialog = new AlertDialog.Builder(top.getParentActivity())
                    .setTitle("🎙 Отправить текстом?")
                    .setMessage(text)
                    .setPositiveButton("Текстом", (d, w) -> {
                        SendMessagesHelper.SendMessageParams t = SendMessagesHelper.SendMessageParams.of(text, p.peer, p.replyToMsg, p.replyToTopMsg, null, true, null, null, null, true, 0, 0, null, false);
                        t.quick_reply_shortcut = p.quick_reply_shortcut;
                        direct(account, t);
                    })
                    .setNegativeButton("Голосовым", (d, w) -> direct(account, p))
                    .setNeutralButton("Отмена", null)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
    }

    // ---------------------------------------------------------------- напоминания из переписки

    private static boolean inited;

    /** Подписка на новые сообщения (вызывается из DialogsActivity). */
    public static void init() {
        if (inited) return;
        inited = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final int account = a;
            NotificationCenter.getInstance(account).addObserver((id, acc, args) -> {
                try {
                    if (id != NotificationCenter.didReceiveNewMessages || args.length < 2 || !remindDetect()) return;
                    if (args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2]) return;
                    long dialogId = (Long) args[0];
                    @SuppressWarnings("unchecked") ArrayList<MessageObject> msgs = (ArrayList<MessageObject>) args[1];
                    onNew(account, dialogId, msgs);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }, NotificationCenter.didReceiveNewMessages);
        }
    }

    private static final Pattern TIME = Pattern.compile(
            "(?iu)(?<![\\p{L}\\d])(сегодня|завтра|послезавтра)?[\\s,]*(?:в|к|на)\\s+(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(утра|вечера|дня|ночи|час(?:а|ов)?|ч))?(?![\\p{L}\\d])"
                    + "(?:[\\s,]*(сегодня|завтра|послезавтра))?");

    /** Время встречи из текста или -1. label[0] — найденная фраза. */
    static int parse(String text, int nowSec, String[] label) {
        Matcher m = TIME.matcher(text);
        while (m.find()) {
            String day = m.group(1) != null ? m.group(1) : m.group(5);
            String mm = m.group(3), suffix = m.group(4);
            if (day == null && mm == null && suffix == null) continue; // «в 2 раза» — не время
            int h = Integer.parseInt(m.group(2)), min = mm != null ? Integer.parseInt(mm) : 0;
            if (h > 23 || min > 59) continue;
            String sfx = suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
            if ((sfx.equals("вечера") || sfx.equals("дня")) && h < 12) h += 12;
            else if (sfx.isEmpty() && mm == null && h >= 1 && h <= 7) h += 12; // «в 7» — скорее всего вечер
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(nowSec * 1000L);
            c.set(Calendar.HOUR_OF_DAY, h);
            c.set(Calendar.MINUTE, min);
            c.set(Calendar.SECOND, 0);
            String d = day == null ? "" : day.toLowerCase(Locale.ROOT);
            if (d.equals("завтра")) c.add(Calendar.DAY_OF_YEAR, 1);
            else if (d.equals("послезавтра")) c.add(Calendar.DAY_OF_YEAR, 2);
            int when = (int) (c.getTimeInMillis() / 1000);
            if (d.isEmpty() && when <= nowSec) when += 86400; // время уже прошло — значит завтра
            if (when <= nowSec + 300) continue;
            label[0] = m.group().trim();
            return when;
        }
        return -1;
    }

    private static void onNew(int account, long dialogId, ArrayList<MessageObject> msgs) {
        BaseFragment f = top();
        if (!(f instanceof ChatActivity) || ((ChatActivity) f).getDialogId() != dialogId || msgs == null) return;
        if (dialogId < 0) {
            TLRPC.Chat ch = MessagesController.getInstance(account).getChat(-dialogId);
            if (ch == null || ChatObject.isChannelAndNotMegaGroup(ch)) return;
        }
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        for (MessageObject m : msgs) {
            if (m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message)) continue;
            if (now - m.messageOwner.date > 120) continue;
            String[] label = {null};
            int when = parse(m.messageOwner.message, now, label);
            if (when < 0) continue;
            int remindAt = when - 1800 > now + 300 ? when - 1800 : when;
            String at = new java.text.SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new java.util.Date(when * 1000L));
            BulletinFactory.of(f).createSimpleBulletin(R.raw.timer_3, "⏰ «" + label[0] + "» — " + at,
                    "Напомнить", () -> {
                        StarExtra.remindAt(account, m, remindAt, "договорились на " + at);
                        Toast.makeText(ApplicationLoader.applicationContext, remindAt == when ? "⏰ Напомню в " + at : "⏰ Напомню за 30 минут", Toast.LENGTH_SHORT).show();
                    }).show();
            return;
        }
    }
}
