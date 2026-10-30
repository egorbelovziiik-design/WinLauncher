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

        if (args.Length >= 4 && string.Equals(args[0], "--e2e-launch", StringComparison.OrdinalIgnoreCase))
        {
            string mcVersion = args[1];
            string dataRoot = args[2];
            string outReport = args[3];
            return RunRealMinecraftE2ELaunchAsync(mcVersion, dataRoot, outReport).GetAwaiter().GetResult();
        }

        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
        return 0;
    }

    public static AppBuilder BuildAvaloniaApp() =>
        AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace();

    private static async Task<int> RunRealMinecraftE2ELaunchAsync(string mcVersion, string dataRoot, string outReportPath)
    {
        var controller = new LauncherStateController(dataRoot);
        string? j8Home = Environment.GetEnvironmentVariable("JAVA_HOME_8_X64");
        string? j21Home = Environment.GetEnvironmentVariable("JAVA_HOME_21_X64");
        if (!string.IsNullOrWhiteSpace(j8Home))
            controller.Settings.Java8Path = Path.Combine(j8Home, "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");
        if (!string.IsNullOrWhiteSpace(j21Home))
            controller.Settings.Java21Path = Path.Combine(j21Home, "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");

        var account = controller.AuthService.CreateLocalSandboxAccount("AxsesCiRunner");
        var spec = controller.Versions.First(v => v.MinecraftVersion == mcVersion);

        string instDir = controller.LaunchEngine.GetInstanceDir(mcVersion);
        Directory.CreateDirectory(instDir);
        await File.WriteAllTextAsync(
            Path.Combine(instDir, "options.txt"),
            "onboardAccessibility:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n");

        var profile = new LaunchProfile
        {
            Name = $"E2E {mcVersion}",
            MinecraftVersion = mcVersion,
            RamMb = 2048,
            ResolutionWidth = 1280,
            ResolutionHeight = 720,
            CustomJvmArgs = "-XX:+UseG1GC -Daxsesclient.selftest=true"
        };

        using var cts = new CancellationTokenSource(TimeSpan.FromMinutes(4));
        var proc = await controller.LaunchEngine.LaunchMinecraftAsync(
            spec,
            profile,
            account,
            controller.Settings,
            controller.Modules,
            stage => Console.WriteLine($"[Stage {stage.StageNumber}/{stage.TotalStages}] {stage.StageName}"),
            log => Console.WriteLine(log),
            null,
            cts.Token);

        await proc.WaitForExitAsync(cts.Token);
        string generatedReport = Path.Combine(instDir, "axsesclient", $"selftest-{mcVersion}.json");
        if (!File.Exists(generatedReport))
        {
            Console.Error.WriteLine($"Expected self-test report missing: {generatedReport} (exit code {proc.ExitCode})");
            return 1;
        }

        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(outReportPath))!);
        File.Copy(generatedReport, outReportPath, overwrite: true);
        Console.WriteLine(await File.ReadAllTextAsync(outReportPath, cts.Token));
        return 0;
    }

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

            string encrypted = SecureTokenVault.Encrypt("super_secret_oauth_token_12345", Path.Combine(tempRoot, "vault"));
            string decrypted = SecureTokenVault.Decrypt(encrypted, Path.Combine(tempRoot, "vault"));
            if (decrypted != "super_secret_oauth_token_12345")
                throw new InvalidOperationException("SecureTokenVault roundtrip failed.");

            string scrubbed = SecureTokenVault.ScrubSecrets("Launching with --accessToken super_secret_oauth_token_12345 now");
            if (scrubbed.Contains("super_secret_oauth_token_12345", StringComparison.Ordinal))
                throw new InvalidOperationException("SecureTokenVault failed to redact token from logs.");

            controller.SetActiveTheme("axses-purple");
            controller.SelectVersion("1.21.11");
            controller.SyncModulesAndThemeToSelectedInstance();

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
