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
    @Override
    public boolean isLightStatusBar() {
        return !StarSilk.barsOn() && super.isLightStatusBar();
    }


    private static final int ID_ITEM = 1000, ID_KEEP = 1, ID_CLEAR = 2, ID_TYPE = 3, ID_CHAT = 4, ID_PERIOD = 5, ID_EXPORT = 6;
    private static final String[] TYPES = {"Все", "💬 Текст", "🖼 Фото", "🎬 Видео", "🎤 Голосовые и кружки", "📎 Файлы", "🌟 Стикеры"};
    private static final String[] PERIODS = {"За всё время", "Сегодня", "За неделю", "За месяц"};
    private int typeFilter, periodFilter;
    private long chatFilter;
    private static final int LIMIT = 300;

    private static final class Entry {
        long dialogId;
        int mid;
        int date;
        String text;
        String title;
        long scope;
        int deletedAt;
        boolean unread;
        TLRPC.Message msg;
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
        StarSilk.decorate(this, StarSilk.RED, listView);
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
                                e.text = StarVault.preview(m);
                                if (e.text.isEmpty()) e.text = "Сообщение";
                                e.scope = scope;
                                e.msg = m;
                                e.deletedAt = StarMod.deletedTime(account, scope, mid);
                                e.unread = StarMod.deletedUnreadKey(account, scope, mid);
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
                    e.title = StarVault.title(mc, e.dialogId);
                    if (e.dialogId < 0 && !e.msg.out) e.title = StarVault.sender(mc, e.msg, e.dialogId) + " в «" + e.title + "»";
                    else if (e.msg.out) e.title = "Ты · " + e.title;
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
        items.add(UItem.asButton(ID_KEEP, R.drawable.msg_delete, "🧺 Хранить удалённые", StarVault.keepTitle()));
        items.add(UItem.asButton(ID_CLEAR, "Очистить корзину", StarVault.sizeTitle()).red());
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Фильтры · " + StarVault.weekTitle(currentAccount)));
        items.add(UItem.asButton(ID_TYPE, "Тип", TYPES[typeFilter]));
        items.add(UItem.asButton(ID_CHAT, "Чат", chatFilter == 0 ? "все чаты" : chatName(chatFilter)));
        items.add(UItem.asButton(ID_PERIOD, "Период", PERIODS[periodFilter]));
        items.add(UItem.asButton(ID_EXPORT, R.drawable.msg_download, "📄 Сохранить показанные в .txt", ""));
        items.add(UItem.asShadow(null));
        if (loading) {
            items.add(UItem.asShadow("Загрузка…"));
            return;
        }
        int shown = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!matches(e)) continue;
            int t = e.deletedAt > 0 ? e.deletedAt : e.date;
            String when = (e.deletedAt > 0 ? "удалено " : "") + LocaleController.getInstance().getFormatterDayMonth().format((long) t * 1000) + ", "
                    + LocaleController.getInstance().getFormatterDay().format((long) t * 1000)
                    + (StarVault.mediaFile(currentAccount, e.scope, e.mid) != null ? " · 📎" : "")
                    + (e.unread ? " · не прочитано" : "");
            String snippet = e.text.length() > 120 ? e.text.substring(0, 120) + "…" : e.text;
            items.add(SettingsActivity.SettingCell.Factory.of(ID_ITEM + i, 0xFFF45255, 0xFFDF3955, R.drawable.msg_delete,
                    e.title + " · " + when, snippet.replace('\n', ' ')));
            shown++;
        }
        if (entries.isEmpty()) {
            if (getParentActivity() != null) items.add(UItem.asCustom(StarGlow.empty(getParentActivity(), "🗑", "Пока ничего не удалено", "Когда кто-то удалит сообщение, оно появится здесь — с текстом и временем удаления.")));
        } else if (shown == 0) {
            items.add(UItem.asShadow("Ничего не найдено"));
        } else {
            items.add(UItem.asShadow("Показаны последние " + Math.min(entries.size(), LIMIT) + ". Нажмите — перейти, восстановить, ответить цитатой или сохранить файл."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Runnable refresh = () -> {
            if (listView != null) listView.adapter.update(true);
        };
        if (item.id == ID_TYPE) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity()).setTitle("Тип").setItems(TYPES, (d, w) -> {
                typeFilter = w;
                refresh.run();
            }).show();
            return;
        }
        if (item.id == ID_PERIOD) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity()).setTitle("Период").setItems(PERIODS, (d, w) -> {
                periodFilter = w;
                refresh.run();
            }).show();
            return;
        }
        if (item.id == ID_CHAT) {
            java.util.LinkedHashMap<Long, String> chats = new java.util.LinkedHashMap<>();
            for (Entry e : entries) if (!chats.containsKey(e.dialogId)) chats.put(e.dialogId, chatName(e.dialogId));
            ArrayList<Long> ids = new ArrayList<>(chats.keySet());
            CharSequence[] names = new CharSequence[ids.size() + 1];
            names[0] = "Все чаты";
            for (int k = 0; k < ids.size(); k++) names[k + 1] = chats.get(ids.get(k));
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity()).setTitle("Чат").setItems(names, (d, w) -> {
                chatFilter = w == 0 ? 0 : ids.get(w - 1);
                refresh.run();
            }).show();
            return;
        }
        if (item.id == ID_EXPORT) {
            export();
            return;
        }
        if (item.id == ID_KEEP) {
            StarVault.chooseKeep(this, this::load);
            return;
        }
        if (item.id == ID_CLEAR) {
            StarVault.clearAll(this, () -> {
                entries.clear();
                refresh.run();
            });
            return;
        }
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= entries.size() || getParentActivity() == null) return;
        Entry e = entries.get(i);
        java.io.File file = StarVault.mediaFile(currentAccount, e.scope, e.mid);
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> acts = new ArrayList<>();
        labels.add("💬 Перейти к сообщению");
        acts.add(() -> presentFragment(chatFor(e)));
        labels.add("⭐ Восстановить в «Избранное»");
        acts.add(() -> StarVault.restore(this, currentAccount, e.dialogId, e.msg));
        labels.add("↩️ Ответить цитатой");
        acts.add(() -> {
            ChatActivity chat = chatFor(e);
            presentFragment(chat);
            AndroidUtilities.runOnUIThread(() -> StarVault.quote(chat, e.msg), 700);
        });
        if (file != null) {
            labels.add("📥 Сохранить или отправить файл");
            acts.add(() -> StarVault.shareFile(getParentActivity(), file));
        }
        labels.add("🗑 Удалить навсегда");
        acts.add(() -> {
            ArrayList<Integer> ids = new ArrayList<>();
            ids.add(e.mid);
            StarVault.reallyDelete(currentAccount, e.scope, ids);
            entries.remove(e);
            refresh.run();
        });
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity()).setTitle(e.title)
                .setItems(labels.toArray(new CharSequence[0]), (d, w) -> acts.get(w).run()).show();
    }

    private String chatName(long did) {
        return StarVault.title(MessagesController.getInstance(currentAccount), did);
    }

    private static int type(TLRPC.Message m) {
        if (m.media == null || m.media instanceof TLRPC.TL_messageMediaEmpty || m.media instanceof TLRPC.TL_messageMediaWebPage) return 1;
        if (m.media.photo != null) return 2;
        if (m.media.document != null) {
            TLRPC.Document d = m.media.document;
            if (org.telegram.messenger.MessageObject.isVoiceDocument(d) || org.telegram.messenger.MessageObject.isRoundVideoDocument(d)) return 4;
            if (org.telegram.messenger.MessageObject.isStickerDocument(d) || org.telegram.messenger.MessageObject.isAnimatedStickerDocument(d, true)) return 6;
            if (org.telegram.messenger.MessageObject.isVideoDocument(d)) return 3;
            return 5;
        }
        return 5;
    }

    private boolean matches(Entry e) {
        if (!query.isEmpty() && !(e.text.toLowerCase(Locale.ROOT).contains(query) || (e.title != null && e.title.toLowerCase(Locale.ROOT).contains(query)))) return false;
        if (typeFilter != 0 && type(e.msg) != typeFilter) return false;
        if (chatFilter != 0 && e.dialogId != chatFilter) return false;
        if (periodFilter != 0) {
            int t = e.deletedAt > 0 ? e.deletedAt : e.date;
            long now = System.currentTimeMillis() / 1000;
            long border;
            if (periodFilter == 1) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(java.util.Calendar.HOUR_OF_DAY, 0);
                c.set(java.util.Calendar.MINUTE, 0);
                c.set(java.util.Calendar.SECOND, 0);
                border = c.getTimeInMillis() / 1000;
            } else border = now - (periodFilter == 2 ? 7 : 30) * 86400L;
            if (t < border) return false;
        }
        return true;
    }

    /** №13: показанные (с учётом фильтров и поиска) — в .txt. */
    private void export() {
        if (getParentActivity() == null) return;
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder("Удалённые сообщения — сохранено в StarTegs " + df.format(new java.util.Date()) + "\n\n");
        int n = 0;
        for (Entry e : entries) {
            if (!matches(e)) continue;
            n++;
            sb.append('[').append(df.format(new java.util.Date(e.date * 1000L))).append("] ").append(e.title);
            if (e.deletedAt > 0) sb.append(" · удалено ").append(df.format(new java.util.Date(e.deletedAt * 1000L)));
            if (e.unread) sb.append(" · не прочитано");
            sb.append("\n").append(e.text).append("\n\n");
        }
        if (n == 0) {
            android.widget.Toast.makeText(getParentActivity(), "Нечего сохранять", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.File dir = new java.io.File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "Удалённые_StarTegs.txt");
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
                os.write(sb.toString().getBytes("UTF-8"));
            }
            StarVault.shareFile(getParentActivity(), f);
        } catch (Exception ex) {
            FileLog.e(ex);
        }
    }

    private static ChatActivity chatFor(Entry e) {
        Bundle args = new Bundle();
        if (e.dialogId > 0) args.putLong("user_id", e.dialogId);
        else args.putLong("chat_id", -e.dialogId);
        args.putInt("message_id", e.mid);
        return new ChatActivity(args);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
