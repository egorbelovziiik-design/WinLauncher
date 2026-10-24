package nexus.core.module;

import nexus.core.Nexus;
import nexus.core.event.SettingChangeEvent;
import nexus.core.setting.Setting;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of every module of the client.
 */
public final class ModuleManager {

    private static final Comparator<Module> BY_NAME = new Comparator<Module>() {
        @Override
        public int compare(Module a, Module b) {
            return a.displayName().compareToIgnoreCase(b.displayName());
        }
    };

    /** Array list ordering: longest name first, then alphabetically. */
    private static final Comparator<Module> BY_HUD = new Comparator<Module>() {
        @Override
        public int compare(Module a, Module b) {
            int widthA = a.displayName().length() + a.hudInfo().length();
            int widthB = b.displayName().length() + b.hudInfo().length();
            if (widthA != widthB) {
                return Integer.compare(widthB, widthA);
            }
            return a.displayName().compareToIgnoreCase(b.displayName());
        }
    };

    private final List<Module> modules = new ArrayList<Module>();
    private final Map<String, Module> byName = new LinkedHashMap<String, Module>();
    private final Map<ModuleCategory, List<Module>> byCategory = new HashMap<ModuleCategory, List<Module>>();

    public Module register(Module module) {
        if (byName.containsKey(module.name().toLowerCase(Locale.ROOT))) {
            nexus.util.Log.warn("Duplicate module id ignored: " + module.name());
            return module;
        }
        modules.add(module);
        byName.put(module.name().toLowerCase(Locale.ROOT), module);
        List<Module> categoryList = byCategory.get(module.category());
        if (categoryList == null) {
            categoryList = new ArrayList<Module>();
            byCategory.put(module.category(), categoryList);
        }
        categoryList.add(module);
        Nexus.instance().keybinds().registerModule(module);
        return module;
    }

    /** Sorts every module list alphabetically by display name. */
    public void sortAll() {
        Collections.sort(modules, BY_NAME);
        for (List<Module> list : byCategory.values()) {
            Collections.sort(list, BY_NAME);
        }
    }

    public void registerAll(List<Module> list) {
        for (Module module : list) {
            register(module);
        }
    }

    public List<Module> all() {
        return modules;
    }

    public int size() {
        return modules.size();
    }

    public Module byName(String name) {
        if (name == null) {
            return null;
        }
        Module direct = byName.get(name.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        // fall back to display name matching ("Auto Sprint" == "autosprint")
        String normalized = name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (Module module : modules) {
            if (module.displayName().replaceAll("\\s+", "").equalsIgnoreCase(normalized)) {
                return module;
            }
        }
        return null;
    }

    public List<Module> byCategory(ModuleCategory category) {
        List<Module> list = byCategory.get(category);
        if (list == null) {
            return Collections.emptyList();
        }
        List<Module> sorted = new ArrayList<Module>(list);
        Collections.sort(sorted, BY_NAME);
        return sorted;
    }

    public List<Module> enabled() {
        List<Module> list = new ArrayList<Module>();
        for (Module module : modules) {
            if (module.isEnabled() && !module.isHiddenFromArray()) {
                list.add(module);
            }
        }
        Collections.sort(list, BY_HUD);
        return list;
    }

    public List<Module> enabledRaw() {
        List<Module> list = new ArrayList<Module>();
        for (Module module : modules) {
            if (module.isEnabled()) {
                list.add(module);
            }
        }
        return list;
    }

    /** Search used by the ClickGUI search bar and {@code .search}. */
    public List<Module> search(String query) {
        List<Module> result = new ArrayList<Module>();
        if (query == null || query.trim().isEmpty()) {
            return result;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (Module module : modules) {
            if (module.displayName().toLowerCase(Locale.ROOT).contains(needle)
                    || module.name().toLowerCase(Locale.ROOT).contains(needle)
                    || module.description().toLowerCase(Locale.ROOT).contains(needle)
                    || module.category().name().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(module);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    public List<Module> favorites() {
        List<Module> result = new ArrayList<Module>();
        for (Module module : modules) {
            if (module.isFavorite()) {
                result.add(module);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    /** Disables every module (PANIC). Client-critical modules are never disabled. */
    public int disableAll() {
        int count = 0;
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.setEnabled(false, false);
                count++;
            }
        }
        return count;
    }

    public int enabledCount() {
        int count = 0;
        for (Module module : modules) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public int unsupportedCount() {
        int count = 0;
        for (Module module : modules) {
            if (!module.isSupported()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        for (Module module : modules) {
            object.set(module.name(), module.toJson());
        }
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        for (Module module : modules) {
            Json stored = object.get(module.name());
            if (!stored.isNull()) {
                try {
                    module.fromJson(stored);
                } catch (Throwable throwable) {
                    nexus.util.Log.error("Failed to load module " + module.name(), throwable);
                }
            }
        }
    }

    /** Notifies listeners (HUD, config autosave) that a setting changed. */
    public void notifySettingChanged(Setting<?> setting) {
        Nexus.eventBus().post(new SettingChangeEvent(setting));
    }
}
