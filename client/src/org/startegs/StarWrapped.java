/*
 * StarTegs 2.1 — «Итоги года»: красивая карточка со статистикой переписки за год,
 * которой можно поделиться. Считается только на телефоне, по сообщениям, загруженным в приложение.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StarWrapped extends UniversalFragment {

    private static final int ID_SHARE = 100;
    private static final int ID_RECOUNT = 101;

    private static final class Result {
        int year, mine, theirs, chats, days, peakHour, longest;
        String longestChat = "";
        final ArrayList<String[]> top = new ArrayList<>();    // имя, сообщений
        final ArrayList<String> words = new ArrayList<>();
        final ArrayList<String> emoji = new ArrayList<>();
        String busiestDay = "";
    }

    private Result result;
    private boolean counting;
    private LinearLayout poster;

    private static final HashSet<String> STOP = new HashSet<>();

    static {
        Collections.addAll(STOP, ("и в во не что он на я с со как а то все она так его но да ты к у же вы за бы по только ее мне было вот от меня еще нет о из ему "
                + "теперь когда даже ну вдруг ли если уже или ни быть был него до вас нибудь опять уж вам ведь там потом себя ничего ей может они тут где есть надо "
                + "ней для мы тебя их чем была сам чтоб без будто чего раз тоже себе под будет ж тогда кто этот того потому этого какой совсем ним здесь этом один "
                + "почти мой тем чтобы нее сейчас были куда зачем всех никогда можно при наконец два об другой хоть после над больше тот через эти нас про всего них "
                + "какая много разве три эту моя впрочем хорошо свою этой перед иногда лучше чуть том нельзя такой им более всегда конечно всю между это так вот "
                + "привет спасибо ладно давай окей ок типа просто очень тебе мне меня https http www com ru").split(" "));
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}]{4,}");
    private static final Pattern EMOJI = Pattern.compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");

    @Override
    protected CharSequence getTitle() {
        return "🎁 Итоги года";
    }

    @Override
    public boolean onFragmentCreate() {
        count();
        return super.onFragmentCreate();
    }

    private void count() {
        if (counting) return;
        counting = true;
        final int account = currentAccount;
        final MessagesController mc = MessagesController.getInstance(account);
        final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(mc.getDialogs(0));
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            Result r = new Result();
            Calendar c = Calendar.getInstance();
            r.year = c.get(Calendar.YEAR);
            c.set(r.year, Calendar.JANUARY, 1, 0, 0, 0);
            int from = (int) (c.getTimeInMillis() / 1000);
            HashMap<String, Integer> words = new HashMap<>(), emoji = new HashMap<>();
            HashMap<Long, Integer> perChat = new HashMap<>();
            int[] hours = new int[24];
            HashMap<Integer, Integer> perDay = new HashMap<>();
            long selfId = UserConfig.getInstance(account).getClientUserId();
            int scanned = 0;
            for (TLRPC.Dialog d : dialogs) {
                if (d == null || DialogObject.isEncryptedDialog(d.id) || d.id == selfId || d.last_message_date < from) continue;
                if (d.id < 0) {
                    TLRPC.Chat ch = mc.getChat(-d.id);
                    if (ch == null || org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(ch)) continue;
                }
                if (d.id > 0) {
                    TLRPC.User u = mc.getUser(d.id);
                    if (u == null || u.bot) continue;
                }
                ArrayList<StarTools.Msg> msgs = StarTools.readMessages(account, d.id, 4000);
                int inChat = 0;
                for (StarTools.Msg m : msgs) {
                    if (m.date < from) break;
                    inChat++;
                    if (m.out) {
                        r.mine++;
                        Calendar mc2 = Calendar.getInstance();
                        mc2.setTimeInMillis(m.date * 1000L);
                        hours[mc2.get(Calendar.HOUR_OF_DAY)]++;
                        int day = mc2.get(Calendar.DAY_OF_YEAR);
                        Integer pd = perDay.get(day);
                        perDay.put(day, pd == null ? 1 : pd + 1);
                        String t = m.text.toLowerCase();
                        Matcher w = WORD.matcher(t);
                        while (w.find()) {
                            String s = w.group();
                            if (STOP.contains(s)) continue;
                            Integer v = words.get(s);
                            words.put(s, v == null ? 1 : v + 1);
                        }
                        Matcher e = EMOJI.matcher(m.text);
                        while (e.find()) {
                            String s = e.group();
                            Integer v = emoji.get(s);
                            emoji.put(s, v == null ? 1 : v + 1);
                        }
                    } else {
                        r.theirs++;
                    }
                }
                if (inChat > 0) {
                    r.chats++;
                    perChat.put(d.id, inChat);
                }
                if (++scanned >= 120) break;
            }
            int best = 0;
            for (int h = 0; h < 24; h++) if (hours[h] > hours[best]) best = h;
            r.peakHour = best;
            r.days = perDay.size();
            int bestDay = -1, bestDayCount = 0;
            for (Map.Entry<Integer, Integer> e : perDay.entrySet()) {
                if (e.getValue() > bestDayCount) {
                    bestDayCount = e.getValue();
                    bestDay = e.getKey();
                }
            }
            if (bestDay > 0) {
                Calendar bd = Calendar.getInstance();
                bd.set(Calendar.DAY_OF_YEAR, bestDay);
                r.busiestDay = new java.text.SimpleDateFormat("d MMMM", new java.util.Locale("ru")).format(bd.getTime()) + " — " + bestDayCount + " сообщ.";
            }
            ArrayList<Map.Entry<Long, Integer>> chats = new ArrayList<>(perChat.entrySet());
            Collections.sort(chats, (a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < Math.min(5, chats.size()); i++) {
                r.top.add(new String[]{String.valueOf(chats.get(i).getKey()), String.valueOf(chats.get(i).getValue())});
            }
            r.words.addAll(top(words, 5));
            r.emoji.addAll(top(emoji, 5));
            AndroidUtilities.runOnUIThread(() -> {
                for (String[] t : r.top) t[0] = StarTools.dialogTitle(account, Long.parseLong(t[0]));
                result = r;
                counting = false;
                poster = null;
                if (listView != null) listView.adapter.update(true);
            });
        });
    }

    private static ArrayList<String> top(HashMap<String, Integer> map, int n) {
        ArrayList<Map.Entry<String, Integer>> l = new ArrayList<>(map.entrySet());
        Collections.sort(l, (a, b) -> b.getValue() - a.getValue());
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, l.size()); i++) out.add(l.get(i).getKey());
        return out;
    }

    private static TextView tv(Context ctx, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static LinearLayout block(Context ctx, String label, String value) {
        LinearLayout b = new LinearLayout(ctx);
        b.setOrientation(LinearLayout.VERTICAL);
        int p = AndroidUtilities.dp(14);
        b.setPadding(p, AndroidUtilities.dp(10), p, AndroidUtilities.dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x26FFFFFF);
        bg.setCornerRadius(AndroidUtilities.dp(16));
        b.setBackground(bg);
        b.addView(tv(ctx, label, 12, 0xCCFFFFFF, false));
        b.addView(tv(ctx, value, 18, 0xFFFFFFFF, true));
        return b;
    }

    private LinearLayout buildPoster(Context ctx) {
        Result r = result;
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = AndroidUtilities.dp(20);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF8E3CFF, 0xFF5A5CFF, 0xFF1FB6FF});
        bg.setCornerRadius(AndroidUtilities.dp(26));
        card.setBackground(bg);
        TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
        card.addView(tv(ctx, "⭐ StarTegs · Итоги " + r.year, 14, 0xDDFFFFFF, false));
        card.addView(tv(ctx, (me != null ? UserObject.getFirstName(me) + ", " : "") + "вот ваш год в Telegram", 22, 0xFFFFFFFF, true));
        ArrayList<View> blocks = new ArrayList<>();
        blocks.add(block(ctx, "Вы написали", String.format(java.util.Locale.ROOT, "%,d", r.mine).replace(',', ' ') + " сообщ."));
        blocks.add(block(ctx, "Вам написали", String.format(java.util.Locale.ROOT, "%,d", r.theirs).replace(',', ' ') + " сообщ."));
        blocks.add(block(ctx, "Живых чатов", String.valueOf(r.chats)));
        blocks.add(block(ctx, "Дней на связи", String.valueOf(r.days)));
        blocks.add(block(ctx, "Любимое время", String.format(java.util.Locale.ROOT, "%02d:00–%02d:00", r.peakHour, (r.peakHour + 1) % 24)));
        if (!r.emoji.isEmpty()) blocks.add(block(ctx, "Любимые эмодзи", android.text.TextUtils.join(" ", r.emoji)));
        for (int i = 0; i < blocks.size(); i += 2) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = AndroidUtilities.dp(10);
            for (int k = i; k < Math.min(i + 2, blocks.size()); k++) {
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (k > i) l.leftMargin = AndroidUtilities.dp(10);
                row.addView(blocks.get(k), l);
            }
            card.addView(row, rl);
        }
        if (!r.top.isEmpty()) {
            TextView h = tv(ctx, "🏆 Главные собеседники", 15, 0xFFFFFFFF, true);
            h.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(4));
            card.addView(h);
            String[] medals = {"🥇", "🥈", "🥉", "4.", "5."};
            for (int i = 0; i < r.top.size(); i++) {
                card.addView(tv(ctx, medals[i] + "  " + r.top.get(i)[0] + " — " + r.top.get(i)[1], 15, 0xF0FFFFFF, false));
            }
        }
        if (!r.words.isEmpty()) {
            TextView h = tv(ctx, "💬 Ваши слова года", 15, 0xFFFFFFFF, true);
            h.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(4));
            card.addView(h);
            card.addView(tv(ctx, "«" + android.text.TextUtils.join("», «", r.words) + "»", 15, 0xF0FFFFFF, false));
        }
        if (!r.busiestDay.isEmpty()) {
            TextView h = tv(ctx, "🔥 Самый болтливый день: " + r.busiestDay, 14, 0xE6FFFFFF, false);
            h.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            card.addView(h);
        }
        TextView foot = tv(ctx, "Сделано в StarTegs", 12, 0x99FFFFFF, false);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        card.addView(foot);
        return card;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (result == null) {
            items.add(UItem.asShadow("⏳ Считаю переписку за год… Это займёт несколько секунд."));
            return;
        }
        if (getParentActivity() != null) {
            if (poster == null) poster = buildPoster(getParentActivity());
            LinearLayout wrap = new LinearLayout(getParentActivity());
            int m = AndroidUtilities.dp(14);
            wrap.setPadding(m, m, m, m);
            if (poster.getParent() != null) ((ViewGroup) poster.getParent()).removeView(poster);
            wrap.addView(poster, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            items.add(UItem.asCustom(wrap));
        }
        items.add(UItem.asButton(ID_SHARE, R.drawable.msg_share, "Поделиться картинкой").accent());
        items.add(UItem.asButton(ID_RECOUNT, R.drawable.msg_retry, "Пересчитать"));
        items.add(UItem.asShadow("Считается только на телефоне, по сообщениям, которые загружены в приложение (до 4000 последних в каждом чате). "
                + "Каналы, боты и секретные чаты не учитываются. Никуда не отправляется, пока вы сами не поделитесь."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_RECOUNT) {
            result = null;
            if (listView != null) listView.adapter.update(true);
            count();
        } else if (item.id == ID_SHARE) {
            share();
        }
    }

    private void share() {
        Context ctx = getParentActivity();
        if (ctx == null || poster == null || poster.getWidth() == 0) return;
        try {
            Bitmap b = Bitmap.createBitmap(poster.getWidth(), poster.getHeight(), Bitmap.Config.ARGB_8888);
            poster.draw(new Canvas(b));
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "StarTegs_itogi_" + result.year + ".png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            b.recycle();
            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
            Intent send = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(send, "Итоги года"));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(ctx, "Не удалось сохранить картинку", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
