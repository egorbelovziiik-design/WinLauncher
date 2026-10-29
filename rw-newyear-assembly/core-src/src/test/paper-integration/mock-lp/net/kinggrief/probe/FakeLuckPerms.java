package net.kinggrief.probe;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Minimal LP API and command double. Never ship this plugin to a server. */
public final class FakeLuckPerms extends JavaPlugin implements LuckPerms, UserManager {
    private final Map<UUID, FakeUser> users = new HashMap<UUID, FakeUser>();
    private final Map<String, UUID> names = new HashMap<String, UUID>();
    private final Set<String> groups = new HashSet<String>();
    private static final class FakeUser implements User {
        String group;
        FakeUser(String group) { this.group = group; }
        public String getPrimaryGroup() { return group; }
    }
    @Override public void onEnable() { LuckPermsProvider.register(this); }
    public UserManager getUserManager() { return this; }
    public User getUser(UUID id) { return users.get(id); }
    public CompletableFuture<UUID> lookupUniqueId(String name) { return CompletableFuture.completedFuture(names.get(name.toLowerCase(Locale.ROOT))); }
    public CompletableFuture<User> loadUser(UUID id) { return CompletableFuture.completedFuture(users.get(id)); }
    public void seed(String name, String group) {
        UUID id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
        names.put(name.toLowerCase(Locale.ROOT), id); users.put(id, new FakeUser(group));
    }
    public boolean hasGroup(String name) { return groups.contains(name); }
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("creategroup")) groups.add(args[1]);
        if (args.length >= 5 && args[0].equalsIgnoreCase("user") && args[2].equalsIgnoreCase("parent") && args[3].equalsIgnoreCase("set")) {
            UUID id=names.get(args[1].toLowerCase(Locale.ROOT));
            if (id != null) users.get(id).group=args[4];
        }
        return true;
    }
}
