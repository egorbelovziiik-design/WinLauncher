using System.Globalization;
using System.Text.Json;

namespace DrBrowser;

/// <summary>Pure local implementations used by DrTools; no expression is executed as code.</summary>
public static class UtilityEngine
{
    public static double Calculate(string expression) => new ExpressionParser(expression).Parse();

    public static string FormatJson(string input, bool minify = false)
    {
        using var document = JsonDocument.Parse(input, new JsonDocumentOptions
        {
            AllowTrailingCommas = true,
            CommentHandling = JsonCommentHandling.Skip,
            MaxDepth = 128
        });
        return JsonSerializer.Serialize(document.RootElement, new JsonSerializerOptions { WriteIndented = !minify });
    }

    public static double ConvertUnit(double value, string from, string to)
    {
        if (from == to) return value;
        if (TemperatureUnits.Contains(from) && TemperatureUnits.Contains(to))
        {
            var celsius = from switch
            {
                "°F" => (value - 32) * 5 / 9,
                "K" => value - 273.15,
                _ => value
            };
            return to switch
            {
                "°F" => celsius * 9 / 5 + 32,
                "K" => celsius + 273.15,
                _ => celsius
            };
        }

        if (!UnitFactors.TryGetValue(from, out var fromFactor) || !UnitFactors.TryGetValue(to, out var toFactor))
            throw new ArgumentException("Единицы относятся к разным категориям.");
        var fromCategory = UnitCategories[from];
        if (fromCategory != UnitCategories[to]) throw new ArgumentException("Единицы относятся к разным категориям.");
        return value * fromFactor / toFactor;
    }

    public static IReadOnlyList<string> Units { get; } = new[]
    {
        "mm", "cm", "m", "km", "in", "ft", "yd", "mi",
        "mg", "g", "kg", "oz", "lb",
        "B", "KB", "MB", "GB", "TB",
        "°C", "°F", "K"
    };

    public static string HexToRgb(string input)
    {
        var hex = (input ?? string.Empty).Trim().TrimStart('#');
        if (hex.Length == 3) hex = string.Concat(hex.Select(c => new string(c, 2)));
        if (hex.Length != 6 || !int.TryParse(hex, NumberStyles.HexNumber, CultureInfo.InvariantCulture, out var value))
            throw new FormatException("Введите HEX вида #8B5CF6.");
        return $"rgb({(value >> 16) & 255}, {(value >> 8) & 255}, {value & 255})";
    }

    private static readonly HashSet<string> TemperatureUnits = new() { "°C", "°F", "K" };
    private static readonly Dictionary<string, string> UnitCategories = new()
    {
        ["mm"]="length", ["cm"]="length", ["m"]="length", ["km"]="length", ["in"]="length", ["ft"]="length", ["yd"]="length", ["mi"]="length",
        ["mg"]="mass", ["g"]="mass", ["kg"]="mass", ["oz"]="mass", ["lb"]="mass",
        ["B"]="data", ["KB"]="data", ["MB"]="data", ["GB"]="data", ["TB"]="data"
    };
    private static readonly Dictionary<string, double> UnitFactors = new()
    {
        ["mm"]=0.001, ["cm"]=0.01, ["m"]=1, ["km"]=1000, ["in"]=0.0254, ["ft"]=0.3048, ["yd"]=0.9144, ["mi"]=1609.344,
        ["mg"]=0.000001, ["g"]=0.001, ["kg"]=1, ["oz"]=0.028349523125, ["lb"]=0.45359237,
        ["B"]=1, ["KB"]=1024, ["MB"]=1024d*1024, ["GB"]=1024d*1024*1024, ["TB"]=1024d*1024*1024*1024
    };

    private sealed class ExpressionParser
    {
        private readonly string _text;
        private int _position;

        public ExpressionParser(string text)
            => _text = (text ?? string.Empty).Replace(',', '.');

        public double Parse()
        {
            if (_text.Length > 1000) throw new FormatException("Выражение слишком длинное.");
            var value = ParseExpression();
            SkipWhite();
            if (_position != _text.Length) throw Error("неожиданный символ");
            if (double.IsNaN(value) || double.IsInfinity(value)) throw new ArithmeticException("Результат не является конечным числом.");
            return value;
        }

        private double ParseExpression()
        {
            var value = ParseTerm();
            while (true)
            {
                SkipWhite();
                if (Take('+')) value += ParseTerm();
                else if (Take('-')) value -= ParseTerm();
                else return value;
            }
        }

        private double ParseTerm()
        {
            var value = ParsePower();
            while (true)
            {
                SkipWhite();
                if (Take('*')) value *= ParsePower();
                else if (Take('/'))
                {
                    var divisor = ParsePower();
                    if (Math.Abs(divisor) < double.Epsilon) throw new DivideByZeroException("Деление на ноль.");
                    value /= divisor;
                }
                else if (Take('%')) value %= ParsePower();
                else return value;
            }
        }

        private double ParsePower()
        {
            var value = ParseUnary();
            SkipWhite();
            return Take('^') ? Math.Pow(value, ParsePower()) : value;
        }

        private double ParseUnary()
        {
            SkipWhite();
            if (Take('+')) return ParseUnary();
            if (Take('-')) return -ParseUnary();
            return ParsePrimary();
        }

        private double ParsePrimary()
        {
            SkipWhite();
            if (Take('('))
            {
                var nested = ParseExpression();
                SkipWhite();
                if (!Take(')')) throw Error("ожидалась закрывающая скобка");
                return nested;
            }

            if (_position < _text.Length && (char.IsLetter(_text[_position]) || _text[_position] == 'π'))
            {
                var name = ReadName().ToLowerInvariant();
                if (name is "pi" or "π") return Math.PI;
                if (name == "e") return Math.E;
                SkipWhite();
                if (!Take('(')) throw Error("после функции нужна скобка");
                var argument = ParseExpression();
                if (!Take(')')) throw Error("ожидалась закрывающая скобка");
                return name switch
                {
                    "sqrt" => argument < 0 ? throw new ArithmeticException("Корень из отрицательного числа.") : Math.Sqrt(argument),
                    "abs" => Math.Abs(argument),
                    "sin" => Math.Sin(argument),
                    "cos" => Math.Cos(argument),
                    "tan" => Math.Tan(argument),
                    "ln" => Math.Log(argument),
                    "log" => Math.Log10(argument),
                    _ => throw Error("неизвестная функция " + name)
                };
            }

            var start = _position;
            while (_position < _text.Length && (char.IsDigit(_text[_position]) || _text[_position] is '.' or 'e' or 'E'
                                                 || (_position > start && (_text[_position] == '+' || _text[_position] == '-')
                                                                       && (_text[_position - 1] == 'e' || _text[_position - 1] == 'E'))))
                _position++;
            if (start == _position || !double.TryParse(_text[start.._position], NumberStyles.Float, CultureInfo.InvariantCulture, out var number))
                throw Error("ожидалось число");
            return number;
        }

        private string ReadName()
        {
            var start = _position;
            while (_position < _text.Length && (char.IsLetter(_text[_position]) || _text[_position] == 'π')) _position++;
            return _text[start.._position];
        }

        private void SkipWhite()
        {
            while (_position < _text.Length && char.IsWhiteSpace(_text[_position])) _position++;
        }

        private bool Take(char value)
        {
            SkipWhite();
            if (_position >= _text.Length || _text[_position] != value) return false;
            _position++;
            return true;
        }

        private FormatException Error(string message) => new($"{message} (позиция {_position + 1}).");
    }
}
