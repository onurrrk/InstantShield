package com.instantshield;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class InstantShield extends JavaPlugin implements Listener {

    private static final Material SHIELD = Material.getMaterial("SHIELD");

    private static final long RAISE_WINDOW_MS = 450L;
    private static final int AXE_COOLDOWN_TICKS = 100;
    private static final boolean LEGACY_CHANCE_MODE = detectLegacyChanceMode();

    private static final Method IS_HAND_RAISED_METHOD;
    private static final Method GET_COOLDOWN_METHOD;
    private static final Method SET_COOLDOWN_METHOD;

    private static final Class<?> DAMAGEABLE_CLASS;
    private static final Method GET_DAMAGE_METHOD;
    private static final Method SET_DAMAGE_METHOD;

    private final Map<UUID, Long> shieldUseMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shieldDisabledMap = new ConcurrentHashMap<>();

    private volatile boolean instantShieldEnabled = true;

    private ShieldLowerer primaryLowerer;
    private SwapShieldLowerer swapLowerer;
    private UpdateChecker updateChecker;

    static {
        Method handRaised = null;
        try {
            handRaised = LivingEntity.class.getMethod("isHandRaised");
        } catch (Throwable ignored) {}
        IS_HAND_RAISED_METHOD = handRaised;

        Method getCooldown = null;
        Method setCooldown = null;
        try {
            getCooldown = HumanEntity.class.getMethod("getCooldown", Material.class);
            setCooldown = HumanEntity.class.getMethod("setCooldown", Material.class, int.class);
        } catch (Throwable ignored) {}
        GET_COOLDOWN_METHOD = getCooldown;
        SET_COOLDOWN_METHOD = setCooldown;

        Class<?> damageableClass = null;
        Method getDamage = null;
        Method setDamage = null;
        try {
            damageableClass = Class.forName("org.bukkit.inventory.meta.Damageable");
            getDamage = damageableClass.getMethod("getDamage");
            setDamage = damageableClass.getMethod("setDamage", int.class);
        } catch (Throwable ignored) {}
        DAMAGEABLE_CLASS = damageableClass;
        GET_DAMAGE_METHOD = getDamage;
        SET_DAMAGE_METHOD = setDamage;
    }

    @Override
    public void onEnable() {
        try {
            new Metrics(this, 34091);
        } catch (Throwable ignored) {}

        saveDefaultConfig();
        migrateConfig();
        instantShieldEnabled = getConfig().getBoolean("instant-shield-enabled", true);

        swapLowerer = new SwapShieldLowerer(this);
        primaryLowerer = ShieldLowererFactory.create(this, swapLowerer);

        getServer().getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("update-checker", true)) {
            updateChecker = new UpdateChecker(this);
        }
    }

    private void migrateConfig() {
        try {
            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            java.util.List<String> userLines = java.nio.file.Files.readAllLines(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);

            java.util.List<String> defaultLines;
            try (java.io.InputStream in = getResource("config.yml")) {
                if (in == null) {
                    return;
                }
                defaultLines = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .lines().collect(java.util.stream.Collectors.toList());
            }

            java.util.Map<String, String> userValues = new java.util.LinkedHashMap<>();
            for (String line : userLines) {
                String key = topLevelKey(line);
                if (key != null) {
                    userValues.put(key, line);
                }
            }

            java.util.Set<String> defaultKeys = new java.util.HashSet<>();
            java.util.List<String> newLines = new java.util.ArrayList<>();
            for (String line : defaultLines) {
                String key = topLevelKey(line);
                if (key == null) {
                    newLines.add(line);
                } else {
                    defaultKeys.add(key);
                    newLines.add(userValues.containsKey(key) ? userValues.get(key) : line);
                }
            }

            java.util.List<String> extraLines = new java.util.ArrayList<>();
            java.util.List<String> pendingComments = new java.util.ArrayList<>();
            for (String line : userLines) {
                String key = topLevelKey(line);
                if (line.isEmpty()) {
                    pendingComments.clear();
                } else if (line.startsWith("#")) {
                    pendingComments.add(line);
                } else if (key != null) {
                    if (!defaultKeys.contains(key)) {
                        if (!extraLines.isEmpty()) {
                            extraLines.add("");
                        }
                        extraLines.addAll(pendingComments);
                        extraLines.add(line);
                    }
                    pendingComments.clear();
                } else {
                    pendingComments.clear();
                }
            }

            if (!extraLines.isEmpty()) {
                while (!newLines.isEmpty() && newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.remove(newLines.size() - 1);
                }
                newLines.add("");
                newLines.addAll(extraLines);
            }

            if (newLines.equals(userLines)) {
                return;
            }

            java.nio.file.Files.write(configFile.toPath(), newLines, java.nio.charset.StandardCharsets.UTF_8);
            reloadConfig();
        } catch (Throwable ignored) {}
    }

    private static String topLevelKey(String line) {
        if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || line.startsWith("#")) {
            return null;
        }
        int colon = line.indexOf(':');
        return colon > 0 ? line.substring(0, colon).trim() : null;
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        if (swapLowerer != null) {
            swapLowerer.shutdown();
        }
        if (primaryLowerer != null) {
            primaryLowerer.shutdown();
        }
    }

    private static boolean detectLegacyChanceMode() {
        try {
            String[] parts = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major == 1 && minor <= 10;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isShield(ItemStack item) {
        return item != null && SHIELD != null && item.getType() == SHIELD;
    }

    private int getNativeCooldown(Player player) {
        if (GET_COOLDOWN_METHOD == null || SHIELD == null) {
            return 0;
        }
        try {
            return (Integer) GET_COOLDOWN_METHOD.invoke(player, SHIELD);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean isShieldOnCooldown(Player player) {
        if (getNativeCooldown(player) > 0) {
            return true;
        }
        Long until = shieldDisabledMap.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            shieldDisabledMap.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void disableShield(Player victim, boolean offHand) {
        if (SET_COOLDOWN_METHOD != null && SHIELD != null) {
            try {
                SET_COOLDOWN_METHOD.invoke(victim, SHIELD, AXE_COOLDOWN_TICKS);
            } catch (Throwable ignored) {}
        }
        shieldDisabledMap.put(victim.getUniqueId(), System.currentTimeMillis() + AXE_COOLDOWN_TICKS * 50L);
        shieldUseMap.remove(victim.getUniqueId());

        lowerShield(victim, offHand);
    }

    private void lowerShield(Player victim, boolean offHand) {
        if (primaryLowerer != null && primaryLowerer.lower(victim, offHand)) {
            return;
        }
        swapLowerer.lower(victim, offHand);
    }

    private boolean isAxeHit(Entity damager) {
        if (!(damager instanceof Player)) {
            return false;
        }
        ItemStack weapon = ((Player) damager).getInventory().getItemInMainHand();
        return weapon != null && weapon.getType().name().endsWith("_AXE");
    }

    private boolean shouldDisableShield(Entity damager) {
        if (!isAxeHit(damager)) {
            return false;
        }
        if (!LEGACY_CHANCE_MODE) {
            return true;
        }

        Player attacker = (Player) damager;
        double chance = 0.25D + 0.05D * getEfficiencyLevel(attacker.getInventory().getItemInMainHand());
        if (attacker.isSprinting()) {
            chance += 0.75D;
        }
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private int getEfficiencyLevel(ItemStack item) {
        if (item == null) {
            return 0;
        }
        try {
            for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                String name = enchantmentName(entry.getKey());
                if (name.contains("efficiency") || name.contains("dig_speed")) {
                    return entry.getValue();
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private String enchantmentName(Enchantment enchantment) {
        try {
            Method getKey = Enchantment.class.getMethod("getKey");
            return String.valueOf(getKey.invoke(enchantment)).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}
        try {
            return enchantment.getName().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!isShield(event.getItem())) {
            return;
        }

        Player player = event.getPlayer();
        if (isShieldOnCooldown(player)) {
            return;
        }

        shieldUseMap.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        swapLowerer.restore(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        swapLowerer.restore(player);
        shieldUseMap.remove(player.getUniqueId());
        shieldDisabledMap.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!instantShieldEnabled) {
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
        if (lastShieldInteract == null || (System.currentTimeMillis() - lastShieldInteract) > RAISE_WINDOW_MS) {
            return;
        }

        if (IS_HAND_RAISED_METHOD != null) {
            try {
                if (!((Boolean) IS_HAND_RAISED_METHOD.invoke(victim))) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

        ItemStack shield = null;
        boolean offHand = false;
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHandItem = victim.getInventory().getItemInOffHand();

        if (isShield(mainHand)) {
            shield = mainHand;
        } else if (isShield(offHandItem)) {
            shield = offHandItem;
            offHand = true;
        }

        if (shield == null || isShieldOnCooldown(victim)) {
            return;
        }

        Entity damager = event.getDamager();

        Vector playerDirection = victim.getLocation().getDirection().setY(0).normalize();
        Vector damageSourceDirection = damager.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0).normalize();

        if (playerDirection.dot(damageSourceDirection) < 0.0) {
            return;
        }

        event.setCancelled(true);

        if (shouldDisableShield(damager)) {
            playSound(victim, "item.shield.break");
            disableShield(victim, offHand);
            return;
        }

        playSound(victim, "item.shield.block");

        if (victim.getGameMode() != GameMode.CREATIVE) {
            damageShield(victim, shield);
        }
    }

    private void damageShield(Player victim, ItemStack shield) {
        int maxDurability = shield.getType().getMaxDurability();

        if (DAMAGEABLE_CLASS != null && GET_DAMAGE_METHOD != null && SET_DAMAGE_METHOD != null) {
            try {
                ItemMeta meta = shield.getItemMeta();
                if (meta != null && DAMAGEABLE_CLASS.isInstance(meta)) {
                    int currentDamage = (Integer) GET_DAMAGE_METHOD.invoke(meta);
                    if (currentDamage + 1 >= maxDurability) {
                        breakShield(victim, shield);
                    } else {
                        SET_DAMAGE_METHOD.invoke(meta, currentDamage + 1);
                        shield.setItemMeta(meta);
                    }
                    return;
                }
            } catch (Throwable ignored) {}
        }

        short currentDamage = shield.getDurability();
        if (currentDamage + 1 >= maxDurability) {
            breakShield(victim, shield);
        } else {
            shield.setDurability((short) (currentDamage + 1));
        }
    }

    private void breakShield(Player victim, ItemStack shield) {
        shield.setAmount(0);
        playSound(victim, "item.shield.break");
        shieldUseMap.remove(victim.getUniqueId());
    }

    private void playSound(Player player, String soundKey) {
        try {
            Location location = player.getLocation();
            player.getWorld().playSound(location, soundKey, 1.0f, 1.0f);
        } catch (Throwable ignored) {}
    }
}