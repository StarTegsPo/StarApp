/*
 * StarTegs 1.9 — шаблоны быстрых ответов, сводка дня от ИИ, резервная копия настроек мода.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class StarMore {

    private StarMore() {}

    public static final int MENU_TEMPLATES = 9013;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ================================================================ шаблоны ответов

    static JSONArray templates() {
        try {
            return new JSONArray(prefs().getString("templates", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void saveTemplates(JSONArray a) {
        prefs().edit().putString("templates", a.toString()).apply();
    }

    static int templatesCount() {
        return templates().length();
    }

    /** Меню чата → «Шаблоны ответов»: вставить шаблон в поле ввода ({имя} — имя собеседника). */
    public static void pickTemplate(ChatActivity chat) {
        JSONArray a = templates();
        if (chat.getParentActivity() == null) return;
        if (a.length() == 0) {
            new AlertDialog.Builder(chat.getParentActivity())
                    .setTitle("💬 Шаблоны ответов")
                    .setMessage("Шаблонов пока нет. Создайте первый — например «Реквизиты» или «Спасибо за заказ». В тексте можно писать {имя}.")
                    .setPositiveButton("Создать", (d, w) -> editTemplate(chat, -1, null))
                    .setNegativeButton("Закрыть", null)
                    .show();
            return;
        }
        CharSequence[] items = new CharSequence[a.length() + 1];
        for (int i = 0; i < a.length(); i++) items[i] = "💬 " + a.optJSONObject(i).optString("t");
        items[a.length()] = "➕ Новый шаблон";
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("💬 Шаблоны ответов")
                .setItems(items, (d, which) -> {
                    if (which == a.length()) {
                        editTemplate(chat, -1, null);
                        return;
                    }
                    String text = a.optJSONObject(which).optString("x");
                    String name = "";
                    long dialog = chat.getDialogId();
                    if (dialog > 0) {
                        TLRPC.User u = MessagesController.getInstance(chat.getCurrentAccount()).getUser(dialog);
                        if (u != null) name = UserObject.getFirstName(u);
                    }
                    text = text.replace("{имя}", name).replace("{name}", name);
                    if (chat.getChatActivityEnterView() != null) chat.getChatActivityEnterView().setFieldText(text);
                })
                .show();
    }

    /** Создать (index = -1) или изменить шаблон. */
    static void editTemplate(BaseFragment fragment, int index, Runnable done) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        JSONArray a = templates();
        if (index < 0 && a.length() >= 50) {
            Toast.makeText(ctx, "Можно до 50 шаблонов", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject cur = index >= 0 ? a.optJSONObject(index) : null;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        EditText title = new EditText(ctx);
        title.setHint("Название, например «Реквизиты»");
        title.setSingleLine(true);
        EditText text = new EditText(ctx);
        text.setHint("Текст. {имя} заменится на имя собеседника");
        text.setMinLines(3);
        if (cur != null) {
            title.setText(cur.optString("t"));
            text.setText(cur.optString("x"));
        }
        box.addView(title);
        box.addView(text);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(index >= 0 ? "✏️ Шаблон" : "➕ Новый шаблон")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String t = title.getText().toString().trim(), x = text.getText().toString().trim();
                    if (t.isEmpty() || x.isEmpty()) {
                        Toast.makeText(ctx, "Нужны название и текст", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        JSONObject o = new JSONObject().put("t", t.length() > 60 ? t.substring(0, 60) : t).put("x", x.length() > 2000 ? x.substring(0, 2000) : x);
                        JSONArray arr = templates();
                        if (index >= 0 && index < arr.length()) arr.put(index, o);
                        else arr.put(o);
                        saveTemplates(arr);
                        if (done != null) done.run();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton("Отмена", null);
        if (index >= 0) b.setNeutralButton("Удалить", (d, w) -> {
            JSONArray arr = templates(), out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) if (i != index) out.put(arr.opt(i));
            saveTemplates(out);
            if (done != null) done.run();
        });
        b.show();
    }

    /** Экран StarTegs → «Шаблоны ответов»: список с изменением. */
    static void manageTemplates(BaseFragment fragment, Runnable done) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        JSONArray a = templates();
        CharSequence[] items = new CharSequence[a.length() + 1];
        for (int i = 0; i < a.length(); i++) items[i] = "✏️ " + a.optJSONObject(i).optString("t");
        items[a.length()] = "➕ Новый шаблон";
        new AlertDialog.Builder(ctx)
                .setTitle("💬 Шаблоны ответов")
                .setItems(items, (d, which) -> editTemplate(fragment, which == a.length() ? -1 : which, done))
                .show();
    }

    // ================================================================ сводка дня

    /** «Сводка дня»: нейросеть пересказывает переписку за последние сутки во всех личных чатах и группах. */
    static void dailySummary(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) return;
        final int account = fragment.getCurrentAccount();
        final MessagesController mc = MessagesController.getInstance(account);
        final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(mc.getDialogs(0));
        final int since = ConnectionsManager.getInstance(account).getCurrentTime() - 86400;
        Toast.makeText(fragment.getParentActivity(), "Собираю переписку за сутки…", Toast.LENGTH_SHORT).show();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            ArrayList<Object[]> chats = new ArrayList<>();
            int n = 0;
            for (TLRPC.Dialog d : dialogs) {
                if (d == null || d.id == 0 || d.last_message_date < since) continue;
                if (StarHidden.isHidden(d.id)) continue; // скрытые чаты в сводку не попадают
                ArrayList<StarTools.Msg> msgs = StarTools.readMessages(account, d.id, 25);
                chats.add(new Object[]{d.id, d.unread_count, msgs});
                if (++n >= 15) break;
            }
            AndroidUtilities.runOnUIThread(() -> {
                StringBuilder sb = new StringBuilder();
                for (Object[] c : chats) {
                    long id = (Long) c[0];
                    TLRPC.Chat ch = id < 0 ? mc.getChat(-id) : null;
                    if (ChatObjectIsBroadcast(ch)) continue; // каналы пропускаем — там нет переписки
                    @SuppressWarnings("unchecked") ArrayList<StarTools.Msg> msgs = (ArrayList<StarTools.Msg>) c[2];
                    StringBuilder part = new StringBuilder();
                    for (int i = msgs.size() - 1; i >= 0; i--) {
                        StarTools.Msg m = msgs.get(i);
                        if (m.date < since || m.text.isEmpty()) continue;
                        String who = m.out ? "Я" : StarTools.dialogTitle(account, m.from);
                        part.append("  ").append(who).append(": ").append(m.text.length() > 200 ? m.text.substring(0, 200) + "…" : m.text).append('\n');
                    }
                    if (part.length() == 0) continue;
                    sb.append("Чат «").append(StarTools.dialogTitle(account, id)).append("»")
                            .append((Integer) c[1] > 0 ? " (непрочитано " + c[1] + ")" : "").append(":\n").append(part);
                    if (sb.length() > 12000) break;
                }
                if (sb.length() == 0) {
                    Toast.makeText(fragment.getParentActivity(), "За сутки не было переписки", Toast.LENGTH_SHORT).show();
                    return;
                }
                StarAi.streamDialog(fragment, "☀️ Сводка дня",
                        "Сделай сводку моей переписки за сутки: по каждому чату 1–2 строки — главное; отдельно выдели, кому я не ответил и что важно сделать. "
                                + "Коротко, списком, по-русски.\n\n" + sb, null, null);
            });
        });
    }

    private static boolean ChatObjectIsBroadcast(TLRPC.Chat ch) {
        return ch != null && org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(ch);
    }

    // ================================================================ резервная копия настроек

    private static final String[] PREFS = {"startegs_mod", "startegs_lock", "startegs_notes", "startegs_labels"};

    /** Все настройки мода одной строкой JSON (без PIN-кода: его хэш не переносится). */
    static String exportJson() throws Exception {
        JSONObject root = new JSONObject().put("startegs", StarModConfig.MOD_VERSION);
        for (String name : PREFS) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                String k = e.getKey();
                if (k.startsWith("pin_") || k.equals("tier") || k.equals("tier_at") || k.startsWith("update_")) continue;
                Object v = e.getValue();
                if (v instanceof Set) {
                    JSONArray arr = new JSONArray();
                    for (Object x : (Set<?>) v) arr.put(String.valueOf(x));
                    o.put(k, new JSONObject().put("set", arr));
                } else {
                    o.put(k, new JSONObject().put(v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Boolean ? "b" : v instanceof Float ? "f" : "s", v));
                }
            }
            root.put(name, o);
        }
        return root.toString();
    }

    static int importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!root.has("startegs")) throw new IllegalArgumentException("Это не копия настроек StarTegs");
        int n = 0;
        for (String name : PREFS) {
            JSONObject o = root.optJSONObject(name);
            if (o == null) continue;
            SharedPreferences.Editor ed = ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                if (k.startsWith("pin_")) continue;
                JSONObject v = o.optJSONObject(k);
                if (v == null) continue;
                if (v.has("set")) {
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    JSONArray arr = v.getJSONArray("set");
                    for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                    ed.putStringSet(k, set);
                } else if (v.has("i")) ed.putInt(k, v.getInt("i"));
                else if (v.has("l")) ed.putLong(k, v.getLong("l"));
                else if (v.has("b")) ed.putBoolean(k, v.getBoolean("b"));
                else if (v.has("f")) ed.putFloat(k, (float) v.getDouble("f"));
                else if (v.has("s")) ed.putString(k, v.getString("s"));
                n++;
            }
            ed.apply();
        }
        return n;
    }

    static void backup(BaseFragment fragment) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        new AlertDialog.Builder(ctx)
                .setTitle("💾 Резервная копия StarTegs")
                .setMessage("Заметки, метки, шаблоны, фильтры, скрытые и закрытые чаты, оформление. PIN-код не переносится — на новом телефоне задайте его заново.\n\n"
                        + "Копия отправляется текстом: сохраните её в «Избранное». Чтобы восстановить — скопируйте текст копии и нажмите «Восстановить».")
                .setPositiveButton("Сохранить копию", (d, w) -> {
                    try {
                        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, exportJson());
                        ctx.startActivity(android.content.Intent.createChooser(send, "Сохранить копию StarTegs"));
                    } catch (Exception e) {
                        FileLog.e(e);
                        Toast.makeText(ctx, "Не удалось создать копию", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Восстановить", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        CharSequence clip = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0 ? cm.getPrimaryClip().getItemAt(0).coerceToText(ctx) : null;
                        if (TextUtils.isEmpty(clip)) {
                            Toast.makeText(ctx, "Сначала скопируйте текст копии", Toast.LENGTH_LONG).show();
                            return;
                        }
                        int n = importJson(clip.toString());
                        Toast.makeText(ctx, "Восстановлено настроек: " + n + ". Перезапустите приложение.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(ctx, "Не похоже на копию StarTegs", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }
}
