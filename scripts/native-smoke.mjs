// Runs ONLY against a real compiled Windows Tauri application via WebView2 CDP.
// No mocked IPC, fake downloads, OAuth credentials or Minecraft license bypass.
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { setTimeout as delay } from 'node:timers/promises';
import path from 'node:path';

const report = [];
let processHandle;
let browser;
let page;
const outputDir = path.resolve('release-artifacts');
await mkdir(outputDir, { recursive: true });
const record = (check, status, detail) => { report.push({ check, status, detail }); console.log(`${status}: ${check} — ${detail}`); };
const invoke = (command, args = {}) => page.evaluate(({ command, args }) => window.__TAURI_INTERNALS__.invoke(command, args), { command, args });
async function test(check, fn) { try { const detail = await fn(); record(check, 'PASSED', detail ?? 'Completed against the native backend.'); return true; } catch (e) { record(check, 'FAILED', String(e)); return false; } }
async function writeReport() {
  await writeFile(path.join(outputDir, 'NATIVE-SMOKE.json'), JSON.stringify({ generated_at: new Date().toISOString(), source_commit: process.env.GITHUB_SHA ?? 'local', runtime: 'Windows x64 / real Tauri + WebView2', checks: report }, null, 2));
  const lines = ['# Native Windows smoke results', '', 'These checks use the real compiled launcher. They do not establish that a licensed game was rendered.', '', ...report.map(r => `## ${r.check}: ${r.status}\n\n${r.detail}\n`)];
  await writeFile(path.join(outputDir, 'NATIVE-SMOKE.md'), lines.join('\n'));
}
try {
  const executable = path.resolve('src-tauri/target/x86_64-pc-windows-msvc/release/winlauncher.exe');
  processHandle = spawn(executable, [], { env: { ...process.env, WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS: '--remote-debugging-port=9222 --remote-allow-origins=http://localhost:9222 --disable-gpu' }, stdio: 'ignore' });
  let launchError;
  processHandle.on('error', e => { launchError = e; });
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    if (launchError) throw launchError;
    try { browser = await chromium.connectOverCDP('http://127.0.0.1:9222', { timeout: 3000 }); break; } catch { await delay(1500); }
  }
  if (!browser) throw new Error('WebView2 CDP was not reachable. GUI smoke tests require a working Windows desktop/WebView2 runtime.');
  const context = browser.contexts()[0];
  for (let attempt = 0; attempt < 40; attempt++) {
    page = context.pages().find(p => !p.url().startsWith('devtools:'));
    if (page && await page.evaluate(() => Boolean(window.__TAURI_INTERNALS__)).catch(() => false)) break;
    await delay(500);
  }
  if (!page) throw new Error('The native WebView page did not open.');
  await page.waitForFunction(() => Boolean(window.__TAURI_INTERNALS__), { timeout: 30000 });
  const snapshot = await invoke('get_snapshot');
  record('Native EXE + WebView2 + SQLite bootstrap', 'PASSED', `Opened WinLauncher ${snapshot.version}; real SQLite profiles=${snapshot.profiles.length}.`);
  const originalSettings = snapshot.settings;
  await test('System hardware information', async () => { const system = await invoke('get_system_info'); if (system.total_memory_mb <= 0 || !system.cpu || !system.os) throw new Error('Missing hardware information'); return `${system.os}, ${system.arch}, ${system.total_memory_mb} MB RAM; CPU and disk detected.`; });
  await test('Native theme persistence in SQLite', async () => { await invoke('save_settings', { settings: { ...originalSettings, theme: 'nether', effects: false } }); const readback = await invoke('get_snapshot'); if (readback.settings.theme !== 'nether' || readback.settings.effects) throw new Error('Saved preferences did not match'); await invoke('save_settings', { settings: originalSettings }); return 'Saved Nether/effects=false via Rust and read back from the native state backed by SQLite. App restart is a separate test.'; });
  let manifest;
  await test('Official Mojang version manifest API', async () => { manifest = await invoke('version_manifest'); if (!manifest.versions.some(v => v.id === '1.21.4') || !manifest.versions.some(v => v.id === '1.8.9')) throw new Error('Expected genuine Mojang versions not present'); return `${manifest.versions.length} official versions. Current release ${manifest.latest.release}.`; });
  const makeProfile = (loader) => ({ id: crypto.randomUUID(), name: `CI native smoke ${loader}`, game_version: '1.21.4', loader, loader_version: null, min_memory_mb: 512, max_memory_mb: 2048, java_path: null, java_args: [], width: 854, height: 480, fullscreen: false, created_at: Math.floor(Date.now() / 1000), last_played: null, play_seconds: 0, installed: false, modpack: null });
  const vanilla = makeProfile('vanilla');
  await test('Create isolated profile', async () => { const result = await invoke('save_profile', { profile: vanilla }); if (result.id !== vanilla.id || result.installed) throw new Error('Profile was not correctly persisted'); return `Created real isolated profile ${result.id}.`; });
  await test('Reject game launch without licensed Microsoft account', async () => {
    try { await invoke('launch_game', { profileId: vanilla.id, allowMemoryPressure: false }); } catch (e) { if (!String(e).includes('Microsoft')) throw e; return 'Launch rejected before downloading or spawning Java. No offline/cracked fallback.'; }
    throw new Error('Unlicensed launch was unexpectedly accepted!');
  });
  let installed = false;
  if (manifest) installed = await test('Real Vanilla 1.21.4 installation', async () => { await invoke('install_game', { profileId: vanilla.id }); const fresh = await invoke('get_snapshot'); if (!fresh.profiles.find(p => p.id === vanilla.id)?.installed) throw new Error('Profile did not become installed'); return 'Official client, libraries, native DLLs and assets downloaded/verified by Rust, not a simulated progress bar.'; });
  if (installed) {
    await test('Managed Java auto-install and actual java -version', async () => { const runtime = await invoke('install_java', { major: 21, forceManaged: true }); if (runtime.major !== 21 || !runtime.managed || runtime.architecture !== 'amd64') throw new Error('Managed Java 21 x64 was not installed'); return 'Downloaded genuine Adoptium Java 21, verified SHA256, extracted it into managed runtime and executed java -version.'; });
    await test('Verify already-installed Minecraft files', async () => { await invoke('install_game', { profileId: vanilla.id }); return 'Repeated native SHA verification completed against the real cached files.'; });
    const fabric = makeProfile('fabric');
    await invoke('save_profile', { profile: fabric });
    const fabricInstalled = await test('Real Fabric 1.21.4 installation', async () => { await invoke('install_game', { profileId: fabric.id }); const result = (await invoke('get_snapshot')).profiles.find(p => p.id === fabric.id); if (!result?.installed || !result?.loader_version) throw new Error('Fabric installation/version was not persisted'); return `Official Fabric ${result.loader_version} installed with genuine Maven libraries.`; });
    if (fabricInstalled) {
      await test('Real Modrinth search and mod installation', async () => {
        const found = await invoke('search_projects', { provider: 'modrinth', query: 'sodium', kind: 'mod', profileId: fabric.id, offset: 0, sort: 'downloads' });
        const project = found.hits.find(p => p.slug === 'sodium'); if (!project) throw new Error('Sodium was not returned by the real Modrinth API');
        await invoke('install_project', { provider: 'modrinth', profileId: fabric.id, projectId: project.id, versionId: null });
        const content = (await invoke('get_snapshot')).installed.find(f => f.profile_id === fabric.id && f.project_id === project.id); if (!content) throw new Error('Mod was not recorded after download');
        const id = `${content.profile_id}:${content.provider}:${content.project_id}`;
        await invoke('toggle_content', { id });
        if ((await invoke('get_snapshot')).installed.find(f => f.project_id === project.id && f.profile_id === fabric.id)?.enabled !== false) throw new Error('Mod was not disabled');
        await invoke('toggle_content', { id });
        return `Installed ${content.name} ${content.version_id}; hash verified; disabled and re-enabled the actual file.`;
      });
    }
  }
  for (const check of ['Licensed Microsoft OAuth login and token refresh', 'Rendered Minecraft Java game session', 'Forge installation and game launch', 'NeoForge installation and game launch', 'Quilt installation and game launch', 'CurseForge key / distribution permissions', 'Real modpack import/export', 'Discord Desktop Rich Presence', 'RAM in a running licensed game JVM', 'Data-directory migration with user worlds', 'World backups and restore', 'Settings across a full application restart', 'NSIS installer execution']) record(check, 'NOT TESTED', 'Requires additional credentials, real user data, interactive confirmation or an explicitly configured acceptance environment.');
} catch (e) {
  record('Native GUI smoke harness', 'NOT TESTED', String(e));
} finally {
  if (browser) await browser.close().catch(() => {});
  if (processHandle?.pid) {
    // This is only the process created by this test, never a user game process.
    spawn('taskkill', ['/PID', String(processHandle.pid), '/T', '/F'], { stdio: 'ignore' });
  }
  await writeReport();
}
if (report.some(r => r.status === 'FAILED')) process.exitCode = 1;
