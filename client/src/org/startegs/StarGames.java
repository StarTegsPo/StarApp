/*
 * StarTegs 3.0 — мини-игры в чате: крестики-нолики (ходы — сообщениями), правда или действие,
 * «угадай по эмодзи». Друг без мода видит обычный текст с полем, с модом — может ходить.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.util.Locale;
import java.util.Random;

public final class StarGames {

    private StarGames() {}

    public static final int MENU_GAMES = 9014;
    public static final int OPTION_PLAY = 9006;

    private static void send(ChatActivity chat, String text) {
        SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(text, chat.getDialogId());
        StarSend.direct(chat.getCurrentAccount(), p);
        StarAchieve.bump("games", 1);
        StarQuest.progress("game", 1);
        if (text.contains("🏆 Победа") || text.startsWith("✅ Угадал")) StarAchieve.bump("wins", 1);
    }

    private static String name(int account, long uid) {
        TLRPC.User u = MessagesController.getInstance(account).getUser(uid);
        return u != null ? UserObject.getFirstName(u) : "игрок";
    }

    /** Меню чата → «🎮 Игры». */
    public static void menu(ChatActivity chat) {
        if (chat.getParentActivity() == null) return;
        CharSequence[] items = {"❌⭕ Крестики-нолики", "🎲 Правда или действие", "🧩 Угадай по эмодзи", "🎯 Кубик, дартс, баскетбол"};
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("🎮 Игры с другом")
                .setItems(items, (d, which) -> {
                    if (which == 0) startTicTacToe(chat);
                    else if (which == 1) truthOrDare(chat);
                    else if (which == 2) riddle(chat);
                    else {
                        String[] e = {"🎲", "🎯", "🏀", "⚽", "🎳", "🎰"};
                        new AlertDialog.Builder(chat.getParentActivity())
                                .setItems(e, (d2, w2) -> send(chat, e[w2]))
                                .show();
                    }
                })
                .show();
    }

    // ================================================================ крестики-нолики

    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private static char winner(char[] b) {
        for (int[] l : LINES) if (b[l[0]] != '.' && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        for (char c : b) if (c == '.') return 0;
        return 'd';
    }

    private static String boardText(char[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            sb.append(b[i] == 'x' ? "❌" : b[i] == 'o' ? "⭕" : "⬜");
            if (i % 3 == 2) sb.append('\n');
        }
        return sb.toString();
    }

    private static void startTicTacToe(ChatActivity chat) {
        long me = UserConfig.getInstance(chat.getCurrentAccount()).getClientUserId();
        long other = chat.getDialogId() > 0 ? chat.getDialogId() : 0;
        board(chat, ".........".toCharArray(), me, other, 'x');
    }

    /** Поле 3×3: нажали клетку — ход уходит сообщением. */
    private static void board(ChatActivity chat, char[] b, long x, long o, char mySide) {
        Context ctx = chat.getParentActivity();
        if (ctx == null) return;
        GridLayout g = new GridLayout(ctx);
        g.setColumnCount(3);
        g.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        AlertDialog[] dlg = new AlertDialog[1];
        for (int i = 0; i < 9; i++) {
            final int cell = i;
            TextView t = new TextView(ctx);
            t.setText(b[i] == 'x' ? "❌" : b[i] == 'o' ? "⭕" : "");
            t.setTextSize(30);
            t.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(14));
            bg.setColor((Theme.getColor(Theme.key_chats_actionBackground) & 0x00FFFFFF) | 0x22000000);
            t.setBackground(bg);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = AndroidUtilities.dp(72);
            lp.height = AndroidUtilities.dp(72);
            lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            t.setOnClickListener(v -> {
                if (b[cell] != '.') return;
                b[cell] = mySide;
                if (dlg[0] != null) dlg[0].dismiss();
                long me = UserConfig.getInstance(chat.getCurrentAccount()).getClientUserId();
                long oo = mySide == 'o' && o == 0 ? me : o;
                char w = winner(b);
                long next = mySide == 'x' ? oo : x;
                String head = "🎮 Крестики-нолики\n" + boardText(b);
                String tail;
                if (w == 'x' || w == 'o') tail = "🏆 Победа: " + (w == 'x' ? "❌ " : "⭕ ") + name(chat.getCurrentAccount(), me) + "!";
                else if (w == 'd') tail = "🤝 Ничья!";
                else tail = "Ходит " + (mySide == 'x' ? "⭕" : "❌") + (next != 0 ? " " + name(chat.getCurrentAccount(), next) : "") + " — долгое нажатие → «Сделать ход»";
                String data = "ttt|" + new String(b) + "|" + (w == 0 ? next : -1) + "|" + x + "|" + oo;
                send(chat, StarCodec.attach(head + tail, data));
            });
            g.addView(t, lp);
        }
        dlg[0] = new AlertDialog.Builder(ctx)
                .setTitle(mySide == 'x' ? "Ваш ход: ❌" : "Ваш ход: ⭕")
                .setView(g)
                .setNegativeButton("Закрыть", null)
                .create();
        dlg[0].show();
    }

    // ================================================================ ходы по сообщениям

    /** Можно ли сделать ход / ответить на это сообщение. */
    public static boolean canPlay(int account, MessageObject m) {
        if (m == null || m.messageOwner == null || TextUtils.isEmpty(m.messageOwner.message)) return false;
        String d = StarCodec.read(m.messageOwner.message);
        if (d == null) return false;
        long me = UserConfig.getInstance(account).getClientUserId();
        if (d.startsWith("ttt|")) {
            String[] p = d.split("\\|");
            if (p.length < 5) return false;
            long next = parse(p[2]), x = parse(p[3]);
            return next == me || next == 0 && x != me && !m.isOut();
        }
        return d.startsWith("rid|") && !m.isOut();
    }

    public static String playTitle(int account, MessageObject m) {
        String d = StarCodec.read(m.messageOwner.message);
        return d != null && d.startsWith("rid|") ? "🧩 Ответить на загадку" : "🎮 Сделать ход";
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1;
        }
    }

    public static void play(ChatActivity chat, MessageObject m) {
        String d = StarCodec.read(m.messageOwner.message);
        if (d == null) return;
        if (d.startsWith("ttt|")) {
            String[] p = d.split("\\|");
            if (p.length < 5 || p[1].length() != 9) return;
            long me = UserConfig.getInstance(chat.getCurrentAccount()).getClientUserId();
            long x = parse(p[3]);
            char side = me == x ? 'x' : 'o';
            board(chat, p[1].toCharArray(), x, parse(p[4]), side);
        } else if (d.startsWith("rid|")) {
            int id = (int) parse(d.substring(4));
            if (id < 0 || id >= RIDDLES.length) return;
            answerRiddle(chat, id);
        }
    }

    // ================================================================ правда или действие

    private static final String[] TRUTH = {
            "Какая песня у тебя сейчас на повторе?", "Что самое смешное с тобой случалось в школе?", "Какой твой самый странный страх?",
            "Если бы мог(ла) жить в любом городе мира — где?", "Какой суперспособностью хочешь обладать?", "Твоё самое неловкое воспоминание?",
            "Кто из знаменитостей тебе нравится и почему?", "Что ты делаешь, когда никто не видит?", "Самый лучший подарок, который тебе дарили?",
            "О чём ты мечтаешь через 5 лет?", "Какое приложение ты открываешь чаще всего?", "Какой фильм пересматривал(а) больше всех раз?",
            "Что бы ты сделал(а), если бы был(а) невидимкой день?", "Самая вкусная еда в твоей жизни?", "Какую привычку хочешь бросить?",
    };
    private static final String[] DARE = {
            "Отправь самый старый мем из своей галереи 😅", "Запиши голосовое, где поёшь припев любимой песни 🎤", "Поставь в статус то, что я скажу, на 10 минут",
            "Отправь 5 эмодзи, которые описывают твой день", "Сделай селфи с самой смешной рожицей", "Напиши стих из 4 строк про меня",
            "Расскажи анекдот голосовым", "Отправь фото того, что сейчас перед тобой", "Говори следующие 10 минут только эмодзи",
            "Отправь скрин своей клавиатуры с подсказками на «я тебя»", "Придумай мне новое прозвище", "Изобрази животное голосовым — я угадываю",
            "Покажи последнее фото в галерее (если не секрет 😉)", "Напиши мне комплимент на английском", "Сделай 10 приседаний и отчитайся 💪",
    };

    private static void truthOrDare(ChatActivity chat) {
        CharSequence[] items = {"🤔 Правда", "🔥 Действие", "🎲 Случайно"};
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("🎲 Правда или действие")
                .setItems(items, (d, which) -> {
                    Random r = new Random();
                    boolean truth = which == 0 || which == 2 && r.nextBoolean();
                    String q = truth ? TRUTH[r.nextInt(TRUTH.length)] : DARE[r.nextInt(DARE.length)];
                    String who = chat.getDialogId() > 0 ? name(chat.getCurrentAccount(), chat.getDialogId()) + ", " : "";
                    send(chat, "🎲 Правда или действие\n" + (truth ? "🤔 Правда" : "🔥 Действие") + "\n\n" + who + q);
                })
                .show();
    }

    // ================================================================ угадай по эмодзи

    private static final String[][] RIDDLES = {
            {"🦁👑", "мультфильм", "король лев"}, {"🧊❄️👸", "мультфильм", "холодное сердце"}, {"🕷️🧑", "фильм", "человек паук"},
            {"🧙‍♂️⚡👓", "фильм", "гарри поттер"}, {"🚢🧊💔", "фильм", "титаник"}, {"🐼🥋", "мультфильм", "кунг фу панда"},
            {"👻🚫", "фильм", "охотники за привидениями"}, {"🐠🔍", "мультфильм", "в поисках немо"}, {"🦖🏝️", "фильм", "парк юрского периода"},
            {"🍫🏭", "фильм", "чарли и шоколадная фабрика"}, {"🤖❤️🌱", "мультфильм", "валл и"}, {"👨‍🚀🌽🕳️", "фильм", "интерстеллар"},
            {"🧞‍♂️🪔", "мультфильм", "аладдин"}, {"🐀👨‍🍳", "мультфильм", "рататуй"}, {"🦸‍♂️🛡️🇺🇸", "фильм", "капитан америка"},
            {"🏴‍☠️💀⛵", "фильм", "пираты карибского моря"}, {"🐉🏠", "мультфильм", "как приручить дракона"}, {"🎈🏠👴", "мультфильм", "вверх"},
            {"🧸🚀", "мультфильм", "история игрушек"}, {"🦇🃏", "фильм", "темный рыцарь"}, {"🧟‍♂️🌍", "игра", "майнкрафт"},
            {"🍄👨‍🔧", "игра", "марио"}, {"🔫🏝️🪂", "игра", "фортнайт"}, {"🚗⭐🏙️", "игра", "гта"}, {"🐦😡🐷", "игра", "angry birds"},
    };

    private static void riddle(ChatActivity chat) {
        int id = new Random().nextInt(RIDDLES.length);
        String[] r = RIDDLES[id];
        send(chat, StarCodec.attach("🧩 Угадай по эмодзи (" + r[1] + "):\n\n" + r[0] + "\n\nОтвечай в чат! С StarTegs — долгое нажатие → «Ответить на загадку»", "rid|" + id));
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\d]", "");
    }

    private static void answerRiddle(ChatActivity chat, int id) {
        Context ctx = chat.getParentActivity();
        EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setHint("Твой ответ");
        FrameLayout box = new FrameLayout(ctx);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle("🧩 " + RIDDLES[id][0])
                .setView(box)
                .setPositiveButton("Проверить", (d, w) -> {
                    String a = norm(input.getText().toString()), right = norm(RIDDLES[id][2]);
                    boolean ok = !a.isEmpty() && (a.equals(right) || a.length() >= 4 && (right.contains(a) || a.contains(right)));
                    if (ok) {
                        StarFx.play(chat, "fx_confetti");
                        send(chat, "✅ Угадал(а)! Это «" + RIDDLES[id][2] + "» 🎉");
                    } else {
                        Toast.makeText(ctx, "❌ Не то… попробуй ещё", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Сдаюсь", (d, w) -> send(chat, "🏳️ Сдаюсь! Что это было?"))
                .show();
    }
}
