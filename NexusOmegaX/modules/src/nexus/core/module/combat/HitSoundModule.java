package nexus.core.module.combat;

import nexus.core.event.AttackEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Plays a sound when you land a hit. */
public final class HitSoundModule extends Module {

    private final ModeSetting sound;
    private final NumberSetting volume;
    private final NumberSetting pitch;
    private final BooleanSetting criticalOnly;

    public HitSoundModule() {
        super("hitsound", "HitSound", "Plays a confirmation sound when your attack connects.",
                ModuleCategory.COMBAT);
        this.sound = mode("Sound", "Sound played on hit", "Pop", "Pop", "Click", "Bell", "Anvil", "Experience");
        this.volume = number("Volume", "Sound volume", 0.8, 0.0, 2.0, 0.05);
        this.pitch = number("Pitch", "Sound pitch", 1.4, 0.5, 2.0, 0.05);
        this.criticalOnly = bool("Critical Only", "Only play on critical hits", false);
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event.target == null) {
            return;
        }
        if (criticalOnly.isEnabled() && player().attackCooldown() < 0.9) {
            return;
        }
        String id = "entity.player.attack.crit";
        if (sound.is("Pop")) {
            id = "entity.item.pickup";
        } else if (sound.is("Click")) {
            id = "ui.button.click";
        } else if (sound.is("Bell")) {
            id = "block.bell.use";
        } else if (sound.is("Anvil")) {
            id = "block.anvil.land";
        } else if (sound.is("Experience")) {
            id = "entity.player.levelup";
        }
        mc().resources().playSound(id, (float) volume.value(), (float) pitch.value());
        markActivity();
    }
}
