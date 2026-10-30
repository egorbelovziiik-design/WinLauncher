using Avalonia;
using Avalonia.Headless;
using AxsesClient.Launcher;
using AxsesClient.Tests;

[assembly: AvaloniaTestApplication(typeof(TestAppBuilder))]

namespace AxsesClient.Tests;

public sealed class TestAppBuilder
{
    public static AppBuilder BuildAvaloniaApp() =>
        AppBuilder.Configure<App>()
            .UseSkia()
            .WithInterFont()
            .UseHeadless(new AvaloniaHeadlessPlatformOptions
            {
                UseHeadlessDrawing = false
            });
}
