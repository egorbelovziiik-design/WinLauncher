import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Test-only JVM interop probe, not a simulated Minecraft client. */
public final class ArgfileProbe {
    public static void main(String[] args) throws Exception {
        if (!"value with spaces".equals(System.getProperty("winlauncher.probe"))) {
            throw new IllegalStateException("JVM option was not preserved");
        }
        String name = "\u0444\u0430\u0439\u043b.txt";
        String expected = "\u041f\u0440\u0438\u0432\u0435\u0442";
        String content = new String(Files.readAllBytes(Paths.get(name)), StandardCharsets.UTF_8);
        if (!expected.equals(content)) throw new IllegalStateException("Unicode file content mismatch");
        System.out.println("ARGFILE_UNICODE_FILES_OK");
    }
}
