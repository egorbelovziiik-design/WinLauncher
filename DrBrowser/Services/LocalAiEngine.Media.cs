using System.Text;

namespace DrBrowser;

/// <summary>
/// Concrete answers about links, videos and audio, built only from metadata that was
/// actually retrieved (oEmbed / page tags). No invented facts.
/// </summary>
public sealed partial class LocalAiEngine
{
    public string DescribeMedia(string? question, MediaContext media)
    {
        if (media is null) return "Ссылка не распознана.";

        var q = (question ?? string.Empty).ToLowerInvariant();
        var builder = new StringBuilder();

        if (!media.Ok)
        {
            builder.Append("Не удалось прочитать данные по ссылке: ")
                .Append(media.Message).Append('.');
            builder.Append("\n\nЧто можно сделать: открыть ссылку во вкладке и нажать «Кратко по странице» — ")
                .Append("тогда DrAI разберёт уже загруженный текст.");
            return builder.ToString();
        }

        var kindLabel = media.Kind switch
        {
            MediaKind.Video => "видео",
            MediaKind.Audio => "аудио",
            _ => "страница"
        };

        if (q.Contains("кто автор") || q.Contains("канал") || q.Contains("кто сделал"))
        {
            return string.IsNullOrWhiteSpace(media.Author)
                ? $"В метаданных {media.Provider} автор/канал не указан."
                : $"Автор/канал: {media.Author} ({media.Provider}).";
        }

        if (q.Contains("о чём") || q.Contains("о чем") || q.Contains("тема") || q.Contains("про что"))
        {
            builder.Append($"«{media.Title}» — это {kindLabel} на {media.Provider}.");
            if (!string.IsNullOrWhiteSpace(media.Author)) builder.Append($" Автор: {media.Author}.");
            builder.Append("\n\n");
            builder.Append(string.IsNullOrWhiteSpace(media.Description)
                ? "Описания в метаданных нет, поэтому точнее сказать о содержании нельзя."
                : "Из описания: " + media.Description);
            builder.Append("\n\nКадры и звук DrAI без vision-модели не смотрит — это ограничение честное: " +
                           "подключите ключ в Настройки → AI Assistant, чтобы модель разобрала содержимое.");
            return builder.ToString();
        }

        builder.Append("Ссылка: ").Append(media.SourceUrl).Append('\n');
        builder.Append("Провайдер: ").Append(media.Provider).Append(" · тип: ").Append(kindLabel).Append('\n');
        builder.Append("Заголовок: ").Append(string.IsNullOrWhiteSpace(media.Title) ? "—" : media.Title).Append('\n');
        if (!string.IsNullOrWhiteSpace(media.Author)) builder.Append("Автор: ").Append(media.Author).Append('\n');
        if (!string.IsNullOrWhiteSpace(media.Description)) builder.Append("Описание: ").Append(media.Description).Append('\n');
        builder.Append("Источник данных: ").Append(media.Message).Append('.');
        return builder.ToString().TrimEnd();
    }

    /// <summary>Short status line used by the AI panel after a link lookup.</summary>
    public static string MediaStatus(MediaContext media) => media.Ok
        ? $"DrAI: {media.Provider} · {media.Kind} · {media.Message}"
        : $"DrAI: метаданные ссылки недоступны ({media.Message})";
}
