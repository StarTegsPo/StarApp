/*
 * StarTegs — экран «Реклама, каналы, прокси» (этап 1, идеи из WhiteGram).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class StarAdsActivity extends UniversalFragment {

    private static final int ID_SPONSORED = 1;
    private static final int ID_KEYWORDS = 2;
    private static final int ID_ADMINS = 3;
    private static final int ID_WIDE = 4;
    private static final int ID_BOTTOM = 5;
    private static final int ID_STORY_CONFIRM = 6;
    private static final int ID_STORY_SWIPE = 7;
    private static final int ID_PROXY_AUTO = 8;
    private static final int ID_PROXY_REFRESH = 9;

    @Override
    protected CharSequence getTitle() {
        return "Реклама, каналы, прокси";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("🚫 Реклама"));
        items.add(UItem.asCheck(ID_SPONSORED, "Скрывать официальную рекламу").setChecked(StarAds.hideSponsored()));
        items.add(UItem.asCheck(ID_KEYWORDS, "Скрывать рекламу по словам (erid, «реклама»…)").setChecked(StarAds.hideKeywordAds()));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("📢 Каналы и истории"));
        items.add(UItem.asCheck(ID_ADMINS, "Просмотр админов без прав").setChecked(StarAds.viewAdminsAlways()));
        items.add(UItem.asCheck(ID_WIDE, "Широкие посты в каналах").setChecked(StarAds.wideChannelPosts()));
        items.add(UItem.asCheck(ID_BOTTOM, "Скрывать нижнюю панель канала").setChecked(StarAds.hideChannelBottomPanel()));
        items.add(UItem.asCheck(ID_STORY_CONFIRM, "Подтверждение просмотра историй").setChecked(StarAds.confirmStoryView()));
        items.add(UItem.asCheck(ID_STORY_SWIPE, "Не записывать истории свайпом").setChecked(StarAds.disableStorySwipeRecord()));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("🌐 Прокси"));
        items.add(UItem.asCheck(ID_PROXY_AUTO, "Авто-прокси").setChecked(StarAds.autoProxy()));
        items.add(UItem.asButton(ID_PROXY_REFRESH, R.drawable.msg_retry, "Найти свежие прокси…"));
        items.add(UItem.asShadow("Прокси берутся из открытого списка MTProto-прокси; каждый добавляется только после вашего подтверждения."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_SPONSORED: StarAds.toggleHideSponsored(); break;
            case ID_KEYWORDS: StarAds.toggleHideKeywordAds(); break;
            case ID_ADMINS: StarAds.toggleViewAdminsAlways(); break;
            case ID_WIDE: StarAds.toggleWideChannelPosts(); break;
            case ID_BOTTOM: StarAds.toggleHideChannelBottomPanel(); break;
            case ID_STORY_CONFIRM: StarAds.toggleConfirmStoryView(); break;
            case ID_STORY_SWIPE: StarAds.toggleDisableStorySwipeRecord(); break;
            case ID_PROXY_AUTO: StarAds.toggleAutoProxy(); break;
            case ID_PROXY_REFRESH: StarAds.refreshProxies(this); return;
            default: return;
        }
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
