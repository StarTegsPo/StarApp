/*
 * StarTegs — экран «Скрытые чаты». Открывается только после ввода PIN-кода.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

public class StarHiddenActivity extends UniversalFragment {

    private static final int ID_ITEM = 1000;
    private final ArrayList<Long> ids = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "🙈 Скрытые чаты";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ids.clear();
        ids.addAll(StarHidden.list());
        MessagesController mc = MessagesController.getInstance(currentAccount);
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            String title;
            if (id > 0) {
                TLRPC.User u = mc.getUser(id);
                title = u != null ? UserObject.getUserName(u) : "Чат " + id;
            } else {
                TLRPC.Chat c = mc.getChat(-id);
                title = c != null ? c.title : "Чат " + id;
            }
            items.add(SettingsActivity.SettingCell.Factory.of(ID_ITEM + i, 0xFF5A5F7A, 0xFF3A3F5A, R.drawable.msg_permissions,
                    title, StarLock.isLocked(id) ? "🔒 скрыт и закрыт PIN-кодом" : "скрыт из списка чатов"));
        }
        items.add(UItem.asShadow(ids.isEmpty()
                ? "Скрытых чатов нет. Скрыть чат: профиль чата → ⋮ → «Скрыть чат из списка»."
                : "Чтобы вернуть чат в общий список: профиль чата → ⋮ → «Вернуть чат в список». Уведомления от скрытых чатов приходят как обычно."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= ids.size()) return;
        long id = ids.get(i);
        Bundle args = new Bundle();
        if (id > 0) args.putLong("user_id", id);
        else args.putLong("chat_id", -id);
        presentFragment(new ChatActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
