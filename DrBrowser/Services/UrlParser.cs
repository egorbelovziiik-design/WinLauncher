using System.Text.RegularExpressions;

namespace DrBrowser;

/// <summary>Turns whatever the user typed into a URL, a search query or an internal page.</summary>
public static partial class UrlParser
{
    public const string InternalScheme = "drbrowser://";

    public static readonly (string Key, string Title)[] InternalPages =
    {
        ("newtab", "Новая вкладка"),
        ("settings", "Настройки"),
        ("history", "История"),
        ("downloads", "Загрузки"),
        ("bookmarks", "Закладки"),
        ("passwords", "Пароли"),
        ("search", "DrSearch"),
        ("music", "DrMusic"),
        ("music/search", "DrMusic · Поиск"),
        ("music/library", "DrMusic · Библиотека"),
        ("music/playlists", "DrMusic · Плейлисты"),
        ("mind", "DrMind"),
        ("guard", "DrGuard — защита"),
        ("gaming", "DrGaming"),
        ("workspaces", "DrWorkspaces"),
        ("tools", "DrTools"),
        ("notes", "DrNotes"),
        ("tasks", "DrTasks"),
        ("privacy", "DrPrivacy"),
        ("performance", "DrPerformance"),
        ("about", "О DrBrowser OMEGA")
    };

    [GeneratedRegex(@"^[a-zA-Z][a-zA-Z0-9+.\-]*:\/\/")]
    private static partial Regex SchemeRegex();

    [GeneratedRegex(@"^[\w\-]+(\.[\w\-]+)+(:[0-9]{1,5})?(\/.*)?$")]
    private static partial Regex HostLikeRegex();

    public static bool IsInternal(string url)
        => url.StartsWith(InternalScheme, StringComparison.OrdinalIgnoreCase)
           || url.Equals("about:blank", StringComparison.OrdinalIgnoreCase);

    public static string InternalPageKey(string url)
    {
        if (!url.StartsWith(InternalScheme, StringComparison.OrdinalIgnoreCase)) return string.Empty;
        var key = url[InternalScheme.Length..].Trim('/');
        var cut = key.IndexOfAny(new[] { '?', '#' });
        if (cut >= 0) key = key[..cut];
        return key.ToLowerInvariant();
    }

    /// <summary>Reads a ?q= / ?query= value from an internal page URL.</summary>
    public static string QueryOf(string url)
    {
        if (!url.StartsWith(InternalScheme, StringComparison.OrdinalIgnoreCase)) return string.Empty;
        var index = url.IndexOf('?');
        if (index < 0 || index == url.Length - 1) return string.Empty;

        foreach (var pair in url[(index + 1)..].Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var parts = pair.Split('=', 2);
            if (parts.Length != 2) continue;
            if (!parts[0].Equals("q", StringComparison.OrdinalIgnoreCase)
                && !parts[0].Equals("query", StringComparison.OrdinalIgnoreCase)) continue;

            return Uri.UnescapeDataString(parts[1].Replace('+', ' '));
        }

        return string.Empty;
    }

    public static string InternalPageTitle(string url)
    {
        var key = InternalPageKey(url);
        return InternalPages.FirstOrDefault(p => p.Key == key).Title ?? "DrBrowser";
    }

    /// <summary>True when the text should be navigated to rather than searched.</summary>
    public static bool LooksLikeUrl(string input)
    {
        var text = (input ?? string.Empty).Trim();
        if (text.Length == 0) return false;
        if (SchemeRegex().IsMatch(text)) return true;
        if (text.StartsWith(InternalScheme, StringComparison.OrdinalIgnoreCase)) return true;
        if (text.Equals("about:blank", StringComparison.OrdinalIgnoreCase)) return true;
        if (text.StartsWith("localhost", StringComparison.OrdinalIgnoreCase)) return true;
        if (text.Any(char.IsWhiteSpace)) return false;
        return HostLikeRegex().IsMatch(text);
    }

    /// <summary>Returns the URL to navigate to, or the search URL when the text is a query.</summary>
    public static string Resolve(string input, AppSettings settings)
    {
        var text = (input ?? string.Empty).Trim();
        if (text.Length == 0) return "drbrowser://newtab";

        if (text.StartsWith(InternalScheme, StringComparison.OrdinalIgnoreCase)) return text;

        // Shortcuts: "music", "settings", "drsearch <query>" open internal pages directly.
        var shortcut = text.ToLowerInvariant();
        if (shortcut is "music" or "музыка") return InternalScheme + "music";
        if (shortcut is "newtab" or "новая") return InternalScheme + "newtab";
        if (shortcut is "guard" or "защита" or "security") return InternalScheme + "guard";
        if (shortcut is "notes" or "заметки") return InternalScheme + "notes";
        if (shortcut is "tasks" or "задачи") return InternalScheme + "tasks";
        if (shortcut is "tools" or "инструменты") return InternalScheme + "tools";
        if (shortcut is "gaming" or "игры") return InternalScheme + "gaming";
        if (shortcut is "mind" or "drmind") return InternalScheme + "mind";
        if (text.Equals("about:blank", StringComparison.OrdinalIgnoreCase)) return text;

        if (!LooksLikeUrl(text)) return settings.SearchUrl(text);

        if (!SchemeRegex().IsMatch(text) && !text.StartsWith(InternalScheme, StringComparison.OrdinalIgnoreCase))
            text = "https://" + text;

        return Uri.TryCreate(text, UriKind.Absolute, out var uri) ? uri.ToString() : settings.SearchUrl(text);
    }

    /// <summary>Only these schemes are ever handed to the operating system.</summary>
    public static bool IsSafeExternalScheme(string scheme)
        => scheme.Equals("http", StringComparison.OrdinalIgnoreCase)
           || scheme.Equals("https", StringComparison.OrdinalIgnoreCase)
           || scheme.Equals("mailto", StringComparison.OrdinalIgnoreCase);

    public static string HostOf(string url)
        => Uri.TryCreate(url, UriKind.Absolute, out var uri) ? uri.Host : string.Empty;

    public static string DisplayHost(string url)
    {
        if (IsInternal(url)) return "DrBrowser";
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) return string.Empty;
        return uri.IsDefaultPort ? uri.Host : $"{uri.Host}:{uri.Port}";
    }

    public static bool IsHttps(string url)
        => Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps;

    /// <summary>Short label for a tab title when the page has no title yet.</summary>
    public static string FallbackTitle(string url)
    {
        if (IsInternal(url)) return InternalPageTitle(url);
        var host = DisplayHost(url);
        return string.IsNullOrWhiteSpace(host) ? "Новая вкладка" : host;
    }
}
