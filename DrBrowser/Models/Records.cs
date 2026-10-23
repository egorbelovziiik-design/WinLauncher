using System.Collections.ObjectModel;

namespace DrBrowser;

public sealed partial class HistoryEntry
{
    public long Id { get; set; }
    public string Url { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Host { get; set; } = string.Empty;
    public DateTime VisitedAt { get; set; } = DateTime.Now;

    public string VisitedText => VisitedAt.ToString("dd.MM.yyyy HH:mm");
    public string Display => string.IsNullOrWhiteSpace(Title) ? Url : Title;
}

public sealed class VisitedSite
{
    public string Host { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public int Visits { get; set; }
    public DateTime LastVisit { get; set; }
    public string VisitsText => $"{Visits} визит(ов)";
}

public sealed class Bookmark
{
    public long Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Folder { get; set; } = "Закладки";
    public DateTime CreatedAt { get; set; } = DateTime.Now;
    public string Display => string.IsNullOrWhiteSpace(Title) ? Url : Title;
    public string Host => Uri.TryCreate(Url, UriKind.Absolute, out var uri) ? uri.Host : Url;
}

public sealed class PasswordEntry
{
    public long Id { get; set; }
    public string Site { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    /// <summary>Decrypted only on demand; never written back to disk in plain text.</summary>
    public string Password { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; } = DateTime.Now;
    public string PasswordMask => new string('•', Math.Clamp(Password.Length, 6, 16));
}

public enum DownloadState
{
    InProgress,
    Completed,
    Cancelled,
    Failed
}

public sealed class DownloadItem : ObservableObject
{
    private long _received;
    private long _total;
    private DownloadState _state = DownloadState.InProgress;
    private double _speed;

    public long Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public DateTime StartedAt { get; set; } = DateTime.Now;
    public object? Operation { get; set; }

    public long ReceivedBytes { get => _received; set { if (Set(ref _received, value)) Raise(nameof(SizeText), nameof(ProgressPercent), nameof(ProgressFraction)); } }
    public long TotalBytes { get => _total; set { if (Set(ref _total, value)) Raise(nameof(SizeText), nameof(ProgressPercent), nameof(ProgressFraction), nameof(HasTotal)); } }
    public double SpeedBytesPerSecond { get => _speed; set { if (Set(ref _speed, value)) Raise(nameof(SpeedText)); } }

    public DownloadState State
    {
        get => _state;
        set
        {
            if (!Set(ref _state, value)) return;
            Raise(nameof(StateText), nameof(IsActive), nameof(IsCompleted), nameof(IsFailed));
        }
    }

    public bool IsActive => State == DownloadState.InProgress;
    public bool IsCompleted => State == DownloadState.Completed;
    public bool IsFailed => State is DownloadState.Failed or DownloadState.Cancelled;

    public double ProgressFraction => TotalBytes > 0 ? Math.Clamp(ReceivedBytes / (double)TotalBytes, 0, 1) : 0;
    public string ProgressPercent => TotalBytes > 0 ? $"{ProgressFraction:P0}" : "…";
    public bool HasTotal => TotalBytes > 0;

    public string SizeText => TotalBytes > 0
        ? $"{FormatSize(ReceivedBytes)} / {FormatSize(TotalBytes)}"
        : FormatSize(ReceivedBytes);

    public string SpeedText => State == DownloadState.InProgress && SpeedBytesPerSecond > 0
        ? $"{FormatSize((long)SpeedBytesPerSecond)}/s"
        : string.Empty;

    public string StateText => State switch
    {
        DownloadState.InProgress => "Загрузка",
        DownloadState.Completed => "Готово",
        DownloadState.Cancelled => "Отменено",
        _ => "Ошибка"
    };

    public static string FormatSize(long bytes)
    {
        if (bytes < 1024) return $"{bytes} B";
        if (bytes < 1024 * 1024) return $"{bytes / 1024.0:0.0} KB";
        if (bytes < 1024L * 1024 * 1024) return $"{bytes / (1024.0 * 1024):0.0} MB";
        return $"{bytes / (1024.0 * 1024 * 1024):0.00} GB";
    }
}

public sealed class QuickLink : ObservableObject
{
    private string _title = string.Empty;
    private string _url = string.Empty;

    public long Id { get; set; }
    public int SortOrder { get; set; }
    public string Title { get => _title; set => Set(ref _title, value); }
    public string Url { get => _url; set => Set(ref _url, value); }
    public string Letter => string.IsNullOrWhiteSpace(Title) ? "?" : Title[..1].ToUpperInvariant();
}

public sealed class ChatMessage
{
    public AiImageAttachment? Image { get; set; }
    public string AttachmentLabel => Image is null ? string.Empty : "📷 " + Image.Name;
    public string Role { get; set; } = "user";
    public string Text { get; set; } = string.Empty;
    public DateTime At { get; set; } = DateTime.Now;
    public bool IsUser => Role == "user";
    public string TimeText => At.ToString("HH:mm");
}

public sealed class SessionTab
{
    public string Url { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public bool IsPinned { get; set; }
}

public sealed class BrowserSession
{
    public List<SessionTab> Tabs { get; set; } = new();
    public int ActiveIndex { get; set; }
    public DateTime SavedAt { get; set; } = DateTime.Now;
}
