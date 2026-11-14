/*
 * StarTegs — мод клиента Telegram для Android.
 * Лицензия: GNU GPL v2 или новее (как и исходный клиент Telegram).
 *
 * Весь код мода собран в этом файле, а в исходники Telegram вставляются только
 * короткие вызовы StarMod.* (см. client/patches/apply.py). Так мод легче
 * переносить на новые версии Telegram.
 *
 * NOTE: Full file temporarily reduced - will restore from backup commit 2410b6a
 * and re-apply Stage 1 patches. See StarAds.java for ad hiding.
 */
package org.startegs;

// TEMPORARY STUB - restoring full file next
public final class StarMod {
    private StarMod() {}
    public static final int OPTION_EDIT_HISTORY = 9001;
    public static final int OPTION_ASK_AI = 9002;
    public static final int MENU_PEER_INFO = 9301;
    public static final int MENU_LOCK = 9302;
    public static final int MENU_FILTER_USER = 9303;
    public static final int MENU_HUB = 9400;
    public static final int MENU_AI = 9401;
    public static final int MENU_HIDE = 9304;
    public static final int MENU_ADMINS = 9305;

    public static boolean isPro() { return true; }
    public static boolean requirePro(org.telegram.ui.ActionBar.BaseFragment f, String feature) { return true; }
    public static boolean allowIcon(int ordinal) { return true; }
    public static void toggle(String key, boolean def) {}
    public static boolean saveDeleted() { return true; }
    public static boolean saveEdits() { return true; }
    public static boolean showSeconds() { return false; }
    public static boolean ghostTyping() { return false; }
    public static boolean ghostRead() { return false; }
    public static boolean ghostOnline() { return false; }
    public static boolean ghostStories() { return false; }
    public static boolean hideStories() { return false; }
    public static boolean hidePromo() { return false; }
    public static boolean interceptDelete(int account, long scope, java.util.List<Integer> ids) { return false; }
    public static boolean isDeleted(int account, org.telegram.messenger.MessageObject m) { return false; }
    public static CharSequence decorateTime(int account, org.telegram.messenger.MessageObject m, CharSequence time) { return time; }
    public static void rememberEdits(int account, long dialogId, java.util.ArrayList<org.telegram.messenger.MessageObject> edited) {}
    public static boolean hasEdits(int account, org.telegram.messenger.MessageObject m) { return false; }
    public static void showEditHistory(org.telegram.ui.ActionBar.BaseFragment fragment, org.telegram.messenger.MessageObject m) {}
    public static boolean filterMessage(org.telegram.messenger.MessageObject m) {
        try { return m != null && org.startegs.StarAds.shouldHideMessage(m); } catch (Exception e) { return false; }
    }
    public static void fillSettings(org.telegram.ui.ActionBar.BaseFragment fragment, java.util.ArrayList<org.telegram.ui.Components.UItem> items) {}
    public static boolean onSettingsClick(org.telegram.ui.ActionBar.BaseFragment f, int id, Runnable refresh) { return false; }
    public static boolean updateAvailable() { return false; }
    public static String updateName() { return ""; }
    public static void checkUpdate(org.telegram.ui.ActionBar.BaseFragment f, boolean force) {}
    public static boolean showChatsButton() { return true; }
    public static int avatarShape() { return 0; }
    public static int avatarRadius(int def, int fallback) { return def; }
    public static int avatarDrawableRadius(int def, int fallback) { return def; }
    public static void showStats(org.telegram.ui.ActionBar.BaseFragment f) {}
    public static void confirmClear(org.telegram.ui.ActionBar.BaseFragment f) {}
    public static boolean canAskAi(org.telegram.messenger.MessageObject m) { return false; }
    public static void askAi(org.telegram.ui.ActionBar.BaseFragment f, org.telegram.messenger.MessageObject m) {}
    public static void showPeerInfo(org.telegram.ui.ActionBar.BaseFragment f, long dialogId) {}
    public static String tierTitle() { return "Free"; }
    public static int color(String hex) { return 0; }
    public static int iconFor(String name) { return org.telegram.messenger.R.drawable.msg_fave; }
}
