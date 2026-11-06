/*
 * StarTegs 2.1 — «Визитка»: карточка с QR-кодом, вашим @username, ботами и ссылками.
 * Можно показать с экрана или отправить картинкой/текстом. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class StarCard extends UniversalFragment {

    private static final int ID_SHARE = 100;
    private static final int ID_TEXT = 101;
    private static final int ID_EDIT = 102;
    private static final int ID_ROLE = 103;

    private LinearLayout card;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    @Override
    protected CharSequence getTitle() {
        return "🪪 Визитка";
    }

    private String username() {
        TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
        String u = me != null ? UserObject.getPublicUsername(me) : null;
        return u == null ? "" : u;
    }

    private String link() {
        String u = username();
        return u.isEmpty() ? "" : "https://t.me/" + u;
    }

    private ArrayList<String> links() {
        ArrayList<String> out = new ArrayList<>();
        String custom = prefs().getString("card_links", null);
        if (custom == null) {
            for (String[] s : StarModConfig.SERVICES) if (!s[2].isEmpty()) out.add(s[0] + " — @" + s[2]);
        } else {
            for (String l : custom.split("\n")) if (!l.trim().isEmpty()) out.add(l.trim());
        }
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

    private LinearLayout build(Context ctx) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = AndroidUtilities.dp(22);
        c.setPadding(p, p, p, p);
        int[] colors = StarTheme.activePalette() >= 0 ? new int[]{0xFF7B5CFF, 0xFF33A6FF} : new int[]{0xFF8E3CFF, 0xFF1FB6FF};
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(AndroidUtilities.dp(26));
        c.setBackground(bg);

        TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
        TextView name = tv(ctx, me != null ? UserObject.getUserName(me) : "", 24, 0xFFFFFFFF, true);
        name.setGravity(Gravity.CENTER);
        c.addView(name);
        String role = prefs().getString("card_role", "");
        if (!role.isEmpty()) {
            TextView r = tv(ctx, role, 15, 0xE6FFFFFF, false);
            r.setGravity(Gravity.CENTER);
            c.addView(r);
        }
        String u = username();
        if (!u.isEmpty()) {
            TextView un = tv(ctx, "@" + u, 16, 0xFFFFFFFF, false);
            un.setGravity(Gravity.CENTER);
            un.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            c.addView(un);

            FrameLayout qrBox = new FrameLayout(ctx);
            int qp = AndroidUtilities.dp(14);
            qrBox.setPadding(qp, qp, qp, qp);
            GradientDrawable white = new GradientDrawable();
            white.setColor(0xFFFFFFFF);
            white.setCornerRadius(AndroidUtilities.dp(20));
            qrBox.setBackground(white);
            ImageView qr = new ImageView(ctx);
            try {
                HashMap<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                hints.put(EncodeHintType.MARGIN, 0);
                qr.setImageBitmap(new TelegramQRCodeWriter().encode(link(), 640, 640, hints, null));
            } catch (Exception e) {
                FileLog.e(e);
            }
            int size = AndroidUtilities.dp(190);
            qrBox.addView(qr, new FrameLayout.LayoutParams(size, size));
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ql.topMargin = AndroidUtilities.dp(16);
            ql.bottomMargin = AndroidUtilities.dp(12);
            c.addView(qrBox, ql);
        } else {
            TextView hint = tv(ctx, "Задайте имя пользователя в настройках Telegram — тогда здесь появится QR-код", 13, 0xDDFFFFFF, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
            c.addView(hint);
        }
        for (String l : links()) {
            TextView t = tv(ctx, "• " + l, 14, 0xF0FFFFFF, false);
            t.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2));
            c.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView foot = tv(ctx, "⭐ StarTegs", 12, 0x99FFFFFF, false);
        foot.setPadding(0, AndroidUtilities.dp(12), 0, 0);
        c.addView(foot);
        return c;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getParentActivity() != null) {
            card = build(getParentActivity());
            LinearLayout wrap = new LinearLayout(getParentActivity());
            int m = AndroidUtilities.dp(16);
            wrap.setPadding(m, m, m, m);
            wrap.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            items.add(UItem.asCustom(wrap));
        }
        items.add(UItem.asButton(ID_SHARE, R.drawable.msg_share, "Отправить картинкой").accent());
        items.add(UItem.asButton(ID_TEXT, R.drawable.msg_link, "Отправить текстом"));
        items.add(UItem.asButton(ID_ROLE, R.drawable.msg_edit, "Подпись под именем", prefs().getString("card_role", "").isEmpty() ? "нет" : ""));
        items.add(UItem.asButton(ID_EDIT, R.drawable.msg_edit, "Ссылки на визитке", String.valueOf(links().size())));
        items.add(UItem.asShadow("QR-код открывает ваш профиль в Telegram. Покажите экран — его можно отсканировать камерой."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        switch (item.id) {
            case ID_SHARE:
                shareImage(ctx);
                break;
            case ID_TEXT: {
                TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
                StringBuilder sb = new StringBuilder(me != null ? UserObject.getUserName(me) : "");
                String role = prefs().getString("card_role", "");
                if (!role.isEmpty()) sb.append("\n").append(role);
                if (!link().isEmpty()) sb.append("\n").append(link());
                for (String l : links()) sb.append("\n• ").append(l);
                ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()), "Визитка"));
                break;
            }
            case ID_ROLE:
                edit(ctx, "Подпись под именем", "Например: Дизайнер · Москва", "card_role", false);
                break;
            case ID_EDIT:
                edit(ctx, "Ссылки на визитке", "По одной в строке: канал, сайт, боты", "card_links", true);
                break;
        }
    }

    private void edit(Context ctx, String title, String hint, String key, boolean multi) {
        EditText input = new EditText(ctx);
        input.setHint(hint);
        if (multi) {
            input.setMinLines(4);
            input.setText(TextUtils.join("\n", links()));
        } else {
            input.setSingleLine(true);
            input.setText(prefs().getString(key, ""));
        }
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.length() > 1500) v = v.substring(0, 1500);
                    prefs().edit().putString(key, v).apply();
                    if (listView != null) listView.adapter.update(true);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void shareImage(Context ctx) {
        if (card == null || card.getWidth() == 0) return;
        try {
            Bitmap b = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(b));
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "StarTegs_vizitka.png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            b.recycle();
            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
            ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Визитка"));
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
