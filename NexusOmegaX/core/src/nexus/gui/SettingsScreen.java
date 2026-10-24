package nexus.gui;

import nexus.api.IRenderer;
import nexus.api.KeyCodes;
import nexus.api.screen.NexusScreen;
import nexus.core.Nexus;
import nexus.core.keybind.KeybindManager;
import nexus.core.setting.Setting;
import nexus.core.theme.Theme;
import nexus.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/** Client settings: themes, fonts, notifications, HUD options and keybinds. */
public final class SettingsScreen extends NexusScreen {

    private enum Tab {
        THEME("Theme"), NOTIFICATIONS("Notifications"), HUD("HUD"), KEYBINDS("Keybinds"), INFO("Info");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final Nexus nexus;
    private Tab tab = Tab.THEME;
    private double scroll;
    private KeybindManager.Bind listeningBind;

    public SettingsScreen(Nexus nexus) {
        this.nexus = nexus;
    }

    @Override
    public String title() {
        return "Nexus Settings";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.ESCAPE) {
            return false;
        }
        if (listeningBind != null) {
            listeningBind.setKey(keyCode == KeyCodes.ESCAPE ? -1 : keyCode);
            nexus.keybinds().syncToModules(nexus.modules().all());
            nexus.configManager().markDirty();
            listeningBind = null;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0.0, scroll - amount * 14.0);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double width = Math.min(460.0, width() - 40.0);
        double height = Math.min(380.0, height() - 40.0);
        double x = (width() - width) / 2.0;
        double y = (height() - height) / 2.0;
        long now = System.currentTimeMillis();

        // tabs
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            double tabX = x + 12.0 + i * ((width - 24.0) / tabs.length);
            if (mouseY >= y + 30.0 && mouseY <= y + 50.0 && mouseX >= tabX
                    && mouseX <= tabX + (width - 24.0) / tabs.length - 4.0) {
                tab = tabs[i];
                scroll = 0.0;
                return true;
            }
        }

        double contentY = y + 60.0;
        if (tab == Tab.THEME) {
            List<Theme> themes = nexus.themes().themes();
            for (int i = 0; i < themes.size(); i++) {
                double rowY = contentY + i * 26.0 - scroll;
                if (mouseX >= x + 16.0 && mouseX <= x + width - 16.0 && mouseY >= rowY && mouseY <= rowY + 22.0) {
                    nexus.themes().setActive(themes.get(i).name());
                    return true;
                }
            }
            double fontY = contentY + themes.size() * 26.0 + 10.0 - scroll;
            String[] fonts = nexus.themes().availableFonts();
            for (int i = 0; i < fonts.length; i++) {
                double fx = x + 16.0 + i * 88.0;
                if (mouseY >= fontY && mouseY <= fontY + 20.0 && mouseX >= fx && mouseX <= fx + 84.0) {
                    nexus.themes().setFont(fonts[i]);
                    return true;
                }
            }
        } else if (tab == Tab.NOTIFICATIONS) {
            handleSettingsClick(nexus.notifications().settings(), mouseX, mouseY, x + 16.0, contentY - scroll, width - 32.0);
        } else if (tab == Tab.HUD) {
            handleSettingsClick(nexus.hud().settings(), mouseX, mouseY, x + 16.0, contentY - scroll, width - 32.0);
        } else if (tab == Tab.KEYBINDS) {
            List<KeybindManager.Bind> binds = nexus.keybinds().bound();
            for (int i = 0; i < binds.size(); i++) {
                double rowY = contentY + i * 22.0 - scroll;
                if (mouseX >= x + 16.0 && mouseX <= x + width - 16.0 && mouseY >= rowY && mouseY <= rowY + 20.0) {
                    KeybindManager.Bind bind = binds.get(i);
                    if (mouseX > x + width - 130.0) {
                        listeningBind = bind;
                    } else if (mouseX > x + width - 200.0) {
                        bind.setMode(bind.mode() == KeybindManager.Mode.TOGGLE
                                ? KeybindManager.Mode.HOLD
                                : (bind.mode() == KeybindManager.Mode.HOLD
                                ? KeybindManager.Mode.DOUBLE_CLICK : KeybindManager.Mode.TOGGLE));
                        nexus.keybinds().syncToModules(nexus.modules().all());
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private void handleSettingsClick(List<Setting<?>> settings, double mouseX, double mouseY,
                                     double x, double y, double width) {
        for (int i = 0; i < settings.size(); i++) {
            Setting<?> setting = settings.get(i);
            double rowY = y + i * 26.0;
            if (mouseY < rowY || mouseY > rowY + 22.0) {
                continue;
            }
            if (setting instanceof nexus.core.setting.BooleanSetting) {
                ((nexus.core.setting.BooleanSetting) setting).toggle();
            } else if (setting instanceof nexus.core.setting.ModeSetting) {
                ((nexus.core.setting.ModeSetting) setting).cycle(1);
            } else if (setting instanceof nexus.core.setting.NumberSetting) {
                nexus.core.setting.NumberSetting number = (nexus.core.setting.NumberSetting) setting;
                number.setProgress(MathUtilClamp((mouseX - (x + width - 120.0)) / 100.0));
            } else if (setting instanceof nexus.core.setting.ColorSetting) {
                nexus.core.setting.ColorSetting color = (nexus.core.setting.ColorSetting) setting;
                color.setRainbow(!color.isRainbow());
            }
            nexus.configManager().markDirty();
            return;
        }
    }

    private static double MathUtilClamp(double value) {
        return value < 0.0 ? 0.0 : (value > 1.0 ? 1.0 : value);
    }

    @Override
    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
        long now = System.currentTimeMillis();
        GuiTheme.backdrop(renderer, width(), height(), 0.9);

        double width = Math.min(460.0, width() - 40.0);
        double height = Math.min(380.0, height() - 40.0);
        double x = (width() - width) / 2.0;
        double y = (height() - height) / 2.0;

        GuiTheme.panel(renderer, x, y, width, height);
        GuiTheme.titleBar(renderer, x, y, width, "Nexus Settings", 26.0);

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            double tabX = x + 12.0 + i * ((width - 24.0) / tabs.length);
            boolean active = tabs[i] == tab;
            GuiTheme.button(renderer, tabX, y + 30.0, (width - 24.0) / tabs.length - 4.0, 20.0,
                    tabs[i].label,
                    mouseX >= tabX && mouseX <= tabX + (width - 24.0) / tabs.length - 4.0
                            && mouseY >= y + 30.0 && mouseY <= y + 50.0, active);
        }

        double contentY = y + 60.0;
        renderer.pushScissor(x + 8.0, contentY, width - 16.0, height - 70.0);
        if (tab == Tab.THEME) {
            renderThemes(renderer, x + 16.0, contentY - scroll, width - 32.0, mouseX, mouseY, now);
        } else if (tab == Tab.NOTIFICATIONS) {
            renderSettings(renderer, nexus.notifications().settings(), x + 16.0, contentY - scroll, width - 32.0, mouseX, mouseY);
        } else if (tab == Tab.HUD) {
            renderSettings(renderer, nexus.hud().settings(), x + 16.0, contentY - scroll, width - 32.0, mouseX, mouseY);
        } else if (tab == Tab.KEYBINDS) {
            renderKeybinds(renderer, x + 16.0, contentY - scroll, width - 32.0, mouseX, mouseY, now);
        } else {
            int index = 0;
            for (String line : nexus.diagnostics().report().split("\\n")) {
                renderer.drawText(line, x + 16.0, contentY - scroll + index * 11.0,
                        nexus.themes().textDim(), false, 0.72);
                index++;
            }
        }
        renderer.popScissor();
    }

    private void renderThemes(IRenderer renderer, double x, double y, double width,
                              double mouseX, double mouseY, long now) {
        List<Theme> themes = nexus.themes().themes();
        for (int i = 0; i < themes.size(); i++) {
            Theme theme = themes.get(i);
            double rowY = y + i * 26.0;
            boolean active = theme == nexus.themes().activeTheme();
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + 22.0;
            renderer.fillRoundRect(x, rowY, width, 20.0, 4.0,
                    active ? ColorUtil.withAlpha(theme.primary(now), 170)
                            : (hovered ? ColorUtil.withAlpha(0xFFFFFFFF, 22) : ColorUtil.withAlpha(0xFFFFFFFF, 10)));
            renderer.drawText(theme.name(), x + 8.0, rowY + 6.0, nexus.themes().text(), false, 0.85);
            // palette preview
            int[] colors = new int[]{theme.primary(now), theme.secondary(), theme.accent(now), theme.highlight()};
            for (int c = 0; c < colors.length; c++) {
                renderer.fillRect(x + width - 90.0 + c * 20.0, rowY + 4.0, 16.0, 12.0, colors[c]);
            }
        }
        double fontY = y + themes.size() * 26.0 + 10.0;
        renderer.drawText("Font", x, fontY, nexus.themes().text(), false, 0.85);
        String[] fonts = nexus.themes().availableFonts();
        for (int i = 0; i < fonts.length; i++) {
            double fx = x + i * 88.0;
            boolean active = fonts[i].equalsIgnoreCase(nexus.themes().font());
            GuiTheme.button(renderer, fx, fontY + 16.0, 84.0, 20.0, fonts[i],
                    mouseX >= fx && mouseX <= fx + 84.0 && mouseY >= fontY + 16.0 && mouseY <= fontY + 36.0, active);
        }
    }

    private void renderSettings(IRenderer renderer, List<Setting<?>> settings, double x, double y,
                                double width, double mouseX, double mouseY) {
        for (int i = 0; i < settings.size(); i++) {
            Setting<?> setting = settings.get(i);
            double rowY = y + i * 26.0;
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + 22.0;
            if (hovered) {
                renderer.fillRoundRect(x, rowY, width, 22.0, 4.0, ColorUtil.withAlpha(0xFFFFFFFF, 16));
            }
            renderer.drawText(setting.name(), x + 6.0, rowY + 6.0, nexus.themes().text(), false, 0.85);
            if (setting instanceof nexus.core.setting.BooleanSetting) {
                GuiTheme.toggle(renderer, x + width - 30.0, rowY + 6.0, 24.0, 10.0,
                        ((nexus.core.setting.BooleanSetting) setting).isEnabled(),
                        ((nexus.core.setting.BooleanSetting) setting).isEnabled() ? 1.0 : 0.0);
            } else if (setting instanceof nexus.core.setting.ModeSetting) {
                GuiTheme.button(renderer, x + width - 120.0, rowY + 3.0, 116.0, 16.0, setting.displayValue(), hovered, true);
            } else if (setting instanceof nexus.core.setting.NumberSetting) {
                nexus.core.setting.NumberSetting number = (nexus.core.setting.NumberSetting) setting;
                GuiTheme.slider(renderer, x + width - 120.0, rowY + 8.0, 100.0, 6.0, number.progress(), hovered, null);
                renderer.drawText(number.displayValue(), x + width - 16.0, rowY + 6.0,
                        nexus.themes().textDim(), false, 0.8);
            } else if (setting instanceof nexus.core.setting.ColorSetting) {
                nexus.core.setting.ColorSetting color = (nexus.core.setting.ColorSetting) setting;
                renderer.fillRoundRect(x + width - 40.0, rowY + 3.0, 36.0, 16.0, 3.0, color.effectiveColor(System.currentTimeMillis()));
            } else {
                renderer.drawText(setting.displayValue(), x + width - 120.0, rowY + 6.0,
                        nexus.themes().textDim(), false, 0.8);
            }
        }
    }

    private void renderKeybinds(IRenderer renderer, double x, double y, double width,
                                double mouseX, double mouseY, long now) {
        List<KeybindManager.Bind> binds = new ArrayList<KeybindManager.Bind>(nexus.keybinds().bound());
        renderer.drawText(binds.size() + " bound keys - click the key area to rebind, click the mode to cycle",
                x, y - 12.0, ColorUtil.withAlpha(nexus.themes().textDim(), 200), false, 0.75);
        for (int i = 0; i < binds.size(); i++) {
            KeybindManager.Bind bind = binds.get(i);
            double rowY = y + i * 22.0;
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + 20.0;
            if (hovered) {
                renderer.fillRoundRect(x, rowY, width, 20.0, 4.0, ColorUtil.withAlpha(0xFFFFFFFF, 16));
            }
            renderer.drawText(bind.displayName(), x + 6.0, rowY + 6.0, nexus.themes().text(), false, 0.85);
            boolean listening = bind == listeningBind;
            GuiTheme.button(renderer, x + width - 70.0, rowY + 2.0, 66.0, 16.0,
                    listening ? "PRESS KEY" : bind.keyName(), hovered || listening, listening);
            GuiTheme.button(renderer, x + width - 140.0, rowY + 2.0, 66.0, 16.0, bind.mode().display(), hovered, false);
            List<KeybindManager.Bind> conflicts = nexus.keybinds().conflicts(bind.key(), bind);
            if (!conflicts.isEmpty()) {
                renderer.drawText("!", x + width - 148.0, rowY + 6.0, nexus.themes().error(), false, 0.9);
            }
        }
    }
}
