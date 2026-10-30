package dev.axsesclient.common.platform;

public final class StatusEffectInfo {
    private final String id;
    private final String name;
    private final int amplifier;
    private final int durationTicks;
    private final boolean beneficial;
    private final int color;

    public StatusEffectInfo(String id, String name, int amplifier, int durationTicks, boolean beneficial, int color) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.amplifier = Math.max(0, amplifier);
        this.durationTicks = Math.max(0, durationTicks);
        this.beneficial = beneficial;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public boolean isBeneficial() {
        return beneficial;
    }

    public int getColor() {
        return color;
    }

    public String getFormattedDuration() {
        int totalSeconds = durationTicks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public String getRomanAmplifier() {
        int level = amplifier + 1;
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(level);
        }
    }
}
