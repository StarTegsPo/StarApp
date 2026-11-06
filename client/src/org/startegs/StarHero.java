/*
 * StarTegs — шапка экрана StarTegs в стиле StarApp: градиентная карточка с
 * приветствием, тарифом и кнопками быстрого доступа. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.Calendar;

final class StarHero {

    private StarHero() {}

    interface Actions {
        void open(int which); // 0 ИИ, 1 удалённые, 2 скрытые, 3 метки, 4 заметки
    }

    private static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 5 ? "Доброй ночи" : h < 12 ? "Доброе утро" : h < 18 ? "Добрый день" : "Добрый вечер";
    }

    private static String seasonEmoji() {
        int m = Calendar.getInstance().get(Calendar.MONTH);
        return m == 11 || m <= 1 ? "❄️" : m <= 4 ? "🌸" : m <= 7 ? "☀️" : "🍂";
    }

    static FrameLayout create(Context ctx, int account, Actions actions) {
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, m, m, AndroidUtilities.dp(4));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = AndroidUtilities.dp(18);
        card.setPadding(p, p, p, AndroidUtilities.dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF7B5CFF, 0xFF33A6FF});
        bg.setCornerRadius(AndroidUtilities.dp(22));
        card.setBackground(bg);

        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        String name = me != null ? UserObject.getFirstName(me) : "";
        TextView hello = text(ctx, greeting() + (name.isEmpty() ? "" : ", " + name) + " " + seasonEmoji(), 14, 0xDDFFFFFF, false);
        card.addView(hello);
        TextView title = text(ctx, "⭐ StarTegs", 26, 0xFFFFFFFF, true);
        card.addView(title);
        String tier = StarModConfig.ECO_BOT.isEmpty() ? "" : "Тариф: " + StarMod.tierTitle() + "  ·  ";
        card.addView(text(ctx, tier + "версия " + StarModConfig.MOD_VERSION, 13, 0xCCFFFFFF, false));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = AndroidUtilities.dp(14);
        String[][] btns = {{"🤖", "ИИ"}, {"🗑", "Удалённые"}, {"🙈", "Скрытые"}, {"🏷", "Метки"}, {"📝", "Заметки"}};
        for (int i = 0; i < btns.length; i++) {
            if (i == 0 && !StarAi.enabled()) continue;
            if (i == 2 && StarPrivacy.hideEntry()) continue;
            final int which = i;
            LinearLayout b = new LinearLayout(ctx);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView ic = text(ctx, btns[i][0], 20, 0xFFFFFFFF, false);
            ic.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0x33FFFFFF);
            ic.setBackground(circle);
            b.addView(ic, new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44)));
            TextView lb = text(ctx, btns[i][1], 11, 0xEEFFFFFF, false);
            lb.setGravity(Gravity.CENTER);
            lb.setSingleLine(true);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = AndroidUtilities.dp(4);
            b.addView(lb, llp);
            b.setOnClickListener(v -> actions.open(which));
            row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(row, rowLp);
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private static TextView text(Context ctx, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
