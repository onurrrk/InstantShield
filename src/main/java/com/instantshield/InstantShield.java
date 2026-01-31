package com.instantshield;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class InstantShield extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!getConfig().getBoolean("instant-shield-enabled")) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();

        if (victim.isBlocking()) {
            return;
        }

        if (!victim.isHandRaised()) {
            return;
        }

        ItemStack shield = null;
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (mainHand != null && mainHand.getType() == Material.SHIELD) {
            shield = mainHand;
        } else if (offHand != null && offHand.getType() == Material.SHIELD) {
            shield = offHand;
        }

        if (shield == null) {
            return;
        }

        if (victim.getCooldown(Material.SHIELD) > 0) {
            return;
        }

        Vector playerDirection = victim.getLocation().getDirection();
        Vector damageSourceDirection = event.getDamager().getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();

        if (playerDirection.dot(damageSourceDirection) < 0.0) {
            return;
        }

        event.setCancelled(true);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        
        if (!victim.getGameMode().name().equals("CREATIVE")) {
            short currentDamage = shield.getDurability();
            if (currentDamage + 1 >= shield.getType().getMaxDurability()) {
                shield.setAmount(0);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
            } else {
                shield.setDurability((short) (currentDamage + 1));
            }
        }
    }
}