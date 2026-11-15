#!/usr/bin/env python3
"""Apply the StarTegs 3.2 channel/story feature hooks to client and Telegram sources.

Run after Telegram sources are checked out and before client/patches/apply.py:
    python3 client/patches/apply_stargram.py tg

Each edit is checked before any file is written; rerunning is safe.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
TG = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else sys.exit(__doc__)


def replace_once(text, old, new, label):
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def edit(path, edits):
    text = path.read_text(encoding="utf-8")
    for old, new, label in edits:
        text = replace_once(text, old, new, label)
    path.write_text(text, encoding="utf-8")
    print(f"  ✓ {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")


# Expose the settings through StarTegs Hub; flags alone are not useful if unreachable.
hub = ROOT / "client/src/org/startegs/StarHubActivity.java"
edit(hub, [
    ('    private static final int ID_HIDE_PROMO = 501;\n',
     '    private static final int ID_HIDE_PROMO = 501;\n    private static final int ID_CHANNEL_STORY_SETTINGS = 2300;\n',
     "StarHubActivity settings id"),
    ('        items.add(UItem.asHeader("Интерфейс"));\n',
     '        items.add(UItem.asHeader("Каналы и истории"));\n'
     '        items.add(UItem.asButton(ID_CHANNEL_STORY_SETTINGS, R.drawable.msg_settings_old, "Настройки каналов, историй и рекламы"));\n'
     '        items.add(UItem.asShadow("Широкие посты, нижняя панель канала, подтверждение историй, свайп-запись и фильтр рекламы."));\n'
     '        items.add(UItem.asHeader("Интерфейс"));\n',
     "StarHubActivity settings entry"),
    ('        switch (id) {\n',
     '        switch (id) {\n'
     '            case ID_CHANNEL_STORY_SETTINGS:\n'
     '                StarAds.showSettings(this);\n'
     '                return;\n',
     "StarHubActivity settings click"),
])

# Ensure these helpers exist in the Java source copied into the Telegram checkout.
ads = ROOT / "client/src/org/startegs/StarAds.java"
edit(ads, [
    ('    public static void toggleDisableStorySwipeRecord() { toggleFlag("story_no_swipe_record", false); }\n',
     '    public static void toggleDisableStorySwipeRecord() { toggleFlag("story_no_swipe_record", false); }\n'
     '    public static boolean blockSwipeStoryRecord() { return disableStorySwipeRecord(); }\n'
     '    public static boolean confirmStoryOpen() { return confirmStoryView(); }\n'
     '    public static void toggleConfirmStoryOpen() { toggleConfirmStoryView(); }\n'
     '\n'
     '    public static boolean confirmStoryOpen(BaseFragment fragment, Runnable action) {\n'
     '        if (!confirmStoryOpen()) {\n'
     '            if (action != null) action.run();\n'
     '            return true;\n'
     '        }\n'
     '        if (fragment == null || fragment.getParentActivity() == null) {\n'
     '            if (action != null) action.run();\n'
     '            return true;\n'
     '        }\n'
     '        new AlertDialog.Builder(fragment.getParentActivity())\n'
     '                .setTitle("История")\n'
     '                .setMessage("Открыть историю?")\n'
     '                .setPositiveButton("Открыть", (dialog, which) -> { if (action != null) action.run(); })\n'
     '                .setNegativeButton("Отмена", null)\n'
     '                .show();\n'
     '        return false;\n'
     '    }\n',
     "StarAds story helpers"),
])

stories = TG / "TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java"
edit(stories, [
    ('            StoryViewer storyViewer = fragment.getOrCreateStoryViewer();\n'
     '            storyViewer.doOnAnimationReady(() -> storiesController.setLoading(startFromDialogId, false));\n'
     '            boolean finalOnlySelfStories = onlySelfStories;\n'
     '            storyViewer.open(getContext(), null, peerIds, position, null, null, StoriesListPlaceProvider.of(recyclerListView).with(forward -> {\n'
     '                if (finalOnlySelfStories) {\n'
     '                    return;\n'
     '                }\n'
     '                if (forward) {\n'
     '                    boolean hidden = type == TYPE_ARCHIVE;\n'
     '                    storiesController.loadNextStories(hidden);\n'
     '                }\n'
     '            }).setPaginationParaments(type == TYPE_ARCHIVE, onlyUnreadStories, onlySelfStories), false);',
     '            Runnable openStory = () -> {\n'
     '                StoryViewer storyViewer = fragment.getOrCreateStoryViewer();\n'
     '                storyViewer.doOnAnimationReady(() -> storiesController.setLoading(startFromDialogId, false));\n'
     '                boolean finalOnlySelfStories = onlySelfStories;\n'
     '                storyViewer.open(getContext(), null, peerIds, position, null, null, StoriesListPlaceProvider.of(recyclerListView).with(forward -> {\n'
     '                    if (finalOnlySelfStories) {\n'
     '                        return;\n'
     '                    }\n'
     '                    if (forward) {\n'
     '                        boolean hidden = type == TYPE_ARCHIVE;\n'
     '                        storiesController.loadNextStories(hidden);\n'
     '                    }\n'
     '                }).setPaginationParaments(type == TYPE_ARCHIVE, onlyUnreadStories, onlySelfStories), false);\n'
     '            };\n'
     '            org.startegs.StarAds.confirmStoryOpen(fragment, openStory);',
     "DialogStoriesCell story-open confirmation"),
])

# Hide the channel-only bottom action panel without interfering with the message composer.
chat = TG / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"
edit(chat, [
    ('            public void setVisibility(int visibility) {\n'
     '                super.setVisibility(visibility);\n'
     '                bottomViewsVisibilityController.setViewVisible(BOTTOM_OVERLAY_CHAT_CONTAINER, visibility == VISIBLE, getMeasuredWidth() > 0);\n'
     '            }',
     '            public void setVisibility(int visibility) {\n'
     '                if (visibility == VISIBLE && org.startegs.StarAds.hideChannelBottomPanel()\n'
     '                        && ChatObject.isChannelAndNotMegaGroup(currentChat)) {\n'
     '                    visibility = GONE;\n'
     '                }\n'
     '                super.setVisibility(visibility);\n'
     '                bottomViewsVisibilityController.setViewVisible(BOTTOM_OVERLAY_CHAT_CONTAINER, visibility == VISIBLE, getMeasuredWidth() > 0);\n'
     '            }',
     "ChatActivity channel action panel"),
])

print("StarTegs channel/story hooks applied; run client/patches/apply.py next.")
