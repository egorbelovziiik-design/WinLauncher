using System.Text;
using System.Text.RegularExpressions;

namespace DrBrowser;

/// <summary>
/// DrAI Local — the browser's own assistant that runs entirely on this computer.
///
/// It is not a large language model: there is no neural network inside a 70 MB browser
/// and pretending otherwise would be a lie. What it does do, honestly and instantly:
///   * extractive summaries of long pages (frequency + position scoring, RU/EN stop words);
///   * answers to questions grounded in the text of the current page (sentence retrieval);
///   * keywords and reading statistics;
///   * structural analysis of code (language, functions, nesting, TODOs) instead of
///     guessing semantics;
///   * text clean-up (spacing, sentence capitalisation, duplicated spaces/punctuation).
///
/// Free-form conversation, translation and creative writing need an LLM — for those
/// DrAI switches to the user's own OpenAI-compatible endpoint (Settings → AI Assistant).
/// </summary>
public sealed partial class LocalAiEngine
{
    public const string EngineName = "DrAI Local";

    private static readonly HashSet<string> StopWords = new(StringComparer.OrdinalIgnoreCase)
    {
        // English
        "the","a","an","and","or","but","if","then","than","that","this","these","those","of","to","in","on",
        "for","with","as","is","are","was","were","be","been","being","it","its","at","by","from","we","you",
        "they","he","she","i","not","no","yes","do","does","did","has","have","had","can","could","should",
        "would","will","may","might","must","about","into","over","after","before","also","more","most","such",
        // Russian
        "и","в","во","не","что","он","на","я","с","со","как","а","то","все","она","так","его","но","да","ты",
        "к","у","же","вы","за","бы","по","только","ее","мне","было","вот","от","меня","еще","нет","о","из",
        "ему","теперь","когда","даже","ну","вдруг","ли","если","уже","или","ни","быть","был","него","до","вас",
        "нибудь","опять","уж","вам","ведь","там","потом","себя","ничего","ей","может","они","тут","где","есть",
        "надо","ней","для","мы","тебя","их","чем","была","сам","чтоб","без","будто","чего","раз","тоже","себе",
        "под","будет","ж","тогда","кто","этот","того","потому","этого","какой","совсем","ним","здесь","этом",
        "один","почти","мой","тем","чтобы","нее","сейчас","были","куда","зачем","всех","никогда","можно","при",
        "наконец","два","об","другой","хоть","после","над","больше","тот","через","эти","нас","про","всего",
        "них","какая","много","разве","три","эту","моя","впрочем","хорошо","свою","этой","перед","иногда","лучше",
        "чуть","том","нельзя","такой","им","более","всегда","конечно","всю","между"
    };

    public string Name => EngineName;

    public string Capabilities =>
        "Офлайн: краткое содержание, вопросы по странице, ключевые слова, статистика чтения, разбор кода, чистка текста. " +
        "Для перевода и свободного диалога нужен облачный ключ.";

    // ------------------------------------------------------------------
    // Summarisation
    // ------------------------------------------------------------------

    /// <summary>Extractive summary: the most representative sentences, kept in page order.</summary>
    public string Summarize(string text, int sentences = 6)
    {
        var clean = Clean(text);
        if (clean.Length == 0) return "На странице нет текста для краткого содержания.";

        var parts = SplitSentences(clean);
        if (parts.Length <= sentences) return string.Join(" ", parts);

        var frequencies = Frequencies(clean);
        var scored = new List<(int Index, double Score)>(parts.Length);

        for (var i = 0; i < parts.Length; i++)
        {
            var tokens = Tokenize(parts[i]);
            if (tokens.Count < 4) continue;

            var score = tokens.Sum(t => frequencies.TryGetValue(t, out var f) ? f : 0) / Math.Sqrt(tokens.Count);
            if (i < 2) score *= 1.25;                       // lead sentences carry the thesis
            if (i > parts.Length - 3) score *= 1.05;        // conclusions are often useful
            if (parts[i].Length > 400) score *= 0.85;       // avoid wall-of-text sentences
            scored.Add((i, score));
        }

        if (scored.Count == 0) return string.Join(" ", parts.Take(sentences));

        var chosen = scored
            .OrderByDescending(s => s.Score)
            .Take(Math.Max(1, sentences))
            .OrderBy(s => s.Index)
            .ToList();

        var builder = new StringBuilder();
        foreach (var item in chosen)
        {
            builder.Append("• ").Append(parts[item.Index].Trim()).AppendLine();
        }

        var words = Tokenize(clean).Count;
        builder.AppendLine()
            .Append($"Кратко по тексту · {parts.Length} предложений, ~{words} слов, читать ~{Math.Max(1, words / 200)} мин.");
        return builder.ToString().TrimEnd();
    }

    /// <summary>Answers a question using only sentences from the supplied page text.</summary>
    public string Answer(string question, string pageText)
    {
        var clean = Clean(pageText);
        if (clean.Length == 0)
            return "Открытая страница не содержит текста, по которому можно ответить. Откройте статью и повторите.";

        var questionTokens = Tokenize(question).Where(t => !StopWords.Contains(t)).Distinct().ToList();
        if (questionTokens.Count == 0)
            return "Сформулируйте вопрос конкретнее — по странице ищу ответ по ключевым словам.";

        var parts = SplitSentences(clean);
        var scored = parts
            .Select((sentence, index) =>
            {
                var tokens = Tokenize(sentence);
                var set = new HashSet<string>(tokens);
                var overlap = questionTokens.Count(set.Contains);
                if (overlap == 0) return (Index: index, Score: 0.0, Sentence: sentence);

                var score = overlap * 2.0 + overlap / (double)Math.Max(1, tokens.Count) * 10;
                if (index < 3) score += 0.6;
                return (Index: index, Score: score, Sentence: sentence);
            })
            .Where(x => x.Score > 0)
            .OrderByDescending(x => x.Score)
            .ThenBy(x => x.Index)
            .Take(3)
            .ToList();

        if (scored.Count == 0)
            return $"В тексте страницы нет предложений по запросу «{question.Trim()}». Попробуйте переформулировать или задать вопрос облачному DrAI.";

        var builder = new StringBuilder();
        builder.AppendLine("Нашёл на странице:");
        foreach (var hit in scored.OrderBy(s => s.Index))
            builder.Append("• ").AppendLine(hit.Sentence.Trim());

        builder.AppendLine()
            .Append("Источник: текст открытой страницы, локальный поиск DrAI. Ответ не додумывает факты за пределами текста.");
        return builder.ToString().TrimEnd();
    }

    public string Keywords(string text, int count = 8)
    {
        var clean = Clean(text);
        if (clean.Length == 0) return "Нет текста для анализа.";

        var frequencies = Frequencies(clean);
        var top = frequencies
            .Where(p => p.Key.Length > 3 && !float.TryParse(p.Key, out _))
            .OrderByDescending(p => p.Value)
            .Take(Math.Max(3, count))
            .Select(p => p.Key)
            .ToList();

        return top.Count == 0
            ? "Значимых слов не найдено."
            : $"Ключевые слова страницы: {string.Join(", ", top)}.";
    }

    public string ReadingStats(string text)
    {
        var clean = Clean(text);
        var words = Tokenize(clean).Count;
        var sentences = SplitSentences(clean).Length;
        var average = sentences == 0 ? 0 : words / (double)sentences;

        return $"Слов: {words} · предложений: {sentences} · в среднем {average:0.0} слов на предложение · " +
               $"чтение ~{Math.Max(1, words / 200)} мин · сложность: {(average > 24 ? "высокая (длинные предложения)" : average > 16 ? "средняя" : "лёгкая")}.";
    }

    // ------------------------------------------------------------------
    // Code
    // ------------------------------------------------------------------

    public string ExplainCode(string code)
    {
        var text = code?.Replace("\r\n", "\n") ?? string.Empty;
        if (text.Trim().Length == 0) return "Выделите код на странице, чтобы получить разбор.";

        var lines = text.Split('\n');
        var language = DetectLanguage(text);

        var comments = lines.Count(l =>
        {
            var t = l.TrimStart();
            return t.StartsWith("//") || t.StartsWith("#") || t.StartsWith("--") || t.StartsWith("/*")
                   || t.StartsWith("*") || t.StartsWith("<!--");
        });

        var blanks = lines.Count(l => string.IsNullOrWhiteSpace(l));
        var branchKeywords = new[] { "if ", "else", "for ", "while", "switch", "catch", "case ", "&&", "||" };
        var branches = lines.Sum(l => branchKeywords.Count(k => l.Contains(k, StringComparison.Ordinal)));

        var functions = Regex.Matches(text, @"\b(function|def|fn|func)\b").Count
                        + Regex.Matches(text, @"\b(public|private|protected|internal|static|async|void)\b[^\n;{}]*\)\s*\{").Count
                        + Regex.Matches(text, @"=>\s*\{").Count;

        var classes = Regex.Matches(text, @"\b(class|struct|interface|record|enum)\b").Count;
        var todos = lines.Count(l => l.Contains("TODO", StringComparison.OrdinalIgnoreCase)
                                     || l.Contains("FIXME", StringComparison.OrdinalIgnoreCase)
                                     || l.Contains("HACK", StringComparison.OrdinalIgnoreCase));

        var depth = MaxBraceDepth(text);
        var longest = lines.OrderByDescending(l => l.Trim().Length).FirstOrDefault()?.Trim() ?? string.Empty;

        var builder = new StringBuilder();
        builder.AppendLine($"Разбор кода · язык: {language}");
        builder.AppendLine($"• строк: {lines.Length} (непустых {lines.Length - blanks}, комментариев {comments})");
        builder.AppendLine($"• функций/методов: ~{functions} · классов/типов: {classes}");
        builder.AppendLine($"• ветвлений (if/for/while/catch): {branches} · максимальная вложенность фигурных скобок: {depth}");
        if (todos > 0) builder.AppendLine($"• TODO/FIXME/HACK: {todos} — есть незавершённые места");
        if (longest.Length > 140)
            builder.AppendLine($"• самая длинная строка {longest.Length} символов — кандидат на разбиение");

        builder.AppendLine();
        builder.Append("Локальный разбор описывает структуру, а не смысл: пошаговое объяснение алгоритма лучше спросить у облачного DrAI.");
        return builder.ToString().TrimEnd();
    }

    public static string DetectLanguage(string code)
    {
        if (code.Contains("using System", StringComparison.Ordinal) || code.Contains("namespace ", StringComparison.Ordinal)) return "C#";
        if (code.Contains("def ", StringComparison.Ordinal) || code.Contains("import ", StringComparison.Ordinal) && code.Contains("self", StringComparison.Ordinal)) return "Python";
        if (code.Contains("function ", StringComparison.Ordinal) || code.Contains("const ", StringComparison.Ordinal) || code.Contains("=>", StringComparison.Ordinal)) return "JavaScript / TypeScript";
        if (code.Contains("local function", StringComparison.Ordinal) || code.Contains("end)", StringComparison.Ordinal)) return "Luau / Lua";
        if (code.Contains("<html", StringComparison.OrdinalIgnoreCase) || code.Contains("</div>", StringComparison.Ordinal)) return "HTML";
        if (code.Contains("public class", StringComparison.Ordinal) || code.Contains("void ", StringComparison.Ordinal)) return "Java / Kotlin";
        if (code.Contains("SELECT ", StringComparison.OrdinalIgnoreCase) || code.Contains("FROM ", StringComparison.OrdinalIgnoreCase)) return "SQL";
        return "не определён";
    }

    private static int MaxBraceDepth(string text)
    {
        var depth = 0;
        var max = 0;
        foreach (var c in text)
        {
            if (c == '{') { depth++; if (depth > max) max = depth; }
            else if (c == '}' && depth > 0) depth--;
        }

        return max;
    }

    // ------------------------------------------------------------------
    // Text clean-up
    // ------------------------------------------------------------------

    public string Polish(string text)
    {
        var clean = Clean(text);
        if (clean.Length == 0) return "Нечего исправлять.";

        var sentences = SplitSentences(clean);
        var builder = new StringBuilder();
        foreach (var sentence in sentences)
        {
            var trimmed = sentence.Trim();
            if (trimmed.Length == 0) continue;
            var fixedSentence = char.ToUpperInvariant(trimmed[0]) + trimmed[1..];
            if (!fixedSentence.EndsWith('.') && !fixedSentence.EndsWith('!') && !fixedSentence.EndsWith('?'))
                fixedSentence += '.';
            builder.Append(fixedSentence).Append(' ');
        }

        var result = builder.ToString().Trim();
        return result.Length == 0 ? "Нечего исправлять." : result;
    }

    public static string TranslateNotice()
        => "Офлайн-перевод недоступен: для перевода DrAI Local не имеет словарной базы нужного размера. " +
           "Добавьте ключ OpenAI-совместимого API в настройках — и DrAI переведёт выделенный текст.";

    // ------------------------------------------------------------------
    // Text utilities
    // ------------------------------------------------------------------

    public static string Clean(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return string.Empty;
        var normalized = Regex.Replace(text, @"[ \t]+", " ");
        normalized = Regex.Replace(normalized, @"\n{3,}", "\n\n");
        return normalized.Trim();
    }

    public static string[] SplitSentences(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return Array.Empty<string>();

        var parts = Regex.Split(text.Replace("\n", " "), @"(?<=[\.\!\?…])\s+(?=[A-ZА-ЯЁ0-9«""(])");
        return parts
            .Select(p => p.Trim())
            .Where(p => p.Length > 12 && p.Any(char.IsLetter))
            .ToArray();
    }

    private static List<string> Tokenize(string text)
        => Regex.Matches(text.ToLowerInvariant(), @"[\p{L}\p{Nd}]{2,}")
            .Select(m => m.Value)
            .Where(t => t.Length > 2 && !StopWords.Contains(t))
            .ToList();

    private static Dictionary<string, double> Frequencies(string text)
    {
        var tokens = Tokenize(text);
        var frequencies = new Dictionary<string, double>(StringComparer.OrdinalIgnoreCase);
        foreach (var token in tokens)
            frequencies[token] = frequencies.TryGetValue(token, out var value) ? value + 1 : 1;

        var max = frequencies.Count == 0 ? 1 : frequencies.Values.Max();
        return frequencies.ToDictionary(p => p.Key, p => p.Value / max, StringComparer.OrdinalIgnoreCase);
    }
}
