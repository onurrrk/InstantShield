package com.instantshield;

import org.bstats.bukkit.Metrics;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InstantShield extends JavaPlugin implements Listener {

    private static final Method IS_HAND_RAISED_METHOD;

    private static final Class<?> DAMAGEABLE_CLASS;
    private static final Method GET_DAMAGE_METHOD;
    private static final Method SET_DAMAGE_METHOD;

    private final Map<UUID, Long> shieldUseMap = new ConcurrentHashMap<>();

    static {
        Method handRaised = null;
        try {
            handRaised = Player.class.getMethod("isHandRaised");
        } catch (Exception ignored) {}
        IS_HAND_RAISED_METHOD = handRaised;

        Class<?> damageableClass = null;
        Method getDamage = null;
        Method setDamage = null;
        try {
            damageableClass = Class.forName("org.bukkit.inventory.meta.Damageable");
            getDamage = damageableClass.getMethod("getDamage");
            setDamage = damageableClass.getMethod("setDamage", int.class);
        } catch (Exception ignored) {}
        DAMAGEABLE_CLASS = damageableClass;
        GET_DAMAGE_METHOD = getDamage;
        SET_DAMAGE_METHOD = setDamage;
    }

    @Override
    public void onEnable() {
        int pluginId = 34091;
        new Metrics(this, pluginId);

        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getCooldown(Material.SHIELD) > 0) {
            return;
        }

        shieldUseMap.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        shieldUseMap.remove(event.getPlayer().getUniqueId());
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

        Long lastShieldInteract = shieldUseMap.get(victim.getUniqueId());
        if (lastShieldInteract == null || (System.currentTimeMillis() - lastShieldInteract) > 450) {
            return;
        }

        if (IS_HAND_RAISED_METHOD != null) {
            try {
                if (!((boolean) IS_HAND_RAISED_METHOD.invoke(victim))) {
                    return;
                }
            } catch (Exception ignored) {}
        }

        ItemStack shield = null;
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (mainHand != null && mainHand.getType() == Material.SHIELD) {
            shield = mainHand;
        } else if (offHand != null && offHand.getType() == Material.SHIELD) {
            shield = offHand;
        }

        if (shield == null || victim.getCooldown(Material.SHIELD) > 0) {
            return;
        }

        Vector playerDirection = victim.getLocation().getDirection().setY(0).normalize();
        Vector damageSourceDirection = event.getDamager().getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0).normalize();

        if (playerDirection.dot(damageSourceDirection) < 0.0) {
            return;
        }

        event.setCancelled(true);
        playSoundSafely(victim, "ITEM_SHIELD_BLOCK", "BLOCK_ANVIL_LAND");

        if (victim.getGameMode() != GameMode.CREATIVE) {
            damageShield(victim, shield);
        }
    }

    private void damageShield(Player victim, ItemStack shield) {
        int maxDurability = shield.getType().getMaxDurability();

        if (DAMAGEABLE_CLASS != null) {
            try {
                ItemMeta meta = shield.getItemMeta();
                if (meta != null && DAMAGEABLE_CLASS.isInstance(meta)) {
                    int currentDamage = (int) GET_DAMAGE_METHOD.invoke(meta);
                    if (currentDamage + 1 >= maxDurability) {
                        shield.setAmount(0);
                        playSoundSafely(victim, "ITEM_SHIELD_BREAK", "ENTITY_ITEM_BREAK");
                        shieldUseMap.remove(victim.getUniqueId());
                    } else {
                        SET_DAMAGE_METHOD.invoke(meta, currentDamage + 1);
                        shield.setItemMeta(meta);
                    }
                    return;
                }
            } catch (Exception ignored) {}
        }

        short currentDamage = shield.getDurability();
        if (currentDamage + 1 >= maxDurability) {
            shield.setAmount(0);
            playSoundSafely(victim, "ITEM_SHIELD_BREAK", "ENTITY_ITEM_BREAK");
            shieldUseMap.remove(victim.getUniqueId());
        } else {
            shield.setDurability((short) (currentDamage + 1));
        }
    }

    private void playSoundSafely(Player player, String preferredSoundName, String fallbackSoundName) {
        Sound sound = resolveSound(preferredSoundName);
        if (sound == null) {
            sound = resolveSound(fallbackSoundName);
        }
        if (sound == null) {
            return;
        }
        try {
            player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    private Sound resolveSound(String soundName) {
        try {
            return Sound.valueOf(soundName);
        } catch (Exception ignored) {
            return null;
        }
    }
}
