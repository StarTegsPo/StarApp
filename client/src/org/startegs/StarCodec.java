/*
 * StarTegs 3.0 — невидимая метка в конце сообщения для друзей с модом
 * (эффект отправки, ход в игре). Обычный Telegram её не показывает.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import java.nio.charset.StandardCharsets;

final class StarCodec {

    private StarCodec() {}

    private static final String START = "⁠⁣⁠";
    private static final char[] DIGITS = {'⁡', '⁢', '⁣', '⁤'};

    /** Текст + невидимая метка с данными (ASCII/UTF-8). */
    static String attach(String text, String data) {
        byte[] b = data.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(text == null ? "" : text).append(START);
        for (byte x : b) {
            int v = x & 0xFF;
            for (int i = 3; i >= 0; i--) sb.append(DIGITS[(v >> (i * 2)) & 3]);
        }
        return sb.toString();
    }

    /** Данные из метки или null. */
    static String read(CharSequence text) {
        if (text == null) return null;
        String s = text.toString();
        int at = s.lastIndexOf(START);
        if (at < 0) return null;
        String tail = s.substring(at + START.length());
        if (tail.length() % 4 != 0 || tail.isEmpty()) return null;
        byte[] out = new byte[tail.length() / 4];
        for (int i = 0; i < out.length; i++) {
            int v = 0;
            for (int k = 0; k < 4; k++) {
                int d = digit(tail.charAt(i * 4 + k));
                if (d < 0) return null;
                v = (v << 2) | d;
            }
            out[i] = (byte) v;
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    /** Текст без метки (для копирования и показа). */
    static String strip(String text) {
        if (text == null) return null;
        int at = text.lastIndexOf(START);
        return at < 0 ? text : text.substring(0, at);
    }

    private static int digit(char c) {
        for (int i = 0; i < DIGITS.length; i++) if (DIGITS[i] == c) return i;
        return -1;
    }
}
