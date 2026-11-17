/*
 * StarTegs 4.3 — продуктивность:
 *   • «В дела» из сообщения с дедлайном (напоминание приходит в «Избранное») — №54;
 *   • «Прочитать позже» для сообщений и постов — №46;
 *   • «Капсула времени»: сообщение откроется другу через месяц–год (отложенное сообщение Telegram) — №62;
 *   • «События из чата»: нейросеть находит договорённости и даты в переписке — №55;
 *   • история черновиков: вернуть стёртый текст из поля ввода — №42;
 *   • экранное время в Telegram с дневной целью — №57.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StarLife {

    private StarLife() {}

    public static final int OPTION_TODO = 9050;
    public static final int OPTION_LATER = 9051;
    public static final int MENU_EVENTS = 9340;
    public static final int MENU_CAPSULE = 9341;
    public static final int MENU_DRAFTS = 9342;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static JSONArray arr(String key) {
        try {
            return new JSONArray(prefs().getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(String key, JSONArray a) {
        prefs().edit().putString(key, a.toString()).apply();
    }

    private static String snippet(MessageObject m) {
        String t = m != null && m.messageOwner != null && m.messageOwner.message != null ? StarCodec.strip(m.messageOwner.message) : "";
        if (TextUtils.isEmpty(t)) t = m != null && m.isPhoto() ? "📷 Фото" : m != null && m.isVideo() ? "🎬 Видео" : "💬 Сообщение";
        t = t.replace('\n', ' ').trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    static String fmt(long unix) {
        return new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new Date(unix * 1000L));
    }

    /** Напоминание себе: отложенное сообщение в «Избранное» (приходит с уведомлением). */
    static void remindSelf(int account, String text, int when) {
        long self = UserConfig.getInstance(account).getClientUserId();
        SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(text, self, null, null, null, false, null, null, null, true, when, 0, null, false);
        StarSend.direct(account, p);
    }

    // ================================================================ дела

    static JSONArray todos() { return arr("life_todos"); }

    static int openTodos() {
        JSONArray a = todos();
        int n = 0;
        for (int i = 0; i < a.length(); i++) if (!a.optJSONObject(i).optBoolean("done")) n++;
        return n;
    }

    public static boolean canTodo(MessageObject m) {
        return m != null && m.messageOwner != null && m.getId() > 0;
    }

    static final String[] DUE = {"Без срока", "Сегодня в 19:00", "Завтра в 10:00", "Через 3 дня", "Через неделю"};

    static int dueTime(int which, int now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now * 1000L);
        c.set(Calendar.SECOND, 0);
        switch (which) {
            case 1:
                c.set(Calendar.HOUR_OF_DAY, 19);
                c.set(Calendar.MINUTE, 0);
                if (c.getTimeInMillis() / 1000 <= now + 300) c.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case 2:
                c.add(Calendar.DAY_OF_YEAR, 1);
                c.set(Calendar.HOUR_OF_DAY, 10);
                c.set(Calendar.MINUTE, 0);
                break;
            case 3:
                c.add(Calendar.DAY_OF_YEAR, 3);
                break;
            case 4:
                c.add(Calendar.DAY_OF_YEAR, 7);
                break;
            default:
                return 0;
        }
        return (int) (c.getTimeInMillis() / 1000);
    }

    public static void addTodo(BaseFragment f, MessageObject m) {
        if (f == null || f.getParentActivity() == null) return;
        EditText input = new EditText(f.getParentActivity());
        input.setText(m != null ? snippet(m) : "");
        input.setMinLines(2);
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("✅ В дела")
                .setView(input)
                .setPositiveButton("Дальше", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    new AlertDialog.Builder(f.getParentActivity())
                            .setTitle("Когда напомнить?")
                            .setItems(DUE, (d2, which) -> {
                                int account = f.getCurrentAccount();
                                int now = ConnectionsManager.getInstance(account).getCurrentTime();
                                int due = dueTime(which, now);
                                try {
                                    JSONObject o = new JSONObject().put("t", text).put("due", due).put("done", false).put("a", account)
                                            .put("d", m != null ? m.getDialogId() : 0).put("m", m != null ? m.getId() : 0).put("ts", now);
                                    JSONArray a = todos();
                                    a.put(o);
                                    save("life_todos", a);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                if (due > 0) remindSelf(account, "✅ Дело: " + text, due);
                                Toast.makeText(ApplicationLoader.applicationContext, due > 0 ? "✅ В делах. Напомню " + fmt(due) : "✅ Добавлено в дела", Toast.LENGTH_SHORT).show();
                            }).show();
                })
                .setNegativeButton("Отмена", null).show();
    }

    static void toggleTodo(int i) {
        JSONArray a = todos();
        JSONObject o = a.optJSONObject(i);
        if (o == null) return;
        try {
            o.put("done", !o.optBoolean("done"));
        } catch (Exception ignore) {
        }
        save("life_todos", a);
    }

    static void removeAt(String key, int i) {
        JSONArray a = arr(key), b = new JSONArray();
        for (int k = 0; k < a.length(); k++) if (k != i) b.put(a.opt(k));
        save(key, b);
    }

    static void clearDone() {
        JSONArray a = todos(), b = new JSONArray();
        for (int k = 0; k < a.length(); k++) if (!a.optJSONObject(k).optBoolean("done")) b.put(a.opt(k));
        save("life_todos", b);
    }

    // ================================================================ прочитать позже

    static JSONArray later() { return arr("life_later"); }

    private static int laterIndex(int account, MessageObject m) {
        JSONArray a = later();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("a") == account && o.optLong("d") == m.getDialogId() && o.optInt("m") == m.getId()) return i;
        }
        return -1;
    }

    public static String laterTitle(int account, MessageObject m) {
        return m != null && laterIndex(account, m) >= 0 ? "📌 Убрать из «Прочитать позже»" : "📌 Прочитать позже";
    }

    public static void toggleLater(BaseFragment f, MessageObject m) {
        if (f == null || !canTodo(m)) return;
        int account = f.getCurrentAccount();
        int i = laterIndex(account, m);
        if (i >= 0) {
            removeAt("life_later", i);
            Toast.makeText(ApplicationLoader.applicationContext, "Убрано из «Прочитать позже»", Toast.LENGTH_SHORT).show();
            return;
        }
        String chat;
        if (m.getDialogId() > 0) {
            TLRPC.User u = f.getMessagesController().getUser(m.getDialogId());
            chat = u != null ? UserObject.getUserName(u) : "Чат";
        } else {
            TLRPC.Chat c = f.getMessagesController().getChat(-m.getDialogId());
            chat = c != null && c.title != null ? c.title : "Чат";
        }
        try {
            JSONArray a = later();
            a.put(new JSONObject().put("a", account).put("d", m.getDialogId()).put("m", m.getId()).put("t", snippet(m)).put("c", chat)
                    .put("ts", System.currentTimeMillis() / 1000));
            save("life_later", a);
        } catch (Exception e) {
            FileLog.e(e);
        }
        Toast.makeText(ApplicationLoader.applicationContext, "📌 Отложено — StarTegs → Продуктивность", Toast.LENGTH_SHORT).show();
    }

    static void openMessage(BaseFragment f, long dialogId, int mid) {
        Bundle args = new Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else args.putLong("chat_id", -dialogId);
        if (mid > 0) args.putInt("message_id", mid);
        f.presentFragment(new ChatActivity(args));
    }

    // ================================================================ капсула времени

    static final String[] CAPSULE = {"Через месяц", "Через 3 месяца", "Через полгода", "Через год"};
    static final int[] CAPSULE_DAYS = {30, 91, 182, 364};

    public static void capsule(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null) return;
        EditText input = new EditText(chat.getParentActivity());
        input.setHint("Что сказать себе или другу в будущем?");
        input.setMinLines(3);
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("⏳ Капсула времени")
                .setMessage("Сообщение уйдёт в этот чат в выбранный день — его отправит сервер Telegram, даже если телефон выключен.")
                .setView(input)
                .setPositiveButton("Дальше", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    new AlertDialog.Builder(chat.getParentActivity())
                            .setTitle("Когда открыть?")
                            .setItems(CAPSULE, (d2, which) -> {
                                int account = chat.getCurrentAccount();
                                int now = ConnectionsManager.getInstance(account).getCurrentTime();
                                int when = now + CAPSULE_DAYS[which] * 86400;
                                String body = "⏳ Капсула времени от " + new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date(now * 1000L)) + ":\n\n" + text;
                                SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(body, chat.getDialogId(), null, null, null, false, null, null, null, true, when, 0, null, false);
                                StarSend.direct(account, p);
                                StarAchieve.bump("capsule", 1);
                                Toast.makeText(ApplicationLoader.applicationContext, "⏳ Капсула откроется " + fmt(when) + " (видно в «Отложенных»)", Toast.LENGTH_LONG).show();
                            }).show();
                })
                .setNegativeButton("Отмена", null).show();
    }

    // ================================================================ события из чата (ИИ)

    private static final Pattern EVENT = Pattern.compile("^\\s*(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s*\\|\\s*(.+)$");

    public static void events(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null) return;
        if (!StarAi.enabled()) {
            Toast.makeText(chat.getParentActivity(), "Нужен ИИ-бот (ai_bot)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!StarMod.requirePro(chat, "События из чата")) return;
        ArrayList<MessageObject> list = chat.messages;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        if (list != null) {
            for (int i = Math.min(list.size(), 150) - 1; i >= 0; i--) {
                MessageObject m = list.get(i);
                if (m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message)) continue;
                String t = StarCodec.strip(m.messageOwner.message);
                if (t.length() > 300) t = t.substring(0, 300);
                sb.append('[').append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(m.messageOwner.date * 1000L))).append("] ")
                        .append(m.isOut() ? "Я" : "Собеседник").append(": ").append(t).append('\n');
                if (++n >= 100 || sb.length() > 9000) break;
            }
        }
        if (n == 0) {
            Toast.makeText(chat.getParentActivity(), "Нет текста для разбора", Toast.LENGTH_SHORT).show();
            return;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd HH:mm, EEEE", new Locale("ru")).format(new Date());
        String prompt = "Сейчас " + today + ". Найди в переписке будущие встречи, договорённости, дедлайны и дни рождения. "
                + "Для каждого ответь строкой в формате: ГГГГ-ММ-ДД ЧЧ:ММ | короткое описание. Если время не названо — 10:00. "
                + "Только будущие события, не больше 10. Если ничего нет — ответь: НЕТ.\n\n" + sb;
        Toast.makeText(chat.getParentActivity(), "📅 Ищу события…", Toast.LENGTH_SHORT).show();
        StarAi.ask(chat.getCurrentAccount(), prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                AndroidUtilities.runOnUIThread(() -> showEvents(chat, text));
            }

            @Override
            public void error(String why) {
                AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, "ИИ: " + why, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static void showEvents(ChatActivity chat, String text) {
        if (chat.getParentActivity() == null) return;
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<Integer> times = new ArrayList<>();
        ArrayList<String> descs = new ArrayList<>();
        SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        long now = System.currentTimeMillis() / 1000;
        if (text != null) {
            for (String line : text.split("\n")) {
                Matcher mt = EVENT.matcher(line);
                if (!mt.find()) continue;
                try {
                    Date dt = parse.parse(mt.group(1) + " " + mt.group(2));
                    if (dt == null || dt.getTime() / 1000 < now) continue;
                    int when = (int) (dt.getTime() / 1000);
                    times.add(when);
                    descs.add(mt.group(3).trim());
                    titles.add("📅 " + fmt(when) + " — " + mt.group(3).trim());
                } catch (Exception ignore) {
                }
            }
        }
        if (titles.isEmpty()) {
            new AlertDialog.Builder(chat.getParentActivity()).setTitle("📅 События из чата").setMessage("Будущих событий в загруженной переписке не нашлось.").setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("📅 Нажмите, чтобы добавить в дела")
                .setItems(titles.toArray(new CharSequence[0]), (d, which) -> {
                    int account = chat.getCurrentAccount();
                    int when = times.get(which);
                    int remind = when - 3600 > now + 300 ? when - 3600 : when;
                    try {
                        JSONArray a = todos();
                        a.put(new JSONObject().put("t", descs.get(which)).put("due", when).put("done", false).put("a", account)
                                .put("d", chat.getDialogId()).put("m", 0).put("ts", now));
                        save("life_todos", a);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    remindSelf(account, "📅 " + descs.get(which) + " — " + fmt(when), remind);
                    Toast.makeText(ApplicationLoader.applicationContext, "✅ В делах, напомню за час", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Закрыть", null).show();
    }

    // ================================================================ история черновиков

    private static final HashMap<Long, ArrayList<String>> drafts = new HashMap<>();
    private static String lastSent = "";
    private static final java.util.WeakHashMap<EditText, Boolean> watched = new java.util.WeakHashMap<>();

    /** StarSend: последний отправленный текст — его в историю черновиков не кладём. */
    static void noteSent(String text) {
        lastSent = text == null ? "" : text.trim();
    }

    private static void pushDraft(long dialog, String text) {
        String t = text.trim();
        if (t.length() < 5 || t.equals(lastSent)) return;
        ArrayList<String> l = drafts.get(dialog);
        if (l == null) drafts.put(dialog, l = new ArrayList<>());
        l.remove(t);
        l.add(0, t);
        while (l.size() > 15) l.remove(l.size() - 1);
    }

    /** Из ChatActivity.onResume: следим за полем ввода. */
    public static void attachDraft(ChatActivity chat) {
        try {
            if (chat == null || chat.getChatActivityEnterView() == null) return;
            EditText field = chat.getChatActivityEnterView().getEditField();
            if (field == null || watched.containsKey(field)) return;
            watched.put(field, Boolean.TRUE);
            final long dialog = chat.getDialogId();
            field.addTextChangedListener(new TextWatcher() {
                String before = "";

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    before = s == null ? "" : s.toString();
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before2, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String now = s == null ? "" : s.toString();
                    final String old = before;
                    if (old.length() - now.length() >= 5) {
                        // поле очистили после отправки? проверим чуть позже, когда StarSend отметит отправленное
                        AndroidUtilities.runOnUIThread(() -> pushDraft(dialog, old), 600);
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void showDrafts(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null) return;
        ArrayList<String> l = drafts.get(chat.getDialogId());
        if (l == null || l.isEmpty()) {
            Toast.makeText(chat.getParentActivity(), "Стёртых черновиков пока нет (хранятся, пока приложение открыто)", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] items = new CharSequence[l.size()];
        for (int i = 0; i < l.size(); i++) {
            String t = l.get(i).replace('\n', ' ');
            items[i] = t.length() > 80 ? t.substring(0, 80) + "…" : t;
        }
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("📝 Вернуть стёртый текст")
                .setItems(items, (d, which) -> {
                    if (chat.getChatActivityEnterView() != null) chat.getChatActivityEnterView().setFieldText(l.get(which));
                })
                .setNegativeButton("Закрыть", null).show();
    }

    // ================================================================ экранное время

    static final int[] GOALS = {0, 30, 60, 120, 180};
    private static long resumedAt;
    private static boolean lifecycle;

    private static String dayKey(long ms) {
        return "st_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms));
    }

    static int secondsOn(long ms) {
        int s = prefs().getInt(dayKey(ms), 0);
        if (resumedAt > 0 && dayKey(ms).equals(dayKey(System.currentTimeMillis()))) s += (int) ((System.currentTimeMillis() - resumedAt) / 1000);
        return s;
    }

    static int goal() { return prefs().getInt("st_goal", 0); }

    static void setGoal(int minutes) { prefs().edit().putInt("st_goal", minutes).apply(); }

    private static void flush() {
        if (resumedAt <= 0) return;
        long now = System.currentTimeMillis();
        String k = dayKey(now);
        prefs().edit().putInt(k, prefs().getInt(k, 0) + (int) ((now - resumedAt) / 1000)).apply();
        resumedAt = now;
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            flush();
            checkGoal();
            AndroidUtilities.runOnUIThread(this, 60_000);
        }
    };

    private static void checkGoal() {
        int g = goal();
        if (g <= 0) return;
        String warned = "st_warn_" + dayKey(System.currentTimeMillis());
        if (prefs().getBoolean(warned, false)) return;
        if (secondsOn(System.currentTimeMillis()) >= g * 60) {
            prefs().edit().putBoolean(warned, true).apply();
            Toast.makeText(ApplicationLoader.applicationContext, "⏱ Сегодня в Telegram уже " + g + " мин — ваша цель на день. Может, перерыв? 🙂", Toast.LENGTH_LONG).show();
        }
    }

    static String human(int sec) {
        int h = sec / 3600, m = (sec % 3600) / 60;
        return h > 0 ? h + " ч " + m + " мин" : m + " мин";
    }

    static String weekReport() {
        StringBuilder sb = new StringBuilder();
        long day = 86400_000L, now = System.currentTimeMillis();
        int total = 0;
        SimpleDateFormat f = new SimpleDateFormat("EE, d MMM", new Locale("ru"));
        for (int i = 6; i >= 0; i--) {
            int s = secondsOn(now - i * day);
            total += s;
            sb.append(f.format(new Date(now - i * day))).append(" — ").append(human(s)).append('\n');
        }
        sb.append("\nВ среднем: ").append(human(total / 7)).append(" в день");
        return sb.toString();
    }

    /** Из StarPlus.init. */
    static void init(Activity activity) {
        if (lifecycle || activity == null) return;
        lifecycle = true;
        resumedAt = System.currentTimeMillis();
        AndroidUtilities.runOnUIThread(tick, 60_000);
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {
                if (resumedAt <= 0) {
                    resumedAt = System.currentTimeMillis();
                    AndroidUtilities.cancelRunOnUIThread(tick);
                    AndroidUtilities.runOnUIThread(tick, 60_000);
                }
            }
            @Override public void onActivityPaused(Activity a) {
                flush();
                resumedAt = 0;
                AndroidUtilities.cancelRunOnUIThread(tick);
            }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
