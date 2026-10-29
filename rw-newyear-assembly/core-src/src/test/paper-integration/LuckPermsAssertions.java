package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.CuratorManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Runs only when the real LuckPerms plugin is supplied to the disposable server. */
public final class LuckPermsAssertions {
    private static void fail(JavaPlugin plugin, Throwable t) {
        plugin.getLogger().severe("KG-PROBE FAIL: real LuckPerms: " + t);
        t.printStackTrace();
    }

    private static CompletableFuture<Void> seed(LuckPerms api, String name, String group) {
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return api.getUserManager().loadUser(id, name).thenCompose(user -> {
            user.data().add(InheritanceNode.builder(group).build());
            user.setPrimaryGroup(group);
            return api.getUserManager().saveUser(user);
        });
    }

    public static void run(final JavaPlugin test, final KingGriefCore core, final Player actor, final Runnable done) {
        try {
            final LuckPerms api = LuckPermsProvider.get();
            if (api.getGroupManager().getGroup("curator_discord") == null ||
                api.getGroupManager().getGroup("gladmin") == null) {
                throw new AssertionError("Core failed to create new LuckPerms groups");
            }
            final CuratorManager manager = new CuratorManager(core);
            CompletableFuture.allOf(seed(api, "LPOnlyHigher", "curator_discord"),
                                    seed(api, "LPOnlyLower", "default"))
                .whenComplete((unused, error) -> Bukkit.getScheduler().runTask(test, () -> {
                    try {
                        if (error != null) throw new AssertionError("could not seed real LP users", error);
                        core.getRanks().setRank(actor.getName(), "curator_kp", false);
                        core.getStore().setSavedRank("LPOnlyHigher", "helper");
                        core.getStore().setSavedRank("LPOnlyLower", "default");
                        // Core's stale record is lower; real LP must still protect the higher offline user.
                        manager.executeRankChange(actor, "LPOnlyHigher", "default", "probe");
                        Bukkit.getScheduler().runTaskLater(test, () -> {
                            try {
                                if (!"helper".equals(core.getStore().getSavedRank("LPOnlyHigher")))
                                    throw new AssertionError("offline higher LP rank was demoted");
                                // An in-flight request must be re-authorized after the actor loses their role.
                                manager.executeRankChange(actor, "LPOnlyLower", "moder", "probe");
                                core.getRanks().setRank(actor.getName(), "helper", false);
                                Bukkit.getScheduler().runTaskLater(test, () -> {
                                  try {
                                if (!"default".equals(core.getStore().getSavedRank("LPOnlyLower")))
                                    throw new AssertionError("request survived actor demotion");
                                core.getRanks().setRank(actor.getName(), "curator_kp", false);
                                manager.executeRankChange(actor, "LPOnlyLower", "moder", "probe");
                                Bukkit.getScheduler().runTaskLater(test, () -> {
                                    try {
                                        if (!"moder".equals(core.getStore().getSavedRank("LPOnlyLower")))
                                            throw new AssertionError("lower LP user could not be promoted");
                                        UUID low = UUID.nameUUIDFromBytes("OfflinePlayer:LPOnlyLower".getBytes(StandardCharsets.UTF_8));
                                        api.getUserManager().loadUser(low).whenComplete((user, loadError) ->
                                            Bukkit.getScheduler().runTask(test, () -> {
                                                try {
                                                    if (loadError != null || user == null || !"moder".equals(user.getPrimaryGroup()))
                                                        throw new AssertionError("Core rank did not sync to actual LP: " + (user == null ? null : user.getPrimaryGroup()));
                                                    done.run();
                                                } catch (Throwable t) { fail(test, t); }
                                            }));
                                    } catch (Throwable t) { fail(test, t); }
                                }, 70L);
                                  } catch (Throwable t) { fail(test, t); }
                                }, 70L);
                            } catch (Throwable t) { fail(test, t); }
                        }, 70L);
                    } catch (Throwable t) { fail(test, t); }
                }));
        } catch (Throwable t) { fail(test, t); }
    }
}
