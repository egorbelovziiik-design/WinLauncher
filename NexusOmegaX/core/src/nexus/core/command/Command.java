package nexus.core.command;

import java.util.ArrayList;
import java.util.List;

/**
 * A chat command.
 * <p>
 * Commands are invoked with the client prefix ({@code .}) e.g. {@code .toggle sprint}.
 */
public abstract class Command {

    /** Execution context: parsed arguments plus helpers. */
    public static final class Context {

        private final CommandManager manager;
        private final String[] args;
        private final String raw;

        public Context(CommandManager manager, String raw, String[] args) {
            this.manager = manager;
            this.raw = raw;
            this.args = args;
        }

        public CommandManager manager() {
            return manager;
        }

        public String[] args() {
            return args;
        }

        public String raw() {
            return raw;
        }

        public String arg(int index) {
            return index >= 0 && index < args.length ? args[index] : "";
        }

        public String rest(int fromIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = fromIndex; i < args.length; i++) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            return sb.toString();
        }

        public int argCount() {
            return args.length;
        }

        public void reply(String message) {
            manager.reply(message);
        }

        public void success(String message) {
            manager.reply("§a" + message);
        }

        public void error(String message) {
            manager.reply("§c" + message);
        }

        public void info(String message) {
            manager.reply("§7" + message);
        }
    }

    private final String name;
    private final String description;
    private final String usage;
    private final String[] aliases;

    protected Command(String name, String description, String usage, String... aliases) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.aliases = aliases == null ? new String[0] : aliases;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String usage() {
        return usage;
    }

    public String[] aliases() {
        return aliases;
    }

    /** Runs the command. Implementations must handle wrong arguments themselves. */
    public abstract void execute(Context context);

    /** Tab completion for the current argument index. */
    public List<String> complete(Context context) {
        return new ArrayList<String>();
    }
}
