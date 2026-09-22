package com.instantshield;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class PaperShieldLowerer implements ShieldLowerer {

    private final Method clearActiveItem;

    private PaperShieldLowerer(Method clearActiveItem) {
        this.clearActiveItem = clearActiveItem;
    }

    public static PaperShieldLowerer tryCreate() {
        try {
            return new PaperShieldLowerer(LivingEntity.class.getMethod("clearActiveItem"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean lower(Player player, boolean offHand) {
        try {
            clearActiveItem.invoke(player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
