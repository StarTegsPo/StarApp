/*
 * StarTegs 4.3 — «Лента каналов»: свежие посты всех ваших каналов одним списком, новые сверху (№48).
 * Берём по 4 последних поста из 40 самых свежих каналов. Скрытые чаты и посты со стоп-словами
 * (StarGuard) не показываются. Нажатие — открыть пост в канале. Premium.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class StarFeedActivity extends UniversalFragment {

    private static final int ID_REFRESH = 1;
    private static final int ID_ITEM = 1000;
    private static final int CHANNELS = 40;
    private static final int PER_CHANNEL = 4;

    private static final class Post {
        long dialogId;
        int mid, date;
        String channel, text;
    }

    private final ArrayList<Post> posts = new ArrayList<>();
    private boolean loading;
    private int pending;

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return "📰 Лента каналов";
    }

    private void refresh() {
        if (listView != null) listView.adapter.update(true);
    }

    private static String preview(TLRPC.Message m) {
        String t = m.message == null ? "" : StarCodec.strip(m.message).trim();
        String kind = "";
        if (m.media instanceof TLRPC.TL_messageMediaPhoto) kind = "📷 ";
        else if (m.media instanceof TLRPC.TL_messageMediaDocument) kind = "📎 ";
        else if (m.media instanceof TLRPC.TL_messageMediaPoll) kind = "📊 ";
        t = t.replace('\n', ' ');
        if (t.length() > 160) t = t.substring(0, 160) + "…";
        return kind + (t.isEmpty() ? (kind.isEmpty() ? "Пост" : "медиа") : t);
    }

    private void load() {
        if (loading) return;
        if (!StarMod.isPro()) return;
        loading = true;
        posts.clear();
        MessagesController mc = getMessagesController();
        ArrayList<TLRPC.Dialog> channels = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null || d.id >= 0 || StarHidden.isHidden(d.id)) continue;
            TLRPC.Chat c = mc.getChat(-d.id);
            if (c != null && ChatObject.isChannelAndNotMegaGroup(c) && !c.left) channels.add(d);
        }
        Collections.sort(channels, (a, b) -> Integer.compare(b.last_message_date, a.last_message_date));
        if (channels.size() > CHANNELS) channels = new ArrayList<>(channels.subList(0, CHANNELS));
        pending = channels.size();
        if (pending == 0) {
            loading = false;
            refresh();
            return;
        }
        for (TLRPC.Dialog d : channels) {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = mc.getInputPeer(d.id);
            req.limit = PER_CHANNEL;
            final long did = d.id;
            final TLRPC.Chat c = mc.getChat(-d.id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                    for (TLRPC.Message m : r.messages) {
                        if (m == null || m instanceof TLRPC.TL_messageService || m.id <= 0) continue;
                        if (StarGuard.wordHit(m.message)) continue;
                        Post p = new Post();
                        p.dialogId = did;
                        p.mid = m.id;
                        p.date = m.date;
                        p.channel = c != null && c.title != null ? c.title : "Канал";
                        p.text = preview(m);
                        posts.add(p);
                    }
                }
                if (--pending <= 0) {
                    loading = false;
                    Collections.sort(posts, (a, b) -> Integer.compare(b.date, a.date));
                    refresh();
                }
            }));
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!StarMod.isPro()) {
            items.add(UItem.asShadow("📰 Лента собирает свежие посты всех ваших каналов в один список — как лента в Instagram. Доступна в Premium."));
            items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_premium_badge, "⭐ Открыть Premium").accent());
            return;
        }
        items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, loading ? "⏳ Загружаю ленту…" : "🔄 Обновить", posts.isEmpty() ? "" : String.valueOf(posts.size())));
        if (!loading && posts.isEmpty()) items.add(UItem.asShadow("Постов не найдено — вы не подписаны на каналы или все скрыты фильтром."));
        SimpleDateFormat today = new SimpleDateFormat("HH:mm", Locale.US), other = new SimpleDateFormat("d MMM, HH:mm", new Locale("ru"));
        long dayStart = System.currentTimeMillis() / 1000 - (System.currentTimeMillis() / 1000 % 86400);
        for (int i = 0; i < posts.size() && i < 150; i++) {
            Post p = posts.get(i);
            String when = (p.date >= dayStart ? today : other).format(new Date(p.date * 1000L));
            items.add(UItem.asButton(ID_ITEM + i, "📢 " + p.channel + " · " + when + "\n" + p.text, ""));
        }
        items.add(UItem.asShadow("Скрыть посты по словам: StarTegs → Безопасность → «Фильтр каналов»."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_REFRESH) {
            if (!StarMod.requirePro(this, "Лента каналов")) return;
            load();
            refresh();
            return;
        }
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= posts.size()) return;
        StarLife.openMessage(this, posts.get(i).dialogId, posts.get(i).mid);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
