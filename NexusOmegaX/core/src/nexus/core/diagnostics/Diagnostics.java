package nexus.core.diagnostics;

import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.core.module.Module;
import nexus.util.FileUtil;
import nexus.util.Log;
import nexus.util.TimeUtil;

import java.io.File;
import java.util.List;

/** Self test + report generator ({@code .debug}, launcher diagnostics tab). */
public final class Diagnostics {

    private final Nexus nexus;

    public Diagnostics(Nexus nexus) {
        this.nexus = nexus;
    }

    /** Runs the built in health checks and returns the failures found. */
    public List<String> runSelfTest() {
        java.util.List<String> problems = new java.util.ArrayList<String>();

        if (nexus.platform() == null || !nexus.platform().isHealthy()) {
            problems.add("Platform adapter is not healthy");
        }
        if (nexus.directory() == null || !nexus.directory().exists()) {
            problems.add("Config directory is missing");
        }
        if (nexus.modules().size() == 0) {
            problems.add("No modules registered");
        }
        if (nexus.commands().size() == 0) {
            problems.add("No commands registered");
        }
        if (nexus.themes().activeTheme() == null) {
            problems.add("No active theme");
        }
        if (nexus.hud().size() == 0) {
            problems.add("No HUD elements registered");
        }
        for (Module module : nexus.modules().all()) {
            try {
                if (module.settings().isEmpty()) {
                    problems.add("Module without settings: " + module.name());
                }
                if (module.displayName() == null || module.displayName().isEmpty()) {
                    problems.add("Module without display name: " + module.name());
                }
                if (module.description() == null || module.description().isEmpty()) {
                    problems.add("Module without description: " + module.name());
                }
            } catch (Throwable throwable) {
                problems.add("Module " + module.name() + " threw during check: " + throwable);
            }
        }
        if (nexus.performance().usedMemoryMb() > nexus.performance().maxMemoryMb() * 0.9 && nexus.performance().maxMemoryMb() > 0) {
            problems.add("Memory usage above 90%");
        }
        return problems;
    }

    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(NexusInfo.PRODUCT).append(" diagnostic report ===\n");
        sb.append("Client      : ").append(NexusInfo.VERSION).append(" (build ").append(NexusInfo.BUILD).append(")\n");
        sb.append("Adapter     : ").append(nexus.platform().id()).append('\n');
        sb.append("Minecraft   : ").append(nexus.platform().minecraftVersion())
                .append(" (protocol ").append(nexus.platform().protocol()).append(")\n");
        sb.append("Loader      : ").append(nexus.client().loaderName()).append('\n');
        sb.append("Java        : ").append(System.getProperty("java.version")).append(" / ")
                .append(System.getProperty("java.vm.name")).append('\n');
        sb.append("OS          : ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("Directory   : ").append(nexus.directory().getAbsolutePath()).append('\n');
        sb.append("Uptime      : ").append(TimeUtil.format(nexus.session().sessionMs())).append('\n');
        sb.append("Modules     : ").append(nexus.modules().size()).append(" (")
                .append(nexus.modules().enabledCount()).append(" enabled, ")
                .append(nexus.modules().unsupportedCount()).append(" unsupported)\n");
        sb.append("Commands    : ").append(nexus.commands().size()).append('\n');
        sb.append("HUD         : ").append(nexus.hud().size()).append(" elements\n");
        sb.append("Keybinds    : ").append(nexus.keybinds().bound().size()).append(" bound\n");
        sb.append("Event bus   : ").append(nexus.bus().totalHandlers()).append(" handlers\n");
        sb.append("Configs     : ").append(nexus.configManager().info()).append('\n');
        sb.append("Theme       : ").append(nexus.themes().activeName()).append('\n');
        sb.append("Performance : ").append(nexus.performance().summary()).append('\n');
        sb.append("Connection  : ").append(nexus.client().connection().serverAddress()).append(" ping ")
                .append(nexus.client().connection().ping()).append("ms\n");
        sb.append("Waypoints   : ").append(nexus.waypoints().size()).append('\n');
        sb.append("Friends     : ").append(nexus.friends().friends().size()).append(" / enemies ")
                .append(nexus.friends().enemies().size()).append('\n');
        List<String> problems = runSelfTest();
        sb.append("Self test   : ").append(problems.isEmpty() ? "PASSED" : (problems.size() + " issue(s)")).append('\n');
        for (String problem : problems) {
            sb.append("   - ").append(problem).append('\n');
        }
        return sb.toString();
    }

    /** Writes the report to {@code NexusOmegaX/logs/report-<timestamp>.txt}. */
    public File writeReport() {
        File logs = FileUtil.directory(nexus.directory(), "logs");
        File file = new File(logs, "report-" + System.currentTimeMillis() + ".txt");
        FileUtil.write(file, report() + "\n--- log tail ---\n" + Log.tail(200));
        return file;
    }

    /** Quick one line status used by the HUD debug element. */
    public String shortStatus() {
        return NexusInfo.VERSION + " | " + nexus.platform().minecraftVersion() + " | "
                + nexus.modules().enabledCount() + " modules | " + (int) nexus.performance().fps() + " fps";
    }
}
