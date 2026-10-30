using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Avalonia.Headless;
using Avalonia.Headless.XUnit;
using Avalonia.Threading;
using AxsesClient.Launcher.Models;
using AxsesClient.Launcher.Services;
using AxsesClient.Launcher.UI;
using AxsesClient.Updater;
using Xunit;

namespace AxsesClient.Tests;

public sealed class LauncherAndUpdaterTests
{
    [Fact]
    public void TokenVault_EncryptsDecryptsAndScrubsSensitiveTokens()
    {
        string temp = CreateTempDir();
        try
        {
            string rawToken = "secret_mc_access_token_987654321";
            string cipher = SecureTokenVault.Encrypt(rawToken, temp);
            Assert.NotEqual(rawToken, cipher);

            string plain = SecureTokenVault.Decrypt(cipher, temp);
            Assert.Equal(rawToken, plain);

            string logLine = $"Spawning java with --accessToken {rawToken} and eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc1234567890";
            string scrubbed = SecureTokenVault.ScrubSecrets(logLine);
            Assert.DoesNotContain(rawToken, scrubbed);
            Assert.DoesNotContain("eyJhbGciOiJIUzI1NiJ9", scrubbed);
        }
        finally
        {
            Directory.Delete(temp, recursive: true);
        }
    }

    [Fact]
    public void MicrosoftAuth_BuildsBrowserPkceUrlAndLocalSandboxWithoutPasswordPrompt()
    {
        string temp = CreateTempDir();
        try
        {
            var auth = new MicrosoftAuthService(temp);
            var (verifier, challenge) = MicrosoftAuthService.GeneratePkcePair();
            Assert.True(verifier.Length >= 40);
            Assert.NotEqual(verifier, challenge);

            string url = auth.BuildBrowserAuthorizeUrl("http://127.0.0.1:51234/callback/", "state123", challenge);
            Assert.StartsWith("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?", url);
            Assert.Contains("code_challenge_method=S256", url);
            Assert.Contains("XboxLive.signin", url);

            var sandbox = auth.CreateLocalSandboxAccount("TestPilot");
            Assert.Equal("TestPilot", sandbox.Username);
            Assert.Equal("LocalSandbox", sandbox.AccountType);
            Assert.False(sandbox.OwnsMinecraftJava);
        }
        finally
        {
            Directory.Delete(temp, recursive: true);
        }
    }

    [Theory]
    [InlineData("openjdk version \"1.8.0_422\"\nOpenJDK Runtime Environment", 8)]
    [InlineData("openjdk version \"21.0.5\" 2024-10-15 LTS", 21)]
    [InlineData("java version \"17.0.11\" 2024-04-16 LTS", 17)]
    public void JavaLocator_ParsesLegacyAndModernVersionStrings(string stderrSample, int expectedMajor)
    {
        int actual = JavaRuntimeLocator.ParseJavaMajorVersion(stderrSample, out string raw);
        Assert.Equal(expectedMajor, actual);
        Assert.False(string.IsNullOrWhiteSpace(raw));
    }

    [Fact]
    public void LaunchEngine_ResolvesLibrariesWithFabricPrecedenceAndBuildsKnotCommandLine()
    {
        string temp = CreateTempDir();
        try
        {
            const string vanillaJson = """
            {
              "id": "1.16.5",
              "assetIndex": { "id": "1.16", "sha1": "abc", "url": "https://example.com/1.16.json" },
              "libraries": [
                {
                  "name": "org.ow2.asm:asm:7.1",
                  "downloads": {
                    "artifact": { "path": "org/ow2/asm/asm/7.1/asm-7.1.jar", "url": "https://libraries.minecraft.net/org/ow2/asm/asm/7.1/asm-7.1.jar", "sha1": "111" }
                  }
                },
                {
                  "name": "com.mojang:brigadier:1.0.17",
                  "downloads": {
                    "artifact": { "path": "com/mojang/brigadier/1.0.17/brigadier-1.0.17.jar", "url": "https://libraries.minecraft.net/com/mojang/brigadier/1.0.17/brigadier-1.0.17.jar", "sha1": "222" }
                  }
                }
              ]
            }
            """;

            const string fabricJson = """
            {
              "id": "fabric-loader-0.20.1-1.16.5",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": [
                { "name": "org.ow2.asm:asm:9.7.1", "url": "https://maven.fabricmc.net/" },
                { "name": "net.fabricmc:fabric-loader:0.20.1", "url": "https://maven.fabricmc.net/" }
              ]
            }
            """;

            var auth = new MicrosoftAuthService(temp);
            var java = new JavaRuntimeLocator(temp);
            var engine = new MinecraftLaunchEngine(temp, java, auth);

            string verDir = engine.GetVersionMetaDir("1.16.5");
            Directory.CreateDirectory(verDir);
            File.WriteAllText(Path.Combine(verDir, "1.16.5.json"), vanillaJson);
            File.WriteAllText(Path.Combine(verDir, "fabric-1.16.5.json"), fabricJson);
            File.WriteAllBytes(Path.Combine(verDir, "1.16.5.jar"), new byte[2048]);
            File.WriteAllText(Path.Combine(verDir, "install-receipt.json"), "{}");

            var spec = SupportedVersionSpec.CreateDefaults().First(v => v.MinecraftVersion == "1.16.5");
            engine.RefreshVersionState(spec);
            Assert.True(spec.State is VersionInstallState.Installed or VersionInstallState.RepairRequired);

            var acc = auth.CreateLocalSandboxAccount("AxsesDev");
            var profile = new LaunchProfile { Name = "Test 1.16.5", MinecraftVersion = "1.16.5", RamMb = 3072 };
            var args = engine.BuildLaunchArguments(spec, profile, acc, engine.GetInstanceDir("1.16.5"));

            Assert.Contains("-Xmx3072M", args);
            Assert.Contains("net.fabricmc.loader.impl.launch.knot.KnotClient", args);
            int cpIdx = args.IndexOf("-cp");
            Assert.True(cpIdx >= 0);
            string cp = args[cpIdx + 1];
            // Fabric ASM 9.7.1 must replace vanilla ASM 7.1
            Assert.Contains("asm-9.7.1.jar", cp);
            Assert.DoesNotContain("asm-7.1.jar", cp);
        }
        finally
        {
            Directory.Delete(temp, recursive: true);
        }
    }

    [Fact]
    public async Task AutoUpdater_VerifiesSha256AppliesAndRollsBackCleanly()
    {
        string temp = CreateTempDir();
        try
        {
            string installRoot = Path.Combine(temp, "install");
            string remoteSource = Path.Combine(temp, "remote");
            Directory.CreateDirectory(installRoot);
            Directory.CreateDirectory(remoteSource);

            string existingMod = Path.Combine(installRoot, "mods", "AXSESCLIENT-1.21.4.jar");
            Directory.CreateDirectory(Path.GetDirectoryName(existingMod)!);
            await File.WriteAllTextAsync(existingMod, "OLD_V1_0_0_CONTENT");

            string newArtifactSource = Path.Combine(remoteSource, "AXSESCLIENT-1.21.4.jar");
            byte[] newBytes = Encoding.UTF8.GetBytes("NEW_V1_1_0_VERIFIED_CONTENT");
            await File.WriteAllBytesAsync(newArtifactSource, newBytes);
            string sha256 = Convert.ToHexString(SHA256.HashData(newBytes)).ToLowerInvariant();

            var manifest = new UpdateManifest(
                "1.1.0",
                "GENESIS X+",
                "2026-10-30T12:00:00Z",
                ["Improved HUD snapping"],
                [new UpdateArtifact("AXSESCLIENT-1.21.4.jar", "mods/AXSESCLIENT-1.21.4.jar", newArtifactSource, sha256, newBytes.Length)]
            );
            string manifestPath = Path.Combine(remoteSource, "manifest.json");
            await File.WriteAllTextAsync(manifestPath, JsonSerializer.Serialize(manifest));

            var updater = new AutoUpdaterService(installRoot);
            var check = await updater.CheckForUpdatesAsync("1.0.0", manifestPath);
            Assert.True(check.UpdateAvailable);
            Assert.Equal("1.1.0", check.LatestVersion);

            await updater.DownloadAndVerifyArtifactAsync(check.Manifest!.Artifacts[0]);
            await updater.ApplyStagedUpdateAsync(check.Manifest, "1.0.0");
            Assert.Equal("NEW_V1_1_0_VERIFIED_CONTENT", await File.ReadAllTextAsync(existingMod));

            // Test rollback restores v1.0.0 cleanly
            Assert.True(updater.Rollback("1.0.0"));
            Assert.Equal("OLD_V1_0_0_CONTENT", await File.ReadAllTextAsync(existingMod));

            // Corrupt SHA-256 must be rejected
            var badArtifact = new UpdateArtifact("bad.jar", "mods/bad.jar", newArtifactSource, new string('0', 64), newBytes.Length);
            await Assert.ThrowsAsync<CryptographicException>(() => updater.DownloadAndVerifyArtifactAsync(badArtifact));
        }
        finally
        {
            Directory.Delete(temp, recursive: true);
        }
    }

    [AvaloniaFact]
    public void AvaloniaMainWindow_RendersAll11PagesAndCapturesScreenshots()
    {
        string temp = CreateTempDir();
        try
        {
            var controller = new LauncherStateController(temp);
            controller.AuthService.CreateLocalSandboxAccount("AxsesShowcase");
            var window = new MainWindow(controller);
            window.Show();

            string? screenshotDir = Environment.GetEnvironmentVariable("AXSES_SCREENSHOT_OUT");
            if (!string.IsNullOrWhiteSpace(screenshotDir))
                Directory.CreateDirectory(screenshotDir);

            foreach (LauncherPage page in Enum.GetValues<LauncherPage>())
            {
                window.NavigateTo(page);
                Dispatcher.UIThread.RunJobs();

                using var frame = window.CaptureRenderedFrame();
                Assert.NotNull(frame);
                Assert.True(frame!.PixelSize.Width >= 980);
                Assert.True(frame.PixelSize.Height >= 640);

                if (!string.IsNullOrWhiteSpace(screenshotDir))
                {
                    string pngPath = Path.Combine(screenshotDir, $"launcher-{page.ToString().ToLowerInvariant()}.png");
                    frame.Save(pngPath);
                }
            }

            window.Close();
        }
        finally
        {
            Directory.Delete(temp, recursive: true);
        }
    }

    private static string CreateTempDir()
    {
        string dir = Path.Combine(Path.GetTempPath(), "axses_xunit_" + Guid.NewGuid().ToString("N")[..8]);
        Directory.CreateDirectory(dir);
        return dir;
    }
}
