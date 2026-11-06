/*
 * StarTegs 3.0 — «Анонимные вопросы»: своя ссылка (поставить в био), список вопросов
 * и красивая карточка ответа для историй. Работает через бота StarTegs v59.
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class StarAsk extends UniversalFragment {

    private static final int ID_LINK = 100;
    private static final int ID_SHARE_LINK = 101;
    private static final int ID_REFRESH = 102;
    private static final int ID_Q = 1000;

    private String link;
    private final ArrayList<JSONObject> qs = new ArrayList<>();
    private boolean loading = true;
    private String error;

    @Override
    protected CharSequence getTitle() {
        return "🤫 Анонимные вопросы";
    }

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    private void load() {
        loading = true;
        StarCloud.call(currentAccount, "ask_link", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                link = res.optString("link");
                StarCloud.call(currentAccount, "ask_list", null, new StarCloud.Result() {
                    @Override
                    public void ok(JSONObject r2) {
                        qs.clear();
                        JSONArray a = r2.optJSONArray("items");
                        for (int i = 0; a != null && i < a.length(); i++) qs.add(a.optJSONObject(i));
                        loading = false;
                        error = null;
                        update();
                    }

                    @Override
                    public void fail(String why) {
                        loading = false;
                        error = why;
                        update();
                    }
                });
            }

            @Override
            public void fail(String why) {
                loading = false;
                error = why;
                update();
            }
        });
    }

    private void update() {
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(UItem.asShadow("⏳ Загружаю…"));
            return;
        }
        if (error != null) {
            items.add(UItem.asShadow("⚠️ " + error + "\n\nНужен бот StarTegs версии v59 и нажатый /start в @" + StarModConfig.ECO_BOT + "."));
            items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Попробовать снова"));
            return;
        }
        items.add(UItem.asHeader("Твоя ссылка"));
        items.add(UItem.asButton(ID_LINK, R.drawable.msg_link, "Скопировать ссылку", ""));
        items.add(UItem.asButton(ID_SHARE_LINK, R.drawable.msg_share, "Позвать друзей задавать вопросы").accent());
        items.add(UItem.asShadow("Поставь ссылку в био или в историю: «Задай мне вопрос анонимно 👀». Кто перейдёт — напишет вопрос боту, "
                + "а тебе он придёт без имени. Ссылки и @юзернеймы в вопросах скрываются."));
        items.add(UItem.asHeader(qs.isEmpty() ? "Вопросов пока нет" : "Вопросы · " + qs.size()));
        SimpleDateFormat f = new SimpleDateFormat("d MMM, HH:mm", new Locale("ru"));
        for (int i = 0; i < qs.size(); i++) {
            JSONObject q = qs.get(i);
            String t = q.optString("text").replace('\n', ' ');
            if (t.length() > 90) t = t.substring(0, 90) + "…";
            items.add(UItem.asButton(ID_Q + i, (q.optBoolean("new") ? "🆕 " : "💬 ") + t, f.format(new Date(q.optLong("ts") * 1000))));
        }
        if (!qs.isEmpty()) items.add(UItem.asShadow("Нажми на вопрос — сделаем карточку для истории, удалим или заблокируем отправителя (ты не узнаешь, кто это, и он тоже)."));
        items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Обновить"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        if (item.id == ID_REFRESH) {
            load();
            update();
            return;
        }
        if (item.id == ID_LINK && link != null) {
            AndroidUtilities.addToClipboard(link);
            Toast.makeText(ctx, "Ссылка скопирована — вставь в био ✨", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.id == ID_SHARE_LINK && link != null) {
            ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Задай мне вопрос анонимно 👀\n" + link), "Анонимные вопросы"));
            return;
        }
        int i = item.id - ID_Q;
        if (i < 0 || i >= qs.size()) return;
        JSONObject q = qs.get(i);
        new AlertDialog.Builder(ctx)
                .setTitle("🤫 Вопрос")
                .setMessage(q.optString("text"))
                .setPositiveButton("📸 Карточка", (d, w) -> card(q.optString("text")))
                .setNeutralButton("🚫 Заблокировать", (d, w) -> act("ask_block", q.optLong("id")))
                .setNegativeButton("🗑 Удалить", (d, w) -> act("ask_del", q.optLong("id")))
                .show();
    }

    private void act(String a, long id) {
        try {
            StarCloud.call(currentAccount, a, new JSONObject().put("id", id), new StarCloud.Result() {
                @Override
                public void ok(JSONObject res) {
                    if (getParentActivity() != null) Toast.makeText(getParentActivity(), a.equals("ask_block") ? "🚫 Этот человек больше не сможет спрашивать" : "Удалено", Toast.LENGTH_SHORT).show();
                    load();
                    update();
                }

                @Override
                public void fail(String why) {
                    if (getParentActivity() != null) Toast.makeText(getParentActivity(), why, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception ignore) {
        }
    }

    private static TextView tv(Context ctx, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Карточка 1080×1920 для истории: вопрос на градиенте. */
    private void card(String question) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        try {
            int w = 1080, h = 1920;
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(90, 90, 90, 90);
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF8E3CFF, 0xFFFF4F8B, 0xFFFFB347}));
            LinearLayout bubble = new LinearLayout(ctx);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(60, 50, 60, 60);
            GradientDrawable bb = new GradientDrawable();
            bb.setColor(0xFFFFFFFF);
            bb.setCornerRadius(60);
            bubble.setBackground(bb);
            TextView head = new TextView(ctx);
            head.setText("🤫 анонимный вопрос");
            head.setTextSize(TypedValue.COMPLEX_UNIT_PX, 44);
            head.setTextColor(0xFF8E3CFF);
            head.setGravity(Gravity.CENTER);
            bubble.addView(head);
            TextView q = new TextView(ctx);
            q.setText(question);
            q.setTextSize(TypedValue.COMPLEX_UNIT_PX, question.length() > 120 ? 54 : 70);
            q.setTextColor(0xFF1B1B24);
            q.setTypeface(Typeface.DEFAULT_BOLD);
            q.setGravity(Gravity.CENTER);
            q.setPadding(0, 30, 0, 0);
            bubble.addView(q);
            root.addView(bubble, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView foot = new TextView(ctx);
            foot.setText("Задай мне вопрос анонимно — ссылка в профиле ⭐ StarTegs");
            foot.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38);
            foot.setTextColor(0xEEFFFFFF);
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(0, 50, 0, 0);
            root.addView(foot);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "StarTegs_vopros.png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            b.recycle();
            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
            ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Ответить в истории"));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(ctx, "Не удалось сделать карточку", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
