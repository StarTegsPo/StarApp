package com.startegs.warmup;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Premium и бесплатные лимиты.
 * Бесплатно: лайки, избранное, фото-посты, пропуск эфиров, темы, лучшие настройки — до FREE_DAILY постов в день.
 * Premium: без дневного лимита, авто-пропуск рекламы, комментарии (эмодзи и фразы), репосты с текстом.
 * Активация — кодом; в приложении хранятся только SHA-256 хеши кодов.
 */
final class Premium {

    static final int FREE_DAILY = 100;
    static final String BUY_URL = "https://t.me/StarTegsShop_bot";

    private static final String SALT = "tw-premium-v1:";
    private static final String[] HASHES = {
            "e4123a24af10e94e44d7f4a9e65315777155a1573a7d2585adff3177e3789eb9",
            "d51006a9d6f0d61970f06ebd7dcfcfa6aacf3b6ef643ca866c2b04e37cf8df83",
            "5a01d8f98b8d3d7018d7e1d1b19d4255ae748a84f014cbaffa60e7e2d18c8db5",
            "31697c2f1b74e82e13db8178bde6c1a15249c99e302a099a57fe2a5d79f37eb4",
            "66242be201a9190fb446c7de1ad6d7f8a029f9706bc213dd793f48a2a319e2fa",
            "ae821f38cebfe4c951d61b6c63632e83da18cc9b006a2e847135b0328910b533",
            "ef5f6cd78cdb6a64a5196a0784b308c8e5f72725965bad8f650fb8d1cd9041da",
            "c21ff500664d7931060fa995fd54208f72bbef5b7917aff75d2f6a99460d9d91",
            "a02387059cb9a752eb8dfdefa8d4abbe4800957a8adae90c8f973f56fbb58b4c",
            "e30b8127b6df527601efa36ab89cc362edefc381dc08c9166306b2ad112c2f53",
            "fb683a3e9e42c3c11a012f98e73f1f86dd4d07e8c8bfb8822ddba50116db5ba9",
            "e486ec02bf040335b023d233909fc95f4ff816161d2d04f03f8de1097167646d",
            "f4c5fec41236eb38448bd31cd7eed292d06428933a94d5ff9caf6992bfe7dce6",
            "217cdf9843a60f222b22441f7bad12a102656140fb3bfcb847abf42faeec9a0f",
            "254891f1c46695d6eafdcf5bc8c288f9da2d2a9fc5844ce39ff31fee5bb85a6e",
            "1c5e825a15add3c0084771e739317a3f86a33edf6224586bde52a53b0840a4c7",
            "65d48f70f352199a799f80987757fbee4ccc43b1cdd4069b71f2a613d355bacc",
            "025e9af7f1aed5713ae71bd11fb87c69364adf078650c9ec9801d0c8dc9aa0d9",
            "774e0ba1e80429d65c320ae44c0735ba48899d5f55db6f3c0356817ab8a9954c",
            "8790aca35c7c6724eb15ec913a579159996c6a21bc9fde57e997945dc25ee66d",
            "6a53fce736cede6e500d35b6d5b52705244e6ce44ab5a6ac1af695c5a4c55fae",
            "4832338c64b7603558dcc95fc5be19477a91a5e9015ae75d6d232403e2111f44",
            "d61342491760b1bce50b26f440130a3a492323d048b0fb4a44b458cc6c393bd7",
            "0f9dc79e4aaab99a5a128a8846dfc5e00fa7716e7ddeb7e9cb8d20a254eb9ec4",
            "0a4efafe2519939c26b4d578bb43ada0d8f7f2d1aa8bb3e9ed18b58bd0567c44",
            "e5651e7a8bbc41ea6fb483e65793fe7a72b901e5392993701a26dcb41cb1cc35",
            "f765bb464128b779a0ac2e7ec35d71291711ae1acae26475baed5cb9a387ace3",
            "4e0fb07c5725feebe1132a9cff6a9b9e46c0a833f157543f0fa282bcf8c2163c",
            "a7ae00235e966e89f8884ce7e9a4cc2defe3929d96fe954478ee4cdc515f78a5",
            "c8b506fff9b31ffa620d45a3c5e9065509d7041ecc07102b065bc038f8688da6",
            "ff99274ca6b1993a4ed47c9b0b69118e436e013b3f163c812451b6de67b75c12"
    };

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static boolean isActive(Context c) {
        return prefs(c).getBoolean("premium", false);
    }

    /** true — код верный, Premium включён. */
    static boolean activate(Context c, String code) {
        String norm = code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (norm.isEmpty()) return false;
        String h = sha256(SALT + norm);
        for (String x : HASHES) {
            if (x.equals(h)) {
                prefs(c).edit().putBoolean("premium", true).apply();
                return true;
            }
        }
        return false;
    }

    // ---------- дневной счётчик постов ----------

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    static int usedToday(Context c) {
        SharedPreferences p = prefs(c);
        return today().equals(p.getString("day", "")) ? p.getInt("dayCount", 0) : 0;
    }

    static int addToday(Context c) {
        int n = usedToday(c) + 1;
        prefs(c).edit().putString("day", today()).putInt("dayCount", n).apply();
        return n;
    }

    static int leftToday(Context c) {
        return Math.max(0, FREE_DAILY - usedToday(c));
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
