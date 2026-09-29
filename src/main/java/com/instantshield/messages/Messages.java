package com.instantshield.messages;

import com.instantshield.InstantShield;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Messages {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Method CHAT_COLOR_OF;

    static {
        Method of = null;
        try {
            of = Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
        } catch (Throwable ignored) {}
        CHAT_COLOR_OF = of;
    }

    private final InstantShield plugin;
    private YamlConfiguration config;

    public Messages(InstantShield plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        plugin.migrateYamlFile("messages.yml");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String path, String... placeholderPairs) {
        String line = get(path, placeholderPairs);
        if (!line.isEmpty()) {
            sender.sendMessage(line);
        }
    }

    public String get(String path, String... placeholderPairs) {
        String raw = raw(path);
        String prefix = raw("prefix");
        return colorize(applyPlaceholders(raw, prefix, placeholderPairs));
    }

    private String raw(String path) {
        String value = config.getString(path);
        return value != null ? value : "";
    }

    private String applyPlaceholders(String input, String prefix, String[] placeholderPairs) {
        String result = input.replace("{prefix}", prefix);
        for (int i = 0; i + 1 < placeholderPairs.length; i += 2) {
            result = result.replace("{" + placeholderPairs[i] + "}", placeholderPairs[i + 1]);
        }
        return result;
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = input;
        if (CHAT_COLOR_OF != null) {
            Matcher matcher = HEX_PATTERN.matcher(result);
            StringBuffer builder = new StringBuffer();
            while (matcher.find()) {
                try {
                    Object chatColor = CHAT_COLOR_OF.invoke(null, "#" + matcher.group(1));
                    matcher.appendReplacement(builder, Matcher.quoteReplacement(chatColor.toString()));
                } catch (Throwable ignored) {
                    matcher.appendReplacement(builder, "");
                }
            }
            matcher.appendTail(builder);
            result = builder.toString();
        } else {
            result = HEX_PATTERN.matcher(result).replaceAll("");
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }
}
