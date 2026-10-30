using System.Text.Json;
using Avalonia;
using AxsesClient.Launcher.Models;
using AxsesClient.Launcher.Services;

namespace AxsesClient.Launcher;

public static class Program
{
    [STAThread]
    public static int Main(string[] args)
    {
        if (args.Length >= 1 && string.Equals(args[0], "--selftest", StringComparison.OrdinalIgnoreCase))
        {
            string outPath = args.Length >= 2 ? args[1] : "launcher-selftest.json";
            return RunExecutableSelfTestAsync(outPath).GetAwaiter().GetResult();
        }

        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
        return 0;
    }

    public static AppBuilder BuildAvaloniaApp() =>
        AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace();

    private static async Task<int> RunExecutableSelfTestAsync(string reportOutputPath)
    {
        string tempRoot = Path.Combine(Path.GetTempPath(), "axses_launcher_selftest_" + Guid.NewGuid().ToString("N")[..8]);
        try
        {
            var controller = new LauncherStateController(tempRoot);
            if (controller.Modules.Count != 90)
                throw new InvalidOperationException($"Expected 90 modules, found {controller.Modules.Count}");
            if (controller.Themes.Count != 8)
                throw new InvalidOperationException($"Expected 8 themes, found {controller.Themes.Count}");
            if (controller.Versions.Count != 3)
                throw new InvalidOperationException($"Expected 3 versions, found {controller.Versions.Count}");

            // Verify DPAPI / AES-GCM encryption & secret scrubbing
            string encrypted = SecureTokenVault.Encrypt("super_secret_oauth_token_12345", Path.Combine(tempRoot, "vault"));
            string decrypted = SecureTokenVault.Decrypt(encrypted, Path.Combine(tempRoot, "vault"));
            if (decrypted != "super_secret_oauth_token_12345")
                throw new InvalidOperationException("SecureTokenVault roundtrip failed.");

            string scrubbed = SecureTokenVault.ScrubSecrets("Launching with --accessToken super_secret_oauth_token_12345 now");
            if (scrubbed.Contains("super_secret_oauth_token_12345", StringComparison.Ordinal))
                throw new InvalidOperationException("SecureTokenVault failed to redact token from logs.");

            // Verify Theme & Module config sync
            controller.SetActiveTheme("axses-purple");
            controller.SelectVersion("1.21.11");
            controller.SyncModulesAndThemeToSelectedInstance();

            // Run live diagnostics (system, Java, Mojang/Fabric HTTP endpoints, bundled mod JARs)
            await controller.RunDiagnosticsAsync().ConfigureAwait(false);

            var report = new
            {
                status = "PASS",
                executable = Environment.ProcessPath,
                os = System.Runtime.InteropServices.RuntimeInformation.OSDescription,
                modulesCount = controller.Modules.Count,
                themesCount = controller.Themes.Count,
                supportedVersions = controller.Versions.Select(v => v.MinecraftVersion).ToArray(),
                dpapiPrefix = encrypted.Split(':')[0],
                tokenScrubVerified = true,
                diagnosticsChecks = controller.LastDiagnostics
            };

            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(reportOutputPath))!);
            await File.WriteAllTextAsync(
                reportOutputPath,
                JsonSerializer.Serialize(report, new JsonSerializerOptions { WriteIndented = true })).ConfigureAwait(false);
            return 0;
        }
        catch (Exception ex)
        {
            var err = new { status = "FAIL", error = ex.ToString() };
            await File.WriteAllTextAsync(reportOutputPath, JsonSerializer.Serialize(err)).ConfigureAwait(false);
            return 1;
        }
        finally
        {
            try { Directory.Delete(tempRoot, recursive: true); } catch { }
        }
    }
}
