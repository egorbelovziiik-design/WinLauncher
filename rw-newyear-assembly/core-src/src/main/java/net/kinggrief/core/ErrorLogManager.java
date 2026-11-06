package net.kinggrief.core;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerCommandException;
import com.destroystokyo.paper.exception.ServerEventException;
import com.destroystokyo.paper.exception.ServerException;
import com.destroystokyo.paper.exception.ServerPluginException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerEvent;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A bounded, in-memory, deliberately non-verbatim error view. It never saves or
 * sends log messages, exception messages, command text/arguments, file paths,
 * network addresses or ThreadContext. Paper's exception event supplies player
 * context; a name appearing in unstructured log text is never evidence.
 */
final class ErrorLogManager implements Listener, CommandExecutor, TabCompleter {
    private static final int CAPACITY = 500;
    private static final int PAGE_SIZE = 5;
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern SOURCE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_.$-]{0,47}");
    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern IPV4 = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern METHOD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,63}|<init>|<clinit>");
    private static final Pattern SENSITIVE_IDENTIFIER = Pattern.compile("(?i).*(?:password|passwd|secret|cookie|credential|bearer|apikey|api_key|sessionid|access_token).*" );
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneOffset.UTC);

    private final KingGriefCore plugin;
    private final ArrayDeque<Entry> entries = new ArrayDeque<Entry>();
    private LoggerContext context;
    private Configuration attachedConfiguration;
    private AbstractAppender appender;
    private boolean captureAvailable;
    private volatile boolean accepting;

    private static final class Entry {
        final long time;
        final String severity;
        final String exception;
        final String frames;
        final WeakReference<Throwable> original;
        final long threadId;
        volatile String source;
        volatile String kind;
        volatile String player;

        Entry(long time, String severity, String source, String kind, Throwable thrown) {
            this.time = time;
            this.severity = severity;
            this.source = source;
            this.kind = kind;
            this.exception = exceptionName(thrown);
            this.frames = stackFrames(thrown);
            this.original = new WeakReference<Throwable>(thrown);
            this.threadId = Thread.currentThread().getId();
        }
    }

    ErrorLogManager(KingGriefCore plugin) { this.plugin = plugin; }

    void start() {
        accepting = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            context = (LoggerContext) LogManager.getContext(false);
            attachedConfiguration = context.getConfiguration();
            appender = new AbstractAppender("KingGriefErrorView-" + System.identityHashCode(this), null, null, true, Property.EMPTY_ARRAY) {
                @Override public void append(LogEvent event) {
                    // Never log from this callback: that could recurse. Never format
                    // the message, retain event objects, or access Bukkit off-thread.
                    try {
                        if (event == null || event.getLevel() == null || !event.getLevel().isMoreSpecificThan(Level.ERROR)) return;
                        String source = safeSource(event.getLoggerName());
                        Entry entry = new Entry(event.getTimeMillis(), event.getLevel() == Level.FATAL ? "FATAL" : "ERROR",
                                source, "сервер", event.getThrown());
                        add(entry);
                    } catch (Throwable ignored) { /* keep the server logger working */ }
                }
            };
            appender.start();
            attachedConfiguration.addAppender(appender);
            attachedConfiguration.getRootLogger().addAppender(appender, Level.ERROR, null);
            context.updateLoggers();
            captureAvailable = true;
        } catch (Throwable error) {
            // Still retain Paper exception events even if a custom Log4j setup is incompatible.
            captureAvailable = false;
            plugin.getLogger().warning("[ErrorLog] Log4j capture unavailable; only Paper exception events can be shown.");
        }
    }

    void shutdown() {
        accepting = false;
        captureAvailable = false;
        try {
            if (attachedConfiguration != null && appender != null) {
                attachedConfiguration.getRootLogger().removeAppender(appender.getName());
                // The configuration also keeps its own map; release the plugin
                // class loader as well as detaching the root logger.
                try { attachedConfiguration.getAppenders().remove(appender.getName(), appender); }
                catch (UnsupportedOperationException ignored) { }
                if (context != null) context.updateLoggers();
                appender.stop();
            }
        } catch (Throwable ignored) { }
        synchronized (entries) { entries.clear(); }
        appender = null;
        attachedConfiguration = null;
        context = null;
    }

    /** For command-list visibility only; executing/tab-completing also checks authentication and freeze. */
    boolean hasRoleAccess(Player p) {
        if (p == null) return false;
        String id = plugin.getRanks().getRank(p).id;
        return plugin.getRanks().isOwnerLevel(id) || "techsupport".equals(id)
                || "curator_support".equals(id) || "deputy_curator_support".equals(id)
                || p.hasPermission("kinggrief.errorlog");
    }

    private boolean canUse(CommandSender sender) {
        if (!(sender instanceof Player)) return CommandSenderPolicy.isConsole(sender);
        Player p = (Player) sender;
        return hasRoleAccess(p) && plugin.getStore().isAuthenticated(p)
                && !plugin.getStore().isQuickFrozen(p) && !plugin.getModeration().isFrozenOrAscending(p);
    }

    private void add(Entry entry) {
        synchronized (entries) {
            if (!accepting) return;
            while (entries.size() >= CAPACITY) entries.removeFirst();
            entries.addLast(entry);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerException(ServerExceptionEvent event) {
        if (!accepting) return;
        try {
            ServerException wrapped = event.getException();
            // Scheduler exceptions may be logged at WARN. The command/event
            // variants below are the recoverable failures Paper logs as SEVERE.
            if (!(wrapped instanceof ServerEventException) && !(wrapped instanceof ServerCommandException)) return;
            Throwable cause = wrapped.getCause();
            String player = null;
            String kind = "исключение Paper";
            String source = "сервер";
            if (wrapped instanceof ServerPluginException) {
                source = safeSource(((ServerPluginException) wrapped).getResponsiblePlugin().getName());
            }
            if (wrapped instanceof ServerEventException) {
                Event original = ((ServerEventException) wrapped).getEvent();
                player = playerFor(original);
                kind = safeEventName(original);
            } else if (wrapped instanceof ServerCommandException) {
                CommandSender sender = ((ServerCommandException) wrapped).getCommandSender();
                if (sender instanceof Player) player = safeNick(((Player) sender).getName());
                // Command names and arguments can contain authentication data; never read them.
                kind = "команда";
            }
            final String foundPlayer = player;
            final String foundSource = source;
            final String foundKind = kind;
            synchronized (entries) {
                if (!accepting) return;
                // Paper emits a SEVERE first and reports the exact same Throwable
                // afterwards. Enrich that entry only on object identity, not text.
                Iterator<Entry> it = entries.descendingIterator();
                while (it.hasNext()) {
                    Entry previous = it.next();
                    if (cause != null && previous.original.get() == cause
                            && previous.threadId == Thread.currentThread().getId()
                            && Math.abs(System.currentTimeMillis() - previous.time) < 2000L) {
                        previous.player = foundPlayer;
                        previous.source = foundSource;
                        previous.kind = foundKind;
                        return;
                    }
                }
                while (entries.size() >= CAPACITY) entries.removeFirst();
                Entry entry = new Entry(System.currentTimeMillis(), "ERROR", foundSource, foundKind, cause);
                entry.player = foundPlayer;
                entries.addLast(entry);
            }
        } catch (Throwable ignored) { /* do not interfere with Paper's event handling */ }
    }

    private static String playerFor(Event event) {
        Player p = null;
        if (event instanceof PlayerEvent) p = ((PlayerEvent) event).getPlayer();
        else if (event instanceof BlockBreakEvent) p = ((BlockBreakEvent) event).getPlayer();
        else if (event instanceof BlockPlaceEvent) p = ((BlockPlaceEvent) event).getPlayer();
        else if (event instanceof InventoryInteractEvent && ((InventoryInteractEvent) event).getWhoClicked() instanceof Player)
            p = (Player) ((InventoryInteractEvent) event).getWhoClicked();
        else if (event instanceof InventoryOpenEvent && ((InventoryOpenEvent) event).getPlayer() instanceof Player)
            p = (Player) ((InventoryOpenEvent) event).getPlayer();
        else if (event instanceof InventoryCloseEvent && ((InventoryCloseEvent) event).getPlayer() instanceof Player)
            p = (Player) ((InventoryCloseEvent) event).getPlayer();
        else if (event instanceof EntityEvent && ((EntityEvent) event).getEntity() instanceof Player)
            p = (Player) ((EntityEvent) event).getEntity();
        return p == null ? null : safeNick(p.getName());
    }

    private static String safeNick(String name) {
        return name != null && NICK.matcher(name).matches() ? name : null;
    }
    private static String safeEventName(Event event) {
        if (event == null) return "событие";
        String name = event.getClass().getSimpleName();
        return SOURCE.matcher(name).matches() && !SENSITIVE_IDENTIFIER.matcher(name).matches() ? name : "событие";
    }
    private static String safeSource(String name) {
        return name != null && SOURCE.matcher(name).matches() && !SENSITIVE_IDENTIFIER.matcher(name).matches() && !IPV4.matcher(name).find()
                ? name : "сервер";
    }
    private static Throwable root(Throwable thrown) {
        Throwable current = thrown;
        for (int i = 0; current != null && i < 8; i++) {
            Throwable next = current.getCause();
            if (next == null || next == current) break;
            current = next;
        }
        return current;
    }
    private static boolean javaName(String name) {
        return name != null && name.length() <= 150 && JAVA_NAME.matcher(name).matches()
                && !SENSITIVE_IDENTIFIER.matcher(name).matches();
    }
    private static String exceptionName(Throwable error) {
        try {
            Throwable r = root(error);
            String name = r == null ? null : r.getClass().getName();
            return javaName(name) ? name : (r == null ? "без объекта исключения" : "исключение");
        } catch (Throwable ignored) { return "исключение"; }
    }
    private static String stackFrames(Throwable error) {
        try {
            Throwable r = root(error);
            if (r == null) return "";
            StringBuilder out = new StringBuilder();
            for (StackTraceElement frame : r.getStackTrace()) {
                String cls = frame.getClassName(), method = frame.getMethodName();
                if (!javaName(cls) || method == null || !METHOD.matcher(method).matches()
                        || SENSITIVE_IDENTIFIER.matcher(method).matches()) continue;
                if (out.length() > 0) out.append(" ← ");
                out.append(cls).append('.').append(method);
                if (frame.getLineNumber() >= 0) out.append(':').append(frame.getLineNumber());
                if (out.length() > 180) break;
                // At most two frames; no source file names or free-text messages.
                if (out.indexOf(" ← ") >= 0) break;
            }
            return out.length() > 260 ? out.substring(0, 260) : out.toString();
        } catch (Throwable ignored) { return ""; }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(ColorUtil.c("&c/log доступен авторизованной техподдержке, её руководителям и владельцам."));
            return true;
        }
        String filter = null;
        int page = 1;
        if (args.length > 2) return usage(sender);
        if (args.length > 0) {
            if ("-p".equalsIgnoreCase(args[0]) || "--page".equalsIgnoreCase(args[0])) {
                if (args.length != 2) return usage(sender);
                page = parsePage(args[1]);
            } else {
                filter = safeNick(args[0]);
                if (filter == null) return usage(sender);
                if (args.length == 2) page = parsePage(args[1]);
            }
        }
        if (page < 1) return usage(sender);
        List<Entry> found = new ArrayList<Entry>();
        synchronized (entries) {
            Iterator<Entry> it = entries.descendingIterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (filter == null || (e.player != null && e.player.equalsIgnoreCase(filter))) found.add(e);
            }
        }
        int pages = Math.max(1, (found.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            sender.sendMessage(ColorUtil.c("&eНет такой страницы. Доступно: &f1–" + pages));
            return true;
        }
        sender.sendMessage(ColorUtil.c("&#75DDF7&l⚙ ЖУРНАЛ ОШИБОК &8│ &f" + (filter == null ? "СЕРВЕР" : filter)
                + " &8│ &7" + page + "/" + pages + " &8│ &7UTC"));
        if (!captureAvailable) sender.sendMessage(ColorUtil.c("&eОбщий захват недоступен; показаны только исключения Paper."));
        if (found.isEmpty()) sender.sendMessage(ColorUtil.c("&7Таких ошибок с запуска ядра нет."));
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(page * PAGE_SIZE, found.size()); i++) {
            Entry e = found.get(i);
            sender.sendMessage(ColorUtil.c("&8[" + TIME.format(Instant.ofEpochMilli(e.time)) + "] &c" + e.severity
                    + " &8• &f" + e.source + " &8• &7" + e.kind
                    + (e.player == null ? "" : " &8• &b" + e.player)));
            sender.sendMessage(ColorUtil.c(" &7" + e.exception + (e.frames.isEmpty() ? "" : " &8@ &7" + e.frames)));
        }
        sender.sendMessage(ColorUtil.c("&8Текст, аргументы и личные данные скрыты. /log -p <стр> | /log <ник> [стр]"));
        return true;
    }

    private int parsePage(String value) {
        try { int n = Integer.parseInt(value); return n > 0 && n <= CAPACITY ? n : -1; }
        catch (NumberFormatException error) { return -1; }
    }
    private boolean usage(CommandSender sender) {
        sender.sendMessage(ColorUtil.c("&eИспользование: &f/log &7| &f/log -p <страница> &7| &f/log <ник> [страница]"));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canUse(sender)) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("-p".startsWith(prefix)) out.add("-p");
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        } else if (args.length == 2 && "1".startsWith(args[1])) out.add("1");
        return out;
    }
}
