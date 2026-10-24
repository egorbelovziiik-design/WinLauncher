package nexus.gui;
import nexus.core.notification.Notification;

import nexus.api.IRenderer;
import nexus.api.KeyCodes;
import nexus.api.screen.NexusScreen;
import nexus.core.Nexus;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;
import nexus.util.json.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Nexus ClickGUI.
 * <p>
 * Three themed windows (categories / modules / settings) plus a search bar,
 * favorites, animated toggles, sliders, dropdowns, a full color picker, a key
 * bind editor and persistent window positions.
 */
public final class ClickGuiScreen extends NexusScreen {

    private static final double ROW_HEIGHT = 22.0;
    private static final double PADDING = 8.0;

    private final Nexus nexus;
    private final CategoryWindow categories;
    private final ModuleWindow modules;
    private SettingsWindow settings;

    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;
    private Module selectedModule;
    private String search = "";
    private boolean searchFocused = true;
    private double openAnimation;
    private boolean closing;

    public ClickGuiScreen(Nexus nexus) {
        this.nexus = nexus;
        this.categories = new CategoryWindow(20.0, 20.0, 112.0, 300.0);
        this.modules = new ModuleWindow(140.0, 20.0, 190.0, 300.0);
        loadLayout();
    }

    // ------------------------------------------------------------------ screen

    @Override
    public String title() {
        return "Nexus ClickGUI";
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);
        keepInside(categories, width, height);
        keepInside(modules, width, height);
        if (settings != null) {
            keepInside(settings, width, height);
        }
    }

    private void keepInside(GuiWindow window, int width, int height) {
        double maxX = Math.max(0.0, width - window.width() - 4.0);
        double maxY = Math.max(0.0, height - 40.0);
        window.setPosition(MathUtil.clamp(window.x(), 0.0, maxX), MathUtil.clamp(window.y(), 0.0, maxY));
    }

    @Override
    public void tick() {
        openAnimation = closing ? Math.max(0.0, openAnimation - 0.12) : Math.min(1.0, openAnimation + 0.12);
    }

    /** Stops every GUI animation (required on close: no leftover animations). */
    public void resetAnimations() {
        openAnimation = 0.0;
        closing = false;
        categories.resetAnimations();
        modules.resetAnimations();
        if (settings != null) {
            settings.resetAnimations();
        }
    }

    @Override
    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
        double progress = openAnimation;
        if (progress <= 0.001) {
            return;
        }
        GuiTheme.backdrop(renderer, width(), height(), progress);

        categories.render(renderer, mouseX, mouseY, deltaMs);
        modules.render(renderer, mouseX, mouseY, deltaMs);
        if (settings != null) {
            settings.render(renderer, mouseX, mouseY, deltaMs);
        }

        String hint = "RIGHT SHIFT close   |   ESC close window   |   LMB toggle   |   RMB settings   |   MMB favorite";
        renderer.drawCenteredText(hint, width() / 2.0, height() - 14.0,
                ColorUtil.withAlpha(nexus.themes().textDim(), 170), false, 1.0);
        renderer.drawText(Nexus.instance().themes().activeName(), 8.0, height() - 14.0,
                ColorUtil.withAlpha(nexus.themes().accent(System.currentTimeMillis()), 190), false, 1.0);
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (settings != null && settings.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (modules.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (categories.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // click on the backdrop focuses the search field
        if (button == 0) {
            searchFocused = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (settings != null && settings.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (modules.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return categories.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (settings != null && settings.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (modules.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return categories.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (settings != null && settings.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        if (modules.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return categories.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (settings != null && settings.keyPressed(keyCode)) {
            return true;
        }
        if (keyCode == KeyCodes.ESCAPE) {
            if (settings != null) {
                closeSettings();
                return true;
            }
            if (!search.isEmpty()) {
                search = "";
                return true;
            }
            // let the GuiManager close the ClickGUI itself
            return false;
        }
        if (keyCode == KeyCodes.BACKSPACE || keyCode == KeyCodes.DELETE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
            return true;
        }
        searchFocused = true;
        return true;
    }

    @Override
    public boolean charTyped(char character) {
        if (settings != null && settings.charTyped(character)) {
            return true;
        }
        if (character >= 32 && character != 127 && search.length() < 48) {
            search += character;
            searchFocused = true;
            return true;
        }
        return false;
    }

    @Override
    public void onClosed() {
        saveLayout();
        resetAnimations();
    }

    // ---------------------------------------------------------------- settings

    private void openSettings(Module module) {
        selectedModule = module;
        settings = new SettingsWindow(module, nexus.client().window().guiWidth() > 620 ? 340.0 : 20.0, 20.0, 212.0, 320.0);
    }

    private void closeSettings() {
        if (settings != null) {
            settings.resetAnimations();
        }
        settings = null;
        selectedModule = null;
    }

    // ------------------------------------------------------------------ layout

    private File layoutFile() {
        return new File(nexus.configManager().folder(), "gui-layout.json");
    }

    public void saveLayout() {
        Json.JsonObject object = Json.object();
        object.set("categories", windowJson(categories));
        object.set("modules", windowJson(modules));
        if (settings != null) {
            object.set("settings", windowJson(settings));
        }
        nexus.configManager().writeObject(layoutFile(), object);
    }

    private Json windowJson(GuiWindow window) {
        Json.JsonObject object = Json.object();
        object.set("x", window.x());
        object.set("y", window.y());
        object.set("w", window.width());
        object.set("h", window.height());
        return object;
    }

    private void loadLayout() {
        Json.JsonObject object = nexus.configManager().readObject(layoutFile());
        applyWindow(object.get("categories"), categories);
        applyWindow(object.get("modules"), modules);
    }

    private void applyWindow(Json value, GuiWindow window) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        window.setPosition(object.double_("x", window.x()), object.double_("y", window.y()));
        window.setSize(object.double_("w", window.width()), object.double_("h", window.height()));
    }

    // ------------------------------------------------------------------ windows

    /** Left window: category selection (including favorites and configs). */
    private final class CategoryWindow extends GuiWindow {

        CategoryWindow(double x, double y, double width, double height) {
            super("Categories", x, y, width, height, false, true);
        }

        @Override
        protected double contentHeight() {
            return (ModuleCategory.values().length + 1) * ROW_HEIGHT + 8.0;
        }

        @Override
        protected void drawContent(IRenderer renderer, double mouseX, double mouseY,
                                   double contentX, double contentY, double viewWidth, double viewHeight) {
            List<String> rows = new ArrayList<String>();
            rows.add("Favorites");
            for (ModuleCategory category : ModuleCategory.values()) {
                rows.add(category.displayName());
            }
            for (int i = 0; i < rows.size(); i++) {
                double rowY = contentY + 4.0 + i * ROW_HEIGHT;
                boolean active = isCategoryActive(rows.get(i));
                boolean hovered = mouseX >= contentX + PADDING && mouseX <= contentX + viewWidth - PADDING
                        && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2.0;
                int accent = nexus.themes().accent(System.currentTimeMillis());
                if (active) {
                    renderer.fillRoundRect(contentX + PADDING, rowY, viewWidth - PADDING * 2.0,
                            ROW_HEIGHT - 2.0, 4.0, ColorUtil.withAlpha(accent, 170));
                } else if (hovered) {
                    renderer.fillRoundRect(contentX + PADDING, rowY, viewWidth - PADDING * 2.0,
                            ROW_HEIGHT - 2.0, 4.0, ColorUtil.withAlpha(0xFFFFFFFF, 22));
                }
                renderer.drawText(rows.get(i), contentX + PADDING + 6.0,
                        rowY + (ROW_HEIGHT - 2.0) / 2.0 - renderer.fontHeight(0.9) / 2.0,
                        active ? 0xFFFFFFFF : (hovered ? nexus.themes().text() : nexus.themes().textDim()),
                        false, 1.0);
            }
        }

        private boolean isCategoryActive(String name) {
            if (name.equals("Favorites")) {
                return selectedCategory == null;
            }
            return selectedCategory != null && selectedCategory.displayName().equals(name);
        }

        @Override
        protected boolean contentClicked(double mouseX, double mouseY, int button) {
            double localY = mouseY - (y + TITLE_HEIGHT) + scroll;
            int index = (int) Math.floor((localY - 4.0) / ROW_HEIGHT);
            if (index < 0 || index > ModuleCategory.values().length) {
                return false;
            }
            if (index == 0) {
                selectedCategory = null;
                return true;
            }
            ModuleCategory category = ModuleCategory.values()[index - 1];
            if (category == ModuleCategory.CONFIGS) {
                nexus.gui().openConfigManager();
                return true;
            }
            selectedCategory = category;
            return true;
        }
    }

    /** Middle window: module list with search, toggles and keybinds. */
    private final class ModuleWindow extends GuiWindow {

        private Module tooltipModule;
        private double tooltipX;
        private double tooltipY;

        ModuleWindow(double x, double y, double width, double height) {
            super("Modules", x, y, width, height, false, true);
        }

        private List<Module> visibleModules() {
            if (!search.isEmpty()) {
                return nexus.modules().search(search);
            }
            if (selectedCategory == null) {
                return nexus.modules().favorites();
            }
            return nexus.modules().byCategory(selectedCategory);
        }

        @Override
        protected double contentHeight() {
            return visibleModules().size() * ROW_HEIGHT + 40.0;
        }

        @Override
        protected void drawContent(IRenderer renderer, double mouseX, double mouseY,
                                   double contentX, double contentY, double viewWidth, double viewHeight) {
            GuiTheme.searchBox(renderer, contentX + PADDING, contentY + 4.0,
                    viewWidth - PADDING * 2.0, 16.0, search, searchFocused);

            List<Module> list = visibleModules();
            tooltipModule = null;
            for (int i = 0; i < list.size(); i++) {
                Module module = list.get(i);
                double rowY = contentY + 26.0 + i * ROW_HEIGHT;
                if (rowY + ROW_HEIGHT < contentY || rowY > contentY + viewHeight + ROW_HEIGHT) {
                    continue;
                }
                boolean hovered = mouseX >= contentX + PADDING && mouseX <= contentX + viewWidth - PADDING
                        && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2.0;
                int accent = nexus.themes().accent(System.currentTimeMillis());
                int rowColor = module.isEnabled()
                        ? ColorUtil.withAlpha(accent, 60)
                        : (hovered ? ColorUtil.withAlpha(0xFFFFFFFF, 20) : ColorUtil.withAlpha(0xFFFFFFFF, 8));
                renderer.fillRoundRect(contentX + PADDING, rowY, viewWidth - PADDING * 2.0,
                        ROW_HEIGHT - 2.0, 4.0, rowColor);
                if (selectedModule == module) {
                    renderer.outlineRoundRect(contentX + PADDING, rowY, viewWidth - PADDING * 2.0,
                            ROW_HEIGHT - 2.0, 4.0, 1.0, accent);
                }
                String star = module.isFavorite() ? "* " : "";
                renderer.drawText(star + module.displayName(), contentX + PADDING + 6.0,
                        rowY + (ROW_HEIGHT - 2.0) / 2.0 - renderer.fontHeight(0.9) / 2.0,
                        module.isEnabled() ? 0xFFFFFFFF : (hovered ? nexus.themes().text() : nexus.themes().textDim()),
                        false, 1.0);

                String bind = module.keybindSetting().keyName();
                if (!bind.equals("NONE")) {
                    renderer.drawText(bind, contentX + viewWidth - PADDING - 6.0 - renderer.textWidth(bind, 1.0),
                            rowY + (ROW_HEIGHT - 2.0) / 2.0 - renderer.fontHeight(0.8) / 2.0,
                            ColorUtil.withAlpha(nexus.themes().textDim(), 190), false, 1.0);
                }
                // animated toggle switch
                double toggleWidth = 22.0;
                double toggleHeight = 10.0;
                double toggleX = contentX + viewWidth - PADDING - 8.0 - toggleWidth - renderer.textWidth(bind, 1.0) - 6.0;
                double toggleY = rowY + (ROW_HEIGHT - 2.0) / 2.0 - toggleHeight / 2.0;
                if (bind.equals("NONE")) {
                    toggleX = contentX + viewWidth - PADDING - 8.0 - toggleWidth;
                }
                GuiTheme.toggle(renderer, toggleX, toggleY, toggleWidth, toggleHeight,
                        module.isEnabled(), module.isEnabled() ? 1.0 : 0.0);

                if (hovered) {
                    tooltipModule = module;
                    tooltipX = mouseX + 8.0;
                    tooltipY = mouseY + 8.0;
                }
            }
            if (list.isEmpty()) {
                renderer.drawCenteredText("No modules found", contentX + viewWidth / 2.0,
                        contentY + 60.0, ColorUtil.withAlpha(nexus.themes().textDim(), 160), false, 1.0);
            }
            if (tooltipModule != null) {
                GuiTheme.tooltip(renderer, tooltipX, tooltipY, new String[]{
                        tooltipModule.displayName(),
                        tooltipModule.description(),
                        tooltipModule.isSupported() ? "Supported" : "Unsupported on this version"});
            }
        }

        @Override
        protected boolean contentClicked(double mouseX, double mouseY, int button) {
            double searchY = y + TITLE_HEIGHT + 4.0 - scroll;
            if (button == 0 && mouseY >= searchY && mouseY <= searchY + 16.0) {
                searchFocused = true;
                return true;
            }
            double localY = mouseY - (y + TITLE_HEIGHT + 26.0) + scroll;
            int index = (int) Math.floor(localY / ROW_HEIGHT);
            List<Module> list = visibleModules();
            if (index < 0 || index >= list.size()) {
                return false;
            }
            Module module = list.get(index);
            if (button == 0) {
                module.toggle();
                nexus.notifications().push(module.isEnabled()
                                ? Notification.Type.MODULE_ENABLED
                                : Notification.Type.MODULE_DISABLED,
                        module.isEnabled() ? "Module enabled" : "Module disabled", module.displayName());
            } else if (button == 1) {
                openSettings(module);
            } else if (button == 2) {
                module.setFavorite(!module.isFavorite());
            }
            return true;
        }
    }

    /** Right window (nested): settings editor for the selected module. */
    private final class SettingsWindow extends GuiWindow {

        private final Module module;
        private Setting<?> textFocused;
        private Setting<?> listeningKey;
        private Setting<?> pickerOpen;

        SettingsWindow(Module module, double x, double y, double width, double height) {
            super(module.displayName(), x, y, width, height, true, true);
            this.module = module;
        }

        private List<Setting<?>> rows() {
            return module.visibleSettings();
        }

        @Override
        protected double contentHeight() {
            double total = 0.0;
            for (Setting<?> setting : rows()) {
                total += rowHeight(setting);
            }
            return total + 8.0;
        }

        private double rowHeight(Setting<?> setting) {
            if (setting == pickerOpen) {
                return ROW_HEIGHT + 84.0;
            }
            if (setting instanceof MultiSelectSetting) {
                int options = ((MultiSelectSetting) setting).options().length;
                return ROW_HEIGHT + Math.ceil(options / 2.0) * 18.0;
            }
            return ROW_HEIGHT;
        }

        @Override
        protected void drawContent(IRenderer renderer, double mouseX, double mouseY,
                                   double contentX, double contentY, double viewWidth, double viewHeight) {
            double cursorY = contentY + 4.0;
            long now = System.currentTimeMillis();
            renderer.drawText(module.description(), contentX + PADDING, cursorY,
                    ColorUtil.withAlpha(nexus.themes().textDim(), 200), false, 1.0);
            cursorY += 12.0;

            for (Setting<?> setting : rows()) {
                double height = rowHeight(setting);
                boolean hovered = mouseX >= contentX + PADDING && mouseX <= contentX + viewWidth - PADDING
                        && mouseY >= cursorY && mouseY <= cursorY + height;
                drawRow(renderer, setting, contentX + PADDING, cursorY, viewWidth - PADDING * 2.0, height, hovered, now);
                cursorY += height;
            }
        }

        private void drawRow(IRenderer renderer, Setting<?> setting, double x, double y,
                             double width, double height, boolean hovered, long now) {
            renderer.drawText(setting.name(), x + 2.0, y + 2.0, nexus.themes().text(), false, 1.0);
            if (hovered && !setting.description().isEmpty()) {
                renderer.drawText(setting.description(), x + 2.0, y + 12.0,
                        ColorUtil.withAlpha(nexus.themes().textDim(), 170), false, 0.7);
            }

            if (setting instanceof BooleanSetting) {
                GuiTheme.toggle(renderer, x + width - 26.0, y + 3.0, 22.0, 10.0,
                        ((BooleanSetting) setting).isEnabled(), ((BooleanSetting) setting).isEnabled() ? 1.0 : 0.0);
            } else if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                GuiTheme.slider(renderer, x + 2.0, y + 12.0, width - 44.0, 6.0, number.progress(), hovered, null);
                renderer.drawText(number.displayValue(), x + width - 38.0, y + 9.0,
                        nexus.themes().textDim(), false, 1.0);
            } else if (setting instanceof ModeSetting) {
                ModeSetting mode = (ModeSetting) setting;
                GuiTheme.button(renderer, x + width - 90.0, y + 1.0, 86.0, 14.0, mode.get(), hovered, true);
            } else if (setting instanceof ColorSetting) {
                ColorSetting color = (ColorSetting) setting;
                renderer.fillRoundRect(x + width - 40.0, y + 1.0, 36.0, 14.0, 3.0, color.effectiveColor(now));
                renderer.outlineRoundRect(x + width - 40.0, y + 1.0, 36.0, 14.0, 3.0, 1.0,
                        ColorUtil.withAlpha(0xFFFFFFFF, 120));
                if (setting == pickerOpen) {
                    drawColorPicker(renderer, color, x, y + ROW_HEIGHT, width, now);
                }
            } else if (setting instanceof StringSetting) {
                StringSetting text = (StringSetting) setting;
                boolean focused = setting == textFocused;
                renderer.fillRoundRect(x + width - 110.0, y + 1.0, 106.0, 14.0, 3.0,
                        ColorUtil.withAlpha(0xFF000000, focused ? 160 : 90));
                String value = focused ? text.rawValue() + "_" : text.displayValue();
                renderer.drawText(value, x + width - 106.0, y + 4.0, nexus.themes().text(), false, 1.0);
            } else if (setting instanceof KeybindSetting) {
                KeybindSetting keybind = (KeybindSetting) setting;
                boolean listening = setting == listeningKey;
                GuiTheme.button(renderer, x + width - 70.0, y + 1.0, 66.0, 14.0,
                        listening ? "PRESS KEY" : keybind.keyName(), hovered || listening, listening);
            } else if (setting instanceof MultiSelectSetting) {
                MultiSelectSetting multi = (MultiSelectSetting) setting;
                String[] options = multi.options();
                double chipX = x + 2.0;
                double chipY = y + ROW_HEIGHT - 4.0;
                for (int i = 0; i < options.length; i++) {
                    double chipWidth = renderer.textWidth(options[i], 1.0) + 12.0;
                    if (chipX + chipWidth > x + width) {
                        chipX = x + 2.0;
                        chipY += 18.0;
                    }
                    GuiTheme.chip(renderer, chipX, chipY, chipWidth, 14.0, options[i], multi.isSelected(options[i]));
                    chipX += chipWidth + 4.0;
                }
            }
        }

        private void drawColorPicker(IRenderer renderer, ColorSetting color, double x, double y, double width, long now) {
            float[] hsb = java.awt.Color.RGBtoHSB(ColorUtil.red(color.color()), ColorUtil.green(color.color()),
                    ColorUtil.blue(color.color()), null);
            renderer.fillRoundRect(x, y, width, 84.0, 4.0, ColorUtil.withAlpha(0xFF000000, 110));
            GuiTheme.slider(renderer, x + 6.0, y + 8.0, width - 12.0, 6.0, hsb[0], false, "Hue");
            GuiTheme.slider(renderer, x + 6.0, y + 24.0, width - 12.0, 6.0, hsb[1], false, "Sat");
            GuiTheme.slider(renderer, x + 6.0, y + 40.0, width - 12.0, 6.0, hsb[2], false, "Bri");
            GuiTheme.slider(renderer, x + 6.0, y + 56.0, width - 12.0, 6.0, ColorUtil.alpha(color.color()) / 255.0,
                    false, "Alpha");
            GuiTheme.chip(renderer, x + 6.0, y + 68.0, renderer.textWidth("Rainbow", 1.0) + 12.0, 12.0,
                    "Rainbow", color.isRainbow());
        }

        private Setting<?> rowAt(double mouseY, double[] outOffset) {
            double localY = mouseY - (y + TITLE_HEIGHT) + scroll;
            double cursor = 16.0;
            for (Setting<?> setting : rows()) {
                double height = rowHeight(setting);
                if (localY >= cursor && localY <= cursor + height) {
                    outOffset[0] = cursor;
                    return setting;
                }
                cursor += height;
            }
            return null;
        }

        @Override
        protected boolean contentClicked(double mouseX, double mouseY, int button) {
            double[] offset = new double[]{0.0};
            Setting<?> setting = rowAt(mouseY, offset);
            if (setting == null) {
                return false;
            }
            double contentX = x + PADDING;
            double contentWidth = width - PADDING * 2.0;
            if (setting instanceof BooleanSetting) {
                ((BooleanSetting) setting).toggle();
            } else if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                double relative = (mouseX - (contentX + 2.0)) / Math.max(1.0, contentWidth - 44.0);
                number.setProgress(MathUtil.clamp(relative, 0.0, 1.0));
                dragging = true;
            } else if (setting instanceof ModeSetting) {
                ((ModeSetting) setting).cycle(button == 1 ? -1 : 1);
            } else if (setting instanceof ColorSetting) {
                if (setting == pickerOpen) {
                    handlePickerClick((ColorSetting) setting, mouseX, mouseY, contentX, offset[0], contentWidth);
                } else {
                    pickerOpen = setting;
                }
            } else if (setting instanceof StringSetting) {
                textFocused = setting == textFocused ? null : setting;
            } else if (setting instanceof KeybindSetting) {
                listeningKey = setting;
            } else if (setting instanceof MultiSelectSetting) {
                handleMultiClick((MultiSelectSetting) setting, mouseX, mouseY, contentX, offset[0], contentWidth);
            }
            return true;
        }

        private void handlePickerClick(ColorSetting color, double mouseX, double mouseY,
                                       double contentX, double rowY, double contentWidth) {
            double windowY = y + TITLE_HEIGHT - scroll + rowY;
            float[] hsb = java.awt.Color.RGBtoHSB(ColorUtil.red(color.color()), ColorUtil.green(color.color()),
                    ColorUtil.blue(color.color()), null);
            double[] targets = new double[]{hsb[0], hsb[1], hsb[2], ColorUtil.alpha(color.color()) / 255.0};
            String[] names = new String[]{"hue", "sat", "bri", "alpha"};
            for (int i = 0; i < 4; i++) {
                double sliderY = windowY + ROW_HEIGHT + 8.0 + i * 16.0;
                if (mouseY >= sliderY - 3.0 && mouseY <= sliderY + 9.0) {
                    double relative = MathUtil.clamp((mouseX - (contentX + 6.0)) / Math.max(1.0, contentWidth - 12.0), 0.0, 1.0);
                    targets[i] = (float) relative;
                    applyPicker(color, targets);
                    return;
                }
            }
            double chipY = windowY + ROW_HEIGHT + 68.0;
            if (mouseY >= chipY && mouseY <= chipY + 12.0) {
                color.setRainbow(!color.isRainbow());
            }
        }

        private void applyPicker(ColorSetting color, double[] values) {
            int rgb = java.awt.Color.HSBtoRGB((float) values[0], (float) values[1], (float) values[2]);
            color.setSilently(Integer.valueOf(ColorUtil.withAlpha(rgb, (int) (values[3] * 255.0))));
            color.set(Integer.valueOf(ColorUtil.withAlpha(rgb, (int) (values[3] * 255.0))));
        }

        private void handleMultiClick(MultiSelectSetting setting, double mouseX, double mouseY,
                                      double contentX, double rowY, double contentWidth) {
            double windowY = y + TITLE_HEIGHT - scroll + rowY + ROW_HEIGHT - 4.0;
            if (mouseY < windowY) {
                return;
            }
            int rowIndex = (int) Math.floor((mouseY - windowY) / 18.0);
            String[] options = setting.options();
            double chipX = contentX + 2.0;
            int index = 0;
            for (int row = 0; row <= rowIndex; row++) {
                chipX = contentX + 2.0;
                while (index < options.length) {
                    double chipWidth = Nexus.instance().render().renderer() == null
                            ? options[index].length() * 5.0 + 12.0
                            : Nexus.instance().render().renderer().textWidth(options[index], 1.0) + 12.0;
                    if (chipX + chipWidth > contentX + contentWidth) {
                        break;
                    }
                    if (row == rowIndex && mouseX >= chipX && mouseX <= chipX + chipWidth) {
                        setting.toggle(options[index]);
                        return;
                    }
                    chipX += chipWidth + 4.0;
                    index++;
                }
                if (row < rowIndex) {
                    // skip the wrapped option
                    if (index < options.length) {
                        index++;
                    }
                }
            }
        }

        @Override
        protected boolean contentReleased(double mouseX, double mouseY, int button) {
            return dragging;
        }

        @Override
        protected boolean contentDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            double[] offset = new double[]{0.0};
            Setting<?> setting = rowAt(mouseY, offset);
            if (setting instanceof NumberSetting && dragging) {
                NumberSetting number = (NumberSetting) setting;
                double contentWidth = width - PADDING * 2.0;
                double relative = (mouseX - (x + PADDING + 2.0)) / Math.max(1.0, contentWidth - 44.0);
                number.setProgress(MathUtil.clamp(relative, 0.0, 1.0));
                return true;
            }
            return false;
        }

        @Override
        protected boolean contentScrolled(double mouseX, double mouseY, double amount) {
            double[] offset = new double[]{0.0};
            Setting<?> setting = rowAt(mouseY, offset);
            if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                double step = number.step() > 0 ? number.step() : (number.max() - number.min()) / 100.0;
                number.set(Double.valueOf(number.value() - amount * step * (number.isInteger() ? 1.0 : 4.0)));
                return true;
            }
            if (setting instanceof ModeSetting) {
                ((ModeSetting) setting).cycle(amount > 0 ? 1 : -1);
                return true;
            }
            return false;
        }

        public boolean keyPressed(int keyCode) {
            if (listeningKey != null) {
                ((KeybindSetting) listeningKey).set(Integer.valueOf(keyCode));
                nexus.keybinds().syncFromModules(nexus.modules().all());
                listeningKey = null;
                return true;
            }
            if (textFocused != null && keyCode != KeyCodes.ESCAPE) {
                if (keyCode == KeyCodes.BACKSPACE) {
                    StringSetting text = (StringSetting) textFocused;
                    String value = text.rawValue();
                    if (!value.isEmpty()) {
                        text.set(value.substring(0, value.length() - 1));
                    }
                    return true;
                }
                return true;
            }
            return false;
        }

        public boolean charTyped(char character) {
            if (listeningKey != null) {
                return true;
            }
            if (textFocused instanceof StringSetting && character >= 32 && character != 127) {
                StringSetting text = (StringSetting) textFocused;
                text.set(text.rawValue() + character);
                return true;
            }
            return false;
        }

        @Override
        protected void onCloseRequested() {
            closeSettings();
        }
    }

    /** Exposed for tests and for the diagnostics report. */
    public int openWindowCount() {
        int count = 2;
        if (settings != null) {
            count++;
        }
        return count;
    }

    public Module selectedModule() {
        return selectedModule;
    }

    public ModuleCategory selectedCategory() {
        return selectedCategory;
    }

    public String search() {
        return search;
    }

    public void setSearch(String value) {
        search = value == null ? "" : value;
    }

    public double openAnimation() {
        return openAnimation;
    }
}
