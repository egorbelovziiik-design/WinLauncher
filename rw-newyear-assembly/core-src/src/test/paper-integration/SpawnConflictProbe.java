package net.kinggrief.probe;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Simulates another plugin owning the unqualified /spawn command. */
public final class SpawnConflictProbe extends JavaPlugin {
    public boolean called;
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("spawn".equals(command.getName())) { called = true; return true; }
        return false;
    }
}
