package com.instantshield;

import com.instantshield.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InstantShieldCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "shield-delay-ticks", "instant-shield-enabled", "update-checker");
    private static final List<String> BOOLEAN_VALUES = Arrays.asList("true", "false");
    private static final List<String> TICK_SUGGESTIONS = Arrays.asList("0", "1", "2", "3", "4", "5");

    private final InstantShield plugin;

    public InstantShieldCommand(InstantShield plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                handleReload(sender);
                return true;
            case "shield-delay-ticks":
                handleShieldDelay(sender, args);
                return true;
            case "instant-shield-enabled":
                handleInstantShieldEnabled(sender, args);
                return true;
            case "update-checker":
                handleUpdateChecker(sender, args);
                return true;
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        Messages messages = plugin.getMessages();
        messages.send(sender, "usage-separator");
        messages.send(sender, "usage-title");
        messages.send(sender, "usage-line-reload");
        messages.send(sender, "usage-line-shield-delay");
        messages.send(sender, "usage-line-instant-shield-enabled");
        messages.send(sender, "usage-line-update-checker");
        messages.send(sender, "usage-separator");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("instantshield.reload")) {
            plugin.getMessages().send(sender, "no-permission");
            return;
        }
        plugin.reloadPluginConfig();
        plugin.getMessages().send(sender, "reload-success");
    }

    private void handleShieldDelay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("instantshield.config")) {
            plugin.getMessages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage-shield-delay");
            return;
        }
        int ticks;
        try {
            ticks = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.getMessages().send(sender, "invalid-number", "input", args[1]);
            return;
        }
        if (ticks < 0) {
            plugin.getMessages().send(sender, "negative-value");
            return;
        }
        plugin.setShieldDelayTicks(ticks);
        plugin.getMessages().send(sender, "shield-delay-set", "value", String.valueOf(ticks));
    }

    private void handleInstantShieldEnabled(CommandSender sender, String[] args) {
        if (!sender.hasPermission("instantshield.config")) {
            plugin.getMessages().send(sender, "no-permission");
            return;
        }
        Boolean value = parseBoolean(sender, args);
        if (value == null) {
            return;
        }
        plugin.setInstantShieldEnabled(value);
        plugin.getMessages().send(sender, "instant-shield-enabled-set", "value", String.valueOf(value));
    }

    private void handleUpdateChecker(CommandSender sender, String[] args) {
        if (!sender.hasPermission("instantshield.config")) {
            plugin.getMessages().send(sender, "no-permission");
            return;
        }
        Boolean value = parseBoolean(sender, args);
        if (value == null) {
            return;
        }
        plugin.setUpdateCheckerEnabled(value);
        plugin.getMessages().send(sender, "update-checker-set", "value", String.valueOf(value));
    }

    private Boolean parseBoolean(CommandSender sender, String[] args) {
        if (args.length < 2 || !(args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) {
            plugin.getMessages().send(sender, "usage-boolean", "sub", args[0]);
            return null;
        }
        return Boolean.parseBoolean(args[1]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (hasPermissionForSub(sender, sub) && sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (!hasPermissionForSub(sender, sub)) {
                return completions;
            }
            if (sub.equals("shield-delay-ticks")) {
                for (String value : TICK_SUGGESTIONS) {
                    if (value.startsWith(args[1])) {
                        completions.add(value);
                    }
                }
            } else if (sub.equals("instant-shield-enabled") || sub.equals("update-checker")) {
                for (String value : BOOLEAN_VALUES) {
                    if (value.startsWith(args[1].toLowerCase())) {
                        completions.add(value);
                    }
                }
            }
        }

        return completions;
    }

    private boolean hasPermissionForSub(CommandSender sender, String sub) {
        if (sub.equals("reload")) {
            return sender.hasPermission("instantshield.reload");
        }
        return sender.hasPermission("instantshield.config");
    }
}
