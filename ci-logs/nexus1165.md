### Gradle log: nexus1165  (Sat Oct 24 17:02:51 UTC 2026)
```
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
> Task :processResources
> Task :classes
> Task :jar
> Task :sourcesJar
> Task :compileTestJava NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :validateAccessWidener NO-SOURCE
> Task :check UP-TO-DATE
> Task :verifyMixins FAILED
verifyMixins: 3 problem(s)
  MIXIN PROBLEM: ScreenMouseMixin.java: Screen.mouseClicked does not exist
  MIXIN PROBLEM: ScreenMouseMixin.java: Screen.mouseReleased does not exist
  MIXIN PROBLEM: ScreenMouseMixin.java: Screen.mouseDragged does not exist
> Task :remapSourcesJar
> Task :remapJar
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run-1792861331414.json
FAILURE: Build failed with an exception.
* Where:
Build file '/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/build.gradle' line: 204
* What went wrong:
Execution failed for task ':verifyMixins'.
> Mixin targets do not match Minecraft: ScreenMouseMixin.java: Screen.mouseClicked does not exist; ScreenMouseMixin.java: Screen.mouseReleased does not exist; ScreenMouseMixin.java: Screen.mouseDragged does not exist
* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
BUILD FAILED in 44s
7 actionable tasks: 7 executed
```
