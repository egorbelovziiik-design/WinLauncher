namespace StealAnEggUltimate;

public static class AppInfo
{
    public const string ProductName = "STEAL AN EGG ULTIMATE HUB";
    public const string ShortName = "StealAnEggUltimate";
    public const string Version = "1.0.0";
    public const string BuildChannel = "Release";
    public const string DataFolder = "%AppData%\\StealAnEggUltimate";
    public const string RepositoryUrl = "https://github.com/egorbelovziiik-design/WinLauncher";

    public static string VersionText => $"v{Version}  ·  {BuildChannel}  ·  Windows 10/11 x64";

    public static string Description =>
        "Standalone overlay hub. It observes the Roblox client window and talks to the bundled " +
        "test place through a loopback JSON bridge. It does not inject DLLs, read game memory, " +
        "patch the client or bypass anti-cheat.";

    public static string BridgeNote =>
        "Character control (speed, fly, noclip, jump, teleport) and automation are real only inside " +
        "the bundled STEAL AN EGG test place through its Luau bridge. In every other experience the " +
        "hub shows a preview/demo and never modifies the game.";
}
