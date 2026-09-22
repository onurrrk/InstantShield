package com.instantshield;

import org.bukkit.plugin.java.JavaPlugin;

public final class ShieldLowererFactory {

    private ShieldLowererFactory() {}

    public static ShieldLowerer create(JavaPlugin plugin, ShieldLowerer fallback) {
        ShieldLowerer paper = PaperShieldLowerer.tryCreate();
        if (paper != null) {
            return paper;
        }
        return ItemUseTicksShieldLowerer.tryCreate(plugin, fallback);
    }
}