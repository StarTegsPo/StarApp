/*
 * StarTegs 4.8 — большой набор команд в чате (пишутся с точкой, например «.hug Маша»).
 * Всё работает прямо на телефоне, без интернета и без бота. Свои команды и .calc/.tr/.ai — в StarPlugins.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.util.Base64;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class StarCmds {

    private StarCmds() {}

    /** Команда выполнена, отправлять ничего не нужно. */
    static final String CONSUMED = "\u0000consumed";
    /** Сообщение уже подготовлено вместе с форматированием — отправить как есть. */
    static final String KEEP = "\u0000keep";

    private static final Random R = new Random();
    private static final Locale RU = new Locale("ru");

    static final String HELP = "\n✍️ ТЕКСТ\n"
            + ".b / .i / .u / .s текст — жирный, курсив, подчёркнутый, зачёркнутый\n"
            + ".mono текст — моноширинный, .spoiler текст — под спойлером\n"
            + ".quote текст — цитата, .font 1-12 текст — красивый шрифт (.fonts — список)\n"
            + ".low / .title текст — строчные / Каждое Слово\n"
            + ".wide текст — Ｗｉｄｅ, .space т е к с т, .clap текст 👏\n"
            + ".zalgo текст — жуткий текст, .emoji текст — с эмодзи\n"
            + ".translit привет → privet, .ru privet → привет\n"
            + ".repeat 3 текст, .count текст — сколько символов и слов\n"
            + "\n🎮 ИГРЫ И ВЕСЕЛЬЕ\n"
            + ".rps камень — камень-ножницы-бумага, .roll 2d6 — кубики\n"
            + ".coin — монетка, .yesno — да или нет, .percent вопрос — вероятность\n"
            + ".rate что-то — оценка, .iq Имя, .when событие — предсказание\n"
            + ".truth — правда, .dare — действие, .would — что бы ты выбрал\n"
            + ".joke — шутка, .fact — факт, .compliment Имя, .motivate — мотивация\n"
            + ".sort / .shuffle a, b, c — отсортировать / перемешать\n"
            + "\n🤗 ДЕЙСТВИЯ (РП)\n"
            + ".hug .kiss .pat .slap .bite .poke .highfive .cuddle .feed .dance .wave .tickle .boop .cheer Имя\n"
            + ".me действие — «* Ты делаешь что-то *»\n"
            + "\n📅 ВРЕМЯ\n"
            + ".date — сегодня, .days 31.12.2026 — сколько дней осталось\n"
            + ".ny — до Нового года, .age 15.03.2010 — сколько лет\n"
            + ".remind 30 текст — напомнить через N минут (в «Избранное»)\n"
            + "\n🛠 ПОЛЕЗНОЕ\n"
            + ".g / .yt / .ya / .wiki запрос — ссылка на поиск\n"
            + ".pass 16 — пароль (копируется, в чат не уходит), .uuid\n"
            + ".bin / .hex / .dec число, .c2f / .f2c градусы\n"
            + ".morse текст, .unmorse код (.unmorse ру код — по-русски), .b64 / .unb64 текст\n";

    private static String pick(String... a) {
        return a[R.nextInt(a.length)];
    }

    private static String me(int account) {
        TLRPC.User u = UserConfig.getInstance(account).getCurrentUser();
        return u != null ? UserObject.getFirstName(u) : "Я";
    }

    private static void toast(String s) {
        Toast.makeText(ApplicationLoader.applicationContext, s, Toast.LENGTH_LONG).show();
    }

    /** Разметка на всё сообщение. */
    private static String styled(SendMessagesHelper.SendMessageParams p, String text, TLRPC.MessageEntity e) {
        if (text.isEmpty()) return null;
        e.offset = 0;
        e.length = text.length();
        p.message = text;
        p.entities = new ArrayList<>();
        p.entities.add(e);
        return KEEP;
    }

    private static int stableHash(String s, int mod) {
        int h = 7;
        for (char c : s.toLowerCase(RU).trim().toCharArray()) h = h * 31 + c;
        return Math.abs(h % mod);
    }

    private static Date parseDate(String s) {
        for (String f : new String[]{"dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy", "dd.MM"}) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(f, RU);
                df.setLenient(false);
                Date d = df.parse(s.trim());
                if (d == null) continue;
                if (f.equals("dd.MM")) { // ближайшая такая дата в будущем
                    Calendar c = Calendar.getInstance(), now = Calendar.getInstance();
                    c.setTime(d);
                    c.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    if (c.before(now)) c.add(Calendar.YEAR, 1);
                    return c.getTime();
                }
                return d;
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static long daysUntil(Date d) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTime(d);
        for (Calendar c : new Calendar[]{a, b}) {
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        return TimeUnit.MILLISECONDS.toDays(b.getTimeInMillis() - a.getTimeInMillis());
    }

    static String plural(long n, String one, String few, String many) {
        long a = Math.abs(n) % 100, b = a % 10;
        if (a > 10 && a < 20) return many;
        if (b == 1) return one;
        if (b >= 2 && b <= 4) return few;
        return many;
    }

    // ---------------------------------------------------------------- транслит и морзе

    private static final String[][] TR = {
            {"щ", "shch"}, {"ё", "yo"}, {"ж", "zh"}, {"ч", "ch"}, {"ш", "sh"}, {"ю", "yu"}, {"я", "ya"}, {"х", "kh"}, {"ц", "ts"},
            {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"д", "d"}, {"е", "e"}, {"з", "z"}, {"и", "i"}, {"й", "y"},
            {"к", "k"}, {"л", "l"}, {"м", "m"}, {"н", "n"}, {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"},
            {"у", "u"}, {"ф", "f"}, {"ъ", ""}, {"ы", "y"}, {"ь", ""}, {"э", "e"}};

    static String translit(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            char l = Character.toLowerCase(c);
            String rep = null;
            for (String[] t : TR) if (t[0].charAt(0) == l) rep = t[1];
            if (rep == null) b.append(c);
            else if (Character.isUpperCase(c) && !rep.isEmpty()) b.append(Character.toUpperCase(rep.charAt(0))).append(rep.substring(1));
            else b.append(rep);
        }
        return b.toString();
    }

    static String untranslit(String s) {
        String[][] back = {{"shch", "щ"}, {"yo", "ё"}, {"zh", "ж"}, {"ch", "ч"}, {"sh", "ш"}, {"yu", "ю"}, {"ya", "я"}, {"kh", "х"}, {"ts", "ц"},
                {"a", "а"}, {"b", "б"}, {"v", "в"}, {"g", "г"}, {"d", "д"}, {"e", "е"}, {"z", "з"}, {"i", "и"}, {"y", "ы"}, {"j", "й"},
                {"k", "к"}, {"l", "л"}, {"m", "м"}, {"n", "н"}, {"o", "о"}, {"p", "п"}, {"r", "р"}, {"s", "с"}, {"t", "т"},
                {"u", "у"}, {"f", "ф"}, {"h", "х"}, {"c", "ц"}, {"w", "в"}, {"x", "кс"}, {"q", "к"}};
        StringBuilder b = new StringBuilder();
        int i = 0;
        outer:
        while (i < s.length()) {
            for (String[] t : back) {
                if (s.regionMatches(true, i, t[0], 0, t[0].length())) {
                    boolean up = Character.isUpperCase(s.charAt(i));
                    b.append(up ? t[1].toUpperCase(RU) : t[1]);
                    i += t[0].length();
                    continue outer;
                }
            }
            b.append(s.charAt(i++));
        }
        return b.toString();
    }

    private static final String MORSE_KEYS = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String[] MORSE = {
            ".-", "-...", ".--", "--.", "-..", ".", ".", "...-", "--..", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", ".-.", "...", "-",
            "..-", "..-.", "....", "-.-.", "---.", "----", "--.-", "--.--", "-.--", "-..-", "..-..", "..--", ".-.-",
            ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.",
            "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..",
            "-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----."};

    static String morse(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(RU).toCharArray()) {
            if (c == ' ') {
                b.append("/ ");
                continue;
            }
            int i = MORSE_KEYS.indexOf(c);
            if (i >= 0) b.append(MORSE[i]).append(' ');
        }
        return b.toString().trim();
    }

    static String unmorse(String s) {
        boolean cyr = s.startsWith("ру ") || s.startsWith("ru ");
        if (cyr) s = s.substring(3);
        StringBuilder b = new StringBuilder();
        for (String word : s.trim().split("\\s*/\\s*")) {
            for (String code : word.trim().split("\\s+")) {
                int from = cyr ? 0 : 33;
                int to = cyr ? 33 : MORSE.length;
                for (int i = from; i < to; i++) {
                    if (MORSE[i].equals(code)) {
                        b.append(MORSE_KEYS.charAt(i));
                        break;
                    }
                }
            }
            b.append(' ');
        }
        return b.toString().trim();
    }

    // ---------------------------------------------------------------- тексты для игр

    private static final String[] JOKES = {
            "— Мам, можно я поиграю?\n— Сначала уроки.\n— А можно я поиграю в уроки?",
            "Wi-Fi пропал на 5 минут — и я узнал, что у меня есть семья. Вроде хорошие люди.",
            "Учитель: «Почему опоздал?»\n— Шёл по знаку «Школа. Медленно».",
            "Я не ленивый. Я в режиме энергосбережения 🔋",
            "Будильник — это кнопка «отложить», к которой зачем-то прикрутили часы.",
            "— Ты почему не отвечал?\n— Я ответил. Мысленно. Три раза.",
            "Если долго смотреть в холодильник, еда сама не появится. Проверено 47 раз.",
            "Пароль должен содержать заглавную букву, цифру и слёзы.",
    };
    private static final String[] FACTS = {
            "Осьминоги имеют три сердца и голубую кровь 🐙",
            "Мёд не портится: его находили в египетских гробницах, и он был съедобным 🍯",
            "Бананы — ягоды, а клубника — нет 🍌",
            "Улитки могут спать до трёх лет 🐌",
            "Сердце синего кита размером с небольшой автомобиль 🐋",
            "Эйфелева башня летом выше на 15 см — металл расширяется от жары 🗼",
            "У коал отпечатки пальцев почти как у людей 🐨",
            "Венера вращается в обратную сторону, и день на ней длиннее года 🪐",
            "Вомбаты оставляют кубические какашки 🟫",
            "В космосе нельзя плакать: слёзы не падают, а собираются в шарик 🚀",
    };
    private static final String[] TRUTH = {
            "Какое твоё самое неловкое воспоминание?", "В кого ты был(а) влюблён(а) в последний раз?", "Какую песню ты слушаешь тайком?",
            "Что ты сделал(а) бы с миллионом?", "Какой самый странный сон тебе снился?", "Чего ты боишься больше всего?",
            "Кому из чата ты бы доверил(а) свой телефон?", "Что последнее ты гуглил(а)?", "Какая твоя самая большая ложь родителям?",
            "Какой у тебя скрытый талант?",
    };
    private static final String[] DARE = {
            "Поставь на аватарку фото еды на 1 час 🍕", "Отправь голосовое, где поёшь припев любимой песни 🎤",
            "Напиши «я тебя ценю» трём друзьям 💌", "Сделай 15 приседаний и отправь кружок 🏋️",
            "Отправь последний скрин из галереи 📸", "Пиши только эмодзи следующие 10 минут 😶",
            "Расскажи анекдот голосовым 🎙", "Поменяй статус на «Я картошка 🥔» на час", "Позвони кому-нибудь и скажи «мяу» 🐱",
            "Сделай комплимент каждому в этом чате ✨",
    };
    private static final String[] WOULD = {
            "Уметь летать 🕊 или стать невидимкой 👻?", "Жить без музыки 🎧 или без игр 🎮?", "Знать будущее 🔮 или менять прошлое ⏪?",
            "Всегда говорить правду или всегда молчать?", "Лето круглый год ☀️ или зима круглый год ❄️?",
            "Быть самым умным 🧠 или самым смешным 😂?", "Жить в Minecraft-мире ⛏ или в мире аниме 🌸?", "Без телефона неделю 📵 или без сладкого месяц 🍰?",
    };
    private static final String[] COMPLIMENTS = {
            "ты делаешь этот чат лучше ✨", "у тебя отличное чувство юмора 😂", "с тобой всегда интересно 💬",
            "ты сияешь ярче, чем экран на 100% яркости 🌟", "твой вкус в музыке — топ 🎧", "ты очень добрый человек 💖",
            "ты справишься со всем, что задумал(а) 💪", "у тебя классная энергия ⚡",
    };
    private static final String[] MOTIVATE = {
            "Маленький шаг сегодня — большой результат завтра 🚀", "Ты уже дальше, чем вчера 💪", "Ошибки — это опыт, а не провал ✨",
            "Делай сейчас — потом скажешь себе спасибо 🙌", "Не сравнивай себя с другими. Сравнивай с собой вчерашним 📈",
            "Даже звёзды светят только в темноте ⭐",
    };

    private static final String[][] RP = {
            {"hug", "обнять", "🤗 %s крепко обнимает %s"},
            {"kiss", "поцеловать", "😘 %s целует %s"},
            {"pat", "погладить", "🫳 %s гладит по голове %s"},
            {"slap", "шлёп", "👋 %s даёт подзатыльник %s"},
            {"bite", "укусить", "😬 %s кусает %s"},
            {"poke", "тык", "👉 %s тыкает %s"},
            {"highfive", "дай5", "🙌 %s даёт пять %s"},
            {"cuddle", "прижать", "🫂 %s прижимается к %s"},
            {"feed", "покормить", "🍪 %s угощает печенькой %s"},
            {"dance", "танец", "💃 %s танцует с %s"},
            {"wave", "помахать", "👋 %s машет рукой %s"},
            {"tickle", "щекотать", "🤭 %s щекочет %s"},
            {"boop", "буп", "👉👃 %s делает буп по носу %s"},
            {"cheer", "поддержать", "📣 %s громко болеет за %s"},
    };

    // ---------------------------------------------------------------- выполнение

    /** null — не наша команда; CONSUMED / KEEP — см. выше; иначе — текст для отправки. */
    static String run(int account, SendMessagesHelper.SendMessageParams p, String cmd, String arg) {
        for (String[] rp : RP) {
            if (cmd.equals(rp[0]) || cmd.equals(rp[1])) {
                return String.format(rp[2], me(account), arg.isEmpty() ? "всех в чате" : arg);
            }
        }
        switch (cmd) {
            // ---------- оформление (настоящая разметка Telegram)
            case "b": case "bold": return styled(p, arg, new TLRPC.TL_messageEntityBold());
            case "i": case "italic": return styled(p, arg, new TLRPC.TL_messageEntityItalic());
            case "u": case "under": return styled(p, arg, new TLRPC.TL_messageEntityUnderline());
            case "s": case "strike": return styled(p, arg, new TLRPC.TL_messageEntityStrike());
            case "mono": case "code": return styled(p, arg, new TLRPC.TL_messageEntityCode());
            case "spoiler": case "спойлер": return styled(p, arg, new TLRPC.TL_messageEntitySpoiler());
            case "quote": case "цитата": return styled(p, arg, new TLRPC.TL_messageEntityBlockquote());
            case "font": {
                int sp = arg.indexOf(' ');
                if (sp <= 0) return null;
                try {
                    int n = Integer.parseInt(arg.substring(0, sp).trim()) - 1;
                    if (n < 0 || n >= StarStyle.STYLES.length) return null;
                    return StarStyle.apply(n, arg.substring(sp + 1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "fonts": {
                StringBuilder b = new StringBuilder("✨ Шрифты для .font N текст:\n");
                for (int k = 0; k < StarStyle.STYLES.length; k++) b.append(k + 1).append(". ").append(StarStyle.STYLES[k]).append('\n');
                toast(b.toString().trim());
                return CONSUMED;
            }
            case "low": return arg.isEmpty() ? null : arg.toLowerCase(RU);
            case "title": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                boolean start = true;
                for (char c : arg.toCharArray()) {
                    b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
                    start = Character.isWhitespace(c);
                }
                return b.toString();
            }
            case "wide": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                for (char c : arg.toCharArray()) {
                    if (c >= '!' && c <= '~') b.append((char) (c + 0xFEE0));
                    else if (c == ' ') b.append('　');
                    else b.append(c).append(' ');
                }
                return b.toString().trim();
            }
            case "space": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                for (char c : arg.toCharArray()) b.append(c).append(' ');
                return b.toString().trim();
            }
            case "clap": return arg.isEmpty() ? null : "👏 " + arg.trim().replaceAll("\\s+", " 👏 ") + " 👏";
            case "zalgo": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                for (char c : arg.toCharArray()) {
                    b.append(c);
                    if (Character.isWhitespace(c)) continue;
                    for (int k = 0; k < 3; k++) b.append((char) (0x0300 + R.nextInt(0x36F - 0x300)));
                }
                return b.toString();
            }
            case "emoji": {
                if (arg.isEmpty()) return null;
                String[] e = {"✨", "🔥", "💖", "😎", "🌟", "💫", "🥰", "😂", "👀", "💯", "🎉", "⚡"};
                StringBuilder b = new StringBuilder();
                for (String w : arg.trim().split("\\s+")) b.append(w).append(' ').append(e[R.nextInt(e.length)]).append(' ');
                return b.toString().trim();
            }
            case "translit": return arg.isEmpty() ? null : translit(arg);
            case "ru": return arg.isEmpty() ? null : untranslit(arg);
            case "repeat": {
                int sp = arg.indexOf(' ');
                if (sp <= 0) return null;
                try {
                    int n = Math.max(1, Math.min(20, Integer.parseInt(arg.substring(0, sp).trim())));
                    String t = arg.substring(sp + 1);
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < n && b.length() + t.length() < 4000; k++) b.append(t).append('\n');
                    return b.toString().trim();
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "count": {
                if (arg.isEmpty()) return null;
                int words = arg.trim().split("\\s+").length;
                toast("🔢 Символов: " + arg.length() + " · без пробелов: " + arg.replaceAll("\\s", "").length() + " · слов: " + words);
                return CONSUMED;
            }

            // ---------- игры и веселье
            case "rps": {
                String[] names = {"камень", "ножницы", "бумага"};
                String[] icons = {"🪨", "✂️", "📄"};
                String a = arg.toLowerCase(RU).trim();
                int you = a.startsWith("к") || a.startsWith("r") ? 0 : a.startsWith("н") || a.startsWith("s") ? 1 : a.startsWith("б") || a.startsWith("p") ? 2 : R.nextInt(3);
                int bot = R.nextInt(3);
                String res = you == bot ? "Ничья 🤝" : (you + 1) % 3 == bot ? "Ты победил(а)! 🏆" : "Я победил! 😎";
                return "Ты: " + icons[you] + " " + names[you] + "\nЯ: " + icons[bot] + " " + names[bot] + "\n" + res;
            }
            case "roll": {
                String a = arg.isEmpty() ? "1d6" : arg.toLowerCase(Locale.ROOT).replace('д', 'd').replace('к', 'd');
                try {
                    String[] x = a.split("d");
                    int n = x[0].isEmpty() ? 1 : Integer.parseInt(x[0].trim());
                    int f = Integer.parseInt(x[1].trim());
                    if (n < 1 || n > 20 || f < 2 || f > 1000) return null;
                    StringBuilder b = new StringBuilder("🎲 " + n + "d" + f + ": ");
                    int sum = 0;
                    for (int k = 0; k < n; k++) {
                        int v = 1 + R.nextInt(f);
                        sum += v;
                        b.append(k == 0 ? "" : " + ").append(v);
                    }
                    if (n > 1) b.append(" = ").append(sum);
                    return b.toString();
                } catch (Exception e) {
                    return null;
                }
            }
            case "coin": return R.nextBoolean() ? "🪙 Орёл" : "🪙 Решка";
            case "yesno": return pick("✅ Да", "❌ Нет");
            case "percent": return (arg.isEmpty() ? "" : "❓ " + arg + "\n") + "📊 Вероятность: " + R.nextInt(101) + "%";
            case "rate": return arg.isEmpty() ? null : "⭐ " + arg + " — " + (1 + stableHash(arg, 10)) + "/10";
            case "iq": return "🧠 IQ " + (arg.isEmpty() ? me(account) : arg) + ": " + (70 + stableHash(arg.isEmpty() ? me(account) : arg, 91));
            case "when": return (arg.isEmpty() ? "" : "🔮 " + arg + "\n") + pick("Сегодня 😳", "Завтра ⏰", "Через неделю 📅", "Через месяц 🌙", "Через год 🎆", "Никогда 🙅", "Уже случилось 👀", "Когда рак на горе свистнет 🦞");
            case "truth": return "🤔 Правда: " + pick(TRUTH);
            case "dare": return "🔥 Действие: " + pick(DARE);
            case "would": return "🤷 Что бы ты выбрал(а): " + pick(WOULD);
            case "joke": return "😂 " + pick(JOKES);
            case "fact": return "🧠 Факт: " + pick(FACTS);
            case "compliment": return "💐 " + (arg.isEmpty() ? "Ты" : arg) + ", " + pick(COMPLIMENTS);
            case "motivate": return "💪 " + pick(MOTIVATE);
            case "sort": case "shuffle": {
                List<String> v = new ArrayList<>(Arrays.asList(arg.split("\\s*,\\s*")));
                v.removeIf(String::isEmpty);
                if (v.size() < 2) return null;
                if (cmd.equals("sort")) Collections.sort(v, String.CASE_INSENSITIVE_ORDER);
                else Collections.shuffle(v);
                StringBuilder b = new StringBuilder(cmd.equals("sort") ? "🔤 " : "🔀 ");
                for (int k = 0; k < v.size(); k++) b.append(k + 1).append(". ").append(v.get(k).trim()).append(k < v.size() - 1 ? "\n" : "");
                return b.toString();
            }
            case "me": return arg.isEmpty() ? null : "* " + me(account) + " " + arg + " *";

            // ---------- время
            case "date": {
                SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM yyyy", RU);
                Calendar c = Calendar.getInstance();
                return "📅 Сегодня " + df.format(c.getTime()) + " · день " + c.get(Calendar.DAY_OF_YEAR) + " из " + c.getActualMaximum(Calendar.DAY_OF_YEAR);
            }
            case "days": {
                Date d = parseDate(arg);
                if (d == null) return null;
                long n = daysUntil(d);
                String when = new SimpleDateFormat("d MMMM yyyy", RU).format(d);
                if (n == 0) return "🎯 " + when + " — это сегодня!";
                return n > 0 ? "⏳ До " + when + " " + plural(n, "остался", "осталось", "осталось") + " " + n + " " + plural(n, "день", "дня", "дней")
                        : "⌛ С " + when + " прошло " + (-n) + " " + plural(-n, "день", "дня", "дней");
            }
            case "ny": {
                Calendar c = Calendar.getInstance();
                c.set(c.get(Calendar.YEAR) + 1, Calendar.JANUARY, 1);
                long n = daysUntil(c.getTime());
                return "🎄 До Нового года " + plural(n, "остался", "осталось", "осталось") + " " + n + " " + plural(n, "день", "дня", "дней") + "!";
            }
            case "age": {
                Date d = parseDate(arg);
                if (d == null) return null;
                Calendar b = Calendar.getInstance(), now = Calendar.getInstance();
                b.setTime(d);
                int y = now.get(Calendar.YEAR) - b.get(Calendar.YEAR);
                if (now.get(Calendar.DAY_OF_YEAR) < b.get(Calendar.DAY_OF_YEAR)) y--;
                if (y < 0 || y > 150) return null;
                Calendar next = (Calendar) b.clone();
                next.set(Calendar.YEAR, now.get(Calendar.YEAR));
                if (daysUntil(next.getTime()) < 0) next.add(Calendar.YEAR, 1);
                long dn = daysUntil(next.getTime());
                return "🎂 " + y + " " + plural(y, "год", "года", "лет") + (dn == 0 ? " — и сегодня день рождения! 🎉" : " · до дня рождения " + dn + " " + plural(dn, "день", "дня", "дней"));
            }
            case "remind": {
                int sp = arg.indexOf(' ');
                try {
                    int min = Integer.parseInt((sp < 0 ? arg : arg.substring(0, sp)).trim());
                    if (min < 1 || min > 60 * 24 * 30) return null;
                    String text = sp < 0 ? "Напоминание" : arg.substring(sp + 1).trim();
                    int when = ConnectionsManager.getInstance(account).getCurrentTime() + min * 60;
                    StarLife.remindSelf(account, "⏰ " + text, when);
                    toast("⏰ Напомню через " + min + " " + plural(min, "минуту", "минуты", "минут") + " — придёт в «Избранное»");
                    return CONSUMED;
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            // ---------- полезное
            case "g": case "google": return search("https://www.google.com/search?q=", arg);
            case "yt": return search("https://www.youtube.com/results?search_query=", arg);
            case "ya": return search("https://yandex.ru/search/?text=", arg);
            case "wiki": return search("https://ru.wikipedia.org/w/index.php?search=", arg);
            case "pass": {
                int n = 16;
                try {
                    if (!arg.isEmpty()) n = Math.max(6, Math.min(64, Integer.parseInt(arg.trim())));
                } catch (NumberFormatException ignore) {
                }
                String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?";
                SecureRandom sr = new SecureRandom();
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < n; k++) b.append(chars.charAt(sr.nextInt(chars.length())));
                AndroidUtilities.addToClipboard(b.toString());
                toast("🔐 Пароль из " + n + " символов скопирован. В чат он не отправлен.");
                return CONSUMED;
            }
            case "uuid": {
                AndroidUtilities.addToClipboard(UUID.randomUUID().toString());
                toast("🆔 UUID скопирован");
                return CONSUMED;
            }
            case "bin": case "hex": case "dec": {
                try {
                    String a = arg.trim().toLowerCase(Locale.ROOT);
                    long v = a.startsWith("0x") ? Long.parseLong(a.substring(2), 16) : a.startsWith("0b") ? Long.parseLong(a.substring(2), 2) : Long.parseLong(a);
                    return arg.trim() + " = " + (cmd.equals("bin") ? "0b" + Long.toBinaryString(v) : cmd.equals("hex") ? "0x" + Long.toHexString(v).toUpperCase(Locale.ROOT) : String.valueOf(v));
                } catch (Exception e) {
                    return null;
                }
            }
            case "c2f": case "f2c": {
                try {
                    double v = Double.parseDouble(arg.trim().replace(',', '.'));
                    return cmd.equals("c2f") ? "🌡 " + StarPlugins.format(v) + "°C = " + StarPlugins.format(Math.round((v * 9 / 5 + 32) * 10) / 10.0) + "°F"
                            : "🌡 " + StarPlugins.format(v) + "°F = " + StarPlugins.format(Math.round(((v - 32) * 5 / 9) * 10) / 10.0) + "°C";
                } catch (Exception e) {
                    return null;
                }
            }
            case "morse": return arg.isEmpty() ? null : morse(arg);
            case "unmorse": return arg.isEmpty() ? null : unmorse(arg);
            case "b64": return arg.isEmpty() ? null : Base64.encodeToString(arg.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            case "unb64": {
                try {
                    return new String(Base64.decode(arg.trim(), Base64.DEFAULT), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return null;
                }
            }
            default: return StarCmds2.run(account, p, cmd, arg); // 5.4: ещё 32 команды
        }
    }

    private static String search(String base, String q) {
        if (q.isEmpty()) return null;
        try {
            return "🔎 " + q + "\n" + base + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
