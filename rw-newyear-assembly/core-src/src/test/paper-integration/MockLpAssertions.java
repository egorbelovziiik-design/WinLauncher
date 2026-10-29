package net.kinggrief.probe;

import net.kinggrief.core.CuratorManager;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Tests the async/command contract using a small LP double, not real LP. */
public final class MockLpAssertions {
    private static void fail(JavaPlugin test, Throwable t) { test.getLogger().severe("KG-PROBE FAIL: mock LP: " + t); t.printStackTrace(); }
    public static void run(JavaPlugin test, KingGriefCore core, Player actor, Runnable done) {
        try {
            FakeLuckPerms lp=(FakeLuckPerms)Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (!lp.hasGroup("curator_discord") || !lp.hasGroup("gladmin")) throw new AssertionError("new groups not sent to LP");
            lp.seed("LPOnlyHigher", "curator_discord"); lp.seed("LPOnlyLower", "default");
            CuratorManager manager=new CuratorManager(core);
            core.getRanks().setRank(actor.getName(), "curator_kp", false);
            core.getStore().setSavedRank("LPOnlyHigher", "helper");
            core.getStore().setSavedRank("LPOnlyLower", "default");
            // This request completes with actor still authorized: only the external high rank can block it.
            manager.executeRankChange(actor,"LPOnlyHigher","default","probe");
            Bukkit.getScheduler().runTaskLater(test, () -> {
                try {
                    if (!"helper".equals(core.getStore().getSavedRank("LPOnlyHigher"))) throw new AssertionError("offline higher LP rank demoted");
                    manager.executeRankChange(actor,"LPOnlyLower","moder","probe");
                    core.getRanks().setRank(actor.getName(), "helper", false);
                    Bukkit.getScheduler().runTaskLater(test, () -> {
                        try {
                            if (!"default".equals(core.getStore().getSavedRank("LPOnlyLower"))) throw new AssertionError("request survived actor demotion");
                            core.getRanks().setRank(actor.getName(), "curator_kp", false);
                            manager.executeRankChange(actor,"LPOnlyLower","moder","probe");
                            Bukkit.getScheduler().runTaskLater(test, () -> {
                                try {
                                    UUID id=UUID.nameUUIDFromBytes("OfflinePlayer:LPOnlyLower".getBytes(StandardCharsets.UTF_8));
                                    if (!"moder".equals(core.getStore().getSavedRank("LPOnlyLower")) || !"moder".equals(lp.getUser(id).getPrimaryGroup()))
                                        throw new AssertionError("lower rank could not be changed and synced");
                                    done.run();
                                } catch (Throwable t) { fail(test,t); }
                            }, 20L);
                        } catch (Throwable t) { fail(test,t); }
                    }, 20L);
                } catch (Throwable t) { fail(test,t); }
            }, 20L);
        } catch (Throwable t) { fail(test,t); }
    }
}
