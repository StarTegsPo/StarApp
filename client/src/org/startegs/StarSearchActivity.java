/*
 * StarTegs 4.1 — «Поиск по смыслу» по всем чатам: «где мне скидывали адрес дачи».
 * Нейросеть превращает вопрос в несколько поисковых запросов (синонимы, формы слов),
 * мод ищет их глобальным поиском Telegram, а нейросеть выбирает самые подходящие сообщения.
 * Нажатие — переход к сообщению в чате. Premium.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

public class StarSearchActivity extends UniversalFragment {

    private static final int ID_ASK = 1;
    private static final int ID_ITEM = 1000;

    private static final class Hit {
        long dialogId;
        int mid;
        int date;
        String chat;
        String text;
        String why = "";
    }

    private String query = "";
    private String status = null;
    private boolean busy;
    private final ArrayList<Hit> hits = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "🔎 Поиск по смыслу";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_ASK, R.drawable.msg_search, query.isEmpty() ? "Что ищем?" : "«" + query + "»", busy ? "⏳" : "изменить").accent());
        items.add(UItem.asShadow(status != null ? status
                : "Опишите своими словами: «где мне скидывали адрес дачи», «кто обещал вернуть долг», «ссылка на тот рецепт пиццы». "
                + "Ищем во всех чатах через поиск Telegram, нейросеть подбирает слова и выбирает лучшие совпадения."));
        if (!hits.isEmpty()) {
            items.add(UItem.asHeader("Нашлось"));
            SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy", new Locale("ru"));
            for (int i = 0; i < hits.size(); i++) {
                Hit h = hits.get(i);
                String t = h.text.replace('\n', ' ');
                if (t.length() > 90) t = t.substring(0, 90) + "…";
                String title = "💬 " + h.chat + " · " + fmt.format(new Date(h.date * 1000L)) + "\n" + t + (h.why.isEmpty() ? "" : "\n✨ " + h.why);
                items.add(UItem.asButton(ID_ITEM + i, title, ""));
            }
            items.add(UItem.asShadow("Нажмите, чтобы открыть сообщение в чате."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ASK) {
            ask();
            return;
        }
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= hits.size()) return;
        Hit h = hits.get(i);
        Bundle args = new Bundle();
        if (h.dialogId > 0) args.putLong("user_id", h.dialogId);
        else args.putLong("chat_id", -h.dialogId);
        args.putInt("message_id", h.mid);
        presentFragment(new ChatActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void refresh() {
        if (listView != null) listView.adapter.update(true);
    }

    private void ask() {
        if (getParentActivity() == null || busy) return;
        if (!StarMod.requirePro(this, "Поиск по смыслу")) return;
        if (!StarAi.enabled()) {
            status = "Нужен ИИ-бот (ai_bot в startegs.properties).";
            refresh();
            return;
        }
        EditText input = new EditText(getParentActivity());
        input.setText(query);
        input.setHint("где мне скидывали адрес дачи");
        new AlertDialog.Builder(getParentActivity())
                .setTitle("🔎 Что ищем?")
                .setView(input)
                .setPositiveButton("Найти", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.length() < 3) return;
                    query = q.length() > 200 ? q.substring(0, 200) : q;
                    run();
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void run() {
        busy = true;
        hits.clear();
        status = "🧠 Подбираю слова для поиска…";
        refresh();
        String prompt = "Я ищу в своих чатах Telegram сообщение по описанию: «" + query + "». "
                + "Придумай 6 коротких поисковых запросов (1–2 слова каждый), которые скорее всего есть в тексте нужного сообщения: "
                + "ключевые слова, синонимы, разные формы слов, на языке описания. Ответ — только запросы, каждый с новой строки.";
        StarAi.ask(getCurrentAccount(), prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                AndroidUtilities.runOnUIThread(() -> searchAll(keywords(text)));
            }

            @Override
            public void error(String why) {
                AndroidUtilities.runOnUIThread(() -> searchAll(keywords(query)));
            }
        });
    }

    private ArrayList<String> keywords(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (text != null) {
            for (String l : text.split("[\n,;]")) {
                String k = l.replaceAll("^[\\s\\d).•*\\-«\"]+", "").replaceAll("[»\"]+$", "").trim();
                if (k.length() >= 2 && k.length() <= 40 && !out.contains(k)) out.add(k);
                if (out.size() >= 6) break;
            }
        }
        if (out.isEmpty()) out.add(query);
        return out;
    }

    private void searchAll(ArrayList<String> words) {
        status = "🔎 Ищу: " + TextUtils.join(", ", words);
        refresh();
        final LinkedHashMap<String, Hit> found = new LinkedHashMap<>();
        final int[] left = {words.size()};
        MessagesController mc = getMessagesController();
        for (String w : words) {
            TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
            req.q = w;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            req.limit = 20;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                    mc.putUsers(r.users, false);
                    mc.putChats(r.chats, false);
                    for (TLRPC.Message m : r.messages) {
                        if (m == null || TextUtils.isEmpty(m.message)) continue;
                        long did = MessageObject.getDialogId(m);
                        String key = did + ":" + m.id;
                        if (found.containsKey(key) || StarHidden.isHidden(did)) continue;
                        Hit h = new Hit();
                        h.dialogId = did;
                        h.mid = m.id;
                        h.date = m.date;
                        h.text = StarCodec.strip(m.message);
                        h.chat = chatName(did);
                        found.put(key, h);
                    }
                }
                if (--left[0] == 0) rank(new ArrayList<>(found.values()));
            }));
        }
    }

    private String chatName(long did) {
        MessagesController mc = getMessagesController();
        if (did > 0) {
            TLRPC.User u = mc.getUser(did);
            return u != null ? UserObject.getUserName(u) : "Чат";
        }
        TLRPC.Chat c = mc.getChat(-did);
        return c != null && c.title != null ? c.title : "Чат";
    }

    private void rank(ArrayList<Hit> all) {
        if (all.isEmpty()) {
            busy = false;
            status = "Ничего не нашлось 😕 Попробуйте описать иначе — другими словами или точнее.";
            refresh();
            return;
        }
        if (all.size() > 60) all = new ArrayList<>(all.subList(0, 60));
        final ArrayList<Hit> cand = all;
        status = "✨ Нейросеть выбирает лучшее из " + cand.size() + "…";
        refresh();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cand.size(); i++) {
            String t = cand.get(i).text.replace('\n', ' ');
            if (t.length() > 220) t = t.substring(0, 220) + "…";
            sb.append(i + 1).append(". [").append(cand.get(i).chat).append("] ").append(t).append('\n');
        }
        String prompt = "Я ищу сообщение по описанию: «" + query + "». Вот найденные сообщения. Выбери до 8 самых подходящих, лучшие первыми. "
                + "Формат каждой строки: номер | очень короткое пояснение (до 8 слов). Если ничего не подходит — ответь: НЕТ.\n\n" + sb;
        StarAi.ask(currentAccount, prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                AndroidUtilities.runOnUIThread(() -> finish(cand, text));
            }

            @Override
            public void error(String why) {
                AndroidUtilities.runOnUIThread(() -> finish(cand, null));
            }
        });
    }

    private void finish(ArrayList<Hit> cand, String ranking) {
        busy = false;
        hits.clear();
        if (ranking != null) {
            for (String line : ranking.split("\n")) {
                String[] p = line.split("\\|", 2);
                try {
                    int idx = Integer.parseInt(p[0].replaceAll("[^0-9]", "")) - 1;
                    if (idx < 0 || idx >= cand.size() || hits.contains(cand.get(idx))) continue;
                    Hit h = cand.get(idx);
                    h.why = p.length > 1 ? p[1].trim() : "";
                    hits.add(h);
                } catch (Exception ignore) {
                }
                if (hits.size() >= 8) break;
            }
        }
        if (hits.isEmpty()) {
            for (int i = 0; i < Math.min(10, cand.size()); i++) hits.add(cand.get(i));
            status = ranking != null && ranking.trim().toUpperCase(Locale.ROOT).startsWith("НЕТ")
                    ? "Точного совпадения нет — вот близкие сообщения:" : "Найденные сообщения:";
        } else {
            status = "Готово ✨";
        }
        StarAchieve.bump("smart_search", 1);
        refresh();
    }
}
