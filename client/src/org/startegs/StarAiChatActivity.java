/*
 * StarTegs — экран «Чат с ИИ»: диалог с @StarTegsAi_bot прямо в приложении.
 * История общая с ботом: это тот же активный диалог, что и в мини-приложении ИИ.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class StarAiChatActivity extends BaseFragment {

    private static final int MENU_NEW = 1;

    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private TextView send;
    private long chatId;
    private boolean busy;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("🤖 ИИ-ассистент");
        actionBar.setSubtitle("@" + StarModConfig.AI_BOT);
        actionBar.createMenu().addItem(MENU_NEW, R.drawable.msg_edit);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_NEW) newChat();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        scroll = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int p = AndroidUtilities.dp(10);
        list.setPadding(p, p, p, p);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bar.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        input = new EditText(context);
        input.setHint("Спросите что угодно…");
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        input.setBackground(null);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        send = new TextView(context);
        send.setText("➤");
        send.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        send.setTextColor(Theme.getColor(Theme.key_chats_actionBackground));
        send.setGravity(Gravity.CENTER);
        send.setOnClickListener(v -> sendCurrent());
        bar.addView(send, new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fragmentView = root;
        loadHistory();
        return fragmentView;
    }

    private TextView bubble(String text, boolean mine) {
        Context ctx = getParentActivity();
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTextIsSelectable(true);
        tv.setTextColor(Theme.getColor(mine ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setColor(Theme.getColor(mine ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
        tv.setBackground(bg);
        int ph = AndroidUtilities.dp(12), pv = AndroidUtilities.dp(8);
        tv.setPadding(ph, pv, ph, pv);
        tv.setMaxWidth((int) (AndroidUtilities.displaySize.x * 0.8f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.topMargin = AndroidUtilities.dp(6);
        list.addView(tv, lp);
        scrollDown();
        return tv;
    }

    private void hint(String text) {
        TextView tv = new TextView(getParentActivity());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        tv.setGravity(Gravity.CENTER);
        int p = AndroidUtilities.dp(16);
        tv.setPadding(p, p, p, p);
        list.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void scrollDown() {
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void loadHistory() {
        hint("Загрузка диалога…");
        StarAi.api(currentAccount, new StarAi.Api() {
            @Override
            public void ok(String base, String init) {
                Utilities.globalQueue.postRunnable(() -> {
                    JSONObject r = null;
                    try {
                        r = StarAi.call(base, init, "chat", null);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    final JSONObject res = r;
                    AndroidUtilities.runOnUIThread(() -> showHistory(res));
                });
            }

            @Override
            public void fail(String why) {
                list.removeAllViews();
                hint("⚠️ " + why);
            }
        });
    }

    private void showHistory(JSONObject r) {
        if (list == null) return;
        list.removeAllViews();
        if (r == null || r.has("error")) {
            hint("⚠️ " + (r != null ? r.optString("error") : "Сервер ИИ недоступен — бот запущен?"));
            return;
        }
        JSONObject chat = r.optJSONObject("chat");
        chatId = chat != null ? chat.optLong("id", 0) : 0;
        JSONArray ms = r.optJSONArray("messages");
        int shown = 0;
        if (ms != null) {
            for (int i = Math.max(0, ms.length() - 40); i < ms.length(); i++) {
                JSONObject m = ms.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role"), content = m.optString("content");
                if (TextUtils.isEmpty(content) || !("user".equals(role) || "assistant".equals(role))) continue;
                bubble(StarAi.plain(content), "user".equals(role));
                shown++;
            }
        }
        if (shown == 0) hint("Привет! Я ИИ-ассистент StarTegs. Спросите что угодно — отвечу прямо здесь.\nИстория общая с @" + StarModConfig.AI_BOT + ".");
    }

    private void newChat() {
        if (busy) return;
        StarAi.api(currentAccount, new StarAi.Api() {
            @Override
            public void ok(String base, String init) {
                Utilities.globalQueue.postRunnable(() -> {
                    long id = 0;
                    try {
                        id = StarAi.call(base, init, "chat/new", null).optLong("id", 0);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    final long nid = id;
                    AndroidUtilities.runOnUIThread(() -> {
                        chatId = nid;
                        list.removeAllViews();
                        hint("Новый диалог. О чём поговорим?");
                    });
                });
            }

            @Override
            public void fail(String why) {
                hint("⚠️ " + why);
            }
        });
    }

    private void sendCurrent() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || busy) return;
        busy = true;
        send.setAlpha(0.4f);
        input.setText("");
        bubble(text, true);
        TextView answer = bubble("✨ Думаю…", false);
        StarAi.ask(currentAccount, text, chatId != 0 ? chatId : -1, new StarAi.Stream() {
            @Override
            public void partial(String t) {
                answer.setText(t + " ▍");
                scrollDown();
            }

            @Override
            public void done(String t) {
                answer.setText(t);
                finish();
            }

            @Override
            public void error(String why) {
                answer.setText("⚠️ " + why);
                finish();
            }

            private void finish() {
                busy = false;
                send.setAlpha(1f);
                scrollDown();
            }
        });
    }
}
