/*
 * StarTegs — «Метки»: все чаты с метками, сгруппированные по меткам.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

public class StarLabelsActivity extends UniversalFragment {

    private static final int ID_ITEM = 1000;
    private final ArrayList<Long> ids = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "🏷 Метки";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ids.clear();
        int[][] colors = {{0, 0}, {0xFF3CC96A, 0xFF1FA052}, {0xFFF5C33B, 0xFFE09A12}, {0xFF3EA6F5, 0xFF1F7FD6},
                {0xFFB06CF5, 0xFF8A45E0}, {0xFFF55B5B, 0xFFD63A3A}, {0xFFB7BCC6, 0xFF8E949F}};
        for (int l = 1; l < StarExtra.LABELS.length; l++) {
            ArrayList<Long> chats = StarExtra.withLabel(l);
            if (chats.isEmpty()) continue;
            items.add(UItem.asHeader(StarExtra.LABELS[l][0] + " " + StarExtra.LABELS[l][1] + " · " + chats.size()));
            for (long id : chats) {
                String note = StarTools.note(id);
                items.add(SettingsActivity.SettingCell.Factory.of(ID_ITEM + ids.size(), colors[l][0], colors[l][1], R.drawable.msg_folders,
                        StarTools.dialogTitle(currentAccount, id), note.isEmpty() ? null : "📝 " + note.replace('\n', ' ')));
                ids.add(id);
            }
        }
        items.add(UItem.asShadow(ids.isEmpty()
                ? "Меток пока нет. Поставить: профиль чата → ⋮ → «Поставить метку». Метка видна перед именем в списке чатов."
                : "Метка видна перед именем чата в списке. Сменить: профиль чата → ⋮ → «Метка»."));
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
