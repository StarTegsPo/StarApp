/*
 * StarTegs 2.0 — «Рассылка»: одно сообщение в выбранные вами чаты от вашего аккаунта.
 *
 * Отправка идёт отложенными сообщениями Telegram с интервалом: их отправляет сервер Telegram,
 * даже если телефон выключен, а отменить любое можно в «Отложенных» этого чата.
 * Интервал и лимит чатов защищают аккаунт от блокировки за спам.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class StarBroadcastActivity extends UniversalFragment {

    static final int MAX_CHATS = 30;
    private static final int[] INTERVALS = {1, 3, 5, 10, 30}; // минуты

    private static final int ID_TEXT = 100;
    private static final int ID_TEMPLATE = 101;
    private static final int ID_INTERVAL = 200;   // 200.. — интервалы
    private static final int ID_NAME = 300;
    private static final int ID_SEND = 400;
    private static final int ID_CLEAR = 401;
    private static final int ID_SHOW = 500;       // 500.. — фильтр списка
    private static final int ID_CHAT = 10000;     // 10000.. — чаты

    private final ArrayList<Long> listed = new ArrayList<>();

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static String text() {
        return prefs().getString("bc_text", "");
    }

    private static Set<String> selected() {
        return new HashSet<>(prefs().getStringSet("bc_chats", new HashSet<>()));
    }

    private static int interval() {
        return prefs().getInt("bc_interval", 3);
    }

    private static boolean useName() {
        return prefs().getBoolean("bc_name", true);
    }

    private static int show() {
        return prefs().getInt("bc_show", 0); // 0 группы, 1 личные, 2 каналы
    }

    @Override
    protected CharSequence getTitle() {
        return "📣 Рассылка";
    }

    /** Куда можно писать: группы и каналы с правом отправки, люди (не боты), без секретных чатов. */
    private int kind(TLRPC.Dialog d) {
        if (d == null || DialogObject.isEncryptedDialog(d.id) || StarHidden.isHidden(d.id)) return -1;
        MessagesController mc = getMessagesController();
        if (d.id > 0) {
            TLRPC.User u = mc.getUser(d.id);
            if (u == null || u.bot || u.deleted || UserObject.isReplyUser(u) || UserObject.isService(u.id)) return -1;
            return 1;
        }
        TLRPC.Chat c = mc.getChat(-d.id);
        if (c == null || ChatObject.isNotInChat(c) || !ChatObject.canSendPlain(c)) return -1;
        return ChatObject.isChannelAndNotMegaGroup(c) ? 2 : 0;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        listed.clear();
        String t = text();
        Set<String> sel = selected();

        items.add(UItem.asHeader("Сообщение"));
        items.add(UItem.asButton(ID_TEXT, R.drawable.msg_edit, t.isEmpty() ? "Написать текст" : "Изменить текст",
                t.isEmpty() ? "" : String.valueOf(t.length())));
        if (StarMore.templatesCount() > 0) items.add(UItem.asButton(ID_TEMPLATE, R.drawable.msg_list, "Взять из шаблона"));
        items.add(UItem.asCheck(ID_NAME, "Подставлять {имя} в личных чатах").setChecked(useName()));
        items.add(UItem.asShadow(t.isEmpty() ? "Текст уйдёт от вашего имени, как будто вы написали его сами."
                : "«" + (t.length() > 300 ? t.substring(0, 300) + "…" : t) + "»"));

        items.add(UItem.asHeader("Интервал между чатами"));
        for (int i = 0; i < INTERVALS.length; i++) {
            items.add(UItem.asRadio(ID_INTERVAL + i, INTERVALS[i] + " мин" + (INTERVALS[i] == 3 ? " — рекомендуется" : ""))
                    .setChecked(interval() == INTERVALS[i]));
        }
        items.add(UItem.asShadow("К интервалу добавляется случайный разброс, чтобы рассылка не выглядела как спам. "
                + "Чаще, чем раз в минуту, Telegram может ограничить аккаунт."));

        items.add(UItem.asButton(ID_SEND, R.drawable.msg_send, sel.isEmpty() || t.isEmpty() ? "Выберите текст и чаты"
                : "Запустить рассылку", sel.isEmpty() ? "" : sel.size() + " из " + MAX_CHATS));
        if (!sel.isEmpty()) items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_delete, "Снять выбор со всех чатов"));
        items.add(UItem.asShadow("Сообщения ставятся в «Отложенные» каждого чата и уходят с серверов Telegram по расписанию — "
                + "даже если телефон выключен. Передумали — откройте чат и удалите отложенное."));

        items.add(UItem.asHeader("Куда отправить"));
        String[] tabs = {"Группы", "Личные", "Каналы"};
        for (int i = 0; i < tabs.length; i++) items.add(UItem.asRadio(ID_SHOW + i, tabs[i]).setChecked(show() == i));
        int shown = 0;
        for (TLRPC.Dialog d : new ArrayList<>(getMessagesController().getDialogs(0))) {
            if (kind(d) != show()) continue;
            items.add(UItem.asCheck(ID_CHAT + listed.size(), StarTools.dialogTitle(currentAccount, d.id))
                    .setChecked(sel.contains(String.valueOf(d.id))));
            listed.add(d.id);
            if (++shown >= 300) break;
        }
        items.add(UItem.asShadow(shown == 0 ? "Здесь нет чатов, куда можно написать."
                : "Показаны чаты, где вы можете писать. Секретные, скрытые чаты и боты не показываются. За раз — до " + MAX_CHATS + " чатов."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_CHAT) {
            int i = id - ID_CHAT;
            if (i >= listed.size()) return;
            Set<String> s = selected();
            String key = String.valueOf(listed.get(i));
            if (!s.remove(key)) {
                if (s.size() >= MAX_CHATS) {
                    Toast.makeText(getParentActivity(), "За один раз — не больше " + MAX_CHATS + " чатов", Toast.LENGTH_SHORT).show();
                    return;
                }
                s.add(key);
            }
            prefs().edit().putStringSet("bc_chats", s).apply();
        } else if (id >= ID_SHOW && id < ID_SHOW + 3) {
            prefs().edit().putInt("bc_show", id - ID_SHOW).apply();
        } else if (id >= ID_INTERVAL && id < ID_INTERVAL + INTERVALS.length) {
            prefs().edit().putInt("bc_interval", INTERVALS[id - ID_INTERVAL]).apply();
        } else if (id == ID_NAME) {
            prefs().edit().putBoolean("bc_name", !useName()).apply();
        } else if (id == ID_CLEAR) {
            prefs().edit().remove("bc_chats").apply();
        } else if (id == ID_TEXT) {
            editText();
            return;
        } else if (id == ID_TEMPLATE) {
            pickTemplate();
            return;
        } else if (id == ID_SEND) {
            confirm();
            return;
        } else {
            return;
        }
        if (listView != null) listView.adapter.update(true);
    }

    private void refresh() {
        if (listView != null) listView.adapter.update(true);
    }

    private void editText() {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setText(text());
        input.setMinLines(4);
        input.setHint("Текст рассылки. {имя} — имя собеседника");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle("📣 Текст рассылки")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (s.length() > 4000) s = s.substring(0, 4000);
                    prefs().edit().putString("bc_text", s).apply();
                    refresh();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickTemplate() {
        JSONArray a = StarMore.templates();
        CharSequence[] names = new CharSequence[a.length()];
        for (int i = 0; i < a.length(); i++) names[i] = "💬 " + a.optJSONObject(i).optString("t");
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Шаблон для рассылки")
                .setItems(names, (d, which) -> {
                    prefs().edit().putString("bc_text", a.optJSONObject(which).optString("x")).apply();
                    refresh();
                })
                .show();
    }

    private void confirm() {
        String t = text();
        Set<String> sel = selected();
        if (t.isEmpty() || sel.isEmpty()) {
            Toast.makeText(getParentActivity(), t.isEmpty() ? "Сначала напишите текст" : "Выберите хотя бы один чат", Toast.LENGTH_SHORT).show();
            return;
        }
        int minutes = (sel.size() - 1) * interval();
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Запустить рассылку?")
                .setMessage("Чатов: " + sel.size() + "\nИнтервал: ~" + interval() + " мин\n"
                        + "Последнее сообщение уйдёт примерно через " + (minutes < 60 ? minutes + " мин" : (minutes / 60) + " ч " + (minutes % 60) + " мин")
                        + ".\n\nСообщения уходят от вашего аккаунта. Пишите только тем, кто ждёт ваших сообщений: на жалобы «спам» Telegram ограничивает аккаунт.")
                .setPositiveButton("Запустить", (d, w) -> start(t, sel))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void start(String text, Set<String> sel) {
        MessagesController mc = getMessagesController();
        Random rnd = new Random();
        int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        int when = now + 60;
        int sent = 0;
        for (String key : sel) {
            long dialogId;
            try {
                dialogId = Long.parseLong(key);
            } catch (NumberFormatException e) {
                continue;
            }
            String msg = text;
            if (dialogId > 0) {
                TLRPC.User u = mc.getUser(dialogId);
                String name = u != null && useName() ? UserObject.getFirstName(u) : "";
                msg = msg.replace("{имя}", name).replace("{name}", name);
            } else {
                msg = msg.replace(", {имя}", "").replace("{имя}", "").replace("{name}", "");
            }
            SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(msg.trim(), dialogId);
            p.scheduleDate = when;
            SendMessagesHelper.getInstance(currentAccount).sendMessage(p);
            sent++;
            int base = interval() * 60;
            when += base + rnd.nextInt(Math.max(1, base / 3)) - base / 6; // ±~15–30% разброса
        }
        prefs().edit().remove("bc_chats").apply();
        String end = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date((long) (when - interval() * 60) * 1000L));
        Toast.makeText(getParentActivity(), "📣 Рассылка запущена: " + sent + " чатов, последнее ≈ в " + end, Toast.LENGTH_LONG).show();
        refresh();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
