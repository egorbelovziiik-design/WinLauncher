package net.kinggrief.probe;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** An unrelated /tree command loaded before the Core, as on real servers. */
public final class ForeignTreePlugin extends JavaPlugin {
    private int calls;

    public int getCalls() { return calls; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        calls++;
        sender.sendMessage("Foreign tree generator: expected a tree type");
        return true;
    }
}
