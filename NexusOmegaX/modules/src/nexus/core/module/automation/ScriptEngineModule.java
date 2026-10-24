package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Loads and runs simple text scripts from the scripts folder. */
public final class ScriptEngineModule extends Module {

    private final BooleanSetting runOnStartup;
    private final BooleanSetting safeMode;
    private final List<Script> scripts = new ArrayList<Script>();

    /** A script: a list of console style commands executed one per tick. */
    public static final class Script {
        private final String name;
        private final List<String> lines;

        public Script(String name, List<String> lines) {
            this.name = name;
            this.lines = lines;
        }

        public String name() {
            return name;
        }

        public List<String> lines() {
            return lines;
        }
    }

    public ScriptEngineModule() {
        super("script", "Script", "Loads and runs simple text scripts.", ModuleCategory.AUTOMATION);
        this.runOnStartup = bool("Run On Startup", "Run the startup script when the client loads", true);
        this.safeMode = bool("Safe Mode", "Only allow chat and client commands", true);
    }

    @Override
    public void onEnable() {
        reload();
        if (runOnStartup.isEnabled()) {
            run("startup");
        }
    }

    public File folder() {
        return new File(nexus.core.NexusInfo.directory(), "scripts");
    }

    public void reload() {
        scripts.clear();
        File folder = folder();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".txt")) {
                continue;
            }
            String content = nexus.util.FileUtil.read(file);
            if (content == null) {
                continue;
            }
            List<String> lines = new ArrayList<String>();
            for (String line : content.split("\n")) {
                lines.add(line.trim());
            }
            scripts.add(new Script(file.getName().replace(".txt", ""), lines));
        }
    }

    public List<Script> scripts() {
        return scripts;
    }

    public boolean run(String name) {
        for (Script script : scripts) {
            if (!script.name().equalsIgnoreCase(name)) {
                continue;
            }
            queue(script);
            return true;
        }
        return false;
    }

    private void queue(Script script) {
        int delay = 1;
        for (final String line : script.lines()) {
            final String command = line.trim();
            if (command.isEmpty() || command.startsWith("#")) {
                continue;
            }
            if (safeMode.isEnabled() && command.startsWith("/")) {
                continue;
            }
            mc().schedule(new Runnable() {
                @Override
                public void run() {
                    if (isEnabled()) {
                        nexus.core.Nexus.instance().commands().execute(command);
                    }
                }
            }, delay);
            delay += 2;
        }
        markActivity();
    }

    @Override
    public String hudInfo() {
        return scripts.size() + " scripts";
    }
}
