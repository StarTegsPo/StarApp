/*
 * StarTegs — «Статистика переписки»: кто сколько пишет, когда вы общаетесь, частые
 * слова. Считается по сообщениям, загруженным в приложение. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public class StarStatsActivity extends UniversalFragment {

    private final long dialogId;
    private boolean loading = true;
    private int total, mine, media, firstDate, lastDate;
    private final int[] hours = new int[24];
    private final int[] weekdays = new int[7];
    private final ArrayList<Map.Entry<Long, Integer>> senders = new ArrayList<>();
    private final ArrayList<Map.Entry<String, Integer>> words = new ArrayList<>();

    private static final HashSet<String> STOP = new HashSet<>(java.util.Arrays.asList(
            "это", "что", "как", "так", "вот", "уже", "тоже", "если", "есть", "было", "будет", "только", "когда", "меня", "тебя",
            "тебе", "мне", "его", "она", "они", "оно", "мой", "твой", "всё", "все", "еще", "ещё", "нет", "да", "для", "или", "там",
            "тут", "где", "кто", "чем", "чтобы", "потом", "сейчас", "просто", "очень", "можно", "надо", "нужно", "этот", "эта",
            "this", "that", "with", "have", "what", "just", "your", "there", "they", "from", "will", "would"));

    public StarStatsActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected CharSequence getTitle() {
        return "📊 " + StarTools.dialogTitle(currentAccount, dialogId);
    }

    @Override
    public boolean onFragmentCreate() {
        final int account = currentAccount;
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            ArrayList<StarTools.Msg> list = StarTools.readMessages(account, dialogId, 50000);
            HashMap<Long, Integer> bySender = new HashMap<>();
            HashMap<String, Integer> byWord = new HashMap<>();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int t = 0, my = 0, md = 0, first = Integer.MAX_VALUE, last = 0;
            for (StarTools.Msg m : list) {
                t++;
                if (m.out) my++;
                if (m.media) md++;
                first = Math.min(first, m.date);
                last = Math.max(last, m.date);
                cal.setTimeInMillis((long) m.date * 1000);
                hours[cal.get(java.util.Calendar.HOUR_OF_DAY)]++;
                weekdays[(cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7]++; // пн = 0
                Long key = m.out ? Long.valueOf(0) : Long.valueOf(m.from);
                Integer c = bySender.get(key);
                bySender.put(key, c == null ? 1 : c + 1);
                if (!m.text.isEmpty()) {
                    for (String w : m.text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
                        if (w.length() < 4 || STOP.contains(w)) continue;
                        Integer n = byWord.get(w);
                        byWord.put(w, n == null ? 1 : n + 1);
                    }
                }
            }
            ArrayList<Map.Entry<Long, Integer>> s = new ArrayList<>(bySender.entrySet());
            Collections.sort(s, (a, b) -> b.getValue() - a.getValue());
            ArrayList<Map.Entry<String, Integer>> w = new ArrayList<>(byWord.entrySet());
            Collections.sort(w, (a, b) -> b.getValue() - a.getValue());
            final int ft = t, fm = my, fmd = md, ff = first, fl = last;
            AndroidUtilities.runOnUIThread(() -> {
                total = ft;
                mine = fm;
                media = fmd;
                firstDate = ff == Integer.MAX_VALUE ? 0 : ff;
                lastDate = fl;
                senders.clear();
                senders.addAll(s.subList(0, Math.min(10, s.size())));
                words.clear();
                words.addAll(w.subList(0, Math.min(15, w.size())));
                loading = false;
                if (listView != null) listView.adapter.update(true);
            });
        });
        return super.onFragmentCreate();
    }

    private static String bar(int v, int max) {
        int n = max == 0 ? 0 : Math.round(12f * v / max);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('▇');
        return sb.toString();
    }

    private static String pct(int v, int total) {
        return total == 0 ? "0%" : Math.round(100f * v / total) + "%";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow("Считаю…"));
            return;
        }
        if (total == 0) {
            items.add(UItem.asShadow("В приложении нет сообщений этого чата. Пролистайте переписку вверх, чтобы она загрузилась, и откройте статистику снова."));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        int days = Math.max(1, (lastDate - firstDate) / 86400 + 1);
        items.add(UItem.asHeader("Всего"));
        items.add(UItem.asButton(1, "Сообщений", String.valueOf(total)));
        items.add(UItem.asButton(2, "Моих", mine + " · " + pct(mine, total)));
        items.add(UItem.asButton(3, "С фото, видео и файлами", String.valueOf(media)));
        items.add(UItem.asButton(4, "В среднем в день", String.valueOf(Math.round((float) total / days))));
        items.add(UItem.asShadow("С " + f.format(new Date((long) firstDate * 1000)) + " по " + f.format(new Date((long) lastDate * 1000))
                + ". Считается по сообщениям, загруженным в приложение."));

        MessagesController mc = MessagesController.getInstance(currentAccount);
        items.add(UItem.asHeader("Кто пишет"));
        int id = 100;
        for (Map.Entry<Long, Integer> e : senders) {
            long from = e.getKey();
            String name;
            if (from == 0) name = "Я";
            else if (from > 0) {
                TLRPC.User u = mc.getUser(from);
                name = u != null ? UserObject.getUserName(u) : "Пользователь " + from;
            } else {
                TLRPC.Chat c = mc.getChat(-from);
                name = c != null ? c.title : "Канал";
            }
            items.add(UItem.asButton(id++, name, e.getValue() + " · " + pct(e.getValue(), total)));
        }
        items.add(UItem.asShadow(null));

        int maxH = 0;
        for (int h : hours) maxH = Math.max(maxH, h);
        items.add(UItem.asHeader("Когда вы общаетесь"));
        for (int h = 0; h < 24; h += 3) {
            int v = hours[h] + hours[h + 1] + hours[h + 2];
            items.add(UItem.asButton(200 + h, String.format(Locale.US, "%02d–%02d  %s", h, h + 3, bar(v, maxH * 3)), String.valueOf(v)));
        }
        String[] wd = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        int maxD = 0;
        for (int d : weekdays) maxD = Math.max(maxD, d);
        for (int d = 0; d < 7; d++) {
            items.add(UItem.asButton(300 + d, wd[d] + "  " + bar(weekdays[d], maxD), String.valueOf(weekdays[d])));
        }
        items.add(UItem.asShadow(null));

        if (!words.isEmpty()) {
            items.add(UItem.asHeader("Частые слова"));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : words) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(e.getKey()).append(" ").append(e.getValue());
            }
            items.add(UItem.asShadow(sb.toString()));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
