/*
 * StarTegs — нейросеть в приложении через @StarTegsAi_bot. Лицензия: GNU GPL v2 или новее.
 *
 * Мод открывает мини-приложение ИИ-бота «без экрана» (см. StarCloud.webApp), берёт
 * адрес его сервера и подписанные Telegram данные (initData) и говорит с тем же API,
 * что и мини-приложение: /api/send ставит задачу, /api/job отдаёт ответ по мере
 * написания. Лимиты, Premium и выбор модели остаются на стороне бота.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public final class StarAi {

    private StarAi() {}

    /** Пункты меню чата (⋮). */
    public static final int MENU_SUMMARY = 9010;
    public static final int MENU_IMPROVE = 9011;

    public static boolean enabled() {
        return !TextUtils.isEmpty(StarModConfig.AI_BOT);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- API ИИ-бота

    interface Api {
        void ok(String base, String init);

        void fail(String why);
    }

    static void api(int account, Api cb) {
        StarCloud.webApp(account, StarModConfig.AI_BOT, new StarCloud.WebApp() {
            @Override
            public void ok(String url, String menuUrl, String init) {
                // мини-приложение ИИ-бота открывается с его же сервера: API на том же адресе
                Uri u = Uri.parse(url);
                if (u.getScheme() == null || u.getAuthority() == null) {
                    cb.fail("Неверный адрес мини-приложения ИИ");
                    return;
                }
                cb.ok(u.getScheme() + "://" + u.getAuthority(), init);
            }

            @Override
            public void fail(String why) {
                cb.fail(why);
            }
        });
    }

    /** POST {base}/api/{path}. Вызывать не на главном потоке. */
    static JSONObject call(String base, String init, String path, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/" + path).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Init-Data", init);
        try (OutputStream os = c.getOutputStream()) {
            os.write((body == null ? "{}" : body.toString()).getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return new JSONObject(sb.toString());
    }

    interface Stream {
        void partial(String text);

        void done(String text);

        void error(String why);
    }

    /** Убрать разметку Markdown, которую Telegram-клиент не рисует в простом тексте. */
    static String plain(String s) {
        if (s == null) return "";
        return s.replace("**", "").replace("__", "").replaceAll("(?m)^#{1,6}\\s*", "").replace("```", "");
    }

    /**
     * Спросить нейросеть. chatId — диалог в ИИ-боте: > 0 — этот диалог, 0 — отдельный диалог
     * мода (чтобы разовые вопросы не смешивались с вашими чатами в боте), < 0 — активный диалог.
     */
    static void ask(int account, String prompt, long chatId, Stream cb) {
        api(account, new Api() {
            @Override
            public void ok(String base, String init) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        // chatId > 0 — конкретный диалог, 0 — отдельный диалог мода, < 0 — активный диалог в боте
                        long chat = chatId > 0 ? chatId : chatId == 0 ? prefs().getLong("ai_mod_chat", 0) : 0;
                        if (chat == 0 && chatId == 0) {
                            JSONObject n = call(base, init, "chat/new", null);
                            chat = n.optLong("id", 0);
                            if (chat != 0) prefs().edit().putLong("ai_mod_chat", chat).apply();
                        }
                        JSONObject body = new JSONObject().put("text", prompt);
                        if (chat > 0) body.put("chat", chat);
                        JSONObject r = call(base, init, "send", body);
                        if (r.has("error") && chatId == 0) {
                            // диалог мода могли удалить в боте — создаём заново один раз
                            prefs().edit().remove("ai_mod_chat").apply();
                            JSONObject n = call(base, init, "chat/new", null);
                            if (n.optLong("id", 0) != 0) {
                                prefs().edit().putLong("ai_mod_chat", n.optLong("id")).apply();
                                r = call(base, init, "send", body.put("chat", n.optLong("id")));
                            }
                        }
                        if (r.has("error")) {
                            String e = r.optString("error");
                            AndroidUtilities.runOnUIThread(() -> cb.error(e));
                            return;
                        }
                        String job = r.optString("job", "");
                        long started = System.currentTimeMillis();
                        String last = "";
                        while (System.currentTimeMillis() - started < 180_000L) {
                            Thread.sleep(700);
                            JSONObject j = call(base, init, "job", new JSONObject().put("id", job));
                            if (!TextUtils.isEmpty(j.optString("error", "")) && !"null".equals(j.optString("error"))) {
                                String e = j.optString("error");
                                AndroidUtilities.runOnUIThread(() -> cb.error(e));
                                return;
                            }
                            String text = plain(j.optString("text", ""));
                            if (j.optBoolean("done", false)) {
                                AndroidUtilities.runOnUIThread(() -> cb.done(text));
                                return;
                            }
                            if (!text.equals(last)) {
                                last = text;
                                AndroidUtilities.runOnUIThread(() -> cb.partial(text));
                            }
                        }
                        AndroidUtilities.runOnUIThread(() -> cb.error("Нейросеть долго не отвечает, попробуйте ещё раз"));
                    } catch (Exception e) {
                        FileLog.e(e);
                        StarCloud.forget(account, StarModConfig.AI_BOT);
                        AndroidUtilities.runOnUIThread(() -> cb.error("Сервер ИИ недоступен — бот запущен?"));
                    }
                });
            }

            @Override
            public void fail(String why) {
                cb.error(why);
            }
        });
    }

    // ---------------------------------------------------------------- окно с ответом

    interface Use {
        void run(String text);
    }

    /** Показывает ответ нейросети в окне, текст появляется по мере написания. */
    static void streamDialog(BaseFragment fragment, String title, String prompt, String useLabel, Use use) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        tv.setTextIsSelectable(true);
        tv.setText("✨ Думаю…");
        int pad = AndroidUtilities.dp(22);
        tv.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(8));
        ScrollView sv = new ScrollView(ctx);
        sv.addView(tv);
        final String[] result = {null};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx).setTitle(title).setView(sv, AndroidUtilities.dp(320));
        b.setPositiveButton("Копировать", (d, w) -> {
            if (result[0] != null) {
                AndroidUtilities.addToClipboard(result[0]);
                Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });
        if (use != null) b.setNeutralButton(useLabel, (d, w) -> {
            if (result[0] != null) use.run(result[0]);
        });
        b.setNegativeButton("Закрыть", null);
        b.show();
        ask(fragment.getCurrentAccount(), prompt, 0, new Stream() {
            @Override
            public void partial(String text) {
                tv.setText(text + " ▍");
            }

            @Override
            public void done(String text) {
                result[0] = text;
                tv.setText(text);
            }

            @Override
            public void error(String why) {
                tv.setText("⚠️ " + why);
            }
        });
    }

    // ---------------------------------------------------------------- функции в чатах

    /** «Спросить ИИ» в меню сообщения. */
    public static void askAboutMessage(BaseFragment fragment, MessageObject m) {
        if (fragment.getParentActivity() == null || m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message)) return;
        final String text = m.messageOwner.message.length() > 3500 ? m.messageOwner.message.substring(0, 3500) + "…" : m.messageOwner.message;
        final String[] prompts = {
                "Перескажи коротко, о чём это сообщение:",
                "Переведи на русский язык (если уже по-русски — на английский). Дай только перевод:",
                "Объясни простыми словами:",
                "Предложи 3 коротких варианта ответа на это сообщение, каждый с новой строки:",
        };
        final String[] titles = {"📝 Пересказ", "🌍 Перевод", "💡 Объяснение", "💬 Варианты ответа"};
        CharSequence[] items = {"📝 Пересказать", "🌍 Перевести", "💡 Объяснить", "💬 Предложить ответ"};
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🤖 Спросить ИИ")
                .setItems(items, (dialog, which) -> {
                    Use reply = fragment instanceof ChatActivity ? t -> setInput((ChatActivity) fragment, which == 3 ? firstLine(t) : t) : null;
                    streamDialog(fragment, titles[which], prompts[which] + "\n\n" + text, "Ответить этим", reply);
                })
                .show();
    }

    private static String firstLine(String t) {
        String s = t.trim();
        int nl = s.indexOf('\n');
        s = nl > 0 ? s.substring(0, nl) : s;
        return s.replaceAll("^[0-9]+[.)]\\s*", "").replaceAll("^[-•]\\s*", "");
    }

    private static void setInput(ChatActivity chat, String text) {
        if (chat.getChatActivityEnterView() != null) chat.getChatActivityEnterView().setFieldText(text);
    }

    /** Меню чата → «Что я пропустил?»: пересказ последних сообщений. */
    public static void summarize(ChatActivity chat) {
        ArrayList<MessageObject> list = chat.messages;
        if (list == null || list.isEmpty()) {
            Toast.makeText(chat.getParentActivity(), "Нет сообщений для пересказа", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        // в ChatActivity.messages новые сообщения идут первыми
        for (int i = Math.min(list.size(), 80) - 1; i >= 0; i--) {
            MessageObject m = list.get(i);
            if (m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message)) continue;
            String who = "?";
            if (m.isOut()) who = "Я";
            else {
                org.telegram.tgnet.TLRPC.User u = chat.getMessagesController().getUser(m.getFromChatId());
                if (u != null) who = UserObject.getFirstName(u);
                else if (m.getFromChatId() < 0) who = "Канал";
            }
            String t = m.messageOwner.message;
            sb.append(who).append(": ").append(t.length() > 400 ? t.substring(0, 400) + "…" : t).append('\n');
            if (++n >= 60 || sb.length() > 6000) break;
        }
        if (n == 0) {
            Toast.makeText(chat.getParentActivity(), "Нет текстовых сообщений для пересказа", Toast.LENGTH_SHORT).show();
            return;
        }
        streamDialog(chat, "✨ Что я пропустил?",
                "Перескажи переписку ниже: главное, решения, вопросы ко мне. Коротко, списком, по-русски.\n\n" + sb, null, null);
    }

    /** Меню чата → «Улучшить мой текст»: правка текста в поле ввода. */
    public static void improveInput(ChatActivity chat) {
        CharSequence cur = chat.getChatActivityEnterView() != null ? chat.getChatActivityEnterView().getFieldText() : null;
        if (TextUtils.isEmpty(cur)) {
            Toast.makeText(chat.getParentActivity(), "Сначала напишите текст в поле ввода", Toast.LENGTH_SHORT).show();
            return;
        }
        final String text = cur.toString();
        final String[] prompts = {
                "Исправь ошибки и опечатки, сохрани смысл и стиль. Дай только исправленный текст:",
                "Перепиши вежливее и дружелюбнее. Дай только новый текст:",
                "Перепиши короче и яснее. Дай только новый текст:",
                "Переведи на английский. Дай только перевод:",
        };
        CharSequence[] items = {"✍️ Исправить ошибки", "🤝 Вежливее", "✂️ Короче", "🇬🇧 На английский"};
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("✨ Улучшить текст")
                .setItems(items, (dialog, which) -> streamDialog(chat, String.valueOf(items[which]), prompts[which] + "\n\n" + text,
                        "Заменить", t -> setInput(chat, t.trim())))
                .show();
    }
}
