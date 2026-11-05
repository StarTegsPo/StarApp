/*
 * StarTegs — экран «Удалённые»: все сохранённые модом удалённые сообщения из всех
 * чатов, с поиском и переходом к сообщению. Лицензия: GNU GPL v2 или новее.
 *
 * Мод не стирает удалённые сообщения из базы Telegram (см. StarMod.interceptDelete),
 * поэтому их текст читается прямо из messages_v2, а в своей базе мод хранит только
 * список того, что было удалено.
 */
package org.startegs;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StarDeletedActivity extends UniversalFragment {

    private static final int ID_ITEM = 1000;
    private static final int LIMIT = 300;

    private static final class Entry {
        long dialogId;
        int mid;
        int date;
        String text;
        String title;
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private boolean loading = true;
    private String query = "";

    @Override
    protected CharSequence getTitle() {
        return "🗑 Удалённые";
    }

    @Override
    public View createView(Context context) {
        View v = super.createView(context);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem search = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true);
        search.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onTextChanged(EditText editText) {
                query = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                if (listView != null) listView.adapter.update(true);
            }

            @Override
            public void onSearchCollapse() {
                query = "";
                if (listView != null) listView.adapter.update(true);
            }
        });
        search.setSearchFieldHint("Поиск по удалённым");
        load();
        return v;
    }

    private void load() {
        final int account = currentAccount;
        final List<long[]> keys = StarMod.deletedKeys(account, LIMIT);
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<Entry> out = new ArrayList<>();
            for (long[] k : keys) {
                long scope = k[0];
                int mid = (int) k[1];
                SQLiteCursor c = null;
                try {
                    // scope != 0 — канал (-channel_id), иначе личный чат или обычная группа
                    c = scope != 0
                            ? storage.getDatabase().queryFinalized("SELECT uid, data FROM messages_v2 WHERE uid = " + scope + " AND mid = " + mid + " LIMIT 1")
                            : storage.getDatabase().queryFinalized("SELECT uid, data FROM messages_v2 WHERE mid = " + mid + " AND is_channel = 0 LIMIT 1");
                    if (c.next()) {
                        NativeByteBuffer data = c.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            if (m != null) {
                                Entry e = new Entry();
                                e.dialogId = c.longValue(0);
                                e.mid = mid;
                                e.date = m.date;
                                e.text = !TextUtils.isEmpty(m.message) ? m.message : m.media != null ? "📎 Медиа" : "Сообщение";
                                out.add(e);
                            }
                        }
                    }
                } catch (Exception ex) {
                    FileLog.e(ex);
                } finally {
                    if (c != null) c.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController mc = MessagesController.getInstance(account);
                for (Entry e : out) {
                    if (e.dialogId > 0) {
                        TLRPC.User u = mc.getUser(e.dialogId);
                        e.title = u != null ? UserObject.getUserName(u) : "Личный чат";
                    } else {
                        TLRPC.Chat ch = mc.getChat(-e.dialogId);
                        e.title = ch != null ? ch.title : "Группа или канал";
                    }
                }
                entries.clear();
                entries.addAll(out);
                loading = false;
                if (listView != null) listView.adapter.update(true);
            });
        });
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow("Загрузка…"));
            return;
        }
        int shown = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!query.isEmpty() && !(e.text.toLowerCase(Locale.ROOT).contains(query) || (e.title != null && e.title.toLowerCase(Locale.ROOT).contains(query)))) {
                continue;
            }
            String when = LocaleController.getInstance().getFormatterDayMonth().format((long) e.date * 1000) + ", "
                    + LocaleController.getInstance().getFormatterDay().format((long) e.date * 1000);
            String snippet = e.text.length() > 120 ? e.text.substring(0, 120) + "…" : e.text;
            items.add(SettingsActivity.SettingCell.Factory.of(ID_ITEM + i, 0xFFF45255, 0xFFDF3955, R.drawable.msg_delete,
                    e.title + " · " + when, snippet.replace('\n', ' ')));
            shown++;
        }
        if (entries.isEmpty()) {
            items.add(UItem.asShadow("Пока ничего не удалено. Когда кто-то удалит сообщение, оно появится здесь."));
        } else if (shown == 0) {
            items.add(UItem.asShadow("Ничего не найдено"));
        } else {
            items.add(UItem.asShadow("Показаны последние " + Math.min(entries.size(), LIMIT) + ". Нажмите, чтобы перейти к сообщению в чате."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= entries.size()) return;
        Entry e = entries.get(i);
        Bundle args = new Bundle();
        if (e.dialogId > 0) args.putLong("user_id", e.dialogId);
        else args.putLong("chat_id", -e.dialogId);
        args.putInt("message_id", e.mid);
        presentFragment(new ChatActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
