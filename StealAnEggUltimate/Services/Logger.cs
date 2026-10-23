using System.Collections.Concurrent;
using System.IO;
using System.Text;

namespace StealAnEggUltimate;

public enum LogLevel
{
    Debug,
    Info,
    Warn,
    Error
}

public sealed record LogEntry(DateTime Time, LogLevel Level, string Message)
{
    public string Line => $"{Time:HH:mm:ss}  {Level.ToString().ToUpperInvariant(),-5}  {Message}";
}

/// <summary>
/// Thread-safe file + in-memory logger. Never throws: logging failures are swallowed.
/// Keeps up to 10 daily files and trims a single file at 4 MB.
/// </summary>
public sealed class Logger
{
    private const long MaxFileBytes = 4L * 1024 * 1024;
    private const int MaxRetainedFiles = 10;
    private const int MaxRecentEntries = 500;

    private readonly object _sync = new();
    private readonly ConcurrentQueue<LogEntry> _recent = new();
    private readonly string _filePath;

    public Logger(string? logDirectory = null)
    {
        var dir = logDirectory ?? AppPaths.LogDir;
        try
        {
            Directory.CreateDirectory(dir);
        }
        catch
        {
            // read-only profile: fall back to temp
            dir = Path.Combine(Path.GetTempPath(), "StealAnEggUltimate", "logs");
            Directory.CreateDirectory(dir);
        }

        _filePath = Path.Combine(dir, $"hub-{DateTime.Now:yyyyMMdd}.log");
        DirectoryPath = dir;
    }

    public string DirectoryPath { get; }

    public event EventHandler<LogEntry>? EntryWritten;

    public void Debug(string message) => Write(LogLevel.Debug, message);
    public void Info(string message) => Write(LogLevel.Info, message);
    public void Warn(string message) => Write(LogLevel.Warn, message);

    public void Error(string message, Exception? exception = null)
        => Write(LogLevel.Error, exception is null ? message : $"{message} :: {exception.GetType().Name}: {exception.Message}");

    public IReadOnlyList<LogEntry> Recent(int count)
        => _recent.Reverse().Take(Math.Max(1, count)).Reverse().ToList();

    public void Write(LogLevel level, string message)
    {
        var entry = new LogEntry(DateTime.Now, level, message ?? string.Empty);

        _recent.Enqueue(entry);
        while (_recent.Count > MaxRecentEntries && _recent.TryDequeue(out _))
        {
        }

        try
        {
            lock (_sync)
            {
                TrimIfNeeded();
                File.AppendAllText(_filePath, entry.Line + Environment.NewLine, Encoding.UTF8);
            }
        }
        catch
        {
            // Logging must never take the app down.
        }

        try
        {
            EntryWritten?.Invoke(this, entry);
        }
        catch
        {
            // ignore subscriber failures
        }
    }

    private void TrimIfNeeded()
    {
        try
        {
            var info = new FileInfo(_filePath);
            if (info.Exists && info.Length > MaxFileBytes)
            {
                var rolled = Path.Combine(DirectoryPath, $"hub-{DateTime.Now:yyyyMMdd-HHmmss}.log");
                File.Move(_filePath, rolled, overwrite: true);
            }

            var files = new DirectoryInfo(DirectoryPath).GetFiles("hub-*.log")
                .OrderByDescending(f => f.LastWriteTimeUtc)
                .Skip(MaxRetainedFiles)
                .ToList();

            foreach (var file in files)
            {
                try
                {
                    file.Delete();
                }
                catch
                {
                    // best-effort retention
                }
            }
        }
        catch
        {
            // best effort
        }
    }
}
