using System.IO;
using System.Text.Json;

namespace DrBrowser;

/// <summary>Saves and restores the open tab set between runs.</summary>
public sealed class SessionService
{
    private static readonly JsonSerializerOptions Options = new() { WriteIndented = true };
    private readonly Logger _log;

    public SessionService(Logger log) => _log = log;

    public void Save(IEnumerable<SessionTab> tabs, int activeIndex)
    {
        try
        {
            AppPaths.EnsureCreated();
            var session = new BrowserSession
            {
                Tabs = tabs.ToList(),
                ActiveIndex = Math.Max(0, activeIndex),
                SavedAt = DateTime.Now
            };

            var json = JsonSerializer.Serialize(session, Options);
            var tmp = AppPaths.SessionFile + ".tmp";
            File.WriteAllText(tmp, json);
            File.Move(tmp, AppPaths.SessionFile, overwrite: true);
        }
        catch (Exception ex)
        {
            _log.Error("session save failed", ex);
        }
    }

    public BrowserSession? Load()
    {
        try
        {
            if (!File.Exists(AppPaths.SessionFile)) return null;
            var json = File.ReadAllText(AppPaths.SessionFile);
            var session = JsonSerializer.Deserialize<BrowserSession>(json, Options);
            return session;
        }
        catch (Exception ex)
        {
            _log.Error("session load failed", ex);
            return null;
        }
    }

    public void Clear()
    {
        try
        {
            if (File.Exists(AppPaths.SessionFile)) File.Delete(AppPaths.SessionFile);
        }
        catch
        {
            // ignore
        }
    }
}
