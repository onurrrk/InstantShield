package com.instantshield;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SwapShieldLowerer implements ShieldLowerer {

    private static final Material SHIELD = Material.getMaterial("SHIELD");

    private final JavaPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private static final class Pending {
        private final ItemStack item;
        private final boolean offHand;

        private Pending(ItemStack item, boolean offHand) {
            this.item = item;
            this.offHand = offHand;
        }
    }

    public SwapShieldLowerer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean lower(Player player, boolean offHand) {
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) {
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack current = offHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        if (current == null || SHIELD == null || current.getType() != SHIELD) {
            return false;
        }

        pending.put(id, new Pending(current.clone(), offHand));

        if (offHand) {
            inventory.setItemInOffHand(null);
        } else {
            inventory.setItemInMainHand(null);
        }

        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    restore(online);
                } else {
                    pending.remove(id);
                }
            }, 1L);
        } catch (Throwable t) {
            restore(player);
        }
        return true;
    }

    @Override
    public void restore(Player player) {
        Pending entry = pending.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack current = entry.offHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();

        if (current == null || current.getType() == Material.AIR) {
            if (entry.offHand) {
                inventory.setItemInOffHand(entry.item);
            } else {
                inventory.setItemInMainHand(entry.item);
            }
            return;
        }

        for (ItemStack rest : inventory.addItem(entry.item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(pending.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restore(player);
            }
        }
        pending.clear();
    }
}
