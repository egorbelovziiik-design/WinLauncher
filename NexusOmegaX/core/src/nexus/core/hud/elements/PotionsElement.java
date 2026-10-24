package nexus.core.hud.elements;

import nexus.api.IPotionEffect;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

import java.util.List;

/** Active potion effects with remaining time. */
public final class PotionsElement extends HudElement {

    private final BooleanSetting showAmplifier;
    private final BooleanSetting onlyBeneficial;

    public PotionsElement() {
        super("potions", "Potions");
        this.showAmplifier = new BooleanSetting("Show Amplifier", "Show the effect level", true);
        this.onlyBeneficial = new BooleanSetting("Only Beneficial", "Hide negative effects", false);
    }

    private List<IPotionEffect> effects() {
        if (!Nexus.instance().client().isInGame()) {
            return java.util.Collections.emptyList();
        }
        List<IPotionEffect> all = Nexus.instance().client().player().potionEffects();
        List<IPotionEffect> result = new java.util.ArrayList<IPotionEffect>();
        for (IPotionEffect effect : all) {
            if (onlyBeneficial.isEnabled() && !effect.isBeneficial()) {
                continue;
            }
            result.add(effect);
        }
        return result;
    }

    private String label(IPotionEffect effect) {
        String name = effect.name();
        int seconds = effect.duration() / 20;
        String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
        String level = showAmplifier.isEnabled() && effect.amplifier() > 0
                ? " " + roman(effect.amplifier() + 1) : "";
        return name + level + " " + time;
    }

    private static String roman(int value) {
        switch (value) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            default:
                return String.valueOf(value);
        }
    }

    @Override
    public double width() {
        double max = 0.0;
        for (IPotionEffect effect : effects()) {
            max = Math.max(max, textWidth(label(effect), scale()));
        }
        return max;
    }

    @Override
    public double height() {
        List<IPotionEffect> effects = effects();
        return Math.max(fontHeight(scale()), effects.size() * lineHeight());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        double s = scale();
        int index = 0;
        for (IPotionEffect effect : effects()) {
            int textColor = effect.color() == 0 ? color(System.currentTimeMillis()) : effect.color();
            event.renderer.drawText(label(effect), x, y + index * lineHeight(), textColor, shadow(), s);
            index++;
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showAmplifier);
        list.add(onlyBeneficial);
        return list;
    }

    @Override
    public String previewText() {
        return "Speed II 01:30\nStrength 00:12";
    }
}
