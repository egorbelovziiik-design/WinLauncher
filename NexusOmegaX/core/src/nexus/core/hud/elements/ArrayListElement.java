package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.ModeSetting;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;

import java.util.List;

/** Vertical list of the enabled modules. */
public final class ArrayListElement extends HudElement {

    private final ModeSetting sorting;
    private final NumberSetting spacing;
    private final nexus.core.setting.BooleanSetting showInfo;

    public ArrayListElement() {
        super("arraylist", "ArrayList");
        this.sorting = new ModeSetting("Sorting", "Order of the entries", "Length", "Length", "Alphabetical", "Category");
        this.spacing = new NumberSetting("Spacing", "Vertical spacing", 2.0, 0.0, 10.0, 1.0);
        this.showInfo = new nexus.core.setting.BooleanSetting("Show Info", "Show module suffix info", true);
    }

    private List<Module> modules() {
        List<Module> list = Nexus.instance().modules().enabled();
        String mode = sorting.get();
        if (mode.equals("Alphabetical")) {
            java.util.Collections.sort(list, new java.util.Comparator<Module>() {
                @Override
                public int compare(Module a, Module b) {
                    return a.displayName().compareToIgnoreCase(b.displayName());
                }
            });
        } else if (mode.equals("Category")) {
            java.util.Collections.sort(list, new java.util.Comparator<Module>() {
                @Override
                public int compare(Module a, Module b) {
                    int byCategory = Integer.compare(a.category().ordinal(), b.category().ordinal());
                    return byCategory != 0 ? byCategory : a.displayName().compareToIgnoreCase(b.displayName());
                }
            });
        }
        return list;
    }

    private String label(Module module) {
        String info = module.hudInfo();
        return showInfo.isEnabled() && !info.isEmpty() ? module.displayName() + " " + info : module.displayName();
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
        List<Module> list = modules();
        if (list.isEmpty()) {
            return fontHeight(scale());
        }
        return list.size() * (fontHeight(scale()) + spacing.value()) - spacing.value();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        long now = System.currentTimeMillis();
        double s = scale();
        double step = fontHeight(s) + spacing.value();
        int index = 0;
        for (Module module : modules()) {
            String text = label(module);
            int color = color(now, index);
            double textX = x;
            if (module.category() == ModuleCategory.COMBAT) {
                event.renderer.fillRect(x - 2.0, y + index * step, 1.5, fontHeight(s), color);
            }
            event.renderer.drawText(text, textX, y + index * step, color, shadow(), s);
            index++;
        }
    }

    private int color(long now, int index) {
        int base = colorSetting().effectiveColor(now, index * 0.12);
        return ColorUtil.withAlpha(base, Math.max(60, ColorUtil.alpha(base)));
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(sorting);
        list.add(spacing);
        list.add(showInfo);
        return list;
    }

    @Override
    public String previewText() {
        return "Sprint\nAutoEat [Apple]\nPlayerESP";
    }
}
