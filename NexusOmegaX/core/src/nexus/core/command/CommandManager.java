package nexus.core.command;

import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Chat command registry, parser and tab completer. */
public final class CommandManager {

    private final Nexus nexus;
    private final Map<String, Command> commands = new LinkedHashMap<String, Command>();
    private String lastError = "";

    public CommandManager(Nexus nexus) {
        this.nexus = nexus;
    }

    public void register(Command command) {
        commands.put(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            commands.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public void registerDefaults() {
        DefaultCommands.register(this);
        Log.info("Registered " + size() + " commands");
    }

    public Command byName(String name) {
        return commands.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    public List<Command> all() {
        List<Command> result = new ArrayList<Command>();
        for (Command command : commands.values()) {
            if (!result.contains(command)) {
                result.add(command);
            }
        }
        Collections.sort(result, new java.util.Comparator<Command>() {
            @Override
            public int compare(Command a, Command b) {
                return a.name().compareToIgnoreCase(b.name());
            }
        });
        return result;
    }

    /** Number of distinct commands (aliases excluded). */
    public int size() {
        return all().size();
    }

    public Nexus nexus() {
        return nexus;
    }

    public void reply(String message) {
        nexus.client().chat().print(NexusInfo.NAME, message.replace('§', '§'), 0xFFC084FC);
    }

    /**
     * Executes a raw command line (without the prefix).
     *
     * @return true when a command handled the input
     */
    public boolean execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        String[] parts = input.trim().split("\\s+");
        String name = parts[0];
        Command command = byName(name);
        if (command == null) {
            lastError = "Unknown command: " + name;
            reply("§c" + lastError + " §7(use .help)");
            return false;
        }
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        Command.Context context = new Command.Context(this, input, args);
        try {
            command.execute(context);
            nexus.session().onCommandExecuted();
            return true;
        } catch (Throwable throwable) {
            Log.error("Command failed: " + input, throwable);
            reply("§cCommand error: " + throwable);
            reply("§7Usage: " + command.usage());
            return false;
        }
    }

    /**
     * Tab completion for a raw (possibly partial) command line.
     */
    public List<String> complete(String input) {
        List<String> result = new ArrayList<String>();
        if (input == null) {
            return result;
        }
        String text = input.trim();
        String[] parts = text.split("\\s+");
        if (parts.length <= 1) {
            String prefix = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
            for (Command command : all()) {
                if (command.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(command.name());
                }
            }
            return result;
        }
        Command command = byName(parts[0]);
        if (command == null) {
            return result;
        }
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        List<String> suggestions = command.complete(new Command.Context(this, text, args));
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(current)) {
                result.add(suggestion);
            }
        }
        return result;
    }

    public String lastError() {
        return lastError;
    }
}
