/*
 * StarTegs Business — кабинет: показатели, графики (время ответа, когда пишут клиенты, неотвеченные,
 * новые чаты, воронка, продажи в Маркете, топ клиентов), отчёт за неделю картинкой и экраны:
 * клиенты, неотвеченные, автоответы, ИИ-продавец, контент-план. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class StarBizActivity extends UniversalFragment {

    private static final int ID_BUY = 100;
    private static final int ID_REFRESH = 101;
    private static final int ID_TOGGLE = 102;
    private static final int ID_CLIENTS = 110;
    private static final int ID_UNANSWERED = 111;
    private static final int ID_RULES = 112;
    private static final int ID_SELLER = 113;
    private static final int ID_POSTS = 114;
    private static final int ID_REPORT = 115;

    /** Посчитанные данные кабинета. */
    static final class Data {
        float[] reply = new float[14];        // среднее время ответа по дням, мин
        String[] replyLabels = new String[14];
        int[][] heat = new int[7][24];
        int[] waitBuckets = new int[4];       // <1ч, 1–3ч, 3–24ч, >сутки
        int[] newChats = new int[8];          // по неделям
        String[] weekLabels = new String[8];
        ArrayList<long[]> waiting = new ArrayList<>(); // {dialog, сек}
        ArrayList<long[]> top = new ArrayList<>();     // {dialog, сообщений за 30 дн}
        int unanswered, newWeek, avgReply7, incomingWeek;
        int[] marketDays = new int[14];
        long marketSum;
        int marketOrders;
        String marketTop = "";
    }

    private Data data;
    private boolean loading;

    @Override
    protected CharSequence getTitle() {
        return "💼 StarTegs Business";
    }

    @Override
    public boolean onFragmentCreate() {
        StarBiz.refresh(currentAccount, () -> {
            if (StarBiz.on()) load();
            update();
        });
        if (StarBiz.on()) load();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    private void update() {
        if (listView != null) listView.adapter.update(true);
    }

    // ---------------------------------------------------------------- расчёт

    private void load() {
        if (loading) return;
        loading = true;
        final int account = currentAccount;
        final MessagesController mc = MessagesController.getInstance(account);
        final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(mc.getDialogs(0));
        final int now = ConnectionsManager.getInstance(account).getCurrentTime();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            Data d = new Data();
            Calendar cal = Calendar.getInstance();
            int todayIdx = StarStreak.dayIndex(System.currentTimeMillis());
            long[] replySum = new long[14];
            int[] replyN = new int[14];
            SimpleDateFormat df = new SimpleDateFormat("d.MM", Locale.ROOT);
            for (int i = 0; i < 14; i++) d.replyLabels[13 - i] = df.format(new Date(System.currentTimeMillis() - i * 86400_000L));
            for (int i = 0; i < 8; i++) d.weekLabels[7 - i] = i == 0 ? "эта" : "-" + i;
            int n = 0;
            long sumAll = 0;
            int cntAll = 0;
            for (TLRPC.Dialog dl : dialogs) {
                if (dl == null || dl.id <= 0 || StarHidden.isHidden(dl.id)) continue;
                TLRPC.User u = mc.getUser(dl.id);
                if (u == null || u.bot || u.self || u.support) continue;
                ArrayList<StarTools.Msg> msgs = StarTools.readMessages(account, dl.id, 600);
                if (msgs.isEmpty()) continue;
                StarTools.Msg last = msgs.get(0);
                if (!last.out) {
                    int w = now - last.date;
                    d.waitBuckets[w < 3600 ? 0 : w < 3 * 3600 ? 1 : w < 86400 ? 2 : 3]++;
                    if (w >= 3600) d.unanswered++;
                    if (w < 30 * 86400) d.waiting.add(new long[]{dl.id, w});
                }
                int first = msgs.get(msgs.size() - 1).date;
                int weeksAgo = (now - first) / (7 * 86400);
                if (msgs.size() < 600 && weeksAgo < 8) {
                    d.newChats[7 - weeksAgo]++;
                    if (weeksAgo == 0) d.newWeek++;
                }
                int incoming30 = 0;
                StarTools.Msg prev = null;
                for (int i = msgs.size() - 1; i >= 0; i--) { // от старых к новым
                    StarTools.Msg m = msgs.get(i);
                    if (!m.out && now - m.date < 30 * 86400) {
                        incoming30++;
                        cal.setTimeInMillis(m.date * 1000L);
                        int wd = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Пн=0
                        d.heat[wd][cal.get(Calendar.HOUR_OF_DAY)]++;
                        if (now - m.date < 7 * 86400) d.incomingWeek++;
                    }
                    if (prev != null && !prev.out && m.out) {
                        int delta = Math.min(m.date - prev.date, 86400);
                        int ago = todayIdx - StarStreak.dayIndex(m.date * 1000L);
                        if (ago >= 0 && ago < 14) {
                            replySum[13 - ago] += delta;
                            replyN[13 - ago]++;
                            if (ago < 7) {
                                sumAll += delta;
                                cntAll++;
                            }
                        }
                    }
                    prev = m;
                }
                if (incoming30 > 0) d.top.add(new long[]{dl.id, incoming30});
                if (++n >= 80) break;
            }
            for (int i = 0; i < 14; i++) d.reply[i] = replyN[i] == 0 ? 0 : replySum[i] / 60f / replyN[i];
            d.avgReply7 = cntAll == 0 ? 0 : (int) (sumAll / 60 / cntAll);
            Collections.sort(d.waiting, (a, b) -> Long.compare(b[1], a[1]));
            Collections.sort(d.top, (a, b) -> Long.compare(b[1], a[1]));
            AndroidUtilities.runOnUIThread(() -> {
                data = d;
                loading = false;
                update();
                loadMarket();
            });
        });
    }

    private void loadMarket() {
        StarCloud.call(currentAccount, "biz_market", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                if (data == null) return;
                JSONArray days = res.optJSONArray("days");
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                long today = StarStreak.dayIndex(System.currentTimeMillis());
                for (int i = 0; days != null && i < days.length(); i++) {
                    JSONObject o = days.optJSONObject(i);
                    try {
                        long idx = StarStreak.dayIndex(f.parse(o.optString("d")).getTime());
                        long ago = today - idx;
                        if (ago >= 0 && ago < 14) data.marketDays[13 - (int) ago] += o.optInt("n");
                        data.marketSum += o.optLong("sum");
                        data.marketOrders += o.optInt("n");
                    } catch (Exception ignore) {
                    }
                }
                JSONArray top = res.optJSONArray("top");
                if (top != null && top.length() > 0) data.marketTop = top.optJSONObject(0).optString("title");
                update();
            }

            @Override
            public void fail(String why) {
            }
        });
    }

    // ---------------------------------------------------------------- вид

    private static TextView tv(Context ctx, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Плитки показателей 2×2. */
    private View tiles(Context ctx, Data d) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(12);
        box.setPadding(pad, pad, pad, pad);
        String[][] t = {
                {"⏳ Ждут ответа", String.valueOf(d.unanswered), d.unanswered == 0 ? "все отвечены 👍" : "дольше часа"},
                {"⚡ Ответ в среднем", d.avgReply7 < 60 ? d.avgReply7 + " мин" : d.avgReply7 / 60 + " ч " + d.avgReply7 % 60 + " мин", "за 7 дней"},
                {"🆕 Новые чаты", String.valueOf(d.newWeek), "за неделю"},
                {"💰 Продажи", d.marketOrders > 0 ? String.format(Locale.ROOT, "%,d", d.marketSum).replace(',', ' ') : "—", d.marketOrders + " заказов · 30 дн"},
        };
        int[][] colors = {{0xFFFF7A45, 0xFFE2405A}, {0xFF7B5CFF, 0xFF33A6FF}, {0xFF1FB98A, 0xFF0F8F6A}, {0xFFF5B400, 0xFFE08A00}};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(ctx);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                LinearLayout tile = new LinearLayout(ctx);
                tile.setOrientation(LinearLayout.VERTICAL);
                int tp = AndroidUtilities.dp(14);
                tile.setPadding(tp, AndroidUtilities.dp(12), tp, AndroidUtilities.dp(12));
                GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[i]);
                bg.setCornerRadius(AndroidUtilities.dp(18));
                tile.setBackground(bg);
                tile.addView(tv(ctx, t[i][0], 12, 0xE6FFFFFF, false));
                tile.addView(tv(ctx, t[i][1], 22, 0xFFFFFFFF, true));
                tile.addView(tv(ctx, t[i][2], 11, 0xCCFFFFFF, false));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
                row.addView(tile, lp);
            }
            box.addView(row);
        }
        return box;
    }

    private int[] funnel() {
        int[] f = new int[StarBiz.STAGES.length];
        for (long[] c : StarBiz.clientList()) f[(int) Math.max(0, Math.min(f.length - 1, c[1]))]++;
        return f;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context ctx = getParentActivity();
        if (!StarBiz.paid()) {
            items.add(UItem.asHeader("Бизнес-режим"));
            items.add(UItem.asShadow("Кабинет для тех, кто продаёт и общается с клиентами в Telegram:\n\n"
                    + "📊 графики — время ответа, когда пишут клиенты, неотвеченные, новые чаты, воронка, продажи в Маркете\n"
                    + "👥 мини-CRM — этап, сумма, заметка и напоминание у каждого клиента\n"
                    + "⏳ неотвеченные — «⏳2ч» прямо в списке чатов\n"
                    + "⚡ автоответы по словам («цена» → прайс)\n"
                    + "🤖 ИИ-продавец — отвечает новым клиентам по вашему каталогу и зовёт вас к «горячим»\n"
                    + "🗓 контент-план для каналов и отчёт за неделю картинкой"));
            items.add(UItem.asButton(ID_BUY, R.drawable.msg_premium_badge, "Оформить Business — " + StarBiz.price() + " ⭐ / 30 дней").accent());
            items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Я уже оплатил — проверить"));
            return;
        }
        items.add(UItem.asCheck(ID_TOGGLE, "💼 Бизнес-режим включён").setChecked(StarBiz.enabled()));
        if (StarBiz.until() > 0) {
            items.add(UItem.asShadow("Подписка до " + new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date(StarBiz.until() * 1000))
                    + ". В режиме: этапы клиентов и «⏳» в списке чатов, автоответы и ИИ-продавец."));
        }
        if (!StarBiz.enabled()) return;
        if (data == null) {
            items.add(UItem.asShadow("⏳ Считаю показатели по перепискам…"));
        } else if (ctx != null) {
            Data d = data;
            items.add(UItem.asCustom(tiles(ctx, d)));
            items.add(UItem.asButton(ID_REPORT, R.drawable.msg_share, "📤 Отчёт за неделю картинкой").accent());

            items.add(UItem.asHeader("⚡ Время ответа клиентам, мин"));
            items.add(UItem.asCustom(StarChart.line(ctx, d.replyLabels, d.reply, "мин")));
            items.add(UItem.asHeader("🔥 Когда пишут клиенты (30 дней)"));
            items.add(UItem.asCustom(StarChart.heatmap(ctx, d.heat)));
            items.add(UItem.asShadow("Чем ярче клетка — тем больше сообщений в этот час. Планируйте ответы и посты на «горячие» часы."));
            items.add(UItem.asHeader("⏳ Сколько ждут ответа"));
            items.add(UItem.asCustom(StarChart.bars(ctx, new String[]{"<1 ч", "1–3 ч", "3–24 ч", ">сутки"}, d.waitBuckets)));
            items.add(UItem.asHeader("🆕 Новые чаты по неделям"));
            items.add(UItem.asCustom(StarChart.bars(ctx, d.weekLabels, d.newChats)));
            items.add(UItem.asHeader("🎯 Воронка клиентов"));
            String[] st = new String[StarBiz.STAGES.length];
            for (int i = 0; i < st.length; i++) st[i] = StarBiz.STAGES[i][0];
            items.add(UItem.asCustom(StarChart.bars(ctx, st, funnel())));
            items.add(UItem.asShadow("🆕 новый · 💬 в работе · 🤔 думает · 🔥 горячий · ✅ оплатил · ❌ отказ. Этап ставится в профиле клиента → ⋮ → «Карточка клиента»."));
            if (d.marketOrders > 0) {
                items.add(UItem.asHeader("🛒 Заказы в Маркете (14 дней)"));
                String[] ml = new String[14];
                for (int i = 0; i < 14; i++) ml[i] = d.replyLabels[i];
                items.add(UItem.asCustom(StarChart.bars(ctx, ml, d.marketDays)));
                if (!d.marketTop.isEmpty()) items.add(UItem.asShadow("Хит продаж: «" + d.marketTop + "»"));
            }
            if (!d.top.isEmpty()) {
                items.add(UItem.asHeader("👑 Топ клиентов (30 дней)"));
                for (int i = 0; i < Math.min(5, d.top.size()); i++) {
                    long id = d.top.get(i)[0];
                    JSONObject c = StarBiz.client(id);
                    String amount = c != null && c.optLong("amount") > 0 ? " · " + c.optLong("amount") : "";
                    items.add(UItem.asButton(5000 + i, StarTools.dialogTitle(currentAccount, id), d.top.get(i)[1] + " сообщ." + amount));
                }
            }
        }
        items.add(UItem.asHeader("Инструменты"));
        items.add(UItem.asButton(ID_CLIENTS, R.drawable.msg_contacts, "👥 Клиенты (CRM)", String.valueOf(StarBiz.clientList().size())));
        items.add(UItem.asButton(ID_UNANSWERED, R.drawable.msg_mute, "⏳ Неотвеченные", data != null ? String.valueOf(data.unanswered) : ""));
        items.add(UItem.asButton(ID_RULES, R.drawable.msg_edit, "⚡ Автоответы по словам", String.valueOf(StarBiz.rules().length())));
        if (StarAi.enabled()) items.add(UItem.asButton(ID_SELLER, R.drawable.msg_bot, "🤖 ИИ-продавец", StarBiz.seller() ? "вкл" : "выкл"));
        items.add(UItem.asButton(ID_POSTS, R.drawable.msg_calendar2, "🗓 Контент-план для каналов"));
        items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Пересчитать"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_BUY:
                StarBiz.buy(this);
                return;
            case ID_REFRESH:
                data = null;
                StarBiz.refresh(currentAccount, () -> {
                    if (StarBiz.on()) load();
                    update();
                });
                update();
                return;
            case ID_TOGGLE:
                StarBiz.setEnabled(!StarBiz.enabled());
                if (StarBiz.on()) load();
                getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
                update();
                return;
            case ID_CLIENTS:
                presentFragment(new Clients());
                return;
            case ID_UNANSWERED:
                presentFragment(new Unanswered());
                return;
            case ID_RULES:
                presentFragment(new Rules());
                return;
            case ID_SELLER:
                presentFragment(new Seller());
                return;
            case ID_POSTS:
                presentFragment(new Posts());
                return;
            case ID_REPORT:
                report();
                return;
        }
        if (item.id >= 5000 && data != null && item.id - 5000 < data.top.size()) openChat(this, data.top.get(item.id - 5000)[0]);
    }

    static void openChat(UniversalFragment f, long id) {
        Bundle args = new Bundle();
        if (id > 0) args.putLong("user_id", id);
        else args.putLong("chat_id", -id);
        f.presentFragment(new ChatActivity(args));
    }

    /** Отчёт за неделю: плитки и два графика одной картинкой. */
    private void report() {
        Context ctx = getParentActivity();
        if (ctx == null || data == null) return;
        try {
            int w = 1080;
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            root.setPadding(24, 32, 24, 32);
            TextView head = tv(ctx, "💼 Отчёт за неделю · " + new SimpleDateFormat("d MMMM", new Locale("ru")).format(new Date()), 22,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true);
            head.setPadding(24, 0, 24, 8);
            root.addView(head);
            root.addView(tiles(ctx, data));
            root.addView(tv(ctx, "  ⚡ Время ответа, мин", 15, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true));
            View line = StarChart.line(ctx, data.replyLabels, data.reply, "мин");
            root.addView(line, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(180)));
            root.addView(tv(ctx, "  ⏳ Сколько ждут ответа", 15, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true));
            View bars = StarChart.bars(ctx, new String[]{"<1 ч", "1–3 ч", "3–24 ч", ">сутки"}, data.waitBuckets);
            root.addView(bars, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(170)));
            TextView foot = tv(ctx, "Сделано в ⭐ StarTegs Business", 12, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
            foot.setPadding(24, 12, 24, 0);
            root.addView(foot);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            root.layout(0, 0, w, root.getMeasuredHeight());
            // графики анимируются при показе — для картинки рисуем сразу в конечном виде
            forceFinal(root);
            Bitmap b = Bitmap.createBitmap(w, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "StarTegs_otchet.png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            b.recycle();
            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
            ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Отчёт за неделю"));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(ctx, "Не удалось сделать отчёт", Toast.LENGTH_SHORT).show();
        }
    }

    private static void forceFinal(View v) {
        StarChart.finish(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) forceFinal(g.getChildAt(i));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    // ================================================================ клиенты

    public static class Clients extends UniversalFragment {
        private final ArrayList<long[]> list = new ArrayList<>();

        @Override
        protected CharSequence getTitle() {
            return "👥 Клиенты";
        }

        @Override
        public void onResume() {
            super.onResume();
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            list.clear();
            ArrayList<long[]> all = StarBiz.clientList();
            for (int s = 0; s < StarBiz.STAGES.length; s++) {
                long sum = 0;
                int cnt = 0;
                for (long[] c : all) if (c[1] == s) {
                    sum += c[2];
                    cnt++;
                }
                if (cnt == 0) continue;
                items.add(UItem.asHeader(StarBiz.STAGES[s][0] + " " + StarBiz.STAGES[s][1] + " · " + cnt + (sum > 0 ? " · " + sum : "")));
                for (long[] c : all) {
                    if (c[1] != s) continue;
                    JSONObject cl = StarBiz.client(c[0]);
                    String note = cl != null ? cl.optString("note") : "";
                    items.add(UItem.asButton(1000 + list.size(), StarTools.dialogTitle(currentAccount, c[0]),
                            (c[2] > 0 ? String.valueOf(c[2]) : "") + (note.isEmpty() ? "" : " 📝")));
                    list.add(c);
                }
            }
            items.add(UItem.asShadow(list.isEmpty() ? "Клиентов пока нет. Добавить: профиль собеседника → ⋮ → «Карточка клиента». Автоответы и ИИ-продавец добавляют новых сами."
                    : "Нажмите — открыть чат. Долгое нажатие — карточка клиента."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - 1000;
            if (i >= 0 && i < list.size()) openChat(this, list.get(i)[0]);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - 1000;
            if (i < 0 || i >= list.size()) return false;
            StarBiz.editClient(this, list.get(i)[0]);
            return true;
        }
    }

    // ================================================================ неотвеченные

    public static class Unanswered extends UniversalFragment {
        private final ArrayList<long[]> list = new ArrayList<>();

        @Override
        protected CharSequence getTitle() {
            return "⏳ Ждут ответа";
        }

        @Override
        public void onResume() {
            super.onResume();
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            list.clear();
            for (TLRPC.Dialog d : new ArrayList<>(getMessagesController().getDialogs(0))) {
                if (d == null || StarHidden.isHidden(d.id)) continue;
                int w = StarBiz.waiting(currentAccount, d.id);
                if (w >= 0 && w < 30 * 86400) list.add(new long[]{d.id, w});
            }
            Collections.sort(list, (a, b) -> Long.compare(b[1], a[1]));
            for (int i = 0; i < list.size(); i++) {
                long w = list.get(i)[1];
                String t = w < 3600 ? (w / 60) + " мин" : w < 86400 ? (w / 3600) + " ч" : (w / 86400) + " дн";
                items.add(UItem.asButton(1000 + i, (w >= 86400 ? "🔴 " : w >= 3600 ? "🟡 " : "🟢 ") + StarTools.dialogTitle(currentAccount, list.get(i)[0]), t));
            }
            items.add(UItem.asShadow(list.isEmpty() ? "Все ответы отправлены 👍" : "Личные чаты, где последнее сообщение от собеседника. В списке чатов у таких — «⏳» с временем ожидания."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - 1000;
            if (i >= 0 && i < list.size()) openChat(this, list.get(i)[0]);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    // ================================================================ автоответы

    public static class Rules extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "⚡ Автоответы";
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            JSONArray rs = StarBiz.rules();
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.optJSONObject(i);
                String reply = r.optString("r").replace('\n', ' ');
                items.add(UItem.asButton(1000 + i, "🔑 " + r.optString("k"), reply.length() > 24 ? reply.substring(0, 24) + "…" : reply));
            }
            items.add(UItem.asButton(100, R.drawable.msg_edit, "➕ Добавить автоответ").accent());
            items.add(UItem.asShadow("Если в личном сообщении клиента есть одно из слов — сразу уходит ваш ответ. Одному клиенту — не чаще раза в 30 минут на правило. "
                    + "Работает, пока приложение запущено и включён бизнес-режим."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            Runnable done = () -> {
                if (listView != null) listView.adapter.update(true);
            };
            if (item.id == 100) StarBiz.editRule(this, -1, done);
            else if (item.id >= 1000) StarBiz.editRule(this, item.id - 1000, done);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    // ================================================================ ИИ-продавец

    public static class Seller extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "🤖 ИИ-продавец";
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCheck(100, "ИИ отвечает новым клиентам").setChecked(StarBiz.seller()));
            String cat = StarBiz.catalog();
            items.add(UItem.asButton(101, R.drawable.msg_edit, "📦 Каталог и цены", cat.isEmpty() ? "заполнить" : cat.length() + " симв."));
            items.add(UItem.asShadow("Отвечает тем, кого нет в ваших контактах, — по каталогу, не выдумывая. Когда клиент готов купить, "
                    + "он получает этап 🔥 «Горячий», а вы — уведомление. Если вы ответили сами — ИИ молчит 15 минут. Лимиты — 20 ответов в час."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == 100) StarBiz.toggleSeller();
            else if (item.id == 101) {
                StarBiz.input(getParentActivity(), "📦 Каталог", "Товары, цены, доставка, оплата, часы работы — всё, что нужно знать продавцу",
                        StarBiz.catalog(), false, v -> {
                            StarBiz.setCatalog(v);
                            if (listView != null) listView.adapter.update(true);
                        });
                return;
            }
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    // ================================================================ контент-план

    public static class Posts extends UniversalFragment {
        private final ArrayList<Long> channels = new ArrayList<>();

        @Override
        protected CharSequence getTitle() {
            return "🗓 Контент-план";
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            channels.clear();
            for (TLRPC.Dialog d : new ArrayList<>(getMessagesController().getDialogs(0))) {
                if (d == null || d.id >= 0) continue;
                TLRPC.Chat c = getMessagesController().getChat(-d.id);
                if (c == null || !ChatObject.isChannelAndNotMegaGroup(c) || !ChatObject.canPost(c)) continue;
                items.add(UItem.asButton(1000 + channels.size(), "📢 " + c.title, "новый пост"));
                channels.add(d.id);
            }
            items.add(UItem.asShadow(channels.isEmpty() ? "У вас нет каналов, где можно публиковать."
                    : "Пост ставится в «Отложенные» канала — Telegram опубликует его сам в нужное время. «Лучшее время» — час, когда у прошлых постов больше всего просмотров."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - 1000;
            if (i < 0 || i >= channels.size()) return;
            long ch = channels.get(i);
            StarBiz.input(getParentActivity(), "📝 Новый пост", "Текст поста", "", false, text -> {
                if (text.isEmpty()) return;
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    int best = bestHour(currentAccount, ch); // база — только на её очереди
                    AndroidUtilities.runOnUIThread(() -> schedule(ch, text, best));
                });
            });
        }

        private void schedule(long ch, String text, int best) {
            if (getParentActivity() == null) return;
            {
                CharSequence[] when = {"Через 1 час", "Сегодня в 19:00", "Завтра в 10:00", "Завтра в лучшее время (" + best + ":00)"};
                new AlertDialog.Builder(getParentActivity())
                        .setTitle("Когда опубликовать?")
                        .setItems(when, (d, which) -> {
                            Calendar c = Calendar.getInstance();
                            if (which == 0) c.add(Calendar.HOUR_OF_DAY, 1);
                            else {
                                if (which != 1) c.add(Calendar.DAY_OF_YEAR, 1);
                                c.set(Calendar.HOUR_OF_DAY, which == 1 ? 19 : which == 2 ? 10 : best);
                                c.set(Calendar.MINUTE, 0);
                                if (c.getTimeInMillis() < System.currentTimeMillis() + 120_000) c.add(Calendar.DAY_OF_YEAR, 1);
                            }
                            SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(text, ch);
                            p.scheduleDate = (int) (c.getTimeInMillis() / 1000);
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(p);
                            Toast.makeText(getParentActivity(), "🗓 Пост запланирован на " + new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(c.getTime()), Toast.LENGTH_LONG).show();
                        })
                        .show();
            }
        }

        /** Час, в который у постов канала больше всего просмотров (по загруженным постам). */
        static int bestHour(int account, long dialogId) {
            long[] views = new long[24];
            int[] count = new int[24];
            SQLiteCursor c = null;
            try {
                c = MessagesStorage.getInstance(account).getDatabase().queryFinalized("SELECT data, date FROM messages_v2 WHERE uid = " + dialogId + " ORDER BY mid DESC LIMIT 300");
                Calendar cal = Calendar.getInstance();
                while (c.next()) {
                    NativeByteBuffer data = c.byteBufferValue(0);
                    if (data == null) continue;
                    TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (m == null || m.views <= 0) continue;
                    cal.setTimeInMillis(c.intValue(1) * 1000L);
                    int h = cal.get(Calendar.HOUR_OF_DAY);
                    views[h] += m.views;
                    count[h]++;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.dispose();
            }
            int best = 19;
            double bestAvg = 0;
            for (int h = 7; h < 24; h++) {
                if (count[h] == 0) continue;
                double avg = (double) views[h] / count[h];
                if (avg > bestAvg) {
                    bestAvg = avg;
                    best = h;
                }
            }
            return best;
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
