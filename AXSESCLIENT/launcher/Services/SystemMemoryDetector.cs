using System.Runtime.InteropServices;

namespace AxsesClient.Launcher.Services;

public sealed record SystemMemorySnapshot(
    long TotalPhysicalMb,
    long AvailablePhysicalMb,
    int MaxRecommendedAllocationMb,
    int OptimalDefaultAllocationMb);

public static partial class SystemMemoryDetector
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct MEMORYSTATUSEX
    {
        public uint dwLength;
        public uint dwMemoryLoad;
        public ulong ullTotalPhys;
        public ulong ullAvailPhys;
        public ulong ullTotalPageFile;
        public ulong ullAvailPageFile;
        public ulong ullTotalVirtual;
        public ulong ullAvailVirtual;
        public ulong ullAvailExtendedVirtual;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GlobalMemoryStatusEx(ref MEMORYSTATUSEX lpBuffer);

    public static SystemMemorySnapshot Query()
    {
        long totalMb = 0;
        long availMb = 0;

        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            try
            {
                var mem = new MEMORYSTATUSEX { dwLength = (uint)Marshal.SizeOf<MEMORYSTATUSEX>() };
                if (GlobalMemoryStatusEx(ref mem) && mem.ullTotalPhys > 0)
                {
                    totalMb = (long)(mem.ullTotalPhys / (1024UL * 1024UL));
                    availMb = (long)(mem.ullAvailPhys / (1024UL * 1024UL));
                }
            }
            catch
            {
                // Fallback below
            }
        }
        else if (File.Exists("/proc/meminfo"))
        {
            try
            {
                foreach (string line in File.ReadLines("/proc/meminfo"))
                {
                    if (line.StartsWith("MemTotal:", StringComparison.Ordinal))
                        totalMb = ParseMemInfoKb(line) / 1024L;
                    else if (line.StartsWith("MemAvailable:", StringComparison.Ordinal))
                        availMb = ParseMemInfoKb(line) / 1024L;
                }
            }
            catch { }
        }

        if (totalMb <= 0)
        {
            long gcTotal = GC.GetGCMemoryInfo().TotalAvailableMemoryBytes;
            totalMb = gcTotal > 0 ? gcTotal / (1024L * 1024L) : 8192L;
        }
        if (availMb <= 0 || availMb > totalMb)
        {
            availMb = Math.Max(1024L, (long)(totalMb * 0.55));
        }

        // Leave at least 2 GB for Windows OS & GPU drivers, quantized cleanly to 256 MB steps
        int maxAlloc = (int)Math.Clamp(totalMb - 2048L, 2048L, 32768L);
        maxAlloc = (maxAlloc / 256) * 256;

        int optimal = totalMb >= 16000 ? 6144 : (totalMb >= 8000 ? 4096 : 2560);
        optimal = Math.Clamp(optimal, 1024, maxAlloc);

        return new SystemMemorySnapshot(totalMb, availMb, maxAlloc, optimal);
    }

    private static long ParseMemInfoKb(string line)
    {
        string digits = new string(line.Where(char.IsDigit).ToArray());
        return long.TryParse(digits, out long kb) ? kb : 0L;
    }
}
