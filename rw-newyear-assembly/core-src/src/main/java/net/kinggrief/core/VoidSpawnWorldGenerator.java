package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public final class VoidSpawnWorldGenerator extends ChunkGenerator {

    public static final String SPAWN_WORLD_NAME = "spawn_world";

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        if (biome != null) {
            try {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        biome.setBiome(x, z, Biome.SNOWY_PLAINS);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return data;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    public static World getOrCreateSpawnWorld() {
        World w = Bukkit.getWorld(SPAWN_WORLD_NAME);
        if (w == null) {
            WorldCreator wc = new WorldCreator(SPAWN_WORLD_NAME);
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.FLAT);
            wc.generator(new VoidSpawnWorldGenerator());
            wc.generateStructures(false);
            w = Bukkit.createWorld(wc);
        }
        if (w != null) {
            configureSpawnWorldRules(w);
        }
        return w;
    }

    public static void configureSpawnWorldRules(World w) {
        if (w == null) return;
        try {
            w.setDifficulty(Difficulty.NORMAL);
            w.setTime(6000L); // Bright sunny winter noon so colorful Quartz, Emerald Pine, Gold & Concrete pop!
            w.setStorm(false);
            w.setThundering(false);
            setRule(w, "DO_DAYLIGHT_CYCLE", Boolean.FALSE);
            setRule(w, "DO_WEATHER_CYCLE", Boolean.FALSE);
            setRule(w, "DO_MOB_SPAWNING", Boolean.FALSE);
            setRule(w, "DO_FIRE_TICK", Boolean.FALSE);
            setRule(w, "MOB_GRIEFING", Boolean.FALSE);
            setRule(w, "COMMAND_BLOCK_OUTPUT", Boolean.FALSE);
            setRule(w, "SEND_COMMAND_FEEDBACK", Boolean.FALSE);
            setRule(w, "ANNOUNCE_ADVANCEMENTS", Boolean.FALSE);
            setRule(w, "DO_PATROL_SPAWNING", Boolean.FALSE);
            setRule(w, "DO_TRADER_SPAWNING", Boolean.FALSE);
            setRule(w, "RANDOM_TICK_SPEED", Integer.valueOf(0));
            // Do not delete user-owned entities or reset an existing world spawn here.
        } catch (Throwable ignored) {
        }
    }

    public static World getSurvivalWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL
                    && !w.getName().equalsIgnoreCase(SPAWN_WORLD_NAME)) {
                silenceCommandSpam(w);
                return w;
            }
        }
        // RTP must not synchronously create or generate a new world on command.
        // A missing survival world is reported to the player instead.
        return null;
    }

    public static World getNetherWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NETHER) {
                silenceCommandSpam(w);
                return w;
            }
        }
        return null;
    }

    public static World getEndWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.THE_END) {
                silenceCommandSpam(w);
                return w;
            }
        }
        return null;
    }

    public static void silenceCommandSpam(World w) {
        if (w == null) return;
        setRule(w, "COMMAND_BLOCK_OUTPUT", Boolean.FALSE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setRule(World w, String fieldName, Object val) {
        try {
            java.lang.reflect.Field f = GameRule.class.getField(fieldName);
            GameRule rule = (GameRule) f.get(null);
            if (rule != null) {
                w.setGameRule(rule, val);
            }
        } catch (Throwable ignored) {
        }
    }
}
