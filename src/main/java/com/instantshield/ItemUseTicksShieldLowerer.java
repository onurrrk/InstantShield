package com.instantshield;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ItemUseTicksShieldLowerer implements ShieldLowerer {

    private final JavaPlugin plugin;
    private final ShieldLowerer fallback;
    private final Method setItemInUseTicks;
    private final Method isHandRaised;

    private ItemUseTicksShieldLowerer(JavaPlugin plugin, ShieldLowerer fallback, Method setItemInUseTicks, Method isHandRaised) {
        this.plugin = plugin;
        this.fallback = fallback;
        this.setItemInUseTicks = setItemInUseTicks;
        this.isHandRaised = isHandRaised;
    }

    public static ItemUseTicksShieldLowerer tryCreate(JavaPlugin plugin, ShieldLowerer fallback) {
        try {
            Method set = LivingEntity.class.getMethod("setItemInUseTicks", int.class);
            Method raised = null;
            try {
                raised = LivingEntity.class.getMethod("isHandRaised");
            } catch (Throwable ignored) {}
            return new ItemUseTicksShieldLowerer(plugin, fallback, set, raised);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean lower(Player player, boolean offHand) {
        try {
            setItemInUseTicks.invoke(player, 1);
        } catch (Throwable ignored) {
            return false;
        }

        if (isHandRaised == null) {
            return true;
        }

        UUID id = player.getUniqueId();
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(id);
                if (online == null) {
                    return;
                }
                try {
                    if ((Boolean) isHandRaised.invoke(online)) {
                        fallback.lower(online, offHand);
                    }
                } catch (Throwable ignored) {}
            }, 3L);
        } catch (Throwable ignored) {}

        return true;
    }
}