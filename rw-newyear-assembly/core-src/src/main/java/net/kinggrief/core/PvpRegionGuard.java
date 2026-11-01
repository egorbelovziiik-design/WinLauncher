package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/** WorldGuard check for secondary effects that do not generate a damage event.
 * Normal direct hits are handled by WorldGuard's own listener. */
final class PvpRegionGuard {
    private final KingGriefCore plugin;
    private Plugin cachedPlugin, failedPlugin;
    private Object query, worldGuardPlugin, flags;
    private Method wrapPlayer, adaptLocation, testBuild, queryState;
    private long retryAfterNanos;
    private boolean warned;

    PvpRegionGuard(KingGriefCore plugin) { this.plugin = plugin; }

    /** Called on the server thread. If WorldGuard is absent this makes no claim
     * about other protection plugins. If it is installed but unavailable or its
     * API fails, skip the secondary effect rather than bypass its regions. */
    boolean canAffect(Player attacker, Player target) {
        Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null) return true;
        if (!worldGuard.isEnabled()) {
            warnOnce("WorldGuard is installed but disabled");
            return false;
        }
        long now = System.nanoTime();
        if (worldGuard == failedPlugin && now - retryAfterNanos < 0L) return false;
        try {
            if (cachedPlugin != worldGuard || query == null) initialize(worldGuard);
            Object localPlayer = wrapPlayer.invoke(worldGuardPlugin, attacker);
            Object from = adaptLocation.invoke(null, attacker.getLocation());
            Object to = adaptLocation.invoke(null, target.getLocation());
            if (localPlayer == null || from == null || to == null) throw new IllegalStateException("WorldGuard player/location unavailable");
            boolean buildAllows = ((Boolean) testBuild.invoke(query, to, localPlayer, flags)).booleanValue();
            Object fromState = queryState.invoke(query, from, localPlayer, flags);
            Object toState = queryState.invoke(query, to, localPlayer, flags);
            warned = false;
            failedPlugin = null;
            return buildAllows && !isDeny(fromState) && !isDeny(toState);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            cachedPlugin = null;
            query = null;
            failedPlugin = worldGuard;
            retryAfterNanos = now + 30_000_000_000L;
            warnOnce("WorldGuard PvP query failed: " + error.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean isDeny(Object state) { return state != null && "DENY".equals(state.toString()); }

    private void initialize(Plugin worldGuard) throws ReflectiveOperationException {
        ClassLoader loader = worldGuard.getClass().getClassLoader();
        Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
        Object wg = wgClass.getMethod("getInstance").invoke(null);
        Object platform = wgClass.getMethod("getPlatform").invoke(wg);
        Class<?> platformType = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform", true, loader);
        Object container = platformType.getMethod("getRegionContainer").invoke(platform);
        Class<?> containerType = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer", true, loader);
        Object regionQuery = containerType.getMethod("createQuery").invoke(container);
        Class<?> queryType = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery", true, loader);
        Class<?> wgPluginType = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin", true, loader);
        Object wrappedPlugin = wgPluginType.getMethod("inst").invoke(null);
        Class<?> localPlayerType = Class.forName("com.sk89q.worldguard.LocalPlayer", true, loader);
        Class<?> worldEditLocation = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
        Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
        Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
        Class<?> flagsType = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader);
        Object flagArray = Array.newInstance(stateFlag, 1);
        Array.set(flagArray, 0, flagsType.getField("PVP").get(null));
        Class<?> arrayType = flagArray.getClass();

        // Assign only after all lookups succeed, so a partial API cannot grant effects.
        Method wrap = wgPluginType.getMethod("wrapPlayer", Player.class);
        Method adapt = adapter.getMethod("adapt", Location.class);
        Method build = queryType.getMethod("testBuild", worldEditLocation, localPlayerType, arrayType);
        Method state = queryType.getMethod("queryState", worldEditLocation, localPlayerType, arrayType);
        if (regionQuery == null || wrappedPlugin == null) throw new IllegalStateException("WorldGuard query unavailable");
        this.query = regionQuery;
        this.worldGuardPlugin = wrappedPlugin;
        this.flags = flagArray;
        this.wrapPlayer = wrap;
        this.adaptLocation = adapt;
        this.testBuild = build;
        this.queryState = state;
        this.cachedPlugin = worldGuard;
    }

    private void warnOnce(String message) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("[PvP] " + message + "; secondary player effects are disabled for safety.");
    }
}
