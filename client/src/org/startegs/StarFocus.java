/*
 * StarTegs 2.1 — режим «Фокус»: на время видны только выбранные чаты и уведомления только от них.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class StarFocus {

    private StarFocus() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static volatile Set<String> cache;

    private static Set<String> chats() {
        Set<String> c = cache;
        if (c == null) cache = c = new HashSet<>(prefs().getStringSet("focus_chats", new HashSet<>()));
        return c;
    }

    static long until() {
        return prefs().getLong("focus_until", 0);
    }

    public static boolean active() {
        return until() > System.currentTimeMillis() && !chats().isEmpty();
    }

    public static boolean allowed(long dialogId) {
        return chats().contains(String.valueOf(dialogId));
    }

    /** Вызывается из NotificationsController.getNotifyOverride: во время «Фокуса» остальные чаты молчат. */
    public static boolean mutes(long dialogId) {
        return active() && !allowed(dialogId);
    }

    static String statusText() {
        return active() ? "до " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(until())) : "";
    }

    private static void reload() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        }
    }

    static void start(int minutes, Set<String> ids) {
        cache = new HashSet<>(ids);
        long end = System.currentTimeMillis() + minutes * 60_000L;
        prefs().edit().putStringSet("focus_chats", new HashSet<>(ids)).putLong("focus_until", end).apply();
        reload();
        AndroidUtilities.runOnUIThread(StarFocus::reload, minutes * 60_000L + 1000);
    }

    static void stop() {
        prefs().edit().putLong("focus_until", 0).apply();
        reload();
    }

    /** Экран настройки «Фокуса». */
    public static class Screen extends UniversalFragment {

        private static final int[] MINUTES = {30, 60, 120, 180};
        private static final int ID_DUR = 100;
        private static final int ID_START = 200;
        private static final int ID_STOP = 201;
        private static final int ID_CHAT = 10000;

        private final ArrayList<Long> listed = new ArrayList<>();
        private final HashSet<String> picked = new HashSet<>(chats());
        private int minutes = prefs().getInt("focus_minutes", 60);

        @Override
        protected CharSequence getTitle() {
            return "🎯 Фокус";
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            listed.clear();
            if (active()) {
                items.add(UItem.asHeader("Фокус включён " + statusText()));
                items.add(UItem.asButton(ID_STOP, R.drawable.msg_cancel, "Выключить сейчас").red());
                items.add(UItem.asShadow("Сейчас в списке и в уведомлениях только выбранные чаты. Остальные вернутся сами после окончания."));
                return;
            }
            items.add(UItem.asHeader("Сколько длится"));
            for (int i = 0; i < MINUTES.length; i++) {
                int m = MINUTES[i];
                items.add(UItem.asRadio(ID_DUR + i, m < 60 ? m + " минут" : (m / 60) + (m == 60 ? " час" : " часа")).setChecked(minutes == m));
            }
            items.add(UItem.asButton(ID_START, R.drawable.msg_fave, picked.isEmpty() ? "Выберите чаты ниже" : "Включить «Фокус»",
                    picked.isEmpty() ? "" : String.valueOf(picked.size())).accent());
            items.add(UItem.asShadow("На это время в списке чатов останутся только выбранные, и уведомления придут только от них. "
                    + "Остальные сообщения не теряются — они просто подождут."));
            items.add(UItem.asHeader("Какие чаты оставить"));
            int n = 0;
            for (TLRPC.Dialog d : new ArrayList<>(getMessagesController().getDialogs(0))) {
                if (d == null || DialogObject.isEncryptedDialog(d.id) || StarHidden.isHidden(d.id)) continue;
                items.add(UItem.asCheck(ID_CHAT + listed.size(), StarTools.dialogTitle(currentAccount, d.id)).setChecked(picked.contains(String.valueOf(d.id))));
                listed.add(d.id);
                if (++n >= 300) break;
            }
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int id = item.id;
            if (id >= ID_CHAT) {
                int i = id - ID_CHAT;
                if (i >= listed.size()) return;
                String k = String.valueOf(listed.get(i));
                if (!picked.remove(k)) picked.add(k);
            } else if (id >= ID_DUR && id < ID_DUR + MINUTES.length) {
                minutes = MINUTES[id - ID_DUR];
                prefs().edit().putInt("focus_minutes", minutes).apply();
            } else if (id == ID_START) {
                if (picked.isEmpty()) {
                    Toast.makeText(getParentActivity(), "Отметьте хотя бы один чат", Toast.LENGTH_SHORT).show();
                    return;
                }
                start(minutes, picked);
                Toast.makeText(getParentActivity(), "🎯 Фокус включён " + statusText(), Toast.LENGTH_SHORT).show();
                finishFragment();
                return;
            } else if (id == ID_STOP) {
                stop();
            } else {
                return;
            }
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
