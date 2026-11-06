/*
 * StarTegs 3.0 — «Лучшие друзья»: топ-3 недели, совместимость с каждым («на одной волне на 87%»),
 * стрики и карточка для истории. Считается только на телефоне. Лицензия: GNU GPL v2 или новее.
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
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StarFriends extends UniversalFragment {

    private static final int ID_SHARE = 100;
    private static final int ID_STREAKS = 101;
    private static final int ID_FRIEND = 1000;

    static final class Friend {
        long id;
        String name;
        int week, total, compat, streak;
    }

    private final ArrayList<Friend> friends = new ArrayList<>();
    private boolean loading = true;
    private LinearLayout card;

    private static final Pattern EMOJI = Pattern.compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");

    @Override
    protected CharSequence getTitle() {
        return "💞 Лучшие друзья";
    }

    @Override
    public boolean onFragmentCreate() {
        count();
        return super.onFragmentCreate();
    }

    /** Совместимость 0–100: похожие часы активности, баланс переписки, скорость ответов и общие эмодзи. */
    static int compat(ArrayList<StarTools.Msg> msgs) {
        float[] hm = new float[24], ht = new float[24];
        HashSet<String> em = new HashSet<>(), et = new HashSet<>();
        int mine = 0, theirs = 0, fast = 0, replies = 0;
        StarTools.Msg prev = null;
        Calendar c = Calendar.getInstance();
        for (int i = msgs.size() - 1; i >= 0; i--) { // от старых к новым
            StarTools.Msg m = msgs.get(i);
            c.setTimeInMillis(m.date * 1000L);
            (m.out ? hm : ht)[c.get(Calendar.HOUR_OF_DAY)]++;
            if (m.out) mine++;
            else theirs++;
            Matcher e = EMOJI.matcher(m.text);
            while (e.find()) (m.out ? em : et).add(e.group());
            if (prev != null && prev.out != m.out) {
                replies++;
                if (m.date - prev.date < 600) fast++;
            }
            prev = m;
        }
        if (mine + theirs < 10) return 0;
        double dot = 0, a = 0, b = 0;
        for (int h = 0; h < 24; h++) {
            dot += hm[h] * ht[h];
            a += hm[h] * hm[h];
            b += ht[h] * ht[h];
        }
        double hours = a == 0 || b == 0 ? 0 : dot / Math.sqrt(a * b);
        double balance = (double) Math.min(mine, theirs) / Math.max(1, Math.max(mine, theirs));
        double speed = replies == 0 ? 0 : (double) fast / replies;
        HashSet<String> inter = new HashSet<>(em);
        inter.retainAll(et);
        HashSet<String> uni = new HashSet<>(em);
        uni.addAll(et);
        double emoji = uni.isEmpty() ? 0.5 : (double) inter.size() / uni.size();
        double score = 0.35 * hours + 0.25 * balance + 0.25 * speed + 0.15 * emoji;
        return (int) Math.round(40 + 60 * Math.min(1, score * 1.15)); // 40–100: друзья всё-таки 🙂
    }

    private void count() {
        final int account = currentAccount;
        final MessagesController mc = MessagesController.getInstance(account);
        final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(mc.getDialogs(0));
        final long self = UserConfig.getInstance(account).getClientUserId();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            int weekAgo = (int) (System.currentTimeMillis() / 1000) - 7 * 86400;
            ArrayList<Friend> out = new ArrayList<>();
            int n = 0;
            for (TLRPC.Dialog d : dialogs) {
                if (d == null || d.id <= 0 || d.id == self || StarHidden.isHidden(d.id)) continue;
                TLRPC.User u = mc.getUser(d.id);
                if (u == null || u.bot || u.deleted) continue;
                ArrayList<StarTools.Msg> msgs = StarTools.readMessages(account, d.id, 1500);
                Friend f = new Friend();
                f.id = d.id;
                for (StarTools.Msg m : msgs) if (m.date >= weekAgo) f.week++;
                f.total = msgs.size();
                f.compat = compat(msgs);
                f.streak = StarStreak.compute(msgs)[0];
                if (f.total > 0) out.add(f);
                if (++n >= 40) break;
            }
            Collections.sort(out, (x, y) -> y.week != x.week ? y.week - x.week : y.total - x.total);
            AndroidUtilities.runOnUIThread(() -> {
                for (Friend f : out) f.name = StarTools.dialogTitle(account, f.id);
                friends.clear();
                friends.addAll(out);
                StarAchieve.setMax("friends", Math.min(3, out.size()));
                for (Friend f : out) StarAchieve.setMax("streak", f.streak);
                loading = false;
                card = null;
                if (listView != null) listView.adapter.update(true);
            });
        });
    }

    private static TextView tv(Context ctx, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout buildCard(Context ctx) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = AndroidUtilities.dp(20);
        c.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF4F8B, 0xFF8E3CFF, 0xFF33A6FF});
        bg.setCornerRadius(AndroidUtilities.dp(26));
        c.setBackground(bg);
        c.addView(tv(ctx, "⭐ StarTegs · неделя", 13, 0xDDFFFFFF, false));
        c.addView(tv(ctx, "💞 Мои лучшие друзья", 22, 0xFFFFFFFF, true));
        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < Math.min(3, friends.size()); i++) {
            Friend f = friends.get(i);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            int rp = AndroidUtilities.dp(14);
            row.setPadding(rp, AndroidUtilities.dp(10), rp, AndroidUtilities.dp(10));
            GradientDrawable rb = new GradientDrawable();
            rb.setColor(0x26FFFFFF);
            rb.setCornerRadius(AndroidUtilities.dp(16));
            row.setBackground(rb);
            row.addView(tv(ctx, medals[i] + "  " + f.name, 17, 0xFFFFFFFF, true));
            row.addView(tv(ctx, "на одной волне на " + f.compat + "%" + (f.streak >= 3 ? "  ·  🔥" + f.streak + " дн." : "") + "  ·  " + f.week + " сообщ.", 13, 0xE6FFFFFF, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = AndroidUtilities.dp(10);
            c.addView(row, lp);
        }
        TextView foot = tv(ctx, "А кто в топе у тебя? 👀", 13, 0xCCFFFFFF, false);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, AndroidUtilities.dp(12), 0, 0);
        c.addView(foot);
        return c;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow("⏳ Считаю, с кем ты общаешься больше всего…"));
            return;
        }
        if (friends.isEmpty()) {
            items.add(UItem.asShadow("Пока не с кем сравнивать — пообщайся с друзьями, и здесь появится твой топ 💬"));
            return;
        }
        if (getParentActivity() != null) {
            if (card == null) card = buildCard(getParentActivity());
            if (card.getParent() != null) ((ViewGroup) card.getParent()).removeView(card);
            LinearLayout wrap = new LinearLayout(getParentActivity());
            int m = AndroidUtilities.dp(14);
            wrap.setPadding(m, m, m, m);
            wrap.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            items.add(UItem.asCustom(wrap));
            items.add(UItem.asButton(ID_SHARE, R.drawable.msg_share, "Поделиться в истории").accent());
        }
        items.add(UItem.asHeader("Совместимость"));
        for (int i = 0; i < Math.min(15, friends.size()); i++) {
            Friend f = friends.get(i);
            items.add(UItem.asButton(ID_FRIEND + i, f.name, f.compat + "%" + (f.streak >= 3 ? " · 🔥" + f.streak : "")));
        }
        items.add(UItem.asShadow("Совместимость — по тому, в одни ли часы вы пишете, как быстро отвечаете друг другу, поровну ли пишете и какие эмодзи любите. "
                + "Стрик 🔥 — сколько дней подряд вы оба писали друг другу (виден в списке чатов от 3 дней)."));
        items.add(UItem.asCheck(ID_STREAKS, "🔥 Показывать стрики в списке чатов").setChecked(StarStreak.enabled()));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_STREAKS) {
            StarStreak.toggle();
            if (listView != null) listView.adapter.update(true);
            return;
        }
        if (item.id == ID_SHARE) {
            share();
            return;
        }
        int i = item.id - ID_FRIEND;
        if (i >= 0 && i < friends.size()) {
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("user_id", friends.get(i).id);
            presentFragment(new org.telegram.ui.ChatActivity(args));
        }
    }

    private void share() {
        Context ctx = getParentActivity();
        if (ctx == null || card == null || card.getWidth() == 0) return;
        try {
            Bitmap b = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(b));
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "StarTegs_druzya.png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            b.recycle();
            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
            ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Лучшие друзья"));
            StarQuest.progress("share", 1);
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
