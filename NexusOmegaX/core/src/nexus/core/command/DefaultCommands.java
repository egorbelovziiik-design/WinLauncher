package nexus.core.command;

import nexus.api.KeyCodes;
import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.profile.ClientProfile;
import nexus.core.setting.Setting;
import nexus.util.ColorUtil;
import nexus.util.FileUtil;
import nexus.util.Log;
import nexus.util.TimeUtil;
import nexus.util.json.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Registers every default Nexus command (prefix {@code .}). */
public final class DefaultCommands {

    /** Executor used by the {@link SimpleCommand} helper. */
    private interface Executor {
        void run(Command.Context context);
    }

    private interface Completer {
        List<String> complete(Command.Context context);
    }

    private static final class SimpleCommand extends Command {

        private final Executor executor;
        private final Completer completer;

        SimpleCommand(String name, String description, String usage, Executor executor) {
            this(name, description, usage, executor, null, new String[0]);
        }

        SimpleCommand(String name, String description, String usage, Executor executor,
                      Completer completer, String... aliases) {
            super(name, description, usage, aliases);
            this.executor = executor;
            this.completer = completer;
        }

        @Override
        public void execute(Command.Context context) {
            executor.run(context);
        }

        @Override
        public List<String> complete(Command.Context context) {
            return completer == null ? super.complete(context) : completer.complete(context);
        }
    }

    private DefaultCommands() {
    }

    public static void register(CommandManager manager) {
        final Nexus nexus = manager.nexus();

        manager.register(new SimpleCommand("menu", "Opens the Nexus main menu", ".menu",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        nexus.gui().openMainMenu();
                    }
                }));

        manager.register(new SimpleCommand("nick", "Sets the nickname shown in the Nexus menu",
                ".nick <name|reset>",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() == 0) {
                            String current = nexus.profile().nickname();
                            context.reply("§7Nickname: §f" + (current.isEmpty() ? "not set" : current));
                            return;
                        }
                        String value = "reset".equalsIgnoreCase(context.arg(0))
                                ? "" : ClientProfile.sanitize(context.arg(0));
                        nexus.profile().setNickname(value);
                        context.reply(value.isEmpty()
                                ? "§7Nickname cleared" : "§dNickname set to §f" + value);
                    }
                }));

        manager.register(new SimpleCommand("help", "Lists commands or shows details",
                ".help [command|page]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() > 0) {
                            Command command = context.manager().byName(context.arg(0));
                            if (command == null) {
                                context.error("Unknown command: " + context.arg(0));
                                return;
                            }
                            context.reply("§d" + command.name() + "§7 - " + command.description());
                            context.reply("§7Usage: §f" + command.usage());
                            return;
                        }
                        int page = 1;
                        if (context.argCount() > 0) {
                            try {
                                page = Integer.parseInt(context.arg(0));
                            } catch (NumberFormatException ignored) {
                                page = 1;
                            }
                        }
                        List<Command> commands = context.manager().all();
                        int perPage = 8;
                        int pages = Math.max(1, (commands.size() + perPage - 1) / perPage);
                        page = Math.max(1, Math.min(pages, page));
                        context.reply("§d" + NexusInfo.PRODUCT + " §7commands (page " + page + "/" + pages + ")");
                        for (int i = (page - 1) * perPage; i < Math.min(commands.size(), page * perPage); i++) {
                            Command command = commands.get(i);
                            context.reply("§7.§f" + command.name() + " §7- " + command.description());
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        List<String> names = new ArrayList<String>();
                        for (Command command : context.manager().all()) {
                            names.add(command.name());
                        }
                        return names;
                    }
                }, "?"));

        manager.register(new SimpleCommand("bind", "Binds a module to a key", ".bind MODULE KEY",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() < 1) {
                            context.error("Usage: .bind MODULE KEY");
                            return;
                        }
                        Module module = nexus.modules().byName(context.arg(0));
                        if (module == null) {
                            context.error("Unknown module: " + context.arg(0));
                            return;
                        }
                        if (context.argCount() < 2) {
                            context.reply(module.displayName() + " is bound to §d" + module.keybindSetting().keyName());
                            return;
                        }
                        String keyText = context.arg(1).toUpperCase();
                        nexus.keybinds().syncFromModules(nexus.modules().all());
                        nexus.core.keybind.KeybindManager.Bind bind = nexus.keybinds().forModule(module);
                        if (keyText.equals("NONE")) {
                            if (bind != null) {
                                bind.setKey(KeyCodes.NONE);
                            }
                            module.setKeybind(KeyCodes.NONE);
                            context.success(module.displayName() + " unbound");
                            nexus.configManager().markDirty();
                            return;
                        }
                        int key = KeyCodes.parse(keyText);
                        if (key == KeyCodes.NONE) {
                            context.error("Unknown key: " + keyText);
                            return;
                        }
                        if (bind != null) {
                            bind.setKey(key);
                        }
                        module.setKeybind(key);
                        List<nexus.core.keybind.KeybindManager.Bind> conflicts = nexus.keybinds().conflicts(key, bind);
                        if (!conflicts.isEmpty()) {
                            context.reply("§eConflict with: " + conflicts.get(0).displayName());
                        }
                        context.success(module.displayName() + " bound to §d" + KeyCodes.name(key));
                        nexus.configManager().markDirty();
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        if (context.argCount() <= 1) {
                            return moduleNames(nexus);
                        }
                        return java.util.Arrays.asList("NONE", "R.SHIFT", "L.SHIFT", "R", "F", "G", "V", "B", "N");
                    }
                }));

        manager.register(new SimpleCommand("toggle", "Toggles a module", ".toggle MODULE [on|off]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() < 1) {
                            context.error("Usage: .toggle MODULE [on|off]");
                            return;
                        }
                        Module module = nexus.modules().byName(context.arg(0));
                        if (module == null) {
                            context.error("Unknown module: " + context.arg(0));
                            return;
                        }
                        if (context.argCount() > 1) {
                            String state = context.arg(1).toLowerCase();
                            boolean value = state.equals("on") || state.equals("true") || state.equals("enable");
                            module.setEnabled(value);
                        } else {
                            module.toggle();
                        }
                        context.success(module.displayName() + " §7is now "
                                + (module.isEnabled() ? "§aenabled" : "§cdisabled"));
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return moduleNames(nexus);
                    }
                }, "t"));

        manager.register(new SimpleCommand("modules", "Lists modules of a category", ".modules [category]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String filter = context.argCount() > 0 ? context.arg(0) : null;
                        if (filter == null) {
                            StringBuilder sb = new StringBuilder();
                            for (ModuleCategory category : ModuleCategory.values()) {
                                sb.append(category.displayName()).append(':')
                                        .append(nexus.modules().byCategory(category).size()).append("  ");
                            }
                            context.reply("§d" + nexus.modules().size() + " modules §7("
                                    + nexus.modules().enabledCount() + " enabled)");
                            context.info(sb.toString());
                            return;
                        }
                        ModuleCategory category = ModuleCategory.byName(filter);
                        List<Module> modules = nexus.modules().byCategory(category);
                        context.reply("§d" + category.displayName() + " §7(" + modules.size() + ")");
                        for (Module module : modules) {
                            String state = module.isEnabled() ? "§aON " : "§cOFF";
                            context.reply(state + " §f" + module.displayName() + " §7"
                                    + module.keybindSetting().keyName());
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        List<String> names = new ArrayList<String>();
                        for (ModuleCategory category : ModuleCategory.values()) {
                            names.add(category.displayName());
                        }
                        return names;
                    }
                }));

        manager.register(new SimpleCommand("search", "Searches modules", ".search QUERY",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() < 1) {
                            context.error("Usage: .search QUERY");
                            return;
                        }
                        List<Module> results = nexus.modules().search(context.rest(0));
                        if (results.isEmpty()) {
                            context.error("No module found for '" + context.rest(0) + "'");
                            return;
                        }
                        context.reply("§d" + results.size() + " result(s)");
                        for (Module module : results) {
                            context.reply("§7[" + module.category().displayName() + "] §f"
                                    + module.displayName() + " §7- " + module.description());
                        }
                    }
                }));

        // ------------------------------------------------------------- configs
        manager.register(new SimpleCommand("cfg", "Config management", ".cfg list|save|load|delete|rename|export|import|folder|reset|backup",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String action = context.argCount() > 0 ? context.arg(0).toLowerCase() : "list";
                        nexus.core.config.ConfigManager configs = nexus.configManager();
                        if (action.equals("list")) {
                            List<String> profiles = configs.listProfiles();
                            context.reply("§dProfiles §7(" + profiles.size() + ") active: " + configs.activeProfile());
                            for (String profile : profiles) {
                                context.reply("§7- §f" + profile + (profile.equals(configs.activeProfile()) ? " §d(active)" : ""));
                            }
                            return;
                        }
                        if (action.equals("save")) {
                            String name = context.argCount() > 1 ? context.arg(1) : configs.activeProfile();
                            configs.saveProfile(name);
                            nexus.session().onConfigSaved();
                            context.success("Profile saved: " + name);
                            return;
                        }
                        if (action.equals("load")) {
                            if (context.argCount() < 2) {
                                context.error("Usage: .cfg load NAME");
                                return;
                            }
                            if (configs.loadProfile(context.arg(1), true)) {
                                context.success("Profile loaded: " + context.arg(1));
                            } else {
                                context.error("Profile not found: " + context.arg(1));
                            }
                            return;
                        }
                        if (action.equals("delete")) {
                            if (context.argCount() < 2) {
                                context.error("Usage: .cfg delete NAME");
                                return;
                            }
                            if (configs.deleteProfile(context.arg(1))) {
                                context.success("Profile deleted: " + context.arg(1));
                            } else {
                                context.error("Cannot delete: " + context.arg(1));
                            }
                            return;
                        }
                        if (action.equals("rename")) {
                            if (context.argCount() < 3) {
                                context.error("Usage: .cfg rename OLD NEW");
                                return;
                            }
                            if (configs.renameProfile(context.arg(1), context.arg(2))) {
                                context.success("Renamed " + context.arg(1) + " -> " + context.arg(2));
                            } else {
                                context.error("Rename failed");
                            }
                            return;
                        }
                        if (action.equals("export")) {
                            if (context.argCount() < 2) {
                                context.error("Usage: .cfg export NAME [file]");
                                return;
                            }
                            File target = context.argCount() > 2
                                    ? new File(context.rest(2))
                                    : new File(configs.folder(), context.arg(1) + "-export.json");
                            if (configs.exportProfile(context.arg(1), target)) {
                                context.success("Exported to " + target.getAbsolutePath());
                            } else {
                                context.error("Export failed");
                            }
                            return;
                        }
                        if (action.equals("import")) {
                            if (context.argCount() < 2) {
                                context.error("Usage: .cfg import FILE [name]");
                                return;
                            }
                            File source = new File(context.arg(1));
                            if (configs.importProfile(source, context.argCount() > 2 ? context.arg(2) : null)) {
                                context.success("Imported " + source.getName());
                            } else {
                                context.error("Import failed");
                            }
                            return;
                        }
                        if (action.equals("folder")) {
                            context.reply("§dConfig folder: §f" + configs.folder().getAbsolutePath());
                            return;
                        }
                        if (action.equals("reset")) {
                            String name = context.argCount() > 1 ? context.arg(1) : configs.activeProfile();
                            configs.resetProfile(name);
                            context.success("Profile reset: " + name);
                            return;
                        }
                        if (action.equals("backup")) {
                            String name = context.argCount() > 1 ? context.arg(1) : configs.activeProfile();
                            File backup = configs.createBackup(name);
                            if (backup != null) {
                                context.success("Backup created: " + backup.getName());
                            } else {
                                context.error("Backup failed (profile does not exist yet)");
                            }
                            return;
                        }
                        context.error("Unknown cfg action: " + action);
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        if (context.argCount() <= 1) {
                            return java.util.Arrays.asList("list", "save", "load", "delete", "rename",
                                    "export", "import", "folder", "reset", "backup");
                        }
                        String action = context.arg(0).toLowerCase();
                        if (action.equals("load") || action.equals("delete") || action.equals("save")
                                || action.equals("backup") || action.equals("reset") || action.equals("export")) {
                            return nexus.configManager().listProfiles();
                        }
                        if (action.equals("rename")) {
                            return nexus.configManager().listProfiles();
                        }
                        return new ArrayList<String>();
                    }
                }, "config"));

        manager.register(new SimpleCommand("profile", "Shows or switches the active profile", ".profile [name|list]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() == 0 || context.arg(0).equalsIgnoreCase("list")) {
                            context.reply("§dActive profile: §f" + nexus.configManager().activeProfile());
                            context.info(String.join(", ", nexus.configManager().listProfiles()));
                            return;
                        }
                        if (nexus.configManager().loadProfile(context.arg(0), true)) {
                            context.success("Profile: " + context.arg(0));
                        } else {
                            context.error("Profile not found: " + context.arg(0));
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return nexus.configManager().listProfiles();
                    }
                }));

        manager.register(new SimpleCommand("export", "Exports the active profile", ".export [name]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String name = context.argCount() > 0 ? context.arg(0) : nexus.configManager().activeProfile();
                        File target = new File(nexus.configManager().folder(), name + "-export.json");
                        if (nexus.configManager().exportProfile(name, target)) {
                            context.success("Exported " + target.getAbsolutePath());
                        } else {
                            context.error("Export failed");
                        }
                    }
                }));

        manager.register(new SimpleCommand("import", "Imports a profile file", ".import FILE [name]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() < 1) {
                            context.error("Usage: .import FILE [name]");
                            return;
                        }
                        if (nexus.configManager().importProfile(new File(context.arg(0)),
                                context.argCount() > 1 ? context.arg(1) : null)) {
                            context.success("Imported profile");
                        } else {
                            context.error("Import failed");
                        }
                    }
                }));

        manager.register(new SimpleCommand("backup", "Creates a config backup", ".backup [name]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String name = context.argCount() > 0 ? context.arg(0) : nexus.configManager().activeProfile();
                        File backup = nexus.configManager().createBackup(name);
                        if (backup != null) {
                            context.success("Backup: " + backup.getName());
                        } else {
                            context.error("Backup failed");
                        }
                    }
                }));

        manager.register(new SimpleCommand("reload", "Reloads the configuration from disk", ".reload",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        nexus.configManager().loadProfile(nexus.configManager().activeProfile(), true);
                        context.success("Configuration reloaded");
                    }
                }));

        manager.register(new SimpleCommand("reset", "Resets modules, HUD or everything", ".reset modules|hud|all|SETTING",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String target = context.argCount() > 0 ? context.arg(0).toLowerCase() : "all";
                        if (target.equals("modules")) {
                            int count = nexus.modules().disableAll();
                            for (Module module : nexus.modules().all()) {
                                for (Setting<?> setting : module.settings()) {
                                    setting.reset();
                                }
                            }
                            context.success(count + " modules disabled and settings reset");
                        } else if (target.equals("hud")) {
                            nexus.hud().resetLayout();
                            context.success("HUD layout reset");
                        } else if (target.equals("all")) {
                            nexus.modules().disableAll();
                            for (Module module : nexus.modules().all()) {
                                for (Setting<?> setting : module.settings()) {
                                    setting.reset();
                                }
                            }
                            nexus.hud().resetLayout();
                            nexus.themes().setActive("OMEGA PURPLE");
                            context.success("Everything reset to defaults");
                        } else {
                            context.error("Usage: .reset modules|hud|all");
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("modules", "hud", "all");
                    }
                }));

        // ------------------------------------------------------------- social
        manager.register(new SimpleCommand("friend", "Manages the friend list", ".friend add|remove|list|clear [NAME]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String action = context.argCount() > 0 ? context.arg(0).toLowerCase() : "list";
                        if (action.equals("list")) {
                            context.reply("§dFriends §7(" + nexus.friends().friends().size() + "): §f"
                                    + String.join(", ", nexus.friends().friends()));
                            return;
                        }
                        if (context.argCount() < 2) {
                            context.error("Usage: .friend " + action + " NAME");
                            return;
                        }
                        if (action.equals("add")) {
                            nexus.friends().addFriend(context.arg(1));
                        } else if (action.equals("remove")) {
                            if (!nexus.friends().removeFriend(context.arg(1))) {
                                context.error("Not in the friend list: " + context.arg(1));
                                return;
                            }
                        } else if (action.equals("clear")) {
                            nexus.friends().clear();
                        } else {
                            context.error("Unknown action: " + action);
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("add", "remove", "list", "clear");
                    }
                }));

        manager.register(new SimpleCommand("enemy", "Manages the enemy list", ".enemy add|remove|list|clear [NAME]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String action = context.argCount() > 0 ? context.arg(0).toLowerCase() : "list";
                        if (action.equals("list")) {
                            context.reply("§cEnemies §7(" + nexus.friends().enemies().size() + "): §f"
                                    + String.join(", ", nexus.friends().enemies()));
                            return;
                        }
                        if (context.argCount() < 2) {
                            context.error("Usage: .enemy " + action + " NAME");
                            return;
                        }
                        if (action.equals("add")) {
                            nexus.friends().addEnemy(context.arg(1));
                        } else if (action.equals("remove")) {
                            if (!nexus.friends().removeEnemy(context.arg(1))) {
                                context.error("Not in the enemy list: " + context.arg(1));
                                return;
                            }
                        } else if (action.equals("clear")) {
                            nexus.friends().clear();
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("add", "remove", "list", "clear");
                    }
                }));

        // -------------------------------------------------------------- visual
        manager.register(new SimpleCommand("theme", "Theme engine control", ".theme [name|list|next|prev]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String argument = context.argCount() > 0 ? context.rest(0) : "list";
                        if (argument.equalsIgnoreCase("list")) {
                            context.reply("§dThemes: §f" + String.join(", ", nexus.themes().names()));
                            context.info("Active: " + nexus.themes().activeName());
                            return;
                        }
                        if (argument.equalsIgnoreCase("next")) {
                            nexus.themes().cycle(1);
                            return;
                        }
                        if (argument.equalsIgnoreCase("prev")) {
                            nexus.themes().cycle(-1);
                            return;
                        }
                        if (nexus.themes().setActive(argument)) {
                            context.success("Theme: " + nexus.themes().activeName());
                        } else {
                            context.error("Unknown theme: " + argument);
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        List<String> names = nexus.themes().names();
                        names.add("list");
                        names.add("next");
                        names.add("prev");
                        return names;
                    }
                }));

        manager.register(new SimpleCommand("font", "Font selection", ".font [name|list]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() == 0 || context.arg(0).equalsIgnoreCase("list")) {
                            context.reply("§dFonts: §f" + String.join(", ", nexus.themes().availableFonts()));
                            context.info("Active: " + nexus.themes().font());
                            return;
                        }
                        if (nexus.themes().setFont(context.rest(0))) {
                            context.success("Font: " + nexus.themes().font());
                        } else {
                            context.error("Unknown font: " + context.rest(0));
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList(nexus.themes().availableFonts());
                    }
                }));

        manager.register(new SimpleCommand("color", "Changes a theme color", ".color TARGET HEX",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() < 2) {
                            context.error("Usage: .color primary|accent|panel|text #RRGGBB");
                            return;
                        }
                        if (nexus.themes().setColor(context.arg(0), context.arg(1))) {
                            context.success("Color updated: " + context.arg(0) + " = " + context.arg(1));
                        } else {
                            context.error("Unknown color target or invalid hex");
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("primary", "secondary", "accent", "highlight",
                                "background", "panel", "panelAlt", "outline", "text", "textDim");
                    }
                }));

        manager.register(new SimpleCommand("watermark", "Sets the watermark text", ".watermark TEXT",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() == 0) {
                            context.reply("§dWatermark: §f" + NexusInfo.shortWatermark());
                            return;
                        }
                        nexus.core.hud.HudElement watermark = nexus.hud().byId("watermark");
                        if (watermark == null) {
                            context.error("Watermark element not found");
                            return;
                        }
                        String text = context.rest(0);
                        watermark.setPosition(watermark.x(), watermark.y());
                        nexus.notifications().success("Watermark", text);
                        context.success("Watermark text saved for this session: " + text);
                    }
                }));

        manager.register(new SimpleCommand("hud", "HUD management", ".hud toggle|reset|editor|list|element NAME on|off",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String action = context.argCount() > 0 ? context.arg(0).toLowerCase() : "list";
                        if (action.equals("toggle")) {
                            nexus.hud().toggleVisible();
                            context.success("HUD is now " + (nexus.hud().isVisible() ? "visible" : "hidden"));
                            return;
                        }
                        if (action.equals("reset")) {
                            nexus.hud().resetLayout();
                            context.success("HUD layout reset");
                            return;
                        }
                        if (action.equals("editor")) {
                            nexus.gui().openHudEditor();
                            context.success("HUD editor opened");
                            return;
                        }
                        if (action.equals("element") && context.argCount() >= 3) {
                            nexus.core.hud.HudElement element = nexus.hud().byName(context.arg(1));
                            if (element == null) {
                                context.error("Unknown HUD element: " + context.arg(1));
                                return;
                            }
                            element.setEnabled(context.arg(2).equalsIgnoreCase("on"));
                            context.success(element.displayName() + " = " + (element.isEnabled() ? "on" : "off"));
                            return;
                        }
                        context.reply("§dHUD elements §7(" + nexus.hud().enabledCount() + "/" + nexus.hud().size() + ")");
                        for (nexus.core.hud.HudElement element : nexus.hud().all()) {
                            context.reply((element.isEnabled() ? "§aON " : "§cOFF") + " §f" + element.displayName());
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        if (context.argCount() <= 1) {
                            return java.util.Arrays.asList("toggle", "reset", "editor", "list", "element");
                        }
                        List<String> names = new ArrayList<String>();
                        for (nexus.core.hud.HudElement element : nexus.hud().all()) {
                            names.add(element.displayName());
                        }
                        return names;
                    }
                }));

        manager.register(new SimpleCommand("gui", "Opens client interfaces", ".gui [clickgui|hud|configs|settings|close]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String target = context.argCount() > 0 ? context.arg(0).toLowerCase() : "clickgui";
                        if (target.equals("close")) {
                            nexus.gui().closeAll(true);
                            return;
                        }
                        if (target.equals("hud")) {
                            nexus.gui().openHudEditor();
                        } else if (target.equals("configs")) {
                            nexus.gui().openConfigManager();
                        } else if (target.equals("settings")) {
                            nexus.gui().openSettings();
                        } else {
                            nexus.gui().openClickGui();
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("clickgui", "hud", "configs", "settings", "close");
                    }
                }));

        manager.register(new SimpleCommand("panic", "Disables everything and hides the HUD", ".panic",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        int disabled = nexus.panic();
                        context.success("PANIC: " + disabled + " modules disabled, HUD hidden");
                    }
                }));

        // --------------------------------------------------------------- info
        manager.register(new SimpleCommand("client", "Client information", ".client",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        context.reply("§d" + NexusInfo.PRODUCT + " §7v" + NexusInfo.VERSION);
                        context.reply("§7" + NexusInfo.SLOGAN);
                        context.info("Adapter: " + nexus.platform().id() + " | MC " + nexus.platform().minecraftVersion());
                        context.info("Modules: " + nexus.modules().size() + " | Commands: " + nexus.commands().size()
                                + " | HUD: " + nexus.hud().size());
                        context.info("Profile: " + nexus.configManager().activeProfile()
                                + " | Theme: " + nexus.themes().activeName());
                    }
                }, null, "nexus"));

        manager.register(new SimpleCommand("version", "Version information", ".version",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        context.reply("§d" + NexusInfo.NAME + " §7v" + NexusInfo.VERSION + " §8(" + NexusInfo.BUILD + ")");
                        context.info("Minecraft: " + nexus.platform().minecraftVersion()
                                + " (protocol " + nexus.platform().protocol() + ")");
                        context.info("Supported versions: " + NexusInfo.SUPPORTED_VERSIONS);
                        context.info("Java: " + System.getProperty("java.version"));
                    }
                }, null, "ver"));

        manager.register(new SimpleCommand("stats", "Session statistics", ".stats",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        nexus.core.session.SessionTracker session = nexus.session();
                        context.reply("§dSession §7" + TimeUtil.format(session.sessionMs()));
                        context.info("Playtime total: " + TimeUtil.format(session.totalPlaytimeMs()));
                        context.info("Kills: " + session.kills() + " | Deaths: " + session.deaths()
                                + " | K/D: " + String.format("%.2f", session.kdRatio()));
                        context.info("Messages: " + session.messagesSent() + " | Commands: " + session.commandsExecuted());
                        context.info("Blocks broken: " + session.blocksBroken() + " | placed: " + session.blocksPlaced());
                        context.info("Last server: " + session.lastServer());
                    }
                }));

        manager.register(new SimpleCommand("debug", "Diagnostics", ".debug [report]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() > 0 && context.arg(0).equalsIgnoreCase("report")) {
                            File report = nexus.diagnostics().writeReport();
                            context.success("Report written: " + report.getAbsolutePath());
                            return;
                        }
                        for (String line : nexus.diagnostics().report().split("\\n")) {
                            context.info(line);
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("report");
                    }
                }));

        manager.register(new SimpleCommand("settings", "Reads or writes module settings", ".settings MODULE [SETTING VALUE]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        if (context.argCount() < 1) {
                            context.error("Usage: .settings MODULE [SETTING VALUE]");
                            return;
                        }
                        Module module = nexus.modules().byName(context.arg(0));
                        if (module == null) {
                            context.error("Unknown module: " + context.arg(0));
                            return;
                        }
                        if (context.argCount() == 1) {
                            context.reply("§d" + module.displayName() + " §7settings");
                            for (Setting<?> setting : module.settings()) {
                                context.reply("§7- §f" + setting.name() + " §7= " + setting.displayValue()
                                        + " §8(" + setting.typeName() + ")");
                            }
                            return;
                        }
                        Setting<?> setting = module.setting(context.arg(1));
                        if (setting == null) {
                            context.error("Unknown setting: " + context.arg(1));
                            return;
                        }
                        if (context.argCount() == 2) {
                            context.reply(setting.name() + " = " + setting.displayValue());
                            return;
                        }
                        if (setting.parse(context.rest(2))) {
                            context.success(setting.name() + " = " + setting.displayValue());
                            nexus.configManager().markDirty();
                        } else {
                            context.error("Invalid value for " + setting.name() + " (" + setting.typeName() + ")");
                        }
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        if (context.argCount() <= 1) {
                            return moduleNames(nexus);
                        }
                        Module module = nexus.modules().byName(context.arg(0));
                        if (module == null) {
                            return new ArrayList<String>();
                        }
                        if (context.argCount() == 2) {
                            List<String> names = new ArrayList<String>();
                            for (Setting<?> setting : module.settings()) {
                                names.add(setting.name());
                            }
                            return names;
                        }
                        Setting<?> setting = module.setting(context.arg(1));
                        if (setting instanceof nexus.core.setting.BooleanSetting) {
                            return java.util.Arrays.asList("true", "false");
                        }
                        if (setting instanceof nexus.core.setting.ModeSetting) {
                            return java.util.Arrays.asList(((nexus.core.setting.ModeSetting) setting).modes());
                        }
                        return new ArrayList<String>();
                    }
                }, "set"));

        manager.register(new SimpleCommand("waypoint", "Waypoint management", ".waypoint add|remove|list|clear|share [args]",
                new Executor() {
                    @Override
                    public void run(Command.Context context) {
                        String action = context.argCount() > 0 ? context.arg(0).toLowerCase() : "list";
                        nexus.core.world.WaypointManager waypoints = nexus.waypoints();
                        if (action.equals("list")) {
                            context.reply("§dWaypoints §7(" + waypoints.size() + ")");
                            for (nexus.core.world.WaypointManager.Waypoint waypoint : waypoints.all()) {
                                context.reply("§f" + waypoint.name() + " §7" + (int) waypoint.x() + ", "
                                        + (int) waypoint.y() + ", " + (int) waypoint.z()
                                        + " §8[" + waypoint.dimension() + "] "
                                        + ColorUtil.toHexRgb(waypoint.color()));
                            }
                            return;
                        }
                        if (action.equals("add")) {
                            if (context.argCount() >= 4) {
                                waypoints.add(context.arg(1), parseDouble(context.arg(2)), parseDouble(context.arg(3)),
                                        parseDouble(context.arg(4)), nexus.client().world().dimensionId(), 0xFFA855F7);
                            } else if (context.argCount() >= 2) {
                                waypoints.addHere(context.arg(1));
                            } else {
                                context.error("Usage: .waypoint add NAME [x y z]");
                                return;
                            }
                            context.success("Waypoint added");
                            return;
                        }
                        if (action.equals("remove")) {
                            if (context.argCount() < 2) {
                                context.error("Usage: .waypoint remove NAME");
                                return;
                            }
                            if (!waypoints.remove(context.arg(1))) {
                                context.error("Waypoint not found: " + context.arg(1));
                            }
                            return;
                        }
                        if (action.equals("clear")) {
                            waypoints.clear();
                            context.success("Waypoints cleared");
                            return;
                        }
                        if (action.equals("share")) {
                            String data = waypoints.exportShareCodes();
                            context.reply("§dShare codes:");
                            for (String line : data.split("\\n")) {
                                if (!line.isEmpty()) {
                                    context.info(line);
                                }
                            }
                            nexus.client().chat().addSuggestion(data.replace("\\n", " "));
                            return;
                        }
                        context.error("Unknown waypoint action: " + action);
                    }
                },
                new Completer() {
                    @Override
                    public List<String> complete(Command.Context context) {
                        return java.util.Arrays.asList("add", "remove", "list", "clear", "share");
                    }
                }, "wp"));

        Log.info("Default commands prepared");
    }

    private static List<String> moduleNames(Nexus nexus) {
        List<String> names = new ArrayList<String>();
        for (Module module : nexus.modules().all()) {
            names.add(module.name());
        }
        return names;
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
