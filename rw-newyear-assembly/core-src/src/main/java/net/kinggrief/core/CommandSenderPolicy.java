package net.kinggrief.core;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

/** A command block, entity or proxied sender is not the server console. */
final class CommandSenderPolicy {
    private CommandSenderPolicy() {}

    static boolean isConsole(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }
}
