/*
 * StarTegs — скрытые чаты: пропадают из списка чатов и открываются только через
 * экран «Скрытые чаты» по PIN-коду. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class StarHidden {

    private StarHidden() {}

    private static volatile Set<Long> cache;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_lock", Context.MODE_PRIVATE);
    }

    private static Set<Long> hidden() {
        Set<Long> c = cache;
        if (c == null) {
            c = new HashSet<>();
            for (String s : prefs().getStringSet("hidden", new HashSet<>())) {
                try {
                    c.add(Long.parseLong(s));
                } catch (NumberFormatException ignore) {
                }
            }
            cache = c;
        }
        return c;
    }

    public static boolean isHidden(long dialogId) {
        return dialogId != 0 && hidden().contains(dialogId);
    }

    public static ArrayList<Long> list() {
        return new ArrayList<>(hidden());
    }

    public static int count() {
        return hidden().size();
    }

    private static void save(Set<Long> s) {
        Set<String> out = new HashSet<>();
        for (Long id : s) out.add(String.valueOf(id));
        prefs().edit().putStringSet("hidden", out).apply();
        cache = null;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload, true);
    }

    /** Пункт профиля: скрыть чат из списка или вернуть обратно. */
    public static void toggle(BaseFragment fragment, long dialogId) {
        if (dialogId == 0) return;
        Set<Long> s = new HashSet<>(hidden());
        if (s.contains(dialogId)) {
            s.remove(dialogId);
            save(s);
            Toast.makeText(fragment.getParentActivity(), "Чат снова в списке", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!StarMod.requirePro(fragment, "Скрытые чаты")) return;
        Runnable hide = () -> {
            Set<Long> s2 = new HashSet<>(hidden());
            s2.add(dialogId);
            save(s2);
            Toast.makeText(fragment.getParentActivity(), "🙈 Чат скрыт. Открыть: ⭐ StarTegs → «Скрытые чаты»", Toast.LENGTH_LONG).show();
        };
        if (StarLock.hasPin()) hide.run(); else StarLock.setupPin(fragment, hide);
    }

    /**
     * Вызывается из DialogsActivity.getDialogsArray: убирает скрытые чаты из списка.
     * Если скрытых нет, возвращает тот же список без копирования.
     */
    public static ArrayList<TLRPC.Dialog> filter(ArrayList<TLRPC.Dialog> dialogs) {
        Set<Long> h = hidden();
        boolean decoy = StarPrivacy.decoy(), focus = StarFocus.active();
        if (dialogs == null || h.isEmpty() && !decoy && !focus) return dialogs;
        ArrayList<TLRPC.Dialog> out = new ArrayList<>(dialogs.size());
        for (TLRPC.Dialog d : dialogs) {
            if (d == null) continue;
            if (h.contains(d.id)) continue;
            if (decoy && StarLock.isLocked(d.id)) continue; // фальшивый код: закрытых чатов «нет»
            if (focus && !StarFocus.allowed(d.id)) continue; // режим «Фокус»
            out.add(d);
        }
        return out;
    }
}
