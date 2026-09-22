package com.instantshield;

import org.bukkit.entity.Player;

public interface ShieldLowerer {

    boolean lower(Player player, boolean offHand);

    default void restore(Player player) {}

    default void shutdown() {}
}
