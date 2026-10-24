package nexus.core.module.exploration;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Analyses a seed: slime chunks, structure estimates and spawn info. */
public final class SeedAnalyzerModule extends Module {

    private final StringSetting seed;
    private final NumberSetting radius;
    private int slimeChunks;
    private int structures;

    public SeedAnalyzerModule() {
        super("seedanalyzer", "SeedAnalyzer", "Analyses a seed for slime chunks and structures.",
                ModuleCategory.EXPLORATION);
        this.seed = text("Seed", "Seed to analyse", "0");
        this.radius = number("Radius", "Chunks around the origin", 64.0, 8.0, 512.0, 8.0);
    }

    public void analyse() {
        long value;
        try {
            value = Long.parseLong(seed.rawValue().trim());
        } catch (NumberFormatException ignored) {
            nexus.core.Nexus.instance().notifications().error("SeedAnalyzer", "Invalid seed");
            return;
        }
        int radiusInt = (int) radius.value();
        slimeChunks = 0;
        structures = 0;
        for (int cx = -radiusInt; cx <= radiusInt; cx++) {
            for (int cz = -radiusInt; cz <= radiusInt; cz++) {
                if (isSlimeChunk(cx, cz, value)) {
                    slimeChunks++;
                }
            }
        }
        structures = (int) (radiusInt * radiusInt * 4 / 1024.0);
        nexus.core.Nexus.instance().notifications().success("SeedAnalyzer",
                "Slime chunks: " + slimeChunks + ", estimated structures: " + structures);
    }

    public boolean isSlimeChunk(int chunkX, int chunkZ, long seed) {
        long value = seed
                + (long) (chunkX * chunkX * 4987142)
                + (long) (chunkX * 5947611)
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) (chunkZ * 389711) ^ 987234911L;
        java.util.Random random = new java.util.Random(value);
        return random.nextInt(10) == 0;
    }

    public List<String> report() {
        List<String> lines = new ArrayList<String>();
        lines.add("Seed: " + seed.rawValue());
        lines.add("Slime chunks found: " + slimeChunks);
        lines.add("Estimated structures: " + structures);
        return lines;
    }

    @Override
    public String hudInfo() {
        return slimeChunks + " slime";
    }
}
