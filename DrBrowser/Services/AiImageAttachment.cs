namespace DrBrowser;

/// <summary>Explicit opt-in attachment; held in memory only, never in settings or SQLite.</summary>
public sealed record AiImageAttachment(string Name, string DataUrl)
{
    public const int MaxBytes = 5 * 1024 * 1024;

    public static AiImageAttachment FromBytes(string name, byte[] bytes)
    {
        if (bytes.Length == 0 || bytes.Length > MaxBytes)
            throw new ArgumentException("Изображение должно быть от 1 байта до 5 МБ.");
        var mime = bytes.Length >= 8 && bytes.AsSpan(0, 8).SequenceEqual(new byte[] {137,80,78,71,13,10,26,10})
            ? "image/png"
            : bytes.Length >= 3 && bytes[0] == 255 && bytes[1] == 216 && bytes[2] == 255
                ? "image/jpeg" : null;
        if (mime is null) throw new ArgumentException("Поддерживаются только PNG и JPEG с корректной сигнатурой.");
        return new AiImageAttachment(System.IO.Path.GetFileName(name), "data:" + mime + ";base64," + Convert.ToBase64String(bytes));
    }
}
