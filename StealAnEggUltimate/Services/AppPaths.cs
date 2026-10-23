using System.IO;

namespace StealAnEggUltimate;

/// <summary>All runtime file locations used by the hub.</summary>
public static class AppPaths
{
    public const string ProductName = "STEAL AN EGG ULTIMATE HUB";
    public const string ProductFolder = "StealAnEggUltimate";

    public static string DataRoot { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ProductFolder);

    public static string ConfigDir => Path.Combine(DataRoot, "config");
    public static string ConfigFile => Path.Combine(ConfigDir, "settings.json");
    public static string ExportedConfigFile => Path.Combine(ConfigDir, "settings.export.json");
    public static string LogDir => Path.Combine(DataRoot, "logs");
    public static string SnapshotsDir => Path.Combine(DataRoot, "snapshots");
    public static string LastKnownSnapshot => Path.Combine(SnapshotsDir, "last-known.json");
    public static string BridgeDir => Path.Combine(DataRoot, "bridge");
    public static string BridgeFile => Path.Combine(BridgeDir, "eggs.json");
    public static string LuauDir => Path.Combine(DataRoot, "RobloxStudio");

    /// <summary>Folder that ships with the app, containing the Luau test-place modules.</summary>
    public static string BundledLuauDir => Path.Combine(AppContext.BaseDirectory, "RobloxStudio", "TestPlaceModules");

    public static void EnsureCreated()
    {
        Directory.CreateDirectory(DataRoot);
        Directory.CreateDirectory(ConfigDir);
        Directory.CreateDirectory(LogDir);
        Directory.CreateDirectory(SnapshotsDir);
        Directory.CreateDirectory(BridgeDir);
        Directory.CreateDirectory(LuauDir);
    }
}
