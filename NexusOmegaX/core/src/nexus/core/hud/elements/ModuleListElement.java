package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.ModeSetting;
import nexus.util.ColorUtil;

import java.util.List;

/** Shows every module of one category with its current state. */
public final class ModuleListElement extends HudElement {

    private final ModeSetting category;

    public ModuleListElement() {
        super("modulelist", "ModuleList", false);
        String[] names = new String[ModuleCategory.values().length];
        for (int i = 0; i < names.length; i++) {
            names[i] = ModuleCategory.values()[i].displayName();
        }
        this.category = new ModeSetting("Category", "Category to display", "Combat", names);
    }

    private List<Module> modules() {
        return Nexus.instance().modules().byCategory(ModuleCategory.byName(category.get()));
    }

    private String label(Module module) {
        return (module.isEnabled() ? "+ " : "- ") + module.displayName();
    }

    @Override
    public double width() {
        double max = 0.0;
        for (Module module : modules()) {
            max = Math.max(max, textWidth(label(module), scale()));
        }
        return max;
    }

    @Override
    public double height() {
        return Math.max(1, modules().size()) * lineHeight();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        long now = System.currentTimeMillis();
        int index = 0;
        for (Module module : modules()) {
            int color = module.isEnabled()
                    ? color(now) : ColorUtil.withAlpha(Nexus.instance().themes().textDim(), 170);
            event.renderer.drawText(label(module), x, y + index * lineHeight(), color, shadow(), scale());
            index++;
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(category);
        return list;
    }

    @Override
    public String previewText() {
        return "+ Sprint\n- AutoEat";
    }
}
