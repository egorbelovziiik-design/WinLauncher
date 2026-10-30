package dev.axsesclient.common.events;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static final class AttackEvent {
        private final float cooldownProgress;
        private final boolean hitEntity;
        private final boolean critical;

        public AttackEvent(float cooldownProgress, boolean hitEntity, boolean critical) {
            this.cooldownProgress = cooldownProgress;
            this.hitEntity = hitEntity;
            this.critical = critical;
        }

        public float getCooldownProgress() {
            return cooldownProgress;
        }

        public boolean isHitEntity() {
            return hitEntity;
        }

        public boolean isCritical() {
            return critical;
        }
    }

    public static final class MouseClickEvent {
        private final int button;
        private final long timestampMs;

        public MouseClickEvent(int button, long timestampMs) {
            this.button = button;
            this.timestampMs = timestampMs;
        }

        public int getButton() {
            return button;
        }

        public long getTimestampMs() {
            return timestampMs;
        }
    }

    public static final class PlayerDeathEvent {
        private final double x;
        private final double y;
        private final double z;
        private final String dimension;

        public PlayerDeathEvent(double x, double y, double z, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension == null ? "minecraft:overworld" : dimension;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public String getDimension() {
            return dimension;
        }
    }

    public static final class ScreenshotEvent {
        private final String fileName;
        private final long timestampMs;

        public ScreenshotEvent(String fileName, long timestampMs) {
            this.fileName = fileName == null ? "screenshot.png" : fileName;
            this.timestampMs = timestampMs;
        }

        public String getFileName() {
            return fileName;
        }

        public long getTimestampMs() {
            return timestampMs;
        }
    }

    public static final class WorldChangeEvent {
        private final String worldId;
        private final boolean singleplayer;

        public WorldChangeEvent(String worldId, boolean singleplayer) {
            this.worldId = worldId == null ? "" : worldId;
            this.singleplayer = singleplayer;
        }

        public String getWorldId() {
            return worldId;
        }

        public boolean isSingleplayer() {
            return singleplayer;
        }
    }
}
