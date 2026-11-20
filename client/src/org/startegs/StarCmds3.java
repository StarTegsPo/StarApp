/*
 * StarTegs 5.5 «Сотня» (часть 2): ещё 24 команды — игры для компании, шифры, столицы и флаги,
 * скороговорки, сон и вода, римские цифры, «Угадай число» и оформление текста.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

final class StarCmds3 {

    private StarCmds3() {}

    private static final Random R = new Random();
    private static final Locale RU = new Locale("ru");

    static final String HELP = "\n🎉 ДЛЯ КОМПАНИИ\n"
            + ".emojiquiz — угадай фильм по эмодзи, .neverhave — «Я никогда не…», .roast Имя — дружеская подколка\n"
            + ".excuse — отмазка, .meme текст — «Когда…», .tongue — скороговорка, .word — слово дня\n"
            + ".guess — игра «Угадай число» (потом .guess 50), .math — пример на скорость\n"
            + "\n✍️ ОФОРМЛЕНИЕ\n"
            + ".hype текст — 🔥 ТЕКСТ 🔥, .box текст — в рамке, .stars текст — ✦ со звёздами ✦\n"
            + ".countdown 5 — 5…4…3…2…1 🚀, .emojify текст — слова → эмодзи\n"
            + "\n🔐 ШИФРЫ\n"
            + ".cipher / .decipher текст — шифр Цезаря, .binary / .unbinary текст — двоичный код\n"
            + "\n🌍 ПОЛЕЗНОЕ\n"
            + ".capital Франция — столица, .flag Япония — флаг, .roman 2026 — римские цифры\n"
            + ".sleep 23:00 — когда вставать по циклам сна, .water 50 — норма воды (вес в кг)\n"
            + ".daysalive 15.03.2010 — сколько дней ты живёшь\n";

    private static String pick(String... a) {
        return a[R.nextInt(a.length)];
    }

    private static void toast(String s) {
        Toast.makeText(ApplicationLoader.applicationContext, s, Toast.LENGTH_LONG).show();
    }

    private static String spoilerAnswer(SendMessagesHelper.SendMessageParams p, String question, String answer) {
        String text = question + "\nОтвет: " + answer;
        TLRPC.TL_messageEntitySpoiler e = new TLRPC.TL_messageEntitySpoiler();
        e.offset = text.length() - answer.length();
        e.length = answer.length();
        p.message = text;
        p.entities = new ArrayList<>();
        p.entities.add(e);
        return StarCmds.KEEP;
    }

    private static final String[][] EMOJI_QUIZ = {{"🦁👑", "Король Лев"}, {"🚢🧊💔", "Титаник"}, {"🧙‍♂️💍🌋", "Властелин колец"},
            {"🕷️🧑", "Человек-паук"}, {"❄️👸⛄", "Холодное сердце"}, {"🧸🍯🐝", "Винни-Пух"}, {"🐠🔍", "В поисках Немо"},
            {"🦖🏝️", "Парк юрского периода"}, {"🧹⚡👓", "Гарри Поттер"}, {"🐼🥋", "Кунг-фу Панда"}, {"🚗⚡🏁", "Тачки"}, {"👻🚫", "Охотники за привидениями"}};
    private static final String[] NEVER = {"Я никогда не засыпал(а) на уроке 😴", "Я никогда не отправлял(а) сообщение не тому человеку 📩",
            "Я никогда не смеялся(ась) в неподходящий момент 😂", "Я никогда не ел(а) пиццу на завтрак 🍕", "Я никогда не пел(а) в душе 🎤",
            "Я никогда не проходил(а) игру за одну ночь 🎮", "Я никогда не притворялся(ась), что сплю 🙈", "Я никогда не забывал(а) домашку дома 📚"};
    private static final String[] ROASTS = {"печатает так долго, что Wi-Fi успевает уснуть 😴", "ставит будильник на 7:00, а встаёт в 7:59 ⏰",
            "читает сообщения и отвечает через три дня 🐢", "говорит «я на минутку» и пропадает на час ⌛", "любит мемы больше, чем домашку 📚",
            "делает 50 селфи, чтобы выложить одно 📸"};
    private static final String[] EXCUSES = {"Извини, телефон сел, а зарядка была у кота 🐱", "Я бы пришёл, но у меня интернет-детокс (на 5 минут) 📵",
            "Прости, я был занят спасением мира в игре 🎮", "У меня в это время плановый сон 😴", "Мне мама сказала, что я сегодня занят 🙃",
            "Я не опоздал, просто время шло слишком быстро ⏱"};
    private static final String[] TONGUE = {"Шла Саша по шоссе и сосала сушку", "Карл у Клары украл кораллы, а Клара у Карла украла кларнет",
            "На дворе трава, на траве дрова", "Ехал Грека через реку", "От топота копыт пыль по полю летит",
            "Четыре чёрненьких чумазеньких чертёнка чертили чёрными чернилами чертёж", "Корабли лавировали, лавировали, да не вылавировали"};
    private static final String[][] WORDS = {{"Серендипность", "умение случайно находить приятное"}, {"Мимими", "что-то очень милое"},
            {"Флексить", "показывать свои успехи"}, {"Эмпатия", "умение чувствовать других"}, {"Прокрастинация", "откладывать дела на потом"},
            {"Сатори", "внезапное озарение"}, {"Хюгге", "уют и тепло с близкими"}, {"Ламповый", "тёплый, душевный"}};
    private static final String[][] COUNTRIES = {{"россия", "Москва", "🇷🇺"}, {"беларусь", "Минск", "🇧🇾"}, {"украина", "Киев", "🇺🇦"},
            {"казахстан", "Астана", "🇰🇿"}, {"узбекистан", "Ташкент", "🇺🇿"}, {"армения", "Ереван", "🇦🇲"}, {"грузия", "Тбилиси", "🇬🇪"},
            {"азербайджан", "Баку", "🇦🇿"}, {"кыргызстан", "Бишкек", "🇰🇬"}, {"таджикистан", "Душанбе", "🇹🇯"}, {"турция", "Анкара", "🇹🇷"},
            {"франция", "Париж", "🇫🇷"}, {"германия", "Берлин", "🇩🇪"}, {"италия", "Рим", "🇮🇹"}, {"испания", "Мадрид", "🇪🇸"},
            {"великобритания", "Лондон", "🇬🇧"}, {"англия", "Лондон", "🇬🇧"}, {"польша", "Варшава", "🇵🇱"}, {"финляндия", "Хельсинки", "🇫🇮"},
            {"норвегия", "Осло", "🇳🇴"}, {"швеция", "Стокгольм", "🇸🇪"}, {"греция", "Афины", "🇬🇷"}, {"египет", "Каир", "🇪🇬"},
            {"сша", "Вашингтон", "🇺🇸"}, {"америка", "Вашингтон", "🇺🇸"}, {"канада", "Оттава", "🇨🇦"}, {"мексика", "Мехико", "🇲🇽"},
            {"бразилия", "Бразилиа", "🇧🇷"}, {"аргентина", "Буэнос-Айрес", "🇦🇷"}, {"китай", "Пекин", "🇨🇳"}, {"япония", "Токио", "🇯🇵"},
            {"корея", "Сеул", "🇰🇷"}, {"индия", "Нью-Дели", "🇮🇳"}, {"таиланд", "Бангкок", "🇹🇭"}, {"оаэ", "Абу-Даби", "🇦🇪"},
            {"австралия", "Канберра", "🇦🇺"}, {"португалия", "Лиссабон", "🇵🇹"}, {"нидерланды", "Амстердам", "🇳🇱"}, {"чехия", "Прага", "🇨🇿"}};
    private static final String[][] EMOJIFY = {{"кот", "🐱"}, {"кошка", "🐱"}, {"собака", "🐶"}, {"пёс", "🐶"}, {"любовь", "❤️"}, {"люблю", "❤️"},
            {"сердце", "❤️"}, {"солнце", "☀️"}, {"дождь", "🌧"}, {"снег", "❄️"}, {"огонь", "🔥"}, {"пицца", "🍕"}, {"кофе", "☕"},
            {"музыка", "🎵"}, {"игра", "🎮"}, {"школа", "🏫"}, {"дом", "🏠"}, {"машина", "🚗"}, {"деньги", "💰"}, {"звезда", "⭐"},
            {"цветы", "💐"}, {"торт", "🎂"}, {"смех", "😂"}, {"сон", "😴"}, {"книга", "📚"}, {"телефон", "📱"}, {"друг", "🤝"}, {"мир", "🌍"}};

    // «Угадай число»: одна игра на всё приложение
    private static int secret = -1, tries;

    private static String roman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) {
            n -= v[i];
            b.append(s[i]);
        }
        return b.toString();
    }

    private static String caesar(String t, int k) {
        String ru = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя", en = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder b = new StringBuilder();
        for (char c : t.toCharArray()) {
            boolean up = Character.isUpperCase(c);
            char l = Character.toLowerCase(c);
            int i;
            if ((i = ru.indexOf(l)) >= 0) l = ru.charAt(((i + k) % 33 + 33) % 33);
            else if ((i = en.indexOf(l)) >= 0) l = en.charAt(((i + k) % 26 + 26) % 26);
            b.append(up ? Character.toUpperCase(l) : l);
        }
        return b.toString();
    }

    static String run(int account, SendMessagesHelper.SendMessageParams p, String cmd, String arg) {
        switch (cmd) {
            case "emojiquiz": {
                String[] q = EMOJI_QUIZ[R.nextInt(EMOJI_QUIZ.length)];
                return spoilerAnswer(p, "🎬 Угадай фильм: " + q[0], q[1]);
            }
            case "neverhave": return "🙋 " + pick(NEVER) + "\n(у кого было — ставь 🔥)";
            case "roast": return "😏 " + (arg.isEmpty() ? "Кто-то в этом чате" : arg) + " " + pick(ROASTS);
            case "excuse": return "😅 " + pick(EXCUSES);
            case "meme": return arg.isEmpty() ? null : "Когда " + arg + "\n\n😐 → 😳 → 💀";
            case "tongue": return "👅 Скороговорка: " + pick(TONGUE) + ". А теперь быстро 3 раза!";
            case "word": {
                String[] w = WORDS[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % WORDS.length];
                return "📖 Слово дня: " + w[0] + " — " + w[1];
            }
            case "guess": {
                if (arg.isEmpty() || secret < 0) {
                    secret = 1 + R.nextInt(100);
                    tries = 0;
                    toast("🎯 Я загадал число от 1 до 100. Пиши .guess 50");
                    return StarCmds.CONSUMED;
                }
                try {
                    int g = Integer.parseInt(arg.trim());
                    tries++;
                    if (g == secret) {
                        String out = "🎉 Угадал(а) число " + secret + " за " + tries + " " + StarCmds.plural(tries, "попытку", "попытки", "попыток") + "!";
                        secret = -1;
                        return out;
                    }
                    toast(g < secret ? "⬆️ Больше, чем " + g : "⬇️ Меньше, чем " + g);
                    return StarCmds.CONSUMED;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "math": {
                int a = 2 + R.nextInt(20), b = 2 + R.nextInt(20);
                int op = R.nextInt(3);
                String q = op == 0 ? a + " + " + b : op == 1 ? (a + b) + " − " + b : a + " × " + b;
                int ans = op == 0 ? a + b : op == 1 ? a : a * b;
                return spoilerAnswer(p, "🧮 Кто быстрее: " + q + " = ?", String.valueOf(ans));
            }

            case "hype": return arg.isEmpty() ? null : "🔥🔥 " + arg.toUpperCase(RU) + " 🔥🔥";
            case "box": {
                if (arg.isEmpty() || arg.length() > 40) return null;
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < arg.length() + 2; i++) line.append('━');
                return "┏" + line + "┓\n┃ " + arg + " ┃\n┗" + line + "┛";
            }
            case "stars": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder("✦ ");
                for (char c : arg.toCharArray()) b.append(c == ' ' ? "  " : c + " ");
                return b.append("✦").toString();
            }
            case "countdown": {
                int n = 5;
                try {
                    if (!arg.isEmpty()) n = Math.max(2, Math.min(10, Integer.parseInt(arg.trim())));
                } catch (NumberFormatException ignore) {
                }
                StringBuilder b = new StringBuilder("⏳ ");
                for (int i = n; i >= 1; i--) b.append(i).append("… ");
                return b.append("🚀").toString();
            }
            case "emojify": {
                if (arg.isEmpty()) return null;
                StringBuilder b = new StringBuilder();
                for (String w : arg.split("\\s+")) {
                    String low = w.toLowerCase(RU).replaceAll("[^\\p{L}]", "");
                    String rep = null;
                    for (String[] e : EMOJIFY) if (low.startsWith(e[0])) rep = e[1];
                    b.append(rep != null ? w + " " + rep : w).append(' ');
                }
                return b.toString().trim();
            }
            case "cipher": return arg.isEmpty() ? null : "🔐 " + caesar(arg, 3);
            case "decipher": return arg.isEmpty() ? null : "🔓 " + caesar(arg.replaceFirst("^🔐\\s*", ""), -3);
            case "binary": {
                if (arg.isEmpty() || arg.length() > 60) return null;
                StringBuilder b = new StringBuilder();
                for (byte x : arg.getBytes(StandardCharsets.UTF_8)) {
                    String s = Integer.toBinaryString(x & 0xFF);
                    b.append("00000000".substring(s.length())).append(s).append(' ');
                }
                return b.toString().trim();
            }
            case "unbinary": {
                try {
                    String[] parts = arg.trim().split("\\s+");
                    byte[] out = new byte[parts.length];
                    for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i], 2);
                    return "💬 " + new String(out, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return null;
                }
            }
            case "capital":
            case "flag": {
                String c = arg.trim().toLowerCase(RU);
                for (String[] x : COUNTRIES) {
                    if (x[0].equals(c) || c.length() >= 4 && x[0].startsWith(c)) {
                        String name = Character.toUpperCase(x[0].charAt(0)) + x[0].substring(1);
                        return cmd.equals("flag") ? x[2] + " " + name : "🏛 Столица: " + name + " — " + x[1] + " " + x[2];
                    }
                }
                toast("Не знаю такую страну 🙈");
                return StarCmds.CONSUMED;
            }
            case "roman": {
                try {
                    int n = Integer.parseInt(arg.trim());
                    if (n < 1 || n > 3999) return null;
                    return "🏛 " + n + " = " + roman(n);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "sleep": {
                try {
                    String[] t = arg.trim().split("[:.]");
                    int h = Integer.parseInt(t[0]), m = t.length > 1 ? Integer.parseInt(t[1]) : 0;
                    Calendar c = Calendar.getInstance();
                    c.set(Calendar.HOUR_OF_DAY, h);
                    c.set(Calendar.MINUTE, m);
                    c.add(Calendar.MINUTE, 15); // время, чтобы уснуть
                    SimpleDateFormat f = new SimpleDateFormat("HH:mm", RU);
                    StringBuilder b = new StringBuilder("😴 Лёг(ла) в " + String.format(Locale.US, "%02d:%02d", h, m) + " — лучше встать в: ");
                    for (int cyc = 4; cyc <= 6; cyc++) {
                        Calendar k = (Calendar) c.clone();
                        k.add(Calendar.MINUTE, cyc * 90);
                        b.append(f.format(k.getTime())).append(cyc < 6 ? ", " : "");
                    }
                    return b.append(" (4–6 циклов по 90 мин)").toString();
                } catch (Exception e) {
                    return null;
                }
            }
            case "water": {
                try {
                    double kg = Double.parseDouble(arg.trim().replace(',', '.'));
                    if (kg < 20 || kg > 200) return null;
                    double l = Math.round(kg * 30 / 100.0) / 10.0;
                    return "💧 Примерно " + StarPlugins.format(l) + " л воды в день (≈ " + Math.round(l * 4) + " стаканов)";
                } catch (Exception e) {
                    return null;
                }
            }
            case "daysalive": {
                try {
                    SimpleDateFormat in = new SimpleDateFormat("dd.MM.yyyy", RU);
                    in.setLenient(false);
                    Date d = in.parse(arg.trim());
                    long days = (System.currentTimeMillis() - d.getTime()) / 86400000L;
                    if (days < 0) return null;
                    return "🌍 Ты живёшь уже " + String.format(Locale.US, "%,d", days).replace(',', ' ') + " " + StarCmds.plural(days, "день", "дня", "дней")
                            + " — это ≈ " + String.format(Locale.US, "%,d", days * 24).replace(',', ' ') + " часов!";
                } catch (Exception e) {
                    return null;
                }
            }
            default: return null;
        }
    }
}
