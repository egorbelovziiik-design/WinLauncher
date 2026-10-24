package nexus.api;

/** Active potion / status effect on an entity. */
public interface IPotionEffect {

    String name();

    /** Remaining duration in ticks. */
    int duration();

    /** Effect amplifier (0 = level I). */
    int amplifier();

    int color();

    boolean isAmbient();

    boolean showParticles();

    boolean isBeneficial();
}
