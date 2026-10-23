using System.IO;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace StealAnEggUltimate;

/// <summary>
/// Loads read-only egg snapshots from disk. Used for the manual fallback workflow
/// (copy the JSON printed by the Luau bridge into the snapshots folder) and for
/// inspecting snapshots of any place offline. Nothing is written to the game.
/// </summary>
public sealed class SnapshotService : IDisposable
{
    private readonly Logger _log;
    private FileSystemWatcher? _watcher;
    private DateTime _lastWatchEvent = DateTime.MinValue;

    public SnapshotService(Logger log)
    {
        _log = log;
    }

    public string Folder => AppPaths.SnapshotsDir;
    public string? LastFile { get; private set; }
    public string LastStatus { get; private set; } = "No snapshot loaded yet.";

    public event EventHandler<EggSnapshot>? SnapshotLoaded;

    public void StartWatching()
    {
        try
        {
            AppPaths.EnsureCreated();
            _watcher = new FileSystemWatcher(AppPaths.SnapshotsDir, "*.json")
            {
                NotifyFilter = NotifyFilters.LastWrite | NotifyFilters.FileName | NotifyFilters.Size,
                EnableRaisingEvents = true
            };

            _watcher.Created += OnChanged;
            _watcher.Changed += OnChanged;
            _watcher.Renamed += OnRenamed;
            _log.Info($"Watching {AppPaths.SnapshotsDir} for snapshot files.");
        }
        catch (Exception ex)
        {
            _log.Error("Snapshot folder watcher failed to start.", ex);
        }
    }

    private void OnRenamed(object sender, RenamedEventArgs e) => OnChanged(sender, e);

    private void OnChanged(object sender, FileSystemEventArgs e)
    {
        // Editors often fire several events; debounce briefly.
        if ((DateTime.UtcNow - _lastWatchEvent).TotalMilliseconds < 600) return;
        _lastWatchEvent = DateTime.UtcNow;

        Task.Run(async () =>
        {
            await Task.Delay(300).ConfigureAwait(false);
            LoadFile(e.FullPath, raiseEvent: true);
        });
    }

    /// <summary>Parses either a full snapshot document or a bare array of eggs.</summary>
    public static EggSnapshot? Parse(string json, string sourceName)
    {
        var trimmed = json.Trim();
        if (trimmed.Length == 0) return null;

        if (trimmed.StartsWith('['))
        {
            var entries = SettingsJson.Deserialize<List<EggSnapshotEntry>>(trimmed);
            if (entries is null) return null;
            return new EggSnapshot
            {
                Source = sourceName,
                Place = "snapshot array",
                Time = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                Eggs = entries
            };
        }

        var snapshot = SettingsJson.Deserialize<EggSnapshot>(trimmed);
        if (snapshot is null) return null;
        snapshot.Source = string.IsNullOrWhiteSpace(snapshot.Source) ? sourceName : snapshot.Source;
        snapshot.Eggs ??= new List<EggSnapshotEntry>();
        return snapshot;
    }

    public EggSnapshot? LoadFile(string path, bool raiseEvent)
    {
        try
        {
            if (!File.Exists(path))
            {
                LastStatus = $"File not found: {Path.GetFileName(path)}";
                return null;
            }

            var json = File.ReadAllText(path);
            var snapshot = Parse(json, Path.GetFileName(path));
            if (snapshot is null)
            {
                LastStatus = $"Unreadable snapshot: {Path.GetFileName(path)}";
                _log.Warn(LastStatus);
                return null;
            }

            LastFile = path;
            LastStatus = $"Loaded {snapshot.Eggs.Count} eggs from {Path.GetFileName(path)} at {DateTime.Now:HH:mm:ss}.";
            _log.Info(LastStatus);

            if (raiseEvent) SnapshotLoaded?.Invoke(this, snapshot);
            return snapshot;
        }
        catch (Exception ex)
        {
            LastStatus = $"Snapshot error: {ex.Message}";
            _log.Error($"Failed to load snapshot {path}.", ex);
            return null;
        }
    }

    public EggSnapshot? LoadNewest()
    {
        try
        {
            if (!Directory.Exists(AppPaths.SnapshotsDir)) return null;
            var newest = new DirectoryInfo(AppPaths.SnapshotsDir)
                .GetFiles("*.json")
                .OrderByDescending(f => f.LastWriteTimeUtc)
                .FirstOrDefault();

            return newest is null ? null : LoadFile(newest.FullName, raiseEvent: false);
        }
        catch (Exception ex)
        {
            _log.Error("Could not scan the snapshots folder.", ex);
            return null;
        }
    }

    public void Dispose()
    {
        if (_watcher is null) return;
        _watcher.EnableRaisingEvents = false;
        _watcher.Dispose();
        _watcher = null;
    }
}
