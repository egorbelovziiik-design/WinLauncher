/*
 * NEXUS CLIENT OMEGA X - NexusInjector
 * ---------------------------------------------------------------------------
 * Native Windows launcher / installer for the Nexus Client Omega X mod.
 *
 * It is deliberately a *legitimate* tool:
 *   - it installs Fabric through the official Fabric installer,
 *   - it copies the Nexus jar into the mods folder of the game directory,
 *   - it registers a normal profile in the official Minecraft launcher,
 *   - it never reads credentials, never injects into other processes and
 *     never touches anything outside the folders the user selects.
 *
 * Build (cross compiled from any platform, see build.sh):
 *   zig cc -target x86_64-windows-gnu -O2 -municode-free -o NexusInjector.exe \
 *       src/nexus_injector.c -lgdi32 -luser32 -lcomctl32 -lcomdlg32 \
 *       -lshell32 -lole32 -lurlmon -lshlwapi -mwindows
 */
#include <windows.h>
#include <commctrl.h>
#include <uxtheme.h>
#include <shlobj.h>
#include <shellapi.h>
#include <urlmon.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <stdlib.h>

#ifndef PBM_SETBARCOLOR
#define PBM_SETBARCOLOR (WM_USER + 9)
#endif
#ifndef PBM_SETBKCOLOR
#define PBM_SETBKCOLOR (WM_USER + 19)
#endif

/* ------------------------------------------------------------------ palette */
#define C_BG        RGB(0x08, 0x08, 0x0D)
#define C_PANEL     RGB(0x10, 0x10, 0x18)
#define C_SURFACE   RGB(0x18, 0x18, 0x24)
#define C_ACCENT    RGB(0x7C, 0x3A, 0xED)
#define C_ACCENT2   RGB(0x93, 0x33, 0xEA)
#define C_ACCENT3   RGB(0xA8, 0x55, 0xF7)
#define C_ACCENT4   RGB(0xC0, 0x84, 0xFC)
#define C_TEXT      RGB(0xF5, 0xF3, 0xFF)
#define C_MUTED     RGB(0x8B, 0x88, 0xA8)

#define CLIENT_NAME  "Nexus Client Omega X"
#define CLIENT_ID    "nexusomegax"
#define CLIENT_VER   "1.0.0"
#define PROJECT_URL  "https://github.com/egorbelovziiik-design/WinLauncher"
#define FABRIC_MAVEN "https://maven.fabricmc.net/net/fabricmc/fabric-installer/"

/* ------------------------------------------------------------------- models */
typedef struct {
    const char *mc;            /* Minecraft version          */
    int         protocol;      /* network protocol           */
    const char *java;          /* required java              */
    const char *jar;           /* adapter jar file name      */
    const char *loaderVersion; /* fabric-loader profile id   */
    const char *installer;     /* fabric-installer version   */
} NexusVersion;

static const NexusVersion VERSIONS[] = {
    {"1.16.5",  754, "Java 8",  "NexusOmegaX-1.16.5.jar",  "fabric-loader-0.14.21-1.16.5",  "0.11.2"},
    {"1.21.4",  769, "Java 21", "NexusOmegaX-1.21.4.jar",  "fabric-loader-0.16.10-1.21.4",  "1.0.3"},
    {"1.21.11", 774, "Java 21", "NexusOmegaX-1.21.11.jar", "fabric-loader-0.17.2-1.21.11",  "1.0.3"}
};
#define VERSION_COUNT ((int)(sizeof(VERSIONS) / sizeof(VERSIONS[0])))

/* ----------------------------------------------------------------- globals */
static HWND   g_hwnd        = NULL;
static HWND   g_log         = NULL;
static HWND   g_progress    = NULL;
static HWND   g_pages[5]    = {0, 0, 0, 0, 0};
static HWND   g_tabs[5]     = {0, 0, 0, 0, 0};
static int    g_page        = 0;
static HBRUSH g_bgBrush     = NULL;
static HBRUSH g_panelBrush  = NULL;
static HBRUSH g_fieldBrush  = NULL;
static HFONT  g_font        = NULL;
static HFONT  g_fontBold    = NULL;
static HFONT  g_fontTitle   = NULL;

/* per page control handles */
static HWND g_editGameDir, g_comboVersion, g_lblVersionInfo, g_chkDefault;
static HWND g_editJava, g_lblJavaInfo;
static HWND g_lblGameDirTitle, g_lblVersionTitle, g_lblJavaTitle;
static HWND g_lblSettingsHint, g_lblSettingsPath;

static char g_appData[MAX_PATH]      = "";
static char g_settingsPath[MAX_PATH] = "";
static char g_logPath[MAX_PATH]      = "";

/* ------------------------------------------------------------------ helpers */
static void appendLog(const char *fmt, ...)
{
    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    if (g_log) {
        SendMessageA(g_log, LB_ADDSTRING, 0, (LPARAM)buffer);
        SendMessageA(g_log, LB_SETCURSEL, (WPARAM)SendMessageA(g_log, LB_GETCOUNT, 0, 0) - 1, 0);
    }
    if (g_logPath[0]) {
        FILE *f = fopen(g_logPath, "ab");
        if (f) {
            fprintf(f, "%s\n", buffer);
            fclose(f);
        }
    }
}

static int fileExists(const char *path)
{
    DWORD attributes = GetFileAttributesA(path);
    return attributes != INVALID_FILE_ATTRIBUTES
           && !(attributes & FILE_ATTRIBUTE_DIRECTORY);
}

static int dirExists(const char *path)
{
    DWORD attributes = GetFileAttributesA(path);
    return attributes != INVALID_FILE_ATTRIBUTES
           && (attributes & FILE_ATTRIBUTE_DIRECTORY);
}

static void ensureDir(const char *path)
{
    if (!dirExists(path)) {
        CreateDirectoryA(path, NULL);
    }
}

static void pathJoin(char *out, size_t size, const char *a, const char *b)
{
    snprintf(out, size, "%s\\%s", a, b);
}

static void progress(int percent)
{
    if (g_progress) {
        SendMessageA(g_progress, PBM_SETPOS, (WPARAM)percent, 0);
    }
}

static void pumpMessages(void)
{
    MSG msg;
    while (PeekMessageA(&msg, NULL, 0, 0, PM_REMOVE)) {
        TranslateMessage(&msg);
        DispatchMessageA(&msg);
    }
}

static void shellOpen(const char *target, const char *params)
{
    HINSTANCE result = ShellExecuteA(NULL, "open", target, params, NULL, SW_SHOWNORMAL);
    if ((INT_PTR)result <= 32) {
        appendLog("   ! could not open %s", target);
    }
}

/* --------------------------------------------------------------- settings io */
static void settingsLoad(char *gameDir, size_t gameSize, char *javaPath, size_t javaSize, int *versionIndex)
{
    FILE *f = fopen(g_settingsPath, "rb");
    char line[1024];
    if (!f) {
        return;
    }
    while (fgets(line, sizeof(line), f)) {
        size_t len = strlen(line);
        while (len && (line[len - 1] == '\n' || line[len - 1] == '\r')) {
            line[--len] = 0;
        }
        if (!strncmp(line, "gameDir=", 8)) {
            strncpy(gameDir, line + 8, gameSize - 1);
        } else if (!strncmp(line, "javaPath=", 9)) {
            strncpy(javaPath, line + 9, javaSize - 1);
        } else if (!strncmp(line, "version=", 8)) {
            int index = atoi(line + 8);
            if (index >= 0 && index < VERSION_COUNT) {
                *versionIndex = index;
            }
        }
    }
    fclose(f);
}

static void settingsSave(const char *gameDir, const char *javaPath, int versionIndex)
{
    FILE *f = fopen(g_settingsPath, "wb");
    if (!f) {
        return;
    }
    fprintf(f, "# NexusInjector settings\n");
    fprintf(f, "gameDir=%s\n", gameDir);
    fprintf(f, "javaPath=%s\n", javaPath);
    fprintf(f, "version=%d\n", versionIndex);
    fclose(f);
}

/* ------------------------------------------------------------- java lookup */
static int findJava(char *out, size_t size)
{
    static const char *roots[] = {
        "C:\\Program Files\\Eclipse Adoptium",
        "C:\\Program Files\\Java",
        "C:\\Program Files\\Zulu",
        "C:\\Program Files\\BellSoft",
        "C:\\Program Files\\Microsoft",
        "C:\\Program Files (x86)\\Java",
        "C:\\Program Files (x86)\\Zulu"
    };
    unsigned int i;
    char buffer[MAX_PATH];

    if (SearchPathA(NULL, "javaw.exe", NULL, (DWORD)size, out, NULL)) {
        return 1;
    }
    for (i = 0; i < sizeof(roots) / sizeof(roots[0]); i++) {
        WIN32_FIND_DATAA data;
        HANDLE handle;
        char pattern[MAX_PATH];
        snprintf(pattern, sizeof(pattern), "%s\\*", roots[i]);
        handle = FindFirstFileA(pattern, &data);
        if (handle == INVALID_HANDLE_VALUE) {
            continue;
        }
        do {
            if (!(data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) {
                continue;
            }
            snprintf(buffer, sizeof(buffer), "%s\\%s\\bin\\javaw.exe", roots[i], data.cFileName);
            if (fileExists(buffer)) {
                strncpy(out, buffer, size - 1);
                out[size - 1] = 0;
                FindClose(handle);
                return 1;
            }
        } while (FindNextFileA(handle, &data));
        FindClose(handle);
    }
    out[0] = 0;
    return 0;
}

/* --------------------------------------------------------- copy with progress */
static DWORD CALLBACK copyProgress(LARGE_INTEGER total, LARGE_INTEGER transferred,
                                   LARGE_INTEGER streamSize, LARGE_INTEGER streamTransferred,
                                   DWORD streamNumber, DWORD reason, HANDLE file,
                                   HANDLE destFile, LPVOID data)
{
    (void)streamSize; (void)streamTransferred; (void)streamNumber;
    (void)reason; (void)file; (void)destFile; (void)data;
    if (total.QuadPart > 0) {
        progress((int)((transferred.QuadPart * 100) / total.QuadPart));
    }
    pumpMessages();
    return PROGRESS_CONTINUE;
}

/* --------------------------------------------------------- profile handling */
/*
 * Inserts or updates the "Nexus Client Omega X" profile inside the official
 * launcher_profiles.json. The rest of the file is never rewritten: only the
 * value of lastVersionId / gameDir inside that profile object changes, so the
 * user keeps every other profile untouched.
 */
static int updateLauncherProfiles(const char *gameDir, const char *versionId, int makeDefault)
{
    char profilesPath[MAX_PATH];
    char *content;
    char *profilesKey;
    char *profileKey;
    char *insertAt;
    char *cursor;
    DWORD size;
    FILE *f;
    long length;
    int updated = 0;

    pathJoin(profilesPath, sizeof(profilesPath), gameDir, "launcher_profiles.json");
    f = fopen(profilesPath, "rb");
    if (!f) {
        appendLog("   ! launcher_profiles.json not found (%s)", profilesPath);
        return 0;
    }
    fseek(f, 0, SEEK_END);
    length = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (length <= 0) {
        fclose(f);
        return 0;
    }
    content = (char *)malloc((size_t)length + 4096);
    if (!content) {
        fclose(f);
        return 0;
    }
    size = (DWORD)fread(content, 1, (size_t)length, f);
    content[size] = 0;
    fclose(f);

    profilesKey = strstr(content, "\"profiles\"");
    if (!profilesKey) {
        free(content);
        appendLog("   ! launcher_profiles.json has no profiles section");
        return 0;
    }
    profileKey = strstr(profilesKey, CLIENT_NAME);
    if (profileKey) {
        char *objectStart = profileKey;
        char *lastVersion;
        char *end;
        while (objectStart > content && *objectStart != '{') {
            objectStart--;
        }
        end = objectStart;
        {
            int depth = 0;
            while (*end) {
                if (*end == '{') depth++;
                else if (*end == '}') {
                    depth--;
                    if (depth == 0) break;
                }
                end++;
            }
        }
        lastVersion = strstr(objectStart, "\"lastVersionId\"");
        if (lastVersion && lastVersion < end) {
            char *firstQuote = strchr(lastVersion, ':');
            if (firstQuote) {
                char *valueStart = strchr(firstQuote, '"');
                if (valueStart && valueStart < end) {
                    char *valueEnd = strchr(valueStart + 1, '"');
                    if (valueEnd && valueEnd < end) {
                        size_t headLen = (size_t)(valueStart - content) + 1;
                        size_t tailLen = strlen(valueEnd) + 1;
                        size_t capacity = (size_t)length + headLen + tailLen + 4096;
                        char *head = (char *)malloc(headLen + 1);
                        char *tail = (char *)malloc(tailLen + 1);
                        char *rebuilt = (char *)malloc(capacity);
                        if (head && tail && rebuilt) {
                            memcpy(head, content, headLen);
                            head[headLen] = 0;
                            memcpy(tail, valueEnd, tailLen);
                            snprintf(rebuilt, capacity, "%s\"%s", head, versionId);
                            strncat(rebuilt, tail, capacity - strlen(rebuilt) - 1);
                            strncpy(content, rebuilt, (size_t)length + 4095);
                            content[(size_t)length + 4095] = 0;
                            updated = 1;
                        }
                        free(head);
                        free(tail);
                        free(rebuilt);
                    }
                }
            }
        }
    }

    if (!updated) {
        char entry[1024];
        char *tail;
        size_t capacity;
        insertAt = strchr(profilesKey, '{');
        if (!insertAt) {
            free(content);
            return 0;
        }
        insertAt++;
        snprintf(entry, sizeof(entry),
                 "\n    \"%s\": {\n"
                 "      \"name\": \"%s\",\n"
                 "      \"type\": \"custom\",\n"
                 "      \"created\": \"1970-01-01T00:00:00.000Z\",\n"
                 "      \"lastUsed\": \"1970-01-01T00:00:00.000Z\",\n"
                 "      \"icon\": \"Furnace\",\n"
                 "      \"lastVersionId\": \"%s\",\n"
                 "      \"gameDir\": \"%s\"\n"
                 "    },",
                 CLIENT_NAME, CLIENT_NAME, versionId, gameDir);
        capacity = strlen(insertAt) + 1;
        tail = (char *)malloc(capacity);
        cursor = (char *)malloc((size_t)length + strlen(entry) + capacity + 4096);
        if (!tail || !cursor) {
            free(tail);
            free(cursor);
            free(content);
            return 0;
        }
        memcpy(tail, insertAt, capacity);
        snprintf(cursor, (size_t)length + strlen(entry) + capacity + 4096, "%.*s%s%s",
                 (int)(insertAt - content), content, entry, tail);
        strncpy(content, cursor, (size_t)length + 4095);
        content[(size_t)length + 4095] = 0;
        free(tail);
        free(cursor);
        updated = 1;
    }

    if (makeDefault) {
        char *selected = strstr(content, "\"selectedProfile\"");
        if (selected) {
            char *firstQuote = strchr(selected, ':');
            if (firstQuote) {
                char *valueStart = strchr(firstQuote, '"');
                if (valueStart) {
                    char *valueEnd = strchr(valueStart + 1, '"');
                    if (valueEnd) {
                        size_t tailLen = strlen(valueEnd) + 1;
                        size_t capacity = (size_t)length + tailLen + 4096;
                        char *tail = (char *)malloc(tailLen);
                        char *rebuilt = (char *)malloc(capacity);
                        if (tail && rebuilt) {
                            memcpy(tail, valueEnd, tailLen);
                            snprintf(rebuilt, capacity, "\"%s\"%s", CLIENT_NAME, tail);
                            strncpy(valueStart, rebuilt,
                                    (size_t)length + 4095 - (size_t)(valueStart - content));
                            content[(size_t)length + 4095] = 0;
                        }
                        free(tail);
                        free(rebuilt);
                    }
                }
            }
        }
    }

    f = fopen(profilesPath, "wb");
    if (f) {
        fwrite(content, 1, strlen(content), f);
        fclose(f);
    }
    free(content);
    return updated;
}

/* ------------------------------------------------------------------ actions */
static void locateGameDir(char *out, size_t size)
{
    const char *appData = getenv("APPDATA");
    if (appData && *appData) {
        snprintf(out, size, "%s\\.minecraft", appData);
    } else {
        snprintf(out, size, "C:\\Users\\%s\\AppData\\Roaming\\.minecraft", getenv("USERNAME") ? getenv("USERNAME") : "User");
    }
}

static int currentVersionIndex(void)
{
    return (int)SendMessageA(g_comboVersion, CB_GETCURSEL, 0, 0);
}

static void getGameDir(char *out, size_t size)
{
    GetWindowTextA(g_editGameDir, out, (int)size);
}

static void findJar(char *out, size_t size)
{
    char exeDir[MAX_PATH];
    char modulePath[MAX_PATH];
    int index = currentVersionIndex();
    if (index < 0 || index >= VERSION_COUNT) {
        index = 1;
    }
    GetModuleFileNameA(NULL, modulePath, MAX_PATH);
    {
        char *slash = strrchr(modulePath, '\\');
        if (slash) {
            *slash = 0;
        }
    }
    strncpy(exeDir, modulePath, sizeof(exeDir) - 1);

    pathJoin(out, size, exeDir, VERSIONS[index].jar);
    if (fileExists(out)) {
        return;
    }
    snprintf(out, size, "%s\\jars\\%s", exeDir, VERSIONS[index].jar);
    if (fileExists(out)) {
        return;
    }
    snprintf(out, size, "%s\\..\\%s", exeDir, VERSIONS[index].jar);
    if (!fileExists(out)) {
        out[0] = 0;
    }
}

static void actionInstallClient(void)
{
    char gameDir[MAX_PATH];
    char modsDir[MAX_PATH];
    char target[MAX_PATH];
    char source[MAX_PATH];
    const NexusVersion *version;
    int index = currentVersionIndex();

    if (index < 0 || index >= VERSION_COUNT) {
        return;
    }
    version = &VERSIONS[index];
    getGameDir(gameDir, sizeof(gameDir));
    findJar(source, sizeof(source));
    if (!source[0]) {
        appendLog("   ! %s not found next to the launcher.", version->jar);
        appendLog("     Build it with gradle (see BUILD_INSTRUCTIONS.md) and place it");
        appendLog("     in the same folder as NexusInjector.exe or in .\\jars\\");
        return;
    }
    if (!dirExists(gameDir)) {
        appendLog("   ! game directory does not exist: %s", gameDir);
        return;
    }
    pathJoin(modsDir, sizeof(modsDir), gameDir, "mods");
    ensureDir(modsDir);
    pathJoin(target, sizeof(target), modsDir, version->jar);

    appendLog("> installing %s -> %s", version->jar, target);
    progress(0);
    if (CopyFileExA(source, target, copyProgress, NULL, FALSE, 0)) {
        progress(100);
        appendLog("   OK installed (%s)", version->jar);
    } else {
        appendLog("   ! copy failed (error %lu)", GetLastError());
    }
}

static void actionInstallFabric(void)
{
    char gameDir[MAX_PATH];
    char tempDir[MAX_PATH];
    char installerPath[MAX_PATH];
    char url[512];
    char params[1024];
    char java[MAX_PATH];
    int index = currentVersionIndex();
    const NexusVersion *version;

    if (index < 0 || index >= VERSION_COUNT) {
        return;
    }
    version = &VERSIONS[index];
    getGameDir(gameDir, sizeof(gameDir));

    GetTempPathA(sizeof(tempDir), tempDir);
    snprintf(installerPath, sizeof(installerPath), "%sfabric-installer-%s.jar",
             tempDir, version->installer);
    snprintf(url, sizeof(url), "%s%s/fabric-installer-%s.jar",
             FABRIC_MAVEN, version->installer, version->installer);

    appendLog("> downloading official Fabric installer %s", version->installer);
    progress(10);
    if (URLDownloadToFileA(NULL, url, installerPath, 0, NULL) != S_OK) {
        appendLog("   ! download failed: %s", url);
        return;
    }
    progress(60);
    appendLog("   OK saved to %s", installerPath);

    GetWindowTextA(g_editJava, java, sizeof(java));
    if (!fileExists(java) && !findJava(java, sizeof(java))) {
        appendLog("   ! javaw.exe not found - install Java %s first", version->java);
        return;
    }
    progress(80);
    snprintf(params, sizeof(params), "-jar \"%s\" client -mcversion %s -dir \"%s\"",
             installerPath, version->mc, gameDir);
    appendLog("> running: javaw %s", params);
    shellOpen(java, params);
    progress(100);
    appendLog("   OK Fabric installer started - complete its dialog to finish.");
    SetWindowTextA(g_lblJavaInfo, java);
}

static void actionCreateProfile(void)
{
    char gameDir[MAX_PATH];
    int index = currentVersionIndex();
    int makeDefault;

    if (index < 0 || index >= VERSION_COUNT) {
        return;
    }
    getGameDir(gameDir, sizeof(gameDir));
    makeDefault = (SendMessageA(g_chkDefault, BM_GETCHECK, 0, 0) == BST_CHECKED);
    appendLog("> registering launcher profile \"%s\" (%s)",
              CLIENT_NAME, VERSIONS[index].loaderVersion);
    if (updateLauncherProfiles(gameDir, VERSIONS[index].loaderVersion, makeDefault)) {
        appendLog("   OK profile ready - open the Minecraft Launcher and press Play.");
    } else {
        appendLog("   ! profile could not be written (start the official launcher once)");
    }
    settingsSave(gameDir, "", index);
}

static void actionLaunchMinecraft(void)
{
    static const char *candidates[] = {
        "\\Minecraft Launcher\\MinecraftLauncher.exe",
        "\\Programs\\Minecraft Launcher\\MinecraftLauncher.exe",
        "\\XboxGames\\Minecraft Launcher\\Content\\Minecraft.exe",
        "\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe",
        "\\Program Files\\Minecraft Launcher\\MinecraftLauncher.exe"
    };
    char path[MAX_PATH];
    unsigned int i;
    const char *localAppData = getenv("LOCALAPPDATA");
    const char *programFiles = getenv("ProgramFiles");
    const char *programFilesX86 = getenv("ProgramFiles(x86)");

    appendLog("> looking for the official Minecraft Launcher...");
    for (i = 0; i < sizeof(candidates) / sizeof(candidates[0]); i++) {
        const char *roots[3];
        unsigned int r;
        roots[0] = localAppData;
        roots[1] = programFiles;
        roots[2] = programFilesX86;
        for (r = 0; r < 3; r++) {
            if (!roots[r] || !roots[r][0]) {
                continue;
            }
            snprintf(path, sizeof(path), "%s%s", roots[r], candidates[i]);
            if (fileExists(path)) {
                appendLog("   OK found %s", path);
                shellOpen(path, NULL);
                return;
            }
        }
    }
    appendLog("   ! Minecraft Launcher not found - opening the download page");
    shellOpen("https://www.minecraft.net/en-us/download", NULL);
}

static void actionOpenFolder(const char *sub)
{
    char gameDir[MAX_PATH];
    char target[MAX_PATH];
    getGameDir(gameDir, sizeof(gameDir));
    if (sub && sub[0]) {
        pathJoin(target, sizeof(target), gameDir, sub);
        ensureDir(target);
    } else {
        strncpy(target, gameDir, sizeof(target) - 1);
    }
    if (!dirExists(target)) {
        appendLog("   ! folder does not exist: %s", target);
        return;
    }
    ShellExecuteA(NULL, "explore", target, NULL, NULL, SW_SHOWNORMAL);
}

static void actionVerify(void)
{
    char gameDir[MAX_PATH];
    char modsDir[MAX_PATH];
    char jarPath[MAX_PATH];
    char java[MAX_PATH];
    int index = currentVersionIndex();
    int problems = 0;
    const NexusVersion *version;

    if (index < 0 || index >= VERSION_COUNT) {
        return;
    }
    version = &VERSIONS[index];
    getGameDir(gameDir, sizeof(gameDir));
    progress(0);

    appendLog("=== verification for Minecraft %s (protocol %d, %s) ===",
              version->mc, version->protocol, version->java);
    if (dirExists(gameDir)) {
        appendLog("   [OK]   game directory      : %s", gameDir);
    } else {
        appendLog("   [FAIL] game directory      : %s", gameDir);
        problems++;
    }
    progress(20);

    pathJoin(modsDir, sizeof(modsDir), gameDir, "mods");
    if (dirExists(modsDir)) {
        appendLog("   [OK]   mods folder         : %s", modsDir);
    } else {
        appendLog("   [FAIL] mods folder missing (created now)");
        ensureDir(modsDir);
        problems++;
    }
    progress(40);

    pathJoin(jarPath, sizeof(jarPath), modsDir, version->jar);
    if (fileExists(jarPath)) {
        appendLog("   [OK]   client installed    : %s", version->jar);
    } else {
        appendLog("   [FAIL] client not installed: %s (run INSTALL CLIENT)", version->jar);
        problems++;
    }
    progress(60);

    GetWindowTextA(g_editJava, java, sizeof(java));
    if (!fileExists(java)) {
        findJava(java, sizeof(java));
    }
    if (fileExists(java)) {
        appendLog("   [OK]   java                : %s", java);
        SetWindowTextA(g_editJava, java);
        SetWindowTextA(g_lblJavaInfo, java);
    } else {
        appendLog("   [FAIL] java %s not found (needed for %s)", version->java, version->mc);
        problems++;
    }
    progress(80);

    {
        char loaderDir[MAX_PATH];
        pathJoin(loaderDir, sizeof(loaderDir), gameDir, "versions");
        snprintf(java, sizeof(java), "%s\\%s", loaderDir, version->loaderVersion);
        if (dirExists(java)) {
            appendLog("   [OK]   fabric profile      : %s", version->loaderVersion);
        } else {
            appendLog("   [WARN] fabric profile      : %s not installed (run INSTALL FABRIC)",
                      version->loaderVersion);
        }
    }
    progress(100);
    appendLog("=== verification finished: %s ===",
              problems == 0 ? "READY" : (problems == 1 ? "1 issue" : "issues found"));
    settingsSave(gameDir, java, index);
}

static void refreshVersionInfo(void)
{
    char text[512];
    int index = currentVersionIndex();
    if (index < 0 || index >= VERSION_COUNT) {
        return;
    }
    snprintf(text, sizeof(text),
             "Minecraft %s   -   protocol %d   -   requires %s\nFabric profile: %s   -   jar: %s",
             VERSIONS[index].mc, VERSIONS[index].protocol, VERSIONS[index].java,
             VERSIONS[index].loaderVersion, VERSIONS[index].jar);
    SetWindowTextA(g_lblVersionInfo, text);
}

/* ------------------------------------------------------------------- browse */
static int browseFolder(HWND owner, char *out, size_t size)
{
    BROWSEINFOA info;
    LPITEMIDLIST pidl;
    char display[MAX_PATH];

    ZeroMemory(&info, sizeof(info));
    info.hwndOwner = owner;
    info.pszDisplayName = display;
    info.lpszTitle = "Select the Minecraft game directory (.minecraft)";
    info.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE | BIF_EDITBOX;
    pidl = SHBrowseForFolderA(&info);
    if (!pidl) {
        return 0;
    }
    SHGetPathFromIDListA(pidl, out);
    CoTaskMemFree(pidl);
    out[size - 1] = 0;
    return 1;
}

static int browseFile(HWND owner, const char *filter, char *out, size_t size)
{
    OPENFILENAMEA dialog;
    ZeroMemory(&dialog, sizeof(dialog));
    dialog.lStructSize = sizeof(dialog);
    dialog.hwndOwner = owner;
    dialog.lpstrFilter = filter;
    dialog.lpstrFile = out;
    dialog.nMaxFile = (DWORD)size;
    dialog.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_HIDEREADONLY;
    dialog.lpstrTitle = "Select javaw.exe";
    out[0] = 0;
    return GetOpenFileNameA(&dialog) ? 1 : 0;
}

/* ----------------------------------------------------------------- controls */
static void styleControl(HWND control)
{
    if (!control) {
        return;
    }
    SetWindowTheme(control, L"", L"");
    SendMessageA(control, WM_SETFONT, (WPARAM)g_font, TRUE);
}

static HWND makeStatic(HWND parent, const char *text, int id, int x, int y, int w, int h)
{
    HWND control = CreateWindowExA(0, "STATIC", text,
                                   WS_CHILD | WS_VISIBLE | SS_LEFT,
                                   x, y, w, h, parent, (HMENU)(INT_PTR)id,
                                   GetModuleHandleA(NULL), NULL);
    styleControl(control);
    return control;
}

static HWND makeButton(HWND parent, const char *text, int id, int x, int y, int w, int h)
{
    HWND control = CreateWindowExA(0, "BUTTON", text,
                                   WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON | BS_FLAT,
                                   x, y, w, h, parent, (HMENU)(INT_PTR)id,
                                   GetModuleHandleA(NULL), NULL);
    styleControl(control);
    return control;
}

static HWND makeEdit(HWND parent, const char *text, int id, int x, int y, int w, int h)
{
    HWND control = CreateWindowExA(WS_EX_CLIENTEDGE, "EDIT", text,
                                   WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL,
                                   x, y, w, h, parent, (HMENU)(INT_PTR)id,
                                   GetModuleHandleA(NULL), NULL);
    styleControl(control);
    return control;
}

/* --------------------------------------------------------------- pages init */
enum {
    ID_TAB_NEWS = 100, ID_TAB_INSTALL, ID_TAB_VERSIONS, ID_TAB_SETTINGS, ID_TAB_ABOUT,
    ID_EDIT_GAMEDIR = 200, ID_BTN_BROWSE_GAME, ID_COMBO_VERSION, ID_LBL_VERSION,
    ID_BTN_INSTALL, ID_BTN_FABRIC, ID_BTN_PROFILE, ID_BTN_LAUNCH, ID_BTN_MODS,
    ID_BTN_VERIFY, ID_CHK_DEFAULT, ID_EDIT_JAVA, ID_BTN_BROWSE_JAVA, ID_LBL_JAVA,
    ID_BTN_OPEN_CONFIG, ID_BTN_UPDATES, ID_BTN_OPEN_MINECRAFT
};

static const char *NEWS_TEXT =
    "NEXUS CLIENT OMEGA X " CLIENT_VER "\n"
    "THE ULTIMATE MINECRAFT EXPERIENCE\n"
    "\n"
    "What is inside\n"
    "  - 273 modules across COMBAT, MOVEMENT, PLAYER, RENDER, WORLD,\n"
    "    AUTOMATION, EXPLORATION, MISC, CLIENT and CONFIGS\n"
    "  - ClickGUI (RIGHT SHIFT opens and closes, ESC closes the current window first)\n"
    "  - HUD editor with 26 widgets, 7 themes, undo / redo / snap to grid\n"
    "  - 30 chat commands, macros, waypoints, friends, config profiles\n"
    "\n"
    "Supported versions\n"
    "  - Minecraft 1.16.5  (protocol 754, Java 8)\n"
    "  - Minecraft 1.21.4  (protocol 769, Java 21)\n"
    "  - Minecraft 1.21.11 (protocol 774, Java 21)\n"
    "\n"
    "Quick start\n"
    "  1. INSTALL tab - select the .minecraft folder and the version\n"
    "  2. INSTALL FABRIC - the official Fabric installer opens, press Install\n"
    "  3. INSTALL CLIENT - copies the adapter jar into mods\\\n"
    "  4. CREATE PROFILE - registers the profile in the official launcher\n"
    "  5. LAUNCH MINECRAFT - press Play, then RIGHT SHIFT in game\n"
    "\n"
    "Every PvP feature is meant for single player, training worlds and\n"
    "servers that allow them. The client contains no telemetry and no\n"
    "credential access of any kind.\n";

static const char *VERSIONS_TEXT =
    "SUPPORTED MINECRAFT VERSIONS\n"
    "\n"
    "  VERSION     PROTOCOL   JAVA       FABRIC PROFILE                      JAR\n"
    "  --------------------------------------------------------------------------------\n"
    "  1.16.5      754        Java  8    fabric-loader-0.14.21-1.16.5        NexusOmegaX-1.16.5.jar\n"
    "  1.21.4      769        Java 21    fabric-loader-0.16.10-1.21.4        NexusOmegaX-1.21.4.jar\n"
    "  1.21.11     774        Java 21    fabric-loader-0.17.2-1.21.11        NexusOmegaX-1.21.11.jar\n"
    "\n"
    "All three adapters share the same NexusCore sources (api/, core/,\n"
    "modules/). Only the thin version adapter changes, so behaviour is\n"
    "identical on every version.\n"
    "\n"
    "Keys\n"
    "  RIGHT SHIFT  open / close the ClickGUI\n"
    "  ESC          close the current window (then the ClickGUI)\n"
    "  .            command prefix in the chat (try .help)\n";

static const char *ABOUT_TEXT =
    "NEXUS CLIENT OMEGA X\n"
    "THE ULTIMATE MINECRAFT EXPERIENCE\n"
    "\n"
    "Version     1.0.0\n"
    "Modules     273\n"
    "Commands    30\n"
    "Themes      7\n"
    "HUD widgets 26\n"
    "\n"
    "License     MIT (sources are included in NexusOmegaX-Source.zip)\n"
    "Project     " PROJECT_URL "\n"
    "\n"
    "This installer is a legitimate tool:\n"
    "  - it installs Fabric through the official Fabric installer only,\n"
    "  - it writes a normal profile into launcher_profiles.json,\n"
    "  - it copies the client jar into the mods folder,\n"
    "  - it never reads tokens, passwords or session files,\n"
    "  - it never injects code into other processes,\n"
    "  - it never disables operating system protections.\n"
    "\n"
    "Use the PvP modules in single player, on training worlds or on\n"
    "servers whose rules allow them. Respect every server you join.\n";

static void createPages(HWND parent)
{
    int i;

    /* ------------------------------------------------------------- NEWS page */
    g_pages[0] = CreateWindowExA(0, "STATIC", NEWS_TEXT,
                                 WS_CHILD | SS_LEFT, 0, 0, 10, 10, parent,
                                 (HMENU)(INT_PTR)900, GetModuleHandleA(NULL), NULL);
    styleControl(g_pages[0]);

    /* ---------------------------------------------------------- INSTALL page */
    g_pages[1] = CreateWindowExA(0, "STATIC", "", WS_CHILD, 0, 0, 10, 10, parent,
                                 (HMENU)(INT_PTR)901, GetModuleHandleA(NULL), NULL);
    {
        HWND page = g_pages[1];
        g_lblGameDirTitle = makeStatic(page, "MINECRAFT FOLDER", 0, 0, 0, 10, 10);
        g_editGameDir = makeEdit(page, "", ID_EDIT_GAMEDIR, 0, 0, 10, 10);
        makeButton(page, "BROWSE", ID_BTN_BROWSE_GAME, 0, 0, 10, 10);
        g_lblVersionTitle = makeStatic(page, "MINECRAFT VERSION", 0, 0, 0, 10, 10);
        g_comboVersion = CreateWindowExA(0, "COMBOBOX", "",
                                         WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL,
                                         0, 0, 10, 120, page,
                                         (HMENU)(INT_PTR)ID_COMBO_VERSION,
                                         GetModuleHandleA(NULL), NULL);
        styleControl(g_comboVersion);
        for (i = 0; i < VERSION_COUNT; i++) {
            SendMessageA(g_comboVersion, CB_ADDSTRING, 0, (LPARAM)VERSIONS[i].mc);
        }
        SendMessageA(g_comboVersion, CB_SETCURSEL, 1, 0);
        g_lblVersionInfo = makeStatic(page, "", ID_LBL_VERSION, 0, 0, 10, 10);

        makeButton(page, "INSTALL CLIENT", ID_BTN_INSTALL, 0, 0, 10, 10);
        makeButton(page, "INSTALL FABRIC", ID_BTN_FABRIC, 0, 0, 10, 10);
        makeButton(page, "CREATE PROFILE", ID_BTN_PROFILE, 0, 0, 10, 10);
        makeButton(page, "LAUNCH MINECRAFT", ID_BTN_LAUNCH, 0, 0, 10, 10);
        makeButton(page, "OPEN MODS FOLDER", ID_BTN_MODS, 0, 0, 10, 10);
        makeButton(page, "VERIFY INSTALL", ID_BTN_VERIFY, 0, 0, 10, 10);

        g_chkDefault = CreateWindowExA(0, "BUTTON", "Set as the default launcher profile",
                                       WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
                                       0, 0, 10, 10, page,
                                       (HMENU)(INT_PTR)ID_CHK_DEFAULT,
                                       GetModuleHandleA(NULL), NULL);
        styleControl(g_chkDefault);
        SendMessageA(g_chkDefault, BM_SETCHECK, BST_CHECKED, 0);

        g_progress = CreateWindowExA(0, PROGRESS_CLASS, "",
                                     WS_CHILD | WS_VISIBLE | PBS_SMOOTH,
                                     0, 0, 10, 10, page, (HMENU)(INT_PTR)950,
                                     GetModuleHandleA(NULL), NULL);
        SetWindowTheme(g_progress, L"", L"");
        SendMessageA(g_progress, PBM_SETRANGE, 0, MAKELPARAM(0, 100));
        SendMessageA(g_progress, PBM_SETBARCOLOR, 0, (LPARAM)C_ACCENT3);
        SendMessageA(g_progress, PBM_SETBKCOLOR, 0, (LPARAM)C_SURFACE);

        g_log = CreateWindowExA(WS_EX_CLIENTEDGE, "LISTBOX", "",
                                WS_CHILD | WS_VISIBLE | WS_VSCROLL | LBS_NOSEL | LBS_HASSTRINGS,
                                0, 0, 10, 10, page, (HMENU)(INT_PTR)951,
                                GetModuleHandleA(NULL), NULL);
        styleControl(g_log);
    }

    /* -------------------------------------------------------- VERSIONS page */
    g_pages[2] = CreateWindowExA(0, "STATIC", VERSIONS_TEXT, WS_CHILD, 0, 0, 10, 10,
                                 parent, (HMENU)(INT_PTR)902, GetModuleHandleA(NULL), NULL);
    styleControl(g_pages[2]);

    /* -------------------------------------------------------- SETTINGS page */
    g_pages[3] = CreateWindowExA(0, "STATIC", "", WS_CHILD, 0, 0, 10, 10, parent,
                                 (HMENU)(INT_PTR)903, GetModuleHandleA(NULL), NULL);
    {
        HWND page = g_pages[3];
        makeStatic(page, "JAVA (javaw.exe)", 0, 0, 0, 10, 10);
        g_editJava = makeEdit(page, "", ID_EDIT_JAVA, 0, 0, 10, 10);
        makeButton(page, "BROWSE", ID_BTN_BROWSE_JAVA, 0, 0, 10, 10);
        g_lblJavaInfo = makeStatic(page, "", ID_LBL_JAVA, 0, 0, 10, 10);
        makeButton(page, "OPEN CLIENT CONFIG FOLDER", ID_BTN_OPEN_CONFIG, 0, 0, 10, 10);
        makeButton(page, "CHECK FOR UPDATES", ID_BTN_UPDATES, 0, 0, 10, 10);
        makeButton(page, "OPEN MINECRAFT FOLDER", ID_BTN_OPEN_MINECRAFT, 0, 0, 10, 10);
        g_lblSettingsHint = makeStatic(page, "Settings are stored in:", 0, 0, 0, 10, 10);
        g_lblSettingsPath = makeStatic(page, g_settingsPath, 0, 0, 0, 10, 10);
    }

    /* ----------------------------------------------------------- ABOUT page */
    g_pages[4] = CreateWindowExA(0, "STATIC", ABOUT_TEXT, WS_CHILD, 0, 0, 10, 10,
                                 parent, (HMENU)(INT_PTR)904, GetModuleHandleA(NULL), NULL);
    styleControl(g_pages[4]);
}

static void layout(HWND hwnd)
{
    RECT client;
    int contentX, contentW, contentH;
    int i;

    GetClientRect(hwnd, &client);
    contentX = 232;
    contentW = client.right - contentX - 24;
    contentH = client.bottom - 32;

    for (i = 0; i < 5; i++) {
        MoveWindow(g_tabs[i], 16, 96 + i * 52, 200, 42, TRUE);
    }

    for (i = 0; i < 5; i++) {
        ShowWindow(g_pages[i], i == g_page ? SW_SHOW : SW_HIDE);
    }

    if (g_page == 0 || g_page == 2 || g_page == 4) {
        MoveWindow(g_pages[g_page], contentX, 24, contentW, contentH, TRUE);
        return;
    }
    if (g_page == 1) {
        HWND page = g_pages[1];
        int y;
        MoveWindow(page, contentX, 24, contentW, contentH, TRUE);
        MoveWindow(g_lblGameDirTitle, 0, 0, contentW, 20, TRUE);
        MoveWindow(g_editGameDir, 0, 24, contentW - 116, 26, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_BROWSE_GAME), contentW - 108, 24, 108, 26, TRUE);
        y = 64;
        MoveWindow(g_lblVersionTitle, 0, y, contentW, 20, TRUE);
        y += 24;
        MoveWindow(g_comboVersion, 0, y, 260, 26, TRUE);
        MoveWindow(g_lblVersionInfo, 276, y + 4, contentW - 276, 40, TRUE);
        y += 56;
        MoveWindow(GetDlgItem(page, ID_BTN_INSTALL), 0, y, 200, 38, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_FABRIC), 212, y, 200, 38, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_PROFILE), 424, y, 200, 38, TRUE);
        y += 48;
        MoveWindow(GetDlgItem(page, ID_BTN_LAUNCH), 0, y, 200, 38, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_MODS), 212, y, 200, 38, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_VERIFY), 424, y, 200, 38, TRUE);
        y += 52;
        MoveWindow(g_chkDefault, 0, y, 320, 24, TRUE);
        y += 32;
        MoveWindow(g_progress, 0, y, contentW, 16, TRUE);
        y += 26;
        MoveWindow(g_log, 0, y, contentW, contentH - y - 24, TRUE);
        return;
    }
    if (g_page == 3) {
        HWND page = g_pages[3];
        int y;
        MoveWindow(page, contentX, 24, contentW, contentH, TRUE);
        MoveWindow(g_lblJavaTitle, 0, 0, contentW, 20, TRUE);
        MoveWindow(g_editJava, 0, 24, contentW - 116, 26, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_BROWSE_JAVA), contentW - 108, 24, 108, 26, TRUE);
        y = 60;
        MoveWindow(g_lblJavaInfo, 0, y, contentW, 20, TRUE);
        y += 40;
        MoveWindow(GetDlgItem(page, ID_BTN_OPEN_CONFIG), 0, y, 260, 38, TRUE);
        MoveWindow(GetDlgItem(page, ID_BTN_UPDATES), 272, y, 200, 38, TRUE);
        y += 48;
        MoveWindow(GetDlgItem(page, ID_BTN_OPEN_MINECRAFT), 0, y, 260, 38, TRUE);
        y += 60;
        MoveWindow(g_lblSettingsHint, 0, y, contentW, 20, TRUE);
        y += 22;
        MoveWindow(g_lblSettingsPath, 0, y, contentW, 20, TRUE);
        return;
    }
}

static void switchPage(int page)
{
    g_page = page;
    layout(g_hwnd);
    InvalidateRect(g_hwnd, NULL, TRUE);
}

/* --------------------------------------------------------------- window proc */
static LRESULT CALLBACK windowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam)
{
    switch (message) {
    case WM_CREATE:
        g_hwnd = hwnd;
        g_bgBrush = CreateSolidBrush(C_BG);
        g_panelBrush = CreateSolidBrush(C_PANEL);
        g_fieldBrush = CreateSolidBrush(C_SURFACE);
        g_font = CreateFontA(-15, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                             DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                             CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, "Segoe UI");
        g_fontBold = CreateFontA(-16, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
                                 DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                 CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, "Segoe UI");
        g_fontTitle = CreateFontA(-22, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
                                  DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                  CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, "Segoe UI");
        g_tabs[0] = makeButton(hwnd, "NEWS", ID_TAB_NEWS, 0, 0, 10, 10);
        g_tabs[1] = makeButton(hwnd, "INSTALL", ID_TAB_INSTALL, 0, 0, 10, 10);
        g_tabs[2] = makeButton(hwnd, "VERSIONS", ID_TAB_VERSIONS, 0, 0, 10, 10);
        g_tabs[3] = makeButton(hwnd, "SETTINGS", ID_TAB_SETTINGS, 0, 0, 10, 10);
        g_tabs[4] = makeButton(hwnd, "ABOUT", ID_TAB_ABOUT, 0, 0, 10, 10);
        createPages(hwnd);
        return 0;

    case WM_COMMAND:
        switch (LOWORD(wParam)) {
        case ID_TAB_NEWS:     switchPage(0); return 0;
        case ID_TAB_INSTALL:  switchPage(1); return 0;
        case ID_TAB_VERSIONS: switchPage(2); return 0;
        case ID_TAB_SETTINGS: switchPage(3); return 0;
        case ID_TAB_ABOUT:    switchPage(4); return 0;
        case ID_BTN_BROWSE_GAME: {
            char dir[MAX_PATH];
            if (browseFolder(hwnd, dir, sizeof(dir))) {
                SetWindowTextA(g_editGameDir, dir);
                {
                    char java[MAX_PATH];
                    if (findJava(java, sizeof(java))) {
                        SetWindowTextA(g_editJava, java);
                        SetWindowTextA(g_lblJavaInfo, java);
                    }
                    settingsSave(dir, java, currentVersionIndex());
                }
            }
            return 0;
        }
        case ID_BTN_BROWSE_JAVA: {
            char java[MAX_PATH] = "";
            if (browseFile(hwnd, "javaw.exe\0javaw.exe\0All files\0*.*\0", java, sizeof(java))) {
                SetWindowTextA(g_editJava, java);
                SetWindowTextA(g_lblJavaInfo, java);
            }
            return 0;
        }
        case ID_BTN_INSTALL:   actionInstallClient(); return 0;
        case ID_BTN_FABRIC:    actionInstallFabric(); return 0;
        case ID_BTN_PROFILE:   actionCreateProfile(); return 0;
        case ID_BTN_LAUNCH:    actionLaunchMinecraft(); return 0;
        case ID_BTN_MODS:      actionOpenFolder("mods"); return 0;
        case ID_BTN_VERIFY:    actionVerify(); return 0;
        case ID_BTN_OPEN_MINECRAFT: actionOpenFolder(NULL); return 0;
        case ID_BTN_OPEN_CONFIG: {
            char configDir[MAX_PATH];
            char gameDir[MAX_PATH];
            getGameDir(gameDir, sizeof(gameDir));
            if (gameDir[0]) {
                snprintf(configDir, sizeof(configDir), "%s\\NexusOmegaX\\configs", gameDir);
            } else {
                snprintf(configDir, sizeof(configDir), "%s\\NexusOmegaX\\configs", g_appData);
            }
            ensureDir(configDir);
            ShellExecuteA(NULL, "explore", configDir, NULL, NULL, SW_SHOWNORMAL);
            return 0;
        }
        case ID_BTN_UPDATES:
            shellOpen(PROJECT_URL, NULL);
            return 0;
        case ID_COMBO_VERSION:
            if (HIWORD(wParam) == CBN_SELCHANGE) {
                refreshVersionInfo();
            }
            return 0;
        default:
            break;
        }
        break;

    case WM_CTLCOLORSTATIC:
    case WM_CTLCOLOREDIT:
    case WM_CTLCOLORLISTBOX: {
        HDC dc = (HDC)wParam;
        SetBkColor(dc, C_SURFACE);
        SetTextColor(dc, C_TEXT);
        if ((HWND)lParam == g_lblVersionInfo || (HWND)lParam == g_lblJavaInfo) {
            SetTextColor(dc, C_ACCENT4);
        }
        if ((HWND)lParam == g_pages[0] || (HWND)lParam == g_pages[1]
            || (HWND)lParam == g_pages[2] || (HWND)lParam == g_pages[3]
            || (HWND)lParam == g_pages[4]) {
            SetBkColor(dc, C_BG);
            return (LRESULT)g_bgBrush;
        }
        return (LRESULT)((message == WM_CTLCOLOREDIT || message == WM_CTLCOLORLISTBOX)
                         ? g_fieldBrush : g_panelBrush);
    }
    case WM_CTLCOLORBTN: {
        HDC dc = (HDC)wParam;
        SetBkColor(dc, C_SURFACE);
        SetTextColor(dc, C_TEXT);
        return (LRESULT)g_fieldBrush;
    }

    case WM_GETMINMAXINFO: {
        MINMAXINFO *info = (MINMAXINFO *)lParam;
        info->ptMinTrackSize.x = 900;
        info->ptMinTrackSize.y = 620;
        return 0;
    }

    case WM_SIZE:
        layout(hwnd);
        return 0;

    case WM_ERASEBKGND:
        return 1;

    case WM_PAINT: {
        PAINTSTRUCT paint;
        HDC dc = BeginPaint(hwnd, &paint);
        RECT client;
        RECT sidebar;
        HGDIOBJ old;

        GetClientRect(hwnd, &client);
        FillRect(dc, &client, g_bgBrush);

        sidebar.left = 0;
        sidebar.top = 0;
        sidebar.right = 220;
        sidebar.bottom = client.bottom;
        FillRect(dc, &sidebar, g_panelBrush);

        old = SelectObject(dc, g_fontTitle);
        SetBkMode(dc, TRANSPARENT);
        SetTextColor(dc, C_ACCENT4);
        TextOutA(dc, 20, 22, "NEXUS", 5);
        SetTextColor(dc, C_TEXT);
        TextOutA(dc, 20, 48, "INJECTOR", 8);
        SelectObject(dc, g_font);
        SetTextColor(dc, C_MUTED);
        TextOutA(dc, 20, 76, CLIENT_NAME, (int)strlen(CLIENT_NAME));

        /* accent strip */
        {
            RECT strip;
            strip.left = 0;
            strip.top = client.bottom - 8;
            strip.right = 220;
            strip.bottom = client.bottom;
            FillRect(dc, &strip, CreateSolidBrush(C_ACCENT));
        }
        SelectObject(dc, old);
        EndPaint(hwnd, &paint);
        return 0;
    }

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    default:
        break;
    }
    return DefWindowProcA(hwnd, message, wParam, lParam);
}

/* -------------------------------------------------------------------- entry */
int WINAPI WinMain(HINSTANCE instance, HINSTANCE previous, LPSTR commandLine, int showCommand)
{
    WNDCLASSA windowClass;
    MSG message;
    char gameDir[MAX_PATH] = "";
    char java[MAX_PATH] = "";
    int versionIndex = 1;
    const char *appData;

    (void)previous; (void)commandLine; (void)showCommand;

    InitCommonControls();
    CoInitialize(NULL);

    appData = getenv("APPDATA");
    if (!appData) {
        appData = "C:\\";
    }
    strncpy(g_appData, appData, sizeof(g_appData) - 1);
    snprintf(g_appData, sizeof(g_appData), "%s\\NexusOmegaX", appData);
    ensureDir(g_appData);
    snprintf(g_settingsPath, sizeof(g_settingsPath), "%s\\injector-settings.ini", g_appData);
    snprintf(g_logPath, sizeof(g_logPath), "%s\\injector.log", g_appData);

    settingsLoad(gameDir, sizeof(gameDir), java, sizeof(java), &versionIndex);
    if (!gameDir[0]) {
        locateGameDir(gameDir, sizeof(gameDir));
    }

    ZeroMemory(&windowClass, sizeof(windowClass));
    windowClass.lpfnWndProc = windowProc;
    windowClass.hInstance = instance;
    windowClass.lpszClassName = "NexusInjectorWindow";
    windowClass.hCursor = LoadCursorA(NULL, IDC_ARROW);
    windowClass.hbrBackground = NULL;
    windowClass.style = CS_HREDRAW | CS_VREDRAW;
    RegisterClassA(&windowClass);

    g_hwnd = CreateWindowExA(0, "NexusInjectorWindow", "NexusInjector - " CLIENT_NAME,
                             WS_OVERLAPPEDWINDOW | WS_VISIBLE,
                             CW_USEDEFAULT, CW_USEDEFAULT, 1020, 680,
                             NULL, NULL, instance, NULL);
    if (!g_hwnd) {
        MessageBoxA(NULL, "Could not create the main window.", "NexusInjector", MB_ICONERROR);
        return 1;
    }

    SetWindowTextA(g_editGameDir, gameDir);
    if (java[0]) {
        SetWindowTextA(g_editJava, java);
        SetWindowTextA(g_lblJavaInfo, java);
    } else if (findJava(java, sizeof(java))) {
        SetWindowTextA(g_editJava, java);
        SetWindowTextA(g_lblJavaInfo, java);
    }
    SendMessageA(g_comboVersion, CB_SETCURSEL, (WPARAM)versionIndex, 0);
    refreshVersionInfo();
    switchPage(1);

    appendLog("NexusInjector %s ready.", CLIENT_VER);
    appendLog("Game directory: %s", gameDir);
    appendLog("Press INSTALL FABRIC, then INSTALL CLIENT, then CREATE PROFILE.");

    while (GetMessageA(&message, NULL, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageA(&message);
    }

    {
        char currentGameDir[MAX_PATH];
        char currentJava[MAX_PATH];
        getGameDir(currentGameDir, sizeof(currentGameDir));
        GetWindowTextA(g_editJava, currentJava, sizeof(currentJava));
        settingsSave(currentGameDir, currentJava, currentVersionIndex());
    }
    CoUninitialize();
    return 0;
}
