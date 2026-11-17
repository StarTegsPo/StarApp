/*
 * StarTegs 4.1 — «Ответь за меня»: нейросеть предлагает 3 варианта ответа в вашем стиле
 * (по вашим последним сообщениям в этом чате). Выбранный вариант встаёт в поле ввода —
 * отправляете вы сами. Бесплатно 3 раза в день, дальше — Premium.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class StarReply {

    private StarReply() {}

    public static final int OPTION_REPLY_FOR_ME = 9040;
    static final int FREE_PER_DAY = 3;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static int usedToday() {
        return today().equals(prefs().getString("reply_day", "")) ? prefs().getInt("reply_n", 0) : 0;
    }

    private static void markUsed() {
        prefs().edit().putString("reply_day", today()).putInt("reply_n", usedToday() + 1).apply();
    }

    static int freeLeft() {
        return Math.max(0, FREE_PER_DAY - usedToday());
    }

    public static boolean canUse(MessageObject m) {
        return StarAi.enabled() && m != null && m.messageOwner != null && !m.isOut()
                && !TextUtils.isEmpty(m.messageOwner.message) && !m.isSponsored();
    }

    public static String title() {
        if (StarMod.isPro()) return "💡 Ответь за меня (ИИ)";
        int left = freeLeft();
        return left > 0 ? "💡 Ответь за меня · " + left + " бесплатно" : "💡 Ответь за меня  ⭐";
    }

    public static void suggest(ChatActivity chat, MessageObject target) {
        if (chat == null || chat.getParentActivity() == null || !canUse(target)) return;
        if (!StarMod.isPro() && freeLeft() <= 0) {
            StarMod.requirePro(chat, "«Ответь за меня» без лимита");
            return;
        }
        StringBuilder ctx = new StringBuilder();
        StringBuilder mine = new StringBuilder();
        ArrayList<MessageObject> list = chat.messages;
        int n = 0, myN = 0;
        if (list != null) {
            for (int i = Math.min(list.size(), 120) - 1; i >= 0; i--) {
                MessageObject m = list.get(i);
                if (m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message)) continue;
                String t = StarCodec.strip(m.messageOwner.message);
                if (t.length() > 300) t = t.substring(0, 300) + "…";
                String who = "Я";
                if (!m.isOut()) {
                    TLRPC.User u = chat.getMessagesController().getUser(m.getFromChatId());
                    who = u != null ? UserObject.getFirstName(u) : "Собеседник";
                } else if (myN < 25) {
                    mine.append("— ").append(t).append('\n');
                    myN++;
                }
                if (n < 30) {
                    ctx.append(who).append(": ").append(t).append('\n');
                    n++;
                }
            }
        }
        String question = StarCodec.strip(target.messageOwner.message);
        String prompt = "Ты помогаешь мне ответить в Telegram. Предложи РОВНО 3 разных варианта моего ответа на последнее сообщение собеседника: "
                + "1) короткий, 2) тёплый/дружелюбный, 3) развёрнутый. Пиши от первого лица, на языке переписки, "
                + "копируя мой стиль (длину фраз, эмодзи, строчные буквы, сленг) по примерам ниже. "
                + "Формат ответа: три строки, каждая начинается с «1) », «2) », «3) », без пояснений.\n\n"
                + (mine.length() > 0 ? "Мои сообщения (стиль):\n" + mine + "\n" : "")
                + "Переписка:\n" + ctx + "\nОтветить на: «" + question + "»";
        Toast.makeText(chat.getParentActivity(), "💡 Придумываю ответы…", Toast.LENGTH_SHORT).show();
        StarAi.ask(chat.getCurrentAccount(), prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> show(chat, target, parse(text)));
            }

            @Override
            public void error(String why) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                    if (chat.getParentActivity() != null) Toast.makeText(chat.getParentActivity(), "ИИ: " + why, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    static ArrayList<String> parse(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.matches("^[1-3][).:\\-]\\s*.+")) {
                l = l.replaceFirst("^[1-3][).:\\-]\\s*", "").trim();
                if (l.startsWith("«") && l.endsWith("»") || l.startsWith("\"") && l.endsWith("\"")) l = l.substring(1, l.length() - 1);
                if (!l.isEmpty()) out.add(l);
            }
        }
        if (out.isEmpty() && !text.trim().isEmpty()) out.add(text.trim());
        while (out.size() > 3) out.remove(out.size() - 1);
        return out;
    }

    private static void show(ChatActivity chat, MessageObject target, ArrayList<String> variants) {
        if (chat.getParentActivity() == null) return;
        if (variants.isEmpty()) {
            Toast.makeText(chat.getParentActivity(), "ИИ не придумал ответ, попробуйте ещё раз", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!StarMod.isPro()) markUsed();
        StarAchieve.bump("reply_ai", 1);
        String[] labels = {"⚡ ", "🤗 ", "📝 "};
        CharSequence[] items = new CharSequence[variants.size() + 1];
        for (int i = 0; i < variants.size(); i++) items[i] = labels[Math.min(i, 2)] + variants.get(i);
        items[variants.size()] = "🔄 Другие варианты";
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("💡 Ответь за меня")
                .setItems(items, (d, which) -> {
                    if (which == variants.size()) {
                        suggest(chat, target);
                        return;
                    }
                    if (chat.getChatActivityEnterView() != null) chat.getChatActivityEnterView().setFieldText(variants.get(which));
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }
}
