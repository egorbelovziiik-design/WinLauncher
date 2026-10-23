using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace StealAnEggUltimate;

/// <summary>
/// Renders live WPF visuals to PNG files. Used by the headless smoke run so the CI
/// log can prove that the hub and the in-game menu really draw something (not an
/// empty/black surface) instead of only asserting that no exception was thrown.
/// </summary>
public static class UiSnapshot
{
    public sealed record Result(bool Saved, int Width, int Height, double OpaqueRatio, double MeanLuma, string? Error);

    /// <summary>Renders <paramref name="element"/> into <paramref name="path"/> and reports pixel stats.</summary>
    public static Result Capture(FrameworkElement element, string path)
    {
        try
        {
            element.UpdateLayout();

            var width = (int)Math.Ceiling(element.ActualWidth);
            var height = (int)Math.Ceiling(element.ActualHeight);

            if (width < 2 || height < 2)
                return new Result(false, width, height, 0, 0, "element has no layout size");

            // Keep the capture small enough to be a reviewable file.
            const int maxWidth = 1600;
            var scale = width > maxWidth ? maxWidth / (double)width : 1.0;

            var rtb = new RenderTargetBitmap(
                (int)Math.Round(width * scale),
                (int)Math.Round(height * scale),
                96, 96, PixelFormats.Pbgra32);
            rtb.Render(element);

            var stride = rtb.PixelWidth * 4;
            var pixels = new byte[stride * rtb.PixelHeight];
            rtb.CopyPixels(pixels, stride, 0);

            var opaque = 0;
            var lumaSum = 0.0;
            for (var i = 0; i < pixels.Length; i += 4)
            {
                var alpha = pixels[i + 3];
                if (alpha > 16) opaque++;
                lumaSum += (pixels[i + 2] * 0.114 + pixels[i + 1] * 0.587 + pixels[i] * 0.299) * (alpha / 255.0);
            }

            var total = rtb.PixelWidth * (double)rtb.PixelHeight;

            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(rtb));
            using (var stream = File.Create(path)) encoder.Save(stream);

            return new Result(true, rtb.PixelWidth, rtb.PixelHeight, opaque / total, lumaSum / total, null);
        }
        catch (Exception ex)
        {
            return new Result(false, 0, 0, 0, 0, $"{ex.GetType().Name}: {ex.Message}");
        }
    }
}
