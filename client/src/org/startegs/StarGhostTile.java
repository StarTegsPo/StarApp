/*
 * StarTegs 4.9 — плитка «👻 Призрак» в шторке Android: включить или выключить призрака,
 * не открывая приложение. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.ComponentName;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.telegram.messenger.ApplicationLoader;

public class StarGhostTile extends TileService {

    static void requestUpdate() {
        if (Build.VERSION.SDK_INT < 24) return;
        try {
            TileService.requestListeningState(ApplicationLoader.applicationContext,
                    new ComponentName(ApplicationLoader.applicationContext, StarGhostTile.class));
        } catch (Throwable ignore) {
        }
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = StarMod.isPro() && StarGhost.any();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Призрак");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(on ? "невидим" : "выключен");
        t.updateTile();
    }

    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        StarGhost.quickToggle(this);
        update();
    }
}
