package com.startegs.warmup;

import android.content.Context;

/** Темы оформления: тёмная (чёрно-красная), светлая и тёмно-красная. */
final class Theme {

    static final String DARK = "dark", LIGHT = "light", WINE = "wine";
    static final String[] IDS = {DARK, LIGHT, WINE};
    static final String[] TITLES = {"🖤 Тёмная", "☀️ Светлая", "🍷 Бордо"};

    final String id;
    final boolean light;
    final int bg, card, line, text, hint;
    final int a1, a2, a3;          // акценты: основной, светлый, глубокий
    final int good, bad;
    final int soft;                // фон мелких кнопок
    final int spark;               // цвет искр на фоне

    private Theme(String id, boolean light, int bg, int card, int line, int text, int hint,
                  int a1, int a2, int a3, int soft, int spark) {
        this.id = id;
        this.light = light;
        this.bg = bg;
        this.card = card;
        this.line = line;
        this.text = text;
        this.hint = hint;
        this.a1 = a1;
        this.a2 = a2;
        this.a3 = a3;
        this.good = light ? 0xFF16A34A : 0xFF34D399;
        this.bad = light ? 0xFFDC2626 : 0xFFFF5A5A;
        this.soft = soft;
        this.spark = spark;
    }

    static Theme byId(String id) {
        switch (id == null ? DARK : id) {
            case LIGHT:
                return new Theme(LIGHT, true,
                        0xFFF7F2F3, 0xEBFFFFFF, 0x14000000, 0xFF1B1B1F, 0xFF6F6A72,
                        0xFFE11D48, 0xFFFF6B6B, 0xFFB3122E, 0x12000000, 0xFFFF8FA3);
            case WINE:
                return new Theme(WINE, false,
                        0xFF1C0509, 0xB32D0B12, 0x2BFF9AA6, 0xFFFFF0F2, 0xFFCC9AA2,
                        0xFFFF3B4F, 0xFFFF8A65, 0xFF8A0F23, 0x26FFD1D6, 0xFFFFB199);
            default:
                return new Theme(DARK, false,
                        0xFF070708, 0xCC141416, 0x1FFFFFFF, 0xFFF5F5F6, 0xFF8E8E96,
                        0xFFE50914, 0xFFFF4D4D, 0xFF7A0008, 0x22FFFFFF, 0xFFFF6A3D);
        }
    }

    static Theme get(Context c) {
        return byId(c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("theme", DARK));
    }

    static void save(Context c, String id) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("theme", id).apply();
    }

    static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF);
    }
}
