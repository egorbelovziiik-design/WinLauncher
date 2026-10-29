package net.kinggrief.probe;
import net.kinggrief.core.BossEventManager;
import org.bukkit.Location;
import org.bukkit.World;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
/** Region-boundary test against an API double, not the real WorldGuard plugin. */
public final class MockWgAssertions {
    public static void run(BossEventManager events,World world) {
        RegionManager manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        // Only the corner of the event footprint touches this region.
        manager.getRegions().put("protected",new ProtectedRegion(new BlockVector3(529,145,529),new BlockVector3(535,160,535)));
        if (events.spawnBossEvent("camp",new Location(world,520,150,520)) != null)
            throw new AssertionError("event overlapped a WorldGuard region");
        manager.getRegions().clear();
    }
}
