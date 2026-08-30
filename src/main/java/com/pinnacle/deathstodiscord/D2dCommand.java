package com.pinnacle.deathstodiscord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class D2dCommand implements TabExecutor {

    private final ReloadHandler reloadHandler;

    D2dCommand(ReloadHandler reloadHandler) {
        this.reloadHandler = reloadHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("d2d")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("d2d.admin")) {
                sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                return true;
            }
            reloadHandler.reload(sender);
            return true;
        }

        sender.sendMessage(Component.text("Usage: /d2d reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("d2d")
                || !sender.hasPermission("d2d.admin")
                || args.length != 1) {
            return Collections.emptyList();
        }

        return "reload".startsWith(args[0].toLowerCase(Locale.ROOT))
                ? List.of("reload")
                : Collections.emptyList();
    }

    @FunctionalInterface
    interface ReloadHandler {
        void reload(CommandSender sender);
    }
}
