package com.startegs.warmup;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String[] KEYS = {
            "videos", "likeMin", "likeMax", "watchMin", "watchMax", "skipChance", "longChance"};
    private static final String[] LABELS = {
            "Сколько роликов пролистать (0 = без ограничения)",
            "Лайк через: от (роликов)",
            "Лайк через: до (роликов)",
            "Время просмотра: от (секунд)",
            "Время просмотра: до (секунд)",
            "Шанс быстро пролистать за 1–3 c (%)",
            "Шанс досмотреть подольше, до 2× «до» (%)"
    };
    private static final int[] DEFAULTS = {120, 1, 18, 5, 25, 15, 10};
    // поля, которые могут быть 0
    private static final boolean[] ZERO_OK = {true, false, false, false, false, true, true};

    private final EditText[] inputs = new EditText[KEYS.length];
    private TextView status;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Прогрев TikTok");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status);

        Button access = new Button(this);
        access.setText("1. Включить доступ (Спец. возможности)");
        access.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Найдите «Прогрев TikTok» в списке и включите", Toast.LENGTH_LONG).show();
        });
        root.addView(access);

        for (int i = 0; i < KEYS.length; i++) {
            TextView label = new TextView(this);
            label.setText(LABELS[i]);
            label.setPadding(0, dp(12), 0, 0);
            root.addView(label);
            EditText e = new EditText(this);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setText(String.valueOf(prefs.getInt(KEYS[i], DEFAULTS[i])));
            inputs[i] = e;
            root.addView(e);
        }

        Button start = new Button(this);
        start.setText("2. Старт — открыть TikTok");
        start.setOnClickListener(v -> startWarmup());
        root.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        Button stop = new Button(this);
        stop.setText("Стоп");
        stop.setOnClickListener(v -> {
            if (WarmupService.instance != null) WarmupService.instance.stop();
            refresh();
        });
        root.addView(stop);

        TextView help = new TextView(this);
        help.setTextColor(Color.GRAY);
        help.setPadding(0, dp(16), 0, 0);
        help.setText("Как пользоваться:\n"
                + "• Нажмите кнопку 1 и включите «Прогрев TikTok».\n"
                + "• Если Android пишет «Настройка недоступна / ограничена»: Настройки → Приложения → "
                + "Прогрев TikTok → ⋮ (вверху справа) → «Разрешить ограниченные настройки», затем снова кнопка 1.\n"
                + "• Нажмите «Старт» — откроется TikTok, через 5 секунд начнётся прогрев.\n"
                + "• Слева вверху появится плашка «■ Стоп» — нажмите её, чтобы остановить.\n"
                + "• Если выйти из TikTok, прогрев встаёт на паузу.\n"
                + "• Пока идёт прогрев, экран не гаснет — удобно для облачного телефона.\n\n"
                + "Важно: TikTok запрещает автоматизацию, не гоняйте прогрев часами подряд.");
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (WarmupService.instance == null) {
            status.setText("❌ Доступ не включён — нажмите кнопку 1");
            status.setTextColor(0xFFC62828);
        } else if (WarmupService.isRunning()) {
            status.setText("▶ Прогрев идёт");
            status.setTextColor(0xFF2E7D32);
        } else {
            status.setText("✅ Доступ включён — можно жать «Старт»");
            status.setTextColor(0xFF2E7D32);
        }
    }

    private void startWarmup() {
        if (WarmupService.instance == null) {
            Toast.makeText(this, "Сначала включите доступ (кнопка 1)", Toast.LENGTH_LONG).show();
            return;
        }
        int[] v = new int[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            try {
                v[i] = Integer.parseInt(inputs[i].getText().toString().trim());
            } catch (NumberFormatException e) {
                v[i] = DEFAULTS[i];
            }
            if (v[i] < 0 || (v[i] == 0 && !ZERO_OK[i])) v[i] = ZERO_OK[i] ? 0 : 1;
        }
        if (v[5] > 100) v[5] = 100;
        if (v[5] + v[6] > 100) v[6] = 100 - v[5];
        if (v[1] > v[2]) { int t = v[1]; v[1] = v[2]; v[2] = t; }
        if (v[3] > v[4]) { int t = v[3]; v[3] = v[4]; v[4] = t; }
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < KEYS.length; i++) {
            ed.putInt(KEYS[i], v[i]);
            inputs[i].setText(String.valueOf(v[i]));
        }
        ed.apply();

        Intent launch = null;
        for (String pkg : WarmupService.TIKTOK) {
            launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) break;
        }
        if (launch == null) {
            Toast.makeText(this, "TikTok не установлен", Toast.LENGTH_LONG).show();
            return;
        }
        WarmupService.instance.start(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
        startActivity(launch);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
