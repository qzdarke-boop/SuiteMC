package com.psdk.vip;

import com.psdk.PSDK;
import com.psdk.vip.integrations.LuckPermsHook;
import com.psdk.vip.util.GGWaveManager;

public class VipManager {

    private final LuckPermsHook luckPermsHook;
    private final GGWaveManager ggWaveManager;

    public VipManager(PSDK plugin) {
        this.luckPermsHook = new LuckPermsHook(plugin);
        this.ggWaveManager = new GGWaveManager(plugin);
    }

    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public GGWaveManager getGgWaveManager() { return ggWaveManager; }

    public void shutdown() {
        ggWaveManager.stopWave();
    }
}
