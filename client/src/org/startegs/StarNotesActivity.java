/*
 * StarTegs — «Мои заметки»: все личные заметки к людям и чатам.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.Map;

public class StarNotesActivity extends UniversalFragment {

    private static final int ID_ITEM = 1000;
    private final ArrayList<Long> ids = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "📝 Мои заметки";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ids.clear();
        for (Map.Entry<String, ?> e : StarTools.allNotes().entrySet()) {
            try {
                long id = Long.parseLong(e.getKey());
                String text = String.valueOf(e.getValue());
                items.add(SettingsActivity.SettingCell.Factory.of(ID_ITEM + ids.size(), 0xFFF3B431, 0xFFE28A14, R.drawable.msg_edit,
                        StarTools.dialogTitle(currentAccount, id), text.length() > 120 ? text.substring(0, 120) + "…" : text.replace('\n', ' ')));
                ids.add(id);
            } catch (NumberFormatException ignore) {
            }
        }
        items.add(UItem.asShadow(ids.isEmpty()
                ? "Заметок пока нет. Добавить: профиль человека или чата → ⋮ → «Добавить заметку»."
                : "Нажмите, чтобы изменить заметку. Заметки хранятся только на этом телефоне."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= ids.size()) return;
        StarTools.editNote(this, ids.get(i), () -> {
            if (listView != null) listView.adapter.update(true);
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
