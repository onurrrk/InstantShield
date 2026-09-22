package com.instantshield;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class UpdateChecker implements Listener {

    private final JavaPlugin plugin;
    private final String currentVersion;
    private volatile String latestVersion = null;
    private volatile boolean updateAvailable = false;
    private final ScheduledExecutorService scheduler;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });

        Bukkit.getPluginManager().registerEvents(this, plugin);

        this.scheduler.execute(() -> {
            try {
                if (fetchUpdate()) {
                    notifyOpsAndConsole();
                    this.scheduler.schedule(() -> {
                        try {
                            if (updateAvailable) {
                                sendConsoleUpdate(Bukkit.getConsoleSender());
                            }
                        } catch (Throwable ignored) {}
                    }, 5, TimeUnit.MINUTES);
                }
            } catch (Throwable ignored) {}
        });

        this.scheduler.scheduleAtFixedRate(() -> {
            try {
                if (fetchUpdate()) {
                    notifyOpsAndConsole();
                }
            } catch (Throwable ignored) {}
        }, 12, 12, TimeUnit.HOURS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        HandlerList.unregisterAll(this);
    }

    private boolean fetchUpdate() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.modrinth.com/v2/project/instantshield/version");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "InstantShield-UpdateChecker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JsonArray jsonArray = new JsonParser().parse(reader).getAsJsonArray();

                    for (JsonElement element : jsonArray) {
                        JsonObject release = element.getAsJsonObject();
                        if (!release.has("version_type") || !"release".equals(release.get("version_type").getAsString())) {
                            continue;
                        }
                        latestVersion = release.get("version_number").getAsString();

                        updateAvailable = isNewerVersion(currentVersion, latestVersion);
                        return updateAvailable;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    private boolean isNewerVersion(String current, String latest) {
        try {
            current = current.replaceAll("[^0-9.]", "");
            latest = latest.replaceAll("[^0-9.]", "");
            String[] currArr = current.split("\\.");
            String[] latArr = latest.split("\\.");
            int length = Math.max(currArr.length, latArr.length);
            for (int i = 0; i < length; i++) {
                int currPart = i < currArr.length ? Integer.parseInt(currArr[i]) : 0;
                int latPart = i < latArr.length ? Integer.parseInt(latArr[i]) : 0;
                if (latPart > currPart) return true;
                if (currPart > latPart) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void notifyOpsAndConsole() {
        if (!updateAvailable) return;
        sendConsoleUpdate(Bukkit.getConsoleSender());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                sendPlayerUpdate(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateAvailable && event.getPlayer().isOp()) {
            runLater(event.getPlayer(), () -> {
                if (event.getPlayer().isOnline()) {
                    sendPlayerUpdate(event.getPlayer());
                }
            }, 150L);
        }
    }

    private void runLater(Player player, Runnable task, long delayTicks) {
        try {
            Object entityScheduler = Player.class.getMethod("getScheduler").invoke(player);
            Class<?> schedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Method runDelayed = schedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            runDelayed.invoke(entityScheduler, plugin, consumer, null, delayTicks);
            return;
        } catch (Throwable ignored) {}

        try {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return;
        } catch (Throwable ignored) {}

        task.run();
    }

    private void sendConsoleUpdate(CommandSender sender) {
        String[] lines = {
            "&8&m--------------------------------------------------",
            "&b&lINSTANTSHIELD UPDATE",
            "&7A new version of the plugin is available!",
            "&cCurrent: " + currentVersion + " &8» &aNew: " + latestVersion,
            "&eDownload here: &a&nhttps://modrinth.com/plugin/instantshield",
            "&8&m--------------------------------------------------"
        };
        for (String line : lines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    private void sendPlayerUpdate(CommandSender sender) {
        String msg = "&8&m--------------------------------------------------\n" +
                     "&b&lINSTANTSHIELD UPDATE\n" +
                     "&7A new version of the plugin is available!\n" +
                     "&cCurrent: " + currentVersion + " &8» &aNew: " + latestVersion + "\n" +
                     "&eDownload here: &a&nhttps://modrinth.com/plugin/instantshield\n" +
                     "&8&m--------------------------------------------------";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }
}