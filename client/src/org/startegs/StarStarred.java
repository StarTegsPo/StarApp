/*
 * StarTegs 2.1 — «Избранные сообщения»: ⭐ на любом сообщении в любом чате и один экран со всеми.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class StarStarred {

    private StarStarred() {}

    public static final int OPTION_STAR = 9005;
    private static final int MAX = 500;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_notes", Context.MODE_PRIVATE);
    }

    static JSONArray all() {
        try {
            return new JSONArray(prefs().getString("starred", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(JSONArray a) {
        prefs().edit().putString("starred", a.toString()).apply();
    }

    static int count() {
        return all().length();
    }

    private static int indexOf(JSONArray a, int account, long dialog, int mid) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("a") == account && o.optLong("d") == dialog && o.optInt("m") == mid) return i;
        }
        return -1;
    }

    public static boolean canStar(MessageObject m) {
        return m != null && m.getId() > 0 && !m.isSponsored();
    }

    public static String title(int account, MessageObject m) {
        return m != null && indexOf(all(), account, m.getDialogId(), m.getId()) >= 0 ? "Убрать из избранных" : "В избранные ⭐";
    }

    public static boolean isStarred(int account, MessageObject m) {
        return m != null && indexOf(all(), account, m.getDialogId(), m.getId()) >= 0;
    }

    /** Меню сообщения → «В избранные ⭐». */
    public static void toggle(BaseFragment fragment, MessageObject m) {
        if (!canStar(m)) return;
        int account = fragment.getCurrentAccount();
        JSONArray a = all();
        int i = indexOf(a, account, m.getDialogId(), m.getId());
        Context ctx = fragment.getParentActivity();
        if (i >= 0) {
            JSONArray out = new JSONArray();
            for (int k = 0; k < a.length(); k++) if (k != i) out.put(a.opt(k));
            save(out);
            if (ctx != null) Toast.makeText(ctx, "Убрано из избранных", Toast.LENGTH_SHORT).show();
            return;
        }
        if (a.length() >= MAX) {
            if (ctx != null) Toast.makeText(ctx, "В избранных уже " + MAX + " сообщений — уберите старые", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String text = m.messageOwner != null && !TextUtils.isEmpty(m.messageOwner.message) ? m.messageOwner.message
                    : m.isVoice() ? "🎙 Голосовое" : m.isPhoto() ? "🖼 Фото" : m.isVideo() ? "🎬 Видео" : m.isSticker() ? "Стикер" : "[медиа]";
            if (text.length() > 500) text = text.substring(0, 500) + "…";
            a.put(new JSONObject().put("a", account).put("d", m.getDialogId()).put("m", m.getId()).put("t", text)
                    .put("w", StarTools.dialogTitle(account, m.getDialogId())).put("dt", m.messageOwner != null ? m.messageOwner.date : 0)
                    .put("at", System.currentTimeMillis() / 1000));
            save(a);
            if (ctx != null) Toast.makeText(ctx, "⭐ В избранных. Все — на экране StarTegs", Toast.LENGTH_SHORT).show();
        } catch (Exception ignore) {
        }
    }

    /** Экран со всеми избранными сообщениями (новые отметки сверху). */
    public static class Screen extends UniversalFragment {

        private static final int ID_ITEM = 1000;
        private final ArrayList<JSONObject> shown = new ArrayList<>();

        @Override
        protected CharSequence getTitle() {
            return "⭐ Избранные сообщения";
        }

        @Override
        public void onResume() {
            super.onResume();
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            shown.clear();
            JSONArray a = all();
            SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy", new Locale("ru"));
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String t = o.optString("t").replace('\n', ' ');
                if (t.length() > 90) t = t.substring(0, 90) + "…";
                long dt = o.optLong("dt");
                items.add(UItem.asButton(ID_ITEM + shown.size(), R.drawable.msg_fave, t,
                        o.optString("w") + (dt > 0 ? " · " + f.format(new Date(dt * 1000)) : "")));
                shown.add(o);
            }
            items.add(UItem.asShadow(shown.isEmpty()
                    ? "Здесь пока пусто. Долгое нажатие на любое сообщение → «В избранные ⭐»."
                    : "Нажмите — откроется место в чате. Долгое нажатие — убрать."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - ID_ITEM;
            if (i < 0 || i >= shown.size()) return;
            JSONObject o = shown.get(i);
            long d = o.optLong("d");
            Bundle args = new Bundle();
            if (d > 0) args.putLong("user_id", d);
            else args.putLong("chat_id", -d);
            args.putInt("message_id", o.optInt("m"));
            presentFragment(new ChatActivity(args));
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - ID_ITEM;
            if (i < 0 || i >= shown.size() || getParentActivity() == null) return false;
            JSONObject o = shown.get(i);
            new AlertDialog.Builder(getParentActivity())
                    .setTitle("Убрать из избранных?")
                    .setMessage(o.optString("t"))
                    .setPositiveButton("Убрать", (dlg, w) -> {
                        JSONArray a = all();
                        int k = indexOf(a, o.optInt("a"), o.optLong("d"), o.optInt("m"));
                        if (k >= 0) {
                            JSONArray out = new JSONArray();
                            for (int j = 0; j < a.length(); j++) if (j != k) out.put(a.opt(j));
                            save(out);
                        }
                        if (listView != null) listView.adapter.update(true);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return true;
        }
    }
}
