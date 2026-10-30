package dev.axsesclient.common.modules;

import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.settings.Setting;
import dev.axsesclient.common.settings.SettingManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModuleManager {
    public enum SortOrder {
        DEFAULT,
        ALPHABETICAL,
        ENABLED_FIRST,
        FAVORITES_FIRST
    }

    private final SettingManager settingManager;
    private final NotificationManager notificationManager;
    private LocalizationManager localizationManager;
    private final Map<String, Module> modulesById = new LinkedHashMap<String, Module>();
    private final List<String> searchHistory = new ArrayList<String>();
    private SortOrder sortOrder = SortOrder.DEFAULT;

    public ModuleManager(SettingManager settingManager, NotificationManager notificationManager) {
        this.settingManager = settingManager;
        this.notificationManager = notificationManager;
    }

    public void setLocalizationManager(LocalizationManager localizationManager) {
        this.localizationManager = localizationManager;
    }

    public synchronized <M extends Module> M register(M module) {
        modulesById.put(module.getId(), module);
        settingManager.register(module.getId(), module.getKeybind());
        for (Setting<?> s : module.getSettings()) {
            settingManager.register(module.getId(), s);
        }
        return module;
    }

    public synchronized Module getById(String id) {
        return modulesById.get(id);
    }

    public synchronized List<Module> getAll() {
        return Collections.unmodifiableList(new ArrayList<Module>(modulesById.values()));
    }

    public synchronized List<Module> getByCategory(ModuleCategory category) {
        List<Module> list = new ArrayList<Module>();
        for (Module m : modulesById.values()) {
            if (m.getCategory() == category) {
                list.add(m);
            }
        }
        sortInPlace(list);
        return list;
    }

    public synchronized List<Module> getFavorites() {
        List<Module> list = new ArrayList<Module>();
        for (Module m : modulesById.values()) {
            if (m.isFavorite()) {
                list.add(m);
            }
        }
        sortInPlace(list);
        return list;
    }

    public synchronized List<Module> query(ModuleCategory categoryOrNull, boolean favoritesOnly, String searchQuery) {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<Module>();
        for (Module m : modulesById.values()) {
            if (favoritesOnly && !m.isFavorite()) {
                continue;
            }
            if (categoryOrNull != null && !favoritesOnly && q.isEmpty() && m.getCategory() != categoryOrNull) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean matches = m.getName().toLowerCase(Locale.ROOT).contains(q)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(q)
                        || m.getId().toLowerCase(Locale.ROOT).contains(q)
                        || m.getCategory().getDefaultTitle().toLowerCase(Locale.ROOT).contains(q);
                if (!matches && localizationManager != null) {
                    String locName = localizationManager.moduleName(m.getId(), m.getName()).toLowerCase(Locale.ROOT);
                    String locDesc = localizationManager.moduleDesc(m.getId(), m.getDescription()).toLowerCase(Locale.ROOT);
                    matches = locName.contains(q) || locDesc.contains(q);
                }
                if (!matches) {
                    for (Setting<?> s : m.getSettings()) {
                        if (s.getName().toLowerCase(Locale.ROOT).contains(q)
                                || s.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (!matches) {
                    continue;
                }
            }
            result.add(m);
        }
        sortInPlace(result);
        return result;
    }

    public synchronized void recordSearchQuery(String query) {
        if (query == null) {
            return;
        }
        String trimmed = query.trim();
        if (trimmed.length() < 2) {
            return;
        }
        searchHistory.remove(trimmed);
        searchHistory.add(0, trimmed);
        while (searchHistory.size() > 10) {
            searchHistory.remove(searchHistory.size() - 1);
        }
    }

    public synchronized List<String> getSearchHistory() {
        return Collections.unmodifiableList(new ArrayList<String>(searchHistory));
    }

    public synchronized void clearSearchHistory() {
        searchHistory.clear();
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    public void cycleSortOrder() {
        SortOrder[] vals = SortOrder.values();
        this.sortOrder = vals[(sortOrder.ordinal() + 1) % vals.length];
    }

    private void sortInPlace(List<Module> list) {
        switch (sortOrder) {
            case ALPHABETICAL:
                Collections.sort(list, new Comparator<Module>() {
                    @Override
                    public int compare(Module a, Module b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                break;
            case ENABLED_FIRST:
                Collections.sort(list, new Comparator<Module>() {
                    @Override
                    public int compare(Module a, Module b) {
                        if (a.isEnabled() != b.isEnabled()) {
                            return a.isEnabled() ? -1 : 1;
                        }
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                break;
            case FAVORITES_FIRST:
                Collections.sort(list, new Comparator<Module>() {
                    @Override
                    public int compare(Module a, Module b) {
                        if (a.isFavorite() != b.isFavorite()) {
                            return a.isFavorite() ? -1 : 1;
                        }
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                break;
            default:
                break;
        }
    }

    public void toggleModuleWithFeedback(Module module, MinecraftAdapter mc) {
        String reason = module.getIncompatibilityReason(mc);
        if (!module.isEnabled() && reason != null) {
            notificationManager.push(module.getName(), reason, NotificationManager.Severity.WARNING);
            return;
        }
        boolean next = !module.isEnabled();
        if (module.setEnabled(next, mc) && notificationManager.isModuleToggleAlerts()) {
            notificationManager.push(
                    module.getName(),
                    next ? "Enabled" : "Disabled",
                    next ? NotificationManager.Severity.SUCCESS : NotificationManager.Severity.INFO,
                    1800L
            );
        }
    }

    public void onTick(MinecraftAdapter mc) {
        for (Module m : modulesById.values()) {
            if (!m.isEnabled()) {
                continue;
            }
            String reason = m.getIncompatibilityReason(mc);
            if (reason != null) {
                m.setEnabled(false, mc);
                notificationManager.push(m.getName() + " Disabled", reason, NotificationManager.Severity.WARNING);
                continue;
            }
            try {
                m.onTick(mc);
                m.clearErrors();
            } catch (Throwable t) {
                handleModuleFault(m, mc, t);
            }
        }
    }

    public void onRender(RenderAdapter renderer, MinecraftAdapter mc, float deltaSeconds) {
        for (Module m : modulesById.values()) {
            if (!m.isEnabled()) {
                continue;
            }
            try {
                m.onRender(renderer, mc, deltaSeconds);
            } catch (Throwable t) {
                handleModuleFault(m, mc, t);
            }
        }
    }

    private void handleModuleFault(Module m, MinecraftAdapter mc, Throwable t) {
        int count = m.recordError(t);
        if (count >= 3) {
            m.setEnabled(false, mc);
            notificationManager.push(
                    "Module Isolated: " + m.getName(),
                    "Auto-disabled after repeated error (" + m.getLastErrorMessage() + ")",
                    NotificationManager.Severity.ERROR,
                    4500L
            );
        }
    }

    public synchronized void resetAll(MinecraftAdapter mc) {
        for (Module m : modulesById.values()) {
            m.setEnabled(false, mc);
            m.setFavorite(false);
            m.resetSettings(mc);
        }
        searchHistory.clear();
        sortOrder = SortOrder.DEFAULT;
    }
}
