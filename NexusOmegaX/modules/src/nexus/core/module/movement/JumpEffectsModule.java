package nexus.core.module.movement;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Sound and particle effects when you jump or land. */
public final class JumpEffectsModule extends Module {

    private final BooleanSetting onJump;
    private final BooleanSetting onLand;
    private final ModeSetting particle;
    private final NumberSetting volume;
    private boolean wasGrounded = true;

    public JumpEffectsModule() {
        super("jumpeffects", "JumpEffects", "Plays effects when you jump and when you land.",
                ModuleCategory.MOVEMENT);
        this.onJump = bool("On Jump", "Play an effect when jumping", true);
        this.onLand = bool("On Land", "Play an effect when landing", true);
        this.particle = mode("Particle", "Particle type", "Cloud", "Cloud", "Flame", "Spark", "Snow");
        this.volume = number("Volume", "Sound volume", 0.4, 0.0, 1.5, 0.05);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        boolean grounded = player().isOnGround();
        nexus.api.math.Vec3d position = player().position();
        if (wasGrounded && !grounded && onJump.isEnabled()) {
            effect(position, "entity.player.levelup");
        } else if (!wasGrounded && grounded && onLand.isEnabled() && player().fallDistance() > 1.0f) {
            effect(position, "block.stone.step");
        }
        wasGrounded = grounded;
    }

    private void effect(nexus.api.math.Vec3d position, String sound) {
        String id = "cloud";
        if (particle.is("Flame")) {
            id = "flame";
        } else if (particle.is("Spark")) {
            id = "firework";
        } else if (particle.is("Snow")) {
            id = "snowflake";
        }
        mc().resources().spawnParticle(id, position.x, position.y + 0.05, position.z, 0.25, 0.05, 0.25, 8);
        if (volume.value() > 0.0) {
            mc().resources().playSound(sound, (float) volume.value(), 1.6f);
        }
        markActivity();
    }
}
