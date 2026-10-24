### Gradle log: nexus1165  (Sat Oct 24 15:27:07 UTC 2026)
```
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:3: error: package net.minecraft.client.options does not exist
import net.minecraft.client.options.GameOptions;
                                   ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:15: error: cannot find symbol
    private GameOptions options() {
            ^
  symbol:   class GameOptions
  location: class Nexus1165Options
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:25: error: package net.minecraft.container does not exist
    private net.minecraft.container.PlayerContainer inventory() {
                                   ^
3 errors

> Task :compileJava FAILED
```

### tail
```
Welcome to Gradle 8.10.2!
Here are the highlights of this release:
 - Support for Java 23
 - Faster configuration cache
 - Better configuration cache reports
For more details see https://docs.gradle.org/8.10.2/release-notes.html
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Configure project :
Fabric Loom: 1.6.12
Not publishing sources jar as it was not found. Use java.withSourcesJar() to fix.
> Task :compileJava
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:3: error: package net.minecraft.client.options does not exist
import net.minecraft.client.options.GameOptions;
                                   ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:15: error: cannot find symbol
    private GameOptions options() {
            ^
  symbol:   class GameOptions
  location: class Nexus1165Options
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:25: error: package net.minecraft.container does not exist
    private net.minecraft.container.PlayerContainer inventory() {
                                   ^
3 errors
> Task :compileJava FAILED
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run-1792855587682.json
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':compileJava'.
> Compilation failed; see the compiler error output for details.
* Try:
> Run with --info option to get more log output.
> Run with --scan to get full insights.
Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
BUILD FAILED in 51s
1 actionable task: 1 executed
```
