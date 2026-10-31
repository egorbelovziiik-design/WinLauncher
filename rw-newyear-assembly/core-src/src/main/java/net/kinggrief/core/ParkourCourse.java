package net.kinggrief.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The same checked layout is consumed by the spawn blueprint generator. */
final class ParkourCourse {
    private static final int[] STAGE_INDEXES = {0, 8, 16, 24, 31};
    private static final String[] STAGE_KINDS = {"start", "checkpoint", "checkpoint", "checkpoint", "finish"};

    static final class Pad {
        final int x, y, z;
        final String kind;
        Pad(int x, int y, int z, String kind) {
            this.x = x; this.y = y; this.z = z; this.kind = kind;
        }
        Location returnLocation(Player player) {
            Location facing = player.getLocation();
            // One eighth above the plate's block base; the actual plate is 1/16 high.
            return new Location(player.getWorld(), x + 0.5, y + 1.125, z + 0.5,
                    facing.getYaw(), facing.getPitch());
        }
        boolean containsPlate(Block block) {
            Material expected = "finish".equals(kind)
                    ? Material.HEAVY_WEIGHTED_PRESSURE_PLATE : Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
            return block.getType() == expected && block.getY() == y + 1
                    && Math.abs(block.getX() - x) <= 1 && Math.abs(block.getZ() - z) <= 1;
        }
    }

    private final Pad[] stages;
    private ParkourCourse(Pad[] stages) { this.stages = stages; }
    Pad stage(int number) { return stages[number]; }
    int findStage(Block plate) {
        for (int i = 0; i < stages.length; i++) if (stages[i].containsPlate(plate)) return i;
        return -1;
    }

    static ParkourCourse load(KingGriefCore plugin) {
        try (InputStream input = plugin.getResource("parkour_v74.csv")) {
            if (input == null) throw new IOException("missing parkour_v74.csv");
            List<Pad> selected = new ArrayList<Pad>();
            int row = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split(",", -1);
                    if (parts.length != 6 || Integer.parseInt(parts[0]) != row || row >= 32)
                        throw new IOException("invalid parkour row " + row);
                    int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]), half = Integer.parseInt(parts[4]);
                    String kind = parts[5];
                    if (Math.abs(x) > 120 || Math.abs(z) > 120 || y < 90 || y > 180)
                        throw new IOException("out-of-range parkour row " + row);
                    boolean isStage = selected.size() < STAGE_INDEXES.length && row == STAGE_INDEXES[selected.size()];
                    if (isStage) {
                        if (half != 2 || !STAGE_KINDS[selected.size()].equals(kind))
                            throw new IOException("invalid parkour checkpoint " + row);
                        selected.add(new Pad(x, y, z, kind));
                    } else if (half != 1 || !"step".equals(kind)) {
                        throw new IOException("invalid parkour platform " + row);
                    }
                    row++;
                }
            }
            if (row != 32 || selected.size() != STAGE_INDEXES.length)
                throw new IOException("incomplete parkour layout");
            return new ParkourCourse(selected.toArray(new Pad[selected.size()]));
        } catch (IOException | IllegalArgumentException error) {
            // A missing/broken layout must never turn arbitrary pressure plates
            // into checkpoints or free rewards.
            plugin.getLogger().severe("[Parkour] Course disabled: " + error.getMessage());
            return null;
        }
    }
}
