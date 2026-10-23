using System.IO;
using System.Text;

namespace DrBrowser;

/// <summary>Very small append-only log; never throws into the UI.</summary>
public sealed class Logger
{
    private readonly object _sync = new();
    private readonly string _file;

    public Logger()
    {
        AppPaths.EnsureCreated();
        _file = Path.Combine(AppPaths.LogDir, $"drbrowser-{DateTime.Now:yyyyMMdd}.log");
    }

    public string FilePath => _file;

    public void Info(string message) => Write("INFO ", message);
    public void Warn(string message) => Write("WARN ", message);
    public void Error(string message, Exception? ex = null)
        => Write("ERROR", ex is null ? message : $"{message} :: {ex.GetType().Name}: {ex.Message}");

    private void Write(string level, string message)
    {
        try
        {
            lock (_sync)
            {
                File.AppendAllText(_file, $"{DateTime.Now:HH:mm:ss.fff} {level} {message}{Environment.NewLine}", Encoding.UTF8);
            }
        }
        catch
        {
            // logging must never break the browser
        }
    }
}
