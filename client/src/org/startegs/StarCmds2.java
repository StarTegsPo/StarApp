/*
 * StarTegs 5.4 «Сотня»: ещё 32 команды в чате — настоящие опросы и анимированные кубики Telegram,
 * счёт и чаевые, конвертер, знаки зодиака, время в городах, игры и генераторы.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeSet;

final class StarCmds2 {

    private StarCmds2() {}

    private static final Random R = new Random();
    private static final Locale RU = new Locale("ru");

    static final String HELP = "\n🎲 АНИМИРОВАННЫЕ ИГРЫ TELEGRAM\n"
            + ".cube .dart .basket .football .bowling .slot — кубик, дартс, баскетбол, футбол, боулинг, слоты\n"
            + ".poll Вопрос | вариант | вариант — настоящий опрос\n"
            + "\n🧮 СЧЁТ И ПЕРЕВОД\n"
            + ".split 1500 3 — разделить счёт, .tip 1500 10 — чаевые, .pct 15 200 — 15% от 200\n"
            + ".conv 5 km mi — км/мили/м/фут/см/дюйм/кг/фунт/г/унция/л/галлон\n"
            + "\n🔮 ДАТЫ И ГОРОСКОПЫ\n"
            + ".zodiac 15.03 — знак зодиака, .chinese 2010 — год по восточному календарю\n"
            + ".weekday 01.01.2027 — день недели, .tz Токио — время в городе\n"
            + "\n✍️ ЕЩЁ ТЕКСТ\n"
            + ".mirror текст — ʇɔʞǝʇ вверх ногами, .tiny текст — ᵐᵃˡᵉⁿᵏᶦᵉ\n"
            + ".rw слова — обратный порядок слов, .anagram слово, .rainbow текст 🌈\n"
            + ".kaomoji (happy/sad/love/angry) — каомодзи, .hearts — стена сердечек\n"
            + "\n🎮 ВЕСЕЛЬЕ\n"
            + ".vs Маша Петя — битва, .riddle — загадка (ответ под спойлером), .fortune — печенье с предсказанием\n"
            + ".card — карта дня, .wisdom — мудрость, .nick — ник, .story — начало истории\n"
            + ".friends Маша Петя — дружба %, .team a, b, c, d — две команды, .lottery 6 49 — лотерея\n";

    private static String pick(String... a) {
        return a[R.nextInt(a.length)];
    }

    private static double num(String s) {
        return Double.parseDouble(s.trim().replace(',', '.').replace(" ", ""));
    }

    // ---------------------------------------------------------------- данные

    private static final String[][] ZODIAC = { // {знак, день начала, месяц начала}
            {"♑ Козерог", "22", "12"}, {"♒ Водолей", "20", "1"}, {"♓ Рыбы", "19", "2"}, {"♈ Овен", "21", "3"},
            {"♉ Телец", "20", "4"}, {"♊ Близнецы", "21", "5"}, {"♋ Рак", "21", "6"}, {"♌ Лев", "23", "7"},
            {"♍ Дева", "23", "8"}, {"♎ Весы", "23", "9"}, {"♏ Скорпион", "24", "10"}, {"♐ Стрелец", "23", "11"}};

    private static final String[] CHINESE = {"🐀 Крыса", "🐂 Бык", "🐅 Тигр", "🐇 Кролик", "🐉 Дракон", "🐍 Змея",
            "🐎 Лошадь", "🐐 Коза", "🐒 Обезьяна", "🐓 Петух", "🐕 Собака", "🐖 Свинья"};

    private static final String[][] CITIES = {
            {"москва", "Europe/Moscow"}, {"питер", "Europe/Moscow"}, {"санкт-петербург", "Europe/Moscow"}, {"калининград", "Europe/Kaliningrad"},
            {"самара", "Europe/Samara"}, {"екатеринбург", "Asia/Yekaterinburg"}, {"омск", "Asia/Omsk"}, {"новосибирск", "Asia/Novosibirsk"},
            {"красноярск", "Asia/Krasnoyarsk"}, {"иркутск", "Asia/Irkutsk"}, {"якутск", "Asia/Yakutsk"}, {"владивосток", "Asia/Vladivostok"},
            {"магадан", "Asia/Magadan"}, {"камчатка", "Asia/Kamchatka"}, {"минск", "Europe/Minsk"}, {"киев", "Europe/Kiev"},
            {"алматы", "Asia/Almaty"}, {"астана", "Asia/Almaty"}, {"ташкент", "Asia/Tashkent"}, {"баку", "Asia/Baku"},
            {"тбилиси", "Asia/Tbilisi"}, {"ереван", "Asia/Yerevan"}, {"стамбул", "Europe/Istanbul"}, {"лондон", "Europe/London"},
            {"париж", "Europe/Paris"}, {"берлин", "Europe/Berlin"}, {"рим", "Europe/Rome"}, {"мадрид", "Europe/Madrid"},
            {"дубай", "Asia/Dubai"}, {"дели", "Asia/Kolkata"}, {"бангкок", "Asia/Bangkok"}, {"пекин", "Asia/Shanghai"},
            {"сеул", "Asia/Seoul"}, {"токио", "Asia/Tokyo"}, {"сидней", "Australia/Sydney"}, {"нью-йорк", "America/New_York"},
            {"чикаго", "America/Chicago"}, {"лос-анджелес", "America/Los_Angeles"}, {"мехико", "America/Mexico_City"}, {"рио", "America/Sao_Paulo"}};

    private static final String UP_LAT_FROM = "abcdefghijklmnopqrstuvwxyz.,!?'()";
    private static final String UP_LAT_TO = "ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz˙'¡¿,)(";
    private static final String[][] UP_CYR = {{"а", "ɐ"}, {"б", "ƍ"}, {"в", "ʚ"}, {"г", "⅃"}, {"д", "ɓ"}, {"е", "ǝ"}, {"ё", "ǝ"},
            {"ж", "ж"}, {"з", "ε"}, {"и", "и"}, {"й", "ņ"}, {"к", "ʞ"}, {"л", "v"}, {"м", "w"}, {"н", "н"}, {"о", "о"}, {"п", "u"},
            {"р", "d"}, {"с", "ɔ"}, {"т", "ɯ"}, {"у", "ʎ"}, {"ф", "ф"}, {"х", "х"}, {"ц", "ǹ"}, {"ч", "Һ"}, {"ш", "m"}, {"щ", "m"},
            {"ъ", "q"}, {"ы", "ıq"}, {"ь", "q"}, {"э", "є"}, {"ю", "оı"}, {"я", "ʁ"}};
    private static final String TINY_FROM = "abcdefghijklmnoprstuvwxyz0123456789+-=()";
    private static final String TINY_TO = "ᵃᵇᶜᵈᵉᶠᵍʰᶦʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾";

    private static final String[][] KAOMOJI = {
            {"(＾▽＾)", "(•‿•)", "ヽ(・∀・)ﾉ", "(≧◡≦)", "＼(^o^)／"},
            {"(╥﹏╥)", "(｡•́︿•̀｡)", "(ಥ﹏ಥ)", "(っ˘̩╭╮˘̩)っ"},
            {"(♡°▽°♡)", "(づ｡◕‿‿◕｡)づ", "(｡♥‿♥｡)", "( ˘ ³˘)♥"},
            {"(╬ಠ益ಠ)", "(ノಠ益ಠ)ノ彡┻━┻", "(¬_¬)", "ヽ(`Д´)ﾉ"}};

    private static final String[][] RIDDLES = {
            {"Что можно увидеть с закрытыми глазами?", "Сон"},
            {"Что становится больше, если из него брать?", "Яма"},
            {"У отца есть шесть сыновей, у каждого одна сестра. Сколько детей?", "Семь"},
            {"Что принадлежит тебе, но другие пользуются этим чаще?", "Имя"},
            {"Без окон, без дверей — полна горница людей?", "Огурец"},
            {"Что идёт, не двигаясь с места?", "Время"},
            {"Чем больше сушишь, тем мокрее становится. Что это?", "Полотенце"},
            {"Что всегда перед нами, но мы его не видим?", "Будущее"},
    };
    private static final String[] FORTUNES = {"Сегодня тебя ждёт приятный сюрприз 🎁", "Скоро ты получишь хорошую новость 📩",
            "Смелый шаг сегодня принесёт удачу 🍀", "Кто-то думает о тебе прямо сейчас 💭", "Твоя идея окажется лучшей 💡",
            "Улыбнись — это сработает лучше любого плана 😊", "Не бойся спросить — ответ будет «да» ✅", "Впереди классные выходные 🎉"};
    private static final String[] CARDS = {"☀️ Солнце — день радости и удачи", "🌙 Луна — доверься интуиции", "⭐ Звезда — мечта ближе, чем кажется",
            "🎡 Колесо — всё меняется к лучшему", "🧙 Маг — у тебя всё получится", "💪 Сила — ты сильнее, чем думаешь",
            "🗼 Башня — неожиданный поворот", "❤️ Влюблённые — важный выбор сердцем", "🃏 Шут — время для нового приключения"};
    private static final String[] WISDOM = {"«Дорогу осилит идущий» — пословица", "«Будь собой, остальные роли уже заняты» — О. Уайльд",
            "«Счастье — это когда тебя понимают» — из фильма", "«Сделай сегодня то, за что завтра скажешь себе спасибо»",
            "«Не ошибается тот, кто ничего не делает»", "«Лучшее время посадить дерево было 20 лет назад. Второе лучшее — сейчас»",
            "«Мечты сбываются у тех, кто встаёт с дивана»", "«Улыбка — кратчайшее расстояние между людьми» — В. Борге"};
    private static final String[] NICK_A = {"Shadow", "Neon", "Pixel", "Frost", "Lunar", "Cosmo", "Silent", "Cyber", "Mystic", "Star", "Crimson", "Velvet"};
    private static final String[] NICK_B = {"Fox", "Wolf", "Ghost", "Rider", "Tiger", "Byte", "Nova", "Storm", "Raven", "Panda", "Blade", "Comet"};
    private static final String[] STORY = {"Однажды ночью мне пришло сообщение от незнакомца: «Не открывай дверь»…",
            "В школе появился новенький, и с первого дня все странно на него смотрели…",
            "Я нашёл старый телефон, а в нём — чат со мной из будущего…",
            "Утром все в городе проснулись и поняли, что интернет исчез…",
            "Мой кот впервые заговорил — и первым делом попросил пароль от Wi-Fi…"};

    // ---------------------------------------------------------------- единицы

    private static final Object[][] UNITS = { // {имена, группа, множитель к базовой}
            {new String[]{"km", "км"}, "len", 1000.0}, {new String[]{"m", "м"}, "len", 1.0}, {new String[]{"cm", "см"}, "len", 0.01},
            {new String[]{"mm", "мм"}, "len", 0.001}, {new String[]{"mi", "миль", "мили", "миля"}, "len", 1609.344},
            {new String[]{"ft", "фут", "футы"}, "len", 0.3048}, {new String[]{"in", "дюйм", "дюймы"}, "len", 0.0254},
            {new String[]{"kg", "кг"}, "mass", 1.0}, {new String[]{"g", "г"}, "mass", 0.001}, {new String[]{"lb", "фунт", "фунты"}, "mass", 0.45359237},
            {new String[]{"oz", "унция", "унции"}, "mass", 0.028349523}, {new String[]{"l", "л"}, "vol", 1.0}, {new String[]{"ml", "мл"}, "vol", 0.001},
            {new String[]{"gal", "галлон", "галлоны"}, "vol", 3.785411784}};

    private static Object[] unit(String n) {
        String k = n.toLowerCase(RU).trim();
        for (Object[] u : UNITS) for (String s : (String[]) u[0]) if (s.equals(k)) return u;
        return null;
    }

    // ---------------------------------------------------------------- выполнение

    static String run(int account, SendMessagesHelper.SendMessageParams p, String cmd, String arg) {
        switch (cmd) {
            // ---------- анимированные игры: эмодзи из этого набора Telegram сам превращает в анимацию
            case "cube": return "🎲";
            case "dart": return "🎯";
            case "basket": return "🏀";
            case "football": return "⚽";
            case "bowling": return "🎳";
            case "slot": return "🎰";
            case "poll": {
                String[] parts = arg.split("\\s*\\|\\s*");
                if (parts.length < 3) {
                    toast("Формат: .poll Вопрос | вариант | вариант");
                    return StarCmds.CONSUMED;
                }
                TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
                poll.poll = new TLRPC.TL_poll();
                poll.poll.question = new TLRPC.TL_textWithEntities();
                poll.poll.question.text = parts[0].length() > 255 ? parts[0].substring(0, 255) : parts[0];
                for (int i = 1; i < parts.length && i <= 10; i++) {
                    if (parts[i].isEmpty()) continue;
                    TLRPC.PollAnswer a = new TLRPC.TL_pollAnswer();
                    a.text = new TLRPC.TL_textWithEntities();
                    a.text.text = parts[i].length() > 100 ? parts[i].substring(0, 100) : parts[i];
                    a.option = new byte[]{(byte) (48 + poll.poll.answers.size())};
                    poll.poll.answers.add(a);
                }
                if (poll.poll.answers.size() < 2) return null;
                poll.results = new TLRPC.TL_pollResults();
                StarSend.direct(account, SendMessagesHelper.SendMessageParams.of(poll, p.peer, p.replyToMsg, p.replyToTopMsg, null, null, p.notify, 0, 0));
                return StarCmds.CONSUMED;
            }

            // ---------- счёт
            case "split": {
                String[] a = arg.split("\\s+");
                try {
                    double sum = num(a[0]);
                    int n = Integer.parseInt(a[1]);
                    if (n < 1 || n > 1000) return null;
                    return "🧾 " + StarPlugins.format(sum) + " на " + n + " " + StarCmds.plural(n, "человека", "человек", "человек") + " = " + StarPlugins.format(Math.ceil(sum / n * 100) / 100) + " с каждого";
                } catch (Exception e) {
                    return null;
                }
            }
            case "tip": {
                String[] a = arg.split("\\s+");
                try {
                    double sum = num(a[0]), pct = a.length > 1 ? num(a[1].replace("%", "")) : 10;
                    double tip = Math.round(sum * pct) / 100.0;
                    return "💸 Чаевые " + StarPlugins.format(pct) + "% от " + StarPlugins.format(sum) + " = " + StarPlugins.format(tip) + " · итого " + StarPlugins.format(sum + tip);
                } catch (Exception e) {
                    return null;
                }
            }
            case "pct": {
                String[] a = arg.replace("%", "").split("\\s+");
                try {
                    double pc = num(a[0]), of = num(a[1]);
                    return "📊 " + StarPlugins.format(pc) + "% от " + StarPlugins.format(of) + " = " + StarPlugins.format(Math.round(of * pc) / 100.0);
                } catch (Exception e) {
                    return null;
                }
            }
            case "conv": {
                String[] a = arg.split("\\s+");
                if (a.length < 3) return null;
                try {
                    double v = num(a[0]);
                    Object[] from = unit(a[1]), to = unit(a[2]);
                    if (from == null || to == null || !from[1].equals(to[1])) return null;
                    double r = v * (Double) from[2] / (Double) to[2];
                    return "🔁 " + StarPlugins.format(v) + " " + a[1] + " = " + StarPlugins.format(Math.round(r * 1000) / 1000.0) + " " + a[2];
                } catch (Exception e) {
                    return null;
                }
            }

            // ---------- даты
            case "zodiac": {
                try {
                    String[] d = arg.trim().split("[./]");
                    int day = Integer.parseInt(d[0]), mon = Integer.parseInt(d[1]);
                    String sign = ZODIAC[0][0];
                    for (int i = ZODIAC.length - 1; i >= 0; i--) {
                        int sd = Integer.parseInt(ZODIAC[i][1]), sm = Integer.parseInt(ZODIAC[i][2]);
                        if (i == 0) break;
                        if (mon > sm || mon == sm && day >= sd) {
                            sign = ZODIAC[i][0];
                            break;
                        }
                    }
                    if (mon == 12 && day >= 22) sign = ZODIAC[0][0];
                    return "✨ " + day + "." + (mon < 10 ? "0" : "") + mon + " — " + sign;
                } catch (Exception e) {
                    return null;
                }
            }
            case "chinese": {
                try {
                    int y = Integer.parseInt(arg.trim());
                    return "🏮 " + y + " — год " + CHINESE[((y - 4) % 12 + 12) % 12].substring(3) + " " + CHINESE[((y - 4) % 12 + 12) % 12].substring(0, 2);
                } catch (Exception e) {
                    return null;
                }
            }
            case "weekday": {
                try {
                    SimpleDateFormat in = new SimpleDateFormat("dd.MM.yyyy", RU);
                    in.setLenient(false);
                    Date d = in.parse(arg.trim());
                    return "📅 " + arg.trim() + " — " + new SimpleDateFormat("EEEE", RU).format(d);
                } catch (Exception e) {
                    return null;
                }
            }
            case "tz": {
                String c = arg.trim().toLowerCase(RU);
                for (String[] x : CITIES) {
                    if (x[0].equals(c) || x[0].startsWith(c) && c.length() >= 3) {
                        SimpleDateFormat f = new SimpleDateFormat("HH:mm, EEEE", RU);
                        f.setTimeZone(TimeZone.getTimeZone(x[1]));
                        String name = Character.toUpperCase(x[0].charAt(0)) + x[0].substring(1);
                        return "🕒 " + name + ": " + f.format(new Date());
                    }
                }
                toast("Не знаю такой город. Попробуй: Москва, Токио, Лондон, Нью-Йорк, Дубай…");
                return StarCmds.CONSUMED;
            }

            // ---------- текст
            case "mirror": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                String low = arg.toLowerCase(RU);
                for (int i = low.length() - 1; i >= 0; i--) {
                    char ch = low.charAt(i);
                    int k = UP_LAT_FROM.indexOf(ch);
                    String rep = k >= 0 ? String.valueOf(UP_LAT_TO.charAt(k)) : null;
                    if (rep == null) for (String[] c : UP_CYR) if (c[0].charAt(0) == ch) rep = c[1];
                    b.append(rep != null ? rep : String.valueOf(ch));
                }
                return b.toString();
            }
            case "tiny": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                for (char ch : StarCmds.translit(arg).toLowerCase(Locale.ROOT).toCharArray()) {
                    int k = TINY_FROM.indexOf(ch);
                    b.append(k >= 0 ? TINY_TO.charAt(k) : ch);
                }
                return b.toString();
            }
            case "rw": {
                if (arg.isEmpty()) return null;
                List<String> w = new ArrayList<>(Arrays.asList(arg.trim().split("\\s+")));
                Collections.reverse(w);
                return String.join(" ", w);
            }
            case "anagram": {
                if (arg.isEmpty() || arg.length() > 40) return null;
                List<Character> c = new ArrayList<>();
                for (char ch : arg.trim().toCharArray()) c.add(ch);
                Collections.shuffle(c);
                StringBuilder b = new StringBuilder("🔤 ");
                for (char ch : c) b.append(ch);
                return b.toString();
            }
            case "rainbow": {
                if (arg.isEmpty()) return null;
                String[] h = {"❤️", "🧡", "💛", "💚", "💙", "💜"};
                StringBuilder b = new StringBuilder();
                String[] w = arg.trim().split("\\s+");
                for (int i = 0; i < w.length; i++) b.append(h[i % h.length]).append(' ').append(w[i]).append(' ');
                return b.append(h[w.length % h.length]).toString();
            }
            case "kaomoji": {
                String a = arg.toLowerCase(Locale.ROOT);
                int set = a.startsWith("sad") || a.startsWith("груст") ? 1 : a.startsWith("love") || a.startsWith("люб") ? 2 : a.startsWith("angry") || a.startsWith("зл") ? 3 : 0;
                return pick(KAOMOJI[set]);
            }
            case "hearts": {
                String[] h = {"❤️", "🧡", "💛", "💚", "💙", "💜", "🤍", "🩷"};
                StringBuilder b = new StringBuilder();
                for (int r = 0; r < 4; r++) {
                    for (int c = 0; c < 8; c++) b.append(h[(r + c) % h.length]);
                    b.append('\n');
                }
                return b.toString().trim();
            }

            // ---------- веселье
            case "vs": {
                String[] v = arg.split("\\s*(,|\\sи\\s|\\svs\\s)\\s*|\\s+");
                if (v.length < 2) return null;
                int a = 100, b = 100;
                StringBuilder s = new StringBuilder("⚔️ " + v[0] + " vs " + v[1] + "\n");
                String[] hits = {"бьёт подушкой", "кидает тапок", "щекочет", "бросает снежок", "читает рэп", "показывает мем"};
                for (int r = 1; r <= 3 && a > 0 && b > 0; r++) {
                    int d1 = 15 + R.nextInt(30), d2 = 15 + R.nextInt(30);
                    b -= d1;
                    a -= d2;
                    s.append("Раунд ").append(r).append(": ").append(v[0]).append(' ').append(pick(hits)).append(" (−").append(d1).append("), ")
                            .append(v[1]).append(' ').append(pick(hits)).append(" (−").append(d2).append(")\n");
                }
                s.append(a == b ? "🤝 Ничья!" : "🏆 Победа: " + (a > b ? v[0] : v[1]) + "!");
                return s.toString();
            }
            case "riddle": {
                String[] r = RIDDLES[R.nextInt(RIDDLES.length)];
                String text = "🧩 " + r[0] + "\nОтвет: " + r[1];
                TLRPC.TL_messageEntitySpoiler e = new TLRPC.TL_messageEntitySpoiler();
                e.offset = text.length() - r[1].length();
                e.length = r[1].length();
                p.message = text;
                p.entities = new ArrayList<>();
                p.entities.add(e);
                return StarCmds.KEEP;
            }
            case "fortune": return "🥠 " + pick(FORTUNES);
            case "card": return "🃏 Карта дня: " + pick(CARDS);
            case "wisdom": return "📜 " + pick(WISDOM);
            case "nick": return "🏷 Ник: " + pick(NICK_A) + pick(NICK_B) + (R.nextBoolean() ? "" : String.valueOf(R.nextInt(99)));
            case "story": return "📖 " + pick(STORY);
            case "friends": {
                String[] v = arg.split("\\s*(\\+|,|\\sи\\s)\\s*|\\s+");
                if (v.length < 2) return null;
                String x = v[0].toLowerCase(RU), y = v[1].toLowerCase(RU);
                int pct = Math.abs(((x.compareTo(y) < 0 ? x + "#" + y : y + "#" + x).hashCode() * 31) % 101);
                return "🤝 Дружба " + v[0] + " и " + v[1] + ": " + pct + "% " + (pct > 80 ? "— лучшие друзья навсегда 💯" : pct > 50 ? "— отличная пара друзей 😎" : "— есть над чем поработать 🙂");
            }
            case "team": {
                List<String> v = new ArrayList<>(Arrays.asList(arg.split("\\s*,\\s*")));
                v.removeIf(String::isEmpty);
                if (v.size() < 2) return null;
                Collections.shuffle(v);
                int half = (v.size() + 1) / 2;
                return "🔵 Команда 1: " + String.join(", ", v.subList(0, half)) + "\n🔴 Команда 2: " + String.join(", ", v.subList(half, v.size()));
            }
            case "lottery": {
                String[] a = arg.trim().isEmpty() ? new String[]{"6", "49"} : arg.trim().split("\\s+");
                try {
                    int n = Integer.parseInt(a[0]), max = a.length > 1 ? Integer.parseInt(a[1]) : 49;
                    if (n < 1 || n > 20 || max < n || max > 1000) return null;
                    TreeSet<Integer> s = new TreeSet<>();
                    while (s.size() < n) s.add(1 + R.nextInt(max));
                    return "🎟 " + n + " из " + max + ": " + s.toString().replace("[", "").replace("]", "");
                } catch (Exception e) {
                    return null;
                }
            }
            default: return StarCmds3.run(account, p, cmd, arg); // 5.5: ещё 24 команды
        }
    }

    private static void toast(String s) {
        Toast.makeText(ApplicationLoader.applicationContext, s, Toast.LENGTH_LONG).show();
    }
}
